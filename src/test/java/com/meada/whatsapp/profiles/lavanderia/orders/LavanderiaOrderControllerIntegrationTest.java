package com.meada.whatsapp.profiles.lavanderia.orders;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de pedidos do perfil lavanderia (camada 8.10): list+filter, detalhe (com as DUAS
 * datas), e o GATE DE ACEITE via PATCH status — aceite (aguardando→coletado), recusa com motivo,
 * 409 transição inválida, 400 status inválido, 403 perfil errado. Pedidos semeados via jdbcTemplate.
 */
class LavanderiaOrderControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID companyId;
    private UUID orderId;
    private String token;

    private void seedLavanderiaOrder() {
        UUID sub = UUID.randomUUID();
        companyId = seedTenantAdmin("lavanderia@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'lavanderia' where id = ?", companyId);
        token = mintValidToken("lavanderia@test.dev", sub);

        UUID instance = UUID.randomUUID();
        UUID contact = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, companyId, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, companyId, "+5511999990016", "Cliente Pedido");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, companyId, contact, instance);
        UUID svc = jdbcTemplate.queryForObject(
            "insert into lavanderia_services (company_id, name, price_cents, category, turnaround_days) "
                + "values (?, 'Lavar camisa', 800, 'lavar', 2) returning id", UUID.class, companyId);
        orderId = jdbcTemplate.queryForObject(
            "insert into lavanderia_orders (company_id, conversation_id, contact_id, subtotal_cents, total_cents, "
                + "delivery_address, collect_date, delivery_date, period) "
                + "values (?, ?, ?, 1600, 2300, 'Rua Y 2', current_date + 1, current_date + 3, 'manha') returning id",
            UUID.class, companyId, conv, contact);
        jdbcTemplate.update("insert into lavanderia_order_items (order_id, service_id, qty, unit_price_cents, "
            + "service_name_snapshot, turnaround_snapshot) values (?, ?, 2, 800, 'Lavar camisa', 2)", orderId, svc);
    }

    @Test
    @DisplayName("GET lista (filtro status=aguardando) + detalhe com items, contato e DUAS datas")
    void listAndDetail() throws Exception {
        seedLavanderiaOrder();
        mockMvc.perform(get("/api/lavanderia/orders?status=aguardando").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].status").value("aguardando"));

        mockMvc.perform(get("/api/lavanderia/orders/" + orderId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryAddress").value("Rua Y 2"))
            .andExpect(jsonPath("$.contactName").value("Cliente Pedido"))
            .andExpect(jsonPath("$.period").value("manha"))
            .andExpect(jsonPath("$.collectDate").exists())
            .andExpect(jsonPath("$.deliveryDate").exists())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].qty").value(2))
            .andExpect(jsonPath("$.items[0].turnaroundSnapshot").value(2));
    }

    @Test
    @DisplayName("aceite: PATCH status aguardando → coletado → 200")
    void patchStatusAccept() throws Exception {
        seedLavanderiaOrder();
        mockMvc.perform(patch("/api/lavanderia/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"coletado\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("coletado"));
    }

    @Test
    @DisplayName("recusa: PATCH status aguardando → recusado com motivo → 200 + rejectionReason gravado")
    void patchStatusReject() throws Exception {
        seedLavanderiaOrder();
        mockMvc.perform(patch("/api/lavanderia/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"recusado\",\"rejectionReason\":\"fora da área de coleta\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("recusado"))
            .andExpect(jsonPath("$.rejectionReason").value("fora da área de coleta"));
    }

    @Test
    @DisplayName("PATCH status aguardando → pronto (inválido) → 409 invalid_status_transition")
    void patchStatusInvalidTransition() throws Exception {
        seedLavanderiaOrder();
        mockMvc.perform(patch("/api/lavanderia/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"pronto\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("PATCH status inexistente → 400 invalid_status")
    void patchStatusInvalidValue() throws Exception {
        seedLavanderiaOrder();
        mockMvc.perform(patch("/api/lavanderia/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"voando\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_status"));
    }

    @Test
    @DisplayName("tenant NÃO-lavanderia (legal) batendo no /api/lavanderia/orders → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID otherCompany = seedTenantAdmin("legal@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'legal' where id = ?", otherCompany);
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/lavanderia/orders").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
