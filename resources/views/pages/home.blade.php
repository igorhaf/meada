<x-layouts.app title="Início">

{{-- ══════════════════════════════════════════════════════
     HERO
══════════════════════════════════════════════════════ --}}
<section class="relative grad-hero min-h-screen flex items-center pt-16 overflow-hidden">
    {{-- Background layers --}}
    <div class="absolute inset-0 dot-pattern opacity-30 pointer-events-none"></div>
    <div class="absolute top-1/3 left-1/4 w-[500px] h-[500px] rounded-full bg-brand-600/[0.07] blur-[100px] pointer-events-none"></div>
    <div class="absolute top-1/2 right-1/6 w-[400px] h-[400px] rounded-full bg-accent-500/[0.06] blur-[80px] pointer-events-none"></div>
    <div class="absolute bottom-0 left-0 right-0 h-48 bg-gradient-to-t from-dark-950 to-transparent pointer-events-none"></div>

    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-28 lg:py-36">
        <div class="grid lg:grid-cols-2 gap-16 lg:gap-24 items-center">

            {{-- Left --}}
            <div>
                <div class="inline-flex items-center gap-2.5 px-4 py-2 rounded-full badge-brand mb-8 fade-up">
                    <span class="w-1.5 h-1.5 rounded-full bg-accent-400 animate-pulse"></span>
                    <span class="text-xs font-semibold tracking-wide">Tecnologia que cabe no seu negócio</span>
                </div>

                <h1 class="text-5xl sm:text-6xl lg:text-7xl font-black text-white leading-[1.05] tracking-tight mb-7 fade-up fade-up-delay-1">
                    Seu negócio<br>
                    <span class="grad-text">na internet.</span><br>
                    Do jeito certo.
                </h1>

                <p class="text-lg text-gray-400 leading-relaxed mb-10 max-w-lg fade-up fade-up-delay-2">
                    Sites que vendem, sistemas que organizam e IA que trabalha por você — enquanto cuida do que realmente importa.
                </p>

                <div class="flex flex-col sm:flex-row gap-3 mb-14 fade-up fade-up-delay-3">
                    <a href="/contact" class="inline-flex items-center justify-center gap-2 px-7 py-4 text-sm font-bold text-white btn-brand rounded-2xl">
                        <span>Orçamento grátis</span>
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3"/></svg>
                    </a>
                    <a href="/portfolio" class="inline-flex items-center justify-center gap-2 px-7 py-4 text-sm font-semibold btn-outline rounded-2xl">
                        Ver projetos
                    </a>
                </div>

                <div class="flex items-center gap-8 fade-up fade-up-delay-4">
                    <div>
                        <div class="text-3xl font-black text-white">+120</div>
                        <div class="text-xs text-gray-500 mt-0.5">Negócios atendidos</div>
                    </div>
                    <div class="w-px h-10 bg-white/[0.08]"></div>
                    <div>
                        <div class="text-3xl font-black text-white">5 anos</div>
                        <div class="text-xs text-gray-500 mt-0.5">No mercado</div>
                    </div>
                    <div class="w-px h-10 bg-white/[0.08]"></div>
                    <div>
                        <div class="text-3xl font-black text-white">98%</div>
                        <div class="text-xs text-gray-500 mt-0.5">Satisfação</div>
                    </div>
                </div>
            </div>

            {{-- Right — Dashboard visual --}}
            <div class="hidden lg:block fade-up fade-up-delay-2">
                <div class="relative">
                    {{-- Glow behind card --}}
                    <div class="absolute inset-0 rounded-3xl bg-brand-600/10 blur-3xl scale-110 pointer-events-none"></div>

                    <div class="relative glass-card rounded-3xl p-6 border border-white/[0.08] glow-brand">
                        {{-- Window bar --}}
                        <div class="flex items-center gap-2 mb-5 pb-4 border-b border-white/[0.05]">
                            <div class="w-3 h-3 rounded-full bg-red-400/70"></div>
                            <div class="w-3 h-3 rounded-full bg-yellow-400/70"></div>
                            <div class="w-3 h-3 rounded-full bg-green-400/70"></div>
                            <div class="ml-3 flex-1 bg-white/[0.04] rounded-lg px-3 py-1.5 text-xs text-gray-600 font-mono">meada.app — painel</div>
                        </div>

                        <div class="space-y-3">
                            {{-- Chart --}}
                            <div class="bg-white/[0.03] rounded-2xl p-4 border border-white/[0.05]">
                                <div class="flex items-center justify-between mb-3">
                                    <div class="text-xs text-gray-500 font-medium">Visitas esta semana</div>
                                    <div class="text-xs text-green-400 font-semibold">+34%</div>
                                </div>
                                <div class="flex items-end gap-1.5 h-16">
                                    @foreach([35,48,42,65,55,78,92] as $h)
                                    <div class="flex-1 rounded-sm opacity-90" style="height:{{ $h }}%; background: linear-gradient(to top, #7c3aed, #22d3ee);"></div>
                                    @endforeach
                                </div>
                                <div class="flex justify-between mt-2 text-[10px] text-gray-600">
                                    <span>Seg</span><span>Ter</span><span>Qua</span><span>Qui</span><span>Sex</span><span>Sáb</span><span>Dom</span>
                                </div>
                            </div>

                            {{-- Metrics --}}
                            <div class="grid grid-cols-3 gap-2.5">
                                <div class="metric-card p-3.5">
                                    <div class="text-[10px] text-gray-500 mb-1.5">Novos leads</div>
                                    <div class="text-xl font-black text-white">47</div>
                                    <div class="text-[10px] text-green-400 mt-1">+23%</div>
                                </div>
                                <div class="metric-card p-3.5">
                                    <div class="text-[10px] text-gray-500 mb-1.5">IA ativa</div>
                                    <div class="text-xl font-black grad-text">24/7</div>
                                    <div class="text-[10px] text-accent-400 mt-1">Online</div>
                                </div>
                                <div class="metric-card p-3.5">
                                    <div class="text-[10px] text-gray-500 mb-1.5">Pedidos</div>
                                    <div class="text-xl font-black text-white">183</div>
                                    <div class="text-[10px] text-green-400 mt-1">esta semana</div>
                                </div>
                            </div>

                            {{-- Alert --}}
                            <div class="flex items-center gap-3 bg-brand-600/[0.12] border border-brand-500/25 rounded-2xl px-4 py-3">
                                <div class="w-7 h-7 rounded-lg btn-brand flex items-center justify-center flex-shrink-0">
                                    <svg class="w-3.5 h-3.5 text-white relative z-10" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
                                </div>
                                <div>
                                    <div class="text-xs font-semibold text-white">IA detectou oportunidade</div>
                                    <div class="text-[10px] text-gray-400 mt-0.5">3 clientes prontos para fechar</div>
                                </div>
                            </div>
                        </div>
                    </div>

                    {{-- Floating chips --}}
                    <div class="absolute -top-5 -right-5 glass-card rounded-2xl px-4 py-3 border border-white/[0.1] shadow-xl">
                        <div class="text-[10px] text-gray-500 mb-0.5">Projeto entregue</div>
                        <div class="text-sm font-bold text-white">Pizzaria Roma 🍕</div>
                        <div class="text-[10px] text-green-400 mt-0.5">+180% pedidos online</div>
                    </div>

                    <div class="absolute -bottom-5 -left-5 glass-card rounded-2xl px-4 py-3 border border-white/[0.1] shadow-xl">
                        <div class="flex items-center gap-2">
                            <div class="w-2 h-2 rounded-full bg-green-400 animate-pulse"></div>
                            <div class="text-xs font-semibold text-white">IA respondendo</div>
                        </div>
                        <div class="text-[10px] text-gray-500 mt-0.5">12 conversas agora</div>
                    </div>
                </div>
            </div>

        </div>
    </div>
</section>

{{-- ══════════════════════════════════════════════════════
     LOGOS / SEGMENTOS
══════════════════════════════════════════════════════ --}}
<section class="py-14 bg-dark-950 border-y border-white/[0.04]">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <p class="text-center text-xs font-semibold text-gray-600 uppercase tracking-[0.2em] mb-8">Feito para quem empreende</p>
        <div class="grid grid-cols-3 sm:grid-cols-6 gap-3">
            @foreach([
                ['emoji' => '🍕', 'label' => 'Restaurantes'],
                ['emoji' => '👗', 'label' => 'Moda & Lojas'],
                ['emoji' => '🏥', 'label' => 'Clínicas'],
                ['emoji' => '💈', 'label' => 'Salões'],
                ['emoji' => '🧺', 'label' => 'Lavanderias'],
                ['emoji' => '🏗️', 'label' => 'Prestadores'],
            ] as $seg)
            <div class="flex flex-col items-center gap-2.5 p-4 rounded-2xl border border-white/[0.05] bg-white/[0.02] hover:border-brand-500/30 hover:bg-brand-600/[0.05] transition-all group">
                <span class="text-2xl">{{ $seg['emoji'] }}</span>
                <span class="text-[11px] font-medium text-gray-500 group-hover:text-gray-300 text-center leading-tight transition">{{ $seg['label'] }}</span>
            </div>
            @endforeach
        </div>
    </div>
</section>

{{-- ══════════════════════════════════════════════════════
     SERVIÇOS
══════════════════════════════════════════════════════ --}}
<section class="py-28 bg-dark-950 relative overflow-hidden">
    <div class="absolute inset-0 grid-pattern opacity-[0.35] pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-20 fade-up">
            <span class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full badge-brand text-xs font-semibold mb-5">
                O que fazemos
            </span>
            <h2 class="text-4xl lg:text-5xl font-black text-white leading-tight mb-5">
                Tudo que seu negócio<br>precisa para <span class="grad-text">crescer online</span>
            </h2>
            <p class="text-gray-500 max-w-lg mx-auto">Sem complicação, sem enrolação. Soluções completas com acompanhamento em cada passo.</p>
        </div>

        <div class="grid md:grid-cols-2 lg:grid-cols-3 gap-5">
            @foreach([
                ['emoji' => '🌐', 'title' => 'Site Profissional',       'desc' => 'Seu negócio na internet com um site bonito, rápido e que aparece no Google. Sem mensalidades escondidas.',            'badge' => 'Mais pedido', 'badgeColor' => 'brand'],
                ['emoji' => '🛒', 'title' => 'Loja Virtual',            'desc' => 'Venda seus produtos 24 horas por dia, receba pelo cartão e gerencie pedidos pelo celular.',                           'badge' => null,          'badgeColor' => null],
                ['emoji' => '🤖', 'title' => 'IA para Atendimento',     'desc' => 'Assistente inteligente que responde clientes no WhatsApp e Instagram enquanto você trabalha.',                         'badge' => 'Novidade',    'badgeColor' => 'green'],
                ['emoji' => '⚙️', 'title' => 'Sistemas sob Medida',     'desc' => 'Controle de estoque, agendamentos, vendas e mais. Sistema feito do zero pro seu jeito de trabalhar.',                'badge' => null,          'badgeColor' => null],
                ['emoji' => '📈', 'title' => 'Automação de Processos',  'desc' => 'Automatize tarefas repetitivas: orçamentos, lembretes para clientes, relatórios automáticos.',                        'badge' => null,          'badgeColor' => null],
                ['emoji' => '📊', 'title' => 'Painel de Resultados',    'desc' => 'Veja em tempo real visitas, contatos e conversões. Dados que ajudam a tomar decisões melhores.',                      'badge' => null,          'badgeColor' => null],
            ] as $s)
            <div class="glass-card-hover rounded-3xl p-7 relative overflow-hidden group fade-up">
                @if($s['badge'])
                <div class="absolute top-5 right-5 px-2.5 py-1 rounded-full text-[10px] font-bold {{ $s['badgeColor'] === 'green' ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20' : 'bg-brand-500/15 text-brand-300 border border-brand-500/20' }}">
                    {{ $s['badge'] }}
                </div>
                @endif
                <div class="text-4xl mb-6">{{ $s['emoji'] }}</div>
                <h3 class="text-lg font-bold text-white mb-2.5">{{ $s['title'] }}</h3>
                <p class="text-gray-500 text-sm leading-relaxed mb-6">{{ $s['desc'] }}</p>
                <div class="flex items-center gap-1.5 text-brand-400 text-sm font-semibold opacity-0 group-hover:opacity-100 transition-all duration-300 translate-y-2 group-hover:translate-y-0">
                    Saiba mais
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3"/></svg>
                </div>
            </div>
            @endforeach
        </div>

        <div class="text-center mt-10 fade-up">
            <a href="/services" class="inline-flex items-center gap-1.5 text-sm font-semibold text-brand-400 hover:text-brand-300 transition">
                Ver todos os serviços
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3"/></svg>
            </a>
        </div>
    </div>
</section>

{{-- ══════════════════════════════════════════════════════
     COMO FUNCIONA
══════════════════════════════════════════════════════ --}}
<section class="py-28 bg-dark-900 relative overflow-hidden">
    <div class="absolute inset-0 dot-pattern opacity-25 pointer-events-none"></div>
    <div class="absolute top-0 left-0 right-0 h-px line-glow pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-20 fade-up">
            <span class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full badge-brand text-xs font-semibold mb-5">
                Processo simples
            </span>
            <h2 class="text-4xl lg:text-5xl font-black text-white mb-5">
                Do zero ao online<br><span class="grad-text">em poucos dias</span>
            </h2>
        </div>

        <div class="grid md:grid-cols-4 gap-5">
            @foreach([
                ['num' => '01', 'title' => 'Conversa rápida',        'desc' => 'Você conta o que precisa, a gente entende seu negócio e apresenta a melhor solução.'],
                ['num' => '02', 'title' => 'Proposta clara',         'desc' => 'Preço fechado, prazo definido, sem surpresas. Você aprova e a gente começa.'],
                ['num' => '03', 'title' => 'Desenvolvemos juntos',   'desc' => 'Você acompanha o progresso. Nada fica pronto sem você aprovar cada detalhe.'],
                ['num' => '04', 'title' => 'Entregamos e suportamos','desc' => 'Publicamos, treinamos sua equipe e ficamos disponíveis sempre que precisar.'],
            ] as $step)
            <div class="glass-card-hover rounded-3xl p-7 relative overflow-hidden fade-up">
                <div class="absolute top-4 right-4 text-6xl font-black leading-none select-none pointer-events-none" style="opacity:0.04; background: linear-gradient(135deg, #c084fc, #22d3ee); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;">{{ $step['num'] }}</div>
                <div class="w-8 h-8 rounded-xl btn-brand flex items-center justify-center text-xs font-bold text-white mb-5 relative z-10">
                    <span>{{ $step['num'] }}</span>
                </div>
                <h3 class="text-base font-bold text-white mb-2.5">{{ $step['title'] }}</h3>
                <p class="text-gray-500 text-sm leading-relaxed">{{ $step['desc'] }}</p>
            </div>
            @endforeach
        </div>
    </div>
</section>

{{-- ══════════════════════════════════════════════════════
     IA SECTION
══════════════════════════════════════════════════════ --}}
<section class="py-28 bg-dark-950 relative overflow-hidden">
    <div class="absolute right-0 top-1/2 -translate-y-1/2 w-[500px] h-[500px] rounded-full bg-accent-500/[0.04] blur-[100px] pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="grid lg:grid-cols-2 gap-20 items-center">
            <div class="fade-up">
                <span class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-purple-500/10 border border-purple-500/20 text-purple-400 text-xs font-semibold mb-7">
                    🤖 Inteligência Artificial
                </span>
                <h2 class="text-4xl lg:text-5xl font-black text-white leading-tight mb-7">
                    IA que trabalha<br>enquanto você<br><span class="grad-text">descansa</span>
                </h2>
                <p class="text-gray-400 leading-relaxed mb-8 text-lg">
                    Um atendente disponível 24 horas, que nunca fica mal humorado, responde dúvidas, manda orçamentos e agenda horários — sem você precisar mexer em nada.
                </p>
                <div class="space-y-3">
                    @foreach([
                        ['icon' => '💬', 'text' => 'Responde clientes no WhatsApp e Instagram automaticamente'],
                        ['icon' => '📋', 'text' => 'Envia orçamentos e agendamentos sem você intervir'],
                        ['icon' => '📊', 'text' => 'Identifica os clientes mais propensos a comprar'],
                        ['icon' => '🔔', 'text' => 'Lembretes automáticos para clientes que sumiram'],
                    ] as $item)
                    <div class="flex items-center gap-4 p-4 rounded-2xl border border-white/[0.05] bg-white/[0.02] hover:border-brand-500/25 hover:bg-white/[0.04] transition group">
                        <span class="text-xl">{{ $item['icon'] }}</span>
                        <span class="text-sm text-gray-400 group-hover:text-gray-300 transition">{{ $item['text'] }}</span>
                    </div>
                    @endforeach
                </div>
            </div>

            {{-- WhatsApp chat mock --}}
            <div class="fade-up fade-up-delay-2">
                <div class="bg-dark-800 rounded-3xl overflow-hidden border border-white/[0.07] shadow-2xl">
                    {{-- Chat header --}}
                    <div class="flex items-center gap-3 px-5 py-4 bg-dark-700 border-b border-white/[0.05]">
                        <div class="w-9 h-9 rounded-full bg-green-500/20 border border-green-500/30 flex items-center justify-center">
                            <svg class="w-5 h-5 text-green-400" fill="currentColor" viewBox="0 0 24 24"><path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.940 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z"/></svg>
                        </div>
                        <div>
                            <div class="text-sm font-semibold text-white">Barbearia do João</div>
                            <div class="flex items-center gap-1 text-[10px] text-green-400">
                                <span class="w-1.5 h-1.5 rounded-full bg-green-400 inline-block"></span>
                                IA ativa · agora
                            </div>
                        </div>
                    </div>

                    {{-- Messages --}}
                    <div class="p-5 space-y-4">
                        <div class="flex gap-2.5">
                            <div class="w-7 h-7 rounded-full bg-gray-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">C</div>
                            <div class="bg-dark-700 rounded-2xl rounded-tl-none px-4 py-3 max-w-[78%]">
                                <p class="text-sm text-gray-200">Oi! Quanto custa um corte masculino?</p>
                                <p class="text-[10px] text-gray-600 mt-1">14:32</p>
                            </div>
                        </div>
                        <div class="flex gap-2.5 flex-row-reverse">
                            <div class="w-7 h-7 rounded-full btn-brand flex items-center justify-center text-white text-[10px] font-bold flex-shrink-0 relative z-10">IA</div>
                            <div class="bg-brand-900/30 border border-brand-500/15 rounded-2xl rounded-tr-none px-4 py-3 max-w-[78%]">
                                <p class="text-sm text-gray-200">Oi! 💈 Corte masculino é R$ 35. Tenho horário hoje às 16h ou 18h — qual prefere?</p>
                                <p class="text-[10px] text-gray-600 mt-1">14:32 · IA</p>
                            </div>
                        </div>
                        <div class="flex gap-2.5">
                            <div class="w-7 h-7 rounded-full bg-gray-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">C</div>
                            <div class="bg-dark-700 rounded-2xl rounded-tl-none px-4 py-3 max-w-[78%]">
                                <p class="text-sm text-gray-200">18h tá ótimo</p>
                                <p class="text-[10px] text-gray-600 mt-1">14:33</p>
                            </div>
                        </div>
                        <div class="flex gap-2.5 flex-row-reverse">
                            <div class="w-7 h-7 rounded-full btn-brand flex items-center justify-center text-white text-[10px] font-bold flex-shrink-0 relative z-10">IA</div>
                            <div class="bg-brand-900/30 border border-brand-500/15 rounded-2xl rounded-tr-none px-4 py-3 max-w-[78%]">
                                <p class="text-sm text-gray-200">Agendado! ✅ Hoje às 18h. Vou te lembrar 1h antes. Até logo!</p>
                                <p class="text-[10px] text-gray-600 mt-1">14:33 · IA</p>
                            </div>
                        </div>
                    </div>

                    <div class="px-5 pb-4 text-[11px] text-gray-600 text-center border-t border-white/[0.04] pt-3">
                        Agendamento em 1 min — João não precisou responder nada
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>

{{-- ══════════════════════════════════════════════════════
     DEPOIMENTOS
══════════════════════════════════════════════════════ --}}
<section class="py-28 bg-dark-900 relative overflow-hidden">
    <div class="absolute top-0 left-0 right-0 h-px line-glow pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-20 fade-up">
            <h2 class="text-4xl lg:text-5xl font-black text-white mb-5">Quem usou, aprovou</h2>
            <p class="text-gray-500">Negócios reais que cresceram com a Meada</p>
        </div>
        <div class="grid md:grid-cols-3 gap-5">
            @foreach([
                ['name' => 'Marina Costa',  'biz' => 'Lavanderia Express',    'emoji' => '🧺', 'color' => 'from-emerald-500 to-teal-600', 'msg' => 'Antes eu perdia cliente porque não conseguia atender no WhatsApp durante o dia. Com a IA da Meada, ela agenda tudo pra mim. Meu faturamento subiu 40% em 3 meses!'],
                ['name' => 'Rafael Mendes', 'biz' => 'Restaurante Sabor Arte', 'emoji' => '🍽️', 'color' => 'from-orange-500 to-red-600',   'msg' => 'O site aparece no Google quando pesquisam restaurante aqui na minha cidade. Hoje recebo reservas online toda semana sem fazer nada.'],
                ['name' => 'Camila Freitas','biz' => 'Studio Bela',            'emoji' => '💅', 'color' => 'from-pink-500 to-purple-600',  'msg' => 'Meu salão tinha sistema nenhum, tudo no caderninho. A Meada fez um sistema de agendamento que minhas clientes amam. Sem cancelamentos, sem confusão.'],
            ] as $t)
            <div class="glass-card-hover rounded-3xl p-8 relative overflow-hidden fade-up">
                <div class="absolute top-0 right-0 w-32 h-32 rounded-full bg-brand-500/[0.04] -translate-y-1/2 translate-x-1/2 pointer-events-none"></div>
                <div class="flex items-center gap-0.5 mb-6">
                    @for($i=0;$i<5;$i++)
                    <svg class="w-4 h-4 text-yellow-400" fill="currentColor" viewBox="0 0 20 20"><path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z"/></svg>
                    @endfor
                </div>
                <p class="text-gray-300 text-sm leading-relaxed mb-8">"{{ $t['msg'] }}"</p>
                <div class="flex items-center gap-3">
                    <div class="w-10 h-10 rounded-2xl bg-gradient-to-br {{ $t['color'] }} flex items-center justify-center text-lg">{{ $t['emoji'] }}</div>
                    <div>
                        <div class="font-bold text-sm text-white">{{ $t['name'] }}</div>
                        <div class="text-xs text-gray-500 mt-0.5">{{ $t['biz'] }}</div>
                    </div>
                </div>
            </div>
            @endforeach
        </div>
    </div>
</section>

{{-- ══════════════════════════════════════════════════════
     CTA FINAL
══════════════════════════════════════════════════════ --}}
<section class="py-32 relative overflow-hidden bg-dark-950">
    <div class="absolute inset-0 dot-pattern opacity-20 pointer-events-none"></div>
    <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] h-[400px] rounded-full bg-brand-600/[0.07] blur-[100px] pointer-events-none"></div>
    <div class="absolute top-0 left-0 right-0 h-px line-glow pointer-events-none"></div>

    <div class="relative max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl btn-brand mb-8 fade-up">
            <svg class="w-8 h-8 text-white relative z-10" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
        </div>
        <h2 class="text-4xl lg:text-6xl font-black text-white mb-7 leading-tight fade-up fade-up-delay-1">
            Pronto para levar<br>seu negócio para o<br><span class="grad-text">próximo nível?</span>
        </h2>
        <p class="text-gray-400 text-lg mb-10 fade-up fade-up-delay-2">Conversa sem compromisso. A gente entende o que você precisa e mostra o que é possível.</p>
        <div class="flex flex-col sm:flex-row gap-4 justify-center fade-up fade-up-delay-3">
            <a href="/contact" class="inline-flex items-center justify-center gap-2 px-8 py-4 text-sm font-bold text-white btn-brand rounded-2xl">
                <span>Quero falar com a Meada</span>
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3"/></svg>
            </a>
            <a href="https://wa.me/5581995489984" target="_blank" class="inline-flex items-center justify-center gap-2 px-8 py-4 text-sm font-semibold btn-outline rounded-2xl">
                <svg class="w-4 h-4 text-green-400" fill="currentColor" viewBox="0 0 24 24"><path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.940 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z"/></svg>
                WhatsApp agora
            </a>
        </div>
    </div>
</section>

</x-layouts.app>
