<?php

namespace Database\Seeders;

use App\Models\SiteSetting;
use Illuminate\Database\Seeder;

class SiteSettingSeeder extends Seeder
{
    public function run(): void
    {
        SiteSetting::query()->delete();

        SiteSetting::create([
            'site_name' => 'Semente Doce',
            'tagline' => 'Doces & salgados artesanais, feitos com carinho',
            'announcement' => '🚚 Entrega no bairro e retirada na loja • Encomendas com 2 dias de antecedência',
            'instagram_url' => 'https://instagram.com/sementedoce',
            'facebook_url' => 'https://facebook.com/sementedoce',
            'tiktok_url' => 'https://tiktok.com/@sementedoce',
            'whatsapp' => '+55 11 98888-0001',
            'contact_email' => 'oi@sementedoce.com.br',
            'contact_phone' => '(11) 98888-0001',
            'address' => 'Rua das Sementes, 100 — Centro, São Paulo — SP',
            'opening_hours' => "Terça a sexta: 9h às 19h\nSábado: 9h às 17h\nDomingo e segunda: fechado",
            'about' => 'A Semente Doce nasceu na cozinha de casa, do sonho de transformar açúcar, '
                . 'fermento e afeto em momentos inesquecíveis. Somos uma doceria e salgaderia '
                . 'artesanal: cada brigadeiro é enrolado à mão, cada bolo é decorado com paixão e '
                . 'cada salgado sai quentinho do forno. Fazemos festas, café da manhã, presentes e '
                . 'encomendas especiais — sempre com ingredientes de verdade e muito carinho.',
        ]);
    }
}
