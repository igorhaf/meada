package com.meada.profiles.nutri.patients;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Paciente (camada 8.0) — SUB-ENTIDADE do contact (nível 1). Espelha nutri_patients. {@code goal} e
 * {@code dietaryRestrictions} são texto livre ADMINISTRATIVO (SEM número nutricional). {@code
 * active=false} arquiva sem perder histórico.
 */
public record NutriPatient(
    UUID id,
    UUID contactId,
    String name,
    String goal,
    String dietaryRestrictions,
    LocalDate birthDate,
    String notes,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
