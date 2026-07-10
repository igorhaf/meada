<?php

namespace App\Console\Commands;

use App\Mail\EventReminderMail;
use App\Models\Event;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Mail;

class SendEventReminders extends Command
{
    protected $signature = 'events:remind';

    protected $description = 'Envia lembretes por email dos eventos próximos aos inscritos.';

    public function handle(): int
    {
        $now = now();
        $sent = 0;

        // Eventos publicados, futuros, ainda não lembrados, que começam DENTRO da
        // janela de lembrete (now ≤ starts_at ≤ now + reminder_hours).
        $events = Event::published()
            ->whereNull('reminded_at')
            ->where('starts_at', '>=', $now->utc())
            ->get()
            ->filter(fn ($e) => $e->starts_at->lessThanOrEqualTo($now->copy()->addHours((int) $e->reminder_hours)));

        foreach ($events as $event) {
            $registrations = $event->registrations()
                ->where('status', '!=', 'cancelled')
                ->where('reminded', false)
                ->whereNotNull('participant_email')
                ->get();

            foreach ($registrations as $registration) {
                Mail::to($registration->participant_email)->send(new EventReminderMail($registration));
                $registration->update(['reminded' => true]);
                $sent++;
            }

            $event->update(['reminded_at' => $now]);
        }

        $this->info("Lembretes enviados: {$sent}.");

        return self::SUCCESS;
    }
}
