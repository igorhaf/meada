<?php

namespace App\Services\Telegram;

use Illuminate\Support\Facades\Http;

class TelegramClient
{
    private function url(string $method): string
    {
        return 'https://api.telegram.org/bot'.config('services.telegram.token').'/'.$method;
    }

    /** @return array<int, array<string, mixed>> */
    public function getUpdates(int $offset, int $timeout = 45): array
    {
        $response = Http::timeout($timeout + 15)->get($this->url('getUpdates'), [
            'offset' => $offset,
            'timeout' => $timeout,
            'allowed_updates' => '["message"]',
        ]);

        return $response->json('result') ?? [];
    }

    public function sendMessage(int|string $chatId, string $text): void
    {
        // Telegram limita 4096 chars por mensagem
        foreach (str_split($text, 3800) as $chunk) {
            Http::timeout(30)->post($this->url('sendMessage'), [
                'chat_id' => $chatId,
                'text' => $chunk,
            ]);
        }
    }

    public function sendTyping(int|string $chatId): void
    {
        Http::timeout(10)->post($this->url('sendChatAction'), [
            'chat_id' => $chatId,
            'action' => 'typing',
        ]);
    }
}
