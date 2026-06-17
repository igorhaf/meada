package com.meada.whatsapp.profiles.salon;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um agendamento de salão (camada 7.5) com as transições cravadas (decisão 2):
 * <pre>
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado  → (terminal)
 *   cancelado  → (terminal)
 *   falta      → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/salon/salon-appointment-status.ts}
 * (SalonAppointmentStatusParityTest garante a paridade Java↔TS).
 */
public enum SalonAppointmentStatus {
    AGENDADO("agendado", "Agendado"),
    CONFIRMADO("confirmado", "Confirmado"),
    REALIZADO("realizado", "Realizado"),
    CANCELADO("cancelado", "Cancelado"),
    FALTA("falta", "Falta");

    private final String id;
    private final String label;

    SalonAppointmentStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<SalonAppointmentStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status (decisão 2). */
    public Set<SalonAppointmentStatus> allowedNext() {
        return switch (this) {
            case AGENDADO -> Set.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> Set.of(REALIZADO, CANCELADO, FALTA);
            case REALIZADO, CANCELADO, FALTA -> Set.of();
        };
    }

    public boolean canTransitionTo(SalonAppointmentStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status (decisão 3). null = não notifica.
     * {@code confirmado} (com data/hora/profissional) e {@code cancelado} avisam o cliente;
     * {@code agendado}/{@code realizado}/{@code falta} são silenciosos. O confirmado é parametrizado
     * — ver {@link #notificationText(String, String, String)}.
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADO -> "Seu agendamento foi cancelado. Pra reagendar, é só me chamar.";
            case CONFIRMADO, AGENDADO, REALIZADO, FALTA -> null;
        };
    }

    /**
     * Texto da notificação de CONFIRMAÇÃO (decisão 3), que depende dos dados do agendamento. Para os
     * demais status, devolve {@link #notificationText()}. Texto defensivo (sem promessa estética).
     */
    public String notificationText(String dateLabel, String timeLabel, String professionalName) {
        if (this == CONFIRMADO) {
            return "Seu agendamento foi confirmado pra " + dateLabel + " às " + timeLabel
                + " com " + professionalName + ". Te esperamos!";
        }
        return notificationText();
    }
}
