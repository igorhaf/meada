<x-layouts.app title="Sobre Nós">

{{-- HERO --}}
<section class="relative grad-hero pt-36 pb-28 overflow-hidden">
    <div class="absolute inset-0 dot-pattern opacity-25 pointer-events-none"></div>
    <div class="absolute top-1/3 right-1/4 w-[400px] h-[400px] rounded-full bg-brand-600/[0.08] blur-[80px] pointer-events-none"></div>
    <div class="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-dark-950 to-transparent pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="max-w-4xl">
            <span class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full badge-brand text-xs font-semibold mb-7 fade-up">Sobre a Meada</span>
            <h1 class="text-5xl lg:text-7xl font-black text-white leading-[1.05] tracking-tight mb-7 fade-up fade-up-delay-1">
                Uma agência feita<br>por quem entende<br><span class="grad-text">de tecnologia</span><br>e de negócios
            </h1>
            <p class="text-xl text-gray-400 leading-relaxed max-w-2xl fade-up fade-up-delay-2">
                Não somos uma empresa de TI fria e distante. Somos parceiros de negócio que falam a língua do empreendedor e entregam tecnologia que funciona.
            </p>
        </div>
    </div>
</section>

{{-- HISTÓRIA + STATS --}}
<section class="py-28 bg-dark-950 relative overflow-hidden">
    <div class="absolute inset-0 grid-pattern opacity-[0.3] pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="grid lg:grid-cols-2 gap-20 items-center">
            <div class="fade-up">
                <h2 class="text-4xl lg:text-5xl font-black text-white mb-8">Nossa história</h2>
                <div class="space-y-5 text-gray-400 leading-relaxed text-lg">
                    <p>A Meada nasceu de uma frustração simples: por que pequenos negócios sempre ficavam de fora das melhores soluções digitais? Por que uma padaria ou loja de bairro precisava se contentar com um site genérico?</p>
                    <p>Em 2019, um grupo de desenvolvedores decidiu mudar isso. Criamos a Meada com missão clara: tornar tecnologia de qualidade acessível para quem realmente faz a economia girar — o pequeno empreendedor brasileiro.</p>
                    <p>Hoje somos especialistas em sites, sistemas e IA para pequenos e médios negócios. Mais de 120 empresas cresceram com a gente — e não vamos parar por aí.</p>
                </div>
                <div class="mt-10">
                    <a href="/contact" class="inline-flex items-center gap-2 px-7 py-4 text-sm font-bold text-white btn-brand rounded-2xl">
                        <span>Fale com a gente</span>
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3"/></svg>
                    </a>
                </div>
            </div>

            <div class="grid grid-cols-2 gap-4 fade-up fade-up-delay-2">
                @foreach([
                    ['num' => '+120', 'label' => 'Negócios atendidos',   'gradient' => 'linear-gradient(135deg,#7c3aed,#4f46e5)'],
                    ['num' => '5 anos','label' => 'No mercado',          'gradient' => 'linear-gradient(135deg,#0891b2,#06b6d4)'],
                    ['num' => '98%',   'label' => 'Clientes satisfeitos','gradient' => 'linear-gradient(135deg,#db2777,#9333ea)'],
                    ['num' => '15',    'label' => 'Profissionais',       'gradient' => 'linear-gradient(135deg,#059669,#0d9488)'],
                ] as $stat)
                <div class="rounded-3xl p-8 text-white relative overflow-hidden group hover-card" style="background: {{ $stat['gradient'] }};">
                    <div class="absolute inset-0 bg-black/10 opacity-0 group-hover:opacity-100 transition"></div>
                    <div class="relative">
                        <div class="text-4xl font-black mb-2 tracking-tight">{{ $stat['num'] }}</div>
                        <div class="text-sm opacity-75">{{ $stat['label'] }}</div>
                    </div>
                </div>
                @endforeach
            </div>
        </div>
    </div>
</section>

{{-- VALORES --}}
<section class="py-28 bg-dark-900 relative overflow-hidden">
    <div class="absolute top-0 left-0 right-0 h-px line-glow pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-20 fade-up">
            <h2 class="text-4xl font-black text-white mb-4">O que nos move</h2>
            <p class="text-gray-500">Os princípios que guiam cada projeto que entregamos</p>
        </div>
        <div class="grid md:grid-cols-3 gap-5">
            @foreach([
                ['icon' => '🎯', 'title' => 'Missão',  'text' => 'Democratizar o acesso à tecnologia de ponta para pequenos e médios empreendedores, tornando-os competitivos no mundo digital.'],
                ['icon' => '🔭', 'title' => 'Visão',   'text' => 'Ser a agência digital de referência para negócios locais no Brasil, conhecida por resultados reais e relacionamentos de longo prazo.'],
                ['icon' => '💎', 'title' => 'Valores', 'text' => 'Transparência total, entregas no prazo, linguagem simples e suporte humano. Tratamos cada cliente como se fosse o único.'],
            ] as $v)
            <div class="glass-card-hover rounded-3xl p-10 text-center fade-up">
                <div class="text-5xl mb-6">{{ $v['icon'] }}</div>
                <h3 class="text-xl font-black text-white mb-4">{{ $v['title'] }}</h3>
                <p class="text-gray-400 text-sm leading-relaxed">{{ $v['text'] }}</p>
            </div>
            @endforeach
        </div>
    </div>
</section>

{{-- TIME --}}
<section class="py-28 bg-dark-950 relative overflow-hidden">
    <div class="absolute inset-0 dot-pattern opacity-20 pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-20 fade-up">
            <span class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full badge-brand text-xs font-semibold mb-5">Time</span>
            <h2 class="text-4xl font-black text-white">Conheça quem faz acontecer</h2>
        </div>
        <div class="grid sm:grid-cols-2 lg:grid-cols-4 gap-6">
            @foreach([
                ['name' => 'Igor Hafner',    'role' => 'CEO & Fundador',  'gradient' => 'linear-gradient(135deg,#7c3aed,#4f46e5)', 'emoji' => '🚀'],
                ['name' => 'Ana Paula Lima', 'role' => 'Design & UX',     'gradient' => 'linear-gradient(135deg,#db2777,#9333ea)', 'emoji' => '🎨'],
                ['name' => 'Lucas Moreira',  'role' => 'Dev Full Stack',  'gradient' => 'linear-gradient(135deg,#0891b2,#06b6d4)', 'emoji' => '💻'],
                ['name' => 'Fernanda Rocha', 'role' => 'IA & Automação',  'gradient' => 'linear-gradient(135deg,#059669,#0d9488)', 'emoji' => '🤖'],
            ] as $m)
            <div class="text-center fade-up group">
                <div class="w-32 h-32 mx-auto rounded-3xl flex items-center justify-center text-5xl mb-6 transition-all duration-300 group-hover:scale-105 group-hover:shadow-2xl" style="background: {{ $m['gradient'] }};">
                    {{ $m['emoji'] }}
                </div>
                <div class="font-bold text-white text-lg">{{ $m['name'] }}</div>
                <div class="text-sm text-gray-500 mt-1">{{ $m['role'] }}</div>
            </div>
            @endforeach
        </div>
    </div>
</section>

</x-layouts.app>
