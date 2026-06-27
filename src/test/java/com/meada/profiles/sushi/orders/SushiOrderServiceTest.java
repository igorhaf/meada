package com.meada.profiles.sushi.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.sushi.menu.SushiMenuItem;
import com.meada.profiles.sushi.menu.SushiMenuService;
import com.meada.profiles.sushi.orders.SushiOrderService.AddressRequiredException;
import com.meada.profiles.sushi.orders.SushiOrderService.InvalidScheduleException;
import com.meada.profiles.sushi.orders.SushiOrderService.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o SushiOrderService (camada 7.1 / sushi funcional): status agora é dado (nasce no INICIAL),
 * transição LIVRE (não-terminal → qualquer; terminal → 409), notificação só quando o status alvo tem
 * notify_enabled+text; cupom (percent/fixed/clamp/inválido) e fidelidade; fulfillment + agendamento.
 */
@Import(SushiOrderServiceTest.TestConfig.class)
class SushiOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SushiOrderService service;
    @Autowired
    private SushiMenuService menuService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c7000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d7000000-0000-0000-0000-000000000001");
    private UUID conversationId;
    private UUID contactId;
    private UUID categoryId;
    private UUID recebidoId;
    private UUID preparoId;
    private UUID entregueId;
    private UUID canceladoId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'sushi')",
            COMPANY, "Sushi O", "sushi-o");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sushi-o.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990003", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);

        categoryId = jdbcTemplate.queryForObject(
            "insert into sushi_categories (company_id, name) values (?, 'Hot rolls') returning id", UUID.class, COMPANY);
        recebidoId = insertStatus("Recebido", 0, true, false, false, null);
        preparoId = insertStatus("Em preparo", 1, false, false, true, "Seu pedido entrou em preparo.");
        entregueId = insertStatus("Entregue", 2, false, true, true, "Pedido entregue. Bom apetite!");
        canceladoId = insertStatus("Cancelado", 3, false, true, true, "Seu pedido foi cancelado.");
    }

    private UUID insertStatus(String name, int sort, boolean initial, boolean terminal,
                              boolean notify, String text) {
        return jdbcTemplate.queryForObject(
            "insert into sushi_order_statuses (company_id, name, sort_order, is_initial, is_terminal, "
                + "notify_enabled, notify_text) values (?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, COMPANY, name, sort, initial, terminal, notify, text);
    }

    private SushiOrder seedOrder() {
        SushiMenuItem item = menuService.create(COMPANY, USER, "Filadélfia", null, 3200, categoryId.toString());
        return service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(item.id(), 2)), "entrega", null, null, null, null);
    }

    @Test
    @DisplayName("pedido nasce no status INICIAL da empresa")
    void bornInInitialStatus() {
        SushiOrder order = seedOrder();
        assertThat(order.status()).isEqualTo(recebidoId);
        assertThat(order.statusName()).isEqualTo("Recebido");
    }

    @Test
    @DisplayName("transição LIVRE: recebido → entregue (não-terminal → qualquer) + notifica")
    void freeTransition_notifies() {
        SushiOrder order = seedOrder();
        SushiOrder updated = service.updateStatus(COMPANY, order.id(), entregueId.toString());
        assertThat(updated.status()).isEqualTo(entregueId);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Pedido entregue");
    }

    @Test
    @DisplayName("transição a partir de TERMINAL → 409 invalid_status_transition")
    void terminalBlocks() {
        SushiOrder order = seedOrder();
        service.updateStatus(COMPANY, order.id(), entregueId.toString());   // terminal
        fakeEvolution.reset();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, order.id(), preparoId.toString()))
            .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("notificação só quando o status alvo tem notify_enabled + text")
    void noNotifyWhenDisabled() {
        UUID silent = insertStatus("Aguardando", 5, false, false, false, null);
        SushiOrder order = seedOrder();
        service.updateStatus(COMPANY, order.id(), silent.toString());
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("cupom percent aplica desconto sobre o subtotal + incrementa uses")
    void couponPercent() {
        jdbcTemplate.update("insert into sushi_coupons (company_id, code, kind, value) values (?, 'OFF20', 'percent', 20)", COMPANY);
        SushiMenuItem item = menuService.create(COMPANY, USER, "Combo", null, 5000, categoryId.toString());
        SushiOrder order = service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(item.id(), 1)), "entrega", null, null, "OFF20", null);
        assertThat(order.subtotalCents()).isEqualTo(5000);
        assertThat(order.discountCents()).isEqualTo(1000);   // 20% de 5000
        assertThat(order.couponCode()).isEqualTo("OFF20");
        Integer uses = jdbcTemplate.queryForObject("select uses from sushi_coupons where code = 'OFF20'", Integer.class);
        assertThat(uses).isEqualTo(1);
    }

    @Test
    @DisplayName("cupom fixed é clampado ao subtotal (desconto nunca > subtotal)")
    void couponFixedClamp() {
        jdbcTemplate.update("insert into sushi_coupons (company_id, code, kind, value) values (?, 'BIG', 'fixed', 99999)", COMPANY);
        SushiMenuItem item = menuService.create(COMPANY, USER, "Combo", null, 4000, categoryId.toString());
        SushiOrder order = service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(item.id(), 1)), "retirada", null, null, "BIG", null);
        assertThat(order.discountCents()).isEqualTo(4000);   // clampado ao subtotal
        assertThat(order.totalCents()).isZero();             // retirada → sem taxa
    }

    @Test
    @DisplayName("cupom inválido (expirado) → pedido criado SEM desconto (não aborta)")
    void invalidCouponNotAborts() {
        jdbcTemplate.update("insert into sushi_coupons (company_id, code, kind, value, valid_until) "
            + "values (?, 'OLD', 'percent', 50, current_date - 1)", COMPANY);
        SushiMenuItem item = menuService.create(COMPANY, USER, "Combo", null, 4000, categoryId.toString());
        SushiOrder order = service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(item.id(), 1)), "retirada", null, null, "OLD", null);
        assertThat(order.discountCents()).isZero();
        assertThat(order.couponCode()).isNull();
        Integer uses = jdbcTemplate.queryForObject("select uses from sushi_coupons where code = 'OLD'", Integer.class);
        assertThat(uses).isZero();
    }

    @Test
    @DisplayName("fidelidade: ao atingir o threshold de entregues → desconto + loyalty_applied")
    void loyaltyApplied() {
        jdbcTemplate.update("insert into sushi_loyalty_config (company_id, enabled, threshold_orders, reward_kind, reward_value) "
            + "values (?, true, 2, 'percent', 10)", COMPANY);
        SushiMenuItem item = menuService.create(COMPANY, USER, "Combo", null, 5000, categoryId.toString());
        // 2 pedidos JÁ entregues do contato (threshold=2 → o 3º ganha o reward).
        jdbcTemplate.update("insert into sushi_orders (company_id, conversation_id, contact_id, status, subtotal_cents, total_cents) "
            + "values (?, ?, ?, ?, 5000, 5000)", COMPANY, conversationId, contactId, entregueId);
        jdbcTemplate.update("insert into sushi_orders (company_id, conversation_id, contact_id, status, subtotal_cents, total_cents) "
            + "values (?, ?, ?, ?, 5000, 5000)", COMPANY, conversationId, contactId, entregueId);

        SushiOrder order = service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(item.id(), 1)), "retirada", null, null, null, null);
        assertThat(order.loyaltyApplied()).isTrue();
        assertThat(order.discountCents()).isEqualTo(500);   // 10% de 5000
    }

    @Test
    @DisplayName("fidelidade: abaixo do threshold → sem desconto")
    void loyaltyBelowThreshold() {
        jdbcTemplate.update("insert into sushi_loyalty_config (company_id, enabled, threshold_orders, reward_kind, reward_value) "
            + "values (?, true, 3, 'percent', 10)", COMPANY);
        SushiMenuItem item = menuService.create(COMPANY, USER, "Combo", null, 5000, categoryId.toString());
        jdbcTemplate.update("insert into sushi_orders (company_id, conversation_id, contact_id, status, subtotal_cents, total_cents) "
            + "values (?, ?, ?, ?, 5000, 5000)", COMPANY, conversationId, contactId, entregueId);
        SushiOrder order = service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(item.id(), 1)), "retirada", null, null, null, null);
        assertThat(order.loyaltyApplied()).isFalse();
        assertThat(order.discountCents()).isZero();
    }

    @Test
    @DisplayName("entrega sem endereço → 422 address_required")
    void entregaWithoutAddress() {
        SushiMenuItem item = menuService.create(COMPANY, USER, "Combo", null, 4000, categoryId.toString());
        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, null,
                List.of(new OrderLineInput(item.id(), 1)), "entrega", null, null, null, null))
            .isInstanceOf(AddressRequiredException.class);
    }

    @Test
    @DisplayName("retirada sem endereço → OK, sem taxa")
    void retiradaNoAddress() {
        jdbcTemplate.update("insert into sushi_restaurant_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
        SushiMenuItem item = menuService.create(COMPANY, USER, "Combo", null, 4000, categoryId.toString());
        SushiOrder order = service.create(COMPANY, conversationId, contactId, null,
            List.of(new OrderLineInput(item.id(), 1)), "retirada", null, null, null, null);
        assertThat(order.fulfillment()).isEqualTo("retirada");
        assertThat(order.deliveryFeeCents()).isZero();
        assertThat(order.totalCents()).isEqualTo(4000);
    }

    @Test
    @DisplayName("agendamento desligado → data/período ignorados")
    void schedulingDisabled() {
        SushiMenuItem item = menuService.create(COMPANY, USER, "Combo", null, 4000, categoryId.toString());
        SushiOrder order = service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(item.id(), 1)), "entrega",
            LocalDate.now().plusDays(2), "manha", null, null);
        assertThat(order.scheduledDate()).isNull();
        assertThat(order.scheduledPeriod()).isNull();
    }

    @Test
    @DisplayName("agendamento ligado + data futura → persiste; data passada → 422 invalid_schedule_date")
    void schedulingEnabled() {
        jdbcTemplate.update("insert into sushi_restaurant_config (company_id, scheduling_enabled) values (?, true)", COMPANY);
        SushiMenuItem item = menuService.create(COMPANY, USER, "Combo", null, 4000, categoryId.toString());
        LocalDate future = LocalDate.now().plusDays(3);
        SushiOrder order = service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(item.id(), 1)), "entrega", future, "tarde", null, null);
        assertThat(order.scheduledDate()).isEqualTo(future.toString());
        assertThat(order.scheduledPeriod()).isEqualTo("tarde");

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "Rua X",
                List.of(new OrderLineInput(item.id(), 1)), "entrega", LocalDate.now().minusDays(1), "tarde", null, null))
            .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    @DisplayName("cancelar (não-terminal → cancelado) → notifica cancelamento")
    void cancel_notifies() {
        SushiOrder order = seedOrder();
        SushiOrder cancelled = service.updateStatus(COMPANY, order.id(), canceladoId.toString());
        assertThat(cancelled.status()).isEqualTo(canceladoId);
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
            return "key-sushi";
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
