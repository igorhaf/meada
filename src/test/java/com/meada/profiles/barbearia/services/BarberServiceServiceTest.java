package com.meada.profiles.barbearia.services;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.barbearia.services.BarberServiceService.ServiceInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o BarberServiceService (camada 8.1): create+audit, toggle, delete em uso → 409 service_in_use.
 */
class BarberServiceServiceTest extends AbstractIntegrationTest {

    @Autowired
    private BarberServiceService service;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("db000000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'barbearia')",
            COMPANY, "Barbearia S2", "barbearia-s2");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u2@barber.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita barber_service_created")
    void create_persistsAndAudits() {
        BarberService s = service.create(COMPANY, USER, "Corte", "Cabelo", 30, 4000, null);
        assertThat(s.name()).isEqualTo("Corte");
        assertThat(s.durationMinutes()).isEqualTo(30);
        assertThat(s.priceCents()).isEqualTo(4000);
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'barber_service_created' and entity_id = ?",
            Long.class, s.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        BarberService s = service.create(COMPANY, USER, "Barba", null, 20, 2500, null);
        BarberService off = service.toggle(COMPANY, USER, s.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de serviço com agendamento → ServiceInUseException (409)")
    void delete_inUse() {
        BarberService s = service.create(COMPANY, USER, "Corte+Barba", null, 45, 6000, null);
        UUID barber = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')", barber, COMPANY);
        Instant start = Instant.parse("2026-07-01T15:00:00Z");
        jdbcTemplate.update(
            "insert into barber_appointments (company_id, barber_id, service_id, guest_name, start_at, "
                + "duration_minutes, end_at, service_name, barber_name) "
                + "values (?, ?, ?, 'Cliente', ?, 45, ?, 'Corte+Barba', 'Marcelo')",
            COMPANY, barber, s.id(), java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(2700)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, s.id()))
            .isInstanceOf(ServiceInUseException.class);
    }
}
