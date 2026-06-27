package com.meada.profiles.otica.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.otica.catalog.OticaCatalogItem;
import com.meada.profiles.otica.catalog.OticaCatalogOption;
import com.meada.profiles.otica.catalog.OticaCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o EncomendaOticaConfirmHandler (camada 8.12, FLUXO B): parse da tag {@code <encomenda_otica>}
 * + create, com a RECEITA (rx persistida AS-IS) + PRAZO (ready_date) + modifiers (unit_price = base +
 * Σ deltas; option fantasma ABORTA). O total_cents mentiroso da IA é DESCARTADO.
 * prescription_pending=true (sem grau) → cria com flag + rx_* todos null.
 */
class EncomendaOticaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private EncomendaOticaConfirmHandler handler;
    @Autowired
    private OticaCatalogService catalogService;

    private static final UUID COMPANY = UUID.fromString("ca120000-0000-0000-0000-000000000006");
    private static final UUID USER = UUID.fromString("da120000-0000-0000-0000-000000000006");
    private UUID conversationId;
    private UUID contactId;

    private static String inDays(int d) {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(d).toString();
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'otica')",
            COMPANY, "Ótica Enc", "otica-enc");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@otica-enc.dev', 'admin')",
            USER, COMPANY);
        jdbcTemplate.update("insert into otica_config (company_id, lead_time_days_default) values (?, 7)", COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990017", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("tag com rx + opções + ready_date → cria; unit_price = base + Σ deltas; total descarta o da IA")
    void parseAndCreate_withRx() {
        OticaCatalogItem lente = catalogService.create(COMPANY, USER, "Lente", null, 9900, "lentes", true, 5);
        OticaCatalogOption multi = catalogService.addOption(COMPANY, USER, lente.id(), "Tipo de lente", "Multifocal", 15000, 0);

        String aiText = "Fechado! Sua encomenda foi registrada.\n"
            + "<encomenda_otica>{\"items\":[{\"catalog_item_id\":\"" + lente.id() + "\","
            + "\"options\":[{\"option_id\":\"" + multi.id() + "\"}],\"quantity\":1}],"
            + "\"ready_date\":\"" + inDays(7) + "\",\"rx\":{\"od\":{\"spherical\":\"-1.00\",\"cylindrical\":\"-0.50\",\"axis\":90},"
            + "\"oe\":{\"spherical\":\"-1.25\",\"cylindrical\":\"-0.75\",\"axis\":85},\"pd\":\"62.0\"},"
            + "\"prescription_pending\":false,\"total_cents\":99999}</encomenda_otica>";

        Optional<OticaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isPresent();
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(24900);   // 9900 + 15000.
        assertThat(order.get().subtotalCents()).isEqualTo(24900);
        assertThat(order.get().totalCents()).isEqualTo(24900);                       // total da IA (99999) descartado.
        assertThat(order.get().status()).isEqualTo("aguardando");
        assertThat(order.get().readyDate().toString()).isEqualTo(inDays(7));
        assertThat(order.get().rxOdSpherical()).isEqualByComparingTo("-1.00");
        assertThat(order.get().rxOeAxis()).isEqualTo(85);
        assertThat(order.get().rxPd()).isEqualByComparingTo("62.0");
        assertThat(order.get().prescriptionPending()).isFalse();
    }

    @Test
    @DisplayName("tag prescription_pending (sem bloco rx) → cria com flag true + rx_* todos null")
    void parseAndCreate_pendingNoGrau() {
        OticaCatalogItem lente = catalogService.create(COMPANY, USER, "Lente", null, 9900, "lentes", true, 5);
        String aiText = "Beleza! Você traz a receita depois.\n"
            + "<encomenda_otica>{\"items\":[{\"catalog_item_id\":\"" + lente.id() + "\",\"quantity\":1}],"
            + "\"ready_date\":\"" + inDays(7) + "\",\"prescription_pending\":true}</encomenda_otica>";
        Optional<OticaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isPresent();
        assertThat(order.get().prescriptionPending()).isTrue();
        assertThat(order.get().rxOdSpherical()).isNull();
        assertThat(order.get().rxOeSpherical()).isNull();
        assertThat(order.get().rxPd()).isNull();
    }

    @Test
    @DisplayName("acessório (não sob encomenda) sem ready_date → cria, ready_date null")
    void parseAndCreate_accessory() {
        OticaCatalogItem estojo = catalogService.create(COMPANY, USER, "Estojo", null, 1500, "acessorios", false, null);
        String aiText = "Anotado!\n<encomenda_otica>{\"items\":[{\"catalog_item_id\":\"" + estojo.id()
            + "\",\"quantity\":2}],\"prescription_pending\":true}</encomenda_otica>";
        Optional<OticaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isPresent();
        assertThat(order.get().readyDate()).isNull();
        assertThat(order.get().totalCents()).isEqualTo(3000);
    }

    @Test
    @DisplayName("item sob encomenda com ready_date cedo demais → Optional.empty + 0 pedidos")
    void parseAndCreate_leadViolation() {
        OticaCatalogItem lente = catalogService.create(COMPANY, USER, "Lente", null, 9900, "lentes", true, 5);
        String aiText = "Pronto!\n<encomenda_otica>{\"items\":[{\"catalog_item_id\":\"" + lente.id()
            + "\",\"quantity\":1}],\"ready_date\":\"" + inDays(1) + "\",\"prescription_pending\":true}</encomenda_otica>";
        Optional<OticaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from otica_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("option_id fantasma → Optional.empty + 0 pedidos")
    void parseAndCreate_invalidOption() {
        OticaCatalogItem lente = catalogService.create(COMPANY, USER, "Lente", null, 9900, "lentes", true, 5);
        OticaCatalogItem outro = catalogService.create(COMPANY, USER, "Lente B", null, 8000, "lentes", true, 5);
        OticaCatalogOption optDeOutro = catalogService.addOption(COMPANY, USER, outro.id(), "Tipo", "Y", 100, 0);
        String aiText = "Pronto!\n<encomenda_otica>{\"items\":[{\"catalog_item_id\":\"" + lente.id()
            + "\",\"options\":[{\"option_id\":\"" + optDeOutro.id() + "\"}],\"quantity\":1}],"
            + "\"ready_date\":\"" + inDays(7) + "\",\"prescription_pending\":true}</encomenda_otica>";
        Optional<OticaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from otica_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("item_id inexistente → Optional.empty + 0 pedidos")
    void parseAndCreate_invalidItem() {
        String aiText = "Pronto!\n<encomenda_otica>{\"items\":[{\"catalog_item_id\":\"" + UUID.randomUUID()
            + "\",\"quantity\":1}],\"prescription_pending\":true}</encomenda_otica>";
        Optional<OticaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from otica_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (carrinho em construção)")
    void parseAndCreate_noTag() {
        Optional<OticaOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Quer ver nossas armações?");
        assertThat(order).isEmpty();
    }
}
