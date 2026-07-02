package com.meada.profiles.atelie.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Varredura e marcação do lembrete de prova/ajuste (onda Ateliê, backlog #1). service_role, cruza
 * TODOS os tenants atelie numa query só (o job roda global). Espelho do AcademiaBillingRepository.
 */
@Repository
public class AtelieFittingReminderRepository {

    private final JdbcTemplate jdbcTemplate;

    public AtelieFittingReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Provas/ajustes DUE para a data alvo (a véspera é responsabilidade do chamador — o job passa
     * amanhã): pendentes, do perfil atelie, com o lembrete LIGADO na config (ausência de linha =
     * ligado), ainda não lembradas PARA ESSE due_date (remarcar a prova rearma o lembrete) e cuja
     * proposta segue viva (não-terminal — proposta cancelada/recusada/realizada não lembra).
     */
    public List<DueFitting> findDueFittings(LocalDate targetDate) {
        return jdbcTemplate.query(
            "select f.id as fitting_id, f.company_id, f.proposal_id, f.title, f.due_date, "
                + "p.conversation_id, p.customer_name "
                + "from atelie_fittings f "
                + "join atelie_proposals p on p.id = f.proposal_id "
                + "join companies c on c.id = f.company_id "
                + "left join atelie_config cfg on cfg.company_id = f.company_id "
                + "where c.profile_id = 'atelie' "
                + "and coalesce(cfg.fitting_reminder_enabled, true) "
                + "and f.status = 'pendente' "
                + "and f.due_date = ? "
                + "and (f.reminded_due_date is null or f.reminded_due_date <> f.due_date) "
                + "and p.status not in ('realizada','recusada','cancelada') "
                + "order by f.company_id, f.due_date, f.position",
            (rs, rn) -> new DueFitting(
                (UUID) rs.getObject("fitting_id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("proposal_id"),
                rs.getString("title"),
                rs.getDate("due_date").toLocalDate(),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("customer_name")),
            Date.valueOf(targetDate));
    }

    /** Marca o due_date como lembrado (idempotência por prova+data — inclusive sem canal resolúvel). */
    public void markReminded(UUID fittingId, LocalDate dueDate) {
        jdbcTemplate.update(
            "update atelie_fittings set reminded_due_date = ?, updated_at = now() where id = ?",
            Date.valueOf(dueDate), fittingId);
    }
}
