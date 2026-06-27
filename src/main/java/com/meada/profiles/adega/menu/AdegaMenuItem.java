package com.meada.profiles.adega.menu;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Item de cardápio do perfil adega (camada 8.4). DTO de saída. Clone de
 * {@link com.meada.profiles.sushi.menu.SushiMenuItem} + a lista de {@code options}
 * (ESCAPADA 2: modifiers). priceCents em centavos é o preço BASE (sem opções); category é o id
 * estável de {@link com.meada.profiles.adega.AdegaCategory}.
 */
public record AdegaMenuItem(
    UUID id,
    String name,
    String description,
    int priceCents,
    String category,
    boolean available,
    Instant createdAt,
    Instant updatedAt,
    List<AdegaMenuOption> options) {
}
