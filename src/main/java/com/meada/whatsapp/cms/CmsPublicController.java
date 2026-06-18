package com.meada.whatsapp.cms;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * CMS PÚBLICO (SM-M). Serve a página PUBLICADA de um tenant, SEM autenticação (estas rotas não estão
 * na allowlist do JwtAuthenticationFilter → passam direto). Resolução por slug da empresa (rota
 * {@code /p/{slug}} do frontend) ou por domínio próprio (host custom apontado pro nosso servidor).
 *
 * <p>Devolve só a VIEW pública (title + blocks) — nunca campos internos (domain, published). 404 se
 * a página não existe ou está em rascunho.
 */
@RestController
public class CmsPublicController {

    private final CmsService service;

    public CmsPublicController(CmsService service) {
        this.service = service;
    }

    /** View pública mínima: título + blocos ordenados. */
    public record PublicPage(String title, JsonNode blocks) {}

    private static PublicPage view(CmsPage p) {
        return new PublicPage(p.title(), p.blocks());
    }

    @GetMapping("/public/cms/by-slug/{slug}")
    public ResponseEntity<Object> bySlug(@PathVariable String slug) {
        Optional<CmsPage> page = service.publishedBySlug(slug);
        return page.<ResponseEntity<Object>>map(p -> ResponseEntity.ok(view(p)))
            .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Not Found", "reason", "page_not_found")));
    }

    @GetMapping("/public/cms/by-domain")
    public ResponseEntity<Object> byDomain(@RequestParam String host) {
        Optional<CmsPage> page = service.publishedByDomain(host);
        return page.<ResponseEntity<Object>>map(p -> ResponseEntity.ok(view(p)))
            .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Not Found", "reason", "page_not_found")));
    }
}
