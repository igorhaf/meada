@extends('layouts.dashboard')

@section('title', 'Inscritos')

@section('content')
<div class="mx-auto max-w-3xl">
    <a href="{{ route('professional.events.index') }}" class="text-sm text-neutral-500 hover:underline">← Meus eventos</a>
    <h1 class="mb-1 mt-2 text-2xl font-extrabold text-neutral-900">{{ $event->title }}</h1>
    <p class="mb-6 text-sm text-neutral-500">{{ $registrations->where('status','!=','cancelled')->count() }} inscritos{{ $event->capacity > 0 ? ' de '.$event->capacity.' vagas' : '' }}</p>

    @if($canManageAttendance)<form method="POST" action="{{ route('professional.events.registrations.store',$event) }}" class="card mb-5 flex flex-wrap items-center gap-3 p-4">@csrf<select name="customer_id" required class="min-w-56 flex-1 rounded-lg border px-3 py-2 text-sm"><option value="">Selecione um cliente</option>@foreach($customers as $customer)<option value="{{ $customer->id }}">{{ $customer->name }} · {{ $customer->email }}</option>@endforeach</select><label class="text-xs"><input type="checkbox" name="privacy_consent" value="1" required> consentimento confirmado</label><button class="btn-brand">Matricular aluno</button><a href="{{ route('professional.customers.create') }}" class="text-sm text-brand-700">Novo cliente</a></form>@endif

    <div class="card overflow-x-auto">
        <table class="w-full text-sm">
            <thead><tr class="border-b border-neutral-200 text-left text-xs uppercase tracking-wide text-neutral-400"><th class="p-3">Participante</th><th>Contato</th>@if($canViewFinancials)<th>Valor</th>@endif<th>Status</th>@if($canViewFinancials)<th>Pagamento</th>@endif@if($canManageAttendance)<th>Check-in</th>@endif</tr></thead>
            <tbody>
            @forelse($registrations as $r)
                <tr class="border-b border-neutral-100">
                    <td class="p-3 font-medium">{{ $r->participant_name }}</td>
                    <td class="text-neutral-500">{{ $r->participant_phone ?: $r->participant_email ?: '—' }}</td>
                    @if($canViewFinancials)<td>{{ $r->amount == 0 ? 'Gratuito' : money($r->amount) }}</td>@endif
                    <td>{{ $r->status_label }}</td>
                    @if($canViewFinancials)<td><span class="chip {{ $r->isPaid() ? 'bg-brand-100 text-brand-800' : 'bg-amber-100 text-amber-800' }}">{{ $r->isPaid() ? 'Pago' : 'Pendente' }}</span></td>@endif
                    @if($canManageAttendance)<td>@if($pass=$r->accessPasses->first())<a href="{{ URL::temporarySignedRoute('passes.show',now()->addYear(),['pass'=>$pass]) }}" class="text-brand-700">{{ $pass->status==='used'?'Presente':'Abrir passaporte' }}</a>@else—@endif</td>@endif
                </tr>
            @empty
                <tr><td colspan="5" class="p-4 text-center text-neutral-400">Nenhum inscrito ainda.</td></tr>
            @endforelse
            </tbody>
        </table>
    </div>
</div>
@endsection
