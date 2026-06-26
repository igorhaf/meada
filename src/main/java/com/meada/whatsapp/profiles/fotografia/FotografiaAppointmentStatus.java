package com.meada.whatsapp.profiles.fotografia;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma sessão de fotografia (camada 8.16 / perfil fotografia) com as transições cravadas
 * (FEMININO):
 * <pre>
 *   agendada   → confirmada, cancelada
 *   confirmada → realizada, cancelada, falta
 *   realizada  → entregue
 *   entregue   → (terminal)
 *   cancelada  → (terminal)
 *   falta      → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Espelho do DermatologiaAppointmentStatus, com gênero feminino, ESTENDIDO: dermatologia terminava
 * em {@code realizada}; fotografia ACRESCENTA {@code entregue} APÓS realizada (o material foi
 * entregue). Espelhado 1:1 por {@code frontend/profiles/fotografia/fotografia-appointment-status.ts}
 * (FotografiaAppointmentStatusParityTest garante a paridade Java↔TS).
 */
public enum FotografiaAppointmentStatus {
    AGENDADA("agendada", "Agendada"),
    CONFIRMADA("confirmada", "Confirmada"),
    REALIZADA("realizada", "Realizada"),
    ENTREGUE("entregue", "Entregue"),
    CANCELADA("cancelada", "Cancelada"),
    FALTA("falta", "Falta");

    private final String id;
    private final String label;

    FotografiaAppointmentStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<FotografiaAppointmentStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<FotografiaAppointmentStatus> allowedNext() {
        return switch (this) {
            case AGENDADA -> Set.of(CONFIRMADA, CANCELADA);
            case CONFIRMADA -> Set.of(REALIZADA, CANCELADA, FALTA);
            case REALIZADA -> Set.of(ENTREGUE);
            case ENTREGUE, CANCELADA, FALTA -> Set.of();
        };
    }

    public boolean canTransitionTo(FotografiaAppointmentStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica.
     * {@code confirmada} (com pacote/profissional/data/hora) e {@code cancelada} avisam o cliente;
     * {@code agendada}/{@code realizada}/{@code entregue}/{@code falta} são silenciosos —
     * ver {@link #notificationText(String, String, String, String)}.
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADA -> "Sua sessão foi cancelada. Para remarcar, é só me chamar.";
            case CONFIRMADA, AGENDADA, REALIZADA, ENTREGUE, FALTA -> null;
        };
    }

    /**
     * Texto da notificação de CONFIRMAÇÃO, com o pacote + profissional + data/hora.
     * Para os demais status, devolve {@link #notificationText()}.
     */
    public String notificationText(String packageLabel, String professionalName, String dateLabel, String timeLabel) {
        if (this == CONFIRMADA) {
            return "Sessão confirmada: " + packageLabel + " com " + professionalName + " em "
                + dateLabel + " às " + timeLabel + ". Até lá!";
        }
        return notificationText();
    }
}
