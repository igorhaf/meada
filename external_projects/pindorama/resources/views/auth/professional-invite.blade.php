@extends('layouts.auth')
@section('title','Ativar acesso profissional')
@section('content')
<div class="mx-auto max-w-md"><h1 class="text-2xl font-extrabold">Bem-vindo(a), {{ $invite->professional->name }}</h1><p class="mt-2 text-sm text-neutral-500">Defina uma senha para acessar seu painel profissional.</p>
@if($errors->any())<div class="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">{{ $errors->first() }}</div>@endif
<form method="POST" action="{{ route('professional-invites.accept',$token) }}" class="mt-6 space-y-4">@csrf<div><label class="text-sm font-medium">Senha</label><input type="password" name="password" required class="mt-1 w-full rounded-xl border px-4 py-3"></div><div><label class="text-sm font-medium">Confirmar senha</label><input type="password" name="password_confirmation" required class="mt-1 w-full rounded-xl border px-4 py-3"></div><label class="flex items-start gap-3 text-sm text-neutral-600"><input type="checkbox" name="accept_terms" value="1" required class="mt-1"> <span>Li e aceito os <a class="font-semibold text-brand-primary underline" href="{{ route('pages.terms') }}" target="_blank">Termos de Uso</a> e a <a class="font-semibold text-brand-primary underline" href="{{ route('pages.privacy') }}" target="_blank">Política de Privacidade</a>.</span></label><button class="btn-brand w-full">Ativar meu acesso</button></form></div>
@endsection
