@extends('layouts.site')

@section('title', 'Entrar · Germinar')

@section('content')
<style>
    /* Estilos exclusivos da tela de login (o site.css não cobre esta página). */
    .login-wrap {
        min-height: 100vh;
        display: grid;
        place-items: center;
        padding: 24px;
        background: var(--color-bg);
    }
    .login-card {
        width: min(400px, 100%);
        background: var(--color-accent-2-100);
        border-radius: 32px;
        padding: 44px 36px 40px;
        display: flex;
        flex-direction: column;
        gap: 16px;
        box-shadow: var(--shadow-md);
    }
    .login-logo {
        width: 56px;
        height: 56px;
        border-radius: 50%;
        object-fit: cover;
        background: #fdfaf4;
        margin: 0 auto;
    }
    .login-title {
        font-family: var(--font-heading);
        font-weight: 400;
        font-size: 28px;
        text-align: center;
        margin: 0 0 8px;
    }
    .login-error {
        font-size: 13px;
        color: var(--color-accent-700);
        margin: 6px 0 0;
    }
    .login-remember {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 14px;
        color: var(--color-neutral-800);
        cursor: pointer;
    }
    .login-card .btn-primary { width: 100%; margin-top: 8px; }
</style>

<div class="login-wrap">
    <form class="login-card" method="POST" action="{{ route('login.store') }}">
        @csrf
        <img class="login-logo" src="{{ asset('images/logo-mark.png') }}" alt="Germinar">
        <h1 class="login-title">Entrar</h1>

        <div class="field">
            <label for="email">E-mail</label>
            <input class="input" type="email" id="email" name="email"
                   value="{{ old('email') }}" required autofocus autocomplete="email">
            @error('email')
                <p class="login-error">{{ $message }}</p>
            @enderror
        </div>

        <div class="field">
            <label for="password">Senha</label>
            <input class="input" type="password" id="password" name="password"
                   required autocomplete="current-password">
            @error('password')
                <p class="login-error">{{ $message }}</p>
            @enderror
        </div>

        <label class="login-remember">
            <input type="checkbox" name="remember" value="1">
            Continuar conectada
        </label>

        <button type="submit" class="btn btn-primary">Entrar</button>
    </form>
</div>
@endsection
