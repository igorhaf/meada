<!doctype html>
<html lang="pt-BR">
<head>
    <meta charset="utf-8">
    <title>Relatório financeiro Pindorama</title>
    <style>
        body{font-family:DejaVu Sans,sans-serif;color:#262626;font-size:10px}h1{font-size:20px;margin:0 0 4px}.meta{color:#666;margin-bottom:18px}.totals{display:table;width:100%;margin-bottom:18px}.total{display:table-cell;border:1px solid #ddd;padding:10px}.total strong{display:block;font-size:15px;margin-top:3px}table{width:100%;border-collapse:collapse}th,td{border-bottom:1px solid #ddd;padding:7px;text-align:left}th{background:#f3f4f6;text-transform:uppercase;font-size:8px}td.money{text-align:right;white-space:nowrap}
    </style>
</head>
<body>
    <h1>Relatório financeiro Pindorama</h1>
    <p class="meta">Emitido em {{ now()->format('d/m/Y H:i') }}@if($professional) · Profissional: {{ $professional->display_name }}@endif @if(request('from'))· De {{ \Carbon\Carbon::parse(request('from'))->format('d/m/Y') }}@endif @if(request('to')) até {{ \Carbon\Carbon::parse(request('to'))->format('d/m/Y') }}@endif</p>
    <div class="totals"><div class="total">Receita bruta<strong>{{ money($totals['revenue']) }}</strong></div><div class="total">Valor da casa<strong>{{ money($totals['house']) }}</strong></div><div class="total">Líquido profissional(is)<strong>{{ money($totals['professional']) }}</strong></div></div>
    <table><thead><tr><th>Referência</th><th>Data</th><th>Origem</th><th>Cliente</th><th>Profissional responsável</th><th>Bruto</th><th>Casa</th><th>Líquido</th><th>Status</th><th>Método</th></tr></thead><tbody>
    @forelse($rows as $transaction)<tr><td>{{ $transaction->reference }}</td><td>{{ $transaction->created_at->format('d/m/Y H:i') }}</td><td>{{ $transaction->payable instanceof \App\Models\Appointment ? 'Serviço' : 'Evento' }}</td><td>{{ $transaction->customer?->name }}</td><td>{{ $transaction->professional?->display_name }}</td><td class="money">{{ money($transaction->gross_amount) }}</td><td class="money">{{ money($transaction->house_amount) }}</td><td class="money">{{ money($transaction->professional_amount) }}</td><td>{{ $transaction->status }}</td><td>{{ $transaction->payment_method }}</td></tr>@empty<tr><td colspan="10">Nenhuma transação para os filtros informados.</td></tr>@endforelse
    </tbody></table>
</body>
</html>
