@extends('layouts.dashboard')

@section('title', 'Categorias')

@section('content')
    <div class="mb-6">
        <h1 class="text-2xl font-extrabold text-neutral-900">Categorias</h1>
        <p class="text-sm text-neutral-500">Organize o cardápio em seções (🎂 Bolos, 🥐 Salgados, 🍬 Doces finos…).</p>
    </div>

    <div class="grid gap-6 lg:grid-cols-3">
        {{-- Form de criação --}}
        <div class="lg:col-span-1">
            <form method="POST" action="{{ route('admin.categories.store') }}" class="card space-y-4 p-6">
                @csrf
                <h2 class="font-bold text-neutral-900">Nova categoria</h2>

                @if ($errors->any())
                    <div class="rounded-xl bg-red-50 px-3 py-2 text-xs text-red-700">
                        <ul class="list-inside list-disc">@foreach ($errors->all() as $e)<li>{{ $e }}</li>@endforeach</ul>
                    </div>
                @endif

                <div>
                    <label class="field-label">Nome *</label>
                    <input name="name" value="{{ old('name') }}" required class="field-input">
                </div>
                <div>
                    <label class="field-label">Categoria-mãe</label>
                    <select name="parent_id" class="field-input">
                        <option value="">— Raiz (menu principal)</option>
                        @foreach($parents as $parent)
                            <option value="{{ $parent->id }}" @selected((int) old('parent_id') === $parent->id)>{{ $parent->name }}</option>
                        @endforeach
                    </select>
                </div>
                <div class="grid grid-cols-3 gap-3">
                    <div>
                        <label class="field-label">Ícone</label>
                        <input name="icon" value="{{ old('icon') }}" placeholder="🎂" class="field-input">
                    </div>
                    <div>
                        <label class="field-label">Cor</label>
                        <input name="accent" type="color" value="{{ old('accent', '#d81e5b') }}" class="h-11 w-full rounded-xl border border-neutral-300">
                    </div>
                    <div>
                        <label class="field-label">Ordem</label>
                        <input name="position" type="number" min="0" value="{{ old('position', 0) }}" class="field-input">
                    </div>
                </div>
                <label class="flex items-center gap-2 text-sm text-neutral-700">
                    <input type="hidden" name="is_active" value="0">
                    <input type="checkbox" name="is_active" value="1" checked class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                    Ativa
                </label>
                <button class="btn-brand w-full">Adicionar categoria</button>
            </form>
        </div>

        {{-- Lista de categorias --}}
        <div class="space-y-4 lg:col-span-2">
            @forelse($categories as $category)
                <div class="card p-5">
                    <div class="flex flex-wrap items-center justify-between gap-3">
                        <div class="flex items-center gap-3">
                            <span class="flex h-10 w-10 items-center justify-center rounded-xl text-xl" style="background: {{ $category->accent ? $category->accent.'22' : '#ffe0e7' }}">{{ $category->icon ?: '🍽️' }}</span>
                            <div>
                                <p class="font-bold text-neutral-900">{{ $category->name }}</p>
                                <p class="text-xs text-neutral-400">/{{ $category->slug }} · {{ $category->children->count() }} subcategoria(s)</p>
                            </div>
                        </div>
                        <div class="flex items-center gap-2">
                            @unless($category->is_active)<span class="chip bg-neutral-100 text-neutral-500">Inativa</span>@endunless
                            <form method="POST" action="{{ route('admin.categories.destroy', $category) }}" onsubmit="return confirm('Remover a categoria e suas subcategorias?')">
                                @csrf @method('DELETE')
                                <button class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-red-500 hover:bg-red-50">Excluir</button>
                            </form>
                        </div>
                    </div>

                    {{-- Edição inline da raiz --}}
                    <form method="POST" action="{{ route('admin.categories.update', $category) }}" class="mt-3 flex flex-wrap items-end gap-2 border-t border-neutral-100 pt-3">
                        @csrf @method('PUT')
                        <input type="hidden" name="parent_id" value="{{ $category->parent_id }}">
                        <div class="grow"><label class="field-label !text-xs">Nome</label><input name="name" value="{{ $category->name }}" class="field-input !py-1.5"></div>
                        <div class="w-16"><label class="field-label !text-xs">Ícone</label><input name="icon" value="{{ $category->icon }}" class="field-input !py-1.5"></div>
                        <div class="w-20"><label class="field-label !text-xs">Ordem</label><input name="position" type="number" min="0" value="{{ $category->position }}" class="field-input !py-1.5"></div>
                        <label class="flex items-center gap-1.5 pb-2 text-xs text-neutral-600">
                            <input type="hidden" name="is_active" value="0">
                            <input type="checkbox" name="is_active" value="1" @checked($category->is_active) class="rounded border-neutral-300 text-brand-600">ativa
                        </label>
                        <button class="btn-outline !px-4 !py-1.5 text-xs">Salvar</button>
                    </form>

                    {{-- Subcategorias --}}
                    @if($category->children->isNotEmpty())
                        <div class="mt-3 space-y-2 border-t border-neutral-100 pt-3">
                            @foreach($category->children as $child)
                                <div class="flex flex-wrap items-end gap-2 rounded-xl bg-neutral-50 p-2">
                                    <form method="POST" action="{{ route('admin.categories.update', $child) }}" class="flex grow flex-wrap items-end gap-2">
                                        @csrf @method('PUT')
                                        <input type="hidden" name="parent_id" value="{{ $category->id }}">
                                        <span class="pb-2 pl-1 text-neutral-300">↳</span>
                                        <div class="grow"><input name="name" value="{{ $child->name }}" class="field-input !py-1.5"></div>
                                        <div class="w-14"><input name="icon" value="{{ $child->icon }}" placeholder="🍬" class="field-input !py-1.5"></div>
                                        <div class="w-16"><input name="position" type="number" min="0" value="{{ $child->position }}" class="field-input !py-1.5"></div>
                                        <label class="flex items-center gap-1 pb-2 text-xs text-neutral-600">
                                            <input type="hidden" name="is_active" value="0">
                                            <input type="checkbox" name="is_active" value="1" @checked($child->is_active) class="rounded border-neutral-300 text-brand-600">ativa
                                        </label>
                                        <button class="btn-outline !px-3 !py-1.5 text-xs">Salvar</button>
                                    </form>
                                    <form method="POST" action="{{ route('admin.categories.destroy', $child) }}" onsubmit="return confirm('Remover subcategoria?')">
                                        @csrf @method('DELETE')
                                        <button class="rounded-lg px-2 py-1.5 text-xs font-medium text-red-500 hover:bg-red-100">✕</button>
                                    </form>
                                </div>
                            @endforeach
                        </div>
                    @endif
                </div>
            @empty
                <div class="card flex flex-col items-center justify-center py-16 text-center">
                    <div class="text-5xl">🗂️</div>
                    <p class="mt-4 font-semibold text-neutral-700">Nenhuma categoria</p>
                    <p class="mt-1 text-sm text-neutral-500">Crie a primeira ao lado.</p>
                </div>
            @endforelse
        </div>
    </div>
@endsection
