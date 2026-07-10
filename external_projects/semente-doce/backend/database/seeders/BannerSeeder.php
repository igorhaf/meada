<?php

namespace Database\Seeders;

use App\Models\Banner;
use Illuminate\Database\Seeder;

class BannerSeeder extends Seeder
{
    public function run(): void
    {
        // Gradientes framboesa → caramelo (tokens da marca).
        $hero = [
            [
                'title' => 'Encomende o doce dos seus sonhos 🎂',
                'subtitle' => 'Bolos decorados, mesas de doces e salgados sob medida para a sua festa. '
                    . 'Peça um orçamento sem compromisso.',
                'cta_label' => 'Fazer encomenda',
                'link_url' => '/encomendas',
                'bg_from' => '#be123c',
                'bg_to' => '#b45309',
            ],
            [
                'title' => 'Kits prontos para a festa 🍬',
                'subtitle' => 'Salgados, docinhos e bolo num combo só — com preço especial e economia garantida.',
                'cta_label' => 'Ver kits',
                'link_url' => '/kits',
                'bg_from' => '#e11d48',
                'bg_to' => '#d97706',
            ],
            [
                'title' => 'Ofertas fresquinhas do dia 🧁',
                'subtitle' => 'Doces e salgados pronta-entrega com precinho de dar água na boca.',
                'cta_label' => 'Ver ofertas',
                'link_url' => '/busca?sort=price_asc',
                'bg_from' => '#9d174d',
                'bg_to' => '#c2410c',
            ],
        ];

        foreach ($hero as $i => $banner) {
            Banner::create($banner + ['placement' => 'hero', 'position' => $i, 'is_active' => true]);
        }

        $strip = [
            [
                'title' => 'Brigadeiros gourmet a partir de R$ 82',
                'subtitle' => 'O cento sortido do seu jeito',
                'cta_label' => 'Provar agora',
                'link_url' => '/categoria/docinhos-de-festa',
                'bg_from' => '#be123c',
                'bg_to' => '#a16207',
            ],
            [
                'title' => 'Salgados para festa 🥐',
                'subtitle' => 'Fritos e assados, sempre crocantes',
                'cta_label' => 'Ver salgados',
                'link_url' => '/categoria/salgados',
                'bg_from' => '#d97706',
                'bg_to' => '#9d174d',
            ],
        ];

        foreach ($strip as $i => $banner) {
            Banner::create($banner + ['placement' => 'strip', 'position' => $i, 'is_active' => true]);
        }
    }
}
