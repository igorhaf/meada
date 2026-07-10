@extends('layouts.dashboard')

@section('title', 'Aluguel / comissão')

@section('content')
@php($sel = 'rounded-lg border border-neutral-300 px-3 py-2 text-sm')
<div class="mx-auto max-w-3xl space-y-8">
    <div>
        <h1 class="text-2xl font-extrabold text-neutral-900">Aluguel / comissão</h1>
        <p class="text-sm text-neutral-500">A comissão da plataforma sobre cada atendimento pago é resolvida por precedência:
            <strong>sala → profissional → serviço → prática → padrão</strong>. A mais específica vence.</p>
    </div>

    @if(session('error'))<div class="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{{ session('error') }}</div>@endif

    {{-- Regras existentes --}}
    <div class="card p-6">
        <h2 class="mb-4 font-bold text-neutral-800">Regras ativas</h2>
        <div class="overflow-x-auto">
            <table class="w-full text-sm">
                <thead><tr class="border-b border-neutral-200 text-left text-xs uppercase tracking-wide text-neutral-400">
                    <th class="py-2">Escopo</th><th>Alvo</th><th>Taxa</th><th></th>
                </tr></thead>
                <tbody>
                @forelse($rules as $rule)
                    <tr class="border-b border-neutral-100">
                        <td class="py-2 font-medium">{{ $scopeTypes[$rule->scope_type] ?? $rule->scope_type }}</td>
                        <td class="text-neutral-600">{{ $rule->scope_type === 'default' ? '—' : ($labels[$rule->scope_type][$rule->scope_id] ?? '#'.$rule->scope_id) }}</td>
                        <td class="font-semibold">{{ $rule->rate_type === 'percent' ? rtrim(rtrim(number_format($rule->rate_value,2,',','.'),'0'),',').'%' : money($rule->rate_value) }}</td>
                        <td class="text-right">
                            <form method="POST" action="{{ route('admin.commission.destroy', $rule) }}" onsubmit="return confirm('Remover regra?')">@csrf @method('DELETE')<button class="text-red-500 hover:underline">Remover</button></form>
                        </td>
                    </tr>
                @empty
                    <tr><td colspan="4" class="py-4 text-center text-neutral-400">Nenhuma regra. Crie ao menos a regra padrão.</td></tr>
                @endforelse
                </tbody>
            </table>
        </div>
    </div>

    {{-- Nova/editar regra --}}
    <div class="card p-6">
        <h2 class="mb-4 font-bold text-neutral-800">Adicionar / atualizar regra</h2>
        <form method="POST" action="{{ route('admin.commission.store') }}" class="grid gap-3 sm:grid-cols-2" id="cform">
            @csrf
            <div>
                <label class="mb-1 block text-xs font-medium text-neutral-600">Escopo</label>
                <select name="scope_type" id="scope_type" class="{{ $sel }} w-full">
                    @foreach($scopeTypes as $val => $label)<option value="{{ $val }}">{{ $label }}</option>@endforeach
                </select>
            </div>
            <div id="target_wrap">
                <label class="mb-1 block text-xs font-medium text-neutral-600">Alvo</label>
                <select name="scope_id" id="scope_id" class="{{ $sel }} w-full">
                    <option value="">—</option>
                </select>
            </div>
            <div>
                <label class="mb-1 block text-xs font-medium text-neutral-600">Tipo</label>
                <select name="rate_type" class="{{ $sel }} w-full">
                    @foreach($rateTypes as $val => $label)<option value="{{ $val }}">{{ $label }}</option>@endforeach
                </select>
            </div>
            <div>
                <label class="mb-1 block text-xs font-medium text-neutral-600">Valor</label>
                <input type="number" step="0.01" min="0" name="rate_value" class="{{ $sel }} w-full" placeholder="Ex.: 20">
            </div>
            <div class="sm:col-span-2"><button class="btn-brand">Salvar regra</button></div>
        </form>
    </div>
</div>

<script>
    const targets = {
        room: @json($rooms->mapWithKeys(fn($r) => [$r->id => $r->name])),
        professional: @json($professionals->mapWithKeys(fn($p) => [$p->id => $p->name])),
        service_category: @json($categories->mapWithKeys(fn($c) => [$c->id => $c->name])),
        service: @json($services->mapWithKeys(fn($s) => [$s->id => $s->title])),
    };
    const scopeType = document.getElementById('scope_type');
    const scopeId = document.getElementById('scope_id');
    const wrap = document.getElementById('target_wrap');
    function refresh() {
        const t = scopeType.value;
        scopeId.innerHTML = '<option value="">—</option>';
        if (t === 'default') { wrap.style.display = 'none'; return; }
        wrap.style.display = '';
        for (const [id, name] of Object.entries(targets[t] || {})) {
            const o = document.createElement('option'); o.value = id; o.textContent = name; scopeId.appendChild(o);
        }
    }
    scopeType.addEventListener('change', refresh); refresh();
</script>
@endsection
