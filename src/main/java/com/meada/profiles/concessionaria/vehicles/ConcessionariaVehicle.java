package com.meada.profiles.concessionaria.vehicles;

import java.time.Instant;
import java.util.UUID;

/**
 * Veículo do ESTOQUE da concessionaria (camada 8.17) — espelha concessionaria_vehicles. É ITEM DE
 * ESTOQUE com identidade única e CICLO próprio: status disponivel→reservado→vendido. Pertence à
 * company (NÃO a um contact). {@code priceCents} é o preço de referência (snapshotado no lead).
 * {@code modelYear}/{@code mileageKm}/{@code color}/{@code fuel}/{@code transmission}/{@code plate}/
 * {@code photoUrl}/{@code description} são opcionais. Foto é LINK (sem upload).
 */
public record ConcessionariaVehicle(
    UUID id,
    UUID companyId,
    String brand,
    String model,
    Integer modelYear,
    Integer mileageKm,
    int priceCents,
    String color,
    String fuel,
    String transmission,
    String plate,
    String photoUrl,
    String description,
    String status,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    Instant statusUpdatedAt) {
}
