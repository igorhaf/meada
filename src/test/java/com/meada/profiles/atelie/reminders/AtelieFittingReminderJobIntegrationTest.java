package com.meada.profiles.atelie.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link AtelieFittingReminderJob} (onda Ateliê, backlog #1) contra PostgreSQL
 * real. A lógica roda via {@link AtelieFittingReminderJob#runFittingReminders()} direto (sem o
 * scheduler). EvolutionSender é um FAKE que só registra os envios.
 *
 * <p>Cenários: prova pendente de amanhã → lembrete + reminded_due_date marcado + idempotente;
 * toggle desligado na config → nada; prova realizada / fora da véspera / proposta terminal → nada;
 * prova REMARCADA (reminded_due_date ≠ due_date) → lembra de novo; proposta sem conversa (manual) →
 * marca sem envio (não revarre eternamente).
 */
@Import(AtelieFittingReminderJobIntegrationTest.TestConfig.class)
class AtelieFittingReminderJobIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("a7000000-0000-0000-0000-0000000000b1");
    private static final UUID INSTANCE = UUID.fromString("a7100000-0000-0000-0000-0000000000b1");

    @Autowired
    private AtelieFittingReminderJob job;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID conversationId;
    private final LocalDate tomorrow = LocalDate.now().plusDays(1);

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'atelie')",
            COMPANY, "Atelie Reminder", "atelie-reminder");
        UUID contact = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-atl", "tok-atl");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990181", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contact, INSTANCE);
    }

    /** Proposta viva (aprovada) do tenant; conversationId null = proposta manual sem canal. */
    private UUID seedProposal(String status, UUID conversation) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into atelie_proposals (id, company_id, conversation_id, customer_name, project_type, status) "
                + "values (?, ?, ?, 'Marina', 'costura', ?)",
            id, COMPANY, conversation, status);
        return id;
    }

    private UUID seedFitting(UUID proposalId, String title, LocalDate dueDate, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into atelie_fittings (id, company_id, proposal_id, title, due_date, status, position) "
                + "values (?, ?, ?, ?, ?, ?, 0)",
            id, COMPANY, proposalId, title, java.sql.Date.valueOf(dueDate), status);
        return id;
    }

    private LocalDate remindedDueDateOf(UUID fittingId) {
        java.sql.Date d = jdbcTemplate.queryForObject(
            "select reminded_due_date from atelie_fittings where id = ?", java.sql.Date.class, fittingId);
        return d == null ? null : d.toLocalDate();
    }

    @Test
    @DisplayName("prova pendente de AMANHÃ → lembrete enviado + reminded_due_date marcado + idempotente")
    void dueFitting_remindedOnceThenIdempotent() {
        UUID proposal = seedProposal("aprovada", conversationId);
        UUID fitting = seedFitting(proposal, "1ª prova", tomorrow, "pendente");

        int touched = job.runFittingReminders();

        assertThat(touched).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("1ª prova").contains("amanhã");
        assertThat(remindedDueDateOf(fitting)).isEqualTo(tomorrow);

        // 2ª passada no mesmo dia → não reenvia.
        fakeEvolution.reset();
        assertThat(job.runFittingReminders()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("toggle desligado na config → nada (opt-out por tenant)")
    void reminderDisabled_nothing() {
        jdbcTemplate.update(
            "insert into atelie_config (company_id, fitting_reminder_enabled) values (?, false)", COMPANY);
        UUID proposal = seedProposal("aprovada", conversationId);
        seedFitting(proposal, "1ª prova", tomorrow, "pendente");

        assertThat(job.runFittingReminders()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("prova realizada, prova fora da véspera e proposta terminal → nada")
    void notDue_nothing() {
        UUID viva = seedProposal("aprovada", conversationId);
        seedFitting(viva, "já realizada", tomorrow, "realizada");
        seedFitting(viva, "semana que vem", tomorrow.plusDays(6), "pendente");
        UUID cancelada = seedProposal("cancelada", conversationId);
        seedFitting(cancelada, "de proposta cancelada", tomorrow, "pendente");

        assertThat(job.runFittingReminders()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("prova REMARCADA (reminded_due_date ≠ due_date) → lembra de novo na nova véspera")
    void rescheduledFitting_remindsAgain() {
        UUID proposal = seedProposal("aprovada", conversationId);
        UUID fitting = seedFitting(proposal, "2ª prova", tomorrow, "pendente");
        // já lembrada para uma data antiga; a prova foi remarcada pra amanhã.
        jdbcTemplate.update("update atelie_fittings set reminded_due_date = ? where id = ?",
            java.sql.Date.valueOf(tomorrow.minusDays(7)), fitting);

        assertThat(job.runFittingReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(remindedDueDateOf(fitting)).isEqualTo(tomorrow);
    }

    @Test
    @DisplayName("proposta manual sem conversa → marca sem envio (não revarre eternamente)")
    void noChannel_markedWithoutSend() {
        UUID proposal = seedProposal("aprovada", null);
        UUID fitting = seedFitting(proposal, "1ª prova", tomorrow, "pendente");

        assertThat(job.runFittingReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).isEmpty();
        assertThat(remindedDueDateOf(fitting)).isEqualTo(tomorrow);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-atelie-reminder";
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeEvolutionSender fakeEvolutionSender() {
            return new FakeEvolutionSender();
        }
    }
}
