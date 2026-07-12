<?php

namespace App\Services\Telegram;

use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Throwable;

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
        // Telegram limita 4096 chars por mensagem; markdown do modelo vira texto puro.
        foreach (str_split($this->plain($text), 3800) as $chunk) {
            $response = Http::timeout(30)->post($this->url('sendMessage'), [
                'chat_id' => $chatId,
                'text' => $chunk,
            ]);

            // Falha de entrega era SILENCIOSA — agora fica no log.
            if (! $response->successful() || $response->json('ok') !== true) {
                Log::error('Telegram sendMessage falhou', [
                    'chat_id' => $chatId,
                    'status' => $response->status(),
                    'body' => mb_substr($response->body(), 0, 300),
                ]);
            }
        }
    }

    public function sendTyping(int|string $chatId): void
    {
        Http::timeout(10)->post($this->url('sendChatAction'), [
            'chat_id' => $chatId,
            'action' => 'typing',
        ]);
    }

    /** Reação imediata na mensagem do usuário: "recebi, estou processando". */
    public function react(int|string $chatId, int $messageId, string $emoji = '👀'): void
    {
        try {
            Http::timeout(10)->post($this->url('setMessageReaction'), [
                'chat_id' => $chatId,
                'message_id' => $messageId,
                'reaction' => json_encode([['type' => 'emoji', 'emoji' => $emoji]]),
            ]);
        } catch (Throwable $e) {
            Log::warning('Telegram react falhou (best-effort)', ['error' => $e->getMessage()]);
        }
    }

    /** Markdown do modelo (**negrito**, *itálico*, `code`) → texto puro. */
    private function plain(string $text): string
    {
        return preg_replace('/\*\*(.*?)\*\*|__(.*?)__|`(.*?)`/s', '$1$2$3', $text) ?? $text;
    }
}
