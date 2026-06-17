package com.meada.whatsapp.profiles.salon.professionals;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.salon.professionals.SalonProfessionalService.ProfessionalInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o SalonProfessionalService (camada 7.5): create+audit, toggle, delete em uso → 409.
 */
class SalonProfessionalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SalonProfessionalService service;

    private static final UUID COMPANY = UUID.fromString("ca000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("da000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'salon')",
            COMPANY, "Salão Teste", "salao-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@salon.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita salon_professional_created")
    void create_persistsAndAudits() {
        SalonProfessional p = service.create(COMPANY, USER, "Carla", "Cabeleireira", null);
        assertThat(p.name()).isEqualTo("Carla");
        assertThat(p.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'salon_professional_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        SalonProfessional p = service.create(COMPANY, USER, "Patrícia", "Manicure", null);
        SalonProfessional off = service.toggle(COMPANY, USER, p.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de profissional com agendamento → ProfessionalInUseException (409)")
    void delete_inUse() {
        SalonProfessional p = service.create(COMPANY, USER, "Júlia", "Esteticista", null);
        // seed de um serviço + agendamento referenciando o profissional (FK restrict).
        UUID offering = UUID.randomUUID();
        jdbcTemplate.update("insert into salon_offerings (id, company_id, name, duration_minutes) values (?, ?, 'Limpeza', 90)",
            offering, COMPANY);
        Instant start = Instant.parse("2026-07-01T15:00:00Z");
        jdbcTemplate.update(
            "insert into salon_appointments (company_id, professional_id, service_id, guest_name, start_at, "
                + "duration_minutes, end_at, service_name, professional_name) "
                + "values (?, ?, ?, 'Cliente', ?, 90, ?, 'Limpeza', 'Júlia')",
            COMPANY, p.id(), offering, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(5400)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(ProfessionalInUseException.class);
    }
}
