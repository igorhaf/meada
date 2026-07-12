@extends('layouts.admin')

@section('title', 'Editar curso')
@section('heading', 'Editar curso')

@section('content')
<form method="POST" action="{{ route('admin.cursos.update', $course) }}" class="admin-form">
    @csrf
    @method('PUT')
    @include('admin.courses._form')
    <div class="admin-form-actions">
        <a class="btn btn-secondary" href="{{ route('admin.cursos.index') }}">Cancelar</a>
        <button type="submit" class="btn btn-primary">Salvar alterações</button>
    </div>
</form>
@endsection
