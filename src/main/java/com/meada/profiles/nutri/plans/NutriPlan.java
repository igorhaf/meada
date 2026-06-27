package com.meada.profiles.nutri.plans;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Plano alimentar (camada 8.0) — SUB-ENTIDADE do paciente (nível 2). Espelha nutri_plans. O {@code
 * body} (markdown livre) é conduta clínica escrita SÓ pelo profissional no painel — a IA ENTREGA o
 * texto EXATO, NUNCA edita/resume/adapta. {@code status} ativo|arquivado; no máximo 1 ativo por
 * paciente (índice parcial UNIQUE; novo ativo arquiva o anterior no service).
 */
public record NutriPlan(
    UUID id,
    UUID patientId,
    UUID professionalId,
    String professionalName,
    String title,
    String body,
    LocalDate startsOn,
    LocalDate endsOn,
    String status,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
