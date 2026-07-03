package com.meada.profiles.casamento.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * Item do catálogo do tenant casamento (onda 1, backlog #3): PACOTE (Prata/Ouro/Diamante) ou
 * ADICIONAL (day-use, cabine de fotos, brunch...). Fonte do AUTOFILL do editor de orçamento (o item
 * da proposta continua snapshot texto) e da apresentação da IA (preço DO CATÁLOGO + upsell de UMA
 * sugestão de adicional).
 */
public record WeddingCatalogItem(
    UUID id,
    UUID companyId,
    String name,
    String kind,
    String description,
    int priceCents,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
