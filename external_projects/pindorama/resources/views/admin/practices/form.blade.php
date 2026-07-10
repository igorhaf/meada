@extends('layouts.dashboard')

@section('title', $category->exists ? 'Editar prática' : 'Nova prática')

@section('content')
@php($input = 'w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm')
<div class="mx-auto max-w-xl">
    <h1 class="mb-6 text-2xl font-extrabold text-neutral-900">{{ $category->exists ? 'Editar prática' : 'Nova prática' }}</h1>
    <form method="POST" action="{{ $category->exists ? route('admin.practices.update', $category) : route('admin.practices.store') }}" class="card space-y-4 p-6">
        @csrf
        @if($category->exists) @method('PUT') @endif
        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">Nome</label>
            <input name="name" value="{{ old('name', $category->name) }}" required class="{{ $input }}">
            @error('name')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
        </div>
        <div class="grid grid-cols-2 gap-3">
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Ícone (emoji)</label>
                <input name="icon" value="{{ old('icon', $category->icon) }}" class="{{ $input }}" placeholder="🌿">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Cor (hex)</label>
                <input name="accent" value="{{ old('accent', $category->accent) }}" class="{{ $input }}" placeholder="#3b7a57">
            </div>
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">Prática mãe (opcional)</label>
            <select name="parent_id" class="{{ $input }}">
                <option value="">— Raiz —</option>
                @foreach($roots as $root)<option value="{{ $root->id }}" @selected((int) old('parent_id', $category->parent_id) === $root->id)>{{ $root->name }}</option>@endforeach
            </select>
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">Descrição</label>
            <textarea name="description" rows="3" class="{{ $input }}">{{ old('description', $category->description) }}</textarea>
        </div>
        <label class="flex items-center gap-2 text-sm text-neutral-700">
            <input type="checkbox" name="is_active" value="1" @checked(old('is_active', $category->is_active ?? true))> Ativa
        </label>
        <div class="flex items-center justify-between pt-2">
            <a href="{{ route('admin.practices.index') }}" class="text-sm text-neutral-500 hover:underline">← Voltar</a>
            <button class="btn-brand">Salvar</button>
        </div>
    </form>
    @if($category->exists)
        <form method="POST" action="{{ route('admin.practices.destroy', $category) }}" class="mt-3 text-right" onsubmit="return confirm('Remover prática?')">@csrf @method('DELETE')<button class="text-sm text-red-500 hover:underline">Remover prática</button></form>
    @endif
</div>
@endsection
