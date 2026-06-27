package com.meada.profiles.nutri.patients;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.nutri.patients.NutriPatientService.ContactNotFoundException;
import com.meada.profiles.nutri.patients.NutriPatientService.PatientInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o NutriPatientService (camada 8.0): create+audit, contato inexistente → ContactNotFoundException,
 * archive (active=false), delete em uso → PatientInUseException.
 */
class NutriPatientServiceTest extends AbstractIntegrationTest {

    @Autowired
    private NutriPatientService service;

    private static final UUID COMPANY = UUID.fromString("cd000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("dd000000-0000-0000-0000-000000000002");
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')",
            COMPANY, "Nutri A", "nutri-a");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@nutri-a.dev', 'admin')",
            USER, COMPANY);
        contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990080", "Marina");
    }

    @Test
    @DisplayName("create válido → persiste + audita nutri_patient_created")
    void create_persistsAndAudits() {
        NutriPatient p = service.create(COMPANY, USER, contactId, "Marina", "Emagrecimento",
            "Sem lactose", LocalDate.of(1990, 5, 20), null);
        assertThat(p.name()).isEqualTo("Marina");
        assertThat(p.goal()).isEqualTo("Emagrecimento");
        assertThat(p.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'nutri_patient_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com contato inexistente → ContactNotFoundException")
    void create_unknownContact() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, UUID.randomUUID(), "Marina", null, null, null, null))
            .isInstanceOf(ContactNotFoundException.class);
    }

    @Test
    @DisplayName("archive → active=false")
    void archive() {
        NutriPatient p = service.create(COMPANY, USER, contactId, "Marina", null, null, null, null);
        NutriPatient archived = service.archive(COMPANY, USER, p.id());
        assertThat(archived.active()).isFalse();
    }

    @Test
    @DisplayName("delete de paciente com consulta → PatientInUseException")
    void delete_inUse() {
        NutriPatient p = service.create(COMPANY, USER, contactId, "Marina", null, null, null, null);
        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_professionals (id, company_id, name) values (?, ?, 'Carla')",
            prof, COMPANY);
        Instant start = Instant.parse("2026-07-01T14:00:00Z");
        jdbcTemplate.update(
            "insert into nutri_appointments (company_id, professional_id, patient_id, contact_id, patient_name, "
                + "professional_name, appointment_type, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, 'Marina', 'Carla', 'primeira', 60, ?, ?, 'agendado')",
            COMPANY, prof, p.id(), contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(3600)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(PatientInUseException.class);
    }
}
