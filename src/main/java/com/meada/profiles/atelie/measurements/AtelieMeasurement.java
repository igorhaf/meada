package com.meada.profiles.atelie.measurements;

import java.time.Instant;
import java.util.UUID;

/**
 * Medida do CLIENTE (contato) do tenant atelie (onda 2, backlog #9). Linha label+valor LIVRE
 * (costura/arte/design medem coisas diferentes), keyed pelo CONTATO — reuso automático na recompra.
 * ADMINISTRATIVA do painel: a IA NÃO recebe medidas no contexto (trava: nunca confirma medida não
 * cravada pela equipe).
 */
public record AtelieMeasurement(
    UUID id,
    UUID companyId,
    UUID contactId,
    String label,
    String value,
    Instant createdAt,
    Instant updatedAt) {
}
