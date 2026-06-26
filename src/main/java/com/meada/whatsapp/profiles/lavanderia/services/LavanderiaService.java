package com.meada.whatsapp.profiles.lavanderia.services;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Serviço do catálogo do perfil lavanderia (camada 8.10). DTO de saída. Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.catalog.FloriculturaCatalogItem} + os campos da
 * lavanderia: {@code turnaroundDays} (prazo de processamento, ENTRA no MAX que calcula a entrega) e
 * {@code careInstructions} (texto livre informativo). priceCents é o preço POR PEÇA (sem opções);
 * category é o id estável de {@link com.meada.whatsapp.profiles.lavanderia.LavanderiaServiceCategory}.
 */
public record LavanderiaService(
    UUID id,
    String name,
    String description,
    int priceCents,
    String category,
    int turnaroundDays,
    String careInstructions,
    boolean available,
    Instant createdAt,
    Instant updatedAt,
    List<LavanderiaServiceOption> options) {
}
