@extends('layouts.app')

@section('title', $product->title)
@section('description', \Illuminate\Support\Str::limit(strip_tags((string) $product->description), 150))

@section('content')
    <div class="container-doce py-6">
        {{-- Breadcrumb --}}
        <nav class="mb-5 flex flex-wrap items-center gap-1 text-sm text-neutral-500">
            <a href="{{ route('home') }}" class="hover:text-brand-700">Início</a>
            @if($product->category)
                <span>/</span>
                <a href="{{ $product->category->url }}" class="hover:text-brand-700">{{ $product->category->name }}</a>
            @endif
            <span>/</span>
            <span class="line-clamp-1 font-medium text-neutral-700">{{ $product->title }}</span>
        </nav>

        <div class="grid gap-8 lg:grid-cols-12">
            {{-- Galeria --}}
            @php($galleryProps = ['images' => $product->images->map(fn ($i) => ['path' => $i->path, 'alt' => $i->alt ?: $product->title])->values()])
            <div class="lg:col-span-7">
                <div data-island="ProductGallery" data-props='@json($galleryProps)'>
                    <div class="aspect-square overflow-hidden rounded-3xl border border-neutral-200 bg-white">
                        <img src="{{ $product->primary_image_url }}" alt="{{ $product->title }}" class="h-full w-full object-cover">
                    </div>
                </div>
            </div>

            {{-- Buy box --}}
            <div class="lg:col-span-5">
                <div class="flex flex-wrap items-center gap-2">
                    <span class="chip bg-neutral-100 text-neutral-600">{{ $product->unit_label }}</span>
                    @if($product->is_made_to_order)
                        <span class="chip bg-caramel-100 text-caramel-700">🎂 Sob encomenda</span>
                    @else
                        <span class="chip bg-pistache-100 text-pistache-600">⚡ Pronta-entrega</span>
                    @endif
                    @if($product->serves)<span class="chip bg-brand-50 text-brand-700">🍽️ {{ $product->serves }}</span>@endif
                </div>

                <h1 class="mt-3 text-2xl font-extrabold leading-tight text-neutral-900 sm:text-3xl">{{ $product->title }}</h1>

                @if($product->flavor)
                    <p class="mt-1 text-base text-neutral-500">{{ $product->flavor }}</p>
                @endif

                @if($product->reviews_count > 0)
                    <div class="mt-2 flex flex-wrap items-center gap-3 text-sm text-neutral-500">
                        <span class="flex items-center gap-1 text-caramel-500">
                            @for($i = 1; $i <= 5; $i++)
                                <svg class="h-4 w-4 {{ $i <= round($product->rating) ? '' : 'text-neutral-300' }}" fill="currentColor" viewBox="0 0 20 20"><path d="M10 15l-5.878 3.09 1.123-6.545L.489 6.91l6.572-.955L10 0l2.939 5.955 6.572.955-4.756 4.635 1.123 6.545z"/></svg>
                            @endfor
                            <span class="ml-1 font-semibold text-neutral-600">{{ number_format($product->rating, 1, ',', '') }}</span>
                        </span>
                        <span>·</span>
                        <span>{{ $product->reviews_count }} avaliações</span>
                        @if($product->sold_count > 0)<span>·</span><span>{{ $product->sold_count }} vendidos</span>@endif
                    </div>
                @endif

                {{-- Preço --}}
                <div class="mt-5 rounded-3xl border border-neutral-200 bg-white p-5">
                    @if($product->compare_at_price && $product->compare_at_price > $product->price)
                        <div class="flex items-center gap-2">
                            <span class="text-sm text-neutral-400 line-through">{{ money($product->compare_at_price) }}</span>
                            <span class="chip bg-brand-600 text-white">-{{ $product->discount_percent }}%</span>
                        </div>
                    @endif
                    <p class="mt-1 text-4xl font-extrabold text-neutral-900">
                        {{ money($product->price) }}
                        <span class="text-base font-medium text-neutral-500">{{ $product->price_suffix }}</span>
                    </p>
                    @if($product->min_qty > 1)
                        <p class="mt-1 text-sm text-neutral-500">Pedido mínimo: <strong class="text-neutral-700">{{ $product->min_qty }} {{ $product->unit_label }}</strong></p>
                    @endif

                    @if($product->is_made_to_order)
                        {{-- Sob encomenda: NÃO vai ao carrinho, leva ao formulário de orçamento. --}}
                        <div class="mt-4 rounded-2xl bg-caramel-100 p-4 text-sm text-caramel-700">
                            🎂 <strong>Sob encomenda.</strong>
                            @if($product->lead_time_days)
                                Faça o pedido com pelo menos <strong>{{ $product->lead_time_days }} {{ \Illuminate\Support\Str::plural('dia', $product->lead_time_days) }}</strong> de antecedência.
                            @else
                                Combinamos o prazo junto com o seu orçamento.
                            @endif
                        </div>
                        <a href="{{ route('custom-orders.create', ['product' => $product->slug]) }}" class="btn-caramel mt-4 w-full">🎂 Encomendar este item</a>
                    @elseif($product->optionGroups->isNotEmpty())
                        {{-- Pronta-entrega com opções: ilha ProductOrder. --}}
                        @php($orderProps = [
                            'product' => $product->toCartPayload(),
                            'groups' => $product->optionGroups->map(fn ($g) => [
                                'id' => $g->id,
                                'name' => $g->name,
                                'minSelect' => (int) $g->min_select,
                                'maxSelect' => (int) $g->max_select,
                                'required' => (bool) $g->is_required,
                                'options' => $g->options->map(fn ($o) => [
                                    'id' => $o->id,
                                    'name' => $o->name,
                                    'delta' => (float) $o->price_delta,
                                ])->values(),
                            ])->values(),
                        ])
                        <div class="mt-5" data-island="ProductOrder" data-props='@json($orderProps)'>
                            <a href="{{ route('cart') }}" class="btn-brand w-full">Montar e adicionar</a>
                        </div>
                    @else
                        {{-- Pronta-entrega sem opções: adiciona direto. --}}
                        @php($buyProps = ['product' => $product->toCartPayload(), 'variant' => 'full'])
                        <div class="mt-5" data-island="AddToCart" data-props='@json($buyProps)'>
                            <a href="{{ route('cart') }}" class="btn-brand w-full">Adicionar à sacola</a>
                        </div>
                    @endif
                </div>

                {{-- Precisa para uma data especial? --}}
                @unless($product->is_made_to_order)
                    <p class="mt-4 text-center text-sm text-neutral-500">
                        Quer uma versão personalizada?
                        <a href="{{ route('custom-orders.create', ['product' => $product->slug]) }}" class="font-semibold text-brand-700 hover:underline">Faça uma encomenda 🎂</a>
                    </p>
                @endunless
            </div>
        </div>

        {{-- Descrição + detalhes --}}
        <div class="mt-10 grid gap-8 lg:grid-cols-12">
            <div class="lg:col-span-7">
                <h2 class="mb-3 text-lg font-bold text-neutral-900">Sobre este docinho</h2>
                <p class="whitespace-pre-line leading-relaxed text-neutral-600">{{ $product->description }}</p>
                @if($product->contains_allergens)
                    <div class="mt-4 rounded-2xl bg-caramel-100 p-4 text-sm text-caramel-700">
                        ⚠️ <strong>Contém alérgenos:</strong> {{ $product->contains_allergens }}
                    </div>
                @endif
            </div>
            <div class="lg:col-span-5">
                <h2 class="mb-3 text-lg font-bold text-neutral-900">Detalhes</h2>
                <dl class="divide-y divide-neutral-100 overflow-hidden rounded-2xl border border-neutral-200 bg-white text-sm">
                    @foreach(array_filter([
                        'Sabor' => $product->flavor,
                        'Vendido por' => $product->unit_label,
                        'Serve' => $product->serves,
                        'Preparo' => $product->prep_minutes ? $product->prep_minutes . ' min' : null,
                        'Antecedência' => $product->is_made_to_order && $product->lead_time_days ? $product->lead_time_days . ' dia(s)' : null,
                        'Código' => $product->sku,
                    ]) as $label => $value)
                        <div class="flex justify-between gap-4 px-4 py-3">
                            <dt class="text-neutral-500">{{ $label }}</dt>
                            <dd class="text-right font-medium text-neutral-800">{{ $value }}</dd>
                        </div>
                    @endforeach
                </dl>
            </div>
        </div>
    </div>

    {{-- Relacionados --}}
    @include('partials.product-carousel', [
        'title' => 'Você também vai gostar',
        'products' => $related,
        'seeAllUrl' => $product->category?->url,
    ])
@endsection
