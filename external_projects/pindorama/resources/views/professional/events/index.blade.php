@extends('layouts.dashboard')

@section('title', 'Eventos')

@section('content')
<div class="mx-auto max-w-3xl">
    <div class="mb-6 flex items-center justify-between">
        <div><h1 class="text-2xl font-extrabold text-neutral-900">Eventos em que atuo</h1><p class="text-sm text-neutral-500">A programação é administrada pela equipe Pindorama.</p></div>
    </div>

    @if($events->isEmpty())
        <div class="card p-10 text-center text-neutral-500">Você ainda não está vinculado a eventos.</div>
    @else
        <div class="space-y-3">
            @foreach($events as $event)
                <div class="card flex flex-wrap items-center justify-between gap-3 p-4">
                    <div class="min-w-0">
                        <p class="font-semibold text-neutral-900">{{ $event->title }}
                            <span class="chip ml-1 {{ $event->status === 'published' ? 'bg-brand-100 text-brand-800' : 'bg-neutral-200 text-neutral-600' }}">{{ \App\Models\Event::STATUSES[$event->status] }}</span>
                        </p>
                        <p class="text-sm text-neutral-500">
                            {{ $event->type_label }} · {{ $event->starts_at->setTimezone($event->timezone)->format('d/m/Y H:i') }}
                            · {{ $event->taken }}{{ $event->capacity > 0 ? '/'.$event->capacity : '' }} inscritos
                        </p>
                    </div>
                    <div class="flex items-center gap-3 text-sm">
                        <a href="{{ route('professional.events.registrations', $event) }}" class="font-medium text-brand-700 hover:underline">Inscritos</a>
                        <a href="{{ route('events.show', $event) }}" class="font-medium text-neutral-500 hover:underline">Página pública</a>
                    </div>
                </div>
            @endforeach
        </div>
    @endif
</div>
@endsection
