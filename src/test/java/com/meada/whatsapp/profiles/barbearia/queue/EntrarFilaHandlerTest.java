package com.meada.whatsapp.profiles.barbearia.queue;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o EntrarFilaHandler (camada 8.1): cria ticket pela tag <fila_barbearia>; barber_id null
 * aceito (fila geral); service inválido → empty; queue_enabled=false → no-op; sem tag → empty.
 */
class EntrarFilaHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private EntrarFilaHandler handler;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000006");
    private UUID conversationId;
    private UUID contactId;
    private UUID barberId;
    private UUID serviceId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'barbearia')",
            COMPANY, "Barbearia F", "barbearia-f");
        jdbcTemplate.update("insert into barber_config (company_id, queue_enabled) values (?, true)", COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990090", "Bruno");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        barberId = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')", barberId, COMPANY);
        serviceId = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 30)",
            serviceId, COMPANY);
    }

    @Test
    @DisplayName("tag <fila_barbearia> com barbeiro → cria ticket aguardando com posição")
    void enqueue_withBarber() {
        String aiText = "Te coloquei na fila do Marcelo, Bruno!\n"
            + "<fila_barbearia>{\"service_id\":\"" + serviceId + "\",\"barber_id\":\"" + barberId + "\"}</fila_barbearia>";
        Optional<BarberQueueTicket> t = handler.parseAndEnqueue(COMPANY, conversationId, contactId, aiText);
        assertThat(t).isPresent();
        assertThat(t.get().status()).isEqualTo("aguardando");
        assertThat(t.get().barberName()).isEqualTo("Marcelo");
        assertThat(t.get().position()).isEqualTo(1);
        assertThat(t.get().guestName()).isEqualTo("Bruno");
    }

    @Test
    @DisplayName("barber_id null → fila geral (aceito)")
    void enqueue_generalQueue() {
        String aiText = "Você entrou na fila geral!\n"
            + "<fila_barbearia>{\"service_id\":\"" + serviceId + "\",\"barber_id\":null}</fila_barbearia>";
        Optional<BarberQueueTicket> t = handler.parseAndEnqueue(COMPANY, conversationId, contactId, aiText);
        assertThat(t).isPresent();
        assertThat(t.get().barberId()).isNull();
        assertThat(t.get().position()).isEqualTo(1);
    }

    @Test
    @DisplayName("service_id inválido → Optional.empty (não enfileirado)")
    void enqueue_invalidService() {
        String aiText = "Ok!\n<fila_barbearia>{\"service_id\":\"" + UUID.randomUUID() + "\"}</fila_barbearia>";
        Optional<BarberQueueTicket> t = handler.parseAndEnqueue(COMPANY, conversationId, contactId, aiText);
        assertThat(t).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from barber_queue_tickets", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("queue_enabled=false → no-op (nenhum ticket criado)")
    void enqueue_queueDisabled() {
        jdbcTemplate.update("update barber_config set queue_enabled = false where company_id = ?", COMPANY);
        String aiText = "Fila!\n<fila_barbearia>{\"service_id\":\"" + serviceId + "\"}</fila_barbearia>";
        Optional<BarberQueueTicket> t = handler.parseAndEnqueue(COMPANY, conversationId, contactId, aiText);
        assertThat(t).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from barber_queue_tickets", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty")
    void enqueue_noTag() {
        Optional<BarberQueueTicket> t = handler.parseAndEnqueue(
            COMPANY, conversationId, contactId, "Quer marcar ou entrar na fila?");
        assertThat(t).isEmpty();
    }
}
