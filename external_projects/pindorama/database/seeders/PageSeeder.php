<?php

namespace Database\Seeders;

use App\Models\Page;
use Illuminate\Database\Seeder;

class PageSeeder extends Seeder
{
    public function run(): void
    {
        $pages = [
            [
                'slug' => 'central-de-ajuda',
                'title' => 'Central de ajuda',
                'body' => "Precisa de ajuda? Estamos aqui.\n\n"
                    . "A Pindorama conecta você a terapeutas de práticas integrativas — acupuntura, reiki, "
                    . "ayurveda, massoterapia e mais. Escolha um terapeuta, um serviço e um horário, e agende "
                    . "com pagamento online.\n\nDúvidas? Fale com a gente pela página de contato.",
            ],
            [
                'slug' => 'trocas-e-devolucoes',
                'title' => 'Cancelamentos e reembolsos',
                'body' => "Você pode cancelar um agendamento pela página \"Meus agendamentos\" enquanto ele "
                    . "estiver aguardando confirmação ou confirmado.\n\nPolíticas de reembolso podem variar por "
                    . "terapeuta — em caso de dúvida, fale diretamente com o profissional.",
            ],
            [
                'slug' => 'privacidade',
                'title' => 'Privacidade',
                'body' => "Levamos sua privacidade a sério. Seus dados são usados apenas para viabilizar os "
                    . "agendamentos e a comunicação com os terapeutas, e nunca são vendidos a terceiros.",
            ],
        ];

        foreach ($pages as $page) {
            Page::updateOrCreate(['slug' => $page['slug']], $page);
        }
    }
}
