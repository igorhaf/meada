@extends('layouts.dashboard')

@section('title', 'Novo local')

@section('content')
<div class="mx-auto max-w-2xl">
    <h1 class="mb-6 text-2xl font-extrabold text-neutral-900">Novo local de atendimento</h1>
    <form method="POST" action="{{ route('professional.locations.store') }}">
        @csrf
        @include('professional.locations._form')
    </form>
</div>
@endsection
