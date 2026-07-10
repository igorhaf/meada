@extends('layouts.dashboard')

@section('title', $user->display_name)

@section('content')
@php($input = 'w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm')
<div class="mx-auto max-w-3xl space-y-6">
    <a href="{{ route('admin.professionals.index') }}" class="text-sm text-neutral-500 hover:underline">← Terapeutas</a>
    <div class="flex items-center justify-between">
        <h1 class="text-2xl font-extrabold text-neutral-900">{{ $user->display_name }}</h1>
        <form method="POST" action="{{ route('admin.professionals.verify', $user) }}">@csrf
            <button class="rounded-lg border px-3 py-1.5 text-xs font-semibold {{ $user->is_verified ? 'border-neutral-300 text-neutral-600' : 'border-brand-600 bg-brand-600 text-white' }}">{{ $user->is_verified ? 'Remover verificação' : 'Verificar' }}</button>
        </form>
    </div>

    {{-- Config de cobrança --}}
    <div class="card p-6">
        <h2 class="mb-4 font-bold text-neutral-800">Cobrança da plataforma</h2>
        <form method="POST" action="{{ route('admin.professionals.billing', $user) }}" class="grid gap-4 sm:grid-cols-2">
            @csrf @method('PUT')
            <div>
                <label class="mb-1 block text-xs font-medium text-neutral-600">Mensalidade (R$)</label>
                <input type="number" step="0.01" min="0" name="billing_monthly_fee" value="{{ old('billing_monthly_fee', $user->billing_monthly_fee) }}" class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-xs font-medium text-neutral-600">Desconto (%)</label>
                <input type="number" step="0.01" min="0" max="100" name="billing_discount_percent" value="{{ old('billing_discount_percent', $user->billing_discount_percent) }}" class="{{ $input }}">
            </div>
            <div>
                <label class="mb-1 block text-xs font-medium text-neutral-600">Dia de vencimento</label>
                <input type="number" min="1" max="28" name="billing_day" value="{{ old('billing_day', $user->billing_day) }}" class="{{ $input }}">
            </div>
            <div class="flex items-end gap-4">
                <label class="flex items-center gap-2 text-sm"><input type="checkbox" name="billing_free" value="1" @checked($user->billing_free)> Gratuidade</label>
                <label class="flex items-center gap-2 text-sm"><input type="checkbox" name="billing_active" value="1" @checked($user->billing_active)> Ativa</label>
            </div>
            <div class="sm:col-span-2"><button class="btn-brand">Salvar cobrança</button></div>
        </form>
    </div>

    {{-- Gerar cobranças --}}
    <div class="grid gap-4 sm:grid-cols-2">
        <form method="POST" action="{{ route('admin.professionals.charge.monthly', $user) }}" class="card p-5">
            @csrf
            <h3 class="mb-2 text-sm font-bold text-neutral-800">Mensalidade do mês</h3>
            <p class="mb-3 text-xs text-neutral-500">Gera a cobrança da mensalidade atual (aplica desconto/gratuidade).</p>
            <button class="btn-outline w-full">Gerar mensalidade</button>
        </form>
        <form method="POST" action="{{ route('admin.professionals.charge.create', $user) }}" class="card space-y-2 p-5">
            @csrf
            <h3 class="text-sm font-bold text-neutral-800">Cobrança avulsa</h3>
            <select name="type" class="{{ $input }}"><option value="registration">Cadastro</option><option value="featured">Anúncio destaque</option></select>
            <input name="description" placeholder="Descrição" class="{{ $input }}">
            <input type="number" step="0.01" min="0" name="base_amount" placeholder="Valor (R$)" class="{{ $input }}">
            <button class="btn-outline w-full">Criar cobrança</button>
        </form>
    </div>

    {{-- Histórico de cobranças --}}
    <div class="card p-6">
        <h2 class="mb-3 font-bold text-neutral-800">Cobranças</h2>
        <div class="overflow-x-auto">
            <table class="w-full text-sm">
                <thead><tr class="border-b border-neutral-200 text-left text-xs uppercase tracking-wide text-neutral-400"><th class="py-2">Tipo</th><th>Descrição</th><th>Valor</th><th>Status</th><th></th></tr></thead>
                <tbody>
                @forelse($charges as $c)
                    <tr class="border-b border-neutral-100">
                        <td class="py-2">{{ $c->type_label }}</td>
                        <td class="text-neutral-600">{{ $c->description }}</td>
                        <td>{{ money($c->amount) }} @if($c->discount_amount > 0)<span class="text-xs text-neutral-400">(-{{ money($c->discount_amount) }})</span>@endif</td>
                        <td><span class="chip {{ $c->isPaid() ? 'bg-brand-100 text-brand-800' : 'bg-amber-100 text-amber-800' }}">{{ $c->status_label }}</span></td>
                        <td class="text-right">
                            @unless($c->isPaid())
                                <form method="POST" action="{{ route('admin.charges.status', $c) }}" class="inline">@csrf<input type="hidden" name="status" value="waived"><button class="text-xs text-neutral-500 hover:underline">isentar</button></form>
                                <form method="POST" action="{{ route('admin.charges.status', $c) }}" class="inline">@csrf<input type="hidden" name="status" value="paid"><button class="ml-2 text-xs text-brand-700 hover:underline">marcar pago</button></form>
                            @endunless
                        </td>
                    </tr>
                @empty
                    <tr><td colspan="5" class="py-4 text-center text-neutral-400">Nenhuma cobrança.</td></tr>
                @endforelse
                </tbody>
            </table>
        </div>
    </div>
</div>
@endsection
