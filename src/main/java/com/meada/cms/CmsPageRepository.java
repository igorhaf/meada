package com.meada.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code cms_pages} (SM-N) — N páginas por company. service_role. {@code blocks} JSONB
 * lido como {@link JsonNode}, escrito via {@code ?::jsonb}. 1 home por company (índice parcial); a
 * troca de home é transacional (zera as outras antes).
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
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("company_id"),
                rs.getString("page_slug"),
                rs.getString("title"),
                blocks,
                rs.getBoolean("is_home"),
                rs.getInt("position"),
                rs.getBoolean("published"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
        };
    }

    private static final String COLS =
        "id, company_id, page_slug, title, blocks, is_home, position, published, created_at, updated_at";

    public List<CmsPage> listByCompany(UUID companyId) {
        return jdbcTemplate.query("select " + COLS + " from cms_pages where company_id = ? order by position asc, created_at asc",
            mapper(), companyId);
    }

    public Optional<CmsPage> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from cms_pages where company_id = ? and id = ?", mapper(), companyId, id)
            .stream().findFirst();
    }

    public Optional<CmsPage> findHome(UUID companyId) {
        return jdbcTemplate.query("select " + COLS + " from cms_pages where company_id = ? and is_home = true", mapper(), companyId)
            .stream().findFirst();
    }

    public Optional<CmsPage> findBySlug(UUID companyId, String pageSlug) {
        return jdbcTemplate.query("select " + COLS + " from cms_pages where company_id = ? and page_slug = ?",
                mapper(), companyId, pageSlug).stream().findFirst();
    }

    public boolean slugExists(UUID companyId, String pageSlug) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from cms_pages where company_id = ? and page_slug = ?", Integer.class, companyId, pageSlug);
        return n != null && n > 0;
    }

    public long countByCompany(UUID companyId) {
        Long n = jdbcTemplate.queryForObject("select count(*) from cms_pages where company_id = ?", Long.class, companyId);
        return n == null ? 0L : n;
    }

    public CmsPage insert(UUID companyId, String pageSlug, String title, boolean isHome) {
        Integer maxPos = jdbcTemplate.queryForObject(
            "select coalesce(max(position), -1) from cms_pages where company_id = ?", Integer.class, companyId);
        int position = (maxPos == null ? -1 : maxPos) + 1;
        UUID id = jdbcTemplate.queryForObject(
            "insert into cms_pages (company_id, page_slug, title, is_home, position) values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, pageSlug, title, isHome, position);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<CmsPage> updateContent(UUID companyId, UUID id, String title, String blocksJson, Boolean published) {
        List<String> sets = new java.util.ArrayList<>();
        List<Object> args = new java.util.ArrayList<>();
        if (title != null) { sets.add("title = ?"); args.add(title); }
        if (blocksJson != null) { sets.add("blocks = ?::jsonb"); args.add(blocksJson); }
        if (published != null) { sets.add("published = ?"); args.add(published); }
        if (sets.isEmpty()) {
            return findById(companyId, id);
        }
        sets.add("updated_at = now()");
        args.add(companyId);
        args.add(id);
        int n = jdbcTemplate.update("update cms_pages set " + String.join(", ", sets) + " where company_id = ? and id = ?",
            args.toArray());
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Marca uma página como home, zerando o flag das outras na mesma transação (1 home por company). */
    @Transactional
    public Optional<CmsPage> setHome(UUID companyId, UUID id) {
        if (findById(companyId, id).isEmpty()) {
            return Optional.empty();
        }
        jdbcTemplate.update("update cms_pages set is_home = false, updated_at = now() where company_id = ? and is_home = true", companyId);
        jdbcTemplate.update("update cms_pages set is_home = true, updated_at = now() where company_id = ? and id = ?", companyId, id);
        return findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from cms_pages where company_id = ? and id = ?", companyId, id) > 0;
    }
}
