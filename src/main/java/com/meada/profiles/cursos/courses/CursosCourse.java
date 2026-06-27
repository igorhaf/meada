package com.meada.profiles.cursos.courses;

import java.time.Instant;
import java.util.UUID;

/**
 * Curso do tenant cursos (camada 8.20 / perfil cursos) — espelha cursos_courses; análogo ao
 * AcademiaPlan (camada 7.7). {@code monthlyCents} é o valor mensal em centavos. Entra como snapshot
 * na matrícula. {@code category} é texto livre opcional.
 */
public record CursosCourse(
    UUID id,
    String title,
    String category,
    int monthlyCents,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
