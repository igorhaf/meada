@extends('layouts.dashboard')

@section('title', 'Meus serviços')

@section('content')
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
            <h1 class="text-2xl font-extrabold text-neutral-900">Meus serviços</h1>
            <p class="text-sm text-neutral-500">{{ $services->total() }} serviço(s) no seu catálogo</p>
        </div>
        <a href="{{ route('professional.services.create') }}" class="btn-brand">+ Novo serviço</a>
    </div>

    <form method="GET" class="mb-4">
        <input name="q" value="{{ request('q') }}" placeholder="Buscar nos meus serviços…"
            class="w-full max-w-sm rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
    </form>

    <div class="card overflow-hidden">
        @if($services->isEmpty())
            <div class="flex flex-col items-center justify-center py-16 text-center">
                <div class="text-5xl">🌿</div>
                <p class="mt-4 font-semibold text-neutral-700">Nenhum serviço ainda</p>
                <p class="mt-1 text-sm text-neutral-500">Cadastre seu primeiro atendimento e comece a receber agendamentos.</p>
                <a href="{{ route('professional.services.create') }}" class="btn-brand mt-6">+ Novo serviço</a>
            </div>
        @else
            <div class="overflow-x-auto">
                <table class="w-full text-sm">
                    <thead class="border-b border-neutral-200 bg-neutral-50 text-left text-xs uppercase tracking-wide text-neutral-500">
                        <tr>
                            <th class="px-4 py-3">Serviço</th>
                            <th class="px-4 py-3">Preço</th>
                            <th class="px-4 py-3">Agendamentos</th>
                            <th class="px-4 py-3">Status</th>
                            <th class="px-4 py-3 text-right">Ações</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-neutral-100">
                        @foreach($services as $service)
                            <tr class="hover:bg-neutral-50">
                                <td class="px-4 py-3">
                                    <div class="flex items-center gap-3">
                                        <img src="{{ $service->cover_url }}" alt="" class="h-12 w-12 shrink-0 rounded-lg object-cover">
                                        <div class="min-w-0">
                                            <a href="{{ $service->url }}" class="line-clamp-1 font-medium text-neutral-800 hover:text-brand-700">{{ $service->title }}</a>
                                            <p class="text-xs text-neutral-400">{{ $service->modality_label }} · {{ $service->duration_label }}</p>
                                        </div>
                                    </div>
                                </td>
                                <td class="px-4 py-3 font-semibold text-neutral-800">{{ money($service->price) }}</td>
                                <td class="px-4 py-3">{{ $service->bookings_count }}</td>
                                <td class="px-4 py-3">
                                    @if($service->is_active)
                                        <span class="chip bg-brand-50 text-brand-700">Ativo</span>
                                    @else
                                        <span class="chip bg-neutral-100 text-neutral-500">Pausado</span>
                                    @endif
                                </td>
                                <td class="px-4 py-3">
                                    <div class="flex items-center justify-end gap-1">
                                        <a href="{{ route('professional.services.edit', $service) }}" class="rounded-lg px-2.5 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-50">Editar</a>
                                        <form method="POST" action="{{ route('professional.services.toggle', $service) }}">
                                            @csrf
                                            <button class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-neutral-500 hover:bg-neutral-100">{{ $service->is_active ? 'Pausar' : 'Ativar' }}</button>
                                        </form>
                                        <form method="POST" action="{{ route('professional.services.destroy', $service) }}" onsubmit="return confirm('Remover este serviço?')">
                                            @csrf @method('DELETE')
                                            <button class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-red-500 hover:bg-red-50">Excluir</button>
                                        </form>
                                    </div>
                                </td>
                            </tr>
                        @endforeach
                    </tbody>
                </table>
            </div>
        @endif
    </div>

    <div class="mt-6">{{ $services->onEachSide(1)->links() }}</div>
@endsection
