package com.meada.profiles.casamento.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code wedding_catalog_items} (onda 1, backlog #3). service_role; escopo por company_id.
 * Delete é livre (nada referencia o catálogo por FK — o item de orçamento é snapshot texto).
 */
@Repository
public class WeddingCatalogRepository {

    private static final RowMapper<WeddingCatalogItem> MAPPER = (rs, rn) -> new WeddingCatalogItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        rs.getString("name"),
        rs.getString("kind"),
        rs.getString("description"),
        rs.getInt("price_cents"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, company_id, name, kind, description, price_cents, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public WeddingCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WeddingCatalogItem> listByCompany(UUID companyId, boolean onlyActive) {
        String sql = "select " + COLS + " from wedding_catalog_items where company_id = ?"
            + (onlyActive ? " and active = true" : "")
            + " order by kind asc, name asc";
        return jdbcTemplate.query(sql, MAPPER, companyId);
    }

    public Optional<WeddingCatalogItem> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from wedding_catalog_items where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public WeddingCatalogItem insert(UUID companyId, String name, String kind, String description,
                                     int priceCents, boolean active) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into wedding_catalog_items (company_id, name, kind, description, price_cents, active) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), kind, description, priceCents, active);
        return findById(companyId, id).orElseThrow();
    }

    /** PATCH parcial. description controlada por flag "provided" (pode ser limpa p/ null). */
    public Optional<WeddingCatalogItem> update(UUID companyId, UUID id, String name, String kind,
                                               String description, boolean descProvided,
                                               Integer priceCents, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (kind != null && !kind.isBlank()) { sets.add("kind = ?"); args.add(kind); }
        if (descProvided) { sets.add("description = ?"); args.add(description); }
        if (priceCents != null) { sets.add("price_cents = ?"); args.add(priceCents); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update wedding_catalog_items set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from wedding_catalog_items where company_id = ? and id = ?", companyId, id) > 0;
    }
}
