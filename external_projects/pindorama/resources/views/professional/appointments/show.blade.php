@extends('layouts.dashboard')

@section('title', 'Agendamento ' . $appointment->reference)

@section('content')
@php($tz = auth()->user()->timezone ?: config('pindorama.timezone'))
@php($start = $appointment->start_at->setTimezone($tz))
@php($end = $appointment->end_at->setTimezone($tz))
<div class="mx-auto max-w-2xl">
    <a href="{{ route('professional.agenda', ['date' => $start->format('Y-m-d')]) }}" class="text-sm text-neutral-500 hover:underline">← Voltar à agenda</a>

    <div class="card mt-4 p-6">
        <div class="flex items-start justify-between">
            <div>
                <p class="text-xs uppercase tracking-wide text-neutral-400">{{ $appointment->reference }}</p>
                <h1 class="mt-1 text-2xl font-extrabold text-neutral-900">{{ $appointment->service_title }}</h1>
            </div>
            <span class="chip bg-neutral-100 text-neutral-700">{{ $appointment->status_label }}</span>
        </div>

        <dl class="mt-5 divide-y divide-neutral-100">
            <div class="flex justify-between py-2 text-sm"><dt class="text-neutral-500">Data e hora</dt><dd class="font-medium text-neutral-800">{{ $start->format('d/m/Y') }} · {{ $start->format('H:i') }}–{{ $end->format('H:i') }}</dd></div>
            <div class="flex justify-between py-2 text-sm"><dt class="text-neutral-500">Paciente</dt><dd class="font-medium text-neutral-800">{{ $appointment->patient_name }}</dd></div>
            @if($appointment->patient_phone)<div class="flex justify-between py-2 text-sm"><dt class="text-neutral-500">Telefone</dt><dd class="font-medium text-neutral-800">{{ $appointment->patient_phone }}</dd></div>@endif
            @if($appointment->patient_email)<div class="flex justify-between py-2 text-sm"><dt class="text-neutral-500">E-mail</dt><dd class="font-medium text-neutral-800">{{ $appointment->patient_email }}</dd></div>@endif
            <div class="flex justify-between py-2 text-sm"><dt class="text-neutral-500">Local</dt><dd class="font-medium text-neutral-800">{{ $appointment->location_label }}</dd></div>
            <div class="flex justify-between py-2 text-sm"><dt class="text-neutral-500">Valor</dt><dd class="font-medium text-neutral-800">{{ money($appointment->total) }}</dd></div>
            <div class="flex justify-between py-2 text-sm"><dt class="text-neutral-500">Pagamento</dt><dd class="font-medium text-neutral-800">{{ $appointment->payment_status_label }}</dd></div>
            @if($appointment->professional_amount !== null)
                <div class="flex justify-between py-2 text-sm"><dt class="text-neutral-500">Seu líquido (após comissão)</dt><dd class="font-semibold text-brand-700">{{ money($appointment->professional_amount) }}</dd></div>
            @endif
        </dl>

        @if($appointment->notes)
            <div class="mt-4 rounded-xl bg-neutral-50 p-4 text-sm text-neutral-600"><strong>Observações do paciente:</strong> {{ $appointment->notes }}</div>
        @endif

        <div class="mt-6 flex flex-wrap gap-2 border-t border-neutral-100 pt-5">
            @if($appointment->status === 'pending')
                <form method="POST" action="{{ route('professional.appointments.confirm', $appointment) }}">@csrf<button class="btn-brand">Confirmar</button></form>
            @endif
            @if($appointment->status === 'confirmed')
                <form method="POST" action="{{ route('professional.appointments.complete', $appointment) }}">@csrf<button class="rounded-xl bg-neutral-700 px-4 py-2 font-semibold text-white hover:bg-neutral-800">Concluir</button></form>
            @endif
            @if(in_array($appointment->status, ['pending', 'confirmed']))
                <form method="POST" action="{{ route('professional.appointments.no-show', $appointment) }}">@csrf<button class="btn-outline">Registrar falta</button></form>
                <form method="POST" action="{{ route('professional.appointments.cancel', $appointment) }}" onsubmit="return confirm('Cancelar este agendamento?')">@csrf<button class="btn-outline text-red-600">Cancelar</button></form>
            @endif
        </div>
    </div>
</div>
@endsection
