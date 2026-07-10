<?php

namespace Database\Seeders;

use App\Models\Category;
use Illuminate\Database\Seeder;
use Illuminate\Support\Str;

class CategorySeeder extends Seeder
{
    /**
     * Árvore da vitrine da doceria.
     * Nome da raiz => [emoji, cor de destaque (hex), [subcategorias...]]
     *
     * As paletas seguem os tokens da marca: framboesa, caramelo e pistache.
     */
    private array $tree = [
        'Docinhos de festa' => ['🍬', '#be123c', ['Brigadeiros', 'Beijinhos & tradicionais', 'Docinhos gourmet']],
        'Bolos & tortas' => ['🎂', '#b45309', ['Bolos caseiros', 'Bolos decorados', 'Tortas']],
        'Salgados' => ['🥐', '#d97706', ['Salgados fritos', 'Salgados assados', 'Salgados especiais']],
        'Biscoitos & bolachas' => ['🍪', '#a16207', []],
        'Bebidas' => ['🥤', '#4d7c0f', []],
        'Cestas & presentes' => ['🎁', '#9d174d', []],
        'Especiais da estação' => ['🧁', '#7c3aed', []],
    ];

    public function run(): void
    {
        $rootPos = 0;

        foreach ($this->tree as $name => [$icon, $accent, $children]) {
            $root = Category::create([
                'name' => $name,
                'slug' => Str::slug($name),
                'icon' => $icon,
                'accent' => $accent,
                'description' => 'Tudo em ' . mb_strtolower($name) . ' da Semente Doce, feito fresquinho na hora.',
                'position' => $rootPos++,
                'is_active' => true,
            ]);

            $childPos = 0;
            foreach ($children as $child) {
                Category::create([
                    'parent_id' => $root->id,
                    'name' => $child,
                    'slug' => Str::slug($child),
                    'icon' => $icon,
                    'accent' => $accent,
                    'position' => $childPos++,
                    'is_active' => true,
                ]);
            }
        }
    }
}
