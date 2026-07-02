package com.meada.profiles.atelie.measurements;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code atelie_measurements} (onda 2, backlog #9). service_role; escopo por company_id.
 * UPSERT por (contato, lower(label)) — regravar a mesma medida atualiza o valor.
 */
@Repository
public class AtelieMeasurementRepository {

    private static final RowMapper<AtelieMeasurement> MAPPER = (rs, rn) -> new AtelieMeasurement(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("label"),
        rs.getString("value"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS = "id, company_id, contact_id, label, value, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public AtelieMeasurementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Confirma que o contato pertence ao tenant (a FK sozinha não valida o company). */
    public boolean contactBelongsToCompany(UUID companyId, UUID contactId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from contacts where company_id = ? and id = ?",
            Integer.class, companyId, contactId);
        return n != null && n > 0;
    }

    public List<AtelieMeasurement> listByContact(UUID companyId, UUID contactId) {
        return jdbcTemplate.query(
            "select " + COLS + " from atelie_measurements where company_id = ? and contact_id = ? "
                + "order by lower(label) asc",
            MAPPER, companyId, contactId);
    }

    public Optional<AtelieMeasurement> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from atelie_measurements where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Upsert por (company, contact, lower(label)): mesma etiqueta atualiza o valor. */
    public AtelieMeasurement upsert(UUID companyId, UUID contactId, String label, String value) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into atelie_measurements (company_id, contact_id, label, value) values (?, ?, ?, ?) "
                + "on conflict (company_id, contact_id, lower(label)) "
                + "do update set value = excluded.value, updated_at = now() returning id",
            UUID.class, companyId, contactId, label.trim(), value.trim());
        return findById(companyId, id).orElseThrow();
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from atelie_measurements where company_id = ? and id = ?", companyId, id) > 0;
    }
}
