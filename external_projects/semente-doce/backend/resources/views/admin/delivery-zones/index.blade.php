@extends('layouts.dashboard')

@section('title', 'Entregas por bairro')

@section('content')
    <div class="mb-6">
        <h1 class="text-2xl font-extrabold text-neutral-900">Entregas por bairro 🛵</h1>
        <p class="text-sm text-neutral-500">Taxa e prazo por bairro. Bairro fora da lista usa a taxa padrão do checkout.</p>
    </div>

    <div class="grid gap-6 lg:grid-cols-3">
        {{-- Form de criação --}}
        <div class="lg:col-span-1">
            <form method="POST" action="{{ route('admin.delivery-zones.store') }}" class="card space-y-4 p-6">
                @csrf
                <h2 class="font-bold text-neutral-900">Novo bairro</h2>

                @if ($errors->any())
                    <div class="rounded-xl bg-red-50 px-3 py-2 text-xs text-red-700">
                        <ul class="list-inside list-disc">@foreach ($errors->all() as $e)<li>{{ $e }}</li>@endforeach</ul>
                    </div>
                @endif

                <div>
                    <label class="field-label">Bairro *</label>
                    <input name="neighborhood" value="{{ old('neighborhood') }}" required class="field-input">
                </div>
                <div>
                    <label class="field-label">Taxa (R$) *</label>
                    <input name="fee" type="number" step="0.01" min="0" value="{{ old('fee', 0) }}" required class="field-input">
                </div>
                <div class="grid grid-cols-2 gap-3">
                    <div>
                        <label class="field-label">Prazo mín (min)</label>
                        <input name="eta_min" type="number" min="0" value="{{ old('eta_min') }}" class="field-input">
                    </div>
                    <div>
                        <label class="field-label">Prazo máx (min)</label>
                        <input name="eta_max" type="number" min="0" value="{{ old('eta_max') }}" class="field-input">
                    </div>
                </div>
                <div>
                    <label class="field-label">Ordem</label>
                    <input name="position" type="number" min="0" value="{{ old('position', 0) }}" class="field-input">
                </div>
                <label class="flex items-center gap-2 text-sm text-neutral-700">
                    <input type="hidden" name="is_active" value="0">
                    <input type="checkbox" name="is_active" value="1" checked class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                    Ativo
                </label>
                <button class="btn-brand w-full">Adicionar bairro</button>
            </form>
        </div>

        {{-- Lista --}}
        <div class="space-y-3 lg:col-span-2">
            @forelse($zones as $zone)
                <div class="card p-4">
                    <form method="POST" action="{{ route('admin.delivery-zones.update', $zone) }}" class="flex flex-wrap items-end gap-2">
                        @csrf @method('PUT')
                        <div class="grow"><label class="field-label !text-xs">Bairro</label><input name="neighborhood" value="{{ $zone->neighborhood }}" class="field-input !py-1.5"></div>
                        <div class="w-24"><label class="field-label !text-xs">Taxa</label><input name="fee" type="number" step="0.01" min="0" value="{{ $zone->fee }}" class="field-input !py-1.5"></div>
                        <div class="w-20"><label class="field-label !text-xs">mín</label><input name="eta_min" type="number" min="0" value="{{ $zone->eta_min }}" class="field-input !py-1.5"></div>
                        <div class="w-20"><label class="field-label !text-xs">máx</label><input name="eta_max" type="number" min="0" value="{{ $zone->eta_max }}" class="field-input !py-1.5"></div>
                        <div class="w-16"><label class="field-label !text-xs">Ordem</label><input name="position" type="number" min="0" value="{{ $zone->position }}" class="field-input !py-1.5"></div>
                        <label class="flex items-center gap-1.5 pb-2 text-xs text-neutral-600">
                            <input type="hidden" name="is_active" value="0">
                            <input type="checkbox" name="is_active" value="1" @checked($zone->is_active) class="rounded border-neutral-300 text-brand-600">ativo
                        </label>
                        <button class="btn-outline !px-4 !py-1.5 text-xs">Salvar</button>
                    </form>
                    <form method="POST" action="{{ route('admin.delivery-zones.destroy', $zone) }}" class="mt-2 text-right" onsubmit="return confirm('Remover este bairro?')">
                        @csrf @method('DELETE')
                        <button class="text-xs font-medium text-red-500 hover:underline">Excluir bairro</button>
                    </form>
                </div>
            @empty
                <div class="card flex flex-col items-center justify-center py-16 text-center">
                    <div class="text-5xl">🛵</div>
                    <p class="mt-4 font-semibold text-neutral-700">Nenhum bairro cadastrado</p>
                    <p class="mt-1 text-sm text-neutral-500">Adicione o primeiro ao lado. Sem bairros, o checkout usa a taxa padrão.</p>
                </div>
            @endforelse
        </div>
    </div>
@endsection
