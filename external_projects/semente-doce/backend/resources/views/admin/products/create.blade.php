@extends('layouts.dashboard')

@section('title', 'Novo item')

@section('content')
    <div class="mb-6">
        <a href="{{ route('admin.products.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar ao cardápio</a>
        <h1 class="mt-1 text-2xl font-extrabold text-neutral-900">Novo item do cardápio</h1>
    </div>

    @include('admin.products._form', [
        'action' => route('admin.products.store'),
        'submitLabel' => 'Criar item',
    ])
@endsection
