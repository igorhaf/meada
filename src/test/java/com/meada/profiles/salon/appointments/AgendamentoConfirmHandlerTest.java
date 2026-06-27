package com.meada.profiles.salon.appointments;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o AgendamentoConfirmHandler (camada 7.5): parse OK + create, professional inválido → empty,
 * sem tag → empty.
 */
class AgendamentoConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private AgendamentoConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("ca000000-0000-0000-0000-000000000004");
    private UUID conversationId;
    private UUID contactId;
    private UUID profId;
    private UUID serviceId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'salon')",
            COMPANY, "Salão H", "salao-h");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990060", "Joana");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        profId = UUID.randomUUID();
        jdbcTemplate.update("insert into salon_professionals (id, company_id, name) values (?, ?, 'Carla')", profId, COMPANY);
        serviceId = UUID.randomUUID();
        jdbcTemplate.update("insert into salon_offerings (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 45)",
            serviceId, COMPANY);
    }

    @Test
    @DisplayName("tag <agendamento> válida → cria agendado (guest_name do contato)")
    void parseAndCreate_ok() {
        String aiText = "Perfeito, Joana! Agendei seu Corte com a Carla pra 01/07 às 12h. Te esperamos!\n"
            + "<agendamento>{\"professional_id\":\"" + profId + "\",\"service_id\":\"" + serviceId
            + "\",\"date\":\"2026-07-01\",\"start_time\":\"12:00\",\"notes\":\"\"}</agendamento>";

        Optional<SalonAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().status()).isEqualTo("agendado");
        assertThat(a.get().professionalName()).isEqualTo("Carla");
        assertThat(a.get().serviceName()).isEqualTo("Corte");
        assertThat(a.get().guestName()).isEqualTo("Joana");
    }

    @Test
    @DisplayName("professional_id inexistente na tag → Optional.empty (não criado)")
    void parseAndCreate_invalidProfessional() {
        String aiText = "Agendado!\n<agendamento>{\"professional_id\":\"" + UUID.randomUUID()
            + "\",\"service_id\":\"" + serviceId + "\",\"date\":\"2026-07-01\",\"start_time\":\"12:00\"}</agendamento>";
        Optional<SalonAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(a).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from salon_appointments", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<SalonAppointment> a = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer agendar um horário?");
        assertThat(a).isEmpty();
    }
}
