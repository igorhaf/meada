@extends('layouts.app')

@section('title', $category->name)
@section('description', "Veja {$category->name} da Semente Doce: doces e salgados artesanais, fresquinhos, com retirada na loja ou entrega no seu bairro.")

@section('content')
    @include('partials.listing')
@endsection
