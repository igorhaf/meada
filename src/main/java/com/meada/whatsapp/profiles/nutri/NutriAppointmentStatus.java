package com.meada.whatsapp.profiles.nutri;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma consulta de nutrição (camada 8.0) com as transições cravadas:
 * <pre>
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado  → (terminal)
 *   cancelado  → (terminal)
 *   falta      → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/nutri/nutri-appointment-status.ts}
 * (NutriAppointmentStatusParityTest garante a paridade Java↔TS).
 */
public enum NutriAppointmentStatus {
    AGENDADO("agendado", "Agendado"),
    CONFIRMADO("confirmado", "Confirmado"),
    REALIZADO("realizado", "Realizado"),
    CANCELADO("cancelado", "Cancelado"),
    FALTA("falta", "Falta");

    private final String id;
    private final String label;

    NutriAppointmentStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<NutriAppointmentStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<NutriAppointmentStatus> allowedNext() {
        return switch (this) {
            case AGENDADO -> Set.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> Set.of(REALIZADO, CANCELADO, FALTA);
            case REALIZADO, CANCELADO, FALTA -> Set.of();
        };
    }

    public boolean canTransitionTo(NutriAppointmentStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica.
     * {@code confirmado} (com tipo de consulta/profissional/data/hora) e {@code cancelado} avisam o
     * paciente; {@code agendado}/{@code realizado}/{@code falta} são silenciosos. Texto acolhedor,
     * SEM conteúdo nutricional — ver {@link #notificationText(String, String, String, String)}.
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADO -> "Sua consulta foi cancelada. Para remarcar, é só me chamar.";
            case CONFIRMADO, AGENDADO, REALIZADO, FALTA -> null;
        };
    }

    /**
     * Texto da notificação de CONFIRMAÇÃO, com o tipo de consulta + profissional + data/hora.
     * Para os demais status, devolve {@link #notificationText()}.
     */
    public String notificationText(String typeLabel, String professionalName, String dateLabel, String timeLabel) {
        if (this == CONFIRMADO) {
            return "Consulta confirmada: " + typeLabel + " com " + professionalName + " em "
                + dateLabel + " às " + timeLabel + ". Até lá!";
        }
        return notificationText();
    }
}
