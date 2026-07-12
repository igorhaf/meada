<?php

namespace App\Services;

use App\Models\Page;
use App\Models\User;
use App\Services\Telegram\TelegramClient;
use Illuminate\Support\Collection;

/** Entrega notificações proativas pelo Telegram (best-effort). */
class FamilyNotifier
{
    public function __construct(private readonly TelegramClient $telegram)
    {
    }

    /** Destinatários de uma página: compartilhada → todos vinculados; pessoal → dono. */
    public function recipientsFor(Page $page): Collection
    {
        if ($page->scope === Page::SCOPE_PERSONAL) {
            return User::where('id', $page->owner_id)->whereNotNull('telegram_chat_id')->get();
        }

        return User::whereNotNull('telegram_chat_id')->get();
    }

    public function notify(Collection $users, string $text): void
    {
        foreach ($users as $user) {
            if ($user->telegram_chat_id) {
                $this->telegram->sendMessage($user->telegram_chat_id, $text);
            }
        }
    }
}
