<?php

namespace App\Services;

use App\Models\AccessPass;
use App\Models\Appointment;
use App\Models\EventRegistration;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Str;

class AccessPassService
{
    public function issue(Appointment|EventRegistration $source): AccessPass
    {
        if ($existing = $source->accessPasses()->whereIn('status', ['valid', 'used'])->first()) {
            return $existing;
        }

        [$from, $until, $holderId, $holderName] = $source instanceof Appointment
            ? [$source->start_at->copy()->subHours(2), $source->end_at->copy()->addDay(), $source->customer_id, $source->patient_name]
            : $this->eventWindow($source);

        $code = strtoupper(Str::random(12));

        return $source->accessPasses()->create([
            'public_code' => $code,
            'token_hash' => hash_hmac('sha256', $code, (string) config('app.key')),
            'holder_id' => $holderId,
            'holder_name' => $holderName,
            'status' => 'valid',
            'valid_from' => $from,
            'valid_until' => $until,
        ]);
    }

    public function cancel(Model $source): void
    {
        $source->accessPasses()->where('status', 'valid')->update(['status' => 'cancelled']);
    }

    /** @return array{0:mixed,1:mixed,2:?int,3:string} */
    private function eventWindow(EventRegistration $registration): array
    {
        $registration->loadMissing('event.sessions');
        $sessions = $registration->event->sessions;
        $from = ($sessions->min('starts_at') ?: $registration->event->starts_at)->copy()->subHours(2);
        $until = ($sessions->max('ends_at') ?: $registration->event->ends_at ?: $registration->event->starts_at)->copy()->addDay();

        return [$from, $until, $registration->customer_id, $registration->participant_name];
    }
}
