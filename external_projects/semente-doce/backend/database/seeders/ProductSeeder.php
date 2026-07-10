<?php

namespace Database\Seeders;

use App\Models\Category;
use App\Models\Product;
use App\Models\ProductImage;
use App\Models\ProductOption;
use App\Models\ProductOptionGroup;
use Illuminate\Database\Seeder;
use Illuminate\Support\Str;

class ProductSeeder extends Seeder
{
    private int $counter = 0;

    /**
     * Catálogo da doceria por slug da categoria (folha). Cada produto usa as chaves:
     * title, unit, price, compare, flavor, serves, allergens, min_qty, made_to_order,
     * lead_time, prep, featured, rating, reviews, sold, options.
     *
     * "options" é uma lista de grupos: [name, min, max, required, options[[name, delta]]].
     */
    private function catalog(): array
    {
        return [
            /* ---------------------------------------------------- Docinhos de festa */
            'brigadeiros' => [
                [
                    'title' => 'Brigadeiro Gourmet Tradicional', 'unit' => 'cento', 'price' => 89.90, 'compare' => 109.90,
                    'flavor' => 'Chocolate belga', 'serves' => 'Rende ~100 docinhos', 'allergens' => 'leite',
                    'min_qty' => 1, 'featured' => true, 'rating' => 4.9, 'reviews' => 320, 'sold' => 1450,
                    'options' => [
                        ['Sabores', 1, 4, true, [
                            ['Brigadeiro tradicional', 0], ['Beijinho de coco', 0], ['Leite Ninho', 0],
                            ['Ninho com Nutella', 6.00], ['Pistache', 8.00], ['Maracujá', 4.00],
                        ]],
                    ],
                ],
                ['title' => 'Beijinho de Coco', 'unit' => 'cento', 'price' => 84.90, 'flavor' => 'Coco queimado', 'serves' => 'Rende ~100 docinhos', 'allergens' => 'leite, coco', 'min_qty' => 1, 'sold' => 680],
                ['title' => 'Brigadeiro de Ninho com Nutella', 'unit' => 'cento', 'price' => 119.90, 'flavor' => 'Leite Ninho e Nutella', 'serves' => 'Rende ~100 docinhos', 'allergens' => 'leite, avelã', 'min_qty' => 1, 'featured' => true, 'sold' => 540],
                ['title' => 'Casadinho Tradicional', 'unit' => 'cento', 'price' => 92.90, 'flavor' => 'Brigadeiro e beijinho', 'serves' => 'Rende ~100 docinhos', 'allergens' => 'leite, coco', 'min_qty' => 1, 'sold' => 210],
            ],
            'beijinhos-tradicionais' => [
                ['title' => 'Beijinho Tradicional', 'unit' => 'cento', 'price' => 82.90, 'flavor' => 'Coco com cravo', 'serves' => 'Rende ~100 docinhos', 'allergens' => 'leite, coco', 'min_qty' => 1, 'sold' => 430],
                ['title' => 'Cajuzinho', 'unit' => 'cento', 'price' => 88.90, 'flavor' => 'Amendoim torrado', 'serves' => 'Rende ~100 docinhos', 'allergens' => 'amendoim', 'min_qty' => 1, 'sold' => 190],
                ['title' => 'Olho de Sogra', 'unit' => 'cento', 'price' => 96.90, 'flavor' => 'Ameixa com coco', 'serves' => 'Rende ~100 docinhos', 'allergens' => 'leite, coco, sulfitos', 'min_qty' => 1, 'sold' => 120],
                ['title' => 'Bicho de Pé', 'unit' => 'cento', 'price' => 90.90, 'flavor' => 'Morango', 'serves' => 'Rende ~100 docinhos', 'allergens' => 'leite', 'min_qty' => 1, 'sold' => 150],
            ],
            'docinhos-gourmet' => [
                ['title' => 'Docinho de Pistache', 'unit' => 'cento', 'price' => 129.90, 'flavor' => 'Pistache siciliano', 'serves' => 'Rende ~100 docinhos', 'allergens' => 'leite, pistache', 'min_qty' => 1, 'featured' => true, 'rating' => 4.9, 'sold' => 260],
                ['title' => 'Brigadeiro de Maracujá', 'unit' => 'cento', 'price' => 99.90, 'flavor' => 'Maracujá com chocolate branco', 'serves' => 'Rende ~100 docinhos', 'allergens' => 'leite', 'min_qty' => 1, 'sold' => 175],
                ['title' => 'Trufa de Chocolate Belga', 'unit' => 'caixa', 'price' => 48.90, 'compare' => 59.90, 'flavor' => 'Chocolate 70%', 'serves' => 'Caixa com 12 trufas', 'allergens' => 'leite, soja', 'min_qty' => 1, 'sold' => 340],
                ['title' => 'Cake Pop Decorado', 'unit' => 'unidade', 'price' => 7.90, 'flavor' => 'Baunilha com cobertura colorida', 'serves' => 'Unidade', 'allergens' => 'glúten, leite', 'min_qty' => 10, 'sold' => 400],
            ],

            /* -------------------------------------------------------- Bolos & tortas */
            'bolos-caseiros' => [
                [
                    'title' => 'Bolo de Cenoura com Chocolate', 'unit' => 'unidade', 'price' => 62.90,
                    'flavor' => 'Cenoura com calda de chocolate', 'serves' => 'Serve 12 fatias', 'allergens' => 'glúten, ovo, leite',
                    'min_qty' => 1, 'featured' => true, 'rating' => 4.8, 'sold' => 520,
                    'options' => [
                        ['Cobertura', 0, 1, false, [
                            ['Calda de chocolate', 0], ['Brigadeiro cremoso', 6.00], ['Sem cobertura', 0],
                        ]],
                    ],
                ],
                ['title' => 'Bolo de Fubá Cremoso', 'unit' => 'unidade', 'price' => 48.90, 'flavor' => 'Fubá com erva-doce', 'serves' => 'Serve 10 fatias', 'allergens' => 'glúten, ovo, leite', 'min_qty' => 1, 'sold' => 300],
                ['title' => 'Bolo Vulcão de Brigadeiro', 'unit' => 'unidade', 'price' => 69.90, 'flavor' => 'Chocolate com brigadeiro escorrendo', 'serves' => 'Serve 12 fatias', 'allergens' => 'glúten, ovo, leite', 'min_qty' => 1, 'sold' => 410],
                ['title' => 'Bolo no Pote', 'unit' => 'unidade', 'price' => 14.90, 'flavor' => 'Ninho com morango', 'serves' => 'Individual (300ml)', 'allergens' => 'glúten, ovo, leite', 'min_qty' => 6, 'featured' => true, 'sold' => 890],
            ],
            'bolos-decorados' => [
                [
                    'title' => 'Bolo Decorado 2 Andares', 'unit' => 'unidade', 'price' => 259.90,
                    'flavor' => 'Sob encomenda', 'serves' => 'Serve 30 a 40 pessoas', 'allergens' => 'glúten, ovo, leite',
                    'min_qty' => 1, 'made_to_order' => true, 'lead_time' => 5, 'featured' => true, 'rating' => 5.0, 'reviews' => 48, 'sold' => 90,
                    'options' => [
                        ['Massa', 1, 1, true, [['Baunilha', 0], ['Chocolate', 0], ['Red Velvet', 15.00]]],
                        ['Recheio', 1, 1, true, [['Brigadeiro', 0], ['Ninho com morango', 12.00], ['Doce de leite', 8.00], ['Quatro leites', 10.00]]],
                        ['Cobertura', 0, 1, false, [['Chantilly', 0], ['Pasta americana', 30.00], ['Ganache', 18.00]]],
                    ],
                ],
                ['title' => 'Naked Cake de Frutas Vermelhas', 'unit' => 'unidade', 'price' => 179.90, 'flavor' => 'Baunilha com frutas vermelhas', 'serves' => 'Serve 20 fatias', 'allergens' => 'glúten, ovo, leite', 'min_qty' => 1, 'made_to_order' => true, 'lead_time' => 3, 'sold' => 65],
                ['title' => 'Bolo Personalizado Infantil', 'unit' => 'unidade', 'price' => 199.90, 'flavor' => 'Tema à escolha', 'serves' => 'Serve 25 pessoas', 'allergens' => 'glúten, ovo, leite', 'min_qty' => 1, 'made_to_order' => true, 'lead_time' => 4, 'sold' => 70],
            ],
            'tortas' => [
                ['title' => 'Torta Holandesa', 'unit' => 'unidade', 'price' => 79.90, 'compare' => 94.90, 'flavor' => 'Chocolate com biscoito', 'serves' => 'Serve 16 fatias', 'allergens' => 'glúten, leite', 'min_qty' => 1, 'sold' => 230],
                ['title' => 'Torta de Limão', 'unit' => 'fatia', 'price' => 12.90, 'flavor' => 'Limão siciliano com merengue', 'serves' => 'Fatia individual', 'allergens' => 'glúten, ovo, leite', 'min_qty' => 1, 'sold' => 310],
                ['title' => 'Cheesecake de Frutas Vermelhas', 'unit' => 'fatia', 'price' => 15.90, 'flavor' => 'Cream cheese com calda artesanal', 'serves' => 'Fatia individual', 'allergens' => 'glúten, leite', 'min_qty' => 1, 'featured' => true, 'sold' => 280],
            ],

            /* -------------------------------------------------------------- Salgados */
            'salgados-fritos' => [
                [
                    'title' => 'Coxinha de Frango', 'unit' => 'cento', 'price' => 84.90,
                    'flavor' => 'Frango desfiado', 'serves' => 'Rende ~100 salgadinhos', 'allergens' => 'glúten, leite',
                    'min_qty' => 1, 'featured' => true, 'rating' => 4.9, 'sold' => 1300,
                    'options' => [
                        ['Recheio', 1, 1, true, [['Frango', 0], ['Frango com catupiry', 10.00], ['Carne seca', 15.00]]],
                    ],
                ],
                ['title' => 'Kibe Frito', 'unit' => 'cento', 'price' => 82.90, 'flavor' => 'Carne com trigo', 'serves' => 'Rende ~100 salgadinhos', 'allergens' => 'glúten', 'min_qty' => 1, 'sold' => 460],
                ['title' => 'Bolinha de Queijo', 'unit' => 'cento', 'price' => 79.90, 'flavor' => 'Queijo muçarela', 'serves' => 'Rende ~100 salgadinhos', 'allergens' => 'glúten, leite', 'min_qty' => 1, 'sold' => 520],
                ['title' => 'Salgadinho Festa Sortido', 'unit' => 'unidade', 'price' => 1.30, 'flavor' => 'Sortido frito', 'serves' => 'Vendido por unidade (mín. 50)', 'allergens' => 'glúten, leite', 'min_qty' => 50, 'featured' => true, 'sold' => 3200],
            ],
            'salgados-assados' => [
                ['title' => 'Empada de Frango', 'unit' => 'duzia', 'price' => 34.90, 'flavor' => 'Frango com palmito', 'serves' => 'Dúzia', 'allergens' => 'glúten, leite, ovo', 'min_qty' => 1, 'sold' => 380],
                ['title' => 'Esfiha de Carne Aberta', 'unit' => 'cento', 'price' => 92.90, 'flavor' => 'Carne temperada', 'serves' => 'Rende ~100 esfihas', 'allergens' => 'glúten', 'min_qty' => 1, 'sold' => 240],
                ['title' => 'Enroladinho de Salsicha', 'unit' => 'cento', 'price' => 78.90, 'flavor' => 'Massa folhada', 'serves' => 'Rende ~100 salgadinhos', 'allergens' => 'glúten, leite', 'min_qty' => 1, 'sold' => 300],
                [
                    'title' => 'Salgado Assado Sortido', 'unit' => 'cento', 'price' => 96.90,
                    'flavor' => 'Sortido assado', 'serves' => 'Rende ~100 salgados assados', 'allergens' => 'glúten, leite, ovo',
                    'min_qty' => 1, 'featured' => true, 'sold' => 610,
                    'options' => [
                        ['Sabores', 1, 5, true, [
                            ['Empada de frango', 0], ['Enroladinho de salsicha', 0], ['Esfiha de carne', 0],
                            ['Mini quiche', 0], ['Mini pizza', 0],
                        ]],
                    ],
                ],
            ],
            'salgados-especiais' => [
                ['title' => 'Mini Quiche Sortida', 'unit' => 'duzia', 'price' => 42.90, 'flavor' => 'Alho-poró, queijo e tomate seco', 'serves' => 'Dúzia', 'allergens' => 'glúten, leite, ovo', 'min_qty' => 1, 'sold' => 160],
                ['title' => 'Torta Salgada de Frango', 'unit' => 'unidade', 'price' => 58.90, 'flavor' => 'Frango com requeijão', 'serves' => 'Serve 12 pessoas', 'allergens' => 'glúten, leite, ovo', 'min_qty' => 1, 'sold' => 130],
            ],

            /* --------------------------------------------------- Biscoitos & bolachas */
            'biscoitos-bolachas' => [
                ['title' => 'Biscoito Amanteigado', 'unit' => 'caixa', 'price' => 28.90, 'flavor' => 'Manteiga com baunilha', 'serves' => 'Caixa com 300g', 'allergens' => 'glúten, leite, ovo', 'min_qty' => 1, 'sold' => 420],
                ['title' => 'Palha Italiana', 'unit' => 'caixa', 'price' => 34.90, 'flavor' => 'Chocolate com biscoito', 'serves' => 'Caixa com 12 fatias', 'allergens' => 'glúten, leite', 'min_qty' => 1, 'sold' => 350],
                ['title' => 'Cookie com Gotas de Chocolate', 'unit' => 'duzia', 'price' => 32.90, 'flavor' => 'Baunilha com gotas belgas', 'serves' => 'Dúzia', 'allergens' => 'glúten, leite, ovo', 'min_qty' => 1, 'featured' => true, 'sold' => 560],
                ['title' => 'Alfajor de Doce de Leite', 'unit' => 'unidade', 'price' => 8.90, 'flavor' => 'Doce de leite com chocolate', 'serves' => 'Unidade', 'allergens' => 'glúten, leite', 'min_qty' => 6, 'sold' => 480],
                ['title' => 'Pão de Mel Recheado', 'unit' => 'unidade', 'price' => 9.90, 'flavor' => 'Mel com doce de leite', 'serves' => 'Unidade', 'allergens' => 'glúten, leite, ovo', 'min_qty' => 6, 'sold' => 520],
            ],

            /* ---------------------------------------------------------------- Bebidas */
            'bebidas' => [
                ['title' => 'Suco Natural de Laranja', 'unit' => 'copo', 'price' => 9.90, 'flavor' => 'Laranja pera', 'serves' => 'Copo 400ml', 'allergens' => null, 'min_qty' => 1, 'sold' => 640],
                ['title' => 'Limonada Suíça', 'unit' => 'copo', 'price' => 11.90, 'flavor' => 'Limão com leite condensado', 'serves' => 'Copo 400ml', 'allergens' => 'leite', 'min_qty' => 1, 'sold' => 410],
                ['title' => 'Chocolate Quente Cremoso', 'unit' => 'copo', 'price' => 13.90, 'flavor' => 'Chocolate meio amargo', 'serves' => 'Copo 300ml', 'allergens' => 'leite', 'min_qty' => 1, 'featured' => true, 'sold' => 300],
                ['title' => 'Café Coado da Casa', 'unit' => 'copo', 'price' => 6.90, 'flavor' => 'Blend artesanal', 'serves' => 'Xícara 150ml', 'allergens' => null, 'min_qty' => 1, 'sold' => 720],
                ['title' => 'Refrigerante Lata', 'unit' => 'unidade', 'price' => 6.00, 'flavor' => 'Diversos sabores', 'serves' => 'Lata 350ml', 'allergens' => null, 'min_qty' => 1, 'sold' => 500],
            ],

            /* ------------------------------------------------------ Cestas & presentes */
            'cestas-presentes' => [
                [
                    'title' => 'Cesta Café da Manhã', 'unit' => 'caixa', 'price' => 89.90,
                    'flavor' => 'Bolo, pães, sucos e frutas', 'serves' => 'Para 2 pessoas', 'allergens' => 'glúten, leite, ovo',
                    'min_qty' => 1, 'made_to_order' => true, 'lead_time' => 1, 'featured' => true, 'rating' => 4.9, 'sold' => 210,
                    'options' => [
                        ['Tamanho', 1, 1, true, [['Para 1 pessoa', 0], ['Para 2 pessoas', 35.00], ['Família (4 pessoas)', 75.00]]],
                    ],
                ],
                ['title' => 'Caixa de Bombons Artesanais', 'unit' => 'caixa', 'price' => 54.90, 'compare' => 64.90, 'flavor' => 'Bombons sortidos', 'serves' => 'Caixa com 16 bombons', 'allergens' => 'leite, soja, amendoim', 'min_qty' => 1, 'sold' => 340],
                ['title' => 'Box Presente Doce', 'unit' => 'caixa', 'price' => 74.90, 'flavor' => 'Docinhos, cookies e cartão', 'serves' => 'Caixa presente', 'allergens' => 'glúten, leite', 'min_qty' => 1, 'sold' => 150],
            ],

            /* --------------------------------------------------- Especiais da estação */
            'especiais-da-estacao' => [
                ['title' => 'Ovo de Páscoa Trufado', 'unit' => 'unidade', 'price' => 79.90, 'flavor' => 'Chocolate ao leite trufado', 'serves' => '350g', 'allergens' => 'leite, soja', 'min_qty' => 1, 'made_to_order' => true, 'lead_time' => 3, 'sold' => 90],
                ['title' => 'Panetone Trufado', 'unit' => 'unidade', 'price' => 69.90, 'flavor' => 'Frutas e chocolate', 'serves' => '500g', 'allergens' => 'glúten, leite, ovo', 'min_qty' => 1, 'made_to_order' => true, 'lead_time' => 2, 'sold' => 60],
                ['title' => 'Bolo Junino de Milho', 'unit' => 'unidade', 'price' => 46.90, 'flavor' => 'Milho verde cremoso', 'serves' => 'Serve 10 fatias', 'allergens' => 'glúten, leite, ovo', 'min_qty' => 1, 'sold' => 140],
            ],
        ];
    }

    public function run(): void
    {
        $categoryIds = Category::pluck('id', 'slug');

        foreach ($this->catalog() as $slug => $products) {
            $categoryId = $categoryIds[$slug] ?? null;
            if (! $categoryId) {
                continue;
            }

            $position = 0;
            foreach ($products as $data) {
                $this->createProduct($categoryId, $data, $position++);
            }
        }
    }

    private function createProduct(int $categoryId, array $data, int $position): void
    {
        $this->counter++;
        $title = $data['title'];
        $slug = Str::slug($title);

        $product = Product::create([
            'category_id' => $categoryId,
            'title' => $title,
            'slug' => $slug,
            'description' => $this->description($data),
            'unit' => $data['unit'],
            'flavor' => $data['flavor'] ?? null,
            'serves' => $data['serves'] ?? null,
            'contains_allergens' => $data['allergens'] ?? null,
            'min_qty' => $data['min_qty'] ?? 1,
            'is_made_to_order' => $data['made_to_order'] ?? false,
            'lead_time_days' => $data['lead_time'] ?? null,
            'prep_minutes' => ($data['made_to_order'] ?? false) ? null : ($data['prep'] ?? 30),
            'price' => $data['price'],
            'compare_at_price' => $data['compare'] ?? null,
            'sku' => 'SD-' . str_pad((string) $this->counter, 4, '0', STR_PAD_LEFT),
            'is_active' => true,
            'is_featured' => $data['featured'] ?? false,
            'position' => $position,
            'rating' => $data['rating'] ?? round(mt_rand(46, 50) / 10, 1),
            'reviews_count' => $data['reviews'] ?? mt_rand(15, 260),
            'sold_count' => $data['sold'] ?? mt_rand(40, 700),
        ]);

        ProductImage::create([
            'product_id' => $product->id,
            'path' => placeholder_image($product->slug, $product->title),
            'alt' => $product->title,
            'is_primary' => true,
            'position' => 0,
        ]);

        foreach ($data['options'] ?? [] as $groupPos => $group) {
            [$name, $min, $max, $required, $options] = $group;

            $optionGroup = ProductOptionGroup::create([
                'product_id' => $product->id,
                'name' => $name,
                'min_select' => $min,
                'max_select' => $max,
                'is_required' => $required,
                'position' => $groupPos,
            ]);

            foreach ($options as $optPos => [$optName, $delta]) {
                ProductOption::create([
                    'group_id' => $optionGroup->id,
                    'name' => $optName,
                    'price_delta' => $delta,
                    'is_active' => true,
                    'position' => $optPos,
                ]);
            }
        }
    }

    private function description(array $data): string
    {
        $title = $data['title'];
        $flavor = $data['flavor'] ?? null;
        $serves = $data['serves'] ?? null;

        $parts = ["{$title} feito artesanalmente na Semente Doce, com ingredientes selecionados e muito carinho."];

        if ($flavor) {
            $parts[] = "Sabor: {$flavor}.";
        }
        if ($serves) {
            $parts[] = "{$serves}.";
        }
        if ($data['made_to_order'] ?? false) {
            $dias = $data['lead_time'] ?? 2;
            $parts[] = "Item sob encomenda — faça seu pedido com pelo menos {$dias} dia(s) de antecedência.";
        } else {
            $parts[] = 'Pronta-entrega, fresquinho todos os dias.';
        }

        return implode(' ', $parts);
    }
}
