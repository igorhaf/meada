<?php

namespace Database\Seeders;

use App\Models\Kit;
use App\Models\KitItem;
use App\Models\Product;
use Illuminate\Database\Seeder;
use Illuminate\Support\Str;

class KitSeeder extends Seeder
{
    /**
     * Kits montados pela doceria a partir de produtos da vitrine. O preço final é
     * arbitrado pela loja (abaixo da soma dos componentes → gera "economia").
     *
     * Cada item: ['product' => TÍTULO, 'qty' => n, 'label' => opcional]
     *         ou ['label' => TEXTO, 'qty' => n, 'unit_price' => valor]  (item avulso).
     */
    private function kits(): array
    {
        return [
            [
                'name' => 'Kit Festa Completa',
                'kit_type' => 'festa',
                'serves' => 'Serve 20 a 25 pessoas',
                'price' => 299.90,
                'is_featured' => true,
                'is_made_to_order' => true,
                'lead_time_days' => 2,
                'description' => 'O combo perfeito para a sua festa: 100 salgados assados, 100 coxinhas, '
                    . '100 brigadeiros gourmet e um bolo recheado. É só chamar a galera!',
                'items' => [
                    ['product' => 'Salgado Assado Sortido', 'qty' => 1, 'label' => '100 salgados assados sortidos'],
                    ['product' => 'Coxinha de Frango', 'qty' => 1, 'label' => '100 coxinhas de frango'],
                    ['product' => 'Brigadeiro Gourmet Tradicional', 'qty' => 1, 'label' => '100 brigadeiros gourmet'],
                    ['product' => 'Bolo Vulcão de Brigadeiro', 'qty' => 1, 'label' => '1 bolo vulcão de brigadeiro'],
                ],
            ],
            [
                'name' => 'Caixa de Brigadeiros Gourmet Sortidos',
                'kit_type' => 'presente',
                'serves' => 'Rende ~100 docinhos + 12 trufas',
                'price' => 199.90,
                'is_featured' => true,
                'is_made_to_order' => false,
                'lead_time_days' => null,
                'description' => 'Uma seleção irresistível dos nossos brigadeiros gourmet mais pedidos, '
                    . 'acompanhada de trufas de chocolate belga. Presente que derrete o coração.',
                'items' => [
                    ['product' => 'Brigadeiro Gourmet Tradicional', 'qty' => 1],
                    ['product' => 'Brigadeiro de Maracujá', 'qty' => 1],
                    ['product' => 'Trufa de Chocolate Belga', 'qty' => 1],
                ],
            ],
            [
                'name' => 'Kit Café da Manhã',
                'kit_type' => 'cafe',
                'serves' => 'Para 2 a 3 pessoas',
                'price' => 149.90,
                'is_featured' => false,
                'is_made_to_order' => true,
                'lead_time_days' => 1,
                'description' => 'Comece o dia com carinho: bolo caseiro, pães de mel, biscoitos amanteigados, '
                    . 'sucos naturais e café coado na hora.',
                'items' => [
                    ['product' => 'Bolo de Cenoura com Chocolate', 'qty' => 1],
                    ['product' => 'Pão de Mel Recheado', 'qty' => 4],
                    ['product' => 'Biscoito Amanteigado', 'qty' => 1],
                    ['product' => 'Suco Natural de Laranja', 'qty' => 2],
                    ['product' => 'Café Coado da Casa', 'qty' => 2],
                ],
            ],
            [
                'name' => 'Cesta Presente Doce',
                'kit_type' => 'presente',
                'serves' => 'Presente para uma pessoa especial',
                'price' => 124.90,
                'is_featured' => true,
                'is_made_to_order' => false,
                'lead_time_days' => null,
                'description' => 'Bombons artesanais, trufas belgas e cookies fresquinhos numa cesta linda, '
                    . 'com cartão personalizado. Para dizer "eu te amo" com doçura.',
                'items' => [
                    ['product' => 'Caixa de Bombons Artesanais', 'qty' => 1],
                    ['product' => 'Trufa de Chocolate Belga', 'qty' => 1],
                    ['product' => 'Cookie com Gotas de Chocolate', 'qty' => 1],
                    ['label' => 'Cartão personalizado com mensagem', 'qty' => 1, 'unit_price' => 5.00],
                ],
            ],
            [
                'name' => 'Kit Salgados para Reunião',
                'kit_type' => 'corporativo',
                'serves' => 'Para 15 a 20 pessoas',
                'price' => 329.90,
                'is_featured' => false,
                'is_made_to_order' => true,
                'lead_time_days' => 2,
                'description' => 'Reunião ou coffee break da empresa? Coxinhas, kibes, bolinhas de queijo, '
                    . 'mini quiches e café para todo mundo trabalhar feliz.',
                'items' => [
                    ['product' => 'Coxinha de Frango', 'qty' => 1],
                    ['product' => 'Kibe Frito', 'qty' => 1],
                    ['product' => 'Bolinha de Queijo', 'qty' => 1],
                    ['product' => 'Mini Quiche Sortida', 'qty' => 2],
                    ['product' => 'Café Coado da Casa', 'qty' => 5],
                ],
            ],
            [
                'name' => 'Kit Chá da Tarde',
                'kit_type' => 'festa',
                'serves' => 'Para 6 a 8 pessoas',
                'price' => 169.90,
                'is_featured' => false,
                'is_made_to_order' => false,
                'lead_time_days' => null,
                'description' => 'Uma tarde aconchegante com fatias de torta de limão, cookies, biscoitos '
                    . 'amanteigados e limonada suíça geladinha.',
                'items' => [
                    ['product' => 'Torta de Limão', 'qty' => 6],
                    ['product' => 'Cookie com Gotas de Chocolate', 'qty' => 1],
                    ['product' => 'Biscoito Amanteigado', 'qty' => 1],
                    ['product' => 'Limonada Suíça', 'qty' => 4],
                ],
            ],
        ];
    }

    public function run(): void
    {
        $products = Product::all()->keyBy('title');
        $position = 0;

        foreach ($this->kits() as $data) {
            $slug = Str::slug($data['name']);

            $kit = Kit::create([
                'name' => $data['name'],
                'slug' => $slug,
                'description' => $data['description'],
                'kit_type' => $data['kit_type'],
                'serves' => $data['serves'],
                'price' => $data['price'],
                'image_path' => placeholder_image($slug, $data['name']),
                'is_active' => true,
                'is_featured' => $data['is_featured'],
                'is_made_to_order' => $data['is_made_to_order'],
                'lead_time_days' => $data['lead_time_days'],
                'position' => $position++,
                'sold_count' => mt_rand(20, 260),
            ]);

            $itemPos = 0;
            foreach ($data['items'] as $item) {
                if (isset($item['product'])) {
                    $product = $products->get($item['product']);
                    if (! $product) {
                        continue;
                    }

                    KitItem::create([
                        'kit_id' => $kit->id,
                        'product_id' => $product->id,
                        'label' => $item['label'] ?? $product->title,
                        'qty' => $item['qty'],
                        'unit_price' => $product->price,
                        'position' => $itemPos++,
                    ]);
                } else {
                    KitItem::create([
                        'kit_id' => $kit->id,
                        'product_id' => null,
                        'label' => $item['label'],
                        'qty' => $item['qty'],
                        'unit_price' => $item['unit_price'],
                        'position' => $itemPos++,
                    ]);
                }
            }
        }
    }
}
