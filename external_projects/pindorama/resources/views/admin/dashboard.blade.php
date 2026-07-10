@extends('layouts.dashboard')

@section('title', 'Administração')

@section('content')
<div class="mx-auto max-w-5xl">
    <h1 class="mb-6 text-2xl font-extrabold text-neutral-900">Administração</h1>

    <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
        @foreach([
            ['Terapeutas', $stats['professionals']],
            ['Serviços', $stats['services']],
            ['Clientes', $stats['customers']],
            ['Agendamentos', $stats['appointments']],
            ['Receita paga', money($stats['revenue'])],
            ['Comissão', money($stats['commission'])],
        ] as [$label, $value])
            <div class="card p-4">
                <p class="text-xs uppercase tracking-wide text-neutral-400">{{ $label }}</p>
                <p class="mt-1 text-xl font-extrabold text-neutral-900">{{ $value }}</p>
            </div>
        @endforeach
    </div>

    <div class="mt-8 grid gap-6 lg:grid-cols-2">
        <div class="card p-5">
            <h2 class="mb-3 font-bold text-neutral-900">Agendamentos recentes</h2>
            @forelse($recent as $a)
                <div class="flex items-center justify-between border-b border-neutral-100 py-2 text-sm last:border-0">
                    <span class="truncate">{{ $a->service_title }} · <span class="text-neutral-500">{{ $a->professional?->display_name }}</span></span>
                    <span class="text-neutral-500">{{ money($a->total) }}</span>
                </div>
            @empty
                <p class="text-sm text-neutral-500">Sem agendamentos ainda.</p>
            @endforelse
        </div>
        <div class="card p-5">
            <h2 class="mb-3 font-bold text-neutral-900">Top terapeutas</h2>
            @forelse($topProfessionals as $p)
                <div class="flex items-center justify-between border-b border-neutral-100 py-2 text-sm last:border-0">
                    <span>{{ $p->display_name }} <span class="text-neutral-400">· {{ $p->services_count }} serviços</span></span>
                    <span class="text-neutral-500">{{ money($p->revenue ?? 0) }}</span>
                </div>
            @empty
                <p class="text-sm text-neutral-500">Sem terapeutas ainda.</p>
            @endforelse
        </div>
    </div>
</div>
@endsection
