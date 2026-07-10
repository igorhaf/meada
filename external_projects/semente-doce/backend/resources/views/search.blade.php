@extends('layouts.app')

@section('title', $term !== '' ? "Busca: {$term}" : 'Buscar no cardápio')

@section('content')
    @include('partials.listing')
@endsection
