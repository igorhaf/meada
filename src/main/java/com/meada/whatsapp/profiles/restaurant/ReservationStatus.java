package com.meada.whatsapp.profiles.restaurant;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma reserva (camada 7.3) com as transições válidas cravadas (decisão 2):
 * <pre>
 *   pendente   → confirmada, cancelada
 *   confirmada → realizada, cancelada, no_show
 *   realizada  → (terminal)
 *   cancelada  → (terminal)
 *   no_show    → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/restaurant/reservation-status.ts}
 * (ReservationStatusParityTest garante a paridade Java↔TS).
 */
public enum ReservationStatus {
    PENDENTE("pendente", "Pendente"),
    CONFIRMADA("confirmada", "Confirmada"),
    REALIZADA("realizada", "Realizada"),
    CANCELADA("cancelada", "Cancelada"),
    NO_SHOW("no_show", "Não compareceu");

    private final String id;
    private final String label;

    ReservationStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<ReservationStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status (decisão 2). */
    public Set<ReservationStatus> allowedNext() {
        return switch (this) {
            case PENDENTE -> Set.of(CONFIRMADA, CANCELADA);
            case CONFIRMADA -> Set.of(REALIZADA, CANCELADA, NO_SHOW);
            case REALIZADA, CANCELADA, NO_SHOW -> Set.of();
        };
    }

    public boolean canTransitionTo(ReservationStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound disparada ao ENTRAR neste status (decisão 3). null = não
     * notifica. {@code confirmada}/{@code cancelada} avisam o cliente; {@code realizada}/{@code
     * no_show}/{@code pendente} são silenciosos (quem furou não recebe sermão; pendente ainda não
     * confirmou). O texto de {@code confirmada} é parametrizado por reserva — ver
     * {@link #confirmedText} (esta versão sem parâmetros devolve null para confirmada).
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADA -> "Sua reserva foi cancelada. Pra remarcar, é só chamar.";
            // confirmada precisa dos dados da reserva (data/hora/mesa/pessoas) → confirmedText().
            case CONFIRMADA, REALIZADA, NO_SHOW, PENDENTE -> null;
        };
    }

    /**
     * Texto da notificação de CONFIRMAÇÃO (decisão 3), que depende dos dados da reserva. Para os
     * demais status, devolve {@link #notificationText()} (independe da reserva). Retorna null
     * quando o status não notifica.
     */
    public String notificationText(String dateLabel, String timeLabel, String tableLabel, int numPeople) {
        if (this == CONFIRMADA) {
            return "Sua reserva foi confirmada pra " + dateLabel + " às " + timeLabel
                + " (" + tableLabel + ", " + numPeople + " pessoa" + (numPeople == 1 ? "" : "s")
                + "). Te esperamos!";
        }
        return notificationText();
    }
}
