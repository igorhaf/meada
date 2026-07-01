package com.meada.profiles.academia.billing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Acesso de leitura/marcação para a régua de inadimplência da Academia (Onda 2). Separado do
 * {@code AcademiaMembershipRepository} de CRUD — espelha a separação {@code ReactivationRepository}
 * do fluxo de reativação do core. Opera via service_role (o job roda fora de request de tenant).
 *
 * <p>A CONTAGEM de meses em aberto NÃO fica aqui em SQL: o job reusa
 * {@code AcademiaPaymentService.summary()} (que já calcula monthsOpen a partir de start_date e dos
 * pagamentos lançados) — fonte única da regra, sem duplicar a lógica.
 */
@Repository
public class AcademiaBillingRepository {

    private static final RowMapper<BillingPolicy> POLICY_MAPPER = (rs, rn) -> new BillingPolicy(
        (UUID) rs.getObject("company_id"),
        rs.getBoolean("billing_reminder_enabled"),
        rs.getInt("grace_days"),
        (Integer) rs.getObject("auto_suspend_days"));

    private static final RowMapper<DueMembership> DUE_MAPPER = (rs, rn) -> new DueMembership(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("contact_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("student_name"),
        rs.getInt("plan_monthly_cents"),
        rs.getObject("start_date", LocalDate.class),
        rs.getObject("overdue_notified_month", LocalDate.class));

    private final JdbcTemplate jdbcTemplate;

    public AcademiaBillingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Políticas de cobrança de TODOS os tenants academia que têm linha em academia_config. Tenant sem
     * linha usa defaults no service (reminder on, grace 5, sem auto-suspensão) — mas sem linha também
     * não tem matrícula relevante configurada, então varrer só os configurados é suficiente e barato.
     */
    public List<BillingPolicy> findBillingPolicies() {
        return jdbcTemplate.query(
            "select company_id, billing_reminder_enabled, grace_days, auto_suspend_days "
                + "from academia_config",
            POLICY_MAPPER);
    }

    /** Matrículas ATIVAS de uma empresa — candidatas à régua (o job filtra meses em aberto na app). */
    public List<DueMembership> findActiveMemberships(UUID companyId) {
        return jdbcTemplate.query(
            "select id, company_id, contact_id, conversation_id, student_name, plan_monthly_cents, "
                + "start_date, overdue_notified_month from academia_memberships "
                + "where company_id = ? and status = 'ativa'",
            DUE_MAPPER, companyId);
    }

    /** Marca o mês de referência já notificado (idempotência do lembrete). */
    public void markOverdueNotified(UUID membershipId, LocalDate referenceMonth) {
        jdbcTemplate.update(
            "update academia_memberships set overdue_notified_month = ? where id = ?",
            Date.valueOf(referenceMonth.withDayOfMonth(1)), membershipId);
    }
}
