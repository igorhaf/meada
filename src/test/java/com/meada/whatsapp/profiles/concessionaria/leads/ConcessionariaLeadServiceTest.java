package com.meada.whatsapp.profiles.concessionaria.leads;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.concessionaria.leads.ConcessionariaLeadService.InvalidPaymentConditionException;
import com.meada.whatsapp.profiles.concessionaria.leads.ConcessionariaLeadService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.concessionaria.leads.ConcessionariaLeadService.VehicleNotAvailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o ConcessionariaLeadService (camada 8.17 — LEAD, o funil + a TRAVA do preço):
 * <ul>
 *   <li>create OK: status 'novo'; snapshot de marca/modelo/ano + PREÇO do CATÁLOGO + cliente;</li>
 *   <li>lead de veículo NÃO-disponível → 422;</li>
 *   <li>payment_condition avista/financiado; default avista; inválida → reject;</li>
 *   <li>funil novo→em_negociacao→fechado; perdido com lost_reason; inválida → 409;</li>
 *   <li>assign vendedor;</li>
 *   <li>REGRESSÃO da trava: criar lead NÃO muda o status de estoque do veículo; o preço do lead é
 *       sempre o snapshot do catálogo, INDEPENDENTE de qualquer input.</li>
 * </ul>
 */
class ConcessionariaLeadServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ConcessionariaLeadService service;

    private static final UUID COMPANY = UUID.fromString("c1000000-0000-0000-0000-000000000005");
    private static final int CATALOG_PRICE = 9000000;

    private UUID vehicleId;
    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'concessionaria')",
            COMPANY, "Concessionária L", "conc-l");
        vehicleId = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, model_year, price_cents) "
            + "values (?, ?, 'Toyota', 'Corolla', 2024, ?)", vehicleId, COMPANY, CATALOG_PRICE);

        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990190", "Maria");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private ConcessionariaLead create(String paymentCondition) {
        return service.createLead(COMPANY,
            new LeadInput(vehicleId, conversationId, contactId, paymentCondition, "interesse"));
    }

    @Test
    @DisplayName("create OK: status novo, snapshot de veículo + preço do catálogo + cliente")
    void create_ok() {
        ConcessionariaLead lead = create("avista");
        assertThat(lead.status()).isEqualTo("novo");
        assertThat(lead.vehicleBrand()).isEqualTo("Toyota");
        assertThat(lead.vehicleModel()).isEqualTo("Corolla");
        assertThat(lead.vehicleYear()).isEqualTo(2024);
        assertThat(lead.vehiclePriceCents()).isEqualTo(CATALOG_PRICE);
        assertThat(lead.customerName()).isEqualTo("Maria");
        assertThat(lead.customerPhone()).isEqualTo("+5511999990190");
    }

    @Test
    @DisplayName("payment_condition null → default 'avista'; 'financiado' → preservado")
    void paymentCondition_defaultAndExplicit() {
        ConcessionariaLead dflt = create(null);
        assertThat(dflt.paymentCondition()).isEqualTo("avista");
        ConcessionariaLead fin = create("financiado");
        assertThat(fin.paymentCondition()).isEqualTo("financiado");
    }

    @Test
    @DisplayName("payment_condition inválida → InvalidPaymentConditionException")
    void paymentCondition_invalid() {
        assertThatThrownBy(() -> create("boleto"))
            .isInstanceOf(InvalidPaymentConditionException.class);
    }

    @Test
    @DisplayName("lead de veículo NÃO-disponível (reservado) → VehicleNotAvailableException (422)")
    void create_vehicleNotAvailable() {
        jdbcTemplate.update("update concessionaria_vehicles set status = 'reservado' where id = ?", vehicleId);
        assertThatThrownBy(() -> create("avista"))
            .isInstanceOf(VehicleNotAvailableException.class);
    }

    @Test
    @DisplayName("funil novo→em_negociacao→fechado OK")
    void funnel_valid() {
        ConcessionariaLead lead = create("avista");
        ConcessionariaLead nego = service.updateStatus(COMPANY, lead.id(), "em_negociacao", null);
        assertThat(nego.status()).isEqualTo("em_negociacao");
        ConcessionariaLead closed = service.updateStatus(COMPANY, lead.id(), "fechado", null);
        assertThat(closed.status()).isEqualTo("fechado");
    }

    @Test
    @DisplayName("novo→perdido grava lost_reason")
    void funnel_perdidoWithReason() {
        ConcessionariaLead lead = create("avista");
        ConcessionariaLead lost = service.updateStatus(COMPANY, lead.id(), "perdido", "comprou em outra loja");
        assertThat(lost.status()).isEqualTo("perdido");
        assertThat(lost.lostReason()).isEqualTo("comprou em outra loja");
    }

    @Test
    @DisplayName("transição inválida (novo→fechado) → InvalidStatusTransitionException (409)")
    void funnel_invalid() {
        ConcessionariaLead lead = create("avista");
        assertThatThrownBy(() -> service.updateStatus(COMPANY, lead.id(), "fechado", null))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("assignSalesperson atribui o vendedor")
    void assign() {
        ConcessionariaLead lead = create("avista");
        UUID sp = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_salespeople (id, company_id, name) values (?, ?, 'Carlos')",
            sp, COMPANY);
        ConcessionariaLead assigned = service.assignSalesperson(COMPANY, lead.id(), sp);
        assertThat(assigned.salespersonId()).isEqualTo(sp);
    }

    @Test
    @DisplayName("REGRESSÃO TRAVA: criar lead NÃO muda o status de estoque do veículo (continua disponivel)")
    void regression_leadDoesNotChangeStock() {
        create("avista");
        String status = jdbcTemplate.queryForObject(
            "select status from concessionaria_vehicles where id = ?", String.class, vehicleId);
        assertThat(status)
            .as("o lead é só interesse — o estoque é ação humana do painel, intocado pela IA")
            .isEqualTo("disponivel");
    }

    @Test
    @DisplayName("REGRESSÃO TRAVA: o preço do lead é o do catálogo, INDEPENDENTE do preço do veículo no momento")
    void regression_priceIsCatalogSnapshot() {
        ConcessionariaLead lead = create("avista");
        // o preço gravado é exatamente o do catálogo no momento do lead (a IA/tag nunca passa preço).
        assertThat(lead.vehiclePriceCents()).isEqualTo(CATALOG_PRICE);
        // alterar o preço do veículo depois NÃO altera o lead passado (snapshot).
        jdbcTemplate.update("update concessionaria_vehicles set price_cents = 5000000 where id = ?", vehicleId);
        ConcessionariaLead reread = service.get(COMPANY, lead.id()).orElseThrow();
        assertThat(reread.vehiclePriceCents()).isEqualTo(CATALOG_PRICE);
    }
}
