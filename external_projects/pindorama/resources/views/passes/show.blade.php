@extends('layouts.app')
@section('title', 'Passaporte '.$pass->public_code)
@section('content')
<div class="container-site max-w-xl py-10"><div class="card overflow-hidden text-center"><div class="bg-forest-900 p-6 text-white"><p class="text-xs font-bold uppercase tracking-[.25em] text-gold-300">Passaporte Pindorama</p><h1 class="mt-2 text-2xl font-extrabold">{{ $pass->holder_name }}</h1></div><div class="p-6">
@php($url = URL::temporarySignedRoute('passes.show', now()->addYear(), ['pass'=>$pass]))
<canvas data-qr="{{ $url }}" class="mx-auto h-56 w-56"></canvas><p class="mt-3 font-mono text-lg font-bold tracking-widest">{{ $pass->public_code }}</p>
<p class="mt-3 text-sm text-neutral-500">{{ $pass->passable instanceof \App\Models\Appointment ? $pass->passable->service_title : $pass->passable->event->title }}</p>
<span class="chip mt-4 {{ $pass->status==='valid'?'bg-brand-100 text-brand-800':($pass->status==='used'?'bg-blue-100 text-blue-800':'bg-red-100 text-red-700') }}">{{ ['valid'=>'Válido','used'=>'Utilizado','cancelled'=>'Cancelado','expired'=>'Expirado'][$pass->status] ?? $pass->status }}</span>
@if($canCheckIn && $pass->status==='valid')<form method="POST" action="{{ route('passes.check-in',$pass) }}" class="mt-5">@csrf<input name="location" placeholder="Local do check-in (opcional)" class="w-full rounded-xl border px-4 py-2.5 text-sm"><button class="btn-brand mt-3 w-full">Confirmar check-in</button></form>@endif
</div></div></div>
@endsection
