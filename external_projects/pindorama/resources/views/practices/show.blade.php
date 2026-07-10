@extends('layouts.app')

@section('title', $category->name)
@section('description', "Agende sessões de {$category->name} com terapeutas no Pindorama: práticas integrativas de saúde com horário online e atendimento presencial.")

@section('content')
    @include('partials.listing')
@endsection
