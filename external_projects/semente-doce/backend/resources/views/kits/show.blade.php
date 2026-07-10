@extends('layouts.app')

@section('title', $kit->name)
@section('description', \Illuminate\Support\Str::limit(strip_tags((string) $kit->description), 150))

@section('content')
    <div class="container-doce py-6">
        {{-- Breadcrumb --}}
        <nav class="mb-5 flex flex-wrap items-center gap-1 text-sm text-neutral-500">
            <a href="{{ route('home') }}" class="hover:text-brand-700">Início</a>
            <span>/</span>
            <a href="{{ route('kits.index') }}" class="hover:text-brand-700">Kits</a>
            <span>/</span>
            <span class="line-clamp-1 font-medium text-neutral-700">{{ $kit->name }}</span>
        </nav>

        <div class="grid gap-8 lg:grid-cols-12">
            {{-- Foto --}}
            <div class="lg:col-span-7">
                <div class="aspect-square overflow-hidden rounded-3xl border border-neutral-200 bg-white">
                    <img src="{{ $kit->image_url }}" alt="{{ $kit->name }}" class="h-full w-full object-cover">
                </div>
            </div>

            {{-- Buy box --}}
            <div class="lg:col-span-5">
                <div class="flex flex-wrap items-center gap-2">
                    <span class="chip bg-brand-600 text-white">🎁 Kit {{ $kit->type_label }}</span>
                    @if($kit->is_made_to_order)
                        <span class="chip bg-caramel-100 text-caramel-700">🎂 Sob encomenda</span>
                    @endif
                    @if($kit->serves)<span class="chip bg-neutral-100 text-neutral-600">🍽️ {{ $kit->serves }}</span>@endif
                </div>

                <h1 class="mt-3 text-2xl font-extrabold leading-tight text-neutral-900 sm:text-3xl">{{ $kit->name }}</h1>

                @if($kit->description)
                    <p class="mt-2 whitespace-pre-line leading-relaxed text-neutral-600">{{ $kit->description }}</p>
                @endif

                {{-- Componentes do kit --}}
                @if($kit->items->isNotEmpty())
                    <div class="mt-5 rounded-3xl border border-neutral-200 bg-white p-5">
                        <h2 class="mb-3 text-sm font-bold text-neutral-800">O que vem no kit</h2>
                        <ul class="divide-y divide-neutral-100 text-sm">
                            @foreach($kit->items as $item)
                                <li class="flex items-center justify-between gap-3 py-2">
                                    <span class="text-neutral-700">{{ $item->label }}</span>
                                    <span class="shrink-0 font-semibold text-neutral-500">{{ $item->qty }}×</span>
                                </li>
                            @endforeach
                        </ul>
                    </div>
                @endif

                {{-- Preço --}}
                <div class="mt-5 rounded-3xl border border-neutral-200 bg-white p-5">
                    @if($kit->savings > 0)
                        <div class="flex items-center gap-2">
                            <span class="text-sm text-neutral-400 line-through">{{ money($kit->components_total) }}</span>
                            <span class="chip bg-pistache-100 text-pistache-600">Economize {{ money($kit->savings) }}</span>
                        </div>
                    @endif
                    <p class="mt-1 text-4xl font-extrabold text-neutral-900">{{ money($kit->price) }}</p>

                    @if($kit->is_made_to_order)
                        <div class="mt-4 rounded-2xl bg-caramel-100 p-4 text-sm text-caramel-700">
                            🎂 <strong>Sob encomenda.</strong>
                            @if($kit->lead_time_days)
                                Peça com pelo menos <strong>{{ $kit->lead_time_days }} {{ \Illuminate\Support\Str::plural('dia', $kit->lead_time_days) }}</strong> de antecedência.
                            @else
                                Combinamos o prazo junto com o orçamento.
                            @endif
                        </div>
                        <a href="{{ route('custom-orders.create', ['kit' => $kit->slug]) }}" class="btn-caramel mt-4 w-full">🎂 Encomendar este kit</a>
                    @else
                        @php($buyProps = ['product' => $kit->toCartPayload(), 'variant' => 'full'])
                        <div class="mt-5" data-island="AddToCart" data-props='@json($buyProps)'>
                            <a href="{{ route('cart') }}" class="btn-brand w-full">Adicionar à sacola</a>
                        </div>
                    @endif
                </div>
            </div>
        </div>
    </div>
@endsection
