@extends('layouts.app')

@section('title', $term !== '' ? "Busca: {$term}" : 'Buscar serviços')

@section('content')
    @include('partials.listing')
@endsection
