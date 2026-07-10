@extends('layouts.dashboard')

@section('title', 'Encomendas')

@php
    // Cor de cabeçalho por coluna do quadro.
    $columnStyles = [
        'requested' => 'bg-caramel-100 text-caramel-700',
        'quoted' => 'bg-brand-100 text-brand-700',
        'confirmed' => 'bg-pistache-100 text-pistache-600',
        'producing' => 'bg-amber-100 text-amber-700',
        'ready' => 'bg-sky-100 text-sky-700',
        'delivered' => 'bg-neutral-200 text-neutral-600',
    ];
@endphp

@section('content')
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
            <h1 class="text-2xl font-extrabold text-neutral-900">⭐ Encomendas</h1>
            <p class="text-sm text-neutral-500">Arraste o olhar pelo fluxo: solicitada → orçada → confirmada → produção → pronta → entregue.</p>
        </div>
    </div>

    {{-- Kanban --}}
    <div class="-mx-4 overflow-x-auto px-4 pb-4 no-scrollbar sm:mx-0 sm:px-0">
        <div class="flex min-w-max gap-4">
            @foreach(\App\Models\CustomOrder::BOARD as $status)
                @php($cards = $board->get($status) ?? collect())
                <div class="flex w-72 shrink-0 flex-col rounded-3xl bg-neutral-50 p-3">
                    <div class="mb-3 flex items-center justify-between px-1">
                        <span class="chip {{ $columnStyles[$status] ?? 'bg-neutral-200 text-neutral-600' }}">{{ \App\Models\CustomOrder::STATUSES[$status] }}</span>
                        <span class="text-xs font-bold text-neutral-400">{{ $cards->count() }}</span>
                    </div>

                    <div class="space-y-3">
                        @forelse($cards as $order)
                            <a href="{{ route('admin.custom-orders.show', $order) }}" class="block rounded-2xl border border-neutral-200 bg-white p-3 shadow-sm transition hover:border-brand-300 hover:shadow-md">
                                <p class="line-clamp-2 text-sm font-bold text-neutral-900">{{ $order->title }}</p>
                                <p class="mt-1 text-xs text-neutral-500">{{ $order->customer_name }}</p>
                                <div class="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
                                    <span class="chip bg-neutral-100 text-neutral-600">📅 {{ $order->event_date?->format('d/m/Y') }}</span>
                                    <span class="chip bg-neutral-100 text-neutral-600">{{ $order->fulfillment_type === 'delivery' ? '🛵 Entrega' : '🏪 Retira' }}</span>
                                </div>
                                <div class="mt-2 border-t border-neutral-100 pt-2">
                                    @if($order->isQuoted())
                                        <span class="text-sm font-extrabold text-brand-700">{{ money($order->quoted_price) }}</span>
                                    @else
                                        <span class="text-xs font-semibold text-caramel-600">A orçar</span>
                                    @endif
                                </div>
                            </a>
                        @empty
                            <p class="rounded-2xl border border-dashed border-neutral-200 py-6 text-center text-xs text-neutral-400">Vazio</p>
                        @endforelse
                    </div>
                </div>
            @endforeach
        </div>
    </div>

    {{-- Encerradas --}}
    @if($archived->isNotEmpty())
        <div class="mt-8">
            <h2 class="mb-3 font-bold text-neutral-900">Encerradas (recusadas/canceladas)</h2>
            <div class="card divide-y divide-neutral-100">
                @foreach($archived as $order)
                    <a href="{{ route('admin.custom-orders.show', $order) }}" class="flex items-center justify-between p-4 text-sm hover:bg-neutral-50">
                        <div>
                            <p class="font-medium text-neutral-700">{{ $order->title }}</p>
                            <p class="text-xs text-neutral-400">{{ $order->customer_name }} · {{ $order->event_date?->format('d/m/Y') }}</p>
                        </div>
                        <span class="chip bg-red-50 text-red-600">{{ $order->status_label }}</span>
                    </a>
                @endforeach
            </div>
        </div>
    @endif
@endsection
