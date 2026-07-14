@extends('layouts.dashboard')

@section('title', 'Agenda')

@section('content')
<div>
    <div class="mb-5 flex flex-wrap items-end justify-between gap-4">
        <div><h1 class="text-2xl font-extrabold text-neutral-900">Agenda</h1><p class="text-sm text-neutral-500">Consultas, encontros e bloqueios em um único calendário.</p></div>
        <a href="{{ route('professional.bookings.create') }}" class="btn-brand">Agendar cliente</a>
    </div>

    <form method="GET" class="card mb-5 flex flex-wrap items-end gap-3 p-4">
        <a href="{{ route('professional.agenda', ['date' => $prev, 'view' => $view]) }}" class="rounded-lg border border-neutral-300 px-3 py-2 text-sm hover:bg-neutral-50">←</a>
        <label class="text-xs">Data<input type="date" name="date" value="{{ $date->format('Y-m-d') }}" class="mt-1 block rounded-lg border px-3 py-2"></label>
        <label class="text-xs">Visão<select name="view" class="mt-1 block rounded-lg border px-3 py-2"><option value="day" @selected($view === 'day')>Dia</option><option value="week" @selected($view === 'week')>Semana</option><option value="month" @selected($view === 'month')>Mês</option></select></label>
        <button class="btn-outline">Exibir</button>
        <a href="{{ route('professional.agenda', ['date' => $next, 'view' => $view]) }}" class="rounded-lg border border-neutral-300 px-3 py-2 text-sm hover:bg-neutral-50">→</a>
    </form>

    @if($view === 'day')
        <h2 class="mb-3 font-bold">{{ ['Domingo','Segunda','Terça','Quarta','Quinta','Sexta','Sábado'][$date->dayOfWeek] }}, {{ $date->format('d/m/Y') }}</h2>
        <div class="space-y-2">
            @forelse($items->get($date->format('Y-m-d'), collect()) as $item)
                <a href="{{ $item['url'] }}" class="card flex items-center gap-4 border-l-4 p-4 {{ $item['type'] === 'event' ? 'border-l-gold-500' : ($item['type'] === 'block' ? 'border-l-neutral-400 bg-neutral-50' : 'border-l-brand-500') }}">
                    <div class="w-24 text-sm font-bold">{{ $item['start']->setTimezone($tz)->format('H:i') }}–{{ $item['end']->setTimezone($tz)->format('H:i') }}</div>
                    <div><p class="font-bold">{{ $item['title'] }}</p><p class="text-sm text-neutral-500">{{ $item['subtitle'] }} @if($item['location'])· {{ $item['location'] }}@endif</p></div>
                </a>
            @empty
                <div class="card p-10 text-center text-neutral-500">Nenhum compromisso neste dia.</div>
            @endforelse
        </div>
    @else
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-7">
            @for($day = $from; $day->lte($to); $day = $day->addDay())
                <section class="min-h-40 rounded-xl border bg-white p-3 {{ $view === 'month' && !$day->isSameMonth($date) ? 'opacity-45' : '' }}">
                    <h2 class="border-b pb-2 text-sm font-bold {{ $day->isToday() ? 'text-brand-700' : '' }}">{{ ['Dom','Seg','Ter','Qua','Qui','Sex','Sáb'][$day->dayOfWeek] }} {{ $day->format('d/m') }}</h2>
                    <div class="mt-2 space-y-2">
                        @forelse($items->get($day->format('Y-m-d'), collect()) as $item)
                            <a href="{{ $item['url'] }}" class="block rounded-lg border-l-4 p-2 text-xs {{ $item['type'] === 'event' ? 'border-gold-500 bg-amber-50' : ($item['type'] === 'block' ? 'border-neutral-400 bg-neutral-100' : 'border-brand-500 bg-brand-50') }}">
                                <p class="font-bold">{{ $item['start']->setTimezone($tz)->format('H:i') }} {{ $item['title'] }}</p>
                                <p class="text-neutral-600">{{ $item['subtitle'] }}</p>
                                @if($item['location'])<p class="text-neutral-400">{{ $item['location'] }}</p>@endif
                            </a>
                        @empty
                            <p class="py-3 text-center text-xs text-neutral-300">Livre</p>
                        @endforelse
                    </div>
                </section>
            @endfor
        </div>
    @endif
</div>
@endsection
