package com.meada.profiles.oficina;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma ordem de serviço (camada 7.9) com as transições cravadas:
 * <pre>
 *   aberta       → orcada, cancelada
 *   orcada       → aprovada, recusada, cancelada
 *   aprovada     → em_execucao, cancelada
 *   em_execucao  → concluida, cancelada
 *   concluida    → entregue
 *   recusada     → (terminal)
 *   entregue     → (terminal)
 *   cancelada    → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller. A passagem para
 * {@code orcada} exige {@code total_cents > 0} (validada no service → 400 empty_budget).
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/oficina/os-status.ts}
 * (OsStatusParityTest garante a paridade Java↔TS).
 */
public enum OsStatus {
    ABERTA("aberta", "Aberta"),
    ORCADA("orcada", "Orçada"),
    APROVADA("aprovada", "Aprovada"),
    RECUSADA("recusada", "Recusada"),
    EM_EXECUCAO("em_execucao", "Em execução"),
    CONCLUIDA("concluida", "Concluída"),
    ENTREGUE("entregue", "Entregue"),
    CANCELADA("cancelada", "Cancelada");

    private final String id;
    private final String label;

    OsStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<OsStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<OsStatus> allowedNext() {
        return switch (this) {
            case ABERTA -> Set.of(ORCADA, CANCELADA);
            case ORCADA -> Set.of(APROVADA, RECUSADA, CANCELADA);
            case APROVADA -> Set.of(EM_EXECUCAO, CANCELADA);
            case EM_EXECUCAO -> Set.of(CONCLUIDA, CANCELADA);
            case CONCLUIDA -> Set.of(ENTREGUE);
            case RECUSADA, ENTREGUE, CANCELADA -> Set.of();
        };
    }

    public boolean canTransitionTo(OsStatus next) {
        return allowedNext().contains(next);
    }

    /** Estado terminal — OS encerrada (preenche closed_at). */
    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /**
     * Estados em que os ITENS da OS NÃO podem mais ser mutados (decisão: trava de estado).
     * Em execução o orçamento já foi aprovado e o trabalho começou; nos terminais a OS está
     * encerrada. Mutar item nesses estados → 409 order_locked.
     */
    public boolean itemsLocked() {
        return switch (this) {
            case EM_EXECUCAO, CONCLUIDA, ENTREGUE, RECUSADA, CANCELADA -> true;
            case ABERTA, ORCADA, APROVADA -> false;
        };
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica.
     * {@code orcada} (com total), {@code aprovada}, {@code concluida} e {@code entregue} avisam o
     * cliente; {@code aberta}/{@code recusada}/{@code em_execucao}/{@code cancelada} são silenciosos.
     * Texto defensivo, SEM diagnóstico/promessa de prazo. Ver
     * {@link #notificationText(String, String)} para o caso orcada (com total + veículo).
     */
    public String notificationText() {
        return switch (this) {
            case APROVADA -> "Recebemos sua aprovação! Já vamos iniciar o serviço e te avisamos quando estiver pronto.";
            case CONCLUIDA -> "Seu veículo está pronto! Pode combinar a retirada com a gente.";
            case ENTREGUE -> "Veículo entregue. Obrigado pela confiança — qualquer coisa, é só chamar!";
            case ORCADA, ABERTA, RECUSADA, EM_EXECUCAO, CANCELADA -> null;
        };
    }

    /**
     * Texto da notificação de ORÇAMENTO (status orcada), com total formatado + identificação do
     * veículo. Para os demais status, devolve {@link #notificationText()}.
     */
    public String notificationText(String vehicleLabel, String totalLabel) {
        if (this == ORCADA) {
            return "Orçamento do " + vehicleLabel + ": " + totalLabel
                + ". Posso seguir com o serviço? Responda com sim para aprovar ou não para recusar.";
        }
        return notificationText();
    }
}
