@extends('layouts.dashboard')

@section('title', 'Agenda')

@section('content')
<div class="mx-auto max-w-4xl">
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 class="text-2xl font-extrabold text-neutral-900">Agenda</h1>
        <form method="GET" class="flex items-center gap-2">
            <a href="{{ route('professional.agenda', ['date' => $prev]) }}" class="rounded-lg border border-neutral-300 px-3 py-2 text-sm hover:bg-neutral-50">←</a>
            <input type="date" name="date" value="{{ $date->format('Y-m-d') }}" onchange="this.form.submit()" class="rounded-lg border border-neutral-300 px-3 py-2 text-sm">
            <a href="{{ route('professional.agenda', ['date' => $next]) }}" class="rounded-lg border border-neutral-300 px-3 py-2 text-sm hover:bg-neutral-50">→</a>
        </form>
    </div>

    <p class="mb-4 text-sm font-medium text-neutral-500">
        {{ ['Domingo','Segunda','Terça','Quarta','Quinta','Sexta','Sábado'][$date->dayOfWeek] }}, {{ $date->format('d/m/Y') }}
    </p>

    @if($appointments->isEmpty())
        <div class="card p-10 text-center text-neutral-500">Nenhum atendimento neste dia.</div>
    @else
        <div class="space-y-2">
            @foreach($appointments as $appt)
                @include('professional.partials.appointment-row', ['appt' => $appt, 'tz' => $tz])
            @endforeach
        </div>
    @endif
</div>
@endsection
