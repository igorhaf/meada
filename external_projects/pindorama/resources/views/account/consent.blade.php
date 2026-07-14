@extends('layouts.app')
@section('title','Termos e privacidade')
@section('content')
<div class="container-site max-w-lg py-10"><div class="card p-6"><h1 class="text-2xl font-extrabold">Termos e privacidade</h1><p class="mt-2 text-sm text-neutral-500">Antes de continuar, confirme que leu os termos e a política de privacidade do Pindorama.</p><form method="POST" action="{{ route('account.consent.store') }}" class="mt-5">@csrf<label class="flex items-start gap-2 text-sm"><input type="checkbox" name="accept_terms" value="1" required class="mt-1"><span>Aceito os <a href="{{ route('pages.terms') }}" class="text-brand-700 underline">termos</a> e a <a href="{{ route('pages.privacy') }}" class="text-brand-700 underline">política de privacidade</a>.</span></label><button class="btn-brand mt-4 w-full">Continuar</button></form></div></div>
@endsection
