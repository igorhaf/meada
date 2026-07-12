@extends('layouts.admin')

@section('title', 'Cursos e treinamentos')
@section('heading', 'Cursos e treinamentos')

@section('header-actions')
    <a class="btn btn-primary" href="{{ route('admin.cursos.create') }}">Novo curso</a>
@endsection

@php
    $props = [
        'items' => $courses->map(fn ($course) => [
            'id' => $course->id,
            'title' => $course->title,
            'subtitle' => $course->tag_label.' — '.\Illuminate\Support\Str::limit($course->description, 70),
            'is_active' => $course->is_active,
        ])->values()->all(),
        'urls' => [
            'reorder' => route('admin.cursos.reorder'),
            'toggle' => route('admin.cursos.toggle', ['course' => '__ID__']),
            'edit' => route('admin.cursos.edit', ['course' => '__ID__']),
            'destroy' => route('admin.cursos.destroy', ['course' => '__ID__']),
        ],
        'labels' => [
            'empty' => 'Nenhum curso cadastrado ainda.',
            'confirmDelete' => 'Excluir este curso? Essa ação não pode ser desfeita.',
        ],
    ];
@endphp

@section('content')
    <div data-vue="sortable-list"
         data-props='@json($props, JSON_HEX_TAG | JSON_HEX_APOS | JSON_HEX_AMP | JSON_HEX_QUOT)'></div>
@endsection
