@extends('layouts.app')

@section('title', 'Encomenda enviada')

@section('content')
    <div class="container-doce py-12">
        <div class="mx-auto max-w-xl text-center">
            <div class="text-6xl">🎉</div>
            <h1 class="mt-4 text-3xl font-extrabold text-neutral-900">Encomenda enviada!</h1>
            <p class="mt-2 text-neutral-600">
                Recebemos seu pedido de orçamento com todo o carinho. Guarde o código abaixo para acompanhar.
            </p>

            <div class="mt-6 inline-flex items-center gap-2 rounded-full border border-brand-100 bg-brand-50 px-6 py-3">
                <span class="text-sm text-neutral-500">Código:</span>
                <span class="text-lg font-extrabold tracking-wide text-brand-700">{{ $customOrder->reference }}</span>
            </div>

            {{-- Próximos passos --}}
            <div class="card mt-8 p-6 text-left">
                <h2 class="text-sm font-bold uppercase tracking-wide text-neutral-500">Próximos passos</h2>
                <ol class="mt-4 space-y-4 text-sm text-neutral-600">
                    <li class="flex gap-3"><span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700">1</span><span>Nossa equipe analisa os detalhes da sua encomenda.</span></li>
                    <li class="flex gap-3"><span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700">2</span><span>Enviamos o <strong>orçamento</strong> pelo WhatsApp ou e-mail que você informou.</span></li>
                    <li class="flex gap-3"><span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700">3</span><span>Com seu ok, confirmamos a produção e combinamos a entrega. 🛵</span></li>
                </ol>
            </div>

            <div class="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
                @auth
                    <a href="{{ route('custom-orders.index') }}" class="btn-brand">Acompanhar minhas encomendas</a>
                @else
                    <a href="{{ route('register') }}" class="btn-brand">Criar conta para acompanhar</a>
                @endauth
                <a href="{{ route('home') }}" class="btn-outline">Voltar ao cardápio</a>
            </div>

            <p class="mt-6 text-sm text-neutral-400">Ficou com alguma dúvida? <a href="{{ route('contact.show') }}" class="font-semibold text-brand-700 hover:underline">Fale com a gente</a>.</p>
        </div>
    </div>
@endsection
