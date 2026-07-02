package com.meada.profiles.atelie.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code atelie_catalog_items} (onda 2, backlog #15). service_role; escopo por company_id.
 * Delete é livre (nada referencia o catálogo por FK — o item de orçamento é snapshot texto).
 */
@Repository
public class AtelieCatalogRepository {

    private static final RowMapper<AtelieCatalogItem> MAPPER = (rs, rn) -> new AtelieCatalogItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        rs.getString("name"),
        rs.getString("category"),
        rs.getInt("unit_price_cents"),
        rs.getBoolean("active"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, company_id, name, category, unit_price_cents, active, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public AtelieCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AtelieCatalogItem> listByCompany(UUID companyId, boolean onlyActive) {
        String sql = "select " + COLS + " from atelie_catalog_items where company_id = ?"
            + (onlyActive ? " and active = true" : "")
            + " order by name asc";
        return jdbcTemplate.query(sql, MAPPER, companyId);
    }

    public Optional<AtelieCatalogItem> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from atelie_catalog_items where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public AtelieCatalogItem insert(UUID companyId, String name, String category, int unitPriceCents,
                                    boolean active, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into atelie_catalog_items (company_id, name, category, unit_price_cents, active, notes) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), category, unitPriceCents, active, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** PATCH parcial. category/notes controlados por flags "provided" (podem ser limpos p/ null). */
    public Optional<AtelieCatalogItem> update(UUID companyId, UUID id, String name, String category,
                                              boolean categoryProvided, Integer unitPriceCents,
                                              Boolean active, String notes, boolean notesProvided) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (categoryProvided) { sets.add("category = ?"); args.add(category); }
        if (unitPriceCents != null) { sets.add("unit_price_cents = ?"); args.add(unitPriceCents); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (notesProvided) { sets.add("notes = ?"); args.add(notes); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update atelie_catalog_items set " + String.join(", ", sets)
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
            "delete from atelie_catalog_items where company_id = ? and id = ?", companyId, id) > 0;
    }
}
