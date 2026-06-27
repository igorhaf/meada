package com.meada.profiles.modainfantil.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.modainfantil.catalog.ModaInfantilProduct;
import com.meada.profiles.modainfantil.catalog.ModaInfantilProductService;
import com.meada.profiles.modainfantil.catalog.ModaInfantilVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PedidoModaInfantilConfirmHandler (camada 8.22): parse da tag {@code <pedido_moda_infantil>} +
 * create, com a escapada de estoque. Prova que: tag válida cria o pedido, decrementa o estoque, usa
 * unit_price da VARIANTE (e do base do produto quando o priceCents da variante é null), e DESCARTA o
 * total da IA; OUT OF STOCK (qtd > estoque) → empty + 0 pedidos + estoque INTACTO; variante inexistente
 * → empty; retirada sem endereço OK; entrega sem endereço → empty. Clone do
 * PedidoLingerieConfirmHandlerTest, adaptado às faixas etárias.
 */
class PedidoModaInfantilConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PedidoModaInfantilConfirmHandler handler;
    @Autowired
    private ModaInfantilProductService productService;

    private static final UUID COMPANY = UUID.fromString("c8220000-0000-0000-0000-000000000092");
    private static final UUID USER = UUID.fromString("d8220000-0000-0000-0000-000000000092");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'moda_infantil')",
            COMPANY, "Moda Infantil H", "moda-infantil-h");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@moda-infantil-h.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990292", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        // taxa de entrega configurada (entra no total só em entrega).
        jdbcTemplate.update("insert into moda_infantil_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
    }

    private int stock(UUID variantId) {
        return jdbcTemplate.queryForObject(
            "select stock_qty from moda_infantil_variants where id = ?", Integer.class, variantId);
    }

    @Test
    @DisplayName("tag válida (variante com preço próprio) → cria, decrementa estoque, total descarta o da IA")
    void parseAndCreate_variantPrice() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Conjunto Verão", null, "menina", 8000);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "1a", "Rosa", null, 8990, 5);

        String aiText = "Confirmado: 2 Conjunto Verão (1a/Rosa).\n"
            + "<pedido_moda_infantil>{\"items\":[{\"variant_id\":\"" + v.id() + "\",\"qtd\":2}],"
            + "\"fulfillment\":\"entrega\",\"endereco\":\"Rua das Flores 10\",\"total_cents\":99999}</pedido_moda_infantil>";

        Optional<ModaInfantilOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        // unit_price = 8990 (variante); subtotal = 8990*2 = 17980; total = 17980 + 700 = 18680. O 99999 é DESCARTADO.
        assertThat(order.get().items()).hasSize(1);
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(8990);
        assertThat(order.get().items().get(0).size()).isEqualTo("1a");
        assertThat(order.get().items().get(0).color()).isEqualTo("Rosa");
        assertThat(order.get().subtotalCents()).isEqualTo(17980);
        assertThat(order.get().deliveryFeeCents()).isEqualTo(700);
        assertThat(order.get().totalCents()).isEqualTo(18680);
        assertThat(order.get().fulfillment()).isEqualTo("entrega");
        assertThat(order.get().deliveryAddress()).isEqualTo("Rua das Flores 10");
        assertThat(order.get().status()).isEqualTo("aguardando");   // nasce aguardando (gate de aceite).
        // estoque decrementado de 5 para 3.
        assertThat(stock(v.id())).isEqualTo(3);
    }

    @Test
    @DisplayName("variante com priceCents null → unit_price vem do base_price do produto")
    void parseAndCreate_inheritsBasePrice() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Camiseta", null, "menino", 1990);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "4a", "Branco", null, null, 10);

        String aiText = "Beleza!\n<pedido_moda_infantil>{\"items\":[{\"variant_id\":\"" + v.id() + "\",\"qtd\":3}],"
            + "\"fulfillment\":\"entrega\",\"endereco\":\"Rua Y 20\",\"total_cents\":0}</pedido_moda_infantil>";

        Optional<ModaInfantilOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(1990);   // herdou o base.
        // subtotal = 1990*3 = 5970; total = 5970 + 700 = 6670.
        assertThat(order.get().subtotalCents()).isEqualTo(5970);
        assertThat(order.get().totalCents()).isEqualTo(6670);
        assertThat(stock(v.id())).isEqualTo(7);   // 10 - 3.
    }

    @Test
    @DisplayName("OUT OF STOCK (qtd > estoque) → Optional.empty + 0 pedidos + estoque INTACTO")
    void parseAndCreate_outOfStock_aborts() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Body", null, "bebe", 5000);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "RN", "Cinza", null, null, 2);

        // pede 3, só há 2 em estoque → out_of_stock → aborta tudo.
        String aiText = "Confirmado!\n<pedido_moda_infantil>{\"items\":[{\"variant_id\":\"" + v.id() + "\",\"qtd\":3}],"
            + "\"fulfillment\":\"retirada\",\"total_cents\":15000}</pedido_moda_infantil>";
        Optional<ModaInfantilOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from moda_infantil_orders", Long.class);
        assertThat(count).isZero();
        // estoque NÃO foi alterado (o decremento condicional não afetou linha → rollback).
        assertThat(stock(v.id())).isEqualTo(2);
    }

    @Test
    @DisplayName("corrida da última unidade: 1º pedido zera o estoque; 2º pedido → out_of_stock (empty)")
    void parseAndCreate_lastUnit_secondFails() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Conjunto", null, "menina", 6000);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "2a", "Vinho", null, null, 1);

        String aiText = "Confirmado!\n<pedido_moda_infantil>{\"items\":[{\"variant_id\":\"" + v.id() + "\",\"qtd\":1}],"
            + "\"fulfillment\":\"retirada\",\"total_cents\":6000}</pedido_moda_infantil>";

        // 1º pedido pega a última unidade — sucesso, estoque vai a 0.
        Optional<ModaInfantilOrder> first = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(first).isPresent();
        assertThat(stock(v.id())).isZero();

        // 2º pedido pra mesma variante → estoque 0 < 1 → out_of_stock → empty.
        Optional<ModaInfantilOrder> second = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(second).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from moda_infantil_orders", Long.class);
        assertThat(count).isEqualTo(1L);   // só o primeiro.
        assertThat(stock(v.id())).isZero();
    }

    @Test
    @DisplayName("variant_id inexistente na tag → Optional.empty (pedido não criado)")
    void parseAndCreate_invalidVariant() {
        String aiText = "Confirmado!\n<pedido_moda_infantil>{\"items\":[{\"variant_id\":\""
            + UUID.randomUUID() + "\",\"qtd\":1}],\"fulfillment\":\"retirada\",\"total_cents\":1000}</pedido_moda_infantil>";
        Optional<ModaInfantilOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from moda_infantil_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("retirada sem endereço → OK (cria, sem taxa de entrega, sem deliveryAddress)")
    void parseAndCreate_retiradaNoAddress() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Pijama", null, "pijamas", 9000);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "6a", "Azul", null, null, 4);

        String aiText = "Beleza, retirada na loja!\n<pedido_moda_infantil>{\"items\":[{\"variant_id\":\"" + v.id()
            + "\",\"qtd\":1}],\"fulfillment\":\"retirada\",\"total_cents\":9000}</pedido_moda_infantil>";
        Optional<ModaInfantilOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().fulfillment()).isEqualTo("retirada");
        assertThat(order.get().deliveryFeeCents()).isZero();   // retirada não soma taxa.
        assertThat(order.get().deliveryAddress()).isNull();
        assertThat(order.get().totalCents()).isEqualTo(9000);
    }

    @Test
    @DisplayName("entrega SEM endereço → Optional.empty (pedido não criado)")
    void parseAndCreate_entregaNoAddress() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Meia", null, "acessorios", 1990);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "1a", "Preto", null, null, 5);
        String aiText = "Confirmado!\n<pedido_moda_infantil>{\"items\":[{\"variant_id\":\"" + v.id()
            + "\",\"qtd\":1}],\"fulfillment\":\"entrega\",\"total_cents\":1990}</pedido_moda_infantil>";
        Optional<ModaInfantilOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from moda_infantil_orders", Long.class);
        assertThat(count).isZero();
        assertThat(stock(v.id())).isEqualTo(5);   // estoque intacto.
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<ModaInfantilOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer ver as novidades de roupinha?");
        assertThat(order).isEmpty();
    }
}
