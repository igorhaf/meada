@extends('layouts.app')

@section('title', 'Minha sacola')

@section('content')
    <div class="container-doce py-8">
        <div data-island="CartPage" data-props='@json($props)'>
            {{-- Fallback SSR enquanto a ilha monta --}}
            <div class="flex items-center justify-center py-16 text-neutral-400">
                <svg class="h-6 w-6 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4z"/></svg>
                <span class="ml-3">Carregando sua sacola…</span>
            </div>
        </div>
    </div>

    @include('partials.product-carousel', [
        'title' => 'Aproveite e leve também',
        'products' => $suggestions,
    ])
@endsection
