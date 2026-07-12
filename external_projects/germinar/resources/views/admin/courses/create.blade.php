@extends('layouts.admin')

@section('title', 'Novo curso')
@section('heading', 'Novo curso')

@section('content')
<form method="POST" action="{{ route('admin.cursos.store') }}" class="admin-form">
    @csrf
    @include('admin.courses._form', ['course' => null])
    <div class="admin-form-actions">
        <a class="btn btn-secondary" href="{{ route('admin.cursos.index') }}">Cancelar</a>
        <button type="submit" class="btn btn-primary">Criar curso</button>
    </div>
</form>
@endsection
