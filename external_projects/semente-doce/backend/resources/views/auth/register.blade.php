@extends('layouts.auth')

@section('title', 'Criar conta')

@section('content')
    <div class="card p-8">
        <h1 class="text-2xl font-extrabold text-neutral-900">Criar conta 🧁</h1>
        <p class="mt-1 text-sm text-neutral-500">Crie sua conta e adoce seus dias com a Semente Doce.</p>

        @if ($errors->any())
            <div class="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
                <ul class="list-inside list-disc space-y-0.5">
                    @foreach ($errors->all() as $error)<li>{{ $error }}</li>@endforeach
                </ul>
            </div>
        @endif

        @include('partials.google-button')

        <form method="POST" action="{{ route('register') }}" class="mt-6 space-y-4">
            @csrf
            <div>
                <label class="field-label">Nome completo</label>
                <input type="text" name="name" value="{{ old('name') }}" required autofocus
                    class="field-input">
            </div>
            <div>
                <label class="field-label">E-mail</label>
                <input type="email" name="email" value="{{ old('email') }}" required
                    class="field-input">
            </div>
            <div>
                <label class="field-label">Telefone / WhatsApp <span class="font-normal text-neutral-400">(opcional)</span></label>
                <input type="text" name="phone" value="{{ old('phone') }}" placeholder="(00) 00000-0000"
                    class="field-input">
            </div>
            <div class="grid grid-cols-2 gap-3">
                <div>
                    <label class="field-label">Senha</label>
                    <input type="password" name="password" required
                        class="field-input">
                </div>
                <div>
                    <label class="field-label">Confirmar</label>
                    <input type="password" name="password_confirmation" required
                        class="field-input">
                </div>
            </div>

            <button type="submit" class="btn-brand w-full">Criar conta</button>
        </form>

        <p class="mt-6 text-center text-sm text-neutral-500">
            Já tem conta?
            <a href="{{ route('login') }}" class="font-semibold text-brand-700 hover:underline">Entrar</a>
        </p>
    </div>
@endsection
