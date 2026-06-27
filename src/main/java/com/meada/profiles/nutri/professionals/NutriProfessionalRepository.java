package com.meada.profiles.nutri.professionals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso a {@code nutri_professionals} (camada 8.0). service_role; escopo por company_id. */
@Repository
public class NutriProfessionalRepository {

    private static final RowMapper<NutriProfessional> MAPPER = (rs, rn) -> new NutriProfessional(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("specialty"),
        rs.getString("crn"),
        rs.getBoolean("active"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS = "id, name, specialty, crn, active, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public NutriProfessionalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NutriProfessional> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from nutri_professionals where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<NutriProfessional> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from nutri_professionals where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public NutriProfessional insert(UUID companyId, String name, String specialty, String crn, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into nutri_professionals (company_id, name, specialty, crn, notes) values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), specialty, crn, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<NutriProfessional> update(UUID companyId, UUID id, String name, String specialty,
                                              String crn, String notes, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (specialty != null) { sets.add("specialty = ?"); args.add(specialty); }
        if (crn != null) { sets.add("crn = ?"); args.add(crn); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update nutri_professionals set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<NutriProfessional> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update nutri_professionals set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from nutri_professionals where company_id = ? and id = ?", companyId, id) > 0;
    }
}
