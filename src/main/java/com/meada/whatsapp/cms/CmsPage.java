package com.meada.whatsapp.cms;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Página pessoal (CMS) de um tenant (SM-M) — espelha cms_pages (1:1 com company). {@code blocks} é
 * o array JSONB ordenado de {@code {id, type, props}} carregado como {@link JsonNode} (o service
 * valida o shape; o controller serializa direto). {@code domain} é o host próprio (nullable).
 */
public record CmsPage(
    UUID companyId,
    String slug,
    String domain,
    boolean published,
    String title,
    JsonNode blocks,
    Instant createdAt,
    Instant updatedAt) {
}
