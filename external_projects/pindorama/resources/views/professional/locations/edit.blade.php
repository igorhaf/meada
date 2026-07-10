@extends('layouts.dashboard')

@section('title', 'Editar local')

@section('content')
<div class="mx-auto max-w-2xl">
    <h1 class="mb-6 text-2xl font-extrabold text-neutral-900">Editar local</h1>
    <form method="POST" action="{{ route('professional.locations.update', $location) }}">
        @csrf
        @method('PUT')
        @include('professional.locations._form')
    </form>
</div>
@endsection
