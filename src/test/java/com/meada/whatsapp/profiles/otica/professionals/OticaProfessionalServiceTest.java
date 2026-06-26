package com.meada.whatsapp.profiles.otica.professionals;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.otica.professionals.OticaProfessionalService.ProfessionalInUseException;
import com.meada.whatsapp.profiles.otica.professionals.OticaProfessionalService.ProfessionalNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o OticaProfessionalService (camada 8.12, FLUXO A): create + audit, update parcial, toggle,
 * delete em uso → 409 professional_in_use.
 */
class OticaProfessionalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OticaProfessionalService service;

    private static final UUID COMPANY = UUID.fromString("ca120000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("da120000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'otica')",
            COMPANY, "Ótica Teste", "otica-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@otica.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita otica_professional_created")
    void create_persistsAndAudits() {
        OticaProfessional p = service.create(COMPANY, USER, "Dra. Lia", "manhã");
        assertThat(p.name()).isEqualTo("Dra. Lia");
        assertThat(p.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'otica_professional_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("update parcial (só notes) preserva o nome")
    void update_partial() {
        OticaProfessional p = service.create(COMPANY, USER, "Dr. Vitor", null);
        OticaProfessional updated = service.update(COMPANY, USER, p.id(), null, "tarde", null);
        assertThat(updated.name()).isEqualTo("Dr. Vitor");
        assertThat(updated.notes()).isEqualTo("tarde");
    }

    @Test
    @DisplayName("toggle active=false")
    void toggle() {
        OticaProfessional p = service.create(COMPANY, USER, "Dr. Toggle", null);
        OticaProfessional off = service.toggle(COMPANY, USER, p.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("update inexistente → ProfessionalNotFoundException")
    void update_notFound() {
        assertThatThrownBy(() -> service.update(COMPANY, USER, UUID.randomUUID(), "X", null, null))
            .isInstanceOf(ProfessionalNotFoundException.class);
    }

    @Test
    @DisplayName("delete de profissional com exame → ProfessionalInUseException (409)")
    void delete_inUse() {
        OticaProfessional p = service.create(COMPANY, USER, "Dr. Uso", null);
        Instant start = Instant.parse("2026-07-01T18:00:00Z");
        jdbcTemplate.update(
            "insert into otica_exam_appointments (company_id, professional_id, customer_name, professional_name, "
                + "start_at, duration_minutes, end_at) values (?, ?, 'Cliente', 'Dr. Uso', ?, 30, ?)",
            COMPANY, p.id(), java.sql.Timestamp.from(start), java.sql.Timestamp.from(start.plusSeconds(1800)));
        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(ProfessionalInUseException.class);
    }
}
