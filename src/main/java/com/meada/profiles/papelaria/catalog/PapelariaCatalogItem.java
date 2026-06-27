package com.meada.profiles.papelaria.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Item de catálogo do perfil papelaria (camada 8.15 / perfil papelaria). DTO de saída. Clone de
 * {@code com.meada.profiles.padaria.menu.PadariaMenuItem} (camada 8.8) — menu→catalog (a
 * tabela é {@code papelaria_catalog_items}) — com {@code madeToOrder} + {@code leadTimeDays} nullable
 * (que sobrepõe o default da config) + {@code specs} (gramatura/material — texto livre informativo, no
 * lugar do {@code allergens} da padaria). priceCents em centavos é o preço BASE UNITÁRIO (sem opções,
 * antes da tiragem); category é o id estável de
 * {@link com.meada.profiles.papelaria.PapelariaCategory}.
 */
public record PapelariaCatalogItem(
    UUID id,
    String name,
    String description,
    int priceCents,
    String category,
    boolean madeToOrder,
    Integer leadTimeDays,
    String specs,
    boolean available,
    Instant createdAt,
    Instant updatedAt,
    List<PapelariaCatalogOption> options) {
}
