package com.meada.profiles.cursos.payments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pagamento manual mensal do tenant cursos (camada 8.20 / perfil cursos) — espelha cursos_payments;
 * clone do AcademiaPayment (camada 7.7). {@code referenceMonth} é sempre o dia 01 do mês de
 * referência. {@code method} texto livre. UNIQUE (enrollment, referenceMonth).
 */
public record CursosPayment(
    UUID id,
    UUID enrollmentId,
    LocalDate referenceMonth,
    Instant paidAt,
    int amountCents,
    String method,
    String notes) {
}
