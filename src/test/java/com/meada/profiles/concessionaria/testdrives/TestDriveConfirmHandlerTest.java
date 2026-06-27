package com.meada.profiles.concessionaria.testdrives;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o TestDriveConfirmHandler (camada 8.17): tag válida → cria; veículo indisponível → empty;
 * conflito → empty; sem tag → empty.
 */
class TestDriveConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private TestDriveConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("c1000000-0000-0000-0000-000000000004");
    private UUID vehicleId;
    private UUID salespersonId;
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'concessionaria')",
            COMPANY, "Concessionária HD", "conc-hd");
        vehicleId = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, model_year, price_cents) "
            + "values (?, ?, 'Toyota', 'Corolla', 2024, 9000000)", vehicleId, COMPANY);
        salespersonId = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_salespeople (id, company_id, name) values (?, ?, 'Carlos')",
            salespersonId, COMPANY);

        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990180", "Maria");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private String tag(UUID vehicle, UUID sp) {
        return "Perfeito! Agendei seu test-drive.\n"
            + "<testdrive_carro>{\"vehicle_id\":\"" + vehicle + "\",\"salesperson_id\":\"" + sp + "\","
            + "\"date\":\"2026-07-01\",\"start_time\":\"15:00\",\"notes\":null}</testdrive_carro>";
    }

    @Test
    @DisplayName("tag válida → cria test-drive agendado")
    void parseAndCreate_ok() {
        Optional<ConcessionariaTestDrive> td =
            handler.parseAndCreate(COMPANY, conversationId, contactId, tag(vehicleId, salespersonId));
        assertThat(td).isPresent();
        assertThat(td.get().status()).isEqualTo("agendado");
        assertThat(td.get().vehicleId()).isEqualTo(vehicleId);
        assertThat(td.get().salespersonId()).isEqualTo(salespersonId);
    }

    @Test
    @DisplayName("veículo indisponível (vendido) → Optional.empty, nada criado")
    void parseAndCreate_vehicleUnavailable() {
        jdbcTemplate.update("update concessionaria_vehicles set status = 'vendido' where id = ?", vehicleId);
        Optional<ConcessionariaTestDrive> td =
            handler.parseAndCreate(COMPANY, conversationId, contactId, tag(vehicleId, salespersonId));
        assertThat(td).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from concessionaria_test_drives", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("conflito de slot (mesmo vendedor já ocupado) → Optional.empty")
    void parseAndCreate_conflict() {
        jdbcTemplate.update(
            "insert into concessionaria_test_drives (company_id, vehicle_id, salesperson_id, vehicle_brand, "
                + "vehicle_model, start_at, duration_minutes, end_at) "
                + "values (?, ?, ?, 'Toyota', 'Corolla', timestamptz '2026-07-01T18:00:00Z', 45, "
                + "timestamptz '2026-07-01T18:45:00Z')",
            COMPANY, vehicleId, salespersonId);

        Optional<ConcessionariaTestDrive> td =
            handler.parseAndCreate(COMPANY, conversationId, contactId, tag(vehicleId, salespersonId));
        assertThat(td).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from concessionaria_test_drives", Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<ConcessionariaTestDrive> td =
            handler.parseAndCreate(COMPANY, conversationId, contactId, "Oi! Quer agendar um test-drive?");
        assertThat(td).isEmpty();
    }
}
