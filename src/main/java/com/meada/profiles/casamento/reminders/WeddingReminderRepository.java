package com.meada.profiles.casamento.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Varreduras dos jobs do casamento (onda 1): lembrete D-3 de tarefa do checklist (#2),
 * auto-realizada (#4) e aniversário de casamento (#16). service_role, cruza TODOS os tenants
 * casamento numa query só. (O lembrete de PARCELA vive no WeddingPaymentRepository.)
 */
@Repository
public class WeddingReminderRepository {

    private final JdbcTemplate jdbcTemplate;

    public WeddingReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record DueChecklistTask(UUID taskId, UUID companyId, UUID conversationId, String customerName,
                                   String title, LocalDate dueDate) {}

    /**
     * Tarefas do checklist NÃO concluídas com prazo até {@code windowEnd} (inclusive), ainda não
     * lembradas para esse due_date (remarcar rearma), de proposta VIVA de tenant casamento com o
     * lembrete de checklist LIGADO (ausência de config = ligado).
     */
    public List<DueChecklistTask> findDueChecklistTasks(LocalDate windowEnd) {
        return jdbcTemplate.query(
            "select t.id as task_id, t.company_id, p.conversation_id, p.customer_name, t.title, t.due_date "
                + "from wedding_checklist_tasks t "
                + "join wedding_proposals p on p.id = t.proposal_id "
                + "join companies c on c.id = t.company_id "
                + "left join wedding_config cfg on cfg.company_id = t.company_id "
                + "where c.profile_id = 'casamento' "
                + "and coalesce(cfg.checklist_reminder_enabled, true) "
                + "and t.done = false "
                + "and t.due_date is not null "
                + "and t.due_date <= ? "
                + "and (t.reminded_due_date is null or t.reminded_due_date <> t.due_date) "
                + "and p.status not in ('realizada','recusada','cancelada') "
                + "order by t.company_id, t.due_date",
            (rs, rn) -> new DueChecklistTask(
                (UUID) rs.getObject("task_id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("customer_name"),
                rs.getString("title"),
                rs.getDate("due_date").toLocalDate()),
            Date.valueOf(windowEnd));
    }

    /** Marca o due_date da tarefa como lembrado (idempotência por tarefa+data). */
    public void markTaskReminded(UUID taskId, LocalDate dueDate) {
        jdbcTemplate.update(
            "update wedding_checklist_tasks set reminded_due_date = ?, updated_at = now() where id = ?",
            Date.valueOf(dueDate), taskId);
    }

    public record CompletableProposal(UUID proposalId, UUID companyId) {}

    /**
     * Propostas FECHADAS cujo wedding_date já passou (a festa aconteceu) → candidatas a 'realizada'
     * (#4), respeitando o toggle auto_complete_enabled (ausência de config = ligado).
     */
    public List<CompletableProposal> findCompletableProposals(LocalDate before) {
        return jdbcTemplate.query(
            "select p.id as proposal_id, p.company_id "
                + "from wedding_proposals p "
                + "join companies c on c.id = p.company_id "
                + "left join wedding_config cfg on cfg.company_id = p.company_id "
                + "where c.profile_id = 'casamento' "
                + "and coalesce(cfg.auto_complete_enabled, true) "
                + "and p.status = 'fechada' "
                + "and p.wedding_date is not null "
                + "and p.wedding_date < ? "
                + "order by p.company_id",
            (rs, rn) -> new CompletableProposal(
                (UUID) rs.getObject("proposal_id"),
                (UUID) rs.getObject("company_id")),
            Date.valueOf(before));
    }

    public record AnniversaryProposal(UUID proposalId, UUID companyId, UUID conversationId,
                                      String customerName, LocalDate weddingDate) {}

    /**
     * Propostas REALIZADAS cujo aniversário de casamento (dia/mês do wedding_date) é HOJE, com pelo
     * menos 1 ano completo e ainda não parabenizadas NESTE ano (#16), respeitando o toggle
     * anniversary_enabled. (Casamento em 29/02 é parabenizado só em ano bissexto — caso raro aceito.)
     */
    public List<AnniversaryProposal> findAnniversaries(LocalDate today) {
        return jdbcTemplate.query(
            "select p.id as proposal_id, p.company_id, p.conversation_id, p.customer_name, p.wedding_date "
                + "from wedding_proposals p "
                + "join companies c on c.id = p.company_id "
                + "left join wedding_config cfg on cfg.company_id = p.company_id "
                + "where c.profile_id = 'casamento' "
                + "and coalesce(cfg.anniversary_enabled, true) "
                + "and p.status = 'realizada' "
                + "and p.wedding_date is not null "
                + "and extract(month from p.wedding_date) = ? "
                + "and extract(day from p.wedding_date) = ? "
                + "and p.wedding_date <= ? "
                + "and (p.anniversary_notified_year is null or p.anniversary_notified_year <> ?) "
                + "order by p.company_id",
            (rs, rn) -> new AnniversaryProposal(
                (UUID) rs.getObject("proposal_id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("customer_name"),
                rs.getDate("wedding_date").toLocalDate()),
            today.getMonthValue(), today.getDayOfMonth(),
            Date.valueOf(today.minusYears(1)), today.getYear());
    }

    /** Marca o ano do parabéns enviado (1 mensagem por ano; inclusive sem canal resolúvel). */
    public void markAnniversaryNotified(UUID proposalId, int year) {
        jdbcTemplate.update(
            "update wedding_proposals set anniversary_notified_year = ?, updated_at = now() where id = ?",
            year, proposalId);
    }
}
