@extends('layouts.dashboard')

@section('title', 'Editar serviço')

@section('content')
    <div class="mb-6 flex items-center justify-between">
        <div>
            <a href="{{ route('professional.services.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar aos serviços</a>
            <h1 class="mt-1 text-2xl font-extrabold text-neutral-900">Editar serviço</h1>
        </div>
        <a href="{{ $service->url }}" class="btn-outline">Ver página ↗</a>
    </div>

    @include('professional.services._form', [
        'action' => route('professional.services.update', $service),
        'method' => 'PUT',
        'submitLabel' => 'Salvar alterações',
    ])
@endsection
