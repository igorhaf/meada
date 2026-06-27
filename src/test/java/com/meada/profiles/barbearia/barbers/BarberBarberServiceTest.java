package com.meada.profiles.barbearia.barbers;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.barbearia.barbers.BarberBarberService.BarberInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o BarberBarberService (camada 8.1): create+audit, toggle, delete em uso (appointment E
 * ticket) → 409 barber_in_use.
 */
class BarberBarberServiceTest extends AbstractIntegrationTest {

    @Autowired
    private BarberBarberService service;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("db000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'barbearia')",
            COMPANY, "Barbearia Teste", "barbearia-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@barber.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita barber_barber_created")
    void create_persistsAndAudits() {
        BarberBarber b = service.create(COMPANY, USER, "Marcelo", "corte/barba", null);
        assertThat(b.name()).isEqualTo("Marcelo");
        assertThat(b.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'barber_barber_created' and entity_id = ?",
            Long.class, b.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        BarberBarber b = service.create(COMPANY, USER, "Júnior", "degradê", null);
        BarberBarber off = service.toggle(COMPANY, USER, b.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de barbeiro com agendamento → BarberInUseException (409)")
    void delete_inUseByAppointment() {
        BarberBarber b = service.create(COMPANY, USER, "João", null, null);
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 30)",
            svc, COMPANY);
        Instant start = Instant.parse("2026-07-01T15:00:00Z");
        jdbcTemplate.update(
            "insert into barber_appointments (company_id, barber_id, service_id, guest_name, start_at, "
                + "duration_minutes, end_at, service_name, barber_name) "
                + "values (?, ?, ?, 'Cliente', ?, 30, ?, 'Corte', 'João')",
            COMPANY, b.id(), svc, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(1800)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, b.id()))
            .isInstanceOf(BarberInUseException.class);
    }

    @Test
    @DisplayName("delete de barbeiro com ticket de fila → BarberInUseException (409) — FK é set null, checado explícito")
    void delete_inUseByTicket() {
        BarberBarber b = service.create(COMPANY, USER, "Léo", null, null);
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 30)",
            svc, COMPANY);
        jdbcTemplate.update(
            "insert into barber_queue_tickets (company_id, barber_id, service_id, service_name, duration_minutes, guest_name) "
                + "values (?, ?, ?, 'Corte', 30, 'Cliente')",
            COMPANY, b.id(), svc);

        assertThatThrownBy(() -> service.delete(COMPANY, USER, b.id()))
            .isInstanceOf(BarberInUseException.class);
    }
}
