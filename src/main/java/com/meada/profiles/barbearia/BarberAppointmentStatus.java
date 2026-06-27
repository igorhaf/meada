package com.meada.profiles.barbearia;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um agendamento de barbearia (camada 8.1) — clone do {@code SalonAppointmentStatus}:
 * <pre>
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado  → (terminal)
 *   cancelado  → (terminal)
 *   falta      → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/barbearia/barber-appointment-status.ts}
 * (BarberAppointmentStatusParityTest garante a paridade Java↔TS).
 */
public enum BarberAppointmentStatus {
    AGENDADO("agendado", "Agendado"),
    CONFIRMADO("confirmado", "Confirmado"),
    REALIZADO("realizado", "Realizado"),
    CANCELADO("cancelado", "Cancelado"),
    FALTA("falta", "Falta");

    private final String id;
    private final String label;

    BarberAppointmentStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<BarberAppointmentStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<BarberAppointmentStatus> allowedNext() {
        return switch (this) {
            case AGENDADO -> Set.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> Set.of(REALIZADO, CANCELADO, FALTA);
            case REALIZADO, CANCELADO, FALTA -> Set.of();
        };
    }

    public boolean canTransitionTo(BarberAppointmentStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica. {@code cancelado}
     * avisa; {@code confirmado} é parametrizado — ver {@link #notificationText(String, String, String)}.
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADO -> "Seu horário foi cancelado. Pra remarcar, é só me chamar.";
            case CONFIRMADO, AGENDADO, REALIZADO, FALTA -> null;
        };
    }

    /**
     * Texto da notificação de CONFIRMAÇÃO, que depende dos dados do agendamento. Para os demais
     * status, devolve {@link #notificationText()}. Texto defensivo (sem promessa de resultado de corte).
     */
    public String notificationText(String dateLabel, String timeLabel, String barberName) {
        if (this == CONFIRMADO) {
            return "Seu horário foi confirmado pra " + dateLabel + " às " + timeLabel
                + " com " + barberName + ". Te esperamos!";
        }
        return notificationText();
    }
}
