@extends('layouts.app')

@section('title', 'Contato')

@section('content')
    @php($settings = \App\Models\SiteSetting::current())
    <div class="container-doce py-10">
        <nav class="mb-4 text-sm text-neutral-500">
            <a href="{{ route('home') }}" class="hover:text-brand-700">Início</a>
            <span>/</span><span class="font-medium text-neutral-700">Contato</span>
        </nav>

        <div class="mx-auto grid max-w-4xl gap-8 lg:grid-cols-2">
            <div>
                <h1 class="text-3xl font-extrabold text-neutral-900">Fale com a {{ $settings->site_name }} 💬</h1>
                <p class="mt-2 text-neutral-600">Dúvida sobre um pedido, uma encomenda especial ou só quer dar um oi? A gente adora conversar.</p>

                <div class="mt-6 space-y-4 text-sm">
                    @if($settings->contact_email)
                        <div class="flex items-center gap-3">
                            <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-100 text-lg">✉️</span>
                            <div>
                                <p class="text-neutral-500">E-mail</p>
                                <a href="mailto:{{ $settings->contact_email }}" class="font-semibold text-brand-700 hover:underline">{{ $settings->contact_email }}</a>
                            </div>
                        </div>
                    @endif
                    @if($settings->contact_phone)
                        <div class="flex items-center gap-3">
                            <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-100 text-lg">📞</span>
                            <div><p class="text-neutral-500">Telefone</p><p class="font-semibold text-neutral-800">{{ $settings->contact_phone }}</p></div>
                        </div>
                    @endif
                    @if($settings->whatsapp)
                        <div class="flex items-center gap-3">
                            <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-pistache-100 text-lg">🟢</span>
                            <div>
                                <p class="text-neutral-500">WhatsApp</p>
                                <a href="https://wa.me/{{ preg_replace('/\D/', '', $settings->whatsapp) }}" target="_blank" rel="noopener" class="font-semibold text-pistache-600 hover:underline">{{ $settings->whatsapp }}</a>
                            </div>
                        </div>
                    @endif
                    @if($settings->address)
                        <div class="flex items-center gap-3">
                            <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-100 text-lg">📍</span>
                            <div><p class="text-neutral-500">Onde estamos</p><p class="font-semibold text-neutral-800">{{ $settings->address }}</p></div>
                        </div>
                    @endif
                    @if($settings->opening_hours)
                        <div class="flex items-center gap-3">
                            <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-caramel-100 text-lg">🕑</span>
                            <div><p class="text-neutral-500">Horário</p><p class="whitespace-pre-line font-semibold text-neutral-800">{{ $settings->opening_hours }}</p></div>
                        </div>
                    @endif
                </div>

                <div class="mt-8 rounded-2xl bg-caramel-100 p-5 text-sm text-caramel-700">
                    🎂 Procurando um bolo ou doce sob medida? <a href="{{ route('custom-orders.create') }}" class="font-semibold underline">Faça uma encomenda</a> — respondemos com o orçamento.
                </div>
            </div>

            <div class="card p-6">
                @if(session('status'))
                    <div class="mb-4 rounded-xl bg-brand-100 px-4 py-3 text-sm font-medium text-brand-800">{{ session('status') }}</div>
                @endif
                @if ($errors->any())
                    <div class="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{{ $errors->first() }}</div>
                @endif

                <form method="POST" action="{{ route('contact.store') }}" class="space-y-4">
                    @csrf
                    <div class="grid gap-4 sm:grid-cols-2">
                        <div>
                            <label class="field-label" for="name">Nome</label>
                            <input id="name" name="name" value="{{ old('name') }}" required class="field-input">
                        </div>
                        <div>
                            <label class="field-label" for="email">E-mail</label>
                            <input id="email" name="email" type="email" value="{{ old('email') }}" required class="field-input">
                        </div>
                    </div>
                    <div>
                        <label class="field-label" for="subject">Assunto</label>
                        <input id="subject" name="subject" value="{{ old('subject') }}" class="field-input">
                    </div>
                    <div>
                        <label class="field-label" for="message">Mensagem</label>
                        <textarea id="message" name="message" rows="5" required class="field-input">{{ old('message') }}</textarea>
                    </div>
                    <button class="btn-brand w-full">Enviar mensagem</button>
                </form>
            </div>
        </div>
    </div>
@endsection
