<nav class="fixed top-0 w-full z-50 transition-all duration-300" id="navbar">
    <div class="absolute inset-0 bg-dark-950/80 backdrop-blur-2xl border-b border-white/[0.04]"></div>
    <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16 lg:h-[68px]">
            <a href="/" class="flex items-center gap-2.5 group">
                <div class="w-8 h-8 btn-brand rounded-xl flex items-center justify-center shadow-lg">
                    <span class="text-white font-black text-sm relative z-10">M</span>
                </div>
                <span class="text-[17px] font-bold text-white tracking-tight">meada<span class="grad-text">.</span></span>
            </a>

            <div class="hidden lg:flex items-center gap-1">
                <a href="/" class="px-3.5 py-2 text-sm font-medium text-gray-400 hover:text-white transition rounded-xl hover:bg-white/[0.04]">Início</a>
                <a href="/about" class="px-3.5 py-2 text-sm font-medium text-gray-400 hover:text-white transition rounded-xl hover:bg-white/[0.04]">Sobre</a>
                <a href="/services" class="px-3.5 py-2 text-sm font-medium text-gray-400 hover:text-white transition rounded-xl hover:bg-white/[0.04]">Serviços</a>
                <a href="/solutions" class="px-3.5 py-2 text-sm font-medium text-gray-400 hover:text-white transition rounded-xl hover:bg-white/[0.04]">Soluções</a>
                <a href="/portfolio" class="px-3.5 py-2 text-sm font-medium text-gray-400 hover:text-white transition rounded-xl hover:bg-white/[0.04]">Portfólio</a>
                <a href="/blog" class="px-3.5 py-2 text-sm font-medium text-gray-400 hover:text-white transition rounded-xl hover:bg-white/[0.04]">Blog</a>
            </div>

            <div class="hidden lg:flex items-center gap-3">
                <a href="https://wa.me/5581995489984" target="_blank" class="flex items-center gap-2 px-3.5 py-2 text-sm font-medium text-gray-400 hover:text-white transition rounded-xl hover:bg-white/[0.04]">
                    <svg class="w-4 h-4 text-green-400" fill="currentColor" viewBox="0 0 24 24"><path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.940 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z"/></svg>
                    WhatsApp
                </a>
                <a href="/contact" class="inline-flex items-center px-5 py-2.5 text-sm font-semibold text-white btn-brand rounded-xl">
                    <span>Fale Conosco</span>
                </a>
            </div>

            <button id="mobile-menu-btn" class="lg:hidden p-2 text-gray-400 hover:text-white transition">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16"/>
                </svg>
            </button>
        </div>

        <div id="mobile-menu" class="hidden lg:hidden pb-5 border-t border-white/[0.05] pt-4">
            <div class="flex flex-col gap-0.5">
                <a href="/" class="px-3 py-2.5 text-sm font-medium text-gray-400 hover:text-white hover:bg-white/[0.05] rounded-xl transition">Início</a>
                <a href="/about" class="px-3 py-2.5 text-sm font-medium text-gray-400 hover:text-white hover:bg-white/[0.05] rounded-xl transition">Sobre</a>
                <a href="/services" class="px-3 py-2.5 text-sm font-medium text-gray-400 hover:text-white hover:bg-white/[0.05] rounded-xl transition">Serviços</a>
                <a href="/solutions" class="px-3 py-2.5 text-sm font-medium text-gray-400 hover:text-white hover:bg-white/[0.05] rounded-xl transition">Soluções</a>
                <a href="/portfolio" class="px-3 py-2.5 text-sm font-medium text-gray-400 hover:text-white hover:bg-white/[0.05] rounded-xl transition">Portfólio</a>
                <a href="/blog" class="px-3 py-2.5 text-sm font-medium text-gray-400 hover:text-white hover:bg-white/[0.05] rounded-xl transition">Blog</a>
                <a href="/contact" class="mt-2 px-3 py-3 text-sm font-semibold text-white btn-brand rounded-xl text-center">Fale Conosco</a>
            </div>
        </div>
    </div>
</nav>
