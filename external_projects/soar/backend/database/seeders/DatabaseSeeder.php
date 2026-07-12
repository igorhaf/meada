<?php

namespace Database\Seeders;

use App\Models\Page;
use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        if (User::query()->exists()) {
            return; // idempotente: não duplica no re-boot do compose
        }

        // Senha vem do ambiente (.env, gitignored) — nunca hardcoded no repo.
        $password = Hash::make(env('SEED_USER_PASSWORD', 'password'));

        $aline = User::create([
            'name' => 'Aline',
            'email' => 'alinecarla.rs@gmail.com',
            'password' => $password,
            'google_calendar_id' => 'alinecarla.rs@gmail.com',
        ]);

        $igor = User::create([
            'name' => 'Igor',
            'email' => 'igorhaf@gmail.com',
            'password' => $password,
            'google_calendar_id' => 'igorhaf@gmail.com',
        ]);

        // ── COMPARTILHADO (a família toda) ───────────────────────────────────
        $inicio = $this->page(null, null, 'note', '🏠', 'Início da Família',
            "Bem-vindos ao Soar da família! 🪁\n\nCada página da barra lateral é uma mini-aplicação:\n\n📅 Agenda da Família — eventos do casal (sincroniza com o Google Calendar)\n✅ Tarefas da Casa — pendências de todo mundo\n💸 Gastos da Família — lançamentos com resumo por categoria (sincroniza com Google Sheets)\n🔐 Cofre — senhas da família (cifradas; nunca saem pelo Telegram)\n💳 Cartões — cadastro dinâmico dos cartões\n👧 Filhos e 🐶 Cachorro — fichas e vacinas\n💊 Remédios — com lembrete na hora certa pelo Telegram\n🥗 Dietas — planos gerados pela IA\n\nFale com o bot @RosendoFrancaBot no Telegram pra registrar e consultar tudo por lá.");

        $this->page(null, null, 'calendar', '📅', 'Agenda da Família');
        $tarefas = $this->page(null, null, 'tasks', '✅', 'Tarefas da Casa');
        $tarefas->taskItems()->createMany([
            ['content' => 'Testar o bot do Telegram (/vincular)', 'position' => 0],
            ['content' => 'Cadastrar os remédios de todo mundo', 'position' => 1],
            ['content' => 'Preencher as fichas dos filhos', 'position' => 2],
        ]);

        $this->page(null, null, 'gastos', '💸', 'Gastos da Família');

        $cofre = $this->page(null, null, 'vault', '🔐', 'Cofre da Família');
        $cofre->vaultEntries()->create([
            'title' => 'Exemplo — Wi-Fi de casa',
            'username' => 'rede-familia',
            'secret' => 'troque-esta-senha',
            'notes' => 'Entrada de exemplo — edite ou exclua.',
            'position' => 0,
        ]);

        $this->page(null, null, 'registro', '💳', 'Cartões', meta: ['template' => [
            ['key' => 'banco', 'label' => 'Banco', 'type' => 'text'],
            ['key' => 'bandeira', 'label' => 'Bandeira', 'type' => 'text'],
            ['key' => 'final', 'label' => 'Final (4 dígitos)', 'type' => 'text'],
            ['key' => 'vencimento_fatura', 'label' => 'Vencimento da fatura', 'type' => 'text'],
            ['key' => 'limite', 'label' => 'Limite', 'type' => 'text'],
        ]]);

        $filhos = $this->page(null, null, 'registro', '👧', 'Fichas dos Filhos', meta: ['template' => [
            ['key' => 'nome', 'label' => 'Nome', 'type' => 'text'],
            ['key' => 'nascimento', 'label' => 'Nascimento', 'type' => 'date'],
            ['key' => 'escola', 'label' => 'Escola', 'type' => 'text'],
            ['key' => 'tamanho_roupa', 'label' => 'Tamanho de roupa', 'type' => 'text'],
            ['key' => 'observacoes', 'label' => 'Observações', 'type' => 'text'],
        ]]);
        foreach (['Filho 1', 'Filho 2', 'Filho 3'] as $i => $nome) {
            $filhos->registroEntries()->create(['data' => ['nome' => $nome], 'position' => $i]);
        }

        $this->page(null, null, 'registro', '🐶', 'Cachorro', meta: ['template' => [
            ['key' => 'item', 'label' => 'Item (vacina/vermífugo/banho)', 'type' => 'text'],
            ['key' => 'data', 'label' => 'Data', 'type' => 'date'],
            ['key' => 'proxima', 'label' => 'Próxima dose', 'type' => 'date'],
            ['key' => 'observacoes', 'label' => 'Observações', 'type' => 'text'],
        ]]);

        $remedios = $this->page(null, null, 'meds', '💊', 'Remédios da Família');
        $remedios->medications()->create([
            'person' => 'Exemplo',
            'name' => 'Vitamina D (exemplo — edite)',
            'dose' => '1 cápsula',
            'schedule_times' => ['08:00'],
            'controlled' => false,
        ]);

        $dietas = $this->page(null, null, 'note', '🥗', 'Dietas',
            'Uma subpágina de dieta por pessoa. Preencha o perfil e clique em "Gerar dieta com IA" — ou peça pelo Telegram: "gera a dieta do Igor".');
        $this->page($dietas->id, null, 'diet', '🥗', 'Dieta do Igor', meta: ['person' => 'Igor']);
        $this->page($dietas->id, null, 'diet', '🥗', 'Dieta da Aline', meta: ['person' => 'Aline']);

        // ── PESSOAL de cada um ───────────────────────────────────────────────
        foreach ([$igor, $aline] as $user) {
            $this->page(null, $user->id, 'calendar', '📅', 'Minha Agenda');
            $this->page(null, $user->id, 'tasks', '✅', 'Minhas Tarefas');
            $this->page(null, $user->id, 'note', '🗒️', 'Notas Rápidas',
                'Suas anotações pelo Telegram ("anota que…") caem aqui.');
        }

        $this->command?->info('Família semeada: Aline + Igor, páginas-aplicação criadas.');
    }

    private function page(?int $parentId, ?int $ownerId, string $kind, string $icon, string $title, ?string $content = null, ?array $meta = null): Page
    {
        static $positions = [];
        $scopeKey = ($ownerId ?? 'shared').'-'.($parentId ?? 'root');
        $positions[$scopeKey] = ($positions[$scopeKey] ?? -1) + 1;

        return Page::create([
            'parent_id' => $parentId,
            'owner_id' => $ownerId,
            'scope' => $ownerId ? Page::SCOPE_PERSONAL : Page::SCOPE_SHARED,
            'kind' => $kind,
            'icon' => $icon,
            'title' => $title,
            'content' => $content,
            'meta' => $meta,
            'position' => $positions[$scopeKey],
        ]);
    }
}
