package com.meada.profiles.nutri.appointments;

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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o EntregaPlanoHandler (camada 8.0): ENTREGA READ-ONLY do plano ativo (body VERBATIM). Cobre a
 * entrega bem-sucedida (body exato + envio), sem plano ativo → empty, a BARREIRA DE SEGURANÇA (não
 * entregar plano de paciente de outro contato) e sem tag → empty. EvolutionSender fake.
 */
@Import(EntregaPlanoHandlerTest.TestConfig.class)
class EntregaPlanoHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private EntregaPlanoHandler handler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cd000000-0000-0000-0000-000000000006");
    private static final String BODY_MARINA = "BODY DA MARINA EXATO";

    private UUID contactA;            // Marina
    private UUID conversationA;
    private UUID contactB;            // Pedro
    private UUID patientPA;           // paciente do contato A, COM plano ativo
    private UUID patientPB;           // paciente do contato B, COM plano ativo
    private UUID patientPC;           // paciente do contato A, SEM plano ativo

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')",
            COMPANY, "Nutri E", "nutri-e");

        UUID instanceA = UUID.randomUUID();
        contactA = UUID.randomUUID();
        conversationA = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instanceA, COMPANY, "inst-a", "tok-a");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactA, COMPANY, "+5511999990100", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationA, COMPANY, contactA, instanceA);

        UUID instanceB = UUID.randomUUID();
        contactB = UUID.randomUUID();
        UUID conversationB = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instanceB, COMPANY, "inst-b", "tok-b");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactB, COMPANY, "+5511999990101", "Pedro");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationB, COMPANY, contactB, instanceB);

        patientPA = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Marina')",
            patientPA, COMPANY, contactA);
        patientPB = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Pedro')",
            patientPB, COMPANY, contactB);
        patientPC = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Marina Sem Plano')",
            patientPC, COMPANY, contactA);

        seedActivePlan(patientPA, "Plano Marina", BODY_MARINA);
        seedActivePlan(patientPB, "Plano Pedro", "BODY DO PEDRO");
    }

    private void seedActivePlan(UUID patientId, String title, String body) {
        jdbcTemplate.update(
            "insert into nutri_plans (company_id, patient_id, title, body, status) values (?, ?, ?, ?, 'ativo')",
            COMPANY, patientId, title, body);
    }

    @Test
    @DisplayName("entrega para o paciente do próprio contato → devolve o body EXATO + envia esse texto")
    void deliver_ownPatient() {
        String aiText = "Aqui está seu plano:\n<entrega_plano>{\"patient_id\":\"" + patientPA + "\"}</entrega_plano>";

        Optional<String> delivered = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);

        assertThat(delivered).contains(BODY_MARINA);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).isEqualTo(BODY_MARINA);
    }

    @Test
    @DisplayName("paciente sem plano ativo → Optional.empty (nada enviado)")
    void deliver_noActivePlan() {
        String aiText = "Vou ver seu plano...\n<entrega_plano>{\"patient_id\":\"" + patientPC + "\"}</entrega_plano>";

        Optional<String> delivered = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);

        assertThat(delivered).isEmpty();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("BARREIRA: plano de paciente de OUTRO contato → Optional.empty (não vaza)")
    void deliver_securityBarrier() {
        // patient_id de Pedro (contactB), mas a conversa/contactId é da Marina (contactA).
        String aiText = "Aqui está o plano:\n<entrega_plano>{\"patient_id\":\"" + patientPB + "\"}</entrega_plano>";

        Optional<String> delivered = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);

        assertThat(delivered).isEmpty();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty")
    void deliver_noTag() {
        Optional<String> delivered = handler.parseAndDeliver(
            COMPANY, conversationA, contactA, "Oi! Quer que eu te mande seu plano alimentar?");
        assertThat(delivered).isEmpty();
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
            return "key-nutri";
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
