package com.meada.profiles.concessionaria.leads;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o LeadCarroConfirmHandler (camada 8.17): tag válida → cria 'novo' com snapshot do preço do
 * catálogo; veículo indisponível → empty; payment_condition inválida → empty; sem tag → empty.
 */
class LeadCarroConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private LeadCarroConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("c1000000-0000-0000-0000-000000000006");
    private static final int CATALOG_PRICE = 9000000;

    private UUID vehicleId;
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'concessionaria')",
            COMPANY, "Concessionária LH", "conc-lh");
        vehicleId = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, model_year, price_cents) "
            + "values (?, ?, 'Toyota', 'Corolla', 2024, ?)", vehicleId, COMPANY, CATALOG_PRICE);

        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990200", "Maria");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private String tag(UUID vehicle, String paymentCondition) {
        return "Anotei seu interesse!\n"
            + "<lead_carro>{\"vehicle_id\":\"" + vehicle + "\",\"payment_condition\":\"" + paymentCondition
            + "\",\"notes\":null}</lead_carro>";
    }

    @Test
    @DisplayName("tag válida → cria lead 'novo' com preço do catálogo (snapshot)")
    void parseAndCreate_ok() {
        Optional<ConcessionariaLead> lead =
            handler.parseAndCreate(COMPANY, conversationId, contactId, tag(vehicleId, "financiado"));
        assertThat(lead).isPresent();
        assertThat(lead.get().status()).isEqualTo("novo");
        assertThat(lead.get().vehicleId()).isEqualTo(vehicleId);
        assertThat(lead.get().vehiclePriceCents())
            .as("preço vem SEMPRE do catálogo, nunca da tag")
            .isEqualTo(CATALOG_PRICE);
        assertThat(lead.get().paymentCondition()).isEqualTo("financiado");
    }

    @Test
    @DisplayName("veículo indisponível (vendido) → Optional.empty, nada criado")
    void parseAndCreate_vehicleUnavailable() {
        jdbcTemplate.update("update concessionaria_vehicles set status = 'vendido' where id = ?", vehicleId);
        Optional<ConcessionariaLead> lead =
            handler.parseAndCreate(COMPANY, conversationId, contactId, tag(vehicleId, "avista"));
        assertThat(lead).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from concessionaria_leads", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("payment_condition inválida → Optional.empty (service rejeita)")
    void parseAndCreate_invalidPayment() {
        Optional<ConcessionariaLead> lead =
            handler.parseAndCreate(COMPANY, conversationId, contactId, tag(vehicleId, "boleto"));
        assertThat(lead).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from concessionaria_leads", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<ConcessionariaLead> lead =
            handler.parseAndCreate(COMPANY, conversationId, contactId, "Oi! Posso ajudar com algum carro?");
        assertThat(lead).isEmpty();
    }
}
