@php($start = $event->starts_at->setTimezone($event->timezone))
<p>Olá, {{ $registration->participant_name }}!</p>

<p>Este é um lembrete do evento <strong>{{ $event->title }}</strong>.</p>

<ul>
    <li><strong>Quando:</strong> {{ $start->format('d/m/Y \à\s H:i') }}</li>
    <li><strong>Onde:</strong> {{ $event->modality === 'online' ? 'Online' : ($event->location_label ?: 'Presencial') }}</li>
    <li><strong>Inscrição:</strong> {{ $registration->reference }}</li>
</ul>

<p>Até lá! 🌿<br>Equipe Pindorama</p>
