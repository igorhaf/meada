package com.meada.profiles.nutri.patients;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code nutri_patients} (camada 8.0). Sub-entidade do contact (nível 1). service_role;
 * escopo por company_id. {@link #contactExists} valida que o cliente é do company.
 */
@Repository
public class NutriPatientRepository {

    private static final RowMapper<NutriPatient> MAPPER = (rs, rn) -> {
        Date bd = rs.getDate("birth_date");
        return new NutriPatient(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("contact_id"),
            rs.getString("name"),
            rs.getString("goal"),
            rs.getString("dietary_restrictions"),
            bd == null ? null : bd.toLocalDate(),
            rs.getString("notes"),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    };

    private static final String COLS =
        "id, contact_id, name, goal, dietary_restrictions, birth_date, notes, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public NutriPatientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** True se o contato existe e é do company (valida o cliente antes de criar o paciente). */
    public boolean contactExists(UUID companyId, UUID contactId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from contacts where id = ? and company_id = ?", Integer.class, contactId, companyId);
        return n != null && n > 0;
    }

    /** Nome do contato (cliente) — para snapshot na consulta. */
    public Optional<String> contactName(UUID companyId, UUID contactId) {
        return jdbcTemplate.query("select name from contacts where id = ? and company_id = ?",
                (rs, rn) -> rs.getString("name"), contactId, companyId)
            .stream().findFirst();
    }

    /** Telefone do contato (cliente) — para snapshot na consulta. */
    public Optional<String> contactPhone(UUID companyId, UUID contactId) {
        return jdbcTemplate.query("select phone_number from contacts where id = ? and company_id = ?",
                (rs, rn) -> rs.getString("phone_number"), contactId, companyId)
            .stream().findFirst();
    }

    public List<NutriPatient> listByCompany(UUID companyId, UUID contactId, Boolean active, String search) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from nutri_patients where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (active != null) { sql.append(" and active = ?"); args.add(active); }
        if (search != null && !search.isBlank()) { sql.append(" and name ilike ?"); args.add("%" + search.trim() + "%"); }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public List<NutriPatient> listByContact(UUID companyId, UUID contactId, boolean onlyActive) {
        return listByCompany(companyId, contactId, onlyActive ? Boolean.TRUE : null, null);
    }

    public Optional<NutriPatient> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from nutri_patients where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public NutriPatient insert(UUID companyId, UUID contactId, String name, String goal,
                               String dietaryRestrictions, LocalDate birthDate, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into nutri_patients (company_id, contact_id, name, goal, dietary_restrictions, birth_date, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, contactId, name.trim(), goal, dietaryRestrictions,
            birthDate == null ? null : Date.valueOf(birthDate), notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<NutriPatient> update(UUID companyId, UUID id, String name, String goal,
                                         String dietaryRestrictions, LocalDate birthDate, boolean birthProvided,
                                         String notes, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (goal != null) { sets.add("goal = ?"); args.add(goal); }
        if (dietaryRestrictions != null) { sets.add("dietary_restrictions = ?"); args.add(dietaryRestrictions); }
        if (birthProvided) { sets.add("birth_date = ?"); args.add(birthDate == null ? null : Date.valueOf(birthDate)); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update nutri_patients set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<NutriPatient> archive(UUID companyId, UUID id) {
        int n = jdbcTemplate.update("update nutri_patients set active = false, updated_at = now() "
            + "where company_id = ? and id = ?", companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from nutri_patients where company_id = ? and id = ?", companyId, id) > 0;
    }
}
