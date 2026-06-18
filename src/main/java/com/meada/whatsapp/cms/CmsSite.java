package com.meada.whatsapp.cms;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Config do SITE (CMS) de um tenant (SM-N) — espelha cms_sites (1:1 com company). Guarda o domínio
 * próprio + verificação de posse, o tema e o flag global de publicação. As PÁGINAS são {@link CmsPage}.
 *
 * @param slug           slug da empresa (companies.slug; base da URL pública /p/{slug})
 * @param domain         host próprio do tenant (nullable, UNIQUE global)
 * @param domainVerified posse comprovada por TXT _meada-verify=<verifyToken>
 * @param verifyToken    token a publicar no DNS (gerado on-demand)
 * @param published      publicação global do site (além do published por página)
 * @param theme          tema livre (ex.: {primaryColor, dark})
 */
public record CmsSite(
    UUID companyId,
    String slug,
    String domain,
    boolean domainVerified,
    String verifyToken,
    boolean published,
    JsonNode theme,
    Instant createdAt,
    Instant updatedAt) {
}
