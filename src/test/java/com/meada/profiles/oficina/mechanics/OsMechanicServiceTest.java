package com.meada.profiles.oficina.mechanics;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.oficina.mechanics.OsMechanicService.MechanicInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o OsMechanicService (camada 7.9): create+audit, toggle, delete em uso → 409.
 */
class OsMechanicServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OsMechanicService service;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("dc000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'oficina')",
            COMPANY, "Oficina Teste", "oficina-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@oficina.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita os_mechanic_created")
    void create_persistsAndAudits() {
        OsMechanic m = service.create(COMPANY, USER, "Carlos", "Motor/suspensão", null);
        assertThat(m.name()).isEqualTo("Carlos");
        assertThat(m.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'os_mechanic_created' and entity_id = ?",
            Long.class, m.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        OsMechanic m = service.create(COMPANY, USER, "Paulo", "Elétrica/ar", null);
        OsMechanic off = service.toggle(COMPANY, USER, m.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de mecânico com OS → MechanicInUseException (409)")
    void delete_inUse() {
        OsMechanic m = service.create(COMPANY, USER, "João", "Geral", null);
        // mechanic_id é ON DELETE SET NULL — uso é checado por hasOrders(): seed de uma OS referenciando o mecânico.
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990170", "Cliente");
        UUID vehicle = UUID.randomUUID();
        jdbcTemplate.update("insert into os_vehicles (id, company_id, contact_id, plate) values (?, ?, ?, 'ABC1D23')",
            vehicle, COMPANY, contactId);
        jdbcTemplate.update(
            "insert into service_orders (company_id, contact_id, vehicle_id, mechanic_id, customer_name, "
                + "vehicle_plate, complaint, status) "
                + "values (?, ?, ?, ?, 'Cliente', 'ABC1D23', 'Barulho no motor', 'aberta')",
            COMPANY, contactId, vehicle, m.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, m.id()))
            .isInstanceOf(MechanicInUseException.class);
    }
}
