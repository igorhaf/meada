@extends('layouts.admin')

@section('title', 'O que fazemos')
@section('heading', 'O que fazemos')

@section('header-actions')
    <a class="btn btn-primary" href="{{ route('admin.servicos.create') }}">Novo serviço</a>
@endsection

@php
    $props = [
        'items' => $services->map(fn ($service) => [
            'id' => $service->id,
            'title' => $service->title,
            'subtitle' => \Illuminate\Support\Str::limit($service->description, 90),
            'is_active' => $service->is_active,
        ])->values()->all(),
        'urls' => [
            'reorder' => route('admin.servicos.reorder'),
            'toggle' => route('admin.servicos.toggle', ['service' => '__ID__']),
            'edit' => route('admin.servicos.edit', ['service' => '__ID__']),
            'destroy' => route('admin.servicos.destroy', ['service' => '__ID__']),
        ],
        'labels' => [
            'empty' => 'Nenhum serviço cadastrado ainda.',
            'confirmDelete' => 'Excluir este serviço? Essa ação não pode ser desfeita.',
        ],
    ];
@endphp

@section('content')
    <div data-vue="sortable-list"
         data-props='@json($props, JSON_HEX_TAG | JSON_HEX_APOS | JSON_HEX_AMP | JSON_HEX_QUOT)'></div>
@endsection
