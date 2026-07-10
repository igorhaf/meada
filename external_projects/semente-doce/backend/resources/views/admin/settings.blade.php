@extends('layouts.dashboard')

@section('title', 'Configurações da doceria')

@section('content')
    <div class="mx-auto max-w-2xl">
        <h1 class="mb-1 text-2xl font-extrabold text-neutral-900">Configurações da doceria</h1>
        <p class="mb-6 text-sm text-neutral-500">Identidade, aviso do topo, redes sociais, contato e funcionamento.</p>

        @if ($errors->any())
            <div class="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
                <ul class="list-inside list-disc">@foreach ($errors->all() as $e)<li>{{ $e }}</li>@endforeach</ul>
            </div>
        @endif

        <form method="POST" action="{{ route('admin.settings.update') }}" class="space-y-6">
            @csrf @method('PUT')

            <div class="card space-y-4 p-6">
                <h2 class="font-bold text-neutral-900">Identidade</h2>
                <div>
                    <label class="field-label">Nome da loja *</label>
                    <input name="site_name" value="{{ old('site_name', $settings->site_name) }}" required class="field-input">
                </div>
                <div>
                    <label class="field-label">Slogan</label>
                    <input name="tagline" value="{{ old('tagline', $settings->tagline) }}" class="field-input">
                </div>
                <div>
                    <label class="field-label">Aviso do topo (barra de anúncio)</label>
                    <input name="announcement" value="{{ old('announcement', $settings->announcement) }}" placeholder="Ex.: Encomendas de Natal já abertas! 🎄" class="field-input">
                </div>
                <div>
                    <label class="field-label">Sobre a doceria</label>
                    <textarea name="about" rows="3" class="field-input">{{ old('about', $settings->about) }}</textarea>
                </div>
            </div>

            <div class="card grid gap-4 p-6 sm:grid-cols-2">
                <h2 class="font-bold text-neutral-900 sm:col-span-2">Redes sociais</h2>
                @foreach(['instagram_url' => 'Instagram', 'facebook_url' => 'Facebook', 'tiktok_url' => 'TikTok'] as $field => $label)
                    <div>
                        <label class="field-label">{{ $label }}</label>
                        <input name="{{ $field }}" value="{{ old($field, $settings->$field) }}" placeholder="https://…" class="field-input">
                    </div>
                @endforeach
                <div>
                    <label class="field-label">WhatsApp</label>
                    <input name="whatsapp" value="{{ old('whatsapp', $settings->whatsapp) }}" placeholder="+55 11 90000-0000" class="field-input">
                </div>
            </div>

            <div class="card grid gap-4 p-6 sm:grid-cols-2">
                <h2 class="font-bold text-neutral-900 sm:col-span-2">Contato & funcionamento</h2>
                <div>
                    <label class="field-label">E-mail</label>
                    <input name="contact_email" value="{{ old('contact_email', $settings->contact_email) }}" class="field-input">
                </div>
                <div>
                    <label class="field-label">Telefone</label>
                    <input name="contact_phone" value="{{ old('contact_phone', $settings->contact_phone) }}" class="field-input">
                </div>
                <div class="sm:col-span-2">
                    <label class="field-label">Endereço</label>
                    <input name="address" value="{{ old('address', $settings->address) }}" placeholder="Rua das Amoras, 123 — Centro" class="field-input">
                </div>
                <div class="sm:col-span-2">
                    <label class="field-label">Horário de funcionamento</label>
                    <textarea name="opening_hours" rows="3" placeholder="Seg a Sex: 9h–18h&#10;Sáb: 9h–13h" class="field-input">{{ old('opening_hours', $settings->opening_hours) }}</textarea>
                </div>
            </div>

            <button class="btn-brand">Salvar configurações</button>
        </form>
    </div>
@endsection
