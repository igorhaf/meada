package com.meada.profiles.adega.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.adega.menu.AdegaMenuItem;
import com.meada.profiles.adega.menu.AdegaMenuOption;
import com.meada.profiles.adega.menu.AdegaMenuService;
import com.meada.profiles.adega.orders.AdegaOrderRepository.InvalidOptionException;
import com.meada.profiles.adega.orders.AdegaOrderService.AgeNotConfirmedException;
import com.meada.profiles.adega.orders.AdegaOrderService.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AdegaOrderService (camada 8.9): a ESCAPADA +18 (trava de faixa etária — create com
 * ageConfirmed=false lança AgeNotConfirmedException ANTES de qualquer cálculo, ZERO pedido no banco;
 * com true cria e persiste age_confirmed), o recálculo de preço (base + Σ deltas × qtd, total da IA
 * descartado), os snapshots, e o gate de aceite (ESCAPADA 1 — aguardando → em_preparo ACEITE /
 * aguardando → recusado RECUSA com motivo defensivo), transição inválida 409, fluxo feliz,
 * cancelamento, e que {@code aguardando} NÃO notifica na criação. Clone do ComidaOrderServiceTest +
 * a ESCAPADA +18.
 */
@Import(AdegaOrderServiceTest.TestConfig.class)
class AdegaOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AdegaOrderService service;
    @Autowired
    private AdegaMenuService menuService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000093");
    private static final UUID USER = UUID.fromString("d8000000-0000-0000-0000-000000000093");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'adega')",
            COMPANY, "Adega O", "adega-o");
        // USER em users (FK audit_log_user_id_fkey) — ver nota no AdegaMenuServiceTest.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@adega-o.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990093", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        // taxa de entrega configurada (entra no total).
        jdbcTemplate.update("insert into adega_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
    }

    /** Cria um pedido +18 confirmado (helper p/ os testes de gate). */
    private AdegaOrder seedOrder() {
        AdegaMenuItem item = menuService.create(COMPANY, USER, "Cerveja", null, 500, "cervejas");
        return service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(item.id(), 2, List.of())), true, null);
    }

    // ---- ESCAPADA +18 (trava de faixa etária) -------------------------------

    @Test
    @DisplayName("ESCAPADA +18: create com ageConfirmed=false → AgeNotConfirmedException + ZERO pedidos (sem pedido parcial)")
    void create_ageNotConfirmed_throwsAndNoOrder() {
        AdegaMenuItem item = menuService.create(COMPANY, USER, "Vodka", null, 9000, "destilados");
        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "Rua X 1",
                List.of(new OrderLineInput(item.id(), 1, List.of())), false, null))
            .isInstanceOf(AgeNotConfirmedException.class);

        // a trava dispara ANTES de qualquer cálculo/INSERT — nenhuma linha em adega_orders.
        Long count = jdbcTemplate.queryForObject("select count(*) from adega_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("ESCAPADA +18: create com ageConfirmed=true → cria e persiste age_confirmed=true")
    void create_ageConfirmed_persists() {
        AdegaOrder order = seedOrder();
        assertThat(order.ageConfirmed()).isTrue();

        Boolean persisted = jdbcTemplate.queryForObject(
            "select age_confirmed from adega_orders where id = ?", Boolean.class, order.id());
        assertThat(persisted).isTrue();
    }

    // ---- Preço (base + Σ deltas × qtd; total da IA descartado) ---------------

    @Test
    @DisplayName("preço = base + Σ deltas (Volume + gelado) × qtd; subtotal/total com fee; snapshot preservado após editar o cardápio")
    void price_withOptionsAndSnapshot() {
        AdegaMenuItem vodka = menuService.create(COMPANY, USER, "Vodka", null, 9000, "destilados");
        AdegaMenuOption volume = menuService.addOption(COMPANY, USER, vodka.id(), "Volume", "1L", 3000, 0);
        AdegaMenuOption gelado = menuService.addOption(COMPANY, USER, vodka.id(), "Temperatura", "Gelado", 200, 1);

        AdegaOrder order = service.create(COMPANY, conversationId, contactId, "Rua Y 2",
            List.of(new OrderLineInput(vodka.id(), 2, List.of(volume.id(), gelado.id()))), true, null);

        // unit_price = 9000 + 3000 + 200 = 12200; subtotal = 12200 * 2 = 24400; total = 24400 + 700 = 25100.
        assertThat(order.items().get(0).unitPriceCents()).isEqualTo(12200);
        assertThat(order.subtotalCents()).isEqualTo(24400);
        assertThat(order.deliveryFeeCents()).isEqualTo(700);
        assertThat(order.totalCents()).isEqualTo(25100);

        // Editar o preço do item DEPOIS não altera o snapshot do pedido.
        menuService.update(COMPANY, USER, vodka.id(), null, null, 99999, null, null);
        AdegaOrder refetched = service.get(COMPANY, order.id()).orElseThrow();
        assertThat(refetched.items().get(0).unitPriceCents()).isEqualTo(12200);   // snapshot preservado.
    }

    @Test
    @DisplayName("option_id fantasma (de outro item) → InvalidOptionException, nada criado")
    void price_invalidOption_aborts() {
        AdegaMenuItem vinho = menuService.create(COMPANY, USER, "Vinho", null, 5000, "vinhos");
        AdegaMenuItem outro = menuService.create(COMPANY, USER, "Cerveja", null, 1000, "cervejas");
        AdegaMenuOption optDeOutro = menuService.addOption(COMPANY, USER, outro.id(), "Volume", "600ml", 500, 0);

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "Rua Z",
                List.of(new OrderLineInput(vinho.id(), 1, List.of(optDeOutro.id()))), true, null))
            .isInstanceOf(InvalidOptionException.class);

        Long count = jdbcTemplate.queryForObject("select count(*) from adega_orders", Long.class);
        assertThat(count).isZero();
    }

    // ---- Gate de aceite (ESCAPADA 1) ----------------------------------------

    @Test
    @DisplayName("pedido nasce 'aguardando' e NÃO dispara notificação na criação (ESCAPADA 1)")
    void create_isAguardando_andSilent() {
        AdegaOrder order = seedOrder();
        assertThat(order.status()).isEqualTo("aguardando");
        // 'aguardando' é silencioso — a IA já confirmou o recebimento na mensagem.
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("aceite (aguardando → em_preparo) → status atualiza + notificação 'aceito/preparo' (ESCAPADA 1)")
    void accept_notifies() {
        AdegaOrder order = seedOrder();

        AdegaOrder updated = service.updateStatus(COMPANY, order.id(), "em_preparo", null);
        assertThat(updated.status()).isEqualTo("em_preparo");

        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("separando suas bebidas");
    }

    @Test
    @DisplayName("recusa (aguardando → recusado) com motivo → terminal + notificação contém o motivo defensivo (ESCAPADA 1)")
    void reject_withReason_notifiesDefensively() {
        AdegaOrder order = seedOrder();

        AdegaOrder rejected = service.updateStatus(COMPANY, order.id(), "recusado", "fora da área de entrega");
        assertThat(rejected.status()).isEqualTo("recusado");
        assertThat(rejected.rejectionReason()).isEqualTo("fora da área de entrega");

        assertThat(fakeEvolution.sent()).hasSize(1);
        String text = fakeEvolution.sent().get(0).text();
        assertThat(text).contains("Infelizmente não conseguimos aceitar");   // texto fixo defensivo.
        assertThat(text).contains("fora da área de entrega");                // motivo concatenado.
    }

    @Test
    @DisplayName("transição inválida (aguardando → entregue) → InvalidStatusTransitionException (409), nada enviado")
    void invalidTransition() {
        AdegaOrder order = seedOrder();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, order.id(), "entregue", null))
            .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("fluxo feliz em_preparo → saiu_entrega → entregue notifica cada transição")
    void happyFlow_notifiesEach() {
        AdegaOrder order = seedOrder();
        service.updateStatus(COMPANY, order.id(), "em_preparo", null);       // 1 envio (aceite).
        service.updateStatus(COMPANY, order.id(), "saiu_entrega", null);     // 2.
        AdegaOrder delivered = service.updateStatus(COMPANY, order.id(), "entregue", null);   // 3.

        assertThat(delivered.status()).isEqualTo("entregue");
        assertThat(fakeEvolution.sent()).hasSize(3);
        assertThat(fakeEvolution.sent().get(1).text()).contains("saiu pra entrega");
        assertThat(fakeEvolution.sent().get(2).text()).contains("entregue");
    }

    @Test
    @DisplayName("cancelar (em_preparo → cancelado) → terminal + notificação de cancelamento")
    void cancel_notifies() {
        AdegaOrder order = seedOrder();
        service.updateStatus(COMPANY, order.id(), "em_preparo", null);   // aceite primeiro (1 envio).
        fakeEvolution.reset();
        AdegaOrder cancelled = service.updateStatus(COMPANY, order.id(), "cancelado", null);
        assertThat(cancelled.status()).isEqualTo("cancelado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("cancelado");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-adega";
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeEvolutionSender fakeEvolutionSender() {
            return new FakeEvolutionSender();
        }
    }
}
