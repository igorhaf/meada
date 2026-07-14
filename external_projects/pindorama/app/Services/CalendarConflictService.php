<?php

namespace App\Services;

use App\Models\Appointment;
use App\Models\AvailabilityBlock;
use App\Models\EventSession;
use Carbon\CarbonInterface;

class CalendarConflictService
{
    /** @return array<int,string> */
    public function conflicts(array $professionalIds, ?int $roomId, CarbonInterface $start, CarbonInterface $end, ?int $ignoreEventId = null): array
    {
        $messages = [];
        foreach ($professionalIds as $professionalId) {
            if (Appointment::blocking()->where('professional_id', $professionalId)->where('start_at', '<', $end)->where('end_at', '>', $start)->exists())
                $messages[] = "O profissional #{$professionalId} possui uma consulta nesse horário.";
            if (AvailabilityBlock::where('professional_id', $professionalId)->where('starts_at', '<', $end)->where('ends_at', '>', $start)->exists())
                $messages[] = "O profissional #{$professionalId} possui um bloqueio nesse horário.";
            if (EventSession::whereHas('professionals', fn ($q) => $q->whereKey($professionalId))->when($ignoreEventId, fn ($q) => $q->where('event_id', '!=', $ignoreEventId))->where('status', '!=', 'cancelled')->where('starts_at', '<', $end)->where('ends_at', '>', $start)->exists())
                $messages[] = "O profissional #{$professionalId} já participa de outro evento nesse horário.";
        }
        if ($roomId) {
            if (Appointment::blocking()->whereHas('location', fn ($q) => $q->where('room_id', $roomId))->where('start_at', '<', $end)->where('end_at', '>', $start)->exists())
                $messages[] = "A sala #{$roomId} já possui uma consulta nesse horário.";
            if (EventSession::where('room_id', $roomId)->when($ignoreEventId, fn ($q) => $q->where('event_id', '!=', $ignoreEventId))->where('status', '!=', 'cancelled')->where('starts_at', '<', $end)->where('ends_at', '>', $start)->exists())
                $messages[] = "A sala #{$roomId} já está reservada para outro evento nesse horário.";
        }
        return array_values(array_unique($messages));
    }
}
