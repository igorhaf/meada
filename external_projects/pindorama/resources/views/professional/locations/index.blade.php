@extends('layouts.dashboard')

@section('title', 'Locais de atendimento')

@section('content')
<div class="mx-auto max-w-3xl">
    <div class="mb-6 flex items-center justify-between">
        <div>
            <h1 class="text-2xl font-extrabold text-neutral-900">Locais de atendimento</h1>
            <p class="text-sm text-neutral-500">Todos os seus locais compartilham a mesma agenda — você não pode estar em dois lugares ao mesmo tempo.</p>
        </div>
        <a href="{{ route('professional.locations.create') }}" class="btn-brand shrink-0">Novo local</a>
    </div>

    @if($locations->isEmpty())
        <div class="card p-10 text-center text-neutral-500">
            <p>Você ainda não cadastrou locais de atendimento.</p>
            <a href="{{ route('professional.locations.create') }}" class="mt-3 inline-block font-medium text-brand-700 hover:underline">Cadastrar meu primeiro local</a>
        </div>
    @else
        <div class="space-y-3">
            @foreach($locations as $loc)
                <div class="card flex items-center justify-between p-4">
                    <div class="min-w-0">
                        <p class="font-semibold text-neutral-900">
                            {{ $loc->is_online ? '💻' : '📍' }} {{ $loc->name }}
                            @unless($loc->is_active)<span class="chip ml-2 bg-neutral-200 text-neutral-600">Inativo</span>@endunless
                        </p>
                        <p class="truncate text-sm text-neutral-500">{{ $loc->full_address }}</p>
                    </div>
                    <div class="flex shrink-0 items-center gap-3 text-sm">
                        <a href="{{ route('professional.locations.edit', $loc) }}" class="font-medium text-brand-700 hover:underline">Editar</a>
                        <form method="POST" action="{{ route('professional.locations.destroy', $loc) }}" onsubmit="return confirm('Remover este local?')">
                            @csrf @method('DELETE')
                            <button class="font-medium text-red-600 hover:underline">Remover</button>
                        </form>
                    </div>
                </div>
            @endforeach
        </div>
    @endif
</div>
@endsection
