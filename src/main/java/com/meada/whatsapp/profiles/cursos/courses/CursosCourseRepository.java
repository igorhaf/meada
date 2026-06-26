package com.meada.whatsapp.profiles.cursos.courses;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code cursos_courses} (camada 8.20 / perfil cursos). Opera via service_role; escopo por
 * company_id. Clone do AcademiaPlanRepository (camada 7.7) com o campo extra {@code category}.
 */
@Repository
public class CursosCourseRepository {

    private static final RowMapper<CursosCourse> MAPPER = (rs, rn) -> new CursosCourse(
        (UUID) rs.getObject("id"),
        rs.getString("title"),
        rs.getString("category"),
        rs.getInt("monthly_cents"),
        rs.getString("description"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, title, category, monthly_cents, description, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public CursosCourseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CursosCourse> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from cursos_courses where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by monthly_cents asc, title asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<CursosCourse> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from cursos_courses where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public CursosCourse insert(UUID companyId, String title, String category, int monthlyCents, String description) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into cursos_courses (company_id, title, category, monthly_cents, description) "
                + "values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, title.trim(), category, monthlyCents, description);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<CursosCourse> update(UUID companyId, UUID id, String title, String category,
                                         Integer monthlyCents, String description, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (title != null && !title.isBlank()) { sets.add("title = ?"); args.add(title.trim()); }
        if (category != null) { sets.add("category = ?"); args.add(category); }
        if (monthlyCents != null) { sets.add("monthly_cents = ?"); args.add(monthlyCents); }
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update cursos_courses set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<CursosCourse> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update cursos_courses set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from cursos_courses where company_id = ? and id = ?", companyId, id) > 0;
    }
}
