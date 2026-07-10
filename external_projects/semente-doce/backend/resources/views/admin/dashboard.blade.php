@extends('layouts.dashboard')

@section('title', 'Visão geral')

@section('content')
    <div class="mb-6">
        <span class="chip bg-caramel-100 text-caramel-700">Cozinha · painel</span>
        <h1 class="mt-2 text-2xl font-extrabold text-neutral-900">Bom dia! 🍬 Como está a doceria hoje</h1>
    </div>

    {{-- Métricas --}}
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-5">
        @foreach([
            ['Pedidos hoje', $stats['orders_today'], '🛍️', route('admin.orders.index')],
            ['Encomendas abertas', $stats['custom_open'], '⭐', route('admin.custom-orders.index')],
            ['Itens no cardápio', $stats['products'], '🧁', route('admin.products.index')],
            ['Kits ativos', $stats['kits'], '🎁', route('admin.kits.index')],
            ['Faturamento pago', money($stats['revenue']), '💰', route('admin.payments')],
        ] as [$label, $value, $icon, $link])
            <a href="{{ $link }}" class="card block p-4">
                <div class="flex items-center justify-between"><span class="text-xs text-neutral-500">{{ $label }}</span><span>{{ $icon }}</span></div>
                <p class="mt-1 text-xl font-extrabold text-neutral-900">{{ $value }}</p>
            </a>
        @endforeach
    </div>

    <div class="mt-6 grid gap-6 lg:grid-cols-2">
        {{-- Encomendas recentes --}}
        <div class="card p-5">
            <div class="mb-4 flex items-center justify-between">
                <h2 class="font-bold text-neutral-900">⭐ Encomendas recentes</h2>
                <a href="{{ route('admin.custom-orders.index') }}" class="text-sm font-semibold text-brand-700 hover:underline">Ver quadro →</a>
            </div>
            @forelse($recentCustomOrders as $co)
                <div class="flex items-center justify-between border-t border-neutral-100 py-3 text-sm first:border-0">
                    <div class="min-w-0">
                        <a href="{{ route('admin.custom-orders.show', $co) }}" class="font-medium text-neutral-800 hover:text-brand-700">{{ $co->title }}</a>
                        <p class="text-xs text-neutral-500">{{ $co->customer_name }} · evento {{ $co->event_date?->format('d/m/Y') }}</p>
                    </div>
                    <span class="chip bg-brand-50 text-brand-700">{{ $co->status_label }}</span>
                </div>
            @empty
                <p class="py-6 text-center text-sm text-neutral-400">Nenhuma encomenda ainda.</p>
            @endforelse
        </div>

        {{-- Pedidos recentes --}}
        <div class="card p-5">
            <div class="mb-4 flex items-center justify-between">
                <h2 class="font-bold text-neutral-900">🛍️ Pedidos recentes</h2>
                <a href="{{ route('admin.orders.index') }}" class="text-sm font-semibold text-brand-700 hover:underline">Ver todos →</a>
            </div>
            @forelse($recentOrders as $order)
                <div class="flex items-center justify-between border-t border-neutral-100 py-3 text-sm first:border-0">
                    <div class="min-w-0">
                        <a href="{{ route('admin.orders.show', $order) }}" class="font-medium text-neutral-800 hover:text-brand-700">{{ $order->reference }}</a>
                        <p class="text-xs text-neutral-500">{{ $order->buyer_name }} · {{ $order->items->count() }} item(s) · {{ $order->created_at->format('d/m/Y') }}</p>
                    </div>
                    <span class="font-bold text-brand-700">{{ money($order->total) }}</span>
                </div>
            @empty
                <p class="py-6 text-center text-sm text-neutral-400">Nenhum pedido ainda.</p>
            @endforelse
        </div>
    </div>
@endsection
