<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;
use RuntimeException;

/**
 * Cliente do Elo (proxy do Claude Code, API Anthropic-compatible).
 * `session_key` mantém a memória da conversa no lado do Elo.
 */
class EloClient
{
    public function isConfigured(): bool
    {
        return (bool) config('services.elo.base_url') && (bool) config('services.elo.key');
    }

    /**
     * @param  array<int, array{role: string, content: string}>  $messages
     */
    public function chat(array $messages, ?string $system = null, ?string $sessionKey = null, int $maxTokens = 2048): string
    {
        $payload = [
            'model' => config('services.elo.model'),
            'max_tokens' => $maxTokens,
            'stream' => false,
            'messages' => $messages,
        ];
        if ($system !== null) {
            $payload['system'] = $system;
        }
        if ($sessionKey !== null) {
            $payload['session_key'] = $sessionKey;
        }

        $response = Http::timeout(300)
            ->withHeaders(['x-api-key' => config('services.elo.key')])
            ->post(rtrim(config('services.elo.base_url'), '/').'/v1/messages', $payload);

        if (! $response->successful()) {
            throw new RuntimeException('Elo indisponível: HTTP '.$response->status());
        }

        $text = collect($response->json('content') ?? [])
            ->where('type', 'text')
            ->pluck('text')
            ->join('');

        if ($text === '') {
            throw new RuntimeException('Elo devolveu resposta vazia.');
        }

        return $text;
    }
}
