package com.meada.profiles.nutri.plans;

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
 * Acesso a {@code nutri_plans} (camada 8.0). Sub-entidade do paciente (nível 2). service_role;
 * escopo por company_id. O {@code body} é escrito SÓ pelo painel — a IA só LÊ (entrega). 1 plano
 * 'ativo' por paciente (índice parcial UNIQUE); o service arquiva o anterior antes de criar/ativar.
 */
@Repository
public class NutriPlanRepository {

    private static final RowMapper<NutriPlan> MAPPER = (rs, rn) -> {
        Date st = rs.getDate("starts_on");
        Date en = rs.getDate("ends_on");
        return new NutriPlan(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("patient_id"),
            (UUID) rs.getObject("professional_id"),
            rs.getString("professional_name"),
            rs.getString("title"),
            rs.getString("body"),
            st == null ? null : st.toLocalDate(),
            en == null ? null : en.toLocalDate(),
            rs.getString("status"),
            rs.getString("notes"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    };

    private static final String SELECT =
        "select p.id, p.patient_id, p.professional_id, pr.name as professional_name, p.title, p.body, "
            + "p.starts_on, p.ends_on, p.status, p.notes, p.created_at, p.updated_at "
            + "from nutri_plans p left join nutri_professionals pr on pr.id = p.professional_id ";

    private final JdbcTemplate jdbcTemplate;

    public NutriPlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NutriPlan> listByPatient(UUID companyId, UUID patientId, String status) {
        StringBuilder sql = new StringBuilder(SELECT + "where p.company_id = ? and p.patient_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.add(patientId);
        if (status != null && !status.isBlank()) { sql.append(" and p.status = ?"); args.add(status); }
        sql.append(" order by p.status asc, p.created_at desc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<NutriPlan> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(SELECT + "where p.company_id = ? and p.id = ?", MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** O plano ATIVO do paciente (no máximo 1). */
    public Optional<NutriPlan> findActiveByPatient(UUID companyId, UUID patientId) {
        return jdbcTemplate.query(SELECT + "where p.company_id = ? and p.patient_id = ? and p.status = 'ativo'",
                MAPPER, companyId, patientId)
            .stream().findFirst();
    }

    /** Arquiva o plano ativo atual do paciente (libera o índice parcial antes de inserir/ativar outro). */
    public void archiveActive(UUID companyId, UUID patientId) {
        jdbcTemplate.update("update nutri_plans set status = 'arquivado', updated_at = now() "
            + "where company_id = ? and patient_id = ? and status = 'ativo'", companyId, patientId);
    }

    public NutriPlan insert(UUID companyId, UUID patientId, UUID professionalId, String title, String body,
                            LocalDate startsOn, LocalDate endsOn, String status, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into nutri_plans (company_id, patient_id, professional_id, title, body, starts_on, ends_on, status, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, patientId, professionalId, title.trim(), body,
            startsOn == null ? null : Date.valueOf(startsOn), endsOn == null ? null : Date.valueOf(endsOn),
            status, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<NutriPlan> update(UUID companyId, UUID id, String title, String body, UUID professionalId,
                                      boolean professionalProvided, LocalDate startsOn, boolean startsProvided,
                                      LocalDate endsOn, boolean endsProvided, String notes) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (title != null && !title.isBlank()) { sets.add("title = ?"); args.add(title.trim()); }
        if (body != null) { sets.add("body = ?"); args.add(body); }
        if (professionalProvided) { sets.add("professional_id = ?"); args.add(professionalId); }
        if (startsProvided) { sets.add("starts_on = ?"); args.add(startsOn == null ? null : Date.valueOf(startsOn)); }
        if (endsProvided) { sets.add("ends_on = ?"); args.add(endsOn == null ? null : Date.valueOf(endsOn)); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update nutri_plans set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Marca o plano como arquivado (sem mexer em outros). */
    public Optional<NutriPlan> archive(UUID companyId, UUID id) {
        int n = jdbcTemplate.update("update nutri_plans set status = 'arquivado', updated_at = now() "
            + "where company_id = ? and id = ?", companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Reativa um plano (após arquivar o ativo atual no service). */
    public Optional<NutriPlan> setActive(UUID companyId, UUID id) {
        int n = jdbcTemplate.update("update nutri_plans set status = 'ativo', updated_at = now() "
            + "where company_id = ? and id = ?", companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }
}
