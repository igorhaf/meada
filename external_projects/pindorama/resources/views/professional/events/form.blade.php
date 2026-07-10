@extends('layouts.dashboard')

@section('title', $event->exists ? 'Editar evento' : 'Novo evento')

@section('content')
@php($input = 'w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm')
@php($tz = auth()->user()->timezone ?: config('pindorama.timezone'))
@php($fmt = fn ($d) => $d ? $d->setTimezone($tz)->format('Y-m-d\TH:i') : '')
<div class="mx-auto max-w-2xl">
    <h1 class="mb-6 text-2xl font-extrabold text-neutral-900">{{ $event->exists ? 'Editar evento' : 'Novo evento' }}</h1>

    @if($errors->any())<div class="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700"><ul class="list-inside list-disc">@foreach($errors->all() as $e)<li>{{ $e }}</li>@endforeach</ul></div>@endif

    <form method="POST" action="{{ $event->exists ? route('professional.events.update', $event) : route('professional.events.store') }}" class="card space-y-4 p-6">
        @csrf
        @if($event->exists) @method('PUT') @endif

        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">Título</label>
            <input name="title" value="{{ old('title', $event->title) }}" required class="{{ $input }}">
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">Descrição</label>
            <textarea name="description" rows="4" class="{{ $input }}">{{ old('description', $event->description) }}</textarea>
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Tipo</label>
                <select name="type" class="{{ $input }}">@foreach(\App\Models\Event::TYPES as $v => $l)<option value="{{ $v }}" @selected(old('type',$event->type)===$v)>{{ $l }}</option>@endforeach</select>
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Modalidade</label>
                <select name="modality" class="{{ $input }}"><option value="presencial" @selected(old('modality',$event->modality)==='presencial')>Presencial</option><option value="online" @selected(old('modality',$event->modality)==='online')>Online</option></select>
            </div>
            <div class="sm:col-span-2">
                <label class="mb-1 block text-sm font-medium text-neutral-700">Local / link</label>
                <input name="location_label" value="{{ old('location_label', $event->location_label) }}" class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Início</label>
                <input type="datetime-local" name="starts_at" value="{{ old('starts_at', $fmt($event->starts_at)) }}" required class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Término (opcional)</label>
                <input type="datetime-local" name="ends_at" value="{{ old('ends_at', $fmt($event->ends_at)) }}" class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Vagas (0 = ilimitado)</label>
                <input type="number" min="0" name="capacity" value="{{ old('capacity', $event->capacity) }}" class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Preço (R$)</label>
                <input type="number" step="0.01" min="0" name="price" value="{{ old('price', $event->price) }}" class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Desconto (%)</label>
                <input type="number" step="0.01" min="0" max="100" name="discount_percent" value="{{ old('discount_percent', $event->discount_percent) }}" class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Lembrete (h antes)</label>
                <input type="number" min="0" max="168" name="reminder_hours" value="{{ old('reminder_hours', $event->reminder_hours ?? 24) }}" class="{{ $input }}">
            </div>
        </div>
        <div class="flex flex-wrap gap-4">
            <label class="flex items-center gap-2 text-sm"><input type="checkbox" name="is_free" value="1" @checked(old('is_free',$event->is_free))> Gratuito</label>
            <label class="flex items-center gap-2 text-sm"><input type="checkbox" name="allow_discount" value="1" @checked(old('allow_discount',$event->allow_discount))> Permitir desconto</label>
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">Status</label>
            <select name="status" class="{{ $input }}">@foreach(\App\Models\Event::STATUSES as $v => $l)<option value="{{ $v }}" @selected(old('status',$event->status)===$v)>{{ $l }}</option>@endforeach</select>
        </div>
        <div class="flex items-center justify-between pt-2">
            <a href="{{ route('professional.events.index') }}" class="text-sm text-neutral-500 hover:underline">← Voltar</a>
            <button class="btn-brand">Salvar evento</button>
        </div>
    </form>
    @if($event->exists)
        <form method="POST" action="{{ route('professional.events.destroy', $event) }}" class="mt-3 text-right" onsubmit="return confirm('Remover evento?')">@csrf @method('DELETE')<button class="text-sm text-red-500 hover:underline">Remover evento</button></form>
    @endif
</div>
@endsection
