package com.meada.profiles.concessionaria.testdrives;

import java.time.Instant;
import java.util.UUID;

/**
 * Test-drive (camada 8.17) — espelha concessionaria_test_drives. Clone do dental_appointments com
 * vehicle_id + salesperson_id. {@code conversationId} é nullable (criado manualmente pelo tenant não
 * tem WhatsApp). {@code durationMinutes} é snapshot do config; {@code endAt} é materializado no INSERT
 * (start_at + durationMinutes). {@code vehicleBrand}/{@code vehicleModel}/{@code vehicleYear} são
 * SNAPSHOTS. {@code notes} é ADMINISTRATIVO.
 */
public record ConcessionariaTestDrive(
    UUID id,
    UUID companyId,
    UUID vehicleId,
    UUID salespersonId,
    UUID conversationId,
    UUID contactId,
    String customerName,
    String vehicleBrand,
    String vehicleModel,
    Integer vehicleYear,
    Instant startAt,
    int durationMinutes,
    Instant endAt,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
