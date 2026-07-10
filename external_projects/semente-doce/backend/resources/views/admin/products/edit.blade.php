@extends('layouts.dashboard')

@section('title', 'Editar item')

@section('content')
    <div class="mb-6 flex items-center justify-between">
        <div>
            <a href="{{ route('admin.products.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar ao cardápio</a>
            <h1 class="mt-1 text-2xl font-extrabold text-neutral-900">Editar {{ $product->title }}</h1>
        </div>
        <a href="{{ $product->url }}" class="btn-outline" target="_blank">Ver na loja ↗</a>
    </div>

    @include('admin.products._form', [
        'action' => route('admin.products.update', $product),
        'method' => 'PUT',
        'submitLabel' => 'Salvar alterações',
    ])
@endsection
