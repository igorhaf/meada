<?php

namespace App\Console\Commands;

use App\Services\Telegram\ConversationService;
use App\Services\Telegram\TelegramClient;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;
use Throwable;

class TelegramPoll extends Command
{
    protected $signature = 'telegram:poll';

    protected $description = 'Long-polling do bot do Telegram (getUpdates) — roda em loop';

    public function handle(TelegramClient $telegram, ConversationService $conversation): int
    {
        if (! config('services.telegram.token')) {
            $this->error('TELEGRAM_BOT_TOKEN ausente.');

            return self::FAILURE;
        }

        $this->info('Bot do Soar ouvindo o Telegram…');
        $offset = (int) Cache::get('telegram_offset', 0);

        while (true) { // @phpstan-ignore-line — loop de polling intencional
            try {
                $updates = $telegram->getUpdates($offset);
                foreach ($updates as $update) {
                    $offset = $update['update_id'] + 1;
                    Cache::forever('telegram_offset', $offset);
                    $conversation->handle($update);
                }
            } catch (Throwable $e) {
                Log::error('telegram:poll erro', ['error' => $e->getMessage()]);
                sleep(5);
            }
        }
    }
}
