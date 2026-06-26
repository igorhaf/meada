package com.meada.whatsapp.profiles.fotografia.appointments;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o EntregaMaterialHandler (camada 8.16): ENTREGA READ-ONLY do delivery_link da sessão (VERBATIM).
 * Cobre a entrega bem-sucedida (link exato + envio), a BARREIRA DE SEGURANÇA (não entregar material de
 * sessão de OUTRO contato), sessão SEM link → não entregue, e sem tag → não entregue. EvolutionSender
 * fake. Clone do EntregaPreparoHandlerTest (mas o link mora NA SESSÃO).
 */
@Import(EntregaMaterialHandlerTest.TestConfig.class)
class EntregaMaterialHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private EntregaMaterialHandler handler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("f0000000-0000-0000-0000-000000000006");
    private static final String LINK = "https://galeria.studio/marina/ensaio-2026-07";

    private UUID contactA;            // Marina
    private UUID conversationA;
    private UUID contactB;            // Pedro
    private UUID sessionWithLinkA;    // sessão do contato A, COM link
    private UUID sessionWithLinkB;    // sessão do contato B, COM link
    private UUID sessionNoLinkA;      // sessão do contato A, SEM link

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'fotografia')",
            COMPANY, "Foto E", "foto-e");

        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into fotografia_professionals (id, company_id, name) values (?, ?, 'Carla')", prof, COMPANY);
        UUID pkg = UUID.randomUUID();
        jdbcTemplate.update("insert into fotografia_packages (id, company_id, name, duration_minutes, price_cents, delivery_days) "
            + "values (?, ?, 'Ensaio 1h', 60, 50000, 7)", pkg, COMPANY);

        UUID instanceA = UUID.randomUUID();
        contactA = UUID.randomUUID();
        conversationA = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instanceA, COMPANY, "inst-a", "tok-a");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactA, COMPANY, "+5511999990300", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationA, COMPANY, contactA, instanceA);

        UUID instanceB = UUID.randomUUID();
        contactB = UUID.randomUUID();
        UUID conversationB = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instanceB, COMPANY, "inst-b", "tok-b");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactB, COMPANY, "+5511999990301", "Pedro");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationB, COMPANY, contactB, instanceB);

        sessionWithLinkA = seedSession(prof, pkg, "Marina", contactA, LINK);
        sessionWithLinkB = seedSession(prof, pkg, "Pedro", contactB, "https://galeria.studio/pedro/aniversario");
        sessionNoLinkA = seedSession(prof, pkg, "Marina", contactA, null);
    }

    private UUID seedSession(UUID prof, UUID pkg, String customerName, UUID contactId, String deliveryLink) {
        UUID id = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-10T14:00:00Z");
        jdbcTemplate.update(
            "insert into fotografia_session_appointments (id, company_id, professional_id, package_id, contact_id, "
                + "customer_name, professional_name, package_name, price_cents, duration_minutes, delivery_days, "
                + "start_at, end_at, delivery_due_date, delivery_link, status) "
                + "values (?, ?, ?, ?, ?, ?, 'Carla', 'Ensaio 1h', 50000, 60, 7, ?, ?, ?, ?, 'realizada')",
            id, COMPANY, prof, pkg, contactId, customerName,
            java.sql.Timestamp.from(start), java.sql.Timestamp.from(start.plusSeconds(3600)),
            java.sql.Date.valueOf("2026-07-17"), deliveryLink);
        return id;
    }

    @Test
    @DisplayName("entrega o material da sessão do próprio contato → envia o link EXATO + retorna true")
    void deliver_ownSession() {
        String aiText = "Aqui está sua galeria:\n<entrega_material>{\"session_id\":\"" + sessionWithLinkA + "\"}</entrega_material>";

        boolean delivered = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);

        assertThat(delivered).isTrue();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).isEqualTo(LINK);
    }

    @Test
    @DisplayName("sessão SEM delivery_link → não entregue (nada enviado)")
    void deliver_noLink() {
        String aiText = "Vou ver o material...\n<entrega_material>{\"session_id\":\"" + sessionNoLinkA + "\"}</entrega_material>";

        boolean delivered = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);

        assertThat(delivered).isFalse();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("BARREIRA: material de sessão de OUTRO contato → não entregue (não vaza)")
    void deliver_securityBarrier() {
        // sessão do Pedro (contactB), mas a conversa/contactId é da Marina (contactA).
        String aiText = "Aqui está o material:\n<entrega_material>{\"session_id\":\"" + sessionWithLinkB + "\"}</entrega_material>";

        boolean delivered = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);

        assertThat(delivered).isFalse();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("texto sem tag → não entregue")
    void deliver_noTag() {
        boolean delivered = handler.parseAndDeliver(
            COMPANY, conversationA, contactA, "Oi! Quer que eu te mande o link da sua galeria?");
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
            return "key-foto";
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
