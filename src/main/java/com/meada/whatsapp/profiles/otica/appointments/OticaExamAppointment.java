package com.meada.whatsapp.profiles.otica.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Exame de vista (camada 8.12, perfil otica FLUXO A) — espelha {@code otica_exam_appointments}. O
 * cliente é o CONTACT (sem sub-entidade de paciente): {@code customerName} é snapshot do contato.
 * {@code professionalName} é snapshot do optometrista. {@code durationMinutes} é snapshot do config.
 * {@code conversationId} é nullable (exame manual sem WhatsApp). {@code notes} é ADMINISTRATIVO.
 * Espelho do {@code FotografiaSessionAppointment} (conflito por professional_id) sem
 * pacote/entrega.
 */
public record OticaExamAppointment(
    UUID id,
    UUID professionalId,
    String professionalName,
    UUID conversationId,
    UUID contactId,
    String customerName,
    Instant startAt,
    Instant endAt,
    int durationMinutes,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
