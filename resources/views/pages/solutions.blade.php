<x-layouts.app title="Soluções">

<section class="relative grad-hero pt-36 pb-28 overflow-hidden">
    <div class="absolute inset-0 dot-pattern opacity-25 pointer-events-none"></div>
    <div class="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-dark-950 to-transparent pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="max-w-4xl">
            <span class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full badge-brand text-xs font-semibold mb-7 fade-up">Soluções por Segmento</span>
            <h1 class="text-5xl lg:text-7xl font-black text-white leading-[1.05] tracking-tight mb-7 fade-up fade-up-delay-1">
                Tecnologia pensada<br>para o seu tipo<br><span class="grad-text">de negócio</span>
            </h1>
            <p class="text-xl text-gray-400 max-w-2xl fade-up fade-up-delay-2">
                Cada segmento tem suas necessidades. Conheça o que a Meada oferece para cada tipo de negócio.
            </p>
        </div>
    </div>
</section>

<section class="py-28 bg-dark-950 relative overflow-hidden">
    <div class="absolute inset-0 grid-pattern opacity-[0.3] pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-20 fade-up">
            <h2 class="text-4xl font-black text-white mb-4">Soluções por segmento</h2>
            <p class="text-gray-500">Clique no seu segmento e veja o que podemos fazer pelo seu negócio</p>
        </div>
        <div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
            @foreach([
                ['emoji' => '🍕', 'title' => 'Restaurantes & Lanchonetes', 'items' => ['Cardápio digital com QR Code', 'Sistema de pedidos online', 'IA para reservas no WhatsApp', 'Painel de vendas por dia/semana', 'Integração com iFood e Uber Eats'], 'gradient' => 'linear-gradient(135deg,#ea580c,#dc2626)'],
                ['emoji' => '👗', 'title' => 'Lojas de Roupas & Moda',      'items' => ['Loja virtual com estoque', 'Catálogo digital para WhatsApp', 'IA para tirar dúvidas sobre peças', 'Gestão de promoções e cupons', 'Relatório de produtos mais vendidos'],  'gradient' => 'linear-gradient(135deg,#db2777,#9333ea)'],
                ['emoji' => '💈', 'title' => 'Salões, Barbearias & Estética','items' => ['Sistema de agendamento online', 'IA que agenda pelo WhatsApp', 'Lembretes automáticos por SMS', 'Histórico de clientes e preferências', 'Gestão de comissões da equipe'],  'gradient' => 'linear-gradient(135deg,#7c3aed,#4f46e5)'],
                ['emoji' => '🏥', 'title' => 'Clínicas & Consultórios',      'items' => ['Agendamento de consultas online', 'Prontuário digital simplificado', 'Confirmação automática por WhatsApp', 'Fila de espera inteligente', 'Portal do paciente'],             'gradient' => 'linear-gradient(135deg,#059669,#0d9488)'],
                ['emoji' => '🧺', 'title' => 'Lavanderias & Limpeza',        'items' => ['Sistema de controle de peças', 'Notificação quando ficou pronto', 'IA para agendamento de coleta', 'Gestão de clientes mensalistas', 'Relatório de produção diária'],      'gradient' => 'linear-gradient(135deg,#0891b2,#06b6d4)'],
                ['emoji' => '🏗️', 'title' => 'Prestadores de Serviço',      'items' => ['Site com portfólio de trabalhos', 'Sistema de orçamentos online', 'IA que qualifica clientes', 'Agenda e controle de visitas', 'Gestão de contratos simples'],            'gradient' => 'linear-gradient(135deg,#4b5563,#374151)'],
            ] as $seg)
            <div class="glass-card-hover rounded-3xl overflow-hidden fade-up">
                <div class="h-24 flex items-center justify-center text-4xl relative overflow-hidden" style="background: {{ $seg['gradient'] }};">
                    <div class="absolute inset-0 dot-pattern opacity-[0.15]"></div>
                    <span class="relative">{{ $seg['emoji'] }}</span>
                </div>
                <div class="p-7">
                    <h3 class="font-black text-white text-lg mb-5">{{ $seg['title'] }}</h3>
                    <ul class="space-y-2.5">
                        @foreach($seg['items'] as $item)
                        <li class="flex items-center gap-2.5 text-sm text-gray-400">
                            <svg class="w-4 h-4 text-brand-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7"/></svg>
                            {{ $item }}
                        </li>
                        @endforeach
                    </ul>
                    <a href="/contact" class="inline-flex items-center mt-7 text-sm font-bold text-brand-400 hover:text-brand-300 gap-1.5 transition">
                        Quero para meu negócio
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3"/></svg>
                    </a>
                </div>
            </div>
            @endforeach
        </div>
    </div>
</section>

<section class="py-28 bg-dark-900 relative overflow-hidden">
    <div class="absolute top-0 left-0 right-0 h-px line-glow pointer-events-none"></div>
    <div class="absolute inset-0 grid-pattern opacity-[0.25] pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-20 fade-up">
            <h2 class="text-4xl font-black text-white mb-4">Nossa stack de tecnologia</h2>
            <p class="text-gray-500">Usamos as ferramentas mais modernas para entregar o melhor resultado</p>
        </div>
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
            @foreach([
                ['name' => 'ChatGPT / OpenAI', 'desc' => 'IA de atendimento',      'emoji' => '🧠'],
                ['name' => 'WhatsApp API',      'desc' => 'Automação de mensagens', 'emoji' => '💬'],
                ['name' => 'Mercado Pago',      'desc' => 'Pagamentos online',      'emoji' => '💳'],
                ['name' => 'Google Business',   'desc' => 'Presença local',         'emoji' => '📍'],
                ['name' => 'Instagram API',     'desc' => 'Automação social',       'emoji' => '📸'],
                ['name' => 'N8N / Zapier',      'desc' => 'Automação de processos', 'emoji' => '⚡'],
                ['name' => 'Google Analytics',  'desc' => 'Relatórios de visitas',  'emoji' => '📊'],
                ['name' => 'AWS / Cloud',       'desc' => 'Hospedagem segura',      'emoji' => '☁️'],
            ] as $tech)
            <div class="glass-card-hover rounded-2xl p-5 fade-up">
                <div class="text-3xl mb-3">{{ $tech['emoji'] }}</div>
                <div class="text-sm font-bold text-white">{{ $tech['name'] }}</div>
                <div class="text-xs text-gray-500 mt-1">{{ $tech['desc'] }}</div>
            </div>
            @endforeach
        </div>
    </div>
</section>

</x-layouts.app>
