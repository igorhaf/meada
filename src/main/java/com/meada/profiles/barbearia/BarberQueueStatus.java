package com.meada.profiles.barbearia;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um ticket da FILA DE WALK-IN da barbearia (camada 8.1) — máquina NOVA desta SM:
 * <pre>
 *   aguardando → chamado, desistiu, expirado
 *   chamado    → atendido, desistiu
 *   atendido   → (terminal)
 *   desistiu   → (terminal)
 *   expirado   → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller. A transição aguardando→chamado é
 * AÇÃO HUMANA no painel (a IA só enfileira e informa; espelho do "cancelamento bloqueado por IA" do
 * dental) e dispara a notificação "chegou sua vez". NÃO há callNext automático nesta SM.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/barbearia/barber-queue-status.ts}
 * (BarberQueueStatusParityTest garante a paridade Java↔TS).
 */
public enum BarberQueueStatus {
    AGUARDANDO("aguardando", "Aguardando"),
    CHAMADO("chamado", "Chamado"),
    ATENDIDO("atendido", "Atendido"),
    DESISTIU("desistiu", "Desistiu"),
    EXPIRADO("expirado", "Expirado");

    private final String id;
    private final String label;

    BarberQueueStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<BarberQueueStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<BarberQueueStatus> allowedNext() {
        return switch (this) {
            case AGUARDANDO -> Set.of(CHAMADO, DESISTIU, EXPIRADO);
            case CHAMADO -> Set.of(ATENDIDO, DESISTIU);
            case ATENDIDO, DESISTIU, EXPIRADO -> Set.of();
        };
    }

    public boolean canTransitionTo(BarberQueueStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica. SÓ {@code chamado}
     * notifica ("chegou sua vez") — é a notificação CRÍTICA do walk-in. É parametrizada com o nome do
     * barbeiro — ver {@link #notificationText(String)}. Os demais (atendido/desistiu/expirado/aguardando)
     * são silenciosos.
     */
    public String notificationText() {
        return null;
    }

    /**
     * Texto da notificação de CHAMADA, com o nome do barbeiro (pode ser null = "qualquer barbeiro"). Para
     * os demais status, devolve {@link #notificationText()} (null). Texto defensivo.
     */
    public String notificationText(String barberName) {
        if (this == CHAMADO) {
            if (barberName == null || barberName.isBlank()) {
                return "Chegou a sua vez! Pode vir que já vamos te atender. 💈";
            }
            return "Chegou a sua vez! Procure o barbeiro " + barberName + ". 💈";
        }
        return notificationText();
    }
}
