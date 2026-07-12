@extends('layouts.admin')

@section('title', 'Editar prática')
@section('heading', 'Editar prática')

@section('content')
<form method="POST" action="{{ route('admin.praticas.update', $practice) }}" class="admin-form">
    @csrf
    @method('PUT')
    @include('admin.practices._form')
    <div class="admin-form-actions">
        <a class="btn btn-secondary" href="{{ route('admin.praticas.index') }}">Cancelar</a>
        <button type="submit" class="btn btn-primary">Salvar alterações</button>
    </div>
</form>
@endsection
