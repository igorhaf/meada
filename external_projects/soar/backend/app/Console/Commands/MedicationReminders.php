<?php

namespace App\Console\Commands;

use App\Models\Medication;
use App\Services\FamilyNotifier;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Cache;

/** A cada minuto: manda "hora do remédio" pra quem tem horário batendo agora. */
class MedicationReminders extends Command
{
    protected $signature = 'soar:medication-reminders';

    protected $description = 'Lembretes de remédio no horário (Telegram)';

    public function handle(FamilyNotifier $notifier): int
    {
        $now = now()->format('H:i');
        $today = now()->toDateString();

        $due = Medication::where('active', true)
            ->whereJsonContains('schedule_times', $now)
            ->with('page')
            ->get();

        foreach ($due as $med) {
            $key = "med-reminder-{$med->id}-{$today}-{$now}";
            if (! Cache::add($key, true, now()->addHours(2))) {
                continue; // já lembrado neste horário
            }

            $dose = $med->dose ? " ({$med->dose})" : '';
            $notifier->notify(
                $notifier->recipientsFor($med->page),
                "💊 Hora do remédio: {$med->person} — {$med->name}{$dose} às {$now}.\nMe avise com \"tomou\" que eu registro.",
            );
        }

        return self::SUCCESS;
    }
}
