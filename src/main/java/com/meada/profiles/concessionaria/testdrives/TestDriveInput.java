package com.meada.profiles.concessionaria.testdrives;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrada para criar um test-drive (camada 8.17) — usada pelo POST manual e pelo
 * {@link TestDriveConfirmHandler}. {@code conversationId} nullable (POST manual sem WhatsApp).
 */
public record TestDriveInput(
    UUID vehicleId,
    UUID salespersonId,
    UUID conversationId,
    UUID contactId,
    Instant startAt,
    String notes) {
}
