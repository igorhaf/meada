<?php

namespace Database\Seeders;

use App\Models\CustomOrder;
use App\Models\Kit;
use App\Models\Product;
use App\Models\User;
use Illuminate\Database\Seeder;

class CustomOrderSeeder extends Seeder
{
    /**
     * Encomendas de demonstração cobrindo todas as colunas do board:
     * requested → quoted → confirmed → producing → ready → delivered.
     */
    private function customOrders(): array
    {
        return [
            [
                'status' => 'requested',
                'title' => 'Bolo de aniversário 2 andares — tema Jardim Encantado',
                'description' => 'Bolo de dois andares para o aniversário de 5 anos da minha filha. '
                    . 'Tema jardim encantado, com flores e borboletas. Massa de baunilha, recheio de ninho com morango.',
                'customer_name' => 'Juliana Prado', 'customer_phone' => '(11) 96666-1010', 'customer_email' => 'juliana@example.com',
                'quantity' => null, 'flavor' => 'Baunilha com ninho e morango', 'message_on_item' => 'Parabéns, Alice!',
                'fulfillment' => 'delivery', 'delivery_address' => 'Rua das Hortênsias, 88 — Jardins',
                'event_in' => 12, 'product' => 'Bolo Decorado 2 Andares', 'kit' => null, 'link_customer' => true,
                'created_days_ago' => 1,
            ],
            [
                'status' => 'quoted',
                'title' => 'Mesa de doces para casamento (200 docinhos)',
                'description' => 'Casamento no salão de festas, 120 convidados. Queríamos uma mesa de docinhos '
                    . 'sortidos (brigadeiro, beijinho, pistache) e a montagem no local.',
                'customer_name' => 'Rafael e Bianca', 'customer_phone' => '(11) 95555-2020', 'customer_email' => 'rafabia@example.com',
                'quantity' => 200, 'flavor' => 'Sortido gourmet', 'message_on_item' => null,
                'fulfillment' => 'delivery', 'delivery_address' => 'Espaço Villa Real — Salão 2',
                'event_in' => 25, 'product' => null, 'kit' => null, 'link_customer' => false,
                'quoted_price' => 480.00, 'quoted_days_ago' => 1,
                'admin_notes' => 'Inclui 200 docinhos gourmet sortidos + montagem e decoração da mesa no local.',
                'created_days_ago' => 3,
            ],
            [
                'status' => 'confirmed',
                'title' => '100 salgados assados + 50 coxinhas para reunião',
                'description' => 'Reunião de trabalho na sexta. Precisamos de salgados assados sortidos e coxinhas, '
                    . 'entrega no escritório às 14h.',
                'customer_name' => 'Tech Solutions Ltda', 'customer_phone' => '(11) 94444-3030', 'customer_email' => 'compras@techsolutions.example',
                'quantity' => 150, 'flavor' => 'Sortido assado', 'message_on_item' => null,
                'fulfillment' => 'delivery', 'delivery_address' => 'Av. Paulista, 1000 — 12º andar',
                'event_in' => 6, 'product' => 'Salgado Assado Sortido', 'kit' => null, 'link_customer' => false,
                'quoted_price' => 220.00, 'quoted_days_ago' => 5, 'confirmed_days_ago' => 4,
                'admin_notes' => 'Confirmado por telefone. Entrega agendada para 14h em ponto.',
                'created_days_ago' => 7,
            ],
            [
                'status' => 'producing',
                'title' => 'Bolo personalizado tema futebol',
                'description' => 'Bolo redondo tema do time do coração para o aniversário do meu filho. '
                    . 'Massa de baunilha e recheio de brigadeiro.',
                'customer_name' => 'Cliente Demo', 'customer_phone' => '(11) 97777-0002', 'customer_email' => 'cliente@sementedoce.com.br',
                'quantity' => 1, 'flavor' => 'Baunilha com brigadeiro', 'message_on_item' => 'Feliz aniversário, Théo!',
                'fulfillment' => 'pickup', 'delivery_address' => null,
                'event_in' => 2, 'product' => 'Bolo Personalizado Infantil', 'kit' => null, 'link_customer' => true,
                'quoted_price' => 180.00, 'quoted_days_ago' => 6, 'confirmed_days_ago' => 5,
                'admin_notes' => 'Em produção — decoração com escudo do time.',
                'created_days_ago' => 8,
            ],
            [
                'status' => 'ready',
                'title' => 'Kit Festa Completa para 30 pessoas',
                'description' => 'Festa de confraternização em casa. Fechamos o Kit Festa Completa com um docinho extra.',
                'customer_name' => 'Marina Costa', 'customer_phone' => '(11) 93333-4040', 'customer_email' => 'marina@example.com',
                'quantity' => 1, 'flavor' => null, 'message_on_item' => null,
                'fulfillment' => 'pickup', 'delivery_address' => null,
                'event_in' => 1, 'product' => null, 'kit' => 'Kit Festa Completa', 'link_customer' => false,
                'quoted_price' => 340.00, 'quoted_days_ago' => 4, 'confirmed_days_ago' => 3,
                'admin_notes' => 'Tudo pronto! Retirada combinada para amanhã de manhã.',
                'created_days_ago' => 6,
            ],
            [
                'status' => 'delivered',
                'title' => 'Ovo de Páscoa personalizado 500g',
                'description' => 'Ovo de colher trufado de 500g, embalagem de presente com laço.',
                'customer_name' => 'Cliente Demo', 'customer_phone' => '(11) 97777-0002', 'customer_email' => 'cliente@sementedoce.com.br',
                'quantity' => 1, 'flavor' => 'Chocolate ao leite trufado', 'message_on_item' => 'Feliz Páscoa!',
                'fulfillment' => 'delivery', 'delivery_address' => 'Rua das Acácias, 250 — apto 42',
                'event_in' => -6, 'product' => 'Ovo de Páscoa Trufado', 'kit' => null, 'link_customer' => true,
                'quoted_price' => 95.00, 'quoted_days_ago' => 20, 'confirmed_days_ago' => 18,
                'admin_notes' => 'Entregue com sucesso. Cliente adorou!',
                'created_days_ago' => 22,
            ],
        ];
    }

    public function run(): void
    {
        $customer = User::where('email', 'cliente@sementedoce.com.br')->first();
        $products = Product::all()->keyBy('title');
        $kits = Kit::all()->keyBy('name');

        $ref = 501;

        foreach ($this->customOrders() as $data) {
            $createdAt = now()->subDays($data['created_days_ago']);

            $custom = CustomOrder::create([
                'reference' => 'ENC-' . str_pad((string) $ref++, 4, '0', STR_PAD_LEFT),
                'user_id' => ($data['link_customer'] && $customer) ? $customer->id : null,
                'product_id' => isset($data['product']) ? optional($products->get($data['product']))->id : null,
                'kit_id' => isset($data['kit']) ? optional($kits->get($data['kit']))->id : null,
                'customer_name' => $data['customer_name'],
                'customer_phone' => $data['customer_phone'],
                'customer_email' => $data['customer_email'],
                'title' => $data['title'],
                'description' => $data['description'],
                'quantity' => $data['quantity'] ?? null,
                'flavor' => $data['flavor'] ?? null,
                'message_on_item' => $data['message_on_item'] ?? null,
                'reference_photo_url' => null,
                'fulfillment_type' => $data['fulfillment'],
                'delivery_address' => $data['delivery_address'] ?? null,
                'event_date' => now()->addDays($data['event_in'])->toDateString(),
                'status' => $data['status'],
                'quoted_price' => $data['quoted_price'] ?? null,
                'admin_notes' => $data['admin_notes'] ?? null,
                'quoted_at' => isset($data['quoted_days_ago']) ? now()->subDays($data['quoted_days_ago']) : null,
                'confirmed_at' => isset($data['confirmed_days_ago']) ? now()->subDays($data['confirmed_days_ago']) : null,
            ]);

            $custom->forceFill([
                'created_at' => $createdAt,
                'updated_at' => $createdAt,
            ])->save();
        }
    }
}
