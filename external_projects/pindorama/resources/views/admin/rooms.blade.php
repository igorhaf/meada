@extends('layouts.dashboard')

@section('title', 'Salas do espaço')

@section('content')
@php($input = 'rounded-lg border border-neutral-300 px-3 py-2 text-sm')
<div class="mx-auto max-w-2xl space-y-6">
    <div>
        <h1 class="text-2xl font-extrabold text-neutral-900">Salas do espaço Pindorama</h1>
        <p class="text-sm text-neutral-500">Salas físicas da plataforma. Um local de atendimento de um terapeuta pode ser vinculado a uma sala — aí a regra de comissão "por sala" se aplica.</p>
    </div>

    <div class="card divide-y divide-neutral-100">
        @forelse($rooms as $room)
            <form method="POST" action="{{ route('admin.rooms.update', $room) }}" class="flex flex-wrap items-center gap-2 p-4">
                @csrf @method('PUT')
                <input name="name" value="{{ $room->name }}" class="{{ $input }} flex-1">
                <input name="description" value="{{ $room->description }}" placeholder="Descrição" class="{{ $input }} flex-1">
                <label class="flex items-center gap-1 text-xs text-neutral-600"><input type="checkbox" name="is_active" value="1" @checked($room->is_active)> ativa</label>
                <span class="text-xs text-neutral-400">{{ $room->attendance_locations_count }} vínculos</span>
                <button class="rounded-lg bg-brand-600 px-3 py-1.5 text-xs font-semibold text-white">Salvar</button>
        </form>
            <form method="POST" action="{{ route('admin.rooms.destroy', $room) }}" class="-mt-11 pr-4 text-right" onsubmit="return confirm('Remover sala?')">@csrf @method('DELETE')<button class="text-xs text-red-500 hover:underline">remover</button></form>
        @empty
            <p class="p-6 text-center text-sm text-neutral-400">Nenhuma sala cadastrada.</p>
        @endforelse
    </div>

    <div class="card p-6">
        <h2 class="mb-3 font-bold text-neutral-800">Nova sala</h2>
        <form method="POST" action="{{ route('admin.rooms.store') }}" class="flex flex-wrap items-end gap-2">
            @csrf
            <input name="name" placeholder="Nome da sala" required class="{{ $input }} flex-1">
            <input name="description" placeholder="Descrição" class="{{ $input }} flex-1">
            <input type="hidden" name="is_active" value="1">
            <button class="btn-brand">Adicionar</button>
        </form>
    </div>
</div>
@endsection
