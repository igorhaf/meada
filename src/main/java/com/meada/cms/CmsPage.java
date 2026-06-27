package com.meada.cms;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma PÁGINA do CMS (SM-N) — espelha cms_pages (N por company). {@code blocks} é o array JSONB
 * ordenado de {@code {id, type, props}}. {@code isHome} marca a página que responde em /p/{slug}; as
 * demais em /p/{slug}/{pageSlug}. {@code position} = ordem na navegação. {@code published} por página.
 */
public record CmsPage(
    UUID id,
    UUID companyId,
    String pageSlug,
    String title,
    JsonNode blocks,
    boolean isHome,
    int position,
    boolean published,
    Instant createdAt,
    Instant updatedAt) {
}
