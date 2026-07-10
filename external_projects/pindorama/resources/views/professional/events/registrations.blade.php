@extends('layouts.dashboard')

@section('title', 'Inscritos')

@section('content')
<div class="mx-auto max-w-3xl">
    <a href="{{ route('professional.events.index') }}" class="text-sm text-neutral-500 hover:underline">← Meus eventos</a>
    <h1 class="mb-1 mt-2 text-2xl font-extrabold text-neutral-900">{{ $event->title }}</h1>
    <p class="mb-6 text-sm text-neutral-500">{{ $registrations->where('status','!=','cancelled')->count() }} inscritos{{ $event->capacity > 0 ? ' de '.$event->capacity.' vagas' : '' }}</p>

    <div class="card overflow-x-auto">
        <table class="w-full text-sm">
            <thead><tr class="border-b border-neutral-200 text-left text-xs uppercase tracking-wide text-neutral-400"><th class="p-3">Participante</th><th>Contato</th><th>Valor</th><th>Status</th><th>Pagamento</th></tr></thead>
            <tbody>
            @forelse($registrations as $r)
                <tr class="border-b border-neutral-100">
                    <td class="p-3 font-medium">{{ $r->participant_name }}</td>
                    <td class="text-neutral-500">{{ $r->participant_phone ?: $r->participant_email ?: '—' }}</td>
                    <td>{{ $r->amount == 0 ? 'Gratuito' : money($r->amount) }}</td>
                    <td>{{ $r->status_label }}</td>
                    <td><span class="chip {{ $r->isPaid() ? 'bg-brand-100 text-brand-800' : 'bg-amber-100 text-amber-800' }}">{{ $r->isPaid() ? 'Pago' : 'Pendente' }}</span></td>
                </tr>
            @empty
                <tr><td colspan="5" class="p-4 text-center text-neutral-400">Nenhum inscrito ainda.</td></tr>
            @endforelse
            </tbody>
        </table>
    </div>
</div>
@endsection
