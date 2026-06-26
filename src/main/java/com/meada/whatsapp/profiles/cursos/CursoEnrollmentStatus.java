package com.meada.whatsapp.profiles.cursos;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma matrícula de curso (camada 8.20 / perfil cursos) — clone do AcademiaMembershipStatus
 * (camada 7.7), mas com 4 estados (academia tem 3). Transições cravadas:
 * <pre>
 *   ativa     → trancada, concluida, cancelada
 *   trancada  → ativa, concluida, cancelada
 *   concluida → (terminal)
 *   cancelada → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller. {@code concluida} E {@code
 * cancelada} materializam end_date.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/cursos/curso-enrollment-status.ts}
 * (CursoEnrollmentStatusParityTest garante a paridade Java↔TS).
 */
public enum CursoEnrollmentStatus {
    ATIVA("ativa", "Ativa"),
    TRANCADA("trancada", "Trancada"),
    CONCLUIDA("concluida", "Concluída"),
    CANCELADA("cancelada", "Cancelada");

    private final String id;
    private final String label;

    CursoEnrollmentStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<CursoEnrollmentStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<CursoEnrollmentStatus> allowedNext() {
        return switch (this) {
            case ATIVA -> Set.of(TRANCADA, CONCLUIDA, CANCELADA);
            case TRANCADA -> Set.of(ATIVA, CONCLUIDA, CANCELADA);
            case CONCLUIDA -> Set.of();
            case CANCELADA -> Set.of();
        };
    }

    public boolean canTransitionTo(CursoEnrollmentStatus next) {
        return allowedNext().contains(next);
    }

    /** {@code concluida} e {@code cancelada} são terminais e materializam end_date. */
    public boolean isTerminal() {
        return this == CONCLUIDA || this == CANCELADA;
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica. {@code ativa}
     * (boas-vindas, com o curso), {@code concluida} (parabéns) e {@code cancelada} (despedida) avisam;
     * {@code trancada} é silenciosa. Texto defensivo, SEM promessa de resultado.
     */
    public String notificationText(String studentName, String courseTitle) {
        return switch (this) {
            case ATIVA -> "Sua matrícula foi confirmada no curso " + courseTitle
                + ". Bons estudos e qualquer dúvida é só me chamar!";
            case CONCLUIDA -> "Parabéns por concluir o curso " + courseTitle
                + "! Foi um prazer ter você com a gente.";
            case CANCELADA -> "Sua matrícula foi cancelada. Pra voltar a estudar com a gente, é só me chamar.";
            case TRANCADA -> null;
        };
    }
}
