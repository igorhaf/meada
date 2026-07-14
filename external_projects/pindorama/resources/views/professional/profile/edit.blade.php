@extends('layouts.dashboard')

@section('title', 'Meu perfil')

@section('content')
@php($input = 'w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500')
<div class="mx-auto max-w-3xl">
    <div class="mb-6 flex items-center justify-between">
        <h1 class="text-2xl font-extrabold text-neutral-900">Meu perfil público</h1>
        @if($user->professional_url)
            <a href="{{ $user->professional_url }}" target="_blank" rel="noopener" class="text-sm font-medium text-brand-700 hover:underline">Ver página pública ↗</a>
        @endif
    </div>

    <form method="POST" action="{{ route('professional.profile.update') }}" enctype="multipart/form-data" class="space-y-6">
        @csrf
        @method('PUT')

        <div class="card space-y-4 p-6">
            <h2 class="font-bold text-neutral-800">Identidade</h2>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Nome público</label>
                <input type="text" name="professional_name" value="{{ old('professional_name', $user->professional_name ?: $user->name) }}" required class="{{ $input }}">
                @error('professional_name')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Chamada (headline)</label>
                <input type="text" name="headline" value="{{ old('headline', $user->headline) }}" placeholder="Ex.: Acupunturista • Fitoterapeuta" class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Sobre você</label>
                <textarea name="bio" rows="5" class="{{ $input }}">{{ old('bio', $user->bio) }}</textarea>
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Registro profissional (opcional)</label>
                <input type="text" name="registration_council" value="{{ old('registration_council', $user->registration_council) }}" class="{{ $input }}">
            </div>
        </div>

        <div class="card grid gap-4 p-6 sm:grid-cols-2">
            <h2 class="col-span-full font-bold text-neutral-800">Contato & localização</h2>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Cidade</label>
                <input type="text" name="city" value="{{ old('city', $user->city) }}" class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Estado (UF)</label>
                <input type="text" name="state" value="{{ old('state', $user->state) }}" maxlength="60" class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Telefone</label>
                <input type="text" name="phone" value="{{ old('phone', $user->phone) }}" class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">WhatsApp</label>
                <input type="text" name="whatsapp" value="{{ old('whatsapp', $user->whatsapp) }}" placeholder="Ex.: 11999998888" class="{{ $input }}">
            </div>
        </div>

        <div class="card grid gap-4 p-6 sm:grid-cols-2">
            <h2 class="col-span-full font-bold text-neutral-800">Imagens</h2>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Foto (avatar)</label>
                <input type="file" name="avatar" accept="image/*" class="{{ $input }}">
                @if($user->avatar_url)<p class="mt-1 text-xs text-neutral-500">Enviada ✓</p>@endif
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Banner</label>
                <input type="file" name="banner" accept="image/*" class="{{ $input }}">
                @if($user->banner_url)<p class="mt-1 text-xs text-neutral-500">Enviado ✓</p>@endif
            </div>
        </div>

        <div class="card grid gap-4 p-6 sm:grid-cols-2">
            <h2 class="col-span-full font-bold text-neutral-800">Redes sociais</h2>
            @foreach(['instagram_url'=>'Instagram','facebook_url'=>'Facebook','youtube_url'=>'YouTube','website_url'=>'Site pessoal'] as $field=>$label)
                <div><label class="mb-1 block text-sm font-medium text-neutral-700">{{ $label }}</label><input type="url" name="{{ $field }}" value="{{ old($field,$user->{$field}) }}" placeholder="https://" class="{{ $input }}"></div>
            @endforeach
        </div>

        <div class="card p-6">
            <h2 class="mb-3 font-bold text-neutral-800">Especialidades</h2>
            <div class="grid grid-cols-2 gap-2 sm:grid-cols-3">
                @foreach($categories as $cat)
                    <label class="flex cursor-pointer items-center gap-2 rounded-lg border border-neutral-200 px-3 py-2 text-sm has-[:checked]:border-brand-500 has-[:checked]:bg-brand-50">
                        <input type="checkbox" name="specialties[]" value="{{ $cat->id }}" @checked(in_array($cat->id, old('specialties', $selectedSpecialties)))>
                        <span>{{ $cat->icon }} {{ $cat->name }}</span>
                    </label>
                @endforeach
            </div>
        </div>

        <div class="flex justify-end">
            <button type="submit" class="btn-brand">Salvar perfil</button>
        </div>
    </form>
</div>
@endsection
