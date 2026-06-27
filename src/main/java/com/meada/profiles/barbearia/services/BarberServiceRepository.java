package com.meada.profiles.barbearia.services;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code barber_services} (camada 8.1). Opera via service_role; escopo por company_id.
 * Espelho de SalonOfferingRepository.
 */
@Repository
public class BarberServiceRepository {

    private static final RowMapper<BarberService> MAPPER = (rs, rn) -> new BarberService(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("category"),
        rs.getInt("duration_minutes"),
        (Integer) rs.getObject("price_cents"),
        rs.getBoolean("active"),
        rs.getString("description"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, name, category, duration_minutes, price_cents, active, description, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public BarberServiceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BarberService> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from barber_services where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by category asc nulls last, name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<BarberService> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from barber_services where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public BarberService insert(UUID companyId, String name, String category, int durationMinutes,
                                Integer priceCents, String description) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into barber_services (company_id, name, category, duration_minutes, price_cents, description) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), category, durationMinutes, priceCents, description);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<BarberService> update(UUID companyId, UUID id, String name, String category,
                                          Integer durationMinutes, Integer priceCents, String description,
                                          Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (category != null) { sets.add("category = ?"); args.add(category); }
        if (durationMinutes != null) { sets.add("duration_minutes = ?"); args.add(durationMinutes); }
        // price_cents: sentinela -1 para "limpar" (pôr null); >=0 grava; null não mexe.
        if (priceCents != null) {
            if (priceCents < 0) { sets.add("price_cents = null"); }
            else { sets.add("price_cents = ?"); args.add(priceCents); }
        }
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update barber_services set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<BarberService> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update(
            "update barber_services set active = ?, updated_at = now() where company_id = ? and id = ?",
            active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from barber_services where company_id = ? and id = ?", companyId, id) > 0;
    }
}
