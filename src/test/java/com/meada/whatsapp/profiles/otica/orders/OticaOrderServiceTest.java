package com.meada.whatsapp.profiles.otica.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.otica.catalog.OticaCatalogItem;
import com.meada.whatsapp.profiles.otica.catalog.OticaCatalogOption;
import com.meada.whatsapp.profiles.otica.catalog.OticaCatalogService;
import com.meada.whatsapp.profiles.otica.orders.OticaOrderRepository.InvalidOptionException;
import com.meada.whatsapp.profiles.otica.orders.OticaOrderService.LeadTimeViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Testa o OticaOrderService (camada 8.12, FLUXO B KEY): acessório sem ready_date → OK; item sob
 * encomenda sem ready_date → 422 lead_time_violation; ready_date < hoje+lead → 422 (earliest na
 * resposta); data válida → OK; ready_date = MAX dos leads de múltiplos itens; modifiers unit_price =
 * base + Σ deltas; campos rx_* persistidos AS-IS; prescription_pending; total recalculado; opção
 * fantasma → aborta.
 */
class OticaOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OticaOrderService service;
    @Autowired
    private OticaCatalogService catalogService;

    private static final UUID COMPANY = UUID.fromString("ca120000-0000-0000-0000-000000000004");
    private static final UUID USER = UUID.fromString("da120000-0000-0000-0000-000000000004");
    private UUID conversationId;
    private UUID contactId;

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'otica')",
            COMPANY, "Ótica Order", "otica-order");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@otica-order.dev', 'admin')",
            USER, COMPANY);
        // lead default = 7.
        jdbcTemplate.update("insert into otica_config (company_id, lead_time_days_default) values (?, 7)", COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990015", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("só acessório (não sob encomenda) sem ready_date → OK, ready_date null")
    void accessory_noReadyDate_ok() {
        OticaCatalogItem estojo = catalogService.create(COMPANY, USER, "Estojo", null, 1500, "acessorios", false, null);
        OticaOrder order = service.create(COMPANY, conversationId, contactId,
            List.of(new OticaOrderLineInput(estojo.id(), 2, List.of())), null, null,
            OticaPrescription.pendingEmpty());
        assertThat(order.status()).isEqualTo("aguardando");
        assertThat(order.readyDate()).isNull();
        assertThat(order.subtotalCents()).isEqualTo(3000);
        assertThat(order.totalCents()).isEqualTo(3000);   // retirada — total = subtotal.
        assertThat(order.prescriptionPending()).isTrue();
    }

    @Test
    @DisplayName("item sob encomenda SEM ready_date → 422 lead_time_violation (earliest = hoje+lead)")
    void madeToOrder_noReadyDate_violation() {
        OticaCatalogItem armacao = catalogService.create(COMPANY, USER, "Armação", null, 19900, "armacoes", true, 5);
        LeadTimeViolationException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId,
                List.of(new OticaOrderLineInput(armacao.id(), 1, List.of())), null, null,
                OticaPrescription.pendingEmpty()),
            LeadTimeViolationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.earliest()).isEqualTo(today().plusDays(5));   // lead do item (5) > default? não, 5 é o item.
    }

    @Test
    @DisplayName("ready_date ANTES de hoje+lead → 422 lead_time_violation com earliest correto")
    void readyDate_tooEarly_violation() {
        OticaCatalogItem armacao = catalogService.create(COMPANY, USER, "Armação", null, 19900, "armacoes", true, 5);
        LocalDate tooEarly = today().plusDays(2);   // antes de hoje+5.
        LeadTimeViolationException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId,
                List.of(new OticaOrderLineInput(armacao.id(), 1, List.of())), null, tooEarly,
                OticaPrescription.pendingEmpty()),
            LeadTimeViolationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.earliest()).isEqualTo(today().plusDays(5));
    }

    @Test
    @DisplayName("ready_date válida (>= hoje+lead) → OK")
    void readyDate_valid_ok() {
        OticaCatalogItem armacao = catalogService.create(COMPANY, USER, "Armação", null, 19900, "armacoes", true, 5);
        LocalDate ok = today().plusDays(7);
        OticaOrder order = service.create(COMPANY, conversationId, contactId,
            List.of(new OticaOrderLineInput(armacao.id(), 1, List.of())), null, ok,
            OticaPrescription.pendingEmpty());
        assertThat(order.readyDate()).isEqualTo(ok);
        assertThat(order.totalCents()).isEqualTo(19900);
    }

    @Test
    @DisplayName("ready_date = MAX dos leads com múltiplos itens sob encomenda")
    void readyDate_maxOfLeads() {
        OticaCatalogItem armacao = catalogService.create(COMPANY, USER, "Armação", null, 19900, "armacoes", true, 3);
        OticaCatalogItem lente = catalogService.create(COMPANY, USER, "Lente", null, 9900, "lentes", true, 10);
        // earliest = hoje + MAX(3, 10) = hoje + 10. data hoje+9 deve violar; hoje+10 deve passar.
        LocalDate justBelow = today().plusDays(9);
        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId,
            List.of(new OticaOrderLineInput(armacao.id(), 1, List.of()),
                new OticaOrderLineInput(lente.id(), 1, List.of())), null, justBelow,
            OticaPrescription.pendingEmpty()))
            .isInstanceOf(LeadTimeViolationException.class);

        OticaOrder order = service.create(COMPANY, conversationId, contactId,
            List.of(new OticaOrderLineInput(armacao.id(), 1, List.of()),
                new OticaOrderLineInput(lente.id(), 1, List.of())), null, today().plusDays(10),
            OticaPrescription.pendingEmpty());
        assertThat(order.readyDate()).isEqualTo(today().plusDays(10));
        assertThat(order.subtotalCents()).isEqualTo(29800);
    }

    @Test
    @DisplayName("item sob encomenda SEM lead próprio → usa o default da config (7)")
    void madeToOrder_nullLead_usesDefault() {
        OticaCatalogItem armacao = catalogService.create(COMPANY, USER, "Armação", null, 19900, "armacoes", true, null);
        LeadTimeViolationException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId,
                List.of(new OticaOrderLineInput(armacao.id(), 1, List.of())), null, today().plusDays(3),
                OticaPrescription.pendingEmpty()),
            LeadTimeViolationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.earliest()).isEqualTo(today().plusDays(7));   // fallback default.
    }

    @Test
    @DisplayName("modifiers: unit_price = base + Σ deltas; total recalculado")
    void modifiers_recalc() {
        OticaCatalogItem lente = catalogService.create(COMPANY, USER, "Lente", null, 9900, "lentes", true, 3);
        OticaCatalogOption multi = catalogService.addOption(COMPANY, USER, lente.id(), "Tipo de lente", "Multifocal", 15000, 0);
        OticaCatalogOption ar = catalogService.addOption(COMPANY, USER, lente.id(), "Tratamento", "Antirreflexo", 5000, 1);

        OticaOrder order = service.create(COMPANY, conversationId, contactId,
            List.of(new OticaOrderLineInput(lente.id(), 1, List.of(multi.id(), ar.id()))), null,
            today().plusDays(3), OticaPrescription.pendingEmpty());
        // unit_price = 9900 + 15000 + 5000 = 29900.
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).unitPriceCents()).isEqualTo(29900);
        assertThat(order.items().get(0).options()).hasSize(2);
        assertThat(order.totalCents()).isEqualTo(29900);
    }

    @Test
    @DisplayName("campos rx_* persistidos AS-IS; prescription_pending=false quando há grau")
    void prescription_persistedAsIs() {
        OticaCatalogItem lente = catalogService.create(COMPANY, USER, "Lente", null, 9900, "lentes", true, 3);
        OticaPrescription rx = new OticaPrescription(
            new BigDecimal("-1.00"), new BigDecimal("-0.50"), 90,
            new BigDecimal("-1.25"), new BigDecimal("-0.75"), 85,
            new BigDecimal("62.0"), false);
        OticaOrder order = service.create(COMPANY, conversationId, contactId,
            List.of(new OticaOrderLineInput(lente.id(), 1, List.of())), null, today().plusDays(3), rx);
        assertThat(order.rxOdSpherical()).isEqualByComparingTo("-1.00");
        assertThat(order.rxOdAxis()).isEqualTo(90);
        assertThat(order.rxOeCylindrical()).isEqualByComparingTo("-0.75");
        assertThat(order.rxPd()).isEqualByComparingTo("62.0");
        assertThat(order.prescriptionPending()).isFalse();
    }

    @Test
    @DisplayName("prescription_pending=true → flag true, sem grau")
    void prescription_pending() {
        OticaCatalogItem lente = catalogService.create(COMPANY, USER, "Lente", null, 9900, "lentes", true, 3);
        OticaOrder order = service.create(COMPANY, conversationId, contactId,
            List.of(new OticaOrderLineInput(lente.id(), 1, List.of())), null, today().plusDays(3),
            OticaPrescription.pendingEmpty());
        assertThat(order.prescriptionPending()).isTrue();
        assertThat(order.rxOdSpherical()).isNull();
    }

    @Test
    @DisplayName("opção fantasma (de outro item) → InvalidOptionException, 0 pedidos")
    void invalidOption_aborts() {
        OticaCatalogItem lente = catalogService.create(COMPANY, USER, "Lente", null, 9900, "lentes", true, 3);
        OticaCatalogItem outro = catalogService.create(COMPANY, USER, "Lente B", null, 8000, "lentes", true, 3);
        OticaCatalogOption optDeOutro = catalogService.addOption(COMPANY, USER, outro.id(), "Tipo", "Y", 100, 0);

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId,
            List.of(new OticaOrderLineInput(lente.id(), 1, List.of(optDeOutro.id()))), null,
            today().plusDays(3), OticaPrescription.pendingEmpty()))
            .isInstanceOf(InvalidOptionException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from otica_orders", Long.class)).isZero();
    }
}
