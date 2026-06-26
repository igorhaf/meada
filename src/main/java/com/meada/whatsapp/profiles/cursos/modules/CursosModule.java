package com.meada.whatsapp.profiles.cursos.modules;

import java.time.Instant;
import java.util.UUID;

/**
 * Módulo ordenado de um curso (camada 8.20 / perfil cursos, ESCAPADA 1) — espelha cursos_modules.
 * Substitui o {@code AcademiaClass} (camada 7.7): em vez de uma aula semanal recorrente, é um marco
 * na TRILHA do curso. {@code position} = ordem (0=primeiro). {@code content} é o material entregue
 * VERBATIM pela IA (read-only, via {@code <entrega_modulo>}). UNIQUE(course_id, position).
 */
public record CursosModule(
    UUID id,
    UUID courseId,
    int position,
    String title,
    String content,
    Instant createdAt,
    Instant updatedAt) {
}
