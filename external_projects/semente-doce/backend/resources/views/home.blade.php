@extends('layouts.app')

@section('content')
    {{-- Hero (carrossel de banners) --}}
    @php($heroSlides = $heroBanners->map(fn ($b) => [
        'title' => $b->title,
        'subtitle' => $b->subtitle,
        'from' => $b->bg_from,
        'to' => $b->bg_to,
        'link' => $b->link_url,
        'ctaLabel' => $b->cta_label,
    ])->values())
    <section class="container-doce pt-6">
        <div data-island="HeroCarousel" data-props='@json(['slides' => $heroSlides])'>
            {{-- Fallback SSR: primeiro slide --}}
            @if($heroBanners->first())
                @php($b = $heroBanners->first())
                <div class="flex min-h-[240px] flex-col justify-center rounded-3xl px-8 py-12 text-white" style="background-image: linear-gradient(120deg, {{ $b->bg_from }}, {{ $b->bg_to }})">
                    <h2 class="text-3xl font-extrabold sm:text-4xl">{{ $b->title }}</h2>
                    <p class="mt-3 max-w-md text-white/90">{{ $b->subtitle }}</p>
                    @if($b->link_url)<a href="{{ $b->link_url }}" class="mt-6 inline-block w-fit rounded-full bg-white px-6 py-3 font-bold text-neutral-900">{{ $b->cta_label }}</a>@endif
                </div>
            @endif
        </div>
    </section>

    {{-- Atalhos por categoria --}}
    @if($categories->isNotEmpty())
        <section class="container-doce pt-10">
            <h2 class="mb-5 text-xl font-extrabold text-neutral-900 sm:text-2xl">O que você quer adoçar hoje? 🍬</h2>
            <div class="grid grid-cols-3 gap-4 sm:grid-cols-6">
                @foreach($categories as $cat)
                    <a href="{{ $cat->url }}" class="group flex flex-col items-center gap-2 text-center">
                        <span class="flex h-16 w-16 items-center justify-center rounded-2xl text-3xl transition group-hover:-translate-y-1 sm:h-20 sm:w-20 sm:text-4xl" style="background-color: {{ $cat->accent }}1f">
                            {{ $cat->icon }}
                        </span>
                        <span class="text-xs font-medium text-neutral-600 group-hover:text-brand-700 sm:text-sm">{{ $cat->name }}</span>
                    </a>
                @endforeach
            </div>
        </section>
    @endif

    {{-- Faixa de encomendas (recurso estrela) --}}
    <section class="container-doce pt-10">
        <div class="flex flex-col items-center justify-between gap-4 overflow-hidden rounded-3xl bg-gradient-to-br from-caramel-400 to-brand-600 px-8 py-8 text-white sm:flex-row">
            <div>
                <p class="text-sm font-semibold uppercase tracking-wide text-white/80">Bolos, doces e festas sob medida</p>
                <h2 class="mt-1 text-2xl font-extrabold sm:text-3xl">🎂 Encomende do seu jeitinho</h2>
                <p class="mt-2 max-w-lg text-white/90">Conte o sabor, a data e a quantidade. A gente monta o orçamento e confirma com você — sem compromisso.</p>
            </div>
            <a href="{{ route('custom-orders.create') }}" class="shrink-0 rounded-full bg-white px-6 py-3 font-bold text-brand-700 transition hover:bg-neutral-100">Fazer uma encomenda</a>
        </div>
    </section>

    {{-- Ofertas --}}
    @include('partials.product-carousel', [
        'title' => '🔥 Ofertas do dia',
        'subtitle' => 'Docinhos com desconto especial',
        'products' => $deals,
        'seeAllUrl' => route('search', ['sort' => 'price_asc']),
    ])

    {{-- Tiles promocionais --}}
    @if($stripBanners->isNotEmpty())
        <section class="container-doce py-6">
            <div class="grid gap-4 sm:grid-cols-3">
                @foreach($stripBanners as $b)
                    <a href="{{ $b->link_url }}" class="group relative flex flex-col justify-between overflow-hidden rounded-3xl p-6 text-white transition hover:shadow-lg" style="background-image: linear-gradient(120deg, {{ $b->bg_from }}, {{ $b->bg_to }})">
                        <div>
                            <p class="text-lg font-extrabold">{{ $b->title }}</p>
                            <p class="text-sm text-white/85">{{ $b->subtitle }}</p>
                        </div>
                        <span class="mt-6 inline-flex w-fit items-center gap-1 text-sm font-bold">
                            {{ $b->cta_label }}
                            <svg class="h-4 w-4 transition group-hover:translate-x-1" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" /></svg>
                        </span>
                    </a>
                @endforeach
            </div>
        </section>
    @endif

    {{-- Pronta-entrega / novidades --}}
    @include('partials.product-carousel', [
        'title' => '⚡ Pronta-entrega & novidades',
        'subtitle' => 'Fresquinhos, prontos para levar hoje',
        'products' => $newest,
        'seeAllUrl' => route('search', ['availability' => 'ready', 'sort' => 'newest']),
    ])

    {{-- Mais pedidos --}}
    @include('partials.product-carousel', [
        'title' => '🏆 Mais pedidos',
        'subtitle' => 'Os queridinhos da casa',
        'products' => $bestSellers,
        'seeAllUrl' => route('search', ['sort' => 'best_selling']),
    ])

    {{-- Kits em destaque --}}
    @if($featuredKits->isNotEmpty())
        <section class="container-doce py-6">
            <div class="mb-4 flex items-end justify-between gap-4">
                <div>
                    <h2 class="text-xl font-extrabold text-neutral-900 sm:text-2xl">🎁 Kits & cestas prontinhos</h2>
                    <p class="text-sm text-neutral-500">Combinações montadas pela doceria — pagando menos que item a item</p>
                </div>
                <a href="{{ route('kits.index') }}" class="hidden text-sm font-semibold text-brand-700 hover:underline sm:inline">Ver todos</a>
            </div>
            <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                @foreach($featuredKits as $kit)
                    @include('partials.kit-card', ['kit' => $kit])
                @endforeach
            </div>
        </section>
    @endif

    {{-- Sob encomenda --}}
    @include('partials.product-carousel', [
        'title' => '🎂 Sob encomenda',
        'subtitle' => 'Feitos especialmente para a sua data',
        'products' => $madeToOrder,
        'seeAllUrl' => route('search', ['availability' => 'order']),
    ])

    {{-- Vitrines por categoria --}}
    @foreach($showcases as $showcase)
        @include('partials.product-carousel', [
            'title' => $showcase['category']->icon . ' ' . $showcase['category']->name,
            'products' => $showcase['products'],
            'seeAllUrl' => $showcase['category']->url,
        ])
    @endforeach

    {{-- Chamada final --}}
    <section class="container-doce py-10">
        <div class="flex flex-col items-center gap-4 rounded-3xl bg-gradient-to-br from-brand-600 to-brand-800 px-8 py-12 text-center text-white">
            <span class="text-4xl">🧁</span>
            <h2 class="max-w-2xl text-2xl font-extrabold sm:text-3xl">Feito à mão, com aquele carinho de casa</h2>
            <p class="max-w-xl text-white/90">Do brigadeiro gourmet ao salgado da festa: escolha, personalize e receba quentinho no seu bairro.</p>
            <a href="{{ route('search') }}" class="mt-2 rounded-full bg-white px-6 py-3 font-bold text-brand-700 transition hover:bg-neutral-100">Ver todo o cardápio</a>
        </div>
    </section>
@endsection
