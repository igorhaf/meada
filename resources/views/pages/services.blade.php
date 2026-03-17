<x-layouts.app title="Serviços">

{{-- HERO --}}
<section class="relative grad-hero pt-36 pb-28 overflow-hidden">
    <div class="absolute inset-0 dot-pattern opacity-25 pointer-events-none"></div>
    <div class="absolute top-1/3 left-1/3 w-[500px] h-[500px] rounded-full bg-brand-600/[0.07] blur-[100px] pointer-events-none"></div>
    <div class="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-dark-950 to-transparent pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="max-w-4xl">
            <span class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full badge-brand text-xs font-semibold mb-7 fade-up">Nossos Serviços</span>
            <h1 class="text-5xl lg:text-7xl font-black text-white leading-[1.05] tracking-tight mb-7 fade-up fade-up-delay-1">
                Tudo que você precisa<br><span class="grad-text">em um só lugar</span>
            </h1>
            <p class="text-xl text-gray-400 max-w-2xl fade-up fade-up-delay-2">
                Soluções digitais completas para pequenos e médios negócios. Preço justo, entrega no prazo, suporte de verdade.
            </p>
        </div>
    </div>
</section>

{{-- SERVICES LIST --}}
@foreach([
    ['id' => 'site', 'emoji' => '🌐', 'title' => 'Site Profissional', 'reverse' => false,
     'desc' => 'Seu negócio precisa existir na internet — e existir com qualidade. Criamos sites modernos, rápidos e otimizados para aparecer no Google quando seu cliente estiver procurando exatamente o que você oferece.',
     'items' => ['Design personalizado para o seu negócio', 'Aparece no Google (SEO local)', 'Funciona no celular e no computador', 'Integração com WhatsApp e redes sociais', 'Painel para você mesmo atualizar'],
     'stat1' => ['val' => '3×', 'label' => 'Mais contatos'],
     'stat2' => ['val' => '7 dias', 'label' => 'Entrega média'],
     'gradient' => 'linear-gradient(135deg,#7c3aed,#4f46e5)'],
    ['id' => 'ecommerce', 'emoji' => '🛒', 'title' => 'Loja Virtual', 'reverse' => true,
     'desc' => 'Venda todos os dias, mesmo quando você está dormindo. Nossa loja virtual é simples de gerenciar pelo celular, aceita cartão, PIX e boleto, e chega prontinha com seus produtos cadastrados.',
     'items' => ['Catálogo completo de produtos', 'Checkout com todos os meios de pagamento', 'Integração com Correios e transportadoras', 'Gestão de pedidos pelo celular', 'Relatórios de vendas em tempo real'],
     'stat1' => ['val' => '24/7', 'label' => 'Vendendo sempre'],
     'stat2' => ['val' => '+60%', 'label' => 'Ticket médio online'],
     'gradient' => 'linear-gradient(135deg,#0891b2,#06b6d4)'],
    ['id' => 'ia', 'emoji' => '🤖', 'title' => 'IA para Atendimento', 'reverse' => false,
     'desc' => 'Um atendente inteligente que nunca dorme, nunca fica de mau humor e responde seus clientes no WhatsApp, Instagram e site. Ele agenda horários, envia orçamentos e qualifica os leads automaticamente.',
     'items' => ['Atendimento automático no WhatsApp', 'Agendamento e confirmação de horários', 'Envio de orçamentos sem você tocar', 'Integração com Instagram e Messenger', 'Relatório do que os clientes mais perguntam'],
     'stat1' => ['val' => '90%', 'label' => 'Das dúvidas resolvidas pela IA'],
     'stat2' => ['val' => '−3h', 'label' => 'Por dia no atendimento'],
     'gradient' => 'linear-gradient(135deg,#9333ea,#7c3aed)'],
    ['id' => 'sistema', 'emoji' => '⚙️', 'title' => 'Sistemas sob Medida', 'reverse' => true,
     'desc' => 'Chega de planilha. Criamos sistemas personalizados para controlar estoque, gerenciar clientes, fazer agendamentos, emitir relatórios e tudo mais que seu negócio precisa.',
     'items' => ['Controle de estoque em tempo real', 'Cadastro e histórico de clientes', 'Sistema de agendamento e reservas', 'Emissão de relatórios e gráficos', 'Acesso pelo celular e computador'],
     'stat1' => ['val' => '−70%', 'label' => 'Menos trabalho manual'],
     'stat2' => ['val' => '100%', 'label' => 'Feito para seu negócio'],
     'gradient' => 'linear-gradient(135deg,#059669,#0d9488)'],
] as $svc)
<section class="py-24 bg-dark-950 relative overflow-hidden" id="{{ $svc['id'] }}">
    @if($loop->first)
    <div class="absolute inset-0 grid-pattern opacity-[0.3] pointer-events-none"></div>
    @endif
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="grid lg:grid-cols-2 gap-20 items-center">

            <div class="{{ $svc['reverse'] ? 'order-2 lg:order-1' : '' }} fade-up">
                <div class="inline-flex items-center justify-center w-14 h-14 rounded-2xl mb-7 text-3xl" style="background: {{ $svc['gradient'] }};">
                    {{ $svc['emoji'] }}
                </div>
                <h2 class="text-4xl font-black text-white mb-6">{{ $svc['title'] }}</h2>
                <p class="text-gray-400 leading-relaxed mb-8 text-lg">{{ $svc['desc'] }}</p>
                <ul class="space-y-3 mb-10">
                    @foreach($svc['items'] as $item)
                    <li class="flex items-center gap-3">
                        <div class="w-5 h-5 rounded-full btn-brand flex items-center justify-center flex-shrink-0">
                            <svg class="w-3 h-3 text-white relative z-10" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"/></svg>
                        </div>
                        <span class="text-sm text-gray-300">{{ $item }}</span>
                    </li>
                    @endforeach
                </ul>
                <a href="/contact" class="inline-flex items-center gap-2 px-7 py-4 text-sm font-bold text-white btn-brand rounded-2xl">
                    <span>Quero esse serviço</span>
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3"/></svg>
                </a>
            </div>

            <div class="{{ $svc['reverse'] ? 'order-1 lg:order-2' : '' }} fade-up fade-up-delay-2">
                <div class="rounded-3xl p-10 text-white relative overflow-hidden" style="background: {{ $svc['gradient'] }};">
                    <div class="absolute inset-0 dot-pattern opacity-[0.15]"></div>
                    <div class="absolute top-0 right-0 w-64 h-64 rounded-full bg-white/[0.04] -translate-y-1/2 translate-x-1/2 pointer-events-none"></div>
                    <div class="relative">
                        <div class="text-7xl mb-8">{{ $svc['emoji'] }}</div>
                        <div class="grid grid-cols-2 gap-4">
                            <div class="bg-white/[0.12] backdrop-blur rounded-2xl p-5">
                                <div class="text-4xl font-black tracking-tight">{{ $svc['stat1']['val'] }}</div>
                                <div class="text-sm opacity-70 mt-1.5">{{ $svc['stat1']['label'] }}</div>
                            </div>
                            <div class="bg-white/[0.12] backdrop-blur rounded-2xl p-5">
                                <div class="text-4xl font-black tracking-tight">{{ $svc['stat2']['val'] }}</div>
                                <div class="text-sm opacity-70 mt-1.5">{{ $svc['stat2']['label'] }}</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

        </div>
    </div>
    @if(!$loop->last)
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 mt-24">
        <div class="h-px bg-gradient-to-r from-transparent via-white/[0.06] to-transparent"></div>
    </div>
    @endif
</section>
@endforeach

{{-- CTA --}}
<section class="py-24 bg-dark-900 relative overflow-hidden">
    <div class="absolute top-0 left-0 right-0 h-px line-glow pointer-events-none"></div>
    <div class="absolute inset-0 dot-pattern opacity-20 pointer-events-none"></div>
    <div class="relative max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center fade-up">
        <div class="inline-flex items-center justify-center w-14 h-14 rounded-2xl btn-brand mb-7">
            <span class="text-2xl relative z-10">💡</span>
        </div>
        <h2 class="text-4xl font-black text-white mb-5">Não sabe qual serviço precisa?</h2>
        <p class="text-gray-400 text-lg mb-10">A gente te ajuda a descobrir. É só falar o que você quer resolver no seu negócio.</p>
        <a href="/contact" class="inline-flex items-center gap-2 px-8 py-4 text-sm font-bold text-white btn-brand rounded-2xl">
            <span>Falar com a Meada agora</span>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3"/></svg>
        </a>
    </div>
</section>

</x-layouts.app>
