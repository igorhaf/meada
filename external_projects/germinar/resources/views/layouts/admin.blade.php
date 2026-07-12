<!doctype html>
<html lang="pt-BR">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>@yield('title', 'Admin') · Germinar</title>
    <meta name="robots" content="noindex">
    <link rel="icon" type="image/png" href="{{ asset('images/logo-mark.png') }}">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Caprasimo:wght@400&family=Figtree:wght@400;600;700&display=swap" rel="stylesheet">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    @vite(['resources/css/admin.css', 'resources/js/admin.js'])
</head>
<body class="admin">
<aside class="admin-sidebar">
    <a class="admin-brand" href="{{ route('home') }}">
        <img src="{{ asset('images/logo-mark.png') }}" alt="Germinar">
        <span>Germinar</span>
    </a>
    <nav class="admin-nav">
        <a href="{{ route('admin.settings.edit') }}" @class(['is-active' => request()->routeIs('admin.settings.*')])>Configurações</a>
        <a href="{{ route('admin.servicos.index') }}" @class(['is-active' => request()->routeIs('admin.servicos.*')])>O que fazemos</a>
        <a href="{{ route('admin.praticas.index') }}" @class(['is-active' => request()->routeIs('admin.praticas.*')])>Práticas integrativas</a>
        <a href="{{ route('admin.cursos.index') }}" @class(['is-active' => request()->routeIs('admin.cursos.*')])>Cursos</a>
    </nav>
    <div class="admin-sidebar-footer">
        <a href="{{ route('home') }}" target="_blank" rel="noopener">Ver o site ↗</a>
        <form method="POST" action="{{ route('logout') }}">
            @csrf
            <button type="submit" class="btn btn-ghost">Sair</button>
        </form>
    </div>
</aside>
<main class="admin-main">
    <header class="admin-header">
        <h1>@yield('heading', 'Admin')</h1>
        @yield('header-actions')
    </header>

    @if (session('status'))
        <div class="admin-flash">{{ session('status') }}</div>
    @endif

    @if ($errors->any())
        <div class="admin-flash admin-flash-error">
            <strong>Confira os campos:</strong>
            <ul>
                @foreach ($errors->all() as $error)
                    <li>{{ $error }}</li>
                @endforeach
            </ul>
        </div>
    @endif

    @yield('content')
</main>
</body>
</html>
