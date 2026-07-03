package com.meada.profiles.concessionaria.reminders;

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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test dos jobs da concessionária (onda 1, backlog #2/#3/#9): lembrete de test-drive
 * nas 24h (idempotente), follow-up de lead parado (re-armável por movimento) e auto-realizado
 * (confirmado com end_at passado). Lógica via métodos públicos (sem scheduler); Evolution FAKE.
 */
@Import(ConcessionariaJobsIntegrationTest.TestConfig.class)
class ConcessionariaJobsIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000087");
    private static final UUID INSTANCE = UUID.fromString("ce100000-0000-0000-0000-000000000087");

    @Autowired
    private ConcessionariaReminderJob reminderJob;
    @Autowired
    private ConcessionariaAutoTransitionJob autoTransitionJob;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private UUID vehicleId;
    private UUID salespersonId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'concessionaria')",
            COMPANY, "Conc Jobs", "conc-jobs");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
        salespersonId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-cj", "tok-cj");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Rui')",
            contactId, COMPANY, "+5511999990187");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, price_cents) "
            + "values (?, ?, 'Toyota', 'Corolla', 12000000)", vehicleId, COMPANY);
        jdbcTemplate.update("insert into concessionaria_salespeople (id, company_id, name) values (?, ?, 'Vera')",
            salespersonId, COMPANY);
    }

    private UUID seedTestDrive(String status, Instant startAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into concessionaria_test_drives (id, company_id, vehicle_id, salesperson_id, "
                + "conversation_id, contact_id, customer_name, vehicle_brand, vehicle_model, start_at, "
                + "duration_minutes, end_at, status) values (?, ?, ?, ?, ?, ?, 'Rui', 'Toyota', 'Corolla', "
                + "?, 45, ?, ?)",
            id, COMPANY, vehicleId, salespersonId, conversationId, contactId,
            java.sql.Timestamp.from(startAt), java.sql.Timestamp.from(startAt.plus(Duration.ofMinutes(45))), status);
        return id;
    }

    private UUID seedLead(String status, Instant statusUpdatedAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into concessionaria_leads (id, company_id, vehicle_id, conversation_id, contact_id, "
                + "customer_name, vehicle_brand, vehicle_model, vehicle_price_cents, payment_condition, "
                + "status, status_updated_at) values (?, ?, ?, ?, ?, 'Rui', 'Toyota', 'Corolla', 12000000, "
                + "'avista', ?, ?)",
            id, COMPANY, vehicleId, conversationId, contactId, status,
            java.sql.Timestamp.from(statusUpdatedAt));
        return id;
    }

    @Test
    @DisplayName("test-drive agendado nas próximas 24h → lembrete SIM/CANCELAR + idempotente")
    void reminder_sentOnceForUpcoming() {
        seedTestDrive("agendado", Instant.now().plus(Duration.ofHours(20)));
        seedTestDrive("agendado", Instant.now().plus(Duration.ofHours(48)));   // fora da janela

        assertThat(reminderJob.runTestDriveReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Corolla").contains("SIM");

        fakeEvolution.reset();
        assertThat(reminderJob.runTestDriveReminders()).isZero();
    }

    @Test
    @DisplayName("auto-realizado: confirmado com end_at passado (2h+) vira realizado (silencioso)")
    void autoTransition_completesPastConfirmed() {
        UUID past = seedTestDrive("confirmado", Instant.now().minus(Duration.ofHours(4)));
        seedTestDrive("confirmado", Instant.now().plus(Duration.ofHours(2)));   // futuro — intocado

        assertThat(autoTransitionJob.runAutoTransitions()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select status from concessionaria_test_drives where id = ?", String.class, past))
            .isEqualTo("realizado");
        assertThat(fakeEvolution.sent()).isEmpty();   // realizado é silencioso
    }

    @Test
    @DisplayName("lead parado (status_updated_at antigo) → follow-up 1x; re-arma só com novo movimento")
    void followup_staleLead() {
        UUID stale = seedLead("em_negociacao", Instant.now().minus(Duration.ofDays(5)));
        seedLead("em_negociacao", Instant.now().minus(Duration.ofHours(2)));   // fresco — intocado
        seedLead("fechado", Instant.now().minus(Duration.ofDays(30)));         // terminal — intocado

        assertThat(autoTransitionJob.runAutoTransitions()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Corolla");

        // 2ª passada: followup_sent_at > status_updated_at → não repete.
        fakeEvolution.reset();
        assertThat(autoTransitionJob.runAutoTransitions()).isZero();

        // lead volta a se mover e estagna de novo → re-arma.
        jdbcTemplate.update(
            "update concessionaria_leads set status_updated_at = now() + interval '1 second' where id = ?", stale);
        jdbcTemplate.update(
            "update concessionaria_leads set status_updated_at = now() - interval '10 days', "
                + "followup_sent_at = now() - interval '11 days' where id = ?", stale);
        assertThat(autoTransitionJob.runAutoTransitions()).isEqualTo(1);
    }

    @Test
    @DisplayName("toggles desligados na config → jobs não agem")
    void togglesOff_nothing() {
        jdbcTemplate.update(
            "insert into concessionaria_config (company_id, testdrive_reminder_enabled, followup_enabled, "
                + "auto_complete_enabled) values (?, false, false, false)", COMPANY);
        seedTestDrive("agendado", Instant.now().plus(Duration.ofHours(3)));
        seedTestDrive("confirmado", Instant.now().minus(Duration.ofHours(5)));
        seedLead("novo", Instant.now().minus(Duration.ofDays(10)));

        assertThat(reminderJob.runTestDriveReminders()).isZero();
        assertThat(autoTransitionJob.runAutoTransitions()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-conc-jobs";
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
