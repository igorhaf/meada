<x-layouts.app title="Blog">

<section class="relative grad-hero pt-36 pb-28 overflow-hidden">
    <div class="absolute inset-0 dot-pattern opacity-25 pointer-events-none"></div>
    <div class="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-dark-950 to-transparent pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="max-w-4xl">
            <span class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full badge-brand text-xs font-semibold mb-7 fade-up">Blog</span>
            <h1 class="text-5xl lg:text-7xl font-black text-white leading-[1.05] tracking-tight mb-7 fade-up fade-up-delay-1">
                Dicas e novidades<br><span class="grad-text">para o seu negócio</span>
            </h1>
            <p class="text-xl text-gray-400 max-w-2xl fade-up fade-up-delay-2">
                Conteúdo prático sobre tecnologia, marketing digital e IA para quem empreende.
            </p>
        </div>
    </div>
</section>

<section class="py-28 bg-dark-950 relative overflow-hidden">
    <div class="absolute inset-0 grid-pattern opacity-[0.25] pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

        {{-- Featured --}}
        <div class="grid lg:grid-cols-2 gap-12 items-center mb-24 pb-24 fade-up" style="border-bottom: 1px solid rgba(255,255,255,0.05);">
            <div class="rounded-3xl overflow-hidden aspect-video flex items-center justify-center relative" style="background: linear-gradient(135deg,#7c3aed,#06b6d4);">
                <div class="absolute inset-0 dot-pattern opacity-[0.15]"></div>
                <div class="relative text-center text-white p-8">
                    <div class="text-7xl mb-3">🤖</div>
                    <div class="text-sm font-medium opacity-70">Artigo em Destaque</div>
                </div>
            </div>
            <div>
                <div class="flex items-center gap-3 mb-5">
                    <span class="px-3 py-1 bg-brand-500/15 text-brand-300 text-xs font-bold rounded-full border border-brand-500/20">IA & Automacao</span>
                    <span class="text-xs text-gray-500">5 de marco de 2026</span>
                </div>
                <h2 class="text-3xl font-black text-white mb-5 leading-tight">Como a IA pode responder seus clientes no WhatsApp enquanto voce dorme</h2>
                <p class="text-gray-400 leading-relaxed mb-8">Restaurantes, saloes e lojas estao usando assistentes de IA para nunca perder um cliente por falta de resposta. Descubra como funciona e quanto custa para o seu negocio.</p>
                <div class="flex items-center gap-3">
                    <div class="w-9 h-9 rounded-full btn-brand flex items-center justify-center text-sm font-bold text-white relative z-10">IH</div>
                    <div>
                        <div class="font-semibold text-sm text-white">Igor Hafner</div>
                        <div class="text-xs text-gray-500">CEO · 8 min de leitura</div>
                    </div>
                </div>
            </div>
        </div>

        {{-- Grid --}}
        <div class="grid md:grid-cols-2 lg:grid-cols-3 gap-5">
            @foreach([
                ['emoji' => '📱', 'cat' => 'Marketing Digital', 'gradient' => 'linear-gradient(135deg,#db2777,#9333ea)', 'date' => '28 fev 2026',
                 'title' => '5 formas de usar o WhatsApp para vender mais no seu negocio local',
                 'excerpt' => 'O WhatsApp virou vitrine, catalogo e caixa registradora. Veja como pequenos negocios estao faturando mais com estrategias simples.',
                 'author' => 'Ana Paula', 'initials' => 'AP', 'time' => '6 min'],
                ['emoji' => '🌐', 'cat' => 'Sites', 'gradient' => 'linear-gradient(135deg,#0891b2,#06b6d4)', 'date' => '20 fev 2026',
                 'title' => 'Por que seu negocio perde clientes sem um site no Google',
                 'excerpt' => '92% das pessoas pesquisam no Google antes de visitar um estabelecimento. Sem site, voce simplesmente nao existe para elas.',
                 'author' => 'Lucas Moreira', 'initials' => 'LM', 'time' => '5 min'],
                ['emoji' => '📊', 'cat' => 'Gestao', 'gradient' => 'linear-gradient(135deg,#059669,#0d9488)', 'date' => '12 fev 2026',
                 'title' => 'Chega de planilha! Como um sistema simples pode organizar seu negocio',
                 'excerpt' => 'Controlar estoque e clientes no Excel gera erros e perde tempo. Descubra como um sistema sob medida transforma o dia a dia.',
                 'author' => 'Fernanda Rocha', 'initials' => 'FR', 'time' => '7 min'],
                ['emoji' => '🛒', 'cat' => 'E-commerce', 'gradient' => 'linear-gradient(135deg,#ea580c,#dc2626)', 'date' => '5 fev 2026',
                 'title' => 'Como abri uma loja virtual e comecei a vender em 7 dias',
                 'excerpt' => 'Uma lojista de Curitiba conta como saiu do zero para faturar online em uma semana com a ajuda da Meada.',
                 'author' => 'Igor Hafner', 'initials' => 'IH', 'time' => '10 min'],
                ['emoji' => '🤖', 'cat' => 'IA', 'gradient' => 'linear-gradient(135deg,#7c3aed,#4f46e5)', 'date' => '28 jan 2026',
                 'title' => 'IA no seu negocio: 3 formas praticas para comecar hoje',
                 'excerpt' => 'Inteligencia artificial nao e so para grandes empresas. Veja tres formas simples de colocar IA para trabalhar no seu negocio local.',
                 'author' => 'Fernanda Rocha', 'initials' => 'FR', 'time' => '9 min'],
                ['emoji' => '⭐', 'cat' => 'Reputacao', 'gradient' => 'linear-gradient(135deg,#ca8a04,#d97706)', 'date' => '20 jan 2026',
                 'title' => 'Como conseguir mais avaliacoes 5 estrelas no Google Maps',
                 'excerpt' => 'Avaliacoes positivas no Google sao o melhor marketing para negocios locais. Aprenda estrategias que funcionam para acumular avaliacoes reais.',
                 'author' => 'Ana Paula', 'initials' => 'AP', 'time' => '5 min'],
            ] as $post)
            <article class="glass-card-hover rounded-3xl overflow-hidden fade-up">
                <div class="h-36 flex items-center justify-center text-5xl relative overflow-hidden" style="background: {{ $post['gradient'] }};">
                    <div class="absolute inset-0 dot-pattern opacity-[0.12]"></div>
                    <span class="relative">{{ $post['emoji'] }}</span>
                </div>
                <div class="p-6">
                    <div class="flex items-center gap-2.5 mb-4">
                        <span class="px-2.5 py-1 glass-card text-xs font-bold text-brand-300 rounded-full border border-brand-500/20">{{ $post['cat'] }}</span>
                        <span class="text-xs text-gray-600">{{ $post['date'] }}</span>
                    </div>
                    <h3 class="font-black text-white text-base mb-2.5 leading-snug">{{ $post['title'] }}</h3>
                    <p class="text-gray-500 text-sm leading-relaxed mb-6">{{ $post['excerpt'] }}</p>
                    <div class="flex items-center gap-2.5">
                        <div class="w-7 h-7 rounded-full btn-brand flex items-center justify-center text-xs font-bold text-white relative z-10">{{ $post['initials'] }}</div>
                        <div>
                            <div class="text-xs font-semibold text-gray-300">{{ $post['author'] }}</div>
                            <div class="text-xs text-gray-600">{{ $post['time'] }} de leitura</div>
                        </div>
                    </div>
                </div>
            </article>
            @endforeach
        </div>
    </div>
</section>

<section class="py-24 bg-dark-900 relative overflow-hidden">
    <div class="absolute top-0 left-0 right-0 h-px line-glow pointer-events-none"></div>
    <div class="absolute inset-0 dot-pattern opacity-20 pointer-events-none"></div>
    <div class="relative max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 text-center fade-up">
        <div class="text-4xl mb-6">📬</div>
        <h2 class="text-3xl font-black text-white mb-3">Receba dicas toda semana</h2>
        <p class="text-gray-500 text-sm mb-8">Conteudo gratuito sobre tecnologia e crescimento para pequenos negocios. Sem spam.</p>
        <form class="flex flex-col sm:flex-row gap-3 max-w-sm mx-auto">
            <input type="email" placeholder="seu@email.com" class="flex-grow px-4 py-3.5 rounded-2xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-500 bg-white/[0.05] border border-white/[0.08] text-white placeholder-gray-600">
            <button type="submit" class="px-6 py-3.5 text-sm font-bold text-white btn-brand rounded-2xl whitespace-nowrap">
                <span>Inscrever-se</span>
            </button>
        </form>
    </div>
</section>

</x-layouts.app>
