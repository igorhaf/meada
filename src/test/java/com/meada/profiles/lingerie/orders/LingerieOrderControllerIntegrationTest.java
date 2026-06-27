package com.meada.profiles.lingerie.orders;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de pedidos do perfil lingerie (camada 8.21): list+filter, detail, e o GATE DE
 * ACEITE via PATCH status — aceite (aguardando→separando), recusa com motivo (aguardando→recusado),
 * 409 transição inválida, 400 status inválido, 403 perfil errado. Pedidos são semeados via
 * jdbcTemplate (no fluxo real vêm da IA — NÃO há POST de criar pedido). EvolutionSender real falha o
 * envio (base-url dummy) mas a notificação é best-effort → não quebra a transição. Análogo ao
 * AdegaOrderControllerIntegrationTest.
 */
class LingerieOrderControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID companyId;
    private UUID orderId;
    private String token;

    /** Semeia um tenant 'lingerie' + um pedido 'aguardando' (default da migration). */
    private void seedLingerieOrder() {
        UUID sub = UUID.randomUUID();
        companyId = seedTenantAdmin("lingerie@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'lingerie' where id = ?", companyId);
        token = mintValidToken("lingerie@test.dev", sub);

        UUID instance = UUID.randomUUID();
        UUID contact = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, companyId, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, companyId, "+5511999990193", "Cliente Pedido");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, companyId, contact, instance);
        UUID product = jdbcTemplate.queryForObject(
            "insert into lingerie_products (company_id, name, category, base_price_cents) "
                + "values (?, 'Conjunto', 'conjuntos', 5000) returning id", UUID.class, companyId);
        UUID variant = jdbcTemplate.queryForObject(
            "insert into lingerie_variants (company_id, product_id, size, color, stock_qty) "
                + "values (?, ?, 'M', 'Preto', 4) returning id", UUID.class, companyId, product);
        // pedido nasce 'aguardando' (default da coluna status).
        orderId = jdbcTemplate.queryForObject(
            "insert into lingerie_orders (company_id, conversation_id, contact_id, fulfillment, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, 'entrega', 10000, 10700, 'Rua Y 2') returning id", UUID.class, companyId, conv, contact);
        jdbcTemplate.update("insert into lingerie_order_items (order_id, variant_id, qtd, unit_price_cents, "
            + "product_name_snapshot, size_snapshot, color_snapshot) values (?, ?, 2, 5000, 'Conjunto', 'M', 'Preto')",
            orderId, variant);
    }

    @Test
    @DisplayName("GET lista (filtro status=aguardando) + detalhe com items e contato")
    void listAndDetail() throws Exception {
        seedLingerieOrder();
        mockMvc.perform(get("/api/lingerie/orders?status=aguardando").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].status").value("aguardando"));

        mockMvc.perform(get("/api/lingerie/orders/" + orderId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryAddress").value("Rua Y 2"))
            .andExpect(jsonPath("$.contactName").value("Cliente Pedido"))
            .andExpect(jsonPath("$.fulfillment").value("entrega"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].qtd").value(2))
            .andExpect(jsonPath("$.items[0].size").value("M"));
    }

    @Test
    @DisplayName("aceite: PATCH status aguardando → separando → 200")
    void patchStatusAccept() throws Exception {
        seedLingerieOrder();
        mockMvc.perform(patch("/api/lingerie/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"separando\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("separando"));
    }

    @Test
    @DisplayName("recusa: PATCH status aguardando → recusado com motivo → 200 + rejectionReason gravado")
    void patchStatusReject() throws Exception {
        seedLingerieOrder();
        mockMvc.perform(patch("/api/lingerie/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"recusado\",\"rejectionReason\":\"sem estoque na cor\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("recusado"))
            .andExpect(jsonPath("$.rejectionReason").value("sem estoque na cor"));
    }

    @Test
    @DisplayName("PATCH status aguardando → entregue (inválido) → 409 invalid_status_transition")
    void patchStatusInvalidTransition() throws Exception {
        seedLingerieOrder();
        mockMvc.perform(patch("/api/lingerie/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"entregue\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("PATCH status inexistente → 400 invalid_status")
    void patchStatusInvalidValue() throws Exception {
        seedLingerieOrder();
        mockMvc.perform(patch("/api/lingerie/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"voando\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_status"));
    }

    @Test
    @DisplayName("tenant NÃO-lingerie (legal) batendo no /api/lingerie/orders → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID otherCompany = seedTenantAdmin("legal@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'legal' where id = ?", otherCompany);
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/lingerie/orders").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
