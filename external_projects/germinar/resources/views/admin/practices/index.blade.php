@extends('layouts.admin')

@section('title', 'Práticas integrativas')
@section('heading', 'Práticas integrativas')

@section('header-actions')
    <a class="btn btn-primary" href="{{ route('admin.praticas.create') }}">Nova prática</a>
@endsection

@php
    $props = [
        'items' => $practices->map(fn ($practice) => [
            'id' => $practice->id,
            'title' => $practice->title,
            'subtitle' => \Illuminate\Support\Str::limit($practice->description, 90),
            'is_active' => $practice->is_active,
        ])->values()->all(),
        'urls' => [
            'reorder' => route('admin.praticas.reorder'),
            'toggle' => route('admin.praticas.toggle', ['practice' => '__ID__']),
            'edit' => route('admin.praticas.edit', ['practice' => '__ID__']),
            'destroy' => route('admin.praticas.destroy', ['practice' => '__ID__']),
        ],
        'labels' => [
            'empty' => 'Nenhuma prática cadastrada ainda.',
            'confirmDelete' => 'Excluir esta prática? Essa ação não pode ser desfeita.',
        ],
    ];
@endphp

@section('content')
    <div data-vue="sortable-list"
         data-props='@json($props, JSON_HEX_TAG | JSON_HEX_APOS | JSON_HEX_AMP | JSON_HEX_QUOT)'></div>
@endsection
