package com.meada.whatsapp.profiles.adega.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.adega.menu.AdegaMenuItem;
import com.meada.whatsapp.profiles.adega.menu.AdegaMenuOption;
import com.meada.whatsapp.profiles.adega.menu.AdegaMenuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PedidoAdegaConfirmHandler (camada 8.9): parse da tag {@code <pedido_adega>} + create, com
 * as opções por item (unit_price = base + Σ deltas; option_id fantasma ABORTA) e a ESCAPADA +18
 * (trava de faixa etária): sem {@code age_confirmed=true} a tag é ABORTADA sem criar pedido. O
 * {@code total_cents} mentiroso da IA é sempre DESCARTADO. Clone do PedidoComidaConfirmHandlerTest
 * + os casos +18.
 */
class PedidoAdegaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PedidoAdegaConfirmHandler handler;
    @Autowired
    private AdegaMenuService menuService;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000092");
    private static final UUID USER = UUID.fromString("d8000000-0000-0000-0000-000000000092");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'adega')",
            COMPANY, "Adega H", "adega-h");
        // USER em users (FK audit_log_user_id_fkey) — ver nota no AdegaMenuServiceTest.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@adega-h.dev', 'admin')",
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
        jdbcTemplate.update("insert into adega_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
    }

    @Test
    @DisplayName("tag age_confirmed:true + itens válidos COM opções → cria pedido, unit_price = base + Σ deltas, total descarta o da IA")
    void parseAndCreate_withOptions() {
        AdegaMenuItem vodka = menuService.create(COMPANY, USER, "Vodka", null, 9000, "destilados");
        AdegaMenuOption volume = menuService.addOption(COMPANY, USER, vodka.id(), "Volume", "1L", 3000, 0);
        AdegaMenuOption gelado = menuService.addOption(COMPANY, USER, vodka.id(), "Temperatura", "Gelado", 200, 1);

        String aiText = "Confirmado: 2 Vodka 1L gelada. Maioridade confirmada!\n"
            + "<pedido_adega>{\"age_confirmed\":true,\"items\":[{\"item_id\":\"" + vodka.id() + "\",\"qtd\":2,"
            + "\"options\":[\"" + volume.id() + "\",\"" + gelado.id() + "\"]}],"
            + "\"endereco\":\"Rua das Flores 10\",\"total_cents\":99999}</pedido_adega>";

        Optional<AdegaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        // unit_price = 9000 (base) + 3000 (1L) + 200 (gelado) = 12200.
        // subtotal = 12200 * 2 = 24400; total = 24400 + 700 (fee) = 25100. O total_cents (99999) é DESCARTADO.
        assertThat(order.get().items()).hasSize(1);
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(12200);
        assertThat(order.get().items().get(0).options()).hasSize(2);
        assertThat(order.get().subtotalCents()).isEqualTo(24400);
        assertThat(order.get().deliveryFeeCents()).isEqualTo(700);
        assertThat(order.get().totalCents()).isEqualTo(25100);
        assertThat(order.get().deliveryAddress()).isEqualTo("Rua das Flores 10");
        assertThat(order.get().status()).isEqualTo("aguardando");   // nasce aguardando (ESCAPADA 1).
        assertThat(order.get().ageConfirmed()).isTrue();            // ESCAPADA +18 persistida.
    }

    @Test
    @DisplayName("item sem opções (age_confirmed:true) → cria com unit_price = base")
    void parseAndCreate_noOptions() {
        AdegaMenuItem suco = menuService.create(COMPANY, USER, "Suco Natural", null, 600, "sem_alcool");
        String aiText = "Beleza!\n<pedido_adega>{\"age_confirmed\":true,\"items\":[{\"item_id\":\"" + suco.id() + "\",\"qtd\":3}],"
            + "\"endereco\":\"Rua Y 20\",\"total_cents\":0}</pedido_adega>";

        Optional<AdegaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(600);
        assertThat(order.get().items().get(0).options()).isEmpty();
        // subtotal = 600*3 = 1800; total = 1800 + 700 = 2500.
        assertThat(order.get().subtotalCents()).isEqualTo(1800);
        assertThat(order.get().totalCents()).isEqualTo(2500);
    }

    @Test
    @DisplayName("item_id inexistente na tag → Optional.empty (pedido não criado)")
    void parseAndCreate_invalidItem() {
        String aiText = "Confirmado!\n<pedido_adega>{\"age_confirmed\":true,\"items\":[{\"item_id\":\""
            + UUID.randomUUID() + "\",\"qtd\":1}],\"endereco\":\"Rua X\",\"total_cents\":1000}</pedido_adega>";
        Optional<AdegaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from adega_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("option_id fantasma (não pertence ao item) → Optional.empty + 0 pedidos")
    void parseAndCreate_invalidOption_aborts() {
        AdegaMenuItem vinho = menuService.create(COMPANY, USER, "Vinho Tinto", null, 5000, "vinhos");
        // opção VÁLIDA num OUTRO item — não pertence ao Vinho Tinto → o repo deve recusar.
        AdegaMenuItem outro = menuService.create(COMPANY, USER, "Cerveja", null, 1000, "cervejas");
        AdegaMenuOption optDeOutroItem = menuService.addOption(COMPANY, USER, outro.id(), "Volume", "600ml", 500, 0);

        String aiText = "Confirmado!\n<pedido_adega>{\"age_confirmed\":true,\"items\":[{\"item_id\":\"" + vinho.id() + "\",\"qtd\":1,"
            + "\"options\":[\"" + optDeOutroItem.id() + "\"]}],\"endereco\":\"Rua Z\",\"total_cents\":5500}</pedido_adega>";

        Optional<AdegaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from adega_orders", Long.class);
        assertThat(count).isZero();
    }

    // ---- ESCAPADA +18 (trava de faixa etária) -------------------------------

    @Test
    @DisplayName("ESCAPADA +18: tag SEM age_confirmed (campo ausente) → Optional.empty + 0 pedidos")
    void parseAndCreate_ageFieldAbsent_aborts() {
        AdegaMenuItem cerveja = menuService.create(COMPANY, USER, "Cerveja Lata", null, 500, "cervejas");
        // tag sem o campo age_confirmed — trava +18 aborta antes de criar.
        String aiText = "Confirmado!\n<pedido_adega>{\"items\":[{\"item_id\":\"" + cerveja.id()
            + "\",\"qtd\":1}],\"endereco\":\"Rua W\",\"total_cents\":500}</pedido_adega>";
        Optional<AdegaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from adega_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("ESCAPADA +18: tag com age_confirmed:false → Optional.empty + 0 pedidos")
    void parseAndCreate_ageFalse_aborts() {
        AdegaMenuItem cerveja = menuService.create(COMPANY, USER, "Cerveja Lata", null, 500, "cervejas");
        String aiText = "Confirmado!\n<pedido_adega>{\"age_confirmed\":false,\"items\":[{\"item_id\":\"" + cerveja.id()
            + "\",\"qtd\":1}],\"endereco\":\"Rua W\",\"total_cents\":500}</pedido_adega>";
        Optional<AdegaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from adega_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("ESCAPADA +18: tag com age_confirmed:true → cria e o pedido persistido tem age_confirmed=true")
    void parseAndCreate_ageTrue_createsAndPersists() {
        AdegaMenuItem cerveja = menuService.create(COMPANY, USER, "Cerveja Lata", null, 500, "cervejas");
        String aiText = "Confirmado!\n<pedido_adega>{\"age_confirmed\":true,\"items\":[{\"item_id\":\"" + cerveja.id()
            + "\",\"qtd\":1}],\"endereco\":\"Rua W\",\"total_cents\":500}</pedido_adega>";
        Optional<AdegaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().ageConfirmed()).isTrue();
        // confirma a persistência da coluna age_confirmed.
        Boolean persisted = jdbcTemplate.queryForObject(
            "select age_confirmed from adega_orders where id = ?", Boolean.class, order.get().id());
        assertThat(persisted).isTrue();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<AdegaOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer ver nossa adega?");
        assertThat(order).isEmpty();
    }

    @Test
    @DisplayName("tag sem endereço (mas age_confirmed:true) → Optional.empty (pedido não criado)")
    void parseAndCreate_noAddress() {
        AdegaMenuItem item = menuService.create(COMPANY, USER, "Espumante", null, 7000, "espumantes");
        String aiText = "Confirmado!\n<pedido_adega>{\"age_confirmed\":true,\"items\":[{\"item_id\":\"" + item.id()
            + "\",\"qtd\":1}],\"total_cents\":7000}</pedido_adega>";
        Optional<AdegaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from adega_orders", Long.class);
        assertThat(count).isZero();
    }
}
