package com.meada.whatsapp.profiles.restaurant.reservations;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o ReservationConfirmHandler (camada 7.3): parse OK + create, table_id inexistente → empty,
 * sem tag → empty.
 */
class ReservationConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private ReservationConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000003");
    private UUID tableId;
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'restaurant')",
            COMPANY, "Rest H", "rest-h");
        tableId = UUID.randomUUID();
        jdbcTemplate.update("insert into restaurant_tables (id, company_id, label, capacity) values (?, ?, 'Mesa 4', 6)",
            tableId, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990020", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("tag <reserva> válida → cria reserva pendente")
    void parseAndCreate_ok() {
        String aiText = "Perfeito! Reservei a Mesa 4 pra você dia 01/07 às 20h, pra 4 pessoas. Te esperamos!\n"
            + "<reserva>{\"table_id\":\"" + tableId + "\",\"date\":\"2026-07-01\","
            + "\"start_time\":\"20:00\",\"num_people\":4}</reserva>";

        Optional<Reservation> r = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Cliente", "+5511999990020", aiText);

        assertThat(r).isPresent();
        assertThat(r.get().status()).isEqualTo("pendente");
        assertThat(r.get().tableLabel()).isEqualTo("Mesa 4");
        assertThat(r.get().numPeople()).isEqualTo(4);
        assertThat(r.get().guestName()).isEqualTo("Cliente");
    }

    @Test
    @DisplayName("table_id inexistente na tag → Optional.empty (reserva não criada)")
    void parseAndCreate_invalidTable() {
        String aiText = "Reservado!\n<reserva>{\"table_id\":\"" + UUID.randomUUID()
            + "\",\"date\":\"2026-07-01\",\"start_time\":\"20:00\",\"num_people\":2}</reserva>";
        Optional<Reservation> r = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Cliente", null, aiText);
        assertThat(r).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from table_reservations", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<Reservation> r = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Cliente", null, "Oi! Quer reservar uma mesa?");
        assertThat(r).isEmpty();
    }
}
