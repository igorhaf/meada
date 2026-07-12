@extends('layouts.admin')

@section('title', 'Nova prática')
@section('heading', 'Nova prática')

@section('content')
<form method="POST" action="{{ route('admin.praticas.store') }}" class="admin-form">
    @csrf
    @include('admin.practices._form', ['practice' => null])
    <div class="admin-form-actions">
        <a class="btn btn-secondary" href="{{ route('admin.praticas.index') }}">Cancelar</a>
        <button type="submit" class="btn btn-primary">Criar prática</button>
    </div>
</form>
@endsection
