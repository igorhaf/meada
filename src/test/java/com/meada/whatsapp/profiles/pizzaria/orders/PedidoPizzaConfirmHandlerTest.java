package com.meada.whatsapp.profiles.pizzaria.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.pizzaria.menu.PizzariaMenuItem;
import com.meada.whatsapp.profiles.pizzaria.menu.PizzariaMenuOption;
import com.meada.whatsapp.profiles.pizzaria.menu.PizzariaMenuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PedidoPizzaConfirmHandler (camada 8.6): parse da tag {@code <pedido_pizza>} + create, com
 * a ESCAPADA 2 (opções por item: unit_price = base + Σ deltas; option_id fantasma ABORTA) E a
 * ESCAPADA NOVA meio-a-meio (frações/sabores; preço pela REGRA DO MAIOR VALOR — MAX, não soma nem
 * média; sabor fantasma ABORTA; persiste em pizzaria_order_item_flavors). O {@code total_cents}
 * mentiroso da IA é sempre DESCARTADO. Clone do PedidoComidaConfirmHandlerTest + os casos da escapada.
 */
class PedidoPizzaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PedidoPizzaConfirmHandler handler;
    @Autowired
    private PizzariaMenuService menuService;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000092");
    private static final UUID USER = UUID.fromString("d8000000-0000-0000-0000-000000000092");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pizzaria')",
            COMPANY, "Pizzaria H", "pizzaria-h");
        // USER em users (FK audit_log_user_id_fkey) — ver nota no PizzariaMenuServiceTest.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@pizzaria-h.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990092", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        // taxa de entrega configurada (entra no total).
        jdbcTemplate.update("insert into pizzaria_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
    }

    // ---- (a) item simples com opções: base + Σ deltas, total descartado --------

    @Test
    @DisplayName("tag com item simples COM opções → cria pedido, unit_price = base + Σ deltas, total descarta o da IA")
    void parseAndCreate_simpleItem_withOptions() {
        PizzariaMenuItem combo = menuService.create(COMPANY, USER, "Combo Família", null, 2500, "combos");
        PizzariaMenuOption refri = menuService.addOption(COMPANY, USER, combo.id(), "Bebida", "Refri 2L", 300, 0);
        PizzariaMenuOption sobremesa = menuService.addOption(COMPANY, USER, combo.id(), "Extra", "Pudim", 200, 1);

        String aiText = "Confirmado: 2 Combo Família com refri e pudim. Vai pra confirmação da pizzaria!\n"
            + "<pedido_pizza>{\"items\":[{\"item_id\":\"" + combo.id() + "\",\"qtd\":2,"
            + "\"options\":[\"" + refri.id() + "\",\"" + sobremesa.id() + "\"]}],"
            + "\"endereco\":\"Rua das Flores 10\",\"total_cents\":99999}</pedido_pizza>";

        Optional<PizzariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        // unit_price = 2500 (base) + 300 (refri) + 200 (pudim) = 3000.
        // subtotal = 3000 * 2 = 6000; total = 6000 + 700 (fee) = 6700. O total_cents (99999) é DESCARTADO.
        assertThat(order.get().items()).hasSize(1);
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(3000);
        assertThat(order.get().items().get(0).options()).hasSize(2);
        assertThat(order.get().items().get(0).flavors()).isEmpty();    // item simples não tem frações.
        assertThat(order.get().subtotalCents()).isEqualTo(6000);
        assertThat(order.get().deliveryFeeCents()).isEqualTo(700);
        assertThat(order.get().totalCents()).isEqualTo(6700);
        assertThat(order.get().deliveryAddress()).isEqualTo("Rua das Flores 10");
        assertThat(order.get().status()).isEqualTo("aguardando");   // nasce aguardando (ESCAPADA 1).
    }

    @Test
    @DisplayName("item sem opções → cria com unit_price = base")
    void parseAndCreate_noOptions() {
        PizzariaMenuItem refri = menuService.create(COMPANY, USER, "Coca-Cola", null, 600, "bebidas");
        String aiText = "Beleza!\n<pedido_pizza>{\"items\":[{\"item_id\":\"" + refri.id() + "\",\"qtd\":3}],"
            + "\"endereco\":\"Rua Y 20\",\"total_cents\":0}</pedido_pizza>";

        Optional<PizzariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(600);
        assertThat(order.get().items().get(0).options()).isEmpty();
        // subtotal = 600*3 = 1800; total = 1800 + 700 = 2500.
        assertThat(order.get().subtotalCents()).isEqualTo(1800);
        assertThat(order.get().totalCents()).isEqualTo(2500);
    }

    // ---- (b) item_id inexistente → empty + 0 pedidos ---------------------------

    @Test
    @DisplayName("item_id inexistente na tag → Optional.empty (pedido não criado)")
    void parseAndCreate_invalidItem() {
        String aiText = "Confirmado!\n<pedido_pizza>{\"items\":[{\"item_id\":\""
            + UUID.randomUUID() + "\",\"qtd\":1}],\"endereco\":\"Rua X\",\"total_cents\":1000}</pedido_pizza>";
        Optional<PizzariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from pizzaria_orders", Long.class);
        assertThat(count).isZero();
    }

    // ---- (c) option_id inválido → empty + 0 pedidos (ESCAPADA 2) ---------------

    @Test
    @DisplayName("option_id fantasma (não pertence ao item) → Optional.empty + 0 pedidos (ESCAPADA 2)")
    void parseAndCreate_invalidOption_aborts() {
        PizzariaMenuItem combo = menuService.create(COMPANY, USER, "Combo Casal", null, 3000, "combos");
        // opção VÁLIDA num OUTRO item — não pertence ao Combo Casal → o repo deve recusar.
        PizzariaMenuItem outro = menuService.create(COMPANY, USER, "Pizza Avulsa", null, 4000, "pizzas_salgadas");
        PizzariaMenuOption optDeOutroItem = menuService.addOption(COMPANY, USER, outro.id(), "Borda", "Cheddar", 500, 0);

        String aiText = "Confirmado!\n<pedido_pizza>{\"items\":[{\"item_id\":\"" + combo.id() + "\",\"qtd\":1,"
            + "\"options\":[\"" + optDeOutroItem.id() + "\"]}],\"endereco\":\"Rua Z\",\"total_cents\":3500}</pedido_pizza>";

        Optional<PizzariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from pizzaria_orders", Long.class);
        assertThat(count).isZero();
    }

    // ---- (d) MEIO-A-MEIO: preço pela REGRA DO MAIOR VALOR (MAX) -----------------

    @Test
    @DisplayName("ESCAPADA meio-a-meio: 2 sabores (5200 e 5500) + Tamanho G (+1200) + Borda (+1000) → unit_price = MAX(5500)+1200+1000 = 7700; principal = sabor 5500; 2 frações com snapshot")
    void parseAndCreate_meioAMeio_maxRule() {
        // Dois sabores com preços DIFERENTES (MAX inequívoco): Portuguesa 5200, Quatro Queijos 5500.
        PizzariaMenuItem portuguesa = menuService.create(COMPANY, USER, "Portuguesa", null, 5200, "pizzas_salgadas");
        PizzariaMenuItem quatroQueijos = menuService.create(COMPANY, USER, "Quatro Queijos", null, 5500, "pizzas_salgadas");
        // Os modifiers (Tamanho/Borda) são resolvidos pelo sabor PRINCIPAL (o de MAIOR preço = Quatro Queijos).
        PizzariaMenuOption tamanhoG = menuService.addOption(COMPANY, USER, quatroQueijos.id(), "Tamanho", "Grande", 1200, 0);
        PizzariaMenuOption borda = menuService.addOption(COMPANY, USER, quatroQueijos.id(), "Borda", "Catupiry", 1000, 1);

        String aiText = "Fechado: pizza meio Portuguesa, meio Quatro Queijos, tamanho G com borda de catupiry.\n"
            + "<pedido_pizza>{\"items\":[{\"flavors\":[\"" + portuguesa.id() + "\",\"" + quatroQueijos.id() + "\"],"
            + "\"options\":[\"" + tamanhoG.id() + "\",\"" + borda.id() + "\"],\"qtd\":1}],"
            + "\"endereco\":\"Rua Pizza 100\",\"total_cents\":12345}</pedido_pizza>";

        Optional<PizzariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        PizzariaOrderItem item = order.get().items().get(0);

        // PROVA DA REGRA DO MAIOR VALOR: unit_price = MAX(5200, 5500) + 1200 + 1000 = 7700.
        // NÃO é soma (5200+5500+1200+1000 = 12900), NÃO é média ((5200+5500)/2 + 2200 = 7550).
        assertThat(item.unitPriceCents())
            .as("unit_price da pizza meio-a-meio = MAX(5500) + 1200 (Tamanho G) + 1000 (Borda) = 7700; "
                + "não a soma dos sabores (12900) nem a média (7550)")
            .isEqualTo(7700);
        assertThat(item.unitPriceCents()).isNotEqualTo(5200 + 5500 + 1200 + 1000);   // não-soma.
        assertThat(item.unitPriceCents()).isNotEqualTo((5200 + 5500) / 2 + 1200 + 1000);   // não-média.

        // O order_item.menu_item_id aponta para o sabor PRINCIPAL (o de 5500 = Quatro Queijos).
        assertThat(item.menuItemId()).isEqualTo(quatroQueijos.id());
        assertThat(item.itemName()).isEqualTo("Quatro Queijos");

        // 2 frações persistidas em pizzaria_order_item_flavors, com snapshots e fraction_index 1,2.
        assertThat(item.flavors()).hasSize(2);
        PizzariaOrderItemFlavor f1 = item.flavors().get(0);
        PizzariaOrderItemFlavor f2 = item.flavors().get(1);
        assertThat(f1.fractionIndex()).isEqualTo(1);
        assertThat(f1.menuItemId()).isEqualTo(portuguesa.id());
        assertThat(f1.flavorName()).isEqualTo("Portuguesa");
        assertThat(f1.flavorPriceCents()).isEqualTo(5200);
        assertThat(f2.fractionIndex()).isEqualTo(2);
        assertThat(f2.menuItemId()).isEqualTo(quatroQueijos.id());
        assertThat(f2.flavorName()).isEqualTo("Quatro Queijos");
        assertThat(f2.flavorPriceCents()).isEqualTo(5500);

        // total = unit_price(7700) * qtd(1) + fee(700) = 8400. total_cents da IA (12345) DESCARTADO.
        assertThat(order.get().subtotalCents()).isEqualTo(7700);
        assertThat(order.get().totalCents()).isEqualTo(8400);

        // Sanidade no banco: exatamente 2 rows de fração para o order_item.
        Long flavorRows = jdbcTemplate.queryForObject(
            "select count(*) from pizzaria_order_item_flavors where order_item_id = ?", Long.class, item.id());
        assertThat(flavorRows).isEqualTo(2L);
    }

    @Test
    @DisplayName("ESCAPADA meio-a-meio: pizza INTEIRA (1 sabor) sem opções → unit_price = preço do sabor; 1 fração")
    void parseAndCreate_pizzaInteira_singleFlavor() {
        PizzariaMenuItem calabresa = menuService.create(COMPANY, USER, "Calabresa", null, 4900, "pizzas_salgadas");
        String aiText = "Fechado: uma Calabresa inteira!\n"
            + "<pedido_pizza>{\"items\":[{\"flavors\":[\"" + calabresa.id() + "\"],\"qtd\":2}],"
            + "\"endereco\":\"Rua Inteira 1\",\"total_cents\":0}</pedido_pizza>";

        Optional<PizzariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        PizzariaOrderItem item = order.get().items().get(0);
        assertThat(item.unitPriceCents()).isEqualTo(4900);
        assertThat(item.menuItemId()).isEqualTo(calabresa.id());
        assertThat(item.flavors()).hasSize(1);
        assertThat(item.flavors().get(0).fractionIndex()).isEqualTo(1);
        // subtotal = 4900 * 2 = 9800; total = 9800 + 700 = 10500.
        assertThat(order.get().subtotalCents()).isEqualTo(9800);
        assertThat(order.get().totalCents()).isEqualTo(10500);
    }

    // ---- (e) flavor_id inválido/indisponível → empty + 0 pedidos (defesa escapada) ----

    @Test
    @DisplayName("flavor_id inexistente na tag meio-a-meio → Optional.empty + 0 pedidos (defesa da escapada)")
    void parseAndCreate_invalidFlavor_aborts() {
        PizzariaMenuItem portuguesa = menuService.create(COMPANY, USER, "Portuguesa", null, 5200, "pizzas_salgadas");
        String aiText = "Confirmado!\n<pedido_pizza>{\"items\":[{\"flavors\":[\"" + portuguesa.id() + "\",\""
            + UUID.randomUUID() + "\"],\"qtd\":1}],\"endereco\":\"Rua W\",\"total_cents\":5000}</pedido_pizza>";
        Optional<PizzariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from pizzaria_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("flavor_id de sabor INDISPONÍVEL (available=false) → Optional.empty + 0 pedidos")
    void parseAndCreate_unavailableFlavor_aborts() {
        PizzariaMenuItem disponivel = menuService.create(COMPANY, USER, "Portuguesa", null, 5200, "pizzas_salgadas");
        PizzariaMenuItem indisponivel = menuService.create(COMPANY, USER, "Quatro Queijos", null, 5500, "pizzas_salgadas");
        menuService.toggle(COMPANY, USER, indisponivel.id(), false);   // desliga o sabor.

        String aiText = "Confirmado!\n<pedido_pizza>{\"items\":[{\"flavors\":[\"" + disponivel.id() + "\",\""
            + indisponivel.id() + "\"],\"qtd\":1}],\"endereco\":\"Rua V\",\"total_cents\":5000}</pedido_pizza>";
        Optional<PizzariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from pizzaria_orders", Long.class);
        assertThat(count).isZero();
    }

    // ---- (f) sem tag / sem endereço → empty -----------------------------------

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<PizzariaOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer ver nosso cardápio de pizzas?");
        assertThat(order).isEmpty();
    }

    @Test
    @DisplayName("tag sem endereço → Optional.empty (pedido não criado)")
    void parseAndCreate_noAddress() {
        PizzariaMenuItem item = menuService.create(COMPANY, USER, "Coca-Cola", null, 600, "bebidas");
        String aiText = "Confirmado!\n<pedido_pizza>{\"items\":[{\"item_id\":\"" + item.id()
            + "\",\"qtd\":1}],\"total_cents\":600}</pedido_pizza>";
        Optional<PizzariaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from pizzaria_orders", Long.class);
        assertThat(count).isZero();
    }
}
