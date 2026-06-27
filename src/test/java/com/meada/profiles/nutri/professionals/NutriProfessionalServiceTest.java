package com.meada.profiles.nutri.professionals;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.nutri.professionals.NutriProfessionalService.ProfessionalInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o NutriProfessionalService (camada 8.0): create+audit, toggle, delete em uso → 409.
 */
class NutriProfessionalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private NutriProfessionalService service;

    private static final UUID COMPANY = UUID.fromString("cd000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("dd000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')",
            COMPANY, "Nutri Teste", "nutri-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@nutri.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita nutri_professional_created")
    void create_persistsAndAudits() {
        NutriProfessional p = service.create(COMPANY, USER, "Carla", "Nutrição clínica", "CRN-3 12345", null);
        assertThat(p.name()).isEqualTo("Carla");
        assertThat(p.specialty()).isEqualTo("Nutrição clínica");
        assertThat(p.crn()).isEqualTo("CRN-3 12345");
        assertThat(p.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'nutri_professional_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        NutriProfessional p = service.create(COMPANY, USER, "Patrícia", "Nutrição esportiva", null, null);
        NutriProfessional off = service.toggle(COMPANY, USER, p.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de profissional com consulta → ProfessionalInUseException (409)")
    void delete_inUse() {
        NutriProfessional p = service.create(COMPANY, USER, "Júlia", "Nutrição clínica", null, null);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990070", "Cliente");
        UUID patientId = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Cliente')",
            patientId, COMPANY, contactId);
        Instant start = Instant.parse("2026-07-01T14:00:00Z");
        jdbcTemplate.update(
            "insert into nutri_appointments (company_id, professional_id, patient_id, contact_id, patient_name, "
                + "professional_name, appointment_type, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, 'Cliente', 'Júlia', 'primeira', 60, ?, ?, 'agendado')",
            COMPANY, p.id(), patientId, contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(3600)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(ProfessionalInUseException.class);
    }
}
