@extends('layouts.dashboard')

@section('title', 'Práticas')

@section('content')
<div class="mx-auto max-w-2xl">
    <div class="mb-6 flex items-center justify-between">
        <h1 class="text-2xl font-extrabold text-neutral-900">Práticas</h1>
        <a href="{{ route('admin.practices.create') }}" class="btn-brand">Nova prática</a>
    </div>

    @if(session('error'))<div class="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{{ session('error') }}</div>@endif

    <div class="card divide-y divide-neutral-100">
        @forelse($roots as $root)
            <div class="p-4">
                <div class="flex items-center justify-between">
                    <span class="font-semibold text-neutral-900">{{ $root->icon }} {{ $root->name }} @unless($root->is_active)<span class="chip ml-1 bg-neutral-200 text-neutral-600">inativa</span>@endunless</span>
                    <a href="{{ route('admin.practices.edit', $root) }}" class="text-sm text-brand-700 hover:underline">editar</a>
                </div>
                @if($root->children->isNotEmpty())
                    <ul class="mt-2 space-y-1 pl-6">
                        @foreach($root->children as $child)
                            <li class="flex items-center justify-between text-sm">
                                <span class="text-neutral-600">{{ $child->icon }} {{ $child->name }}</span>
                                <a href="{{ route('admin.practices.edit', $child) }}" class="text-brand-700 hover:underline">editar</a>
                            </li>
                        @endforeach
                    </ul>
                @endif
            </div>
        @empty
            <p class="p-6 text-center text-sm text-neutral-400">Nenhuma prática cadastrada.</p>
        @endforelse
    </div>
</div>
@endsection
