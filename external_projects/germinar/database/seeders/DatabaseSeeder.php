<?php

namespace Database\Seeders;

use App\Models\Course;
use App\Models\Practice;
use App\Models\Service;
use App\Models\Setting;
use App\Models\User;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Seed idempotente e NÃO-destrutivo: só preenche o que falta. Re-rodar
     * nunca sobrescreve senha do admin nem edições feitas pelo painel.
     */
    public function run(): void
    {
        $this->seedAdminUser();
        $this->seedSettings();
        $this->seedServices();
        $this->seedPractices();
        $this->seedCourses();
    }

    private function seedAdminUser(): void
    {
        // firstOrCreate: re-seed não reseta senha trocada depois do bootstrap.
        User::firstOrCreate(
            ['email' => 'root@germinar.com.br'],
            [
                'name' => 'Germinar',
                'password' => Hash::make('password'),
            ]
        );
    }

    private function seedSettings(): void
    {
        $settings = [
            'contact.whatsapp' => '(81) 99521-6450',
            'contact.instagram' => '@germinargestacional',
            'contact.email' => 'contato@germinar.com.br',
            'hero.kicker' => 'Do pré-natal ao puerpério',
            'hero.title' => 'Quando a mãe recebe apoio, o bebê floresce junto.',
            'hero.subtitle' => 'Acompanhamento de gestantes e puérperas, práticas integrativas e formação de doulas — encontros presenciais ou online, individuais e coletivos.',
            'hero.cta_primary' => 'Falar no WhatsApp',
            'hero.cta_secondary' => 'Ver formações',
            'hero.photo' => 'images/hero.png',
            'services.kicker' => 'O que fazemos',
            'practices.kicker' => 'Práticas integrativas',
            'practices.title' => 'Cuidado integral para o corpo e as emoções',
            'courses.kicker' => 'Cursos e treinamentos',
            'courses.title' => 'Para quem quer cuidar também',
            'about.kicker' => 'Quem somos',
            'about.title' => 'Um coletivo de doulas e terapeutas',
            'about.text' => 'A Germinar nasceu do desejo de acolher a mulher por inteiro — corpo, emoções e história. Nossa equipe reúne doulas, educadoras perinatais e terapeutas integrativas com formação e vivência em gestação, parto e puerpério.',
            'about.cta' => 'Conhecer a equipe',
            'about.photo' => 'images/equipe.png',
            'contact.title' => 'Entre em contato e venha germinar com a gente',
            'contact.subtitle' => 'Pelas redes sociais ou telefone — o primeiro passo é uma conversa.',
            'footer.text' => 'Germinar · Do pré-natal ao puerpério de 60 dias',
        ];

        // Só cria chave ausente — edições do painel sobrevivem ao re-seed.
        foreach ($settings as $key => $value) {
            if (Setting::get($key) === null) {
                Setting::set($key, $value);
            }
        }
    }

    private function seedServices(): void
    {
        $services = [
            [
                'title' => 'Acompanhamento gestacional',
                'description' => 'Do pré-natal ao puerpério, uma doula ao seu lado: preparação para o parto, plano de parto, amamentação e os primeiros 60 dias com o bebê — com escuta, presença e informação baseada em evidências.',
                'dot_color' => 'accent',
                'sort_order' => 1,
            ],
            [
                'title' => 'Práticas integrativas',
                'description' => 'Fitoterapia, aromaterapia, auriculoterapia, meditação, yoga, reflexologia e massagem — práticas seguras que acolhem o corpo e as emoções da mãe em cada fase.',
                'dot_color' => 'accent-2',
                'sort_order' => 2,
            ],
            [
                'title' => 'Cursos e formações',
                'description' => 'Formação de doulas e treinamentos para profissionais de saúde que desejam um cuidado perinatal mais humano — teoria, prática e vivência.',
                'dot_color' => 'accent',
                'sort_order' => 3,
            ],
        ];

        foreach ($services as $service) {
            Service::firstOrCreate(
                ['title' => $service['title']],
                [...$service, 'is_active' => true]
            );
        }
    }

    private function seedPractices(): void
    {
        $practices = [
            ['title' => 'Fitoterapia', 'description' => 'Plantas seguras que apoiam o bem-estar da mãe em cada fase.', 'sort_order' => 1],
            ['title' => 'Aromaterapia', 'description' => 'Aromas para relaxar, acolher e criar um ambiente de calma.', 'sort_order' => 2],
            ['title' => 'Auriculoterapia', 'description' => 'Estímulo à produção de leite e redução da ansiedade.', 'sort_order' => 3],
            ['title' => 'Meditação e Yoga', 'description' => 'Calma, consciência e conexão com o bebê.', 'sort_order' => 4],
            ['title' => 'Reflexologia', 'description' => 'Alívio de desconfortos físicos e equilíbrio hormonal.', 'sort_order' => 5],
            ['title' => 'Massagem', 'description' => 'Bem-estar físico e apoio à descida do leite.', 'sort_order' => 6],
        ];

        foreach ($practices as $practice) {
            Practice::firstOrCreate(
                ['title' => $practice['title']],
                [...$practice, 'is_active' => true]
            );
        }
    }

    private function seedCourses(): void
    {
        $courses = [
            [
                'title' => 'Formação de Doulas',
                'tag_label' => 'Formação',
                'tag_style' => 'accent',
                'description' => '120h de teoria, prática e vivência para acompanhar gestação, parto e puerpério.',
                'meta_info' => 'Presencial + online · Próxima turma em março',
                'sort_order' => 1,
            ],
            [
                'title' => 'Práticas Integrativas no Perinatal',
                'tag_label' => 'Treinamento',
                'tag_style' => 'accent-2',
                'description' => 'Para profissionais de saúde: fitoterapia, aromaterapia e auriculoterapia com segurança.',
                'meta_info' => 'Online ao vivo · 40h',
                'sort_order' => 2,
            ],
            [
                'title' => 'Amamentação e Acolhimento',
                'tag_label' => 'Workshop',
                'tag_style' => 'neutral',
                'description' => 'Um dia de imersão em apoio à amamentação para doulas e famílias.',
                'meta_info' => 'Presencial · 8h',
                'sort_order' => 3,
            ],
        ];

        foreach ($courses as $course) {
            Course::firstOrCreate(
                ['title' => $course['title']],
                [...$course, 'is_active' => true]
            );
        }
    }
}
