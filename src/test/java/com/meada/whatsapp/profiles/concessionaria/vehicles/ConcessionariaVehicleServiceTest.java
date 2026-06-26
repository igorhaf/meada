package com.meada.whatsapp.profiles.concessionaria.vehicles;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.concessionaria.vehicles.ConcessionariaVehicleService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.concessionaria.vehicles.ConcessionariaVehicleService.VehicleInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o ConcessionariaVehicleService (camada 8.17 — STOCK cycle, o eixo central):
 * <ul>
 *   <li>CRUD + audit;</li>
 *   <li>updateStatus disponivel→reservado→vendido OK; transição inválida (vendido→disponivel) → 409;</li>
 *   <li>a ESCAPADA: veículo 'vendido'/'reservado' NÃO entra na vitrine (listAvailable); marcar um
 *       disponível como 'vendido' o REMOVE da vitrine;</li>
 *   <li>delete-in-use → 409 vehicle_in_use.</li>
 * </ul>
 */
class ConcessionariaVehicleServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ConcessionariaVehicleService service;

    private static final UUID COMPANY = UUID.fromString("c1000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("c2000000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'concessionaria')",
            COMPANY, "Concessionária V", "conc-v");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'v@conc.dev', 'admin')",
            USER, COMPANY);
    }

    private ConcessionariaVehicle createVehicle(String model) {
        return service.create(COMPANY, USER, "Toyota", model, 2024, 0, 9000000, "Prata", "flex",
            "automatico", "ABC1D23", null, "Em ótimo estado");
    }

    @Test
    @DisplayName("create válido → persiste 'disponivel' + audita concessionaria_vehicle_created")
    void create_persistsAndAudits() {
        ConcessionariaVehicle v = createVehicle("Corolla");
        assertThat(v.status()).isEqualTo("disponivel");
        assertThat(v.brand()).isEqualTo("Toyota");
        assertThat(v.model()).isEqualTo("Corolla");
        assertThat(v.priceCents()).isEqualTo(9000000);
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'concessionaria_vehicle_created' and entity_id = ?",
            Long.class, v.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("updateStatus disponivel→reservado→vendido OK")
    void updateStatus_validCycle() {
        ConcessionariaVehicle v = createVehicle("Corolla");
        ConcessionariaVehicle reserved = service.updateStatus(COMPANY, USER, v.id(), "reservado");
        assertThat(reserved.status()).isEqualTo("reservado");
        ConcessionariaVehicle sold = service.updateStatus(COMPANY, USER, v.id(), "vendido");
        assertThat(sold.status()).isEqualTo("vendido");
    }

    @Test
    @DisplayName("transição inválida (vendido→disponivel) → InvalidStatusTransitionException (409)")
    void updateStatus_invalidTransition() {
        ConcessionariaVehicle v = createVehicle("Corolla");
        service.updateStatus(COMPANY, USER, v.id(), "vendido");
        assertThatThrownBy(() -> service.updateStatus(COMPANY, USER, v.id(), "disponivel"))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("ESCAPADA: vitrine só mostra disponível+ativo — reservado/vendido somem")
    void vitrine_onlyDisponivel() {
        ConcessionariaVehicle disp = createVehicle("Corolla");
        ConcessionariaVehicle reserved = createVehicle("Hilux");
        ConcessionariaVehicle sold = createVehicle("Yaris");
        service.updateStatus(COMPANY, USER, reserved.id(), "reservado");
        service.updateStatus(COMPANY, USER, sold.id(), "vendido");

        List<ConcessionariaVehicle> vitrine = service.listAvailable(COMPANY);
        assertThat(vitrine).extracting(ConcessionariaVehicle::id).containsExactly(disp.id());
    }

    @Test
    @DisplayName("ESCAPADA: marcar um disponível como 'vendido' o REMOVE da vitrine")
    void markSold_removesFromVitrine() {
        ConcessionariaVehicle v = createVehicle("Corolla");
        assertThat(service.listAvailable(COMPANY)).extracting(ConcessionariaVehicle::id).contains(v.id());

        service.updateStatus(COMPANY, USER, v.id(), "vendido");

        assertThat(service.listAvailable(COMPANY))
            .as("após 'vendido', o veículo não está mais na vitrine")
            .extracting(ConcessionariaVehicle::id).doesNotContain(v.id());
    }

    @Test
    @DisplayName("veículo inativo (active=false) também sai da vitrine")
    void inactive_removedFromVitrine() {
        ConcessionariaVehicle v = createVehicle("Corolla");
        service.update(COMPANY, USER, v.id(), null, null, null, false, null, false, null, null, null,
            null, null, null, null, false);
        assertThat(service.listAvailable(COMPANY)).extracting(ConcessionariaVehicle::id).doesNotContain(v.id());
    }

    @Test
    @DisplayName("delete de veículo com test-drive → VehicleInUseException (409)")
    void delete_inUse() {
        ConcessionariaVehicle v = createVehicle("Corolla");
        UUID sp = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_salespeople (id, company_id, name) values (?, ?, 'João')",
            sp, COMPANY);
        jdbcTemplate.update(
            "insert into concessionaria_test_drives (company_id, vehicle_id, salesperson_id, vehicle_brand, "
                + "vehicle_model, start_at, duration_minutes, end_at) "
                + "values (?, ?, ?, 'Toyota', 'Corolla', timestamptz '2026-07-01T18:00:00Z', 45, "
                + "timestamptz '2026-07-01T18:45:00Z')",
            COMPANY, v.id(), sp);

        assertThatThrownBy(() -> service.delete(COMPANY, USER, v.id()))
            .isInstanceOf(VehicleInUseException.class);
    }

    @Test
    @DisplayName("cache de contexto é invalidado a cada mutação (não estoura)")
    void mutationsInvalidateCache() {
        ConcessionariaVehicle v = createVehicle("Corolla");
        service.updateStatus(COMPANY, USER, v.id(), "reservado");
        // sanidade: a vitrine reflete o estado final (reservado fora).
        assertThat(service.listAvailable(COMPANY)).extracting(ConcessionariaVehicle::id).doesNotContain(v.id());
    }
}
