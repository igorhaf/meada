@extends('layouts.admin')

@section('title', 'Editar serviço')
@section('heading', 'Editar serviço')

@section('content')
<form method="POST" action="{{ route('admin.servicos.update', $service) }}" class="admin-form">
    @csrf
    @method('PUT')
    @include('admin.services._form')
    <div class="admin-form-actions">
        <a class="btn btn-secondary" href="{{ route('admin.servicos.index') }}">Cancelar</a>
        <button type="submit" class="btn btn-primary">Salvar alterações</button>
    </div>
</form>
@endsection
