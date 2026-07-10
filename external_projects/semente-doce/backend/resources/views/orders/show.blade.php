@extends('layouts.app')

@section('title', 'Pedido ' . $order->reference)

@php($orderStatusBadge = [
    'pending' => 'bg-amber-50 text-amber-700',
    'paid' => 'bg-sky-50 text-sky-700',
    'preparing' => 'bg-caramel-100 text-caramel-700',
    'out_for_delivery' => 'bg-brand-50 text-brand-700',
    'ready' => 'bg-pistache-100 text-pistache-600',
    'delivered' => 'bg-neutral-100 text-neutral-600',
    'cancelled' => 'bg-red-50 text-red-600',
])

@section('content')
    <div class="container-doce py-8">
        @if(session('placed') || request('pago'))
            @if($order->isPaid())
                <div class="mb-6 flex items-center gap-4 rounded-2xl border border-brand-200 bg-brand-50 p-6">
                    <span class="text-4xl">🍬</span>
                    <div>
                        <h2 class="text-lg font-extrabold text-neutral-900">Pagamento confirmado!</h2>
                        <p class="text-sm text-neutral-600">Obrigado pelo pedido — já vamos preparar tudo fresquinho para você. 💚</p>
                    </div>
                </div>
            @else
                <div class="mb-6 flex items-center gap-4 rounded-2xl border border-amber-200 bg-amber-50 p-6">
                    <span class="text-4xl">⏳</span>
                    <div>
                        <h2 class="text-lg font-extrabold text-neutral-900">Pedido criado — aguardando pagamento</h2>
                        <p class="text-sm text-neutral-600">Assim que o pagamento for aprovado, começamos a preparar seu pedido.</p>
                    </div>
                </div>
            @endif
            {{-- Limpa a sacola do cliente após um checkout concluído --}}
            <script>try { localStorage.removeItem('semente.cart.v1'); } catch (e) {}</script>
        @endif

        @if(session('status'))
            <div class="mb-6 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800">{{ session('status') }}</div>
        @endif

        <a href="{{ route('orders.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Meus pedidos</a>

        <div class="mt-2 mb-6 flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-2xl font-extrabold text-neutral-900">Pedido {{ $order->reference }}</h1>
            <div class="flex flex-wrap items-center gap-2">
                <span class="text-xs text-neutral-400">Pagamento:</span>
                @include('partials.payment-badge')
                <span class="chip {{ $orderStatusBadge[$order->status] ?? 'bg-neutral-100 text-neutral-600' }}">{{ $order->status_label }}</span>
            </div>
        </div>

        @unless($order->isPaid() || $order->status === 'cancelled')
            <div class="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4">
                <p class="text-sm text-amber-800">Este pedido ainda não foi pago.</p>
                <form method="POST" action="{{ route('orders.retry', $order) }}">
                    @csrf
                    <button class="btn-brand !py-2 text-sm">Pagar agora</button>
                </form>
            </div>
        @endunless

        <div class="grid gap-6 lg:grid-cols-3">
            {{-- Itens --}}
            <div class="card divide-y divide-neutral-100 lg:col-span-2">
                @foreach($order->items as $item)
                    <div class="flex items-center gap-4 p-4">
                        <img src="{{ $item->image_path }}" alt="" class="h-16 w-16 rounded-xl object-cover">
                        <div class="min-w-0 flex-1">
                            @if($item->product)
                                <a href="{{ $item->product->url }}" class="font-medium text-neutral-800 hover:text-brand-700">{{ $item->title }}</a>
                            @else
                                <p class="font-medium text-neutral-800">{{ $item->title }}</p>
                            @endif
                            @if($item->options_summary)
                                <p class="text-xs text-neutral-500">{{ $item->options_summary }}</p>
                            @endif
                            <p class="text-sm text-neutral-500">{{ $item->qty }}× · {{ money($item->price) }}</p>
                        </div>
                        <span class="font-bold text-neutral-900">{{ money($item->line_total) }}</span>
                    </div>
                @endforeach
            </div>

            {{-- Resumo + entrega --}}
            <div class="space-y-4">
                <div class="card p-5">
                    <h2 class="mb-3 font-bold text-neutral-900">Resumo</h2>
                    <dl class="space-y-2 text-sm">
                        <div class="flex justify-between"><dt class="text-neutral-500">Subtotal</dt><dd class="font-semibold">{{ money($order->subtotal) }}</dd></div>
                        <div class="flex justify-between">
                            <dt class="text-neutral-500">{{ $order->isPickup() ? 'Retirada' : 'Entrega' }}</dt>
                            <dd class="font-semibold {{ (float) $order->delivery_fee === 0.0 ? 'text-pistache-600' : '' }}">{{ (float) $order->delivery_fee === 0.0 ? 'Grátis' : money($order->delivery_fee) }}</dd>
                        </div>
                        <div class="flex justify-between border-t border-neutral-100 pt-2 text-base"><dt class="font-medium text-neutral-600">Total</dt><dd class="font-extrabold text-neutral-900">{{ money($order->total) }}</dd></div>
                    </dl>
                </div>

                <div class="card p-5 text-sm">
                    <h2 class="mb-3 font-bold text-neutral-900">{{ $order->isPickup() ? 'Retirada na loja' : 'Entrega' }}</h2>
                    <p class="text-neutral-700">{{ $order->buyer_name }}</p>
                    <p class="text-neutral-500">{{ $order->buyer_email }}</p>
                    @if($order->buyer_phone)<p class="text-neutral-500">{{ $order->buyer_phone }}</p>@endif
                    @if($order->isPickup())
                        <p class="mt-2 text-neutral-500">🏠 Retirar em: {{ config('delivery.origin') }}</p>
                    @else
                        @if($order->delivery_neighborhood)<p class="mt-2 text-neutral-500">Bairro: {{ $order->delivery_neighborhood }}</p>@endif
                        @if($order->delivery_address)<p class="text-neutral-500">{{ $order->delivery_address }}</p>@endif
                    @endif
                    @if($order->scheduled_for)<p class="mt-2 text-neutral-500">📅 Agendado para {{ $order->scheduled_for->format('d/m/Y') }}</p>@endif
                    @if($order->notes)<p class="mt-2 text-neutral-500">📝 {{ $order->notes }}</p>@endif
                </div>
            </div>
        </div>
    </div>
@endsection
