package com.meada.profiles.cursos.enrollments;

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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o EntregaModuloHandler (camada 8.20 / perfil cursos, ESCAPADA 2): ENTREGA READ-ONLY do
 * PRÓXIMO módulo da matrícula (content VERBATIM) + REGISTRO de progresso (a 2ª chamada entrega o 2º
 * módulo). Cobre a entrega bem-sucedida + avanço, a trilha concluída → no-op, e a BARREIRA DE
 * SEGURANÇA (não entregar módulo de matrícula de OUTRO contato). EvolutionSender fake. Clone do
 * EntregaMaterialHandlerTest (camada 8.16).
 */
@Import(EntregaModuloHandlerTest.TestConfig.class)
class EntregaModuloHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private EntregaModuloHandler handler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cc100000-0000-0000-0000-000000000003");
    private static final String MODULE1_CONTENT = "Módulo 1: Verbo to be. Estude as páginas 1 a 10.";
    private static final String MODULE2_CONTENT = "Módulo 2: Present simple. Exercícios 1 a 5.";

    private UUID contactA;            // Marina
    private UUID conversationA;
    private UUID enrollmentA;         // matrícula da Marina
    private UUID enrollmentB;         // matrícula do Pedro (outro contato)
    private UUID course;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'cursos')",
            COMPANY, "Curso E", "curso-e");

        course = UUID.randomUUID();
        jdbcTemplate.update("insert into cursos_courses (id, company_id, title, monthly_cents) "
            + "values (?, ?, 'Inglês', 15000)", course, COMPANY);
        // módulos ordenados (position 0, 1).
        jdbcTemplate.update("insert into cursos_modules (company_id, course_id, position, title, content) "
            + "values (?, ?, 0, 'Verbo to be', ?)", COMPANY, course, MODULE1_CONTENT);
        jdbcTemplate.update("insert into cursos_modules (company_id, course_id, position, title, content) "
            + "values (?, ?, 1, 'Present simple', ?)", COMPANY, course, MODULE2_CONTENT);

        UUID instanceA = UUID.randomUUID();
        contactA = UUID.randomUUID();
        conversationA = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instanceA, COMPANY, "inst-a", "tok-a");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactA, COMPANY, "+5511999991300", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationA, COMPANY, contactA, instanceA);

        UUID contactB = UUID.randomUUID();
        UUID instanceB = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instanceB, COMPANY, "inst-b", "tok-b");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactB, COMPANY, "+5511999991301", "Pedro");

        enrollmentA = seedEnrollment(contactA, conversationA, "Marina");
        enrollmentB = seedEnrollment(contactB, null, "Pedro");
    }

    private UUID seedEnrollment(UUID contactId, UUID conversationId, String studentName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into cursos_enrollments (id, company_id, course_id, conversation_id, contact_id, "
                + "student_name, course_title, course_monthly_cents, status) "
                + "values (?, ?, ?, ?, ?, ?, 'Inglês', 15000, 'ativa')",
            id, COMPANY, course, conversationId, contactId, studentName);
        return id;
    }

    @Test
    @DisplayName("entrega o PRÓXIMO módulo do próprio contato → envia content VERBATIM + registra progresso; 2ª chamada entrega o 2º")
    void deliver_advancesThroughModules() {
        String aiText1 = "Aqui está seu próximo módulo:\n<entrega_modulo>{\"enrollment_id\":\"" + enrollmentA + "\"}</entrega_modulo>";
        boolean delivered1 = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText1);

        assertThat(delivered1).isTrue();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).isEqualTo(MODULE1_CONTENT);
        // progresso registrado.
        Integer progress = jdbcTemplate.queryForObject(
            "select count(*) from cursos_enrollment_progress where enrollment_id = ?", Integer.class, enrollmentA);
        assertThat(progress).isEqualTo(1);

        // 2ª chamada → entrega o 2º módulo.
        boolean delivered2 = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText1);
        assertThat(delivered2).isTrue();
        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().get(1).text()).isEqualTo(MODULE2_CONTENT);
    }

    @Test
    @DisplayName("todos os módulos concluídos → no-op (nada enviado)")
    void deliver_allDone() {
        String aiText = "<entrega_modulo>{\"enrollment_id\":\"" + enrollmentA + "\"}</entrega_modulo>";
        // entrega os 2 módulos.
        handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);
        handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);
        fakeEvolution.reset();
        // 3ª chamada → trilha concluída, nada a entregar.
        boolean delivered = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);
        assertThat(delivered).isFalse();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("BARREIRA: módulo de matrícula de OUTRO contato → não entregue (não vaza)")
    void deliver_securityBarrier() {
        // matrícula do Pedro (enrollmentB), mas a conversa/contactId é da Marina (contactA).
        String aiText = "<entrega_modulo>{\"enrollment_id\":\"" + enrollmentB + "\"}</entrega_modulo>";
        boolean delivered = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);
        assertThat(delivered).isFalse();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("texto sem tag → não entregue")
    void deliver_noTag() {
        boolean delivered = handler.parseAndDeliver(
            COMPANY, conversationA, contactA, "Oi! Quer que eu te mande o próximo módulo?");
        assertThat(delivered).isFalse();
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
            return "key-cur";
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
