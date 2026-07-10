@extends('layouts.dashboard')

@section('title', 'Novo kit')

@section('content')
    <div class="mb-6">
        <a href="{{ route('admin.kits.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar aos kits</a>
        <h1 class="mt-1 text-2xl font-extrabold text-neutral-900">Novo kit 🎁</h1>
        <p class="text-sm text-neutral-500">Crie o kit e, na próxima tela, monte a composição no Montador.</p>
    </div>

    @include('admin.kits._form', [
        'action' => route('admin.kits.store'),
        'submitLabel' => 'Criar e montar →',
        'montador' => false,
    ])
@endsection
