@extends('layouts.dashboard')
@section('title','Check-in')
@section('content')
<div class="mx-auto max-w-md"><h1 class="text-2xl font-extrabold">Check-in por passaporte</h1><p class="mt-1 text-sm text-neutral-500">Leia o QR com a câmera do aparelho ou digite o código exibido ao participante.</p>@if(session('error'))<div class="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">{{ session('error') }}</div>@endif<form method="POST" action="{{ auth()->user()->isRoot()?route('admin.passes.lookup'):route('professional.passes.lookup') }}" class="card mt-5 p-6">@csrf<label class="text-sm font-medium">Código do passaporte<input name="code" autofocus required class="mt-2 w-full rounded-xl border px-4 py-3 text-center font-mono text-xl uppercase tracking-widest"></label><button class="btn-brand mt-4 w-full">Localizar e validar</button></form></div>
@endsection
