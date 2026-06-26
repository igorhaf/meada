package com.meada.whatsapp.profiles.sushi.orders;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de pedidos (camada 7.1 / sushi funcional): list+filter (por status name/id),
 * detail (com os novos campos), patch status (200 transição LIVRE + 409 quando o atual é terminal).
 * Pedidos são semeados via psql (no fluxo real vêm da IA).
 */
class SushiOrderControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID companyId;
    private UUID orderId;
    private UUID recebidoId;
    private UUID preparoId;
    private UUID entregueId;
    private String token;

    private void seedSushiOrder() {
        UUID sub = UUID.randomUUID();
        companyId = seedTenantAdmin("sushi@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'sushi' where id = ?", companyId);
        token = mintValidToken("sushi@test.dev", sub);

        UUID instance = UUID.randomUUID();
        UUID contact = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, companyId, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, companyId, "+5511999990004", "Cliente Pedido");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, companyId, contact, instance);

        UUID cat = jdbcTemplate.queryForObject(
            "insert into sushi_categories (company_id, name) values (?, 'Hot rolls') returning id", UUID.class, companyId);
        recebidoId = jdbcTemplate.queryForObject(
            "insert into sushi_order_statuses (company_id, name, is_initial) values (?, 'Recebido', true) returning id",
            UUID.class, companyId);
        preparoId = jdbcTemplate.queryForObject(
            "insert into sushi_order_statuses (company_id, name) values (?, 'Em preparo') returning id",
            UUID.class, companyId);
        entregueId = jdbcTemplate.queryForObject(
            "insert into sushi_order_statuses (company_id, name, is_terminal) values (?, 'Entregue', true) returning id",
            UUID.class, companyId);

        UUID menuItem = jdbcTemplate.queryForObject(
            "insert into sushi_menu_items (company_id, name, price_cents, category) "
                + "values (?, 'Filadélfia', 3200, ?) returning id", UUID.class, companyId, cat);
        orderId = jdbcTemplate.queryForObject(
            "insert into sushi_orders (company_id, conversation_id, contact_id, status, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, ?, 6400, 6400, 'Rua Y 2') returning id", UUID.class, companyId, conv, contact, recebidoId);
        jdbcTemplate.update("insert into sushi_order_items (order_id, menu_item_id, qtd, unit_price_cents, item_name_snapshot) "
            + "values (?, ?, 2, 3200, 'Filadélfia')", orderId, menuItem);
    }

    @Test
    @DisplayName("GET lista (filtro por status name) + detalhe com items, contato e campos novos")
    void listAndDetail() throws Exception {
        seedSushiOrder();
        mockMvc.perform(get("/api/sushi/orders?status=Recebido").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].statusName").value("Recebido"));

        mockMvc.perform(get("/api/sushi/orders/" + orderId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryAddress").value("Rua Y 2"))
            .andExpect(jsonPath("$.contactName").value("Cliente Pedido"))
            .andExpect(jsonPath("$.statusName").value("Recebido"))
            .andExpect(jsonPath("$.fulfillment").value("entrega"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].qtd").value(2));
    }

    @Test
    @DisplayName("PATCH status (UUID alvo) recebido → preparo → 200 (transição LIVRE)")
    void patchStatusValid() throws Exception {
        seedSushiOrder();
        mockMvc.perform(patch("/api/sushi/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"" + preparoId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusName").value("Em preparo"));
    }

    @Test
    @DisplayName("PATCH status a partir de TERMINAL → 409 invalid_status_transition")
    void patchStatusInvalid() throws Exception {
        seedSushiOrder();
        // primeiro leva ao terminal (entregue), depois tenta sair → 409.
        mockMvc.perform(patch("/api/sushi/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"" + entregueId + "\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/sushi/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"" + preparoId + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("PATCH status com UUID desconhecido → 400 invalid_status")
    void patchStatusUnknown() throws Exception {
        seedSushiOrder();
        mockMvc.perform(patch("/api/sushi/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_status"));
    }
}
