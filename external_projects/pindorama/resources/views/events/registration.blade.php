@extends('layouts.app')

@section('title', 'Inscrição ' . $registration->reference)

@section('content')
@php($event = $registration->event)
@php($start = $event->starts_at->setTimezone($event->timezone))
<div class="container-site max-w-xl py-10">
    @if(session('status'))<div class="mb-4 rounded-xl bg-brand-50 px-4 py-3 text-sm font-medium text-brand-800">{{ session('status') }}</div>@endif
    @if(session('error'))<div class="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{{ session('error') }}</div>@endif

    <div class="card overflow-hidden">
        <div class="bg-brand-700 p-6 text-white">
            <p class="text-xs uppercase tracking-wide text-white/70">{{ $registration->reference }}</p>
            <h1 class="mt-1 text-xl font-extrabold">{{ $event->title }}</h1>
            <p class="mt-1 text-white/80">{{ $registration->status_label }}</p>
        </div>
        <dl class="divide-y divide-neutral-100 px-6">
            <div class="flex justify-between py-3 text-sm"><dt class="text-neutral-500">Quando</dt><dd class="font-medium">{{ $start->format('d/m/Y H:i') }}</dd></div>
            <div class="flex justify-between py-3 text-sm"><dt class="text-neutral-500">Participante</dt><dd class="font-medium">{{ $registration->participant_name }}</dd></div>
            <div class="flex justify-between py-3 text-sm"><dt class="text-neutral-500">Valor</dt><dd class="font-medium">{{ $registration->amount == 0 ? 'Gratuito' : money($registration->amount) }}</dd></div>
        </dl>
        @unless($registration->isPaid())
            <div class="border-t border-neutral-100 p-6">
                <form method="POST" action="{{ route('events.registration.pay', $registration) }}">@csrf
                    <button class="btn-brand w-full">Pagar inscrição</button>
                </form>
            </div>
        @endunless
        @if($pass=$registration->accessPasses()->first())<div class="border-t p-6"><a href="{{ URL::temporarySignedRoute('passes.show',now()->addYear(),['pass'=>$pass]) }}" class="btn-brand block text-center">Ver passaporte / QR</a></div>@endif
        @if(!in_array($registration->status,['cancelled','attended']))<div class="border-t p-6"><form method="POST" action="{{ route('events.registration.cancel',$registration) }}" onsubmit="return confirm('Cancelar esta inscrição? Pagamentos do Mercado Pago serão estornados quando possível.')">@csrf<button class="btn-outline w-full text-red-600">Cancelar inscrição</button></form></div>@endif
    </div>
</div>
@endsection
