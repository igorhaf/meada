package com.meada.whatsapp.profiles.nutri.professionals;

import java.time.Instant;
import java.util.UUID;

/**
 * Nutricionista (camada 8.0) — espelha nutri_professionals. {@code specialty} texto livre
 * ("nutrição clínica", "nutrição esportiva"); {@code crn} registro profissional (nullable). O
 * conflito de agenda é por profissional.
 */
public record NutriProfessional(
    UUID id,
    String name,
    String specialty,
    String crn,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
