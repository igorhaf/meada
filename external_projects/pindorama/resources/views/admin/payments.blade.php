@extends('layouts.dashboard')

@section('title', 'Pagamentos')

@section('content')
<div class="mx-auto max-w-4xl space-y-6">
    <h1 class="text-2xl font-extrabold text-neutral-900">Pagamentos</h1>

    {{-- Config MP --}}
    <div class="card p-5">
        <div class="flex flex-wrap items-center gap-3 text-sm">
            <span class="chip {{ $config['enabled'] ? 'bg-brand-100 text-brand-800' : 'bg-neutral-200 text-neutral-600' }}">
                {{ $config['enabled'] ? 'Mercado Pago ativo' : 'Mercado Pago desligado (modo simulado)' }}
            </span>
            @if($config['enabled'])<span class="text-neutral-500">Ambiente: {{ $config['environment'] }}</span>@endif
        </div>
    </div>

    {{-- Totais --}}
    <div class="grid grid-cols-2 gap-4 sm:grid-cols-5">
        @foreach([
            ['Aprovados', $totals['approved']],
            ['Pendentes', $totals['pending']],
            ['Recusados', $totals['rejected']],
            ['Receita', money($totals['revenue'])],
            ['Comissão', money($totals['commission'])],
        ] as [$label, $value])
            <div class="card p-4"><p class="text-xs uppercase tracking-wide text-neutral-400">{{ $label }}</p><p class="mt-1 text-lg font-extrabold text-neutral-900">{{ $value }}</p></div>
        @endforeach
    </div>

    {{-- Lista --}}
    <div class="card overflow-x-auto">
        <table class="w-full text-sm">
            <thead><tr class="border-b border-neutral-200 text-left text-xs uppercase tracking-wide text-neutral-400">
                <th class="p-3">Ref</th><th>Serviço</th><th>Terapeuta</th><th>Total</th><th>Comissão</th><th>Pagamento</th>
            </tr></thead>
            <tbody>
            @forelse($appointments as $a)
                <tr class="border-b border-neutral-100">
                    <td class="p-3 font-mono text-xs">{{ $a->reference }}</td>
                    <td>{{ $a->service_title }}</td>
                    <td class="text-neutral-500">{{ $a->professional?->display_name }}</td>
                    <td>{{ money($a->total) }}</td>
                    <td class="text-neutral-500">{{ $a->commission_amount !== null ? money($a->commission_amount) : '—' }}</td>
                    <td><span class="chip {{ $a->isPaid() ? 'bg-brand-100 text-brand-800' : 'bg-neutral-100 text-neutral-600' }}">{{ $a->payment_status_label }}</span></td>
                </tr>
            @empty
                <tr><td colspan="6" class="p-4 text-center text-neutral-400">Nenhum pagamento ainda.</td></tr>
            @endforelse
            </tbody>
        </table>
    </div>
    <div>{{ $appointments->links() }}</div>
</div>
@endsection
