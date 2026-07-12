<?php

namespace App\Console\Commands;

use App\Models\CalendarEvent;
use App\Models\TaskItem;
use App\Models\User;
use App\Services\FamilyNotifier;
use Illuminate\Console\Command;

/** Bom-dia com a agenda e as tarefas do dia, por usuário vinculado. */
class DailySummary extends Command
{
    protected $signature = 'soar:daily-summary';

    protected $description = 'Resumo diário (agenda + tarefas) via Telegram';

    public function handle(FamilyNotifier $notifier): int
    {
        foreach (User::whereNotNull('telegram_chat_id')->get() as $user) {
            $events = CalendarEvent::whereHas('page', fn ($q) => $q->accessibleBy($user)->where('kind', 'calendar'))
                ->whereBetween('starts_at', [now()->startOfDay(), now()->endOfDay()])
                ->orderBy('starts_at')
                ->get();

            $tasks = TaskItem::whereHas('page', fn ($q) => $q->accessibleBy($user)->where('kind', 'tasks'))
                ->where('done', false)
                ->whereNotNull('due_date')
                ->where('due_date', '<=', now()->toDateString())
                ->get();

            if ($events->isEmpty() && $tasks->isEmpty()) {
                continue;
            }

            $lines = ["☀️ Bom dia, {$user->name}! Resumo de hoje:"];
            if ($events->isNotEmpty()) {
                $lines[] = "\n📅 Agenda:";
                foreach ($events as $e) {
                    $lines[] = '• '.($e->all_day ? 'o dia todo' : $e->starts_at->format('H:i')).' — '.$e->title;
                }
            }
            if ($tasks->isNotEmpty()) {
                $lines[] = "\n✅ Tarefas pra hoje (ou atrasadas):";
                foreach ($tasks as $t) {
                    $lines[] = '• '.$t->content.($t->due_date->isToday() ? '' : ' (desde '.$t->due_date->format('d/m').')');
                }
            }

            $notifier->notify(collect([$user]), implode("\n", $lines));
        }

        return self::SUCCESS;
    }
}
