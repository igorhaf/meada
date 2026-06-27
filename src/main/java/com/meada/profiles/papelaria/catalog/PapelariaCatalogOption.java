package com.meada.profiles.papelaria.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * Opção/personalização (modifier) de um item do catálogo papelaria (camada 8.15 / perfil papelaria).
 * Cada linha é UMA opção de UM grupo ({@code groupLabel} agrupa no app: "Papel", "Acabamento", "Cor",
 * "Tamanho"). DTO de saída. {@code priceDeltaCents} soma ao preço base no pedido (recálculo no
 * backend). Clone de {@code com.meada.profiles.padaria.menu.PadariaMenuOption} (camada 8.8) —
 * menu→catalog: a coluna FK é {@code catalog_item_id}.
 */
public record PapelariaCatalogOption(
    UUID id,
    UUID catalogItemId,
    String groupLabel,
    String optionLabel,
    int priceDeltaCents,
    boolean available,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt) {
}
