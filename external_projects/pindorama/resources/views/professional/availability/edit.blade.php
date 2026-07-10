@extends('layouts.dashboard')

@section('title', 'Disponibilidade')

@section('content')
@php($sel = 'rounded-lg border border-neutral-300 px-2 py-2 text-sm focus:border-brand-500 focus:outline-none')
<div class="mx-auto max-w-3xl space-y-8">
    <div>
        <h1 class="text-2xl font-extrabold text-neutral-900">Disponibilidade</h1>
        <p class="text-sm text-neutral-500">Defina seus horários de trabalho <strong>por local</strong>. Todos os locais compartilham a mesma agenda.</p>
    </div>

    @if($locations->isEmpty())
        <div class="card p-8 text-center text-neutral-500">
            Cadastre um <a href="{{ route('professional.locations.create') }}" class="font-medium text-brand-700 hover:underline">local de atendimento</a> antes de definir seus horários.
        </div>
    @else
    {{-- Horários semanais --}}
    <form method="POST" action="{{ route('professional.availability.update') }}" class="card p-6">
        @csrf @method('PUT')
        <h2 class="mb-4 font-bold text-neutral-800">Horários semanais</h2>

        <div id="rows" class="space-y-2">
            @forelse($availabilities as $i => $av)
                <div class="row grid grid-cols-[1fr_1fr_auto_auto_auto] items-center gap-2">
                    <select name="rows[{{ $i }}][attendance_location_id]" class="{{ $sel }}">
                        @foreach($locations as $loc)
                            <option value="{{ $loc->id }}" @selected($loc->id === $av->attendance_location_id)>{{ $loc->is_online ? '💻 ' : '' }}{{ $loc->name }}</option>
                        @endforeach
                    </select>
                    <select name="rows[{{ $i }}][weekday]" class="{{ $sel }}">
                        @foreach($weekdays as $num => $label)
                            <option value="{{ $num }}" @selected($num === $av->weekday)>{{ $label }}</option>
                        @endforeach
                    </select>
                    <input type="time" name="rows[{{ $i }}][start_time]" value="{{ $av->start_hm }}" class="{{ $sel }}">
                    <input type="time" name="rows[{{ $i }}][end_time]" value="{{ $av->end_hm }}" class="{{ $sel }}">
                    <button type="button" class="rm text-red-500 hover:text-red-700" aria-label="Remover">✕</button>
                </div>
            @empty
            @endforelse
        </div>

        <button type="button" id="addRow" class="mt-3 text-sm font-medium text-brand-700 hover:underline">+ Adicionar horário</button>

        <div class="mt-6 flex justify-end">
            <button type="submit" class="btn-brand">Salvar horários</button>
        </div>
    </form>

    {{-- Bloqueios / folgas --}}
    <div class="card p-6">
        <h2 class="mb-4 font-bold text-neutral-800">Folgas & bloqueios</h2>

        @if($blocks->isNotEmpty())
            <ul class="mb-5 space-y-2">
                @foreach($blocks as $block)
                    <li class="flex items-center justify-between rounded-lg bg-neutral-50 px-3 py-2 text-sm">
                        <span>
                            🚫 {{ $block->starts_at->format('d/m/Y') }}
                            @unless($block->all_day) · {{ $block->starts_at->format('H:i') }}–{{ $block->ends_at->format('H:i') }} @endunless
                            <span class="text-neutral-500">— {{ $block->location?->name ?? 'Todos os locais' }}{{ $block->reason ? ' · ' . $block->reason : '' }}</span>
                        </span>
                        <form method="POST" action="{{ route('professional.blocks.destroy', $block) }}">
                            @csrf @method('DELETE')
                            <button class="text-red-500 hover:text-red-700">Remover</button>
                        </form>
                    </li>
                @endforeach
            </ul>
        @endif

        <form method="POST" action="{{ route('professional.blocks.store') }}" class="grid gap-3 sm:grid-cols-2">
            @csrf
            <select name="attendance_location_id" class="{{ $sel }}">
                <option value="">Todos os locais</option>
                @foreach($locations as $loc)
                    <option value="{{ $loc->id }}">{{ $loc->name }}</option>
                @endforeach
            </select>
            <input type="text" name="reason" placeholder="Motivo (opcional)" class="{{ $sel }}">
            <input type="date" name="date" required class="{{ $sel }}">
            <label class="flex items-center gap-2 text-sm text-neutral-700">
                <input type="checkbox" name="all_day" value="1" id="all_day" checked class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500"> Dia inteiro
            </label>
            <div id="block_times" class="hidden grid grid-cols-2 gap-3 sm:col-span-2">
                <input type="time" name="start_time" class="{{ $sel }}">
                <input type="time" name="end_time" class="{{ $sel }}">
            </div>
            <div class="sm:col-span-2">
                <button type="submit" class="btn-outline">Adicionar bloqueio</button>
            </div>
        </form>
    </div>
    @endif
</div>

<template id="rowtpl">
    <div class="row grid grid-cols-[1fr_1fr_auto_auto_auto] items-center gap-2">
        <select name="rows[__IDX__][attendance_location_id]" class="{{ $sel }}">
            @foreach($locations as $loc)
                <option value="{{ $loc->id }}">{{ $loc->is_online ? '💻 ' : '' }}{{ $loc->name }}</option>
            @endforeach
        </select>
        <select name="rows[__IDX__][weekday]" class="{{ $sel }}">
            @foreach($weekdays as $num => $label)
                <option value="{{ $num }}">{{ $label }}</option>
            @endforeach
        </select>
        <input type="time" name="rows[__IDX__][start_time]" value="09:00" class="{{ $sel }}">
        <input type="time" name="rows[__IDX__][end_time]" value="12:00" class="{{ $sel }}">
        <button type="button" class="rm text-red-500 hover:text-red-700" aria-label="Remover">✕</button>
    </div>
</template>

<script>
    (function () {
        let idx = {{ $availabilities->count() }};
        const rows = document.getElementById('rows');
        const tpl = document.getElementById('rowtpl');
        document.getElementById('addRow')?.addEventListener('click', () => {
            rows.insertAdjacentHTML('beforeend', tpl.innerHTML.replaceAll('__IDX__', idx++));
        });
        rows?.addEventListener('click', (e) => {
            if (e.target.classList.contains('rm')) e.target.closest('.row').remove();
        });
        const allDay = document.getElementById('all_day');
        allDay?.addEventListener('change', () => {
            document.getElementById('block_times').classList.toggle('hidden', allDay.checked);
        });
    })();
</script>
@endsection
