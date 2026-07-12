<?php

namespace App\Services;

use App\Models\Medication;
use App\Models\Page;

/**
 * Gera o plano alimentar da página de dieta via Elo e grava em pages.content.
 * Usado pelo scheduler (fila assíncrona do painel) e pelo bot (síncrono).
 */
class DietGenerator
{
    public function __construct(private readonly EloClient $elo)
    {
    }

    public function generate(Page $page): string
    {
        $person = $page->meta['person'] ?? $page->title;
        $restrictions = $page->meta['restrictions'] ?? 'nenhuma informada';
        $goals = $page->meta['goals'] ?? 'alimentação equilibrada';

        // Contexto de remédios da pessoa (interações/horários importam pra dieta)
        $meds = Medication::whereHas('page', fn ($q) => $q->where('kind', 'meds'))
            ->where('active', true)
            ->where('person', 'ilike', "%$person%")
            ->get()
            ->map(fn ($m) => "- {$m->name} ({$m->dose}) às ".implode(', ', $m->schedule_times ?? []))
            ->join("\n");

        $prompt = <<<PROMPT
Monte um plano alimentar SEMANAL (segunda a domingo) em português pra {$person}.

Restrições alimentares e de saúde: {$restrictions}
Objetivos: {$goals}
Medicamentos em uso (considerar horários e possíveis interações alimentares):
{$meds}

Formato: texto simples organizado por dia da semana, com café da manhã, almoço, lanche e jantar.
Seja prático com comida brasileira acessível. Inclua no final uma lista de compras da semana.
NÃO inclua conselhos médicos — apenas o cardápio; recomende confirmar com nutricionista.
PROMPT;

        $plan = $this->elo->chat(
            [['role' => 'user', 'content' => $prompt]],
            system: 'Você é um assistente de planejamento de refeições da família. Gere planos práticos e seguros, respeitando TODAS as restrições informadas.',
            maxTokens: 4096,
        );

        $meta = $page->meta ?? [];
        unset($meta['generate_requested'], $meta['generate_requested_by']);
        $meta['generated_at'] = now()->toIso8601String();

        $page->update(['content' => $plan, 'meta' => $meta]);

        return $plan;
    }
}
