package com.meada.profiles.dental.appointments;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o ConsultaConfirmHandler (camada 7.4): parse OK + create (paciente identificado pelo
 * contato), paciente NÃO identificado → empty, sem tag → empty.
 */
class ConsultaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private ConsultaConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("c9000000-0000-0000-0000-000000000003");
    private UUID conversationId;
    private UUID contactId;
    private UUID patientId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'dental')",
            COMPANY, "Clínica H", "clinica-h");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990040", "Maria");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        // Paciente VINCULADO ao contato (a IA resolve contact → patient).
        patientId = UUID.randomUUID();
        jdbcTemplate.update("insert into dental_patients (id, company_id, name, contact_id) values (?, ?, 'Maria Souza', ?)",
            patientId, COMPANY, contactId);
    }

    @Test
    @DisplayName("tag <consulta> com paciente identificado → cria consulta agendada")
    void parseAndCreate_ok() {
        String aiText = "Perfeito, Maria! Agendei sua Limpeza pra 01/07 às 15h. Te esperamos!\n"
            + "<consulta>{\"date\":\"2026-07-01\",\"start_time\":\"15:00\",\"type\":\"Limpeza\",\"notes\":\"\"}</consulta>";

        Optional<DentalAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().status()).isEqualTo("agendada");
        assertThat(a.get().type()).isEqualTo("Limpeza");
        assertThat(a.get().patientId()).isEqualTo(patientId);
    }

    @Test
    @DisplayName("tag <consulta> sem paciente identificado (contato sem vínculo) → Optional.empty")
    void parseAndCreate_noPatient() {
        UUID strangerContact = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            strangerContact, COMPANY, "+5511900000099", "Desconhecido");
        String aiText = "Agendado!\n<consulta>{\"date\":\"2026-07-01\",\"start_time\":\"16:00\","
            + "\"type\":\"Avaliação\",\"notes\":\"\"}</consulta>";
        Optional<DentalAppointment> a = handler.parseAndCreate(COMPANY, conversationId, strangerContact, aiText);
        assertThat(a).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from dental_appointments", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<DentalAppointment> a = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Como posso ajudar?");
        assertThat(a).isEmpty();
    }
}
