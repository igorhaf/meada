@extends('layouts.dashboard')

@section('title', 'Destaques')

@section('content')
<div class="mx-auto max-w-4xl space-y-8">
    <h1 class="text-2xl font-extrabold text-neutral-900">Serviços em destaque</h1>

    <div>
        <h2 class="mb-3 font-bold text-neutral-800">Em destaque ({{ $featured->count() }})</h2>
        @if($featured->isEmpty())
            <p class="card p-6 text-center text-sm text-neutral-400">Nenhum serviço em destaque.</p>
        @else
            <div class="grid gap-2">
                @foreach($featured as $s)
                    <div class="card flex items-center justify-between p-3">
                        <span class="text-sm font-medium">{{ $s->title }} <span class="text-neutral-400">· {{ $s->professional_name }}</span></span>
                        <form method="POST" action="{{ route('admin.featured.toggle', $s) }}">@csrf<button class="text-xs text-red-500 hover:underline">remover</button></form>
                    </div>
                @endforeach
            </div>
        @endif
    </div>

    <div>
        <h2 class="mb-3 font-bold text-neutral-800">Todos os serviços</h2>
        <div class="grid gap-2">
            @foreach($services as $s)
                <div class="card flex items-center justify-between p-3">
                    <span class="text-sm">{{ $s->title }} <span class="text-neutral-400">· {{ $s->professional_name }} · {{ $s->bookings_count }} agend.</span></span>
                    <form method="POST" action="{{ route('admin.featured.toggle', $s) }}">@csrf
                        <button class="text-xs font-semibold {{ $s->is_featured ? 'text-neutral-400' : 'text-brand-700 hover:underline' }}">{{ $s->is_featured ? 'em destaque' : 'destacar' }}</button>
                    </form>
                </div>
            @endforeach
        </div>
        <div class="mt-4">{{ $services->links() }}</div>
    </div>
</div>
@endsection
