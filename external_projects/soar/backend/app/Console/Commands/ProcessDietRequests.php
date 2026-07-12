<?php

namespace App\Console\Commands;

use App\Models\Page;
use App\Models\User;
use App\Services\DietGenerator;
use App\Services\FamilyNotifier;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Log;
use Throwable;

/**
 * Processa pedidos de geração de dieta feitos pelo PAINEL (fila assíncrona:
 * o web em produção não alcança o Elo; este job roda junto do Elo).
 */
class ProcessDietRequests extends Command
{
    protected $signature = 'soar:process-diet-requests';

    protected $description = 'Gera dietas pedidas no painel via Elo';

    public function handle(DietGenerator $generator, FamilyNotifier $notifier): int
    {
        $pages = Page::where('kind', 'diet')->where('meta->generate_requested', true)->get();

        foreach ($pages as $page) {
            try {
                $requestedBy = $page->meta['generate_requested_by'] ?? null;
                $generator->generate($page);
                $this->info("Dieta gerada: {$page->title}");

                $user = $requestedBy ? User::find($requestedBy) : null;
                if ($user?->telegram_chat_id) {
                    $notifier->notify(collect([$user]), "🥗 A dieta \"{$page->title}\" ficou pronta! Veja no painel.");
                }
            } catch (Throwable $e) {
                Log::error('Geração de dieta falhou', ['page' => $page->id, 'error' => $e->getMessage()]);
                // solta a trava pra permitir nova tentativa manual
                $meta = $page->meta;
                unset($meta['generate_requested']);
                $page->update(['meta' => $meta]);
            }
        }

        return self::SUCCESS;
    }
}
