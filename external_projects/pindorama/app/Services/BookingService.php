<?php

namespace App\Services;

use App\Exceptions\OutsideHoursException;
use App\Exceptions\SlotUnavailableException;
use App\Models\Appointment;
use App\Models\AttendanceLocation;
use App\Models\AvailabilityBlock;
use App\Models\Service;
use App\Models\User;
use Carbon\CarbonImmutable;
use Carbon\CarbonInterface;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

/**
 * Agenda engine (chassi-A). Availability is scoped BY LOCATION; occupation
 * (conflict) is ALWAYS by professional, across every location — the professional
 * cannot be in two places at once.
 */
class BookingService
{
    public function __construct(private ?string $defaultTz = null)
    {
        $this->defaultTz ??= config('pindorama.timezone', 'America/Sao_Paulo');
    }

    /**
     * Free start times ("H:i") for professional + service + location + date.
     *
     * @return array<int,string>
     */
    public function availableSlots(User $professional, Service $service, AttendanceLocation $location, CarbonImmutable $date): array
    {
        $tz = $this->tz($professional);
        $date = $date->setTimezone($tz)->startOfDay();
        $duration = (int) $service->duration_minutes;
        $step = $duration + (int) $service->buffer_minutes;

        $now = CarbonImmutable::now($tz);
        if ($date->lessThan($now->startOfDay())) {
            return []; // datas passadas não têm horários
        }

        $windows = $this->windowsForDate($professional, $location, $date);
        if (empty($windows)) {
            return [];
        }

        $busy = $this->busyIntervals($professional, $location, $date->startOfDay(), $date->endOfDay());

        $minStart = $date->isSameDay($now)
            ? $now->addMinutes((int) config('pindorama.min_lead_minutes', 60))
            : $date->subSecond();

        $slots = [];
        foreach ($windows as [$wStart, $wEnd]) {
            for ($t = $wStart; $t->addMinutes($duration)->lessThanOrEqualTo($wEnd); $t = $t->addMinutes($step)) {
                $end = $t->addMinutes($duration);
                if ($t->lessThan($minStart)) {
                    continue;
                }
                if (! $this->overlapsAny($t, $end, $busy)) {
                    $slots[] = $t->format('H:i');
                }
            }
        }

        return array_values(array_unique($slots));
    }

    /**
     * Create an appointment (transactional, conflict-checked). Born `pending`.
     *
     * @param  array{name:string,email?:string|null,phone?:string|null,notes?:string|null}  $patient
     *
     * @throws OutsideHoursException  when outside the location's working hours (422)
     * @throws SlotUnavailableException  when the slot conflicts on the agenda (409)
     */
    public function book(User $professional, Service $service, AttendanceLocation $location, CarbonInterface $start, array $patient, ?User $customer = null): Appointment
    {
        $tz = $this->tz($professional);
        $start = CarbonImmutable::parse($start)->setTimezone($tz);

        // Guards de integridade
        abort_unless($service->professional_id === $professional->id && $service->is_active, 404);
        abort_unless($location->professional_id === $professional->id, 404);
        abort_unless($service->locations()->whereKey($location->id)->exists(), 422, 'Serviço não é oferecido neste local.');

        // Materializa end_at em PHP (nunca GENERATED — timestamptz+interval não é IMMUTABLE)
        $end = $start->addMinutes((int) $service->duration_minutes);

        if ($start->isPast()) {
            throw new OutsideHoursException('Não é possível agendar em um horário passado.');
        }
        if (! $this->isWithinHours($professional, $location, $start, $end)) {
            throw new OutsideHoursException;
        }

        return DB::transaction(function () use ($professional, $service, $location, $start, $end, $patient, $customer, $tz) {
            // Serializa a AGENDA INTEIRA do profissional (cobre a corrida de phantom-insert).
            if (DB::getDriverName() === 'pgsql') {
                DB::statement("SELECT pg_advisory_xact_lock(hashtext('appt:' || ?))", [$professional->id]);
            }

            // Re-verifica conflito por PROFISSIONAL (sem filtro de local) — meia-aberta.
            if ($this->hasConflict($professional, $start, $end)) {
                throw new SlotUnavailableException;
            }

            $appointment = Appointment::create([
                'reference' => $this->uniqueReference(),
                'professional_id' => $professional->id,
                'service_id' => $service->id,
                'attendance_location_id' => $location->id,
                'customer_id' => $customer?->id,
                'patient_name' => $patient['name'],
                'patient_email' => $patient['email'] ?? $customer?->email,
                'patient_phone' => $patient['phone'] ?? null,
                'service_title' => $service->title,
                'service_price' => $service->price,
                'duration_minutes' => $service->duration_minutes,
                'modality' => $service->modality,
                'location_label' => $location->is_online ? 'Atendimento online' : trim($location->name . ' — ' . $location->full_address, ' —'),
                'start_at' => $start,
                'end_at' => $end,
                'timezone' => $tz,
                'status' => 'pending',
                'meeting_link' => null,
                'notes' => $patient['notes'] ?? null,
                'payment_status' => 'pending',
                'total' => $service->price,
            ]);

            $service->increment('bookings_count');

            return $appointment;
        });
    }

    /* -------------------------------------------------- Lifecycle (aceite gate) */

    public function confirm(Appointment $appointment): void
    {
        if ($appointment->status === 'pending') {
            $appointment->update(['status' => 'confirmed', 'confirmed_at' => now()]);
        }
    }

    public function complete(Appointment $appointment): void
    {
        if (in_array($appointment->status, ['pending', 'confirmed'], true)) {
            $appointment->update(['status' => 'completed', 'completed_at' => now()]);
        }
    }

    public function cancel(Appointment $appointment, string $by = 'system'): void
    {
        if (in_array($appointment->status, ['pending', 'confirmed'], true)) {
            $appointment->update(['status' => 'cancelled', 'cancelled_at' => now(), 'cancelled_by' => $by]);
        }
    }

    /* --------------------------------------------------------------- internals */

    private function tz(User $professional): string
    {
        return $professional->timezone ?: $this->defaultTz;
    }

    /**
     * Concrete [start, end] datetime windows for a location on a given date.
     *
     * @return array<int,array{0:CarbonImmutable,1:CarbonImmutable}>
     */
    private function windowsForDate(User $professional, AttendanceLocation $location, CarbonImmutable $date): array
    {
        $weekday = $date->dayOfWeek; // 0=domingo … 6=sábado

        return $professional->availabilities()
            ->where('attendance_location_id', $location->id)
            ->where('weekday', $weekday)
            ->where('is_active', true)
            ->orderBy('start_time')
            ->get()
            ->map(fn ($a) => [
                $date->setTimeFromTimeString((string) $a->start_time),
                $date->setTimeFromTimeString((string) $a->end_time),
            ])
            ->all();
    }

    /**
     * Busy intervals for the day: blocks (this location or global) + appointments
     * on the professional's agenda ACROSS EVERY LOCATION.
     *
     * @return array<int,array{0:CarbonImmutable,1:CarbonImmutable}>
     */
    private function busyIntervals(User $professional, AttendanceLocation $location, CarbonImmutable $dayStart, CarbonImmutable $dayEnd): array
    {
        $intervals = [];

        $blocks = $professional->availabilityBlocks()
            ->overlapping($dayStart->utc(), $dayEnd->utc(), $location->id)
            ->get();
        foreach ($blocks as $b) {
            $intervals[] = [CarbonImmutable::parse($b->starts_at), CarbonImmutable::parse($b->ends_at)];
        }

        $appointments = $professional->appointmentsAsProfessional()
            ->blocking()
            ->where('start_at', '<', $dayEnd->utc())
            ->where('end_at', '>', $dayStart->utc())
            ->get();
        foreach ($appointments as $a) {
            $intervals[] = [CarbonImmutable::parse($a->start_at), CarbonImmutable::parse($a->end_at)];
        }

        return $intervals;
    }

    /** @param array<int,array{0:CarbonImmutable,1:CarbonImmutable}> $intervals */
    private function overlapsAny(CarbonImmutable $start, CarbonImmutable $end, array $intervals): bool
    {
        foreach ($intervals as [$s, $e]) {
            if ($start->lessThan($e) && $end->greaterThan($s)) { // half-open overlap
                return true;
            }
        }

        return false;
    }

    private function isWithinHours(User $professional, AttendanceLocation $location, CarbonImmutable $start, CarbonImmutable $end): bool
    {
        if (! $start->isSameDay($end)) {
            return false;
        }

        $date = $start->startOfDay();
        $withinWindow = false;
        foreach ($this->windowsForDate($professional, $location, $date) as [$wStart, $wEnd]) {
            if ($start->greaterThanOrEqualTo($wStart) && $end->lessThanOrEqualTo($wEnd)) {
                $withinWindow = true;
                break;
            }
        }
        if (! $withinWindow) {
            return false;
        }

        // Nenhum bloqueio pode cobrir o intervalo.
        $blocked = $professional->availabilityBlocks()
            ->overlapping($start->utc(), $end->utc(), $location->id)
            ->exists();

        return ! $blocked;
    }

    private function hasConflict(User $professional, CarbonInterface $start, CarbonInterface $end): bool
    {
        // NOT (end_at <= start OR start_at >= end)  ≡  (start_at < end AND end_at > start)
        return Appointment::where('professional_id', $professional->id)
            ->whereIn('status', Appointment::BLOCKING_STATUSES)
            ->where('start_at', '<', CarbonImmutable::parse($end)->utc())
            ->where('end_at', '>', CarbonImmutable::parse($start)->utc())
            ->exists();
    }

    private function uniqueReference(): string
    {
        do {
            $ref = 'PIND-' . strtoupper(Str::random(8));
        } while (Appointment::where('reference', $ref)->exists());

        return $ref;
    }
}
