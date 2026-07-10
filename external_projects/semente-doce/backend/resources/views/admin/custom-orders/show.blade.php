@extends('layouts.dashboard')

@section('title', 'Encomenda ' . $customOrder->reference)

@section('content')
    <div class="mb-6">
        <a href="{{ route('admin.custom-orders.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar ao quadro</a>
        <div class="mt-1 flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-2xl font-extrabold text-neutral-900">{{ $customOrder->title }}</h1>
            <span class="chip bg-brand-100 text-brand-700">{{ $customOrder->status_label }}</span>
        </div>
        <p class="text-sm text-neutral-400">{{ $customOrder->reference }} · aberta em {{ $customOrder->created_at->format('d/m/Y H:i') }}</p>
    </div>

    <div class="grid gap-6 lg:grid-cols-3">
        {{-- Detalhes da encomenda --}}
        <div class="space-y-6 lg:col-span-2">
            <div class="card space-y-4 p-6">
                <h2 class="font-bold text-neutral-900">O que o cliente pediu</h2>
                <p class="whitespace-pre-line text-sm text-neutral-700">{{ $customOrder->description }}</p>

                <dl class="grid grid-cols-2 gap-4 border-t border-neutral-100 pt-4 text-sm sm:grid-cols-3">
                    <div><dt class="text-xs text-neutral-400">Data do evento</dt><dd class="font-semibold text-neutral-800">{{ $customOrder->event_date?->format('d/m/Y') }}</dd></div>
                    <div><dt class="text-xs text-neutral-400">Quantidade</dt><dd class="font-semibold text-neutral-800">{{ $customOrder->quantity ?? '—' }}</dd></div>
                    <div><dt class="text-xs text-neutral-400">Sabor</dt><dd class="font-semibold text-neutral-800">{{ $customOrder->flavor ?: '—' }}</dd></div>
                    <div><dt class="text-xs text-neutral-400">Entrega</dt><dd class="font-semibold text-neutral-800">{{ $customOrder->fulfillment_label }}</dd></div>
                    <div class="col-span-2"><dt class="text-xs text-neutral-400">Mensagem no item</dt><dd class="font-semibold text-neutral-800">{{ $customOrder->message_on_item ?: '—' }}</dd></div>
                </dl>

                @if($customOrder->fulfillment_type === 'delivery' && $customOrder->delivery_address)
                    <div class="rounded-xl bg-neutral-50 p-3 text-sm text-neutral-600">🛵 {{ $customOrder->delivery_address }}</div>
                @endif

                @if($customOrder->reference_photo_url)
                    <div>
                        <p class="mb-1 text-xs text-neutral-400">Foto de referência</p>
                        <a href="{{ $customOrder->reference_photo_url }}" target="_blank">
                            <img src="{{ $customOrder->reference_photo_url }}" alt="Referência" class="max-h-56 rounded-xl border border-neutral-200 object-cover">
                        </a>
                    </div>
                @endif

                @if($customOrder->product || $customOrder->kit)
                    <p class="text-xs text-neutral-400">
                        Inspirada em:
                        @if($customOrder->product)<a href="{{ $customOrder->product->url }}" class="text-brand-700 hover:underline">{{ $customOrder->product->title }}</a>@endif
                        @if($customOrder->kit)<a href="{{ $customOrder->kit->url }}" class="text-brand-700 hover:underline">{{ $customOrder->kit->name }}</a>@endif
                    </p>
                @endif
            </div>

            {{-- Orçamento --}}
            <div class="card space-y-4 p-6">
                <h2 class="font-bold text-neutral-900">💰 Orçamento</h2>
                @if($customOrder->isQuoted())
                    <div class="flex items-center gap-3 rounded-xl bg-brand-50 p-4">
                        <span class="text-2xl">🏷️</span>
                        <div>
                            <p class="text-lg font-extrabold text-brand-700">{{ money($customOrder->quoted_price) }}</p>
                            <p class="text-xs text-neutral-500">Orçado em {{ $customOrder->quoted_at?->format('d/m/Y H:i') }}</p>
                        </div>
                    </div>
                @endif

                <form method="POST" action="{{ route('admin.custom-orders.quote', $customOrder) }}" class="space-y-3">
                    @csrf @method('PUT')
                    <div>
                        <label class="field-label">Preço do orçamento (R$) *</label>
                        <input name="quoted_price" type="number" step="0.01" min="0" value="{{ old('quoted_price', $customOrder->quoted_price) }}" required class="field-input">
                    </div>
                    <div>
                        <label class="field-label">Observações da loja</label>
                        <textarea name="admin_notes" rows="3" placeholder="Detalhes do que está incluso, condições de pagamento…" class="field-input">{{ old('admin_notes', $customOrder->admin_notes) }}</textarea>
                    </div>
                    <button class="btn-brand">{{ $customOrder->isQuoted() ? 'Atualizar orçamento' : 'Enviar orçamento' }}</button>
                </form>
            </div>
        </div>

        {{-- Coluna lateral: cliente + status --}}
        <div class="space-y-4">
            <div class="card p-5 text-sm">
                <h2 class="mb-3 font-bold text-neutral-900">Cliente</h2>
                <p class="font-medium text-neutral-800">{{ $customOrder->customer_name }}</p>
                <p class="text-neutral-500"><a href="tel:{{ $customOrder->customer_phone }}" class="hover:text-brand-700">{{ $customOrder->customer_phone }}</a></p>
                @if($customOrder->customer_email)<p class="text-neutral-500"><a href="mailto:{{ $customOrder->customer_email }}" class="hover:text-brand-700">{{ $customOrder->customer_email }}</a></p>@endif
                @if($customOrder->user)<p class="mt-1 text-xs text-neutral-400">Conta: {{ $customOrder->user->email }}</p>@endif
            </div>

            <div class="card p-5">
                <h2 class="mb-3 font-bold text-neutral-900">Mover para</h2>
                <div class="flex flex-wrap gap-2">
                    @foreach(\App\Models\CustomOrder::STATUSES as $value => $label)
                        <form method="POST" action="{{ route('admin.custom-orders.status', $customOrder) }}">
                            @csrf
                            <input type="hidden" name="status" value="{{ $value }}">
                            <button
                                @disabled($customOrder->status === $value)
                                class="chip {{ $customOrder->status === $value ? 'bg-brand-600 text-white' : 'bg-neutral-100 text-neutral-600 hover:bg-brand-50 hover:text-brand-700' }}">
                                {{ $label }}
                            </button>
                        </form>
                    @endforeach
                </div>
                <p class="mt-3 text-xs text-neutral-400">Ao confirmar, a data de confirmação é registrada.</p>
            </div>
        </div>
    </div>
@endsection
