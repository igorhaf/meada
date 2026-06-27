package com.meada.profiles.salon.offerings;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.salon.offerings.SalonOfferingService.OfferingInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o SalonOfferingService (camada 7.5): create+audit, update parcial, delete em uso → 409.
 */
class SalonOfferingServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SalonOfferingService service;

    private static final UUID COMPANY = UUID.fromString("ca000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("da000000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'salon')",
            COMPANY, "Salão O", "salao-o");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@salon-o.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita salon_offering_created")
    void create_persistsAndAudits() {
        SalonOffering o = service.create(COMPANY, USER, "Corte feminino", "Cabelo", 45, 8000, null);
        assertThat(o.name()).isEqualTo("Corte feminino");
        assertThat(o.durationMinutes()).isEqualTo(45);
        assertThat(o.priceCents()).isEqualTo(8000);
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'salon_offering_created' and entity_id = ?",
            Long.class, o.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("update parcial (só duração) preserva os demais campos")
    void update_partial() {
        SalonOffering o = service.create(COMPANY, USER, "Escova", "Cabelo", 60, 6000, null);
        SalonOffering updated = service.update(COMPANY, USER, o.id(), null, null, 75, null, null, null);
        assertThat(updated.durationMinutes()).isEqualTo(75);
        assertThat(updated.name()).isEqualTo("Escova");       // preservado
        assertThat(updated.priceCents()).isEqualTo(6000);     // preservado
    }

    @Test
    @DisplayName("delete de serviço com agendamento → OfferingInUseException (409)")
    void delete_inUse() {
        SalonOffering o = service.create(COMPANY, USER, "Coloração", "Cabelo", 180, 25000, null);
        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into salon_professionals (id, company_id, name) values (?, ?, 'Carla')",
            prof, COMPANY);
        Instant start = Instant.parse("2026-07-01T15:00:00Z");
        jdbcTemplate.update(
            "insert into salon_appointments (company_id, professional_id, service_id, guest_name, start_at, "
                + "duration_minutes, end_at, service_name, professional_name) "
                + "values (?, ?, ?, 'Cliente', ?, 180, ?, 'Coloração', 'Carla')",
            COMPANY, prof, o.id(), java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(10800)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, o.id()))
            .isInstanceOf(OfferingInUseException.class);
    }
}
