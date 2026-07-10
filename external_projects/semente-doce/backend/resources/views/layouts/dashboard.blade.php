<!DOCTYPE html>
<html lang="pt-BR" class="h-full">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>@yield('title', 'Painel') · {{ settings()->site_name }}</title>
    <link rel="icon" href="{{ placeholder_image('semente-favicon', '🍬', 64, 64) }}">
    @vite(['resources/css/app.css', 'resources/js/app.js'])
</head>
<body class="flex min-h-full flex-col bg-neutral-100">
    @php($user = auth()->user())

    {{-- Top bar --}}
    <header class="bg-brand-800 text-white">
        <div class="container-doce flex items-center justify-between py-3">
            <a href="{{ route('admin.dashboard') }}" class="flex items-center gap-2 text-xl font-extrabold">
                <span class="text-2xl">🍬</span> {{ settings()->site_name }} <span class="font-light text-white/60">· Cozinha</span>
            </a>
            <div class="flex items-center gap-4 text-sm">
                <a href="{{ route('home') }}" class="hidden text-white/80 hover:text-white sm:inline">Ver loja ↗</a>
                <span class="hidden text-white/60 sm:inline">|</span>
                <span class="text-white/90">{{ $user->name }}</span>
                <form method="POST" action="{{ route('logout') }}">
                    @csrf
                    <button class="rounded-lg bg-white/10 px-3 py-1.5 font-medium transition hover:bg-white/20">Sair</button>
                </form>
            </div>
        </div>

        {{-- Tabs --}}
        <nav class="border-t border-white/10">
            <div class="container-doce flex items-center gap-1 overflow-x-auto no-scrollbar">
                @php($tab = fn ($pattern) => request()->routeIs($pattern) ? 'border-white text-white' : 'border-transparent text-white/70 hover:text-white')
                <a href="{{ route('admin.dashboard') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.dashboard') }}">Visão geral</a>
                <a href="{{ route('admin.orders.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.orders.*') }}">Pedidos</a>
                <a href="{{ route('admin.custom-orders.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.custom-orders.*') }}">⭐ Encomendas</a>
                <a href="{{ route('admin.kits.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.kits.*') }}">⭐ Kits</a>
                <a href="{{ route('admin.products.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.products.*') }}">Cardápio</a>
                <a href="{{ route('admin.categories.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.categories.*') }}">Categorias</a>

                <span class="mx-2 h-5 w-px bg-white/20"></span>
                <span class="whitespace-nowrap py-3 text-xs font-bold uppercase tracking-wide text-pistache-400">Gestão</span>
                <a href="{{ route('admin.finance.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.finance.*') }}">Financeiro</a>
                <a href="{{ route('admin.profit.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.profit.*') }}">Lucro</a>
                <a href="{{ route('admin.ingredients.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.ingredients.*') }}">Insumos</a>
                <a href="{{ route('admin.purchases.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.purchases.*') }}">Compras</a>
                <a href="{{ route('admin.expenses.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.expenses.*') }}">Gastos</a>

                <span class="mx-2 h-5 w-px bg-white/20"></span>
                <span class="whitespace-nowrap py-3 text-xs font-bold uppercase tracking-wide text-caramel-300">Loja</span>
                <a href="{{ route('admin.featured') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.featured') }}">Destaques</a>
                <a href="{{ route('admin.banners.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.banners.*') }}">Banners</a>
                <a href="{{ route('admin.delivery-zones.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.delivery-zones.*') }}">Entregas</a>
                <a href="{{ route('admin.settings.edit') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.settings.*') }}">Config</a>
                <a href="{{ route('admin.pages.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.pages.*') }}">Páginas</a>
                <a href="{{ route('admin.messages.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.messages.*') }}">Mensagens</a>
                <a href="{{ route('admin.payments') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.payments') }}">Pagamentos</a>
            </div>
        </nav>
    </header>

    @if(session('status'))
        <div class="container-doce pt-4">
            <div class="rounded-xl bg-pistache-100 px-4 py-3 text-sm font-medium text-pistache-600">{{ session('status') }}</div>
        </div>
    @endif

    <main class="container-doce flex-1 py-8">
        @yield('content')
    </main>
</body>
</html>
