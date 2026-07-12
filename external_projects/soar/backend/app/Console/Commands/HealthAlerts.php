<?php

namespace App\Console\Commands;

use App\Models\Medication;
use App\Services\FamilyNotifier;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Cache;

/** Alertas diários: receita de controlado vencendo e estoque baixo. */
class HealthAlerts extends Command
{
    protected $signature = 'soar:health-alerts';

    protected $description = 'Alertas de receita vencendo e estoque baixo (Telegram)';

    public function handle(FamilyNotifier $notifier): int
    {
        $today = now()->toDateString();

        // Receitas de CONTROLADOS vencendo em até 7 dias (ou vencidas)
        $expiring = Medication::where('active', true)
            ->where('controlled', true)
            ->whereNotNull('prescription_until')
            ->where('prescription_until', '<=', now()->addDays(7)->toDateString())
            ->with('page')
            ->get();

        foreach ($expiring as $med) {
            if (! Cache::add("rx-alert-{$med->id}-{$today}", true, now()->addDay())) {
                continue;
            }
            $when = $med->prescription_until->isPast()
                ? 'VENCEU em '.$med->prescription_until->format('d/m/Y')
                : 'vence em '.$med->prescription_until->format('d/m/Y');
            $notifier->notify(
                $notifier->recipientsFor($med->page),
                "⚠️ Receita do controlado {$med->name} ({$med->person}) {$when}. Hora de renovar!",
            );
        }

        // Estoque baixo
        $low = Medication::where('active', true)
            ->whereNotNull('stock')
            ->whereNotNull('low_stock_threshold')
            ->whereColumn('stock', '<=', 'low_stock_threshold')
            ->with('page')
            ->get();

        foreach ($low as $med) {
            if (! Cache::add("stock-alert-{$med->id}-{$today}", true, now()->addDay())) {
                continue;
            }
            $notifier->notify(
                $notifier->recipientsFor($med->page),
                "📉 Estoque baixo: {$med->name} ({$med->person}) — restam {$med->stock}. Comprar mais!",
            );
        }

        return self::SUCCESS;
    }
}
