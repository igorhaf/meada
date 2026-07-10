@extends('layouts.app')

@section('title', 'Kits & cestas')
@section('description', 'Kits de festa, café da manhã, presente e corporativo montados pela Semente Doce — combinações prontas que saem mais em conta que item a item.')

@section('content')
    <div class="container-doce py-8">
        {{-- Intro --}}
        <div class="flex flex-col items-center gap-4 overflow-hidden rounded-3xl bg-gradient-to-br from-brand-600 to-caramel-500 px-8 py-10 text-center text-white">
            <span class="text-4xl">🎁</span>
            <h1 class="max-w-2xl text-2xl font-extrabold sm:text-3xl">Kits montados pela doceria</h1>
            <p class="max-w-xl text-white/90">
                Combinações prontas para festa, café da manhã ou presente — com preço fechado e aquela economia de comprar em conjunto.
                Quer algo do seu jeito? A gente monta um kit especial na encomenda.
            </p>
            <a href="{{ route('custom-orders.create') }}" class="mt-1 rounded-full bg-white px-6 py-3 font-bold text-brand-700 transition hover:bg-neutral-100">🎂 Montar meu kit sob encomenda</a>
        </div>

        {{-- Grade de kits --}}
        <div class="mt-8">
            @if($kits->isEmpty())
                <div class="flex flex-col items-center justify-center rounded-3xl border border-dashed border-neutral-300 bg-white py-20 text-center">
                    <div class="text-5xl">🧺</div>
                    <p class="mt-4 text-lg font-semibold text-neutral-700">Ainda não há kits publicados</p>
                    <p class="mt-1 text-sm text-neutral-500">Enquanto isso, você pode montar o seu na encomenda.</p>
                    <a href="{{ route('custom-orders.create') }}" class="btn-caramel mt-6">Fazer uma encomenda</a>
                </div>
            @else
                <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                    @foreach($kits as $kit)
                        @include('partials.kit-card', ['kit' => $kit])
                    @endforeach
                </div>
            @endif
        </div>
    </div>
@endsection
