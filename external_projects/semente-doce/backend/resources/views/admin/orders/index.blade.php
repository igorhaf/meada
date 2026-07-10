@extends('layouts.dashboard')

@section('title', 'Pedidos')

@section('content')
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
            <h1 class="text-2xl font-extrabold text-neutral-900">Pedidos 🛍️</h1>
            <p class="text-sm text-neutral-500">Acompanhe o preparo e a entrega da cozinha.</p>
        </div>
    </div>

    {{-- Filtro por status --}}
    <div class="mb-4 flex flex-wrap gap-2">
        <a href="{{ route('admin.orders.index') }}" class="chip {{ $status ? 'bg-neutral-100 text-neutral-600' : 'bg-brand-600 text-white' }}">Todos</a>
        @foreach(\App\Models\Order::STATUSES as $value => $label)
            <a href="{{ route('admin.orders.index', ['status' => $value]) }}" class="chip {{ $status === $value ? 'bg-brand-600 text-white' : 'bg-neutral-100 text-neutral-600' }}">
                {{ $label }} <span class="opacity-60">{{ $counts->get($value, 0) }}</span>
            </a>
        @endforeach
    </div>

    <form method="GET" class="mb-4">
        @if($status)<input type="hidden" name="status" value="{{ $status }}">@endif
        <input name="q" value="{{ request('q') }}" placeholder="Buscar por referência ou cliente…"
            class="w-full max-w-sm rounded-full border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
    </form>

    <div class="card overflow-hidden">
        @if($orders->isEmpty())
            <div class="flex flex-col items-center justify-center py-16 text-center">
                <div class="text-5xl">🧾</div>
                <p class="mt-4 font-semibold text-neutral-700">Nenhum pedido aqui</p>
            </div>
        @else
            <div class="overflow-x-auto">
                <table class="w-full text-sm">
                    <thead class="border-b border-neutral-200 bg-neutral-50 text-left text-xs uppercase tracking-wide text-neutral-500">
                        <tr>
                            <th class="px-4 py-3">Pedido</th>
                            <th class="px-4 py-3">Cliente</th>
                            <th class="px-4 py-3">Entrega</th>
                            <th class="px-4 py-3">Total</th>
                            <th class="px-4 py-3">Pagamento</th>
                            <th class="px-4 py-3">Status</th>
                            <th class="px-4 py-3">Data</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-neutral-100">
                        @foreach($orders as $order)
                            <tr class="hover:bg-neutral-50">
                                <td class="px-4 py-3"><a href="{{ route('admin.orders.show', $order) }}" class="font-medium text-brand-700 hover:underline">{{ $order->reference }}</a></td>
                                <td class="px-4 py-3 text-neutral-700">{{ $order->buyer_name }}</td>
                                <td class="px-4 py-3 text-neutral-500">{{ $order->fulfillment_label }}</td>
                                <td class="px-4 py-3 font-semibold text-neutral-900">{{ money($order->total) }}</td>
                                <td class="px-4 py-3">@include('partials.payment-badge')</td>
                                <td class="px-4 py-3"><span class="chip bg-caramel-100 text-caramel-700">{{ $order->status_label }}</span></td>
                                <td class="px-4 py-3 text-neutral-500">{{ $order->created_at->format('d/m/Y') }}</td>
                            </tr>
                        @endforeach
                    </tbody>
                </table>
            </div>
        @endif
    </div>

    <div class="mt-6">{{ $orders->onEachSide(1)->links() }}</div>
@endsection
