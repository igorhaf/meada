@extends('layouts.dashboard')

@section('title', 'Minhas cobranças')

@section('content')
<div class="mx-auto max-w-2xl">
    <h1 class="mb-6 text-2xl font-extrabold text-neutral-900">Minhas cobranças</h1>

    @if($charges->isEmpty())
        <div class="card p-8 text-center text-neutral-500">Você não tem cobranças da plataforma.</div>
    @else
        <div class="space-y-2">
            @foreach($charges as $c)
                <div class="card flex items-center justify-between p-4">
                    <div>
                        <p class="font-semibold text-neutral-900">{{ $c->type_label }} · {{ money($c->amount) }}</p>
                        <p class="text-sm text-neutral-500">{{ $c->description }} @if($c->due_date)· vence {{ $c->due_date->format('d/m/Y') }}@endif</p>
                    </div>
                    <div class="flex items-center gap-3">
                        <span class="chip {{ $c->isPaid() ? 'bg-brand-100 text-brand-800' : 'bg-amber-100 text-amber-800' }}">{{ $c->status_label }}</span>
                        @unless($c->isPaid())
                            <form method="POST" action="{{ route('professional.charges.pay', $c) }}">@csrf<button class="btn-brand !px-4 !py-1.5 text-sm">Pagar</button></form>
                        @endunless
                    </div>
                </div>
            @endforeach
        </div>
    @endif
</div>
@endsection
