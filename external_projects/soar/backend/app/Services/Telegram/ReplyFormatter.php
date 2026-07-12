<?php

namespace App\Services\Telegram;

/**
 * Confirmação DETERMINÍSTICA do resultado de uma ação.
 *
 * Por que existe: a resposta natural do modelo custava uma 2ª chamada ao Elo
 * (~8-10s a mais). O usuário achava que a mensagem não tinha chegado e reenviava,
 * duplicando a ação. Sucesso agora responde na hora, sem passar pela IA de novo;
 * só erro/ambiguidade volta pro modelo (que precisa perguntar algo).
 */
class ReplyFormatter
{
    /** @param array<string, mixed> $result */
    public function format(string $tool, array $result): ?string
    {
        if (isset($result['erro'])) {
            return null; // deixa o modelo explicar/perguntar
        }

        return match ($tool) {
            'criar_tarefa' => $this->criarTarefa($result),
            'concluir_tarefa' => "✅ Concluída: {$result['concluida']}",
            'listar_tarefas' => $this->listarTarefas($result),
            'criar_evento' => $this->criarEvento($result),
            'listar_agenda' => $this->listarAgenda($result),
            'registrar_gasto' => "💸 Gasto anotado: {$result['gasto']} — {$result['valor']}",
            'resumo_gastos' => $this->resumoGastos($result),
            'registrar_tomada' => '💊 '.$result['registrado']
                .($result['estoque_restante'] !== null ? " (restam {$result['estoque_restante']})" : ''),
            'listar_remedios' => $this->listarRemedios($result),
            'adicionar_registro' => "📋 Adicionado em {$result['pagina']}: {$result['item']}",
            'criar_pagina_registro' => "📋 Criei “{$result['pagina']}” dentro de {$result['dentro_de']} com os campos: "
                .implode(', ', $result['campos']),
            'anotar' => "🗒️ Anotado em {$result['pagina']}.",
            'recado' => "💌 Recado entregue pra {$result['para']} no Telegram!",
            default => null, // gerar_dieta/buscar_paginas: melhor o modelo redigir
        };
    }

    private function criarTarefa(array $r): string
    {
        $onde = ($r['escopo'] ?? '') === 'pessoal' ? 'sua lista pessoal' : 'lista da família';
        $prazo = $r['prazo'] ? " (até {$r['prazo']})" : '';

        return "✅ Tarefa criada em {$onde}: {$r['tarefa']}{$prazo}";
    }

    private function listarTarefas(array $r): string
    {
        if (empty($r['pendentes'])) {
            return '🎉 Nenhuma tarefa pendente!';
        }

        $linhas = array_map(function ($t) {
            $prazo = $t['prazo'] ? " — até {$t['prazo']}" : '';

            return "• {$t['tarefa']} ({$t['responsavel']}){$prazo}";
        }, $r['pendentes']);

        return "✅ Tarefas pendentes:\n".implode("\n", $linhas);
    }

    private function criarEvento(array $r): string
    {
        return "📅 {$r['evento']} — {$r['quando']}\nAgenda {$r['agenda']}.";
    }

    private function listarAgenda(array $r): string
    {
        if (empty($r['eventos'])) {
            return '📅 Nada na agenda nesse período.';
        }

        $linhas = array_map(
            fn ($e) => "• {$e['inicio']} — {$e['titulo']} ({$e['agenda']})",
            $r['eventos'],
        );

        return "📅 Agenda:\n".implode("\n", $linhas);
    }

    private function resumoGastos(array $r): string
    {
        $linhas = ["💸 Gastos de {$r['mes']}: {$r['total']} ({$r['quantidade']} lançamentos)"];
        foreach ($r['por_categoria'] as $categoria => $valor) {
            $linhas[] = "• {$categoria}: {$valor}";
        }

        return implode("\n", $linhas);
    }

    private function listarRemedios(array $r): string
    {
        if (empty($r['remedios'])) {
            return '💊 Nenhum remédio cadastrado.';
        }

        $linhas = array_map(function ($m) {
            $horarios = implode(', ', $m['horarios'] ?? []);
            $dose = $m['dose'] ? " {$m['dose']}" : '';

            return "• {$m['pessoa']}: {$m['nome']}{$dose} — {$horarios}"
                .($m['controlado'] ? ' ⚠️ controlado' : '');
        }, $r['remedios']);

        return "💊 Remédios:\n".implode("\n", $linhas);
    }
}
