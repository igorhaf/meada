@php($start = $appt->start_at->setTimezone($tz))
@php($end = $appt->end_at->setTimezone($tz))
@php($chip = [
    'pending' => 'bg-amber-100 text-amber-800',
    'confirmed' => 'bg-brand-100 text-brand-800',
    'completed' => 'bg-neutral-200 text-neutral-700',
    'cancelled' => 'bg-red-100 text-red-700',
    'no_show' => 'bg-red-100 text-red-700',
])
<div class="card flex flex-wrap items-center gap-3 p-4">
    <div class="w-16 shrink-0 text-center">
        <p class="text-lg font-extrabold text-neutral-900">{{ $start->format('H:i') }}</p>
        <p class="text-xs text-neutral-400">{{ $start->format('H:i') }}–{{ $end->format('H:i') }}</p>
    </div>
    <div class="min-w-0 flex-1">
        <a href="{{ route('professional.appointments.show', $appt) }}" class="font-semibold text-neutral-900 hover:text-brand-700">{{ $appt->service_title }}</a>
        <p class="text-sm text-neutral-500">{{ $appt->patient_name }} · {{ $appt->location?->name ?? $appt->location_label }}</p>
    </div>
    <span class="chip {{ $chip[$appt->status] ?? 'bg-neutral-100 text-neutral-600' }}">{{ $appt->status_label }}</span>
    <div class="flex items-center gap-1.5">
        @if($appt->status === 'pending')
            <form method="POST" action="{{ route('professional.appointments.confirm', $appt) }}">@csrf<button class="rounded-lg bg-brand-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-brand-700">Confirmar</button></form>
        @endif
        @if($appt->status === 'confirmed')
            <form method="POST" action="{{ route('professional.appointments.complete', $appt) }}">@csrf<button class="rounded-lg bg-neutral-700 px-3 py-1.5 text-xs font-semibold text-white hover:bg-neutral-800">Concluir</button></form>
        @endif
        @if(in_array($appt->status, ['pending', 'confirmed']))
            <form method="POST" action="{{ route('professional.appointments.cancel', $appt) }}" onsubmit="return confirm('Cancelar este agendamento?')">@csrf<button class="rounded-lg border border-neutral-300 px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50">Cancelar</button></form>
        @endif
    </div>
</div>
