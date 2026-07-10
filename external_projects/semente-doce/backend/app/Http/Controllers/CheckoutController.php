<?php

namespace App\Http\Controllers;

use App\Models\DeliveryZone;
use App\Models\Kit;
use App\Models\Order;
use App\Models\Product;
use App\Models\ProductOption;
use App\Services\MercadoPagoService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

class CheckoutController extends Controller
{
    /**
     * Transforma o carrinho do cliente (ilha Vue CartPage) num pedido persistido
     * e inicia o pagamento. Os preços são SEMPRE recalculados a partir do banco —
     * nunca confiamos nos valores vindos do navegador.
     */
    public function store(Request $request, MercadoPagoService $mp): RedirectResponse
    {
        $data = $request->validate([
            'items' => ['required', 'json'],
            'fulfillment_type' => ['required', 'in:delivery,pickup'],
            'delivery_neighborhood' => ['nullable', 'string', 'max:120'],
            'delivery_address' => ['nullable', 'string', 'max:500'],
            'buyer_name' => ['required', 'string', 'max:255'],
            'buyer_email' => ['required', 'email', 'max:255'],
            'buyer_phone' => ['nullable', 'string', 'max:50'],
            'scheduled_for' => ['nullable', 'date'],
            'notes' => ['nullable', 'string', 'max:1000'],
        ]);

        // Só type/id/qty/options importam: qty > 0 e type conhecido.
        $lines = collect(json_decode($data['items'], true) ?: [])
            ->filter(fn ($i) => isset($i['id'], $i['type'])
                && in_array($i['type'], ['product', 'kit'], true)
                && (int) ($i['qty'] ?? 0) > 0)
            ->values();

        if ($lines->isEmpty()) {
            return back()->with('status', 'Seu carrinho está vazio. 🛒');
        }

        // Pré-carrega apenas os itens ATIVOS referenciados no carrinho.
        $products = Product::active()
            ->whereIn('id', $lines->where('type', 'product')->pluck('id')->map(fn ($id) => (int) $id))
            ->get()->keyBy('id');

        $kits = Kit::active()
            ->whereIn('id', $lines->where('type', 'kit')->pluck('id')->map(fn ($id) => (int) $id))
            ->get()->keyBy('id');

        // Monta as linhas com snapshots e preços recalculados no servidor.
        $items = [];       // payloads de OrderItem
        $increments = [];  // [$model, $qty] p/ incrementar sold_count na transação
        $subtotal = 0.0;

        foreach ($lines as $line) {
            $qty = min(99, max(1, (int) $line['qty']));

            if ($line['type'] === 'product') {
                $product = $products->get((int) $line['id']);
                if (! $product) {
                    continue;
                }

                // Opções válidas: ativas E cujo grupo pertence a ESTE produto.
                $selectedIds = array_filter(array_map('intval', (array) ($line['options'] ?? [])));
                $options = $selectedIds
                    ? ProductOption::whereIn('id', $selectedIds)
                        ->where('is_active', true)
                        ->whereHas('group', fn ($q) => $q->where('product_id', $product->id))
                        ->with('group')
                        ->orderBy('position')
                        ->get()
                    : collect();

                $unitPrice = (float) $product->price + (float) $options->sum(fn ($o) => (float) $o->price_delta);
                $lineTotal = $unitPrice * $qty;

                $summary = $options
                    ->map(fn ($o) => trim(($o->group->name ?? '') . ': ' . $o->name))
                    ->implode(' · ');

                $items[] = [
                    'product_id' => $product->id,
                    'kit_id' => null,
                    'title' => $product->title,
                    'image_path' => $product->primary_image_url,
                    'options_summary' => $summary !== '' ? $summary : null,
                    'price' => $unitPrice,
                    'qty' => $qty,
                    'line_total' => $lineTotal,
                ];
                $increments[] = [$product, $qty];
                $subtotal += $lineTotal;
            } else {
                $kit = $kits->get((int) $line['id']);
                if (! $kit) {
                    continue;
                }

                $unitPrice = (float) $kit->price;
                $lineTotal = $unitPrice * $qty;

                $items[] = [
                    'product_id' => null,
                    'kit_id' => $kit->id,
                    'title' => $kit->name,
                    'image_path' => $kit->image_url,
                    'options_summary' => null,
                    'price' => $unitPrice,
                    'qty' => $qty,
                    'line_total' => $lineTotal,
                ];
                $increments[] = [$kit, $qty];
                $subtotal += $lineTotal;
            }
        }

        if (empty($items)) {
            return back()->with('status', 'Os itens do carrinho não estão mais disponíveis. 😔');
        }

        // Pedido mínimo (subtotal) — null desativa a trava.
        $minOrder = config('delivery.min_order');
        if ($minOrder !== null && $subtotal < (float) $minOrder) {
            return back()->with('status', 'O pedido mínimo é de ' . money((float) $minOrder) . '. 🍬');
        }

        // Taxa de entrega: retirada = grátis; free_above zera; senão a zona do bairro.
        $isPickup = $data['fulfillment_type'] === 'pickup';
        $freeAbove = config('delivery.free_above');

        if ($isPickup) {
            $deliveryFee = 0.0;
        } elseif ($freeAbove !== null && $subtotal >= (float) $freeAbove) {
            $deliveryFee = 0.0;
        } else {
            $deliveryFee = (float) (DeliveryZone::matchNeighborhood($data['delivery_neighborhood'] ?? null)?->fee
                ?? config('delivery.default_fee'));
        }

        $order = DB::transaction(function () use ($request, $data, $items, $increments, $subtotal, $deliveryFee, $isPickup, $mp) {
            $order = Order::create([
                'reference' => 'SD-' . Str::upper(Str::random(8)),
                'user_id' => $request->user()->id,
                'buyer_name' => $data['buyer_name'],
                'buyer_email' => $data['buyer_email'],
                'buyer_phone' => $data['buyer_phone'] ?? null,
                'fulfillment_type' => $data['fulfillment_type'],
                'delivery_address' => $isPickup ? null : ($data['delivery_address'] ?? null),
                'delivery_neighborhood' => $isPickup ? null : ($data['delivery_neighborhood'] ?? null),
                'scheduled_for' => $data['scheduled_for'] ?? null,
                'notes' => $data['notes'] ?? null,
                'status' => 'pending',
                'payment_status' => 'pending',
                'payment_method' => $mp->enabled() ? 'mercadopago' : 'simulado',
                'subtotal' => $subtotal,
                'delivery_fee' => $deliveryFee,
                'total' => $subtotal + $deliveryFee,
            ]);

            foreach ($items as $item) {
                $order->items()->create($item);
            }

            foreach ($increments as [$model, $qty]) {
                $model->increment('sold_count', $qty);
            }

            return $order;
        });

        // Checkout Transparente: leva à nossa página de pagamento (Payment Brick).
        if ($mp->enabled()) {
            return redirect()->route('payment.show', $order);
        }

        // Fallback (sem credenciais MP): pagamento simulado, para a demo funcionar.
        $order->applyPaymentStatus('approved');

        return redirect()->route('orders.show', $order)->with('placed', true);
    }
}
