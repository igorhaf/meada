@extends('layouts.dashboard')

@section('title', 'Visão geral')

@section('content')
<div class="mx-auto max-w-4xl">
    <h1 class="mb-6 text-2xl font-extrabold text-neutral-900">Olá, {{ auth()->user()->display_name }} 👋</h1>

    <div class="grid grid-cols-2 gap-4 sm:grid-cols-4">
        @foreach([
            ['Aguardando aceite', $stats['pending'], 'text-amber-600'],
            ['Confirmados', $stats['upcoming'], 'text-brand-600'],
            ['Serviços', $stats['services'], 'text-neutral-800'],
            ['Recebido (líq.)', money($stats['earnings']), 'text-neutral-800'],
        ] as [$label, $value, $color])
            <div class="card p-4">
                <p class="text-xs uppercase tracking-wide text-neutral-400">{{ $label }}</p>
                <p class="mt-1 text-2xl font-extrabold {{ $color }}">{{ $value }}</p>
            </div>
        @endforeach
    </div>

    <div class="mt-8">
        <div class="mb-3 flex items-center justify-between">
            <h2 class="text-lg font-bold text-neutral-900">Hoje</h2>
            <a href="{{ route('professional.agenda') }}" class="text-sm font-medium text-brand-700 hover:underline">Ver agenda completa →</a>
        </div>

        @if($todays->isEmpty())
            <div class="card p-8 text-center text-neutral-500">Nenhum atendimento para hoje.</div>
        @else
            <div class="space-y-2">
                @foreach($todays as $appt)
                    @include('professional.partials.appointment-row', ['appt' => $appt, 'tz' => $tz])
                @endforeach
            </div>
        @endif
    </div>
</div>
@endsection
