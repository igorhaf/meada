package com.meada.profiles.fotografia.professionals;

import java.time.Instant;
import java.util.UUID;

/**
 * Fotógrafo/operador (camada 8.16 / perfil fotografia) — espelha fotografia_professionals.
 * {@code specialty} texto livre ("fotografia social", "vídeo", "ensaio"). O conflito de agenda da
 * sessão é por profissional. Espelho do DermatologiaProfessional SEM crm_rqe.
 */
public record FotografiaProfessional(
    UUID id,
    String name,
    String specialty,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
