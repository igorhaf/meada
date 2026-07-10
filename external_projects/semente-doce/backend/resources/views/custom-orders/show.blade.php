@extends('layouts.app')

@section('title', 'Encomenda ' . $customOrder->reference)

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
        <a href="{{ route('custom-orders.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Minhas encomendas</a>

        <div class="mt-2 mb-6 flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-2xl font-extrabold text-neutral-900">Encomenda {{ $customOrder->reference }}</h1>
            <span class="chip {{ $statusBadge[$customOrder->status] ?? 'bg-neutral-100 text-neutral-600' }}">{{ $customOrder->status_label }}</span>
        </div>

        {{-- Faixa de orçamento --}}
        @if($customOrder->isQuoted())
            <div class="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-brand-200 bg-brand-50 p-5">
                <div>
                    <p class="text-sm text-neutral-500">Orçamento da doceria</p>
                    <p class="text-2xl font-extrabold text-brand-700">{{ money($customOrder->quoted_price) }}</p>
                </div>
                @if($customOrder->status === 'quoted')
                    <p class="max-w-sm text-sm text-neutral-600">Combine a confirmação com a gente pelo WhatsApp para começarmos a produção. 💚</p>
                @endif
            </div>
        @else
            <div class="mb-6 rounded-2xl border border-caramel-300 bg-caramel-100 p-5 text-sm text-caramel-700">
                ⏳ Recebemos sua encomenda! Em breve enviamos o orçamento pelo contato informado.
            </div>
        @endif

        <div class="grid gap-6 lg:grid-cols-3">
            {{-- Detalhes --}}
            <div class="card p-6 lg:col-span-2">
                <h2 class="mb-4 text-sm font-bold uppercase tracking-wide text-neutral-500">Detalhes do pedido</h2>

                <h3 class="text-lg font-bold text-neutral-900">{{ $customOrder->title }}</h3>
                @if($customOrder->description)
                    <p class="mt-2 whitespace-pre-line leading-relaxed text-neutral-600">{{ $customOrder->description }}</p>
                @endif

                <dl class="mt-5 divide-y divide-neutral-100 overflow-hidden rounded-2xl border border-neutral-200 text-sm">
                    @foreach(array_filter([
                        'Quantidade / porções' => $customOrder->quantity,
                        'Sabor' => $customOrder->flavor,
                        'Mensagem no doce' => $customOrder->message_on_item,
                        'Como receber' => $customOrder->fulfillment_label,
                        'Endereço' => $customOrder->fulfillment_type === 'delivery' ? $customOrder->delivery_address : null,
                        'Data do evento' => $customOrder->event_date?->format('d/m/Y'),
                        'Baseado em' => $customOrder->product?->title ?? $customOrder->kit?->name,
                    ], fn ($v) => $v !== null && $v !== '') as $label => $value)
                        <div class="flex justify-between gap-4 px-4 py-3">
                            <dt class="text-neutral-500">{{ $label }}</dt>
                            <dd class="text-right font-medium text-neutral-800">{{ $value }}</dd>
                        </div>
                    @endforeach
                </dl>

                @if($customOrder->reference_photo_url)
                    <a href="{{ $customOrder->reference_photo_url }}" target="_blank" rel="noopener" class="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-brand-700 hover:underline">
                        🖼️ Ver foto de referência enviada
                    </a>
                @endif

                @if($customOrder->admin_notes)
                    <div class="mt-5 rounded-2xl bg-neutral-50 p-4 text-sm text-neutral-600">
                        <p class="font-semibold text-neutral-700">Recado da doceria</p>
                        <p class="mt-1 whitespace-pre-line">{{ $customOrder->admin_notes }}</p>
                    </div>
                @endif
            </div>

            {{-- Contatos + status --}}
            <div class="space-y-4">
                <div class="card p-5 text-sm">
                    <h2 class="mb-3 font-bold text-neutral-900">Contato</h2>
                    <p class="text-neutral-700">{{ $customOrder->customer_name }}</p>
                    <p class="text-neutral-500">{{ $customOrder->customer_phone }}</p>
                    @if($customOrder->customer_email)<p class="text-neutral-500">{{ $customOrder->customer_email }}</p>@endif
                </div>

                <div class="card p-5 text-sm">
                    <h2 class="mb-3 font-bold text-neutral-900">Situação</h2>
                    <div class="flex items-center justify-between">
                        <span class="text-neutral-500">Status</span>
                        <span class="chip {{ $statusBadge[$customOrder->status] ?? 'bg-neutral-100 text-neutral-600' }}">{{ $customOrder->status_label }}</span>
                    </div>
                    <p class="mt-3 text-xs text-neutral-400">Aberta em {{ $customOrder->created_at->format('d/m/Y H:i') }}</p>
                    @if($customOrder->quoted_at)<p class="text-xs text-neutral-400">Orçada em {{ $customOrder->quoted_at->format('d/m/Y') }}</p>@endif
                    @if($customOrder->confirmed_at)<p class="text-xs text-neutral-400">Confirmada em {{ $customOrder->confirmed_at->format('d/m/Y') }}</p>@endif
                </div>

                <a href="{{ route('contact.show') }}" class="btn-outline w-full">Falar com a doceria</a>
            </div>
        </div>
    </div>
@endsection
