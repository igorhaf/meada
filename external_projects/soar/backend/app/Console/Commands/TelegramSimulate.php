<?php

namespace App\Console\Commands;

use App\Models\User;
use App\Services\EloClient;
use App\Services\Telegram\ToolExecutor;
use Illuminate\Console\Command;

/**
 * Smoke do fluxo IA→ação SEM Telegram: executa uma ferramenta diretamente
 * ou roda uma frase pelo Elo e mostra a tag extraída.
 */
class TelegramSimulate extends Command
{
    protected $signature = 'telegram:simulate {email} {--tool=} {--args=} {--say=}';

    protected $description = 'Simula ações do bot (teste): --tool/--args executa direto; --say passa pelo Elo';

    public function handle(ToolExecutor $tools, EloClient $elo): int
    {
        $user = User::where('email', $this->argument('email'))->firstOrFail();

        if ($this->option('tool')) {
            $result = $tools->execute($user, $this->option('tool'), json_decode($this->option('args') ?? '{}', true) ?? []);
            $this->line(json_encode($result, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));

            return self::SUCCESS;
        }

        if ($say = $this->option('say')) {
            $service = app(\App\Services\Telegram\ConversationService::class);
            // usa reflexão só no teste pra acessar o prompt real do bot
            $ref = new \ReflectionMethod($service, 'systemPrompt');
            $system = $ref->invoke($service, $user);

            $response = $elo->chat([['role' => 'user', 'content' => $say]], system: $system, sessionKey: 'simulate-'.$user->id);
            $this->line($response);
        }

        return self::SUCCESS;
    }
}
