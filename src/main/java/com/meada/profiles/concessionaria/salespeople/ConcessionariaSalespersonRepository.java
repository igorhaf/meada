package com.meada.profiles.concessionaria.salespeople;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code concessionaria_salespeople} (camada 8.17). Opera via service_role; escopo por
 * company_id no WHERE é a defesa.
 */
@Repository
public class ConcessionariaSalespersonRepository {

    private static final RowMapper<ConcessionariaSalesperson> MAPPER = (rs, rn) -> new ConcessionariaSalesperson(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("phone"),
        rs.getBoolean("active"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS = "id, name, phone, active, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public ConcessionariaSalespersonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ConcessionariaSalesperson> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from concessionaria_salespeople where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<ConcessionariaSalesperson> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from concessionaria_salespeople where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public ConcessionariaSalesperson insert(UUID companyId, String name, String phone, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into concessionaria_salespeople (company_id, name, phone, notes) "
                + "values (?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), phone, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<ConcessionariaSalesperson> update(UUID companyId, UUID id, String name, String phone,
                                                      String notes, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (phone != null) { sets.add("phone = ?"); args.add(phone); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update concessionaria_salespeople set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<ConcessionariaSalesperson> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update(
            "update concessionaria_salespeople set active = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from concessionaria_salespeople where company_id = ? and id = ?", companyId, id) > 0;
    }

    /**
     * True se o vendedor está referenciado por algum test-drive ou lead. test_drives.salesperson_id é
     * FK restrict; leads.salesperson_id é ON DELETE SET NULL — checamos explicitamente os DOIS para
     * devolver 409 salesperson_in_use em vez de silenciosamente desvincular o histórico do lead.
     */
    public boolean hasReferences(UUID companyId, UUID id) {
        Integer td = jdbcTemplate.queryForObject(
            "select count(*) from concessionaria_test_drives where company_id = ? and salesperson_id = ?",
            Integer.class, companyId, id);
        if (td != null && td > 0) {
            return true;
        }
        Integer leads = jdbcTemplate.queryForObject(
            "select count(*) from concessionaria_leads where company_id = ? and salesperson_id = ?",
            Integer.class, companyId, id);
        return leads != null && leads > 0;
    }
}
