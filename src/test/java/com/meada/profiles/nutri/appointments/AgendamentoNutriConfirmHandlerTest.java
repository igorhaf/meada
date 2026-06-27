package com.meada.profiles.nutri.appointments;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o AgendamentoNutriConfirmHandler (camada 8.0): parse OK + create nos 2 MODOS — patient_id
 * existente E new_patient (cadastra paciente + agenda) —, professional inválido → empty, sem tag → empty.
 */
class AgendamentoNutriConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private AgendamentoNutriConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("cd000000-0000-0000-0000-000000000005");
    private UUID conversationId;
    private UUID contactId;
    private UUID profId;
    private UUID patientId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')",
            COMPANY, "Nutri H", "nutri-h");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990095", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        profId = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_professionals (id, company_id, name) values (?, ?, 'Carla')", profId, COMPANY);
        patientId = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Marina')",
            patientId, COMPANY, contactId);
    }

    @Test
    @DisplayName("MODO patient_id existente → cria agendado para o paciente informado")
    void parseAndCreate_existingPatient() {
        String aiText = "Perfeito, Marina! Agendei sua primeira consulta com a Carla pra 01/07 às 11h.\n"
            + "<consulta_nutri>{\"professional_id\":\"" + profId + "\",\"patient_id\":\"" + patientId
            + "\",\"appointment_type\":\"primeira\",\"date\":\"2026-07-01\",\"start_time\":\"11:00\",\"notes\":\"\"}</consulta_nutri>";

        Optional<NutriAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().status()).isEqualTo("agendado");
        assertThat(a.get().professionalName()).isEqualTo("Carla");
        assertThat(a.get().patientName()).isEqualTo("Marina");
        assertThat(a.get().appointmentType()).isEqualTo("primeira");
    }

    @Test
    @DisplayName("MODO new_patient → cadastra o paciente E agenda (count de pacientes sobe)")
    void parseAndCreate_newPatient() {
        Long before = jdbcTemplate.queryForObject("select count(*) from nutri_patients where company_id = ?",
            Long.class, COMPANY);

        String aiText = "Cadastrei o Bruno e já agendei!\n"
            + "<consulta_nutri>{\"professional_id\":\"" + profId
            + "\",\"new_patient\":{\"name\":\"Bruno\",\"goal\":\"Ganho de massa\"},"
            + "\"appointment_type\":\"primeira\",\"date\":\"2026-07-01\",\"start_time\":\"12:00\"}</consulta_nutri>";

        Optional<NutriAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().patientName()).isEqualTo("Bruno");
        Long after = jdbcTemplate.queryForObject("select count(*) from nutri_patients where company_id = ?",
            Long.class, COMPANY);
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("professional_id inexistente na tag → Optional.empty (não criado)")
    void parseAndCreate_invalidProfessional() {
        String aiText = "Agendado!\n<consulta_nutri>{\"professional_id\":\"" + UUID.randomUUID()
            + "\",\"patient_id\":\"" + patientId + "\",\"appointment_type\":\"primeira\","
            + "\"date\":\"2026-07-01\",\"start_time\":\"11:00\"}</consulta_nutri>";
        Optional<NutriAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(a).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from nutri_appointments", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<NutriAppointment> a = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer marcar uma consulta de nutrição?");
        assertThat(a).isEmpty();
    }
}
