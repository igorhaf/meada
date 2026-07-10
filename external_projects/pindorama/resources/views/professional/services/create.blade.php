@extends('layouts.dashboard')

@section('title', 'Novo serviço')

@section('content')
    <div class="mb-6">
        <a href="{{ route('professional.services.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar aos serviços</a>
        <h1 class="mt-1 text-2xl font-extrabold text-neutral-900">Cadastrar serviço</h1>
    </div>

    @include('professional.services._form', [
        'action' => route('professional.services.store'),
        'submitLabel' => 'Publicar serviço',
    ])
@endsection
