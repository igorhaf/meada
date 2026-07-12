@extends('layouts.admin')

@section('title', 'Novo serviço')
@section('heading', 'Novo serviço')

@section('content')
<form method="POST" action="{{ route('admin.servicos.store') }}" class="admin-form">
    @csrf
    @include('admin.services._form', ['service' => null])
    <div class="admin-form-actions">
        <a class="btn btn-secondary" href="{{ route('admin.servicos.index') }}">Cancelar</a>
        <button type="submit" class="btn btn-primary">Criar serviço</button>
    </div>
</form>
@endsection
