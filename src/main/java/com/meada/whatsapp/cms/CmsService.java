package com.meada.whatsapp.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Regras do CMS (SM-M). O tenant edita a própria página (title + blocks ordenados) e configura um
 * domínio próprio; o público lê a página PUBLICADA por slug ou por domínio.
 *
 * <p>Validação dos blocks (app-level — o JSONB não tem CHECK): o array é normalizado a
 * {@code [{id, type, props}]} com {@code type ∈ CmsBlockType} e {@code props} objeto. Blocos
 * inválidos → {@link InvalidBlocksException}. Domínio: hostname simples, normalizado lowercase,
 * único global (→ {@link DomainTakenException}); não pode ser um host do próprio Meada.
 */
@Service
public class CmsService {

    // hostname básico: labels alfanuméricas com hífen, separadas por ponto, TLD >= 2. Sem esquema/path.
    private static final Pattern DOMAIN = Pattern.compile(
        "^(?=.{1,253}$)([a-z0-9](-?[a-z0-9])*\\.)+[a-z]{2,}$");
    private static final int MAX_BLOCKS = 50;

    private final CmsPageRepository repository;
    private final ObjectMapper objectMapper;

    public CmsService(CmsPageRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public static class InvalidBlocksException extends RuntimeException {}
    public static class InvalidDomainException extends RuntimeException {}
    public static class DomainTakenException extends RuntimeException {}

    /** A página do tenant (cria uma vazia na 1ª leitura, pra o editor ter o que mostrar). */
    @Transactional
    public CmsPage getOrCreate(UUID companyId) {
        repository.ensureExists(companyId);
        return repository.findByCompany(companyId).orElseThrow();
    }

    /**
     * Salva o conteúdo (title + blocks). Valida e NORMALIZA os blocks: cada item vira
     * {@code {id, type, props}} (id gerado se faltar; type ∈ CmsBlockType; props objeto, {} se ausente).
     */
    @Transactional
    public CmsPage saveContent(UUID companyId, String title, JsonNode blocks) {
        ArrayNode normalized = normalizeBlocks(blocks);
        String blocksJson;
        try {
            blocksJson = objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new InvalidBlocksException();
        }
        return repository.updateContent(companyId, title == null ? "" : title, blocksJson);
    }

    @Transactional
    public CmsPage setPublished(UUID companyId, boolean published) {
        return repository.setPublished(companyId, published);
    }

    /**
     * Seta/limpa o domínio próprio. blank/null → limpa. Valida formato e rejeita hosts do Meada.
     * UNIQUE global → {@link DomainTakenException}.
     */
    @Transactional
    public CmsPage setDomain(UUID companyId, String rawDomain) {
        String domain = rawDomain == null ? null : rawDomain.trim().toLowerCase();
        if (domain != null && domain.isEmpty()) {
            domain = null;
        }
        if (domain != null) {
            if (!DOMAIN.matcher(domain).matches() || domain.endsWith("meadadigital.com")
                    || domain.endsWith("meadadigital.local")) {
                throw new InvalidDomainException();
            }
        }
        try {
            return repository.setDomain(companyId, domain);
        } catch (DataIntegrityViolationException e) {
            throw new DomainTakenException();
        }
    }

    // ---- resolução pública ---------------------------------------------------

    /** Página PUBLICADA por slug (rota /p/{slug}). Empty se não existe ou é rascunho. */
    public Optional<CmsPage> publishedBySlug(String slug) {
        return repository.findBySlug(slug).filter(CmsPage::published);
    }

    /** Página PUBLICADA por domínio próprio. Empty se não existe ou é rascunho. */
    public Optional<CmsPage> publishedByDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        return repository.findByDomain(domain.trim().toLowerCase()).filter(CmsPage::published);
    }

    // ---- validação/normalização de blocks ------------------------------------

    private ArrayNode normalizeBlocks(JsonNode blocks) {
        if (blocks == null || !blocks.isArray()) {
            throw new InvalidBlocksException();
        }
        if (blocks.size() > MAX_BLOCKS) {
            throw new InvalidBlocksException();
        }
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode b : blocks) {
            if (!b.isObject()) {
                throw new InvalidBlocksException();
            }
            String type = b.path("type").asText(null);
            if (CmsBlockType.fromId(type).isEmpty()) {
                throw new InvalidBlocksException();
            }
            JsonNode props = b.get("props");
            if (props == null || !props.isObject()) {
                props = objectMapper.createObjectNode();
            }
            String id = b.path("id").asText(null);
            if (id == null || id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            ObjectNode norm = objectMapper.createObjectNode();
            norm.put("id", id);
            norm.put("type", type);
            norm.set("props", props);
            out.add(norm);
        }
        return out;
    }
}
