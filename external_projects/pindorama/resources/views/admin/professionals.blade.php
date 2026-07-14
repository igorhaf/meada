@extends('layouts.dashboard')

@section('title', 'Terapeutas')

@section('content')
<div class="mx-auto max-w-3xl">
    <div class="mb-6 flex items-center justify-between"><h1 class="text-2xl font-extrabold text-neutral-900">Terapeutas</h1><a href="{{ route('admin.professionals.create') }}" class="btn-brand">Novo profissional</a></div>

    <div class="card divide-y divide-neutral-100">
        @forelse($professionals as $pro)
            <div class="flex items-center justify-between gap-3 p-4">
                <div class="min-w-0">
                    <p class="font-semibold text-neutral-900">
                        {{ $pro->display_name }}
                        @unless($pro->is_active)<span class="chip ml-1 bg-red-100 text-red-700">inativo</span>@endunless
                        @if($pro->is_verified)<span class="chip ml-1 bg-brand-100 text-brand-800">✓ verificado</span>@endif
                    </p>
                    <p class="truncate text-sm text-neutral-500">{{ $pro->email }} · {{ $pro->services_count }} serviços · {{ $pro->city ?: 'sem cidade' }}</p>
                </div>
                <div class="flex items-center gap-2">
                    <a href="{{ route('admin.professionals.show', $pro) }}" class="text-sm font-medium text-brand-700 hover:underline">gerenciar</a>
                    @if($pro->professional_slug)<a href="{{ route('professionals.show', $pro->professional_slug) }}" target="_blank" class="text-sm text-neutral-500 hover:underline">página ↗</a>@endif
                    <form method="POST" action="{{ route('admin.professionals.verify', $pro) }}">@csrf
                        <button class="rounded-lg border px-3 py-1.5 text-xs font-semibold {{ $pro->is_verified ? 'border-neutral-300 text-neutral-600' : 'border-brand-600 bg-brand-600 text-white' }}">
                            {{ $pro->is_verified ? 'Remover verificação' : 'Verificar' }}
                        </button>
                    </form>
                </div>
            </div>
        @empty
            <p class="p-6 text-center text-sm text-neutral-400">Nenhum terapeuta cadastrado.</p>
        @endforelse
    </div>
    <div class="mt-6">{{ $professionals->links() }}</div>
</div>
@endsection
