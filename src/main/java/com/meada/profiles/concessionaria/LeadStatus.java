package com.meada.profiles.concessionaria;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status do funil de um lead de compra da concessionária (camada 8.17) — clone do funil do oficina/
 * eventos (sem itens):
 * <pre>
 *   novo          → em_negociacao, perdido
 *   em_negociacao → fechado, perdido
 *   fechado/perdido → (terminal)
 * </pre>
 * A IA cria o lead em {@code novo} e NÃO move o status — a equipe trabalha o funil no painel.
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Nesta SM o lead NÃO notifica automaticamente (é trabalho interno; o cliente já recebeu a
 * confirmação de recebimento da IA). {@code fechado}/{@code perdido} são registro interno — a
 * comunicação de fechamento é do vendedor (evita "você perdeu o carro" automático).
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/concessionaria/concessionaria-lead-status.ts}
 * (LeadStatusParityTest garante a paridade Java↔TS).
 */
public enum LeadStatus {
    NOVO("novo", "Novo"),
    EM_NEGOCIACAO("em_negociacao", "Em negociação"),
    FECHADO("fechado", "Fechado"),
    PERDIDO("perdido", "Perdido");

    private final String id;
    private final String label;

    LeadStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<LeadStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    public Set<LeadStatus> allowedNext() {
        return switch (this) {
            case NOVO -> Set.of(EM_NEGOCIACAO, PERDIDO);
            case EM_NEGOCIACAO -> Set.of(FECHADO, PERDIDO);
            case FECHADO, PERDIDO -> Set.of();
        };
    }

    public boolean canTransitionTo(LeadStatus next) {
        return allowedNext().contains(next);
    }
}
