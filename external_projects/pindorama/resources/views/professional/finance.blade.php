@extends('layouts.dashboard')
@section('title','Meu financeiro')
@section('content')
<div class="mx-auto max-w-5xl space-y-5">
    <div><h1 class="text-2xl font-extrabold">Meu financeiro</h1><p class="text-sm text-neutral-500">Serviços e eventos vinculados ao seu perfil.</p></div>
    <form method="GET" class="card flex flex-wrap gap-3 p-4"><input type="date" name="from" value="{{ request('from') }}" class="rounded-lg border px-3 py-2"><input type="date" name="to" value="{{ request('to') }}" class="rounded-lg border px-3 py-2"><button class="btn-outline">Filtrar</button></form>
    <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">@foreach([['Bruto',money($totals['gross'])],['Casa',money($totals['house'])],['Meu líquido',money($totals['net'])],['Recebido',money($totals['paid_out'])]] as [$l,$v])<div class="card p-4"><p class="text-xs uppercase text-neutral-400">{{ $l }}</p><p class="mt-1 text-lg font-extrabold">{{ $v }}</p></div>@endforeach</div>
    <div class="card overflow-x-auto"><table class="w-full text-sm"><thead><tr class="border-b text-left text-xs uppercase text-neutral-400"><th class="p-3">Data</th><th>Origem</th><th>Bruto</th><th>Casa</th><th>Minha parte</th><th>Status</th></tr></thead><tbody>@forelse($transactions as $tx)<tr class="border-b"><td class="p-3">{{ $tx->created_at->format('d/m/Y') }}</td><td>{{ $tx->payable instanceof \App\Models\Appointment?'Serviço':'Evento' }}</td><td>{{ money($tx->gross_amount) }}</td><td>{{ money($tx->house_amount) }}</td><td>{{ money($tx->splits->first()?->amount ?? 0) }}</td><td>{{ $tx->status }}</td></tr>@empty<tr><td colspan="6" class="p-5 text-center text-neutral-500">Nenhuma transação.</td></tr>@endforelse</tbody></table></div>{{ $transactions->links() }}
    <div class="card p-5"><h2 class="font-bold">Repasses</h2><div class="mt-3 space-y-2">@forelse($payouts as $p)<p class="rounded-lg border p-3 text-sm">{{ $p->period_start->format('d/m/Y') }}–{{ $p->period_end->format('d/m/Y') }} · <strong>{{ money($p->amount) }}</strong> · {{ $p->status }}</p>@empty<p class="text-sm text-neutral-500">Nenhum repasse registrado.</p>@endforelse</div></div>
</div>
@endsection
