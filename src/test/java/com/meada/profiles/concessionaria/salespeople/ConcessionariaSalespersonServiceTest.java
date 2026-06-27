package com.meada.profiles.concessionaria.salespeople;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.concessionaria.salespeople.ConcessionariaSalespersonService.SalespersonInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o ConcessionariaSalespersonService (camada 8.17): create+audit, toggle, delete em uso
 * (vendedor com test-drive OU lead) → 409 salesperson_in_use.
 */
class ConcessionariaSalespersonServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ConcessionariaSalespersonService service;

    private static final UUID COMPANY = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("c2000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'concessionaria')",
            COMPANY, "Concessionária Teste", "conc-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@conc.dev', 'admin')",
            USER, COMPANY);
    }

    private UUID seedVehicle() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, price_cents) "
            + "values (?, ?, 'Toyota', 'Corolla', 9000000)", id, COMPANY);
        return id;
    }

    @Test
    @DisplayName("create válido → persiste + audita concessionaria_salesperson_created")
    void create_persistsAndAudits() {
        ConcessionariaSalesperson sp = service.create(COMPANY, USER, "Carlos", "+5511999990000", null);
        assertThat(sp.name()).isEqualTo("Carlos");
        assertThat(sp.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'concessionaria_salesperson_created' and entity_id = ?",
            Long.class, sp.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        ConcessionariaSalesperson sp = service.create(COMPANY, USER, "Paulo", null, null);
        ConcessionariaSalesperson off = service.toggle(COMPANY, USER, sp.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de vendedor com TEST-DRIVE → SalespersonInUseException (409)")
    void delete_inUse_testDrive() {
        ConcessionariaSalesperson sp = service.create(COMPANY, USER, "João", null, null);
        UUID vehicle = seedVehicle();
        jdbcTemplate.update(
            "insert into concessionaria_test_drives (company_id, vehicle_id, salesperson_id, vehicle_brand, "
                + "vehicle_model, start_at, duration_minutes, end_at) "
                + "values (?, ?, ?, 'Toyota', 'Corolla', ?, 45, ?)",
            COMPANY, vehicle, sp.id(), Timestamp.from(Instant.parse("2026-07-01T18:00:00Z")),
            Timestamp.from(Instant.parse("2026-07-01T18:45:00Z")));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, sp.id()))
            .isInstanceOf(SalespersonInUseException.class);
    }

    @Test
    @DisplayName("delete de vendedor com LEAD (salesperson_id é SET NULL na FK) → SalespersonInUseException (409)")
    void delete_inUse_lead() {
        ConcessionariaSalesperson sp = service.create(COMPANY, USER, "Marta", null, null);
        UUID vehicle = seedVehicle();
        jdbcTemplate.update(
            "insert into concessionaria_leads (company_id, vehicle_id, vehicle_brand, vehicle_model, "
                + "vehicle_price_cents, salesperson_id) values (?, ?, 'Toyota', 'Corolla', 9000000, ?)",
            COMPANY, vehicle, sp.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, sp.id()))
            .isInstanceOf(SalespersonInUseException.class);
    }

    @Test
    @DisplayName("delete de vendedor sem referência → remove")
    void delete_free() {
        ConcessionariaSalesperson sp = service.create(COMPANY, USER, "Livre", null, null);
        service.delete(COMPANY, USER, sp.id());
        assertThat(service.get(COMPANY, sp.id())).isEmpty();
    }
}
