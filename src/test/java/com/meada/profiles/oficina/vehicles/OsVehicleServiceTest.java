package com.meada.profiles.oficina.vehicles;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.oficina.vehicles.OsVehicleService.ContactNotFoundException;
import com.meada.profiles.oficina.vehicles.OsVehicleService.PlateTakenException;
import com.meada.profiles.oficina.vehicles.OsVehicleService.VehicleInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o OsVehicleService (camada 7.9): create+audit, contato inexistente → ContactNotFoundException,
 * placa duplicada → PlateTakenException, archive (active=false), delete em uso → VehicleInUseException.
 */
class OsVehicleServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OsVehicleService service;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("dc000000-0000-0000-0000-000000000002");
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'oficina')",
            COMPANY, "Oficina A", "oficina-a");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@oficina-a.dev', 'admin')",
            USER, COMPANY);
        contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990180", "Cliente");
    }

    @Test
    @DisplayName("create válido → persiste + audita os_vehicle_created")
    void create_persistsAndAudits() {
        OsVehicle v = service.create(COMPANY, USER, contactId, "ABC1D23", "Fiat", "Uno", 2018, "Prata", 80000, null);
        assertThat(v.plate()).isEqualTo("ABC1D23");
        assertThat(v.brand()).isEqualTo("Fiat");
        assertThat(v.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'os_vehicle_created' and entity_id = ?",
            Long.class, v.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com contato inexistente → ContactNotFoundException")
    void create_unknownContact() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, UUID.randomUUID(), "XYZ4E56", null, null, null, null, null, null))
            .isInstanceOf(ContactNotFoundException.class);
    }

    @Test
    @DisplayName("create com placa duplicada → PlateTakenException")
    void create_plateTaken() {
        service.create(COMPANY, USER, contactId, "DUP1A11", "Fiat", "Uno", null, null, null, null);
        assertThatThrownBy(() -> service.create(COMPANY, USER, contactId, "DUP1A11", "VW", "Gol", null, null, null, null))
            .isInstanceOf(PlateTakenException.class);
    }

    @Test
    @DisplayName("archive → active=false")
    void archive() {
        OsVehicle v = service.create(COMPANY, USER, contactId, "ARC1H99", "VW", "Gol", null, null, null, null);
        OsVehicle archived = service.archive(COMPANY, USER, v.id());
        assertThat(archived.active()).isFalse();
    }

    @Test
    @DisplayName("delete de veículo com OS → VehicleInUseException")
    void delete_inUse() {
        OsVehicle v = service.create(COMPANY, USER, contactId, "USE1B22", "Fiat", "Uno", null, null, null, null);
        jdbcTemplate.update(
            "insert into service_orders (company_id, contact_id, vehicle_id, customer_name, "
                + "vehicle_plate, complaint, status) "
                + "values (?, ?, ?, 'Cliente', 'USE1B22', 'Barulho no motor', 'aberta')",
            COMPANY, contactId, v.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, v.id()))
            .isInstanceOf(VehicleInUseException.class);
    }
}
