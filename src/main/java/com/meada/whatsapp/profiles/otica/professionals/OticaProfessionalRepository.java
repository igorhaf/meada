package com.meada.whatsapp.profiles.otica.professionals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code otica_professionals} (camada 8.12, FLUXO A). Opera via service_role; o escopo por
 * company_id no WHERE é a defesa. Clone de
 * {@link com.meada.whatsapp.profiles.salon.professionals.SalonProfessionalRepository} sem specialty.
 */
@Repository
public class OticaProfessionalRepository {

    private static final RowMapper<OticaProfessional> MAPPER = (rs, rn) -> new OticaProfessional(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getBoolean("active"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS = "id, name, active, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public OticaProfessionalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OticaProfessional> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from otica_professionals where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<OticaProfessional> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from otica_professionals where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public OticaProfessional insert(UUID companyId, String name, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into otica_professionals (company_id, name, notes) values (?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<OticaProfessional> update(UUID companyId, UUID id, String name, String notes,
                                              Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update otica_professionals set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<OticaProfessional> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update(
            "update otica_professionals set active = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver exame referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from otica_professionals where company_id = ? and id = ?", companyId, id) > 0;
    }
}
