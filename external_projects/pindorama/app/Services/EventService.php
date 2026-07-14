<?php

namespace App\Services;

use App\Exceptions\EventFullException;
use App\Models\Event;
use App\Models\EventRegistration;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use RuntimeException;

class EventService
{
    public function __construct(private AccessPassService $passes, private NotificationService $notifications) {}

    /**
     * Inscreve um participante num evento com controle de vagas TRANSACIONAL.
     *
     * @param  array{name:string,email?:string|null,phone?:string|null,consent?:bool}  $participant
     *
     * @throws EventFullException  quando não há vagas (409 sold_out)
     * @throws RuntimeException     quando o participante já está inscrito
     */
    public function register(Event $event, array $participant, ?User $customer = null): EventRegistration
    {
        abort_unless($event->status === 'published', 404);

        $registration = DB::transaction(function () use ($event, $participant, $customer) {
            if (DB::getDriverName() === 'pgsql') {
                DB::statement("SELECT pg_advisory_xact_lock(hashtext('event:' || ?))", [$event->id]);
            }

            // Anti-dupla por conta
            if ($customer && EventRegistration::where('event_id', $event->id)->where('customer_id', $customer->id)
                ->where('status', '!=', 'cancelled')->exists()) {
                throw new RuntimeException('Você já está inscrito neste evento.');
            }

            // Vagas (re-checado dentro da transação)
            if ((int) $event->capacity > 0) {
                $taken = EventRegistration::where('event_id', $event->id)->where('status', '!=', 'cancelled')->count();
                if ($taken >= (int) $event->capacity) {
                    throw new EventFullException;
                }
            }

            [$amount, $discount] = $this->price($event);
            $free = $event->is_free || $amount == 0.0;

            return EventRegistration::create([
                'reference' => 'EVT-' . strtoupper(Str::random(8)),
                'event_id' => $event->id,
                'customer_id' => $customer?->id,
                'participant_name' => $participant['name'],
                'participant_email' => $participant['email'] ?? $customer?->email,
                'participant_phone' => $participant['phone'] ?? null,
                'status' => $free ? 'confirmed' : 'registered',
                'amount' => $amount,
                'discount_amount' => $discount,
                'payment_status' => $free ? 'approved' : 'pending',
                'payment_method' => $free ? 'gratuito' : null,
                'paid_at' => $free ? now() : null,
                'consent_at' => ($participant['consent'] ?? false) ? now() : null,
            ]);
        });

        if ($registration->isPaid()) $this->passes->issue($registration);
        $this->notifications->eventRegistered($registration);

        return $registration;
    }

    /** @return array{0:float,1:float} [amount, discount] */
    private function price(Event $event): array
    {
        if ($event->is_free) {
            return [0.0, 0.0];
        }
        $base = (float) $event->price;
        $discount = $event->allow_discount ? round($base * (float) $event->discount_percent / 100, 2) : 0.0;

        return [max(0.0, round($base - $discount, 2)), $discount];
    }
}
