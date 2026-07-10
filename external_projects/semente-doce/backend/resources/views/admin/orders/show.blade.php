@extends('layouts.dashboard')

@section('title', 'Pedido ' . $order->reference)

@section('content')
    <div class="mb-6">
        <a href="{{ route('admin.orders.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar aos pedidos</a>
        <div class="mt-1 flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-2xl font-extrabold text-neutral-900">Pedido {{ $order->reference }}</h1>
            <div class="flex items-center gap-2">
                @include('partials.payment-badge')
                <span class="chip bg-caramel-100 text-caramel-700">{{ $order->status_label }}</span>
            </div>
        </div>
    </div>

    <div class="grid gap-6 lg:grid-cols-3">
        {{-- Itens --}}
        <div class="card divide-y divide-neutral-100 lg:col-span-2">
            @foreach($order->items as $item)
                <div class="flex items-center gap-4 p-4">
                    <img src="{{ $item->image_path }}" alt="" class="h-16 w-16 rounded-xl object-cover">
                    <div class="min-w-0 flex-1">
                        <p class="font-medium text-neutral-800">{{ $item->title }}</p>
                        @if($item->options_summary)<p class="text-xs text-neutral-400">{{ $item->options_summary }}</p>@endif
                        <p class="text-sm text-neutral-500">{{ $item->qty }}x · {{ money($item->price) }}</p>
                    </div>
                    <span class="font-bold text-neutral-900">{{ money($item->line_total) }}</span>
                </div>
            @endforeach
        </div>

        {{-- Coluna lateral --}}
        <div class="space-y-4">
            {{-- Avanço de status --}}
            <div class="card p-5">
                <h2 class="mb-3 font-bold text-neutral-900">Atendimento</h2>
                <form method="POST" action="{{ route('admin.orders.status', $order) }}" class="flex items-center gap-2">
                    @csrf
                    <select name="status" class="field-input">
                        @foreach(\App\Models\Order::STATUSES as $value => $label)
                            <option value="{{ $value }}" @selected($order->status === $value)>{{ $label }}</option>
                        @endforeach
                    </select>
                    <button class="btn-brand shrink-0 !px-4 !py-2.5 text-sm">Atualizar</button>
                </form>
            </div>

            {{-- Resumo --}}
            <div class="card p-5">
                <h2 class="mb-3 font-bold text-neutral-900">Resumo</h2>
                <dl class="space-y-2 text-sm">
                    <div class="flex justify-between"><dt class="text-neutral-500">Subtotal</dt><dd class="font-semibold">{{ money($order->subtotal) }}</dd></div>
                    <div class="flex justify-between"><dt class="text-neutral-500">Entrega</dt><dd class="font-semibold {{ (float) $order->delivery_fee === 0.0 ? 'text-pistache-600' : '' }}">{{ (float) $order->delivery_fee === 0.0 ? 'Grátis' : money($order->delivery_fee) }}</dd></div>
                    <div class="flex justify-between border-t border-neutral-100 pt-2 text-base"><dt class="font-medium text-neutral-600">Total</dt><dd class="font-extrabold text-neutral-900">{{ money($order->total) }}</dd></div>
                </dl>
            </div>

            {{-- Cliente / entrega --}}
            <div class="card p-5 text-sm">
                <h2 class="mb-3 font-bold text-neutral-900">{{ $order->fulfillment_label }}</h2>
                <p class="text-neutral-700">{{ $order->buyer_name }}</p>
                <p class="text-neutral-500">{{ $order->buyer_email }}</p>
                @if($order->buyer_phone)<p class="text-neutral-500">{{ $order->buyer_phone }}</p>@endif
                @if(! $order->isPickup())
                    @if($order->delivery_neighborhood)<p class="mt-2 text-neutral-500">Bairro: {{ $order->delivery_neighborhood }}</p>@endif
                    @if($order->delivery_address)<p class="text-neutral-500">{{ $order->delivery_address }}</p>@endif
                @endif
                @if($order->scheduled_for)<p class="mt-2 text-neutral-500">Agendado: {{ $order->scheduled_for->format('d/m/Y') }}</p>@endif
                @if($order->notes)<p class="mt-2 rounded-xl bg-neutral-50 p-2 text-neutral-600">📝 {{ $order->notes }}</p>@endif
            </div>
        </div>
    </div>
@endsection
