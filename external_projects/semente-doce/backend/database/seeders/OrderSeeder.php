<?php

namespace Database\Seeders;

use App\Models\DeliveryZone;
use App\Models\Kit;
use App\Models\Order;
use App\Models\OrderItem;
use App\Models\Product;
use App\Models\User;
use Illuminate\Database\Seeder;

class OrderSeeder extends Seeder
{
    /**
     * Pedidos de demonstração do cliente. Cada item é:
     *   ['product' => TÍTULO, 'qty' => n]  ou  ['kit' => NOME, 'qty' => n].
     */
    private function orders(): array
    {
        return [
            [
                'status' => 'delivered', 'fulfillment' => 'delivery', 'neighborhood' => 'Jardins',
                'days_ago' => 8, 'notes' => 'Deixar na portaria, por favor.',
                'items' => [
                    ['product' => 'Torta de Limão', 'qty' => 2],
                    ['product' => 'Cookie com Gotas de Chocolate', 'qty' => 1],
                    ['product' => 'Suco Natural de Laranja', 'qty' => 2],
                ],
            ],
            [
                'status' => 'preparing', 'fulfillment' => 'delivery', 'neighborhood' => 'Centro',
                'days_ago' => 1, 'notes' => null,
                'items' => [
                    ['product' => 'Coxinha de Frango', 'qty' => 1],
                    ['product' => 'Brigadeiro Gourmet Tradicional', 'qty' => 1],
                ],
            ],
            [
                'status' => 'paid', 'fulfillment' => 'pickup', 'neighborhood' => null,
                'days_ago' => 2, 'scheduled_in' => 1, 'notes' => 'Retiro no fim da tarde.',
                'items' => [
                    ['product' => 'Bolo Vulcão de Brigadeiro', 'qty' => 1],
                    ['product' => 'Bolo no Pote', 'qty' => 6],
                ],
            ],
            [
                'status' => 'delivered', 'fulfillment' => 'delivery', 'neighborhood' => 'Boa Vista',
                'days_ago' => 15, 'notes' => null,
                'items' => [
                    ['product' => 'Kibe Frito', 'qty' => 1],
                    ['product' => 'Bolinha de Queijo', 'qty' => 1],
                ],
            ],
            [
                'status' => 'preparing', 'fulfillment' => 'delivery', 'neighborhood' => 'Vila Nova',
                'days_ago' => 3, 'notes' => 'Aniversário da minha mãe ❤️',
                'items' => [
                    ['kit' => 'Kit Chá da Tarde', 'qty' => 1],
                    ['product' => 'Café Coado da Casa', 'qty' => 2],
                ],
            ],
        ];
    }

    public function run(): void
    {
        $customer = User::where('email', 'cliente@sementedoce.com.br')->first();
        if (! $customer) {
            return;
        }

        $products = Product::with('images')->get()->keyBy('title');
        $kits = Kit::all()->keyBy('name');

        $freeAbove = (float) config('delivery.free_above', 150);
        $defaultFee = (float) config('delivery.default_fee', 12.90);

        $ref = 1001;

        foreach ($this->orders() as $data) {
            $createdAt = now()->subDays($data['days_ago']);

            $order = Order::create([
                'reference' => 'SD-' . str_pad((string) $ref++, 5, '0', STR_PAD_LEFT),
                'user_id' => $customer->id,
                'buyer_name' => $customer->name,
                'buyer_email' => $customer->email,
                'buyer_phone' => '(11) 97777-0002',
                'fulfillment_type' => $data['fulfillment'],
                'delivery_address' => $data['fulfillment'] === 'delivery' ? 'Rua das Acácias, 250 — apto 42' : null,
                'delivery_neighborhood' => $data['neighborhood'],
                'scheduled_for' => isset($data['scheduled_in']) ? now()->addDays($data['scheduled_in'])->toDateString() : null,
                'notes' => $data['notes'],
                'status' => $data['status'],
                'payment_status' => 'approved',
                'payment_method' => 'simulado',
                'paid_at' => $createdAt,
            ]);

            $subtotal = 0.0;
            foreach ($data['items'] as $item) {
                if (isset($item['kit'])) {
                    $kit = $kits->get($item['kit']);
                    if (! $kit) {
                        continue;
                    }
                    $line = (float) $kit->price * $item['qty'];
                    $subtotal += $line;

                    OrderItem::create([
                        'order_id' => $order->id,
                        'product_id' => null,
                        'kit_id' => $kit->id,
                        'title' => $kit->name,
                        'image_path' => $kit->image_url,
                        'options_summary' => null,
                        'price' => $kit->price,
                        'qty' => $item['qty'],
                        'line_total' => $line,
                    ]);
                } else {
                    $product = $products->get($item['product']);
                    if (! $product) {
                        continue;
                    }
                    $line = (float) $product->price * $item['qty'];
                    $subtotal += $line;

                    OrderItem::create([
                        'order_id' => $order->id,
                        'product_id' => $product->id,
                        'kit_id' => null,
                        'title' => $product->title,
                        'image_path' => $product->primary_image_url,
                        'options_summary' => null,
                        'price' => $product->price,
                        'qty' => $item['qty'],
                        'line_total' => $line,
                    ]);
                }
            }

            $deliveryFee = $this->deliveryFee($data['fulfillment'], $data['neighborhood'], $subtotal, $freeAbove, $defaultFee);

            $order->forceFill([
                'subtotal' => $subtotal,
                'delivery_fee' => $deliveryFee,
                'total' => $subtotal + $deliveryFee,
                'created_at' => $createdAt,
                'updated_at' => $createdAt,
            ])->save();
        }
    }

    private function deliveryFee(string $fulfillment, ?string $neighborhood, float $subtotal, float $freeAbove, float $defaultFee): float
    {
        if ($fulfillment === 'pickup') {
            return 0.0;
        }
        if ($freeAbove && $subtotal >= $freeAbove) {
            return 0.0;
        }

        return (float) (DeliveryZone::matchNeighborhood($neighborhood)?->fee ?? $defaultFee);
    }
}
