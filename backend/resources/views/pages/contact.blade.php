<x-layouts.app title="Contato">

<section class="relative grad-hero pt-36 pb-28 overflow-hidden">
    <div class="absolute inset-0 dot-pattern opacity-25 pointer-events-none"></div>
    <div class="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-dark-950 to-transparent pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="max-w-4xl">
            <span class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full badge-brand text-xs font-semibold mb-7 fade-up">Fale Conosco</span>
            <h1 class="text-5xl lg:text-7xl font-black text-white leading-[1.05] tracking-tight mb-7 fade-up fade-up-delay-1">
                Vamos colocar seu<br>negocio no<br><span class="grad-text">proximo nivel?</span>
            </h1>
            <p class="text-xl text-gray-400 max-w-2xl fade-up fade-up-delay-2">
                Conta o que voce precisa e a gente te apresenta a melhor solucao. Rapido e sem enrolacao.
            </p>
        </div>
    </div>
</section>

<section class="py-28 bg-dark-950 relative overflow-hidden">
    <div class="absolute inset-0 grid-pattern opacity-[0.25] pointer-events-none"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="grid lg:grid-cols-5 gap-16">

            {{-- Form --}}
            <div class="lg:col-span-3 fade-up">
                <h2 class="text-3xl font-black text-white mb-8">Pede um orcamento gratis</h2>
                <form action="#" method="POST" class="space-y-5">
                    @csrf
                    <div class="grid sm:grid-cols-2 gap-5">
                        <div>
                            <label class="block text-sm font-semibold text-gray-400 mb-2">Seu nome</label>
                            <input type="text" name="name" required placeholder="Joao Silva"
                                class="w-full px-4 py-3.5 rounded-2xl text-sm text-white placeholder-gray-600 bg-white/[0.04] border border-white/[0.08] focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent transition">
                        </div>
                        <div>
                            <label class="block text-sm font-semibold text-gray-400 mb-2">WhatsApp</label>
                            <input type="tel" name="phone" placeholder="(99) 9 9999-9999"
                                class="w-full px-4 py-3.5 rounded-2xl text-sm text-white placeholder-gray-600 bg-white/[0.04] border border-white/[0.08] focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent transition">
                        </div>
                    </div>
                    <div>
                        <label class="block text-sm font-semibold text-gray-400 mb-2">E-mail</label>
                        <input type="email" name="email" required placeholder="joao@meurestaurante.com"
                            class="w-full px-4 py-3.5 rounded-2xl text-sm text-white placeholder-gray-600 bg-white/[0.04] border border-white/[0.08] focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent transition">
                    </div>
                    <div>
                        <label class="block text-sm font-semibold text-gray-400 mb-2">Qual e o seu negocio?</label>
                        <select name="segment"
                            class="w-full px-4 py-3.5 rounded-2xl text-sm text-gray-400 bg-dark-800 border border-white/[0.08] focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent transition">
                            <option value="">Selecione o segmento...</option>
                            <option>Restaurante / Lanchonete / Bar</option>
                            <option>Loja de Roupas / Moda</option>
                            <option>Salao / Barbearia / Estetica</option>
                            <option>Clinica / Consultorio</option>
                            <option>Lavanderia / Limpeza</option>
                            <option>Prestador de Servicos</option>
                            <option>Outro</option>
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-semibold text-gray-400 mb-2">O que voce precisa?</label>
                        <select name="service"
                            class="w-full px-4 py-3.5 rounded-2xl text-sm text-gray-400 bg-dark-800 border border-white/[0.08] focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent transition">
                            <option value="">Selecione o servico...</option>
                            <option>Site Profissional</option>
                            <option>Loja Virtual</option>
                            <option>IA para Atendimento (WhatsApp)</option>
                            <option>Sistema sob Medida</option>
                            <option>Nao sei ainda, preciso de orientacao</option>
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-semibold text-gray-400 mb-2">Conta mais sobre o que voce precisa</label>
                        <textarea name="message" rows="4" required placeholder="Ex: Tenho uma pizzaria e quero que os clientes possam fazer pedidos pelo meu site e WhatsApp..."
                            class="w-full px-4 py-3.5 rounded-2xl text-sm text-white placeholder-gray-600 bg-white/[0.04] border border-white/[0.08] focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent resize-none transition"></textarea>
                    </div>
                    <div class="flex items-center gap-4 pt-2">
                        <button type="submit" class="inline-flex items-center gap-2 px-8 py-4 text-sm font-bold text-white btn-brand rounded-2xl">
                            <span>Enviar mensagem</span>
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
                        </button>
                        <p class="text-xs text-gray-600">Respondemos em ate 2 horas no horario comercial</p>
                    </div>
                </form>
            </div>

            {{-- Sidebar --}}
            <div class="lg:col-span-2 space-y-5 fade-up fade-up-delay-2">
                <div class="glass-card rounded-3xl p-7 border border-white/[0.07]">
                    <h3 class="font-black text-white text-lg mb-6">Outras formas de falar</h3>
                    <div class="space-y-3">
                        <a href="https://wa.me/5581995489984" target="_blank" class="flex items-center gap-4 p-4 rounded-2xl transition hover:bg-white/[0.04] border border-white/[0.05] hover:border-green-500/30 group">
                            <div class="w-11 h-11 bg-green-500 rounded-xl flex items-center justify-center flex-shrink-0">
                                <svg class="w-5 h-5 text-white" fill="currentColor" viewBox="0 0 24 24"><path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.940 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z"/></svg>
                            </div>
                            <div>
                                <div class="text-sm font-bold text-white group-hover:text-green-400 transition">Falar pelo WhatsApp</div>
                                <div class="text-xs text-gray-500 mt-0.5">(81) 99548-9984 · Resposta rapida</div>
                            </div>
                        </a>

                        <div class="flex items-center gap-4 p-4 rounded-2xl border border-white/[0.05]">
                            <div class="w-11 h-11 bg-brand-600/20 border border-brand-500/20 rounded-xl flex items-center justify-center flex-shrink-0">
                                <svg class="w-5 h-5 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/></svg>
                            </div>
                            <div>
                                <div class="text-sm font-bold text-white">E-mail</div>
                                <div class="text-xs text-gray-500 mt-0.5">oi@meadadigital.com</div>
                            </div>
                        </div>

                        <a href="https://instagram.com/meadaagencia" target="_blank" class="flex items-center gap-4 p-4 rounded-2xl transition hover:bg-white/[0.04] border border-white/[0.05] hover:border-pink-500/30 group">
                            <div class="w-11 h-11 bg-pink-600/20 border border-pink-500/20 rounded-xl flex items-center justify-center flex-shrink-0">
                                <svg class="w-5 h-5 text-pink-400" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zm0-2.163c-3.259 0-3.667.014-4.947.072-4.358.2-6.78 2.618-6.98 6.98-.059 1.281-.073 1.689-.073 4.948 0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98-1.281-.059-1.69-.073-4.949-.073zm0 5.838c-3.403 0-6.162 2.759-6.162 6.162s2.759 6.163 6.162 6.163 6.162-2.759 6.162-6.163c0-3.403-2.759-6.162-6.162-6.162zm0 10.162c-2.209 0-4-1.79-4-4 0-2.209 1.791-4 4-4s4 1.791 4 4c0 2.21-1.791 4-4 4zm6.406-11.845c-.796 0-1.441.645-1.441 1.44s.645 1.44 1.441 1.44c.795 0 1.439-.645 1.439-1.44s-.644-1.44-1.439-1.44z"/></svg>
                            </div>
                            <div>
                                <div class="text-sm font-bold text-white group-hover:text-pink-400 transition">Instagram</div>
                                <div class="text-xs text-gray-500 mt-0.5">@meadaagencia</div>
                            </div>
                        </a>
                    </div>
                </div>

                <div class="rounded-3xl p-8 relative overflow-hidden" style="background: linear-gradient(135deg,#7c3aed,#0891b2);">
                    <div class="absolute inset-0 dot-pattern opacity-[0.12]"></div>
                    <div class="absolute top-0 right-0 w-40 h-40 rounded-full bg-white/[0.05] -translate-y-1/2 translate-x-1/2 pointer-events-none"></div>
                    <div class="relative">
                        <div class="text-4xl mb-4">⚡</div>
                        <h3 class="font-black text-white text-xl mb-3">Orcamento em ate 2 horas</h3>
                        <p class="text-sm text-white/70 leading-relaxed">Respondemos todos os contatos no mesmo dia. Sem enrolacao, sem proposta de 50 paginas. Uma conversa simples sobre o que voce precisa.</p>
                    </div>
                </div>
            </div>

        </div>
    </div>
</section>

</x-layouts.app>
