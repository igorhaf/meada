package com.meada.whatsapp.profiles.otica;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um exame de vista (camada 8.12, perfil otica FLUXO A) — clone de
 * {@link com.meada.whatsapp.profiles.dental.AppointmentStatus} (forma canTransitionTo +
 * notificationText). Transições (espelho dental, masculino-neutro):
 * <pre>
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado  → (terminal)
 *   cancelado  → (terminal)
 *   falta      → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/otica/otica-exam-status.ts}
 * (OticaExamStatusParityTest garante a paridade Java↔TS).
 */
public enum OticaExamStatus {
    AGENDADO("agendado", "Agendado"),
    CONFIRMADO("confirmado", "Confirmado"),
    REALIZADO("realizado", "Realizado"),
    CANCELADO("cancelado", "Cancelado"),
    FALTA("falta", "Falta");

    private final String id;
    private final String label;

    OticaExamStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<OticaExamStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<OticaExamStatus> allowedNext() {
        return switch (this) {
            case AGENDADO -> Set.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> Set.of(REALIZADO, CANCELADO, FALTA);
            case REALIZADO, CANCELADO, FALTA -> Set.of();
        };
    }

    public boolean canTransitionTo(OticaExamStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica.
     * {@code confirmado} (com data/hora) e {@code cancelado} avisam o cliente; {@code agendado}
     * (inicial — o cliente já viu no chat), {@code realizado} e {@code falta} (quem furou não recebe
     * sermão) são silenciosos. O texto é DEFENSIVO — SEM promessa clínica (ótica não diagnostica).
     * O texto de {@code confirmado} é parametrizado — ver {@link #notificationText(String, String)}.
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADO -> "Seu exame de vista foi cancelado. Pra reagendar, é só me chamar.";
            // confirmado precisa de data/hora → notificationText(dateLabel, timeLabel).
            case CONFIRMADO, AGENDADO, REALIZADO, FALTA -> null;
        };
    }

    /**
     * Texto da notificação de CONFIRMAÇÃO, que depende dos dados do exame. Para os demais status,
     * devolve {@link #notificationText()}. Sem promessa clínica (texto defensivo).
     */
    public String notificationText(String dateLabel, String timeLabel) {
        if (this == CONFIRMADO) {
            return "Seu exame de vista foi confirmado pra " + dateLabel + " às " + timeLabel + ". Te esperamos!";
        }
        return notificationText();
    }
}
