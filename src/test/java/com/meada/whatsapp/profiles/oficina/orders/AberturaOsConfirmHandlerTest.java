package com.meada.whatsapp.profiles.oficina.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o AberturaOsConfirmHandler (camada 7.9): parse OK + abertura nos 2 MODOS — vehicle_id
 * existente E new_vehicle (cadastra veículo + abre OS) —, vehicle_id inválido → empty, sem tag → empty.
 */
class AberturaOsConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private AberturaOsConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-000000000004");
    private UUID conversationId;
    private UUID contactId;
    private UUID mechanicId;
    private UUID vehicleId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'oficina')",
            COMPANY, "Oficina H", "oficina-h");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990195", "João");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        mechanicId = UUID.randomUUID();
        jdbcTemplate.update("insert into os_mechanics (id, company_id, name) values (?, ?, 'Carlos')", mechanicId, COMPANY);
        vehicleId = UUID.randomUUID();
        jdbcTemplate.update("insert into os_vehicles (id, company_id, contact_id, plate, brand, model) "
            + "values (?, ?, ?, 'ABC1D23', 'Fiat', 'Uno')", vehicleId, COMPANY, contactId);
    }

    @Test
    @DisplayName("MODO vehicle_id existente → abre OS aberta para o veículo informado")
    void parseAndCreate_existingVehicle() {
        String aiText = "Pronto, João! Abri a ordem de serviço do seu Uno.\n"
            + "<ordem_servico>{\"vehicle_id\":\"" + vehicleId + "\",\"mechanic_id\":\"" + mechanicId
            + "\",\"complaint\":\"Barulho no motor\",\"notes\":\"\"}</ordem_servico>";

        Optional<ServiceOrder> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isPresent();
        assertThat(o.get().status()).isEqualTo("aberta");
        assertThat(o.get().vehiclePlate()).isEqualTo("ABC1D23");
    }

    @Test
    @DisplayName("MODO new_vehicle → cadastra o veículo E abre a OS (count de veículos sobe)")
    void parseAndCreate_newVehicle() {
        Long before = jdbcTemplate.queryForObject("select count(*) from os_vehicles where company_id = ?",
            Long.class, COMPANY);

        String aiText = "Cadastrei o veículo e já abri a OS!\n"
            + "<ordem_servico>{\"new_vehicle\":{\"plate\":\"XYZ9Z99\",\"brand\":\"VW\",\"model\":\"Gol\",\"year\":2020},"
            + "\"complaint\":\"Revisão completa\"}</ordem_servico>";

        Optional<ServiceOrder> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isPresent();
        assertThat(o.get().status()).isEqualTo("aberta");
        assertThat(o.get().vehiclePlate()).isEqualTo("XYZ9Z99");
        Long after = jdbcTemplate.queryForObject("select count(*) from os_vehicles where company_id = ?",
            Long.class, COMPANY);
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("vehicle_id inexistente na tag → Optional.empty (não aberta)")
    void parseAndCreate_invalidVehicle() {
        String aiText = "Ordem aberta!\n<ordem_servico>{\"vehicle_id\":\"" + UUID.randomUUID()
            + "\",\"complaint\":\"Barulho no motor\"}</ordem_servico>";
        Optional<ServiceOrder> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(o).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from service_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<ServiceOrder> o = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Qual o problema com o seu carro?");
        assertThat(o).isEmpty();
    }
}
