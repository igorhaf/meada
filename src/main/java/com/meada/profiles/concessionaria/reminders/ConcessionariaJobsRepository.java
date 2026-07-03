package com.meada.profiles.concessionaria.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Varreduras dos jobs da concessionária (onda 1): lembrete de test-drive (#3), follow-up de lead
 * parado (#2) e auto-realizado (#9). service_role, cruza TODOS os tenants concessionaria numa query
 * só, respeitando os toggles da config (ausência de linha = ligado).
 */
@Repository
public class ConcessionariaJobsRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConcessionariaJobsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record DueTestDrive(UUID testDriveId, UUID companyId, UUID conversationId, String customerName,
                               String vehicleBrand, String vehicleModel, Instant startAt) {}

    /** Test-drives 'agendado' começando até {@code windowEnd}, ainda não lembrados, toggle ligado. */
    public List<DueTestDrive> findDueTestDrives(Instant windowEnd) {
        return jdbcTemplate.query(
            "select t.id as test_drive_id, t.company_id, t.conversation_id, t.customer_name, "
                + "t.vehicle_brand, t.vehicle_model, t.start_at "
                + "from concessionaria_test_drives t "
                + "join companies c on c.id = t.company_id "
                + "left join concessionaria_config cfg on cfg.company_id = t.company_id "
                + "where c.profile_id = 'concessionaria' "
                + "and coalesce(cfg.testdrive_reminder_enabled, true) "
                + "and t.status = 'agendado' "
                + "and t.reminded_24h = false "
                + "and t.start_at > now() "
                + "and t.start_at <= ? "
                + "order by t.company_id, t.start_at",
            (rs, rn) -> new DueTestDrive(
                (UUID) rs.getObject("test_drive_id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("customer_name"),
                rs.getString("vehicle_brand"),
                rs.getString("vehicle_model"),
                rs.getTimestamp("start_at").toInstant()),
            Timestamp.from(windowEnd));
    }

    /** Marca o lembrete enviado (inclusive sem canal — não revarre). */
    public void markTestDriveReminded(UUID testDriveId) {
        jdbcTemplate.update(
            "update concessionaria_test_drives set reminded_24h = true, status_updated_at = status_updated_at "
                + "where id = ?", testDriveId);
    }

    public record StaleLead(UUID leadId, UUID companyId, UUID conversationId, String customerName,
                            String vehicleBrand, String vehicleModel) {}

    /**
     * Leads 'novo'/'em_negociacao' PARADOS (status_updated_at anterior a agora − followup_days da
     * config de cada tenant) e ainda não re-followupados nesta janela (followup_sent_at null OU
     * anterior ao último movimento — re-arma quando o lead volta a se mover e estagna de novo).
     */
    public List<StaleLead> findStaleLeads() {
        return jdbcTemplate.query(
            "select l.id as lead_id, l.company_id, l.conversation_id, l.customer_name, "
                + "l.vehicle_brand, l.vehicle_model "
                + "from concessionaria_leads l "
                + "join companies c on c.id = l.company_id "
                + "left join concessionaria_config cfg on cfg.company_id = l.company_id "
                + "where c.profile_id = 'concessionaria' "
                + "and coalesce(cfg.followup_enabled, true) "
                + "and l.status in ('novo','em_negociacao') "
                + "and l.status_updated_at < now() - make_interval(days => coalesce(cfg.followup_days, 3)) "
                + "and (l.followup_sent_at is null or l.followup_sent_at < l.status_updated_at) "
                + "order by l.company_id, l.status_updated_at",
            (rs, rn) -> new StaleLead(
                (UUID) rs.getObject("lead_id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("customer_name"),
                rs.getString("vehicle_brand"),
                rs.getString("vehicle_model")));
    }

    /** Marca o follow-up enviado (inclusive sem canal — não revarre até o lead se mover de novo). */
    public void markLeadFollowedUp(UUID leadId) {
        jdbcTemplate.update(
            "update concessionaria_leads set followup_sent_at = now() where id = ?", leadId);
    }

    public record CompletableTestDrive(UUID testDriveId, UUID companyId) {}

    /** Test-drives 'confirmado' cujo end_at passou de {@code before} (graça), toggle ligado. */
    public List<CompletableTestDrive> findCompletableTestDrives(Instant before) {
        return jdbcTemplate.query(
            "select t.id as test_drive_id, t.company_id "
                + "from concessionaria_test_drives t "
                + "join companies c on c.id = t.company_id "
                + "left join concessionaria_config cfg on cfg.company_id = t.company_id "
                + "where c.profile_id = 'concessionaria' "
                + "and coalesce(cfg.auto_complete_enabled, true) "
                + "and t.status = 'confirmado' "
                + "and t.end_at < ? "
                + "order by t.company_id",
            (rs, rn) -> new CompletableTestDrive(
                (UUID) rs.getObject("test_drive_id"),
                (UUID) rs.getObject("company_id")),
            Timestamp.from(before));
    }
}
