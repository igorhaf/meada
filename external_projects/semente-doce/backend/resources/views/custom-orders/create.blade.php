@extends('layouts.app')

@section('title', 'Fazer uma encomenda')
@section('description', 'Encomende bolos, doces e salgados sob medida na Semente Doce. Conte o que você imagina, a data e a quantidade — a gente orça e confirma com você.')

@section('content')
    @php($u = auth()->user())
    @php($prefillTitle = old('title', $product?->title ?? $kit?->name))
    @php($prefillFlavor = old('flavor', $product?->flavor))
    @php($ff = old('fulfillment_type', 'delivery'))

    <div class="container-doce py-8">
        <nav class="mb-4 text-sm text-neutral-500">
            <a href="{{ route('home') }}" class="hover:text-brand-700">Início</a>
            <span>/</span><span class="font-medium text-neutral-700">Encomenda</span>
        </nav>

        <div class="grid gap-8 lg:grid-cols-[1fr_360px]">
            {{-- Formulário --}}
            <div>
                <h1 class="text-3xl font-extrabold text-neutral-900">🎂 Vamos montar sua encomenda</h1>
                <p class="mt-2 max-w-xl text-neutral-600">
                    Conte pra gente o que você imagina — sabor, tamanho, tema e a data. É só um pedido de orçamento:
                    <strong>a doceria confere tudo, calcula o preço e confirma com você</strong> antes de começar. Sem compromisso. 💚
                </p>

                @if($product || $kit)
                    <div class="mt-4 flex items-center gap-3 rounded-2xl border border-brand-100 bg-brand-50 p-4">
                        <span class="text-2xl">{{ $kit ? '🎁' : '🧁' }}</span>
                        <div class="text-sm">
                            <p class="text-neutral-500">Encomenda baseada em</p>
                            <p class="font-semibold text-brand-800">{{ $product?->title ?? $kit?->name }}</p>
                        </div>
                    </div>
                @endif

                @if ($errors->any())
                    <div class="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
                        Confira os campos destacados abaixo.
                    </div>
                @endif

                <form method="POST" action="{{ route('custom-orders.store') }}" class="mt-6 space-y-5">
                    @csrf
                    @if($product)<input type="hidden" name="product" value="{{ $product->slug }}">@endif
                    @if($kit)<input type="hidden" name="kit" value="{{ $kit->slug }}">@endif

                    {{-- O que você quer --}}
                    <div class="card p-5">
                        <h2 class="mb-4 text-sm font-bold uppercase tracking-wide text-neutral-500">O que você quer encomendar</h2>

                        <div class="space-y-4">
                            <div>
                                <label class="field-label" for="title">Título da encomenda *</label>
                                <input id="title" name="title" value="{{ $prefillTitle }}" required placeholder="Ex.: Bolo de aniversário 2 andares" class="field-input">
                                @error('title')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                            </div>

                            <div>
                                <label class="field-label" for="description">Descreva sua ideia</label>
                                <textarea id="description" name="description" rows="4" placeholder="Tema, cores, recheios, cobertura, referências… quanto mais detalhes, melhor o orçamento!" class="field-input">{{ old('description') }}</textarea>
                                @error('description')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                            </div>

                            <div class="grid gap-4 sm:grid-cols-2">
                                <div>
                                    <label class="field-label" for="flavor">Sabor</label>
                                    <input id="flavor" name="flavor" value="{{ $prefillFlavor }}" placeholder="Ex.: chocolate com morango" class="field-input">
                                    @error('flavor')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                                </div>
                                <div>
                                    <label class="field-label" for="quantity">Quantidade / porções</label>
                                    <input id="quantity" name="quantity" type="number" min="1" value="{{ old('quantity', 1) }}" class="field-input">
                                    @error('quantity')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                                </div>
                            </div>

                            <div>
                                <label class="field-label" for="message_on_item">Mensagem no doce (opcional)</label>
                                <input id="message_on_item" name="message_on_item" value="{{ old('message_on_item') }}" placeholder="Ex.: Parabéns, Helena! 🎉" class="field-input">
                                @error('message_on_item')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                            </div>

                            <div>
                                <label class="field-label" for="reference_photo_url">Foto de referência (link, opcional)</label>
                                <input id="reference_photo_url" name="reference_photo_url" type="url" value="{{ old('reference_photo_url') }}" placeholder="https://…" class="field-input">
                                <p class="mt-1 text-xs text-neutral-400">Cole o link de uma imagem que te inspira (Pinterest, Instagram, Drive…).</p>
                                @error('reference_photo_url')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                            </div>
                        </div>
                    </div>

                    {{-- Data e entrega --}}
                    <div class="card p-5">
                        <h2 class="mb-4 text-sm font-bold uppercase tracking-wide text-neutral-500">Quando e como receber</h2>

                        <div class="space-y-4">
                            <div>
                                <label class="field-label" for="event_date">Data do evento / entrega</label>
                                <input id="event_date" name="event_date" type="date" value="{{ old('event_date') }}" min="{{ now()->toDateString() }}" class="field-input">
                                @error('event_date')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                            </div>

                            <div>
                                <span class="field-label">Como você quer receber?</span>
                                <div class="grid grid-cols-2 gap-3" data-fulfillment>
                                    <label class="flex cursor-pointer items-center gap-2 rounded-xl border p-3 text-sm font-semibold transition {{ $ff === 'delivery' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-neutral-200 text-neutral-600' }}">
                                        <input type="radio" name="fulfillment_type" value="delivery" @checked($ff === 'delivery') class="text-brand-600 focus:ring-brand-500">
                                        🛵 Entrega
                                    </label>
                                    <label class="flex cursor-pointer items-center gap-2 rounded-xl border p-3 text-sm font-semibold transition {{ $ff === 'pickup' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-neutral-200 text-neutral-600' }}">
                                        <input type="radio" name="fulfillment_type" value="pickup" @checked($ff === 'pickup') class="text-brand-600 focus:ring-brand-500">
                                        🏠 Retirar na loja
                                    </label>
                                </div>
                                @error('fulfillment_type')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                            </div>

                            <div data-address {{ $ff === 'pickup' ? 'hidden' : '' }}>
                                <label class="field-label" for="delivery_address">Endereço de entrega</label>
                                <input id="delivery_address" name="delivery_address" value="{{ old('delivery_address') }}" placeholder="Rua, número, bairro, complemento" class="field-input">
                                @error('delivery_address')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                            </div>
                        </div>
                    </div>

                    {{-- Seus dados --}}
                    <div class="card p-5">
                        <h2 class="mb-4 text-sm font-bold uppercase tracking-wide text-neutral-500">Seus contatos</h2>

                        <div class="space-y-4">
                            <div class="grid gap-4 sm:grid-cols-2">
                                <div>
                                    <label class="field-label" for="customer_name">Nome *</label>
                                    <input id="customer_name" name="customer_name" value="{{ old('customer_name', $u->name ?? '') }}" required class="field-input">
                                    @error('customer_name')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                                </div>
                                <div>
                                    <label class="field-label" for="customer_phone">WhatsApp *</label>
                                    <input id="customer_phone" name="customer_phone" value="{{ old('customer_phone', $u->phone ?? '') }}" required placeholder="(00) 00000-0000" class="field-input">
                                    @error('customer_phone')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                                </div>
                            </div>
                            <div>
                                <label class="field-label" for="customer_email">E-mail (opcional)</label>
                                <input id="customer_email" name="customer_email" type="email" value="{{ old('customer_email', $u->email ?? '') }}" class="field-input">
                                @error('customer_email')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
                            </div>
                        </div>
                    </div>

                    <button type="submit" class="btn-brand w-full text-base">Enviar pedido de orçamento 🎂</button>
                    <p class="text-center text-xs text-neutral-400">Sem pagamento agora. A gente responde com o orçamento pelo WhatsApp ou e-mail.</p>
                </form>
            </div>

            {{-- Como funciona --}}
            <aside>
                <div class="sticky top-24 space-y-4">
                    <div class="card p-6">
                        <h2 class="text-lg font-bold text-neutral-900">Como funciona 🍬</h2>
                        <ol class="mt-4 space-y-4 text-sm text-neutral-600">
                            <li class="flex gap-3"><span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700">1</span><span>Você conta sua ideia neste formulário.</span></li>
                            <li class="flex gap-3"><span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700">2</span><span>A doceria analisa e envia o <strong>orçamento</strong>.</span></li>
                            <li class="flex gap-3"><span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700">3</span><span>Você aprova e a gente <strong>confirma a produção</strong>.</span></li>
                            <li class="flex gap-3"><span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700">4</span><span>Retirada na loja ou entrega quentinha no dia. 🛵</span></li>
                        </ol>
                    </div>
                    @auth
                        <a href="{{ route('custom-orders.index') }}" class="btn-outline w-full">Ver minhas encomendas</a>
                    @endauth
                </div>
            </aside>
        </div>
    </div>

    <script>
        // Mostra o endereço só quando a entrega está selecionada + destaque do cartão do radio.
        (function () {
            const group = document.querySelector('[data-fulfillment]');
            const address = document.querySelector('[data-address]');
            if (!group) return;
            group.addEventListener('change', () => {
                const value = group.querySelector('input[name="fulfillment_type"]:checked')?.value;
                if (address) address.hidden = value === 'pickup';
                group.querySelectorAll('label').forEach((label) => {
                    const on = label.querySelector('input')?.checked;
                    label.classList.toggle('border-brand-500', on);
                    label.classList.toggle('bg-brand-50', on);
                    label.classList.toggle('text-brand-700', on);
                    label.classList.toggle('border-neutral-200', !on);
                    label.classList.toggle('text-neutral-600', !on);
                });
            });
        })();
    </script>
@endsection
