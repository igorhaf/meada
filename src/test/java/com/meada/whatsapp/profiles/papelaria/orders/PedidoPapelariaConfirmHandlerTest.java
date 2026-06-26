package com.meada.whatsapp.profiles.papelaria.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.papelaria.PapelariaConfigRepository;
import com.meada.whatsapp.profiles.papelaria.catalog.PapelariaCatalogItem;
import com.meada.whatsapp.profiles.papelaria.catalog.PapelariaCatalogOption;
import com.meada.whatsapp.profiles.papelaria.catalog.PapelariaCatalogService;
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
 * Testa o PedidoPapelariaConfirmHandler (camada 8.15 / perfil papelaria): parse da tag
 * {@code <pedido_papelaria>} + create, com as escapadas (fulfillment retirada/entrega, data condicional
 * + lead time, personalização/custom_text, TIRAGEM que escala o total) e os modifiers (unit_price =
 * base + Σ deltas; option fantasma ABORTA). O {@code total} mentiroso da IA é sempre DESCARTADO. Clone
 * do EncomendaPadariaConfirmHandlerTest (camada 8.8) — menu_item_id→catalog_item_id,
 * cake_message→custom_text.
 */
class PedidoPapelariaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PedidoPapelariaConfirmHandler handler;
    @Autowired
    private PapelariaCatalogService catalogService;
    @Autowired
    private PapelariaConfigRepository configRepository;

    private static final UUID COMPANY = UUID.fromString("c8150000-0000-0000-0000-000000000072");
    private static final UUID USER = UUID.fromString("d8150000-0000-0000-0000-000000000072");
    private UUID conversationId;
    private UUID contactId;

    /** Uma data daqui a N dias (fuso America/Sao_Paulo). */
    private static String inDays(int n) {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(n).toString();
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'papelaria')",
            COMPANY, "Papelaria H", "papelaria-h");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@papelaria-h.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990072", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        configRepository.upsert(COMPANY, 700, 0, 5);
    }

    @Test
    @DisplayName("pronta-entrega (retirada, sem data) → cria pedido, total descarta o da IA")
    void parseAndCreate_readyPickup() {
        PapelariaCatalogItem bloco = catalogService.create(COMPANY, USER, "Bloco", null, 100, "papelaria", false, null, null);
        String aiText = "Confirmado: 5 blocos, retirada. Total R$ 5.\n"
            + "<pedido_papelaria>{\"fulfillment\":\"retirada\",\"pickup_or_delivery_date\":null,"
            + "\"delivery_period\":null,\"delivery_address\":null,"
            + "\"items\":[{\"catalog_item_id\":\"" + bloco.id() + "\",\"quantity\":5}],\"notes\":\"\"}</pedido_papelaria>";

        Optional<PapelariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().fulfillment()).isEqualTo("retirada");
        assertThat(order.get().subtotalCents()).isEqualTo(500);
        assertThat(order.get().deliveryFeeCents()).isZero();
        assertThat(order.get().totalCents()).isEqualTo(500);
        assertThat(order.get().pickupOrDeliveryDate()).isNull();
        assertThat(order.get().status()).isEqualTo("aguardando");
        assertThat(order.get().artApproved()).isFalse();
    }

    @Test
    @DisplayName("sob encomenda (entrega) com personalização + data válida + TIRAGEM → cria, unit = base + Σ deltas, line = unit × qtd")
    void parseAndCreate_madeToOrderWithPersonalizationAndTiragem() {
        PapelariaCatalogItem convite = catalogService.create(COMPANY, USER, "Convite", null, 800, "convites", true, 7, null);
        PapelariaCatalogOption papel = catalogService.addOption(COMPANY, USER, convite.id(), "Papel", "Perolado", 500, 0);
        PapelariaCatalogOption acab = catalogService.addOption(COMPANY, USER, convite.id(), "Acabamento", "Verniz", 1500, 1);

        String aiText = "Confirmado: 100 convites (Perolado, Verniz), entrega dia X de manhã. Total R$ 999.\n"
            + "<pedido_papelaria>{\"fulfillment\":\"entrega\",\"pickup_or_delivery_date\":\"" + inDays(7) + "\","
            + "\"delivery_period\":\"manha\",\"delivery_address\":\"Rua das Flores 10\","
            + "\"items\":[{\"catalog_item_id\":\"" + convite.id() + "\",\"quantity\":100,"
            + "\"options\":[{\"option_id\":\"" + papel.id() + "\"},{\"option_id\":\"" + acab.id() + "\"}],"
            + "\"custom_text\":\"Ana & João\"}],\"notes\":\"\"}</pedido_papelaria>";

        Optional<PapelariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        // unit = 800 + 500 + 1500 = 2800; line = 2800 × 100 = 280000; total = 280000 + 700 (entrega).
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(2800);
        assertThat(order.get().items().get(0).qtd()).isEqualTo(100);
        assertThat(order.get().items().get(0).options()).hasSize(2);
        assertThat(order.get().items().get(0).customText()).isEqualTo("Ana & João");
        assertThat(order.get().items().get(0).madeToOrder()).isTrue();
        assertThat(order.get().subtotalCents()).isEqualTo(280000);
        assertThat(order.get().deliveryFeeCents()).isEqualTo(700);
        assertThat(order.get().totalCents()).isEqualTo(280700);
        assertThat(order.get().pickupOrDeliveryDate().toString()).isEqualTo(inDays(7));
        assertThat(order.get().deliveryPeriod()).isEqualTo("manha");
    }

    @Test
    @DisplayName("sob encomenda com data ANTES do lead → Optional.empty + 0 pedidos (lead_time_violation)")
    void parseAndCreate_leadViolation_aborts() {
        PapelariaCatalogItem convite = catalogService.create(COMPANY, USER, "Convite", null, 800, "convites", true, 10, null);
        String aiText = "Confirmado!\n<pedido_papelaria>{\"fulfillment\":\"retirada\","
            + "\"pickup_or_delivery_date\":\"" + inDays(2) + "\",\"delivery_period\":\"manha\",\"delivery_address\":null,"
            + "\"items\":[{\"catalog_item_id\":\"" + convite.id() + "\",\"quantity\":50}],\"notes\":\"\"}</pedido_papelaria>";
        Optional<PapelariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from papelaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("entrega SEM endereço → Optional.empty + 0 pedidos (address_required)")
    void parseAndCreate_deliveryNoAddress_aborts() {
        PapelariaCatalogItem bloco = catalogService.create(COMPANY, USER, "Bloco", null, 100, "papelaria", false, null, null);
        String aiText = "Confirmado!\n<pedido_papelaria>{\"fulfillment\":\"entrega\","
            + "\"pickup_or_delivery_date\":null,\"delivery_period\":null,\"delivery_address\":null,"
            + "\"items\":[{\"catalog_item_id\":\"" + bloco.id() + "\",\"quantity\":1}],\"notes\":\"\"}</pedido_papelaria>";
        Optional<PapelariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from papelaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("fulfillment inválido → Optional.empty")
    void parseAndCreate_invalidFulfillment() {
        PapelariaCatalogItem bloco = catalogService.create(COMPANY, USER, "Bloco", null, 100, "papelaria", false, null, null);
        String aiText = "Confirmado!\n<pedido_papelaria>{\"fulfillment\":\"voando\","
            + "\"items\":[{\"catalog_item_id\":\"" + bloco.id() + "\",\"quantity\":1}]}</pedido_papelaria>";
        Optional<PapelariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from papelaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("data no PASSADO → Optional.empty + 0 pedidos")
    void parseAndCreate_pastDate_aborts() {
        PapelariaCatalogItem convite = catalogService.create(COMPANY, USER, "Convite", null, 800, "convites", true, 1, null);
        String ontem = LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(1).toString();
        String aiText = "Confirmado!\n<pedido_papelaria>{\"fulfillment\":\"retirada\","
            + "\"pickup_or_delivery_date\":\"" + ontem + "\",\"delivery_period\":\"manha\","
            + "\"items\":[{\"catalog_item_id\":\"" + convite.id() + "\",\"quantity\":50}]}</pedido_papelaria>";
        Optional<PapelariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from papelaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("item_id inexistente → Optional.empty + 0 pedidos")
    void parseAndCreate_invalidItem() {
        String aiText = "Confirmado!\n<pedido_papelaria>{\"fulfillment\":\"retirada\","
            + "\"items\":[{\"catalog_item_id\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]}</pedido_papelaria>";
        Optional<PapelariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from papelaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("option_id fantasma (não pertence ao item) → Optional.empty + 0 pedidos")
    void parseAndCreate_invalidOption_aborts() {
        PapelariaCatalogItem convite = catalogService.create(COMPANY, USER, "Convite", null, 800, "convites", true, 1, null);
        PapelariaCatalogItem outro = catalogService.create(COMPANY, USER, "Cartão", null, 400, "cartoes", true, 1, null);
        PapelariaCatalogOption optDeOutro = catalogService.addOption(COMPANY, USER, outro.id(), "Cor", "Azul", 500, 0);

        String aiText = "Confirmado!\n<pedido_papelaria>{\"fulfillment\":\"retirada\","
            + "\"pickup_or_delivery_date\":\"" + inDays(1) + "\",\"delivery_period\":\"tarde\","
            + "\"items\":[{\"catalog_item_id\":\"" + convite.id() + "\",\"quantity\":50,"
            + "\"options\":[{\"option_id\":\"" + optDeOutro.id() + "\"}]}]}</pedido_papelaria>";
        Optional<PapelariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from papelaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<PapelariaOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer ver nosso catálogo de convites e papelaria?");
        assertThat(order).isEmpty();
    }
}
