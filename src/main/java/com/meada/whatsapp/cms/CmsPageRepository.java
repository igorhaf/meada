package com.meada.whatsapp.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code cms_pages} (SM-M). 1:1 com company. Opera via service_role. {@code blocks} é JSONB
 * lido como {@link JsonNode} e escrito via {@code ?::jsonb} (string serializada). Resolução pública
 * por slug (join companies) ou por domínio (host próprio).
 */
@Repository
public class CmsPageRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CmsPageRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private RowMapper<CmsPage> mapper() {
        return (rs, rn) -> {
            JsonNode blocks;
            try {
                String raw = rs.getString("blocks");
                blocks = raw == null ? objectMapper.createArrayNode() : objectMapper.readTree(raw);
            } catch (Exception e) {
                blocks = objectMapper.createArrayNode();
            }
            return new CmsPage(
                (UUID) rs.getObject("company_id"),
                rs.getString("slug"),
                rs.getString("domain"),
                rs.getBoolean("published"),
                rs.getString("title"),
                blocks,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
        };
    }

    // sempre faz o join com companies pra trazer o slug (URL pública /p/{slug}).
    private static final String SELECT =
        "select p.company_id, c.slug, p.domain, p.published, p.title, p.blocks, p.created_at, p.updated_at "
            + "from cms_pages p join companies c on c.id = p.company_id ";

    public Optional<CmsPage> findByCompany(UUID companyId) {
        return jdbcTemplate.query(SELECT + "where p.company_id = ?", mapper(), companyId)
            .stream().findFirst();
    }

    /** Garante que a linha existe (cria vazia na 1ª vez). Idempotente. */
    public void ensureExists(UUID companyId) {
        jdbcTemplate.update(
            "insert into cms_pages (company_id) values (?) on conflict (company_id) do nothing", companyId);
    }

    /** Atualiza title + blocks (page builder). blocksJson é o array JSON serializado. */
    public CmsPage updateContent(UUID companyId, String title, String blocksJson) {
        ensureExists(companyId);
        jdbcTemplate.update(
            "update cms_pages set title = ?, blocks = ?::jsonb, updated_at = now() where company_id = ?",
            title, blocksJson, companyId);
        return findByCompany(companyId).orElseThrow();
    }

    public CmsPage setPublished(UUID companyId, boolean published) {
        ensureExists(companyId);
        jdbcTemplate.update(
            "update cms_pages set published = ?, updated_at = now() where company_id = ?", published, companyId);
        return findByCompany(companyId).orElseThrow();
    }

    /** Seta (ou limpa, com null) o domínio próprio. Violação de UNIQUE sobe como DataIntegrityViolation. */
    public CmsPage setDomain(UUID companyId, String domain) {
        ensureExists(companyId);
        jdbcTemplate.update(
            "update cms_pages set domain = ?, updated_at = now() where company_id = ?", domain, companyId);
        return findByCompany(companyId).orElseThrow();
    }

    /** Resolução PÚBLICA por domínio (host próprio do tenant). */
    public Optional<CmsPage> findByDomain(String domain) {
        return jdbcTemplate.query(SELECT + "where p.domain = ?", mapper(), domain)
            .stream().findFirst();
    }

    /** Resolução PÚBLICA por slug da empresa (rota /p/{slug}). */
    public Optional<CmsPage> findBySlug(String slug) {
        return jdbcTemplate.query(SELECT + "where c.slug = ?", mapper(), slug)
            .stream().findFirst();
    }
}
