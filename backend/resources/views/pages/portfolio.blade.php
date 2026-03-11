<x-layouts.app title="Portfólio">

<section class="relative grad-hero pt-36 pb-28 overflow-hidden">
    <div class="absolute inset-0 dot-pattern opacity-25 pointer-events-none"></div>
    <div class="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-dark-950 to-transparent pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="max-w-4xl">
            <span class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full badge-brand text-xs font-semibold mb-7 fade-up">Portfólio</span>
            <h1 class="text-5xl lg:text-7xl font-black text-white leading-[1.05] tracking-tight mb-7 fade-up fade-up-delay-1">
                Projetos reais,<br><span class="grad-text">resultados reais</span>
            </h1>
            <p class="text-xl text-gray-400 max-w-2xl fade-up fade-up-delay-2">
                Mais de 120 negócios atendidos. Veja alguns dos casos que mais nos orgulhamos.
            </p>
        </div>
    </div>
</section>

<section class="py-28 bg-dark-950 relative overflow-hidden">
    <div class="absolute inset-0 grid-pattern opacity-[0.25] pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="space-y-24">

            @foreach([
                ['emoji' => '🍕', 'name' => 'Pizzaria Roma',      'seg' => 'Restaurante',     'gradient' => 'linear-gradient(135deg,#ea580c,#dc2626)',
                 'desc' => 'A Pizzaria Roma vendia apenas por telefone e enfrentava perda de pedidos no horário de pico. Criamos um site com cardápio digital e um assistente de IA no WhatsApp que recebe e confirma pedidos automaticamente.',
                 'results' => [['v'=>'+180%','l'=>'Pedidos online em 60 dias'], ['v'=>'−2h','l'=>'Por dia no atendimento'], ['v'=>'4.9★','l'=>'Avaliação no Google']],
                 'services' => ['Site + Cardápio Digital', 'IA WhatsApp', 'SEO Local']],
                ['emoji' => '💅', 'name' => 'Studio Bela',        'seg' => 'Salão de Beleza', 'gradient' => 'linear-gradient(135deg,#db2777,#9333ea)',
                 'desc' => 'O Studio Bela sofria com cancelamentos de última hora e dificuldade em organizar a agenda de 5 profissionais. Desenvolvemos um sistema de agendamento com confirmação automática por WhatsApp e lembretes 24h antes.',
                 'results' => [['v'=>'−65%','l'=>'Cancelamentos'], ['v'=>'+45%','l'=>'Clientes novos'], ['v'=>'Zero','l'=>'Conflito de horários']],
                 'services' => ['Sistema de Agendamento', 'IA WhatsApp', 'Site Profissional']],
                ['emoji' => '👗', 'name' => 'Boutique Estilo',    'seg' => 'Loja de Roupas',  'gradient' => 'linear-gradient(135deg,#7c3aed,#06b6d4)',
                 'desc' => 'A Boutique Estilo queria vender pela internet mas tinha medo de complicação. Entregamos uma loja virtual completa com catálogo, gestão de estoque e checkout simples. Em 30 dias vendia para outras cidades.',
                 'results' => [['v'=>'R$ 28k','l'=>'Faturamento no 1º mês'], ['v'=>'12×','l'=>'Retorno sobre investimento'], ['v'=>'+3','l'=>'Novas cidades atendidas']],
                 'services' => ['Loja Virtual', 'Painel de Gestão', 'Catálogo Digital']],
                ['emoji' => '🧺', 'name' => 'Lavanderia Express', 'seg' => 'Lavanderia',      'gradient' => 'linear-gradient(135deg,#059669,#0d9488)',
                 'desc' => 'A Lavanderia Express perdia clientes porque demorava para responder mensagens. Implementamos uma IA que responde no WhatsApp, registra os pedidos e avisa quando as roupas ficam prontas.',
                 'results' => [['v'=>'+40%','l'=>'Faturamento em 3 meses'], ['v'=>'100%','l'=>'Clientes avisados no prazo'], ['v'=>'0s','l'=>'Tempo de resposta da IA']],
                 'services' => ['IA WhatsApp', 'Sistema de Controle', 'Notificações Automáticas']],
            ] as $proj)
            <div class="grid lg:grid-cols-2 gap-16 items-center fade-up">
                <div class="{{ $loop->even ? 'order-2 lg:order-1' : '' }}">
                    <div class="inline-flex items-center gap-3 mb-6">
                        <div class="w-12 h-12 rounded-2xl flex items-center justify-center text-2xl" style="background: {{ $proj['gradient'] }};">{{ $proj['emoji'] }}</div>
                        <div>
                            <div class="font-black text-white text-xl">{{ $proj['name'] }}</div>
                            <div class="text-sm text-gray-500">{{ $proj['seg'] }}</div>
                        </div>
                    </div>
                    <p class="text-gray-400 leading-relaxed mb-8 text-lg">{{ $proj['desc'] }}</p>
                    <div class="grid grid-cols-3 gap-3 mb-8">
                        @foreach($proj['results'] as $r)
                        <div class="glass-card rounded-2xl p-4 text-center border border-white/[0.06]">
                            <div class="text-xl font-black text-white">{{ $r['v'] }}</div>
                            <div class="text-xs text-gray-500 mt-1 leading-tight">{{ $r['l'] }}</div>
                        </div>
                        @endforeach
                    </div>
                    <div class="flex flex-wrap gap-2">
                        @foreach($proj['services'] as $s)
                        <span class="px-3 py-1.5 glass-card text-brand-300 text-xs font-semibold rounded-full border border-brand-500/20">{{ $s }}</span>
                        @endforeach
                    </div>
                </div>

                <div class="{{ $loop->even ? 'order-1 lg:order-2' : '' }}">
                    <div class="rounded-3xl h-72 flex items-center justify-center relative overflow-hidden" style="background: {{ $proj['gradient'] }};">
                        <div class="absolute inset-0 dot-pattern opacity-[0.15]"></div>
                        <div class="absolute top-0 right-0 w-64 h-64 rounded-full bg-white/[0.05] -translate-y-1/2 translate-x-1/2 pointer-events-none"></div>
                        <div class="relative text-center text-white">
                            <div class="text-7xl mb-4">{{ $proj['emoji'] }}</div>
                            <div class="text-2xl font-black">{{ $proj['name'] }}</div>
                            <div class="text-sm opacity-70 mt-1.5">{{ $proj['seg'] }}</div>
                        </div>
                    </div>
                </div>
            </div>
            @if(!$loop->last)
            <div class="h-px bg-gradient-to-r from-transparent via-white/[0.06] to-transparent"></div>
            @endif
            @endforeach

        </div>
    </div>
</section>

<section class="py-24 bg-dark-900 relative overflow-hidden">
    <div class="absolute top-0 left-0 right-0 h-px line-glow pointer-events-none"></div>
    <div class="absolute inset-0 dot-pattern opacity-20 pointer-events-none"></div>
    <div class="relative max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center fade-up">
        <div class="text-5xl mb-6">🤝</div>
        <h2 class="text-4xl font-black text-white mb-5">Seu negócio pode ser o próximo</h2>
        <p class="text-gray-400 text-lg mb-10">Manda uma mensagem e conta o que você precisa. Rápido, sem burocracia e sem compromisso.</p>
        <a href="/contact" class="inline-flex items-center gap-2 px-8 py-4 text-sm font-bold text-white btn-brand rounded-2xl">
            <span>Quero um orçamento grátis</span>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3"/></svg>
        </a>
    </div>
</section>

</x-layouts.app>
