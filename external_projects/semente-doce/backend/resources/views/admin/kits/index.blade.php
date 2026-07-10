@extends('layouts.dashboard')

@section('title', 'Kits')

@section('content')
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
            <h1 class="text-2xl font-extrabold text-neutral-900">Kits 🎁</h1>
            <p class="text-sm text-neutral-500">{{ $kits->count() }} kit(s) — combos montados pela doceria.</p>
        </div>
        <a href="{{ route('admin.kits.create') }}" class="btn-brand">+ Novo kit</a>
    </div>

    @if($kits->isEmpty())
        <div class="card flex flex-col items-center justify-center py-16 text-center">
            <div class="text-5xl">🧺</div>
            <p class="mt-4 font-semibold text-neutral-700">Nenhum kit montado</p>
            <p class="mt-1 text-sm text-neutral-500">Crie um combo (festa, café, presente) e monte a composição.</p>
            <a href="{{ route('admin.kits.create') }}" class="btn-brand mt-6">+ Novo kit</a>
        </div>
    @else
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            @foreach($kits as $kit)
                <div class="card overflow-hidden">
                    <div class="relative">
                        <img src="{{ $kit->image_url }}" alt="" class="aspect-video w-full object-cover">
                        <span class="chip absolute left-2 top-2 bg-white/90 text-neutral-700">{{ $kit->type_label }}</span>
                        @unless($kit->is_active)<span class="chip absolute right-2 top-2 bg-neutral-800/80 text-white">Pausado</span>@endunless
                    </div>
                    <div class="space-y-2 p-4">
                        <div class="flex items-start justify-between gap-2">
                            <p class="font-bold text-neutral-900">{{ $kit->name }}</p>
                            @if($kit->is_featured)<span title="Destaque">⭐</span>@endif
                        </div>
                        <p class="text-xs text-neutral-400">{{ $kit->items->count() }} componente(s){{ $kit->serves ? ' · '.$kit->serves : '' }}</p>

                        <div class="flex items-end justify-between pt-1">
                            <div>
                                <p class="text-lg font-extrabold text-brand-700">{{ money($kit->price) }}</p>
                                <p class="text-xs text-neutral-400">componentes: {{ money($kit->components_total) }}</p>
                            </div>
                            @if($kit->savings > 0)
                                <span class="chip bg-pistache-100 text-pistache-600">Economia {{ money($kit->savings) }}</span>
                            @endif
                        </div>

                        <div class="flex items-center gap-1 border-t border-neutral-100 pt-3">
                            <a href="{{ route('admin.kits.edit', $kit) }}" class="btn-brand flex-1 !py-2 text-xs">🧺 Montar</a>
                            <form method="POST" action="{{ route('admin.kits.toggle', $kit) }}">
                                @csrf
                                <button class="rounded-lg px-2.5 py-2 text-xs font-medium text-neutral-500 hover:bg-neutral-100">{{ $kit->is_active ? 'Pausar' : 'Ativar' }}</button>
                            </form>
                            <form method="POST" action="{{ route('admin.kits.destroy', $kit) }}" onsubmit="return confirm('Remover este kit?')">
                                @csrf @method('DELETE')
                                <button class="rounded-lg px-2.5 py-2 text-xs font-medium text-red-500 hover:bg-red-50">✕</button>
                            </form>
                        </div>
                    </div>
                </div>
            @endforeach
        </div>
    @endif
@endsection
