package com.meada.whatsapp.profiles.eventos;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma proposta de evento (camada 8.2) com as transições cravadas — espelho do
 * {@link com.meada.whatsapp.profiles.oficina.OsStatus}, adaptado ao funil de uma casa de festas:
 * <pre>
 *   rascunho   → orcada, cancelada
 *   orcada     → aprovada, recusada, cancelada
 *   aprovada   → fechada, cancelada
 *   fechada    → realizada, cancelada
 *   realizada  → (terminal)
 *   recusada   → (terminal)
 *   cancelada  → (terminal)
 * </pre>
 * (rascunho = proposta aberta sem orçamento; orcada = aguardando aprovação do cliente; aprovada =
 * cliente aceitou; fechada = "contrato" fechado/sinal combinado fora do app; realizada = a festa
 * aconteceu.)
 *
 * <p>Transição inválida → 409 invalid_status_transition no controller. A passagem para
 * {@code orcada} exige {@code total_cents > 0} (validada no service → 400 empty_budget).
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/eventos/event-proposal-status.ts}
 * (EventProposalStatusParityTest garante a paridade Java↔TS).
 */
public enum EventProposalStatus {
    RASCUNHO("rascunho", "Rascunho"),
    ORCADA("orcada", "Orçada"),
    APROVADA("aprovada", "Aprovada"),
    RECUSADA("recusada", "Recusada"),
    FECHADA("fechada", "Fechada"),
    REALIZADA("realizada", "Realizada"),
    CANCELADA("cancelada", "Cancelada");

    private final String id;
    private final String label;

    EventProposalStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<EventProposalStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<EventProposalStatus> allowedNext() {
        return switch (this) {
            case RASCUNHO -> Set.of(ORCADA, CANCELADA);
            case ORCADA -> Set.of(APROVADA, RECUSADA, CANCELADA);
            case APROVADA -> Set.of(FECHADA, CANCELADA);
            case FECHADA -> Set.of(REALIZADA, CANCELADA);
            case REALIZADA, RECUSADA, CANCELADA -> Set.of();
        };
    }

    public boolean canTransitionTo(EventProposalStatus next) {
        return allowedNext().contains(next);
    }

    /** Estado terminal — proposta encerrada (preenche closed_at). */
    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /**
     * Estados em que os ITENS da proposta (orçamento E cronograma) NÃO podem mais ser mutados
     * (decisão cravada: trava de estado a partir de 'fechada'). Depois que o contrato fechou, o
     * escopo congela; nos terminais a proposta está encerrada. Mutar item nesses estados → 409
     * proposal_locked. Antes disso (rascunho/orcada/aprovada) o cerimonialista ainda ajusta.
     */
    public boolean itemsLocked() {
        return switch (this) {
            case FECHADA, REALIZADA, RECUSADA, CANCELADA -> true;
            case RASCUNHO, ORCADA, APROVADA -> false;
        };
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica.
     * {@code orcada} (com total), {@code aprovada}, {@code fechada} e {@code recusada} avisam o
     * cliente; {@code rascunho}/{@code realizada}/{@code cancelada} são silenciosos. Texto defensivo,
     * SEM promessa de "evento perfeito". Ver {@link #notificationText(String, String)} para o caso
     * orcada (com total + tipo de evento).
     */
    public String notificationText() {
        return switch (this) {
            case APROVADA -> "Recebemos sua aprovação! Vamos preparar o contrato e os próximos passos do seu evento.";
            case FECHADA -> "Tudo certo, seu evento está confirmado com a gente! Em breve alinhamos os detalhes finais.";
            case RECUSADA -> "Tudo bem, registramos que a proposta não foi adiante. Seguimos à disposição para o que precisar.";
            case ORCADA, RASCUNHO, REALIZADA, CANCELADA -> null;
        };
    }

    /**
     * Texto da notificação de ORÇAMENTO (status orcada), com total formatado + tipo de evento. Para
     * os demais status, devolve {@link #notificationText()}.
     */
    public String notificationText(String eventLabel, String totalLabel) {
        if (this == ORCADA) {
            return "Orçamento do seu " + eventLabel + ": " + totalLabel
                + ". Posso seguir com a reserva? Responda com sim para aprovar ou não para recusar.";
        }
        return notificationText();
    }
}
