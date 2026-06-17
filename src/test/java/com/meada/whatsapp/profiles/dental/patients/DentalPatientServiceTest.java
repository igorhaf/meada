package com.meada.whatsapp.profiles.dental.patients;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.dental.patients.DentalPatientService.PatientInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o DentalPatientService (camada 7.4): create + audit, update parcial, delete em uso → 409.
 */
class DentalPatientServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DentalPatientService service;

    private static final UUID COMPANY = UUID.fromString("c9000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d9000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'dental')",
            COMPANY, "Clínica Teste", "clinica-teste");
        // USER em users (FK audit_log_user_id_fkey) — ver nota nos testes anteriores.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@dental.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita dental_patient_created")
    void create_persistsAndAudits() {
        DentalPatient p = service.create(COMPANY, USER, "Maria Souza", "maria@email.com",
            "+5511988887777", "12345678901", null, null, "prefere manhã");
        assertThat(p.name()).isEqualTo("Maria Souza");
        assertThat(p.email()).isEqualTo("maria@email.com");

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'dental_patient_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("update parcial (só telefone) preserva os demais campos")
    void update_partial() {
        DentalPatient p = service.create(COMPANY, USER, "João Lima", "joao@email.com",
            "+5511900000000", null, null, null, null);
        DentalPatient updated = service.update(COMPANY, USER, p.id(), null, null,
            "+5511911111111", null, null, null, null);
        assertThat(updated.phone()).isEqualTo("+5511911111111");
        assertThat(updated.name()).isEqualTo("João Lima");       // preservado
        assertThat(updated.email()).isEqualTo("joao@email.com"); // preservado
    }

    @Test
    @DisplayName("delete de paciente com consulta → PatientInUseException (409)")
    void delete_inUse() {
        DentalPatient p = service.create(COMPANY, USER, "Carlos Dias", null, null, null, null, null, null);
        Instant start = Instant.parse("2026-07-01T15:00:00Z");
        jdbcTemplate.update(
            "insert into dental_appointments (company_id, patient_id, start_at, duration_minutes, end_at, type) "
                + "values (?, ?, ?, 30, ?, 'Limpeza')",
            COMPANY, p.id(), java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(1800)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(PatientInUseException.class);
    }
}
