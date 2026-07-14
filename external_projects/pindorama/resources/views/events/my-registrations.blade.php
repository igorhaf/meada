@extends('layouts.app')
@section('title', 'Minhas inscrições')
@section('content')
<div class="container-site max-w-4xl py-10"><h1 class="text-2xl font-extrabold">Minhas inscrições</h1>
<div class="mt-6 space-y-3">@forelse($registrations as $registration)<a href="{{ route('events.registration',$registration) }}" class="card flex items-center justify-between gap-4 p-5 hover:border-brand-300"><div><p class="font-bold">{{ $registration->event->title }}</p><p class="text-sm text-neutral-500">{{ $registration->event->starts_at->setTimezone($registration->event->timezone)->format('d/m/Y H:i') }} · {{ $registration->status_label }}</p></div><span class="chip {{ $registration->isPaid()?'bg-brand-100 text-brand-800':'bg-amber-100 text-amber-800' }}">{{ $registration->isPaid()?'Pago':'Pagamento pendente' }}</span></a>@empty<p class="card p-6 text-center text-neutral-500">Você ainda não possui inscrições.</p>@endforelse</div><div class="mt-6">{{ $registrations->links() }}</div></div>
@endsection
