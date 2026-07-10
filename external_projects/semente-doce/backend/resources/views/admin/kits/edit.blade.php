@extends('layouts.dashboard')

@section('title', 'Montar ' . $kit->name)

@section('content')
    <div class="mb-6 flex items-center justify-between">
        <div>
            <a href="{{ route('admin.kits.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar aos kits</a>
            <h1 class="mt-1 text-2xl font-extrabold text-neutral-900">Montar {{ $kit->name }}</h1>
        </div>
        <a href="{{ $kit->url }}" class="btn-outline" target="_blank">Ver na loja ↗</a>
    </div>

    @include('admin.kits._form', [
        'action' => route('admin.kits.update', $kit),
        'method' => 'PUT',
        'submitLabel' => 'Salvar kit',
        'montador' => true,
        'products' => $products,
        'items' => $items,
    ])
@endsection
