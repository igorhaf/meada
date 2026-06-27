package com.meada.profiles.barbearia.appointments;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o AgendamentoBarbeariaConfirmHandler (camada 8.1, clone salon): parse OK + create, barbeiro
 * inválido → empty, sem tag → empty. Tag <agendamento_barbearia> (namespace próprio).
 */
class AgendamentoBarbeariaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private AgendamentoBarbeariaConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000005");
    private UUID conversationId;
    private UUID contactId;
    private UUID barberId;
    private UUID serviceId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'barbearia')",
            COMPANY, "Barbearia H", "barbearia-h");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990080", "Pedro");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        barberId = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')", barberId, COMPANY);
        serviceId = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 30)",
            serviceId, COMPANY);
    }

    @Test
    @DisplayName("tag <agendamento_barbearia> válida → cria agendado (guest_name do contato)")
    void parseAndCreate_ok() {
        String aiText = "Beleza, Pedro! Marquei seu Corte com o Marcelo pra 01/07 às 12h.\n"
            + "<agendamento_barbearia>{\"barber_id\":\"" + barberId + "\",\"service_id\":\"" + serviceId
            + "\",\"date\":\"2026-07-01\",\"start_time\":\"12:00\",\"notes\":\"\"}</agendamento_barbearia>";

        Optional<BarberAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().status()).isEqualTo("agendado");
        assertThat(a.get().barberName()).isEqualTo("Marcelo");
        assertThat(a.get().serviceName()).isEqualTo("Corte");
        assertThat(a.get().guestName()).isEqualTo("Pedro");
    }

    @Test
    @DisplayName("barber_id inexistente na tag → Optional.empty (não criado)")
    void parseAndCreate_invalidBarber() {
        String aiText = "Marcado!\n<agendamento_barbearia>{\"barber_id\":\"" + UUID.randomUUID()
            + "\",\"service_id\":\"" + serviceId + "\",\"date\":\"2026-07-01\",\"start_time\":\"12:00\"}</agendamento_barbearia>";
        Optional<BarberAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(a).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from barber_appointments", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<BarberAppointment> a = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "E aí! Quer marcar um horário ou entrar na fila?");
        assertThat(a).isEmpty();
    }
}
