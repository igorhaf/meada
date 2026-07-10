<?php

namespace App\Http\Controllers;

use App\Models\CustomOrder;
use App\Models\Kit;
use App\Models\Product;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class CustomOrderController extends Controller
{
    /**
     * Formulário de encomenda (pedido de orçamento). Pode chegar pré-preenchido a
     * partir de um produto (?product=slug) ou kit (?kit=slug) sob encomenda.
     */
    public function create(Request $request): View
    {
        $product = $request->filled('product')
            ? Product::active()->where('slug', $request->query('product'))->first()
            : null;

        $kit = $request->filled('kit')
            ? Kit::active()->where('slug', $request->query('kit'))->first()
            : null;

        return view('custom-orders.create', compact('product', 'kit'));
    }

    /**
     * Registra a solicitação. É um pedido de ORÇAMENTO — nasce 'requested' e a
     * loja é quem orça e confirma (gate humano). Pode ser feito sem login.
     */
    public function store(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'customer_name' => ['required', 'string', 'max:255'],
            'customer_phone' => ['required', 'string', 'max:50'],
            'customer_email' => ['nullable', 'email', 'max:255'],
            'title' => ['required', 'string', 'max:255'],
            'description' => ['nullable', 'string', 'max:2000'],
            'quantity' => ['nullable', 'integer', 'min:1', 'max:100000'],
            'flavor' => ['nullable', 'string', 'max:255'],
            'message_on_item' => ['nullable', 'string', 'max:255'],
            'reference_photo_url' => ['nullable', 'url', 'max:1000'],
            'fulfillment_type' => ['required', 'in:pickup,delivery'],
            'delivery_address' => ['nullable', 'required_if:fulfillment_type,delivery', 'string', 'max:500'],
            'event_date' => ['nullable', 'date', 'after_or_equal:today'],
            'product' => ['nullable', 'string', 'max:255'],
            'kit' => ['nullable', 'string', 'max:255'],
        ]);

        $product = ! empty($data['product'])
            ? Product::where('slug', $data['product'])->first()
            : null;

        $kit = ! empty($data['kit'])
            ? Kit::where('slug', $data['kit'])->first()
            : null;

        $customOrder = CustomOrder::create([
            'reference' => 'ENC-' . Str::upper(Str::random(6)),
            'user_id' => auth()->id(),
            'product_id' => $product?->id,
            'kit_id' => $kit?->id,
            'customer_name' => $data['customer_name'],
            'customer_phone' => $data['customer_phone'],
            'customer_email' => $data['customer_email'] ?? null,
            'title' => $data['title'],
            'description' => $data['description'] ?? null,
            'quantity' => $data['quantity'] ?? 1,
            'flavor' => $data['flavor'] ?? null,
            'message_on_item' => $data['message_on_item'] ?? null,
            'reference_photo_url' => $data['reference_photo_url'] ?? null,
            'fulfillment_type' => $data['fulfillment_type'],
            'delivery_address' => $data['fulfillment_type'] === 'delivery'
                ? ($data['delivery_address'] ?? null)
                : null,
            'event_date' => $data['event_date'] ?? null,
            'status' => 'requested',
        ]);

        return redirect()
            ->route('custom-orders.thanks', $customOrder->reference)
            ->with('status', 'Encomenda registrada! Em breve enviamos o seu orçamento. 🎂');
    }

    /** Confirmação simpática após enviar a encomenda (bind por reference). */
    public function thanks(CustomOrder $customOrder): View
    {
        return view('custom-orders.thanks', compact('customOrder'));
    }

    /** Encomendas abertas pelo cliente logado. */
    public function index(): View
    {
        $customOrders = auth()->user()->customOrders()
            ->latest()
            ->paginate(10);

        return view('custom-orders.index', compact('customOrders'));
    }

    /** Detalhe de uma encomenda do próprio cliente (ou do root). */
    public function show(CustomOrder $customOrder): View
    {
        abort_unless($customOrder->user_id === auth()->id() || auth()->user()->isRoot(), 403);

        $customOrder->load(['product', 'kit']);

        return view('custom-orders.show', compact('customOrder'));
    }
}
