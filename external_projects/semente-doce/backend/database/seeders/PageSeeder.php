<?php

namespace Database\Seeders;

use App\Models\ContactMessage;
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
                'body' => <<<'HTML'
                <h2>Central de ajuda</h2>
                <p>Que bom ter você por aqui! Reunimos as dúvidas mais comuns de quem pede doces e salgados na Semente Doce.</p>

                <h3>Como faço um pedido?</h3>
                <p>É só adicionar os itens ao carrinho e finalizar a compra. Você acompanha tudo em <strong>"Meus pedidos"</strong>.</p>

                <h3>Vocês entregam?</h3>
                <p>Sim! Entregamos em vários bairros (a taxa aparece no checkout conforme a sua região) ou você pode <strong>retirar na loja</strong> sem custo.</p>

                <h3>Quais as formas de pagamento?</h3>
                <p>Aceitamos cartão de crédito e Pix pelo Mercado Pago. No ambiente de demonstração o pagamento é simulado.</p>

                <h3>E as encomendas especiais?</h3>
                <p>Bolos decorados, mesas de doces e salgados em grande quantidade são feitos <strong>sob encomenda</strong>. Peça um orçamento na página <em>Encomendas</em> — a gente responde rapidinho.</p>
                HTML,
            ],
            [
                'slug' => 'como-encomendar',
                'title' => 'Como encomendar',
                'body' => <<<'HTML'
                <h2>Como encomendar</h2>
                <p>Encomendar na Semente Doce é simples e sem compromisso. Veja o passo a passo:</p>

                <h3>1. Conte o que você imagina</h3>
                <p>Na página de <strong>Encomendas</strong>, descreva o doce ou salgado dos seus sonhos: tema, sabor, quantidade e a data do evento.</p>

                <h3>2. Receba o orçamento</h3>
                <p>Nossa equipe analisa o pedido e envia um <strong>orçamento</strong> com o valor e o prazo. Você recebe a resposta pelo WhatsApp ou e-mail.</p>

                <h3>3. Confirme e agende</h3>
                <p>Gostou? É só confirmar. A partir daí entramos em <strong>produção</strong> e combinamos a entrega ou retirada na data combinada.</p>

                <p><strong>Dica:</strong> encomendas de bolos decorados pedem pelo menos <strong>2 a 5 dias</strong> de antecedência. Quanto antes, melhor para garantir a sua data!</p>
                HTML,
            ],
            [
                'slug' => 'privacidade',
                'title' => 'Política de privacidade',
                'body' => <<<'HTML'
                <h2>Política de privacidade</h2>
                <p>A sua privacidade é prioridade para a Semente Doce. Esta política explica como tratamos seus dados, em conformidade com a LGPD (Lei nº 13.709/2018).</p>

                <h3>Dados que coletamos</h3>
                <p>Coletamos apenas o necessário para atender você: nome, e-mail, telefone, endereço de entrega e histórico de pedidos e encomendas.</p>

                <h3>Como usamos seus dados</h3>
                <p>Utilizamos suas informações para processar pedidos, montar orçamentos, combinar entregas e dar suporte. Nunca vendemos seus dados a terceiros.</p>

                <h3>Seus direitos</h3>
                <p>Você pode solicitar acesso, correção ou exclusão dos seus dados a qualquer momento pela página de Contato.</p>

                <h3>Segurança</h3>
                <p>Adotamos medidas técnicas e organizacionais para proteger suas informações contra acessos não autorizados.</p>
                HTML,
            ],
        ];

        foreach ($pages as $page) {
            Page::updateOrCreate(['slug' => $page['slug']], $page);
        }

        // Algumas mensagens de contato para o painel do root não ficar vazio.
        if (ContactMessage::count() === 0) {
            ContactMessage::insert([
                ['name' => 'Ana Beatriz', 'email' => 'ana@example.com', 'subject' => 'Encomenda de bolo', 'message' => 'Oi! Vocês fazem bolo de casamento para 100 pessoas?', 'is_read' => false, 'created_at' => now()->subDays(2), 'updated_at' => now()->subDays(2)],
                ['name' => 'Marcos Silva', 'email' => 'marcos@example.com', 'subject' => 'Salgados para evento', 'message' => 'Preciso de 500 salgados para sexta que vem, é possível?', 'is_read' => true, 'created_at' => now()->subDay(), 'updated_at' => now()->subDay()],
            ]);
        }
    }
}
