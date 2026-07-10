@extends('layouts.app')

@section('title', 'Minhas encomendas')

@php($statusBadge = [
    'requested' => 'bg-amber-50 text-amber-700',
    'quoted' => 'bg-sky-50 text-sky-700',
    'confirmed' => 'bg-brand-50 text-brand-700',
    'producing' => 'bg-caramel-100 text-caramel-700',
    'ready' => 'bg-pistache-100 text-pistache-600',
    'delivered' => 'bg-neutral-100 text-neutral-600',
    'declined' => 'bg-red-50 text-red-600',
    'cancelled' => 'bg-red-50 text-red-600',
])

@section('content')
    <div class="container-doce py-8">
        <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
            <h1 class="text-2xl font-extrabold text-neutral-900">🎂 Minhas encomendas</h1>
            <a href="{{ route('custom-orders.create') }}" class="btn-caramel !py-2 text-sm">Nova encomenda</a>
        </div>

        @forelse($customOrders as $order)
            <a href="{{ route('custom-orders.show', $order) }}" class="card mb-4 block overflow-hidden transition hover:border-brand-300">
                <div class="flex flex-wrap items-center justify-between gap-2 border-b border-neutral-100 bg-neutral-50 px-5 py-3 text-sm">
                    <div class="flex items-center gap-2">
                        <span class="font-bold text-neutral-800">{{ $order->reference }}</span>
                        <span class="chip {{ $statusBadge[$order->status] ?? 'bg-neutral-100 text-neutral-600' }}">{{ $order->status_label }}</span>
                    </div>
                    <span class="text-neutral-500">{{ $order->created_at->format('d/m/Y') }}</span>
                </div>
                <div class="flex flex-wrap items-center gap-4 p-5">
                    <div class="min-w-0 flex-1">
                        <p class="font-semibold text-neutral-800">{{ $order->title }}</p>
                        <p class="mt-0.5 text-sm text-neutral-500">
                            {{ $order->quantity }}× · {{ $order->fulfillment_label }}
                            @if($order->event_date)· para {{ $order->event_date->format('d/m/Y') }}@endif
                        </p>
                    </div>
                    <div class="text-right">
                        @if($order->isQuoted())
                            <p class="text-lg font-extrabold text-neutral-900">{{ money($order->quoted_price) }}</p>
                            <span class="text-xs text-neutral-400">orçado</span>
                        @else
                            <span class="text-sm text-caramel-600">aguardando orçamento</span>
                        @endif
                        <p class="text-sm text-brand-700">Ver detalhes →</p>
                    </div>
                </div>
            </a>
        @empty
            <div class="card flex flex-col items-center justify-center py-16 text-center">
                <div class="text-5xl">🎂</div>
                <p class="mt-4 font-semibold text-neutral-700">Você ainda não fez encomendas</p>
                <p class="mt-1 text-sm text-neutral-500">Que tal um bolo sob medida para a próxima festa?</p>
                <a href="{{ route('custom-orders.create') }}" class="btn-caramel mt-6">Fazer uma encomenda</a>
            </div>
        @endforelse

        <div class="mt-6">{{ $customOrders->onEachSide(1)->links() }}</div>
    </div>
@endsection
