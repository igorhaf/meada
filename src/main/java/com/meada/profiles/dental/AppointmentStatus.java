package com.meada.profiles.dental;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma consulta (camada 7.4) com as transições válidas cravadas (decisão 1):
 * <pre>
 *   agendada   → confirmada, cancelada
 *   confirmada → realizada, cancelada, falta
 *   realizada  → (terminal)
 *   cancelada  → (terminal)
 *   falta      → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/dental/appointment-status.ts}
 * (AppointmentStatusParityTest garante a paridade Java↔TS).
 */
public enum AppointmentStatus {
    AGENDADA("agendada", "Agendada"),
    CONFIRMADA("confirmada", "Confirmada"),
    REALIZADA("realizada", "Realizada"),
    CANCELADA("cancelada", "Cancelada"),
    FALTA("falta", "Falta");

    private final String id;
    private final String label;

    AppointmentStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<AppointmentStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status (decisão 1). */
    public Set<AppointmentStatus> allowedNext() {
        return switch (this) {
            case AGENDADA -> Set.of(CONFIRMADA, CANCELADA);
            case CONFIRMADA -> Set.of(REALIZADA, CANCELADA, FALTA);
            case REALIZADA, CANCELADA, FALTA -> Set.of();
        };
    }

    public boolean canTransitionTo(AppointmentStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status (decisão 2). null = não notifica.
     * {@code confirmada} (com data/hora) e {@code cancelada} avisam o paciente; {@code agendada}
     * (inicial — o paciente já viu no chat), {@code realizada} e {@code falta} (quem furou não
     * recebe sermão) são silenciosos. O texto de {@code confirmada} é parametrizado por consulta
     * — ver {@link #notificationText(String, String)}.
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADA -> "Sua consulta foi cancelada. Pra reagendar, é só me chamar.";
            // confirmada precisa de data/hora → notificationText(dateLabel, timeLabel).
            case CONFIRMADA, AGENDADA, REALIZADA, FALTA -> null;
        };
    }

    /**
     * Texto da notificação de CONFIRMAÇÃO (decisão 2), que depende dos dados da consulta. Para os
     * demais status, devolve {@link #notificationText()}. Sem promessa clínica (texto defensivo).
     */
    public String notificationText(String dateLabel, String timeLabel) {
        if (this == CONFIRMADA) {
            return "Sua consulta foi confirmada pra " + dateLabel + " às " + timeLabel + ". Te esperamos!";
        }
        return notificationText();
    }
}
