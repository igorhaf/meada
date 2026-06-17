package com.meada.whatsapp.profiles.oficina.vehicles;

import java.time.Instant;
import java.util.UUID;

/**
 * Veículo (camada 7.9) — SUB-ENTIDADE do cliente (contact). Espelha os_vehicles. {@code plate} é
 * UNIQUE por company. {@code active=false} arquiva sem perder histórico de OS. {@code notes}
 * administrativo.
 */
public record OsVehicle(
    UUID id,
    UUID contactId,
    String plate,
    String brand,
    String model,
    Integer year,
    String color,
    Integer mileageKm,
    String notes,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
