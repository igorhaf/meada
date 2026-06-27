package com.meada.profiles.barbearia.queue;

import java.time.Instant;
import java.util.UUID;

/**
 * Ticket da FILA DE WALK-IN da barbearia (camada 8.1) — espelha barber_queue_tickets.
 *
 * <p>A ESCAPADA estrutural desta SM: {@code position} e {@code etaMinutes} NÃO são colunas
 * persistidas — são DERIVADOS no momento da leitura ({@link BarberQueueService}). No banco só existem
 * o {@code status} e o {@code enqueuedAt} (a âncora de ordem). Atender/desistir de quem está à frente
 * RECOMPUTA a posição sem nenhum UPDATE de reordenação.
 *
 * <p>{@code barberId} null = "qualquer barbeiro" (fila geral). {@code barberName} snapshot nullable.
 * {@code serviceName}/{@code durationMinutes} são snapshots (o ETA usa a duração).
 */
public record BarberQueueTicket(
    UUID id,
    UUID barberId,
    String barberName,
    UUID serviceId,
    String serviceName,
    int durationMinutes,
    UUID conversationId,
    UUID contactId,
    String guestName,
    String guestPhone,
    String status,
    Instant enqueuedAt,
    Instant calledAt,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt,
    // --- campos DERIVADOS (não persistidos; preenchidos na leitura para tickets 'aguardando') ---
    Integer position,
    Integer etaMinutes) {

    /** Cópia do ticket com a posição + ETA derivados preenchidos (usado na leitura). */
    public BarberQueueTicket withPosition(Integer position, Integer etaMinutes) {
        return new BarberQueueTicket(id, barberId, barberName, serviceId, serviceName, durationMinutes,
            conversationId, contactId, guestName, guestPhone, status, enqueuedAt, calledAt, notes,
            createdAt, statusUpdatedAt, position, etaMinutes);
    }
}
