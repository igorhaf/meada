package com.meada.profiles.concessionaria.testdrives;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.ConflictException;
import com.meada.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.InvalidStatusTransitionException;
import com.meada.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.OutsideHoursException;
import com.meada.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.VehicleNotAvailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o ConcessionariaTestDriveService (camada 8.17 — AGENDA, o eixo-conflito):
 * <ul>
 *   <li>create OK: end_at materializado = start + duração (45min default); snapshots de veículo;</li>
 *   <li>conflito POR vendedor → ConflictException; mesmo horário, vendedor DIFERENTE → OK (paralelismo);</li>
 *   <li>fora do horário → OutsideHoursException;</li>
 *   <li>veículo NÃO-disponível (reservado/vendido) → VehicleNotAvailableException;</li>
 *   <li>transições de status (agendado→confirmado→realizado; cancelado/no_show; inválida → 409).</li>
 * </ul>
 */
@Import(ConcessionariaTestDriveServiceTest.TestConfig.class)
class ConcessionariaTestDriveServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ConcessionariaTestDriveService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c1000000-0000-0000-0000-000000000003");

    // 2026-07-01T15:00-03:00 (BRT) → dentro da janela 09:00–18:00; dura 45min (termina 15:45 BRT).
    private static final Instant START = Instant.parse("2026-07-01T18:00:00Z");

    private UUID vehicleId;
    private UUID salespersonA;
    private UUID salespersonB;
    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'concessionaria')",
            COMPANY, "Concessionária TD", "conc-td");
        vehicleId = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, model_year, price_cents) "
            + "values (?, ?, 'Toyota', 'Corolla', 2024, 9000000)", vehicleId, COMPANY);
        salespersonA = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_salespeople (id, company_id, name) values (?, ?, 'Carlos')",
            salespersonA, COMPANY);
        salespersonB = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_salespeople (id, company_id, name) values (?, ?, 'Marta')",
            salespersonB, COMPANY);

        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990170", "Maria");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private ConcessionariaTestDrive create(UUID vehicle, UUID salesperson, Instant start) {
        return service.createTestDrive(COMPANY,
            new TestDriveInput(vehicle, salesperson, conversationId, contactId, start, "test-drive"));
    }

    @Test
    @DisplayName("create OK: agendado, end_at = start+45min, snapshots de veículo")
    void create_ok() {
        ConcessionariaTestDrive td = create(vehicleId, salespersonA, START);
        assertThat(td.status()).isEqualTo("agendado");
        assertThat(td.durationMinutes()).isEqualTo(45);
        assertThat(td.endAt()).isEqualTo(START.plusSeconds(45 * 60L));
        assertThat(td.vehicleBrand()).isEqualTo("Toyota");
        assertThat(td.vehicleModel()).isEqualTo("Corolla");
        assertThat(td.vehicleYear()).isEqualTo(2024);
        assertThat(td.customerName()).isEqualTo("Maria");
    }

    @Test
    @DisplayName("conflito POR vendedor (mesmo horário, mesmo vendedor) → ConflictException (409)")
    void conflict_bySalesperson() {
        create(vehicleId, salespersonA, START);
        assertThatThrownBy(() -> create(vehicleId, salespersonA, START))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("PARALELISMO: mesmo horário, vendedor DIFERENTE → OK (não conflita)")
    void parallelism_differentSalesperson() {
        create(vehicleId, salespersonA, START);
        ConcessionariaTestDrive second = create(vehicleId, salespersonB, START);
        assertThat(second.status()).isEqualTo("agendado");
        assertThat(second.salespersonId()).isEqualTo(salespersonB);
    }

    @Test
    @DisplayName("fora do horário (06:00 BRT) → OutsideHoursException (400)")
    void create_outsideHours() {
        // 2026-07-01T06:00-03:00 → 09:00 UTC = 06:00 BRT; antes de opens_at 09:00 BRT.
        Instant early = Instant.parse("2026-07-01T09:00:00Z");
        assertThatThrownBy(() -> create(vehicleId, salespersonA, early))
            .isInstanceOf(OutsideHoursException.class);
    }

    @Test
    @DisplayName("test-drive de veículo NÃO-disponível (vendido) → VehicleNotAvailableException (422)")
    void create_vehicleNotAvailable() {
        jdbcTemplate.update("update concessionaria_vehicles set status = 'vendido' where id = ?", vehicleId);
        assertThatThrownBy(() -> create(vehicleId, salespersonA, START))
            .isInstanceOf(VehicleNotAvailableException.class);
    }

    @Test
    @DisplayName("updateStatus agendado→confirmado → notifica o cliente")
    void confirm_notifies() {
        ConcessionariaTestDrive td = create(vehicleId, salespersonA, START);
        ConcessionariaTestDrive confirmed = service.updateStatus(COMPANY, td.id(), "confirmado");
        assertThat(confirmed.status()).isEqualTo("confirmado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("confirmado");
    }

    @Test
    @DisplayName("updateStatus confirmado→realizado → silencioso (sem notificação)")
    void realizado_silent() {
        ConcessionariaTestDrive td = create(vehicleId, salespersonA, START);
        service.updateStatus(COMPANY, td.id(), "confirmado");
        fakeEvolution.reset();
        ConcessionariaTestDrive done = service.updateStatus(COMPANY, td.id(), "realizado");
        assertThat(done.status()).isEqualTo("realizado");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("updateStatus confirmado→no_show → silencioso")
    void noShow_silent() {
        ConcessionariaTestDrive td = create(vehicleId, salespersonA, START);
        service.updateStatus(COMPANY, td.id(), "confirmado");
        fakeEvolution.reset();
        ConcessionariaTestDrive ns = service.updateStatus(COMPANY, td.id(), "no_show");
        assertThat(ns.status()).isEqualTo("no_show");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("transição inválida (agendado→realizado) → InvalidStatusTransitionException (409)")
    void invalidTransition() {
        ConcessionariaTestDrive td = create(vehicleId, salespersonA, START);
        assertThatThrownBy(() -> service.updateStatus(COMPANY, td.id(), "realizado"))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-conc-td";
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeEvolutionSender fakeEvolutionSender() {
            return new FakeEvolutionSender();
        }
    }
}
