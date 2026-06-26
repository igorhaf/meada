package com.meada.whatsapp.profiles.papelaria.orders;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de pedidos do perfil papelaria (camada 8.15 / perfil papelaria): list+filter,
 * detail, o GATE DE ACEITE via PATCH status, e a ESCAPADA PROVA DE ARTE (PATCH /art sobe a arte →
 * arte_aprovacao; 409 art_not_approved ao tentar em_producao sem aprovar; PATCH /art approve → em_producao;
 * funil que diverge no fim), 409 transição inválida, 400 status inválido, 403 perfil errado. Pedidos são
 * semeados via jdbcTemplate (no fluxo real vêm da IA). EvolutionSender real falha o envio (base-url dummy)
 * mas a notificação é best-effort → não quebra a transição. Clone do PadariaOrderControllerIntegrationTest.
 */
class PapelariaOrderControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID companyId;
    private UUID orderId;
    private String token;

    /** Semeia um tenant 'papelaria' + um pedido 'aguardando' (default da migration) com um item sob encomenda. */
    private void seedPapelariaOrder(String fulfillment) {
        UUID sub = UUID.randomUUID();
        companyId = seedTenantAdmin("papelaria@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'papelaria' where id = ?", companyId);
        token = mintValidToken("papelaria@test.dev", sub);

        UUID instance = UUID.randomUUID();
        UUID contact = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, companyId, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, companyId, "+5511999990016", "Cliente Pedido");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, companyId, contact, instance);
        UUID catalogItem = jdbcTemplate.queryForObject(
            "insert into papelaria_catalog_items (company_id, name, price_cents, category, made_to_order) "
                + "values (?, 'Convite', 800, 'convites', true) returning id", UUID.class, companyId);
        // pedido nasce 'aguardando' (default da coluna status) — não setar explicitamente.
        String addr = "entrega".equals(fulfillment) ? "'Rua Y 2'" : "null";
        orderId = jdbcTemplate.queryForObject(
            "insert into papelaria_orders (company_id, conversation_id, contact_id, fulfillment, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, '" + fulfillment + "', 80000, 80000, " + addr + ") returning id",
            UUID.class, companyId, conv, contact);
        jdbcTemplate.update("insert into papelaria_order_items (order_id, catalog_item_id, quantity, unit_price_cents, item_name_snapshot, made_to_order_snapshot) "
            + "values (?, ?, 100, 800, 'Convite', true)", orderId, catalogItem);
    }

    @Test
    @DisplayName("GET lista (filtro status=aguardando) + detalhe com items, tiragem e contato")
    void listAndDetail() throws Exception {
        seedPapelariaOrder("retirada");
        mockMvc.perform(get("/api/papelaria/orders?status=aguardando").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].status").value("aguardando"))
            .andExpect(jsonPath("$.items[0].fulfillment").value("retirada"));

        mockMvc.perform(get("/api/papelaria/orders/" + orderId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contactName").value("Cliente Pedido"))
            .andExpect(jsonPath("$.artApproved").value(false))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].qtd").value(100));
    }

    @Test
    @DisplayName("ESCAPADA: aguardando→aceito → PATCH /art sobe arte (arte_aprovacao) → 409 art_not_approved → approve → em_producao → pronto → retirado")
    void artFlow() throws Exception {
        seedPapelariaOrder("retirada");
        // aceite
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"aceito\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("aceito"));
        // sobe a arte → arte_aprovacao
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/art").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"artUrl\":\"https://arte.example/abc.png\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("arte_aprovacao"))
            .andExpect(jsonPath("$.artUrl").value("https://arte.example/abc.png"));
        // tentar em_producao sem aprovar → 409 art_not_approved
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"em_producao\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("art_not_approved"));
        // aprova a arte → em_producao
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/art").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"approve\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("em_producao"))
            .andExpect(jsonPath("$.artApproved").value(true));
        // pronto → retirado
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"pronto\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("pronto"));
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"retirado\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("retirado"));
    }

    @Test
    @DisplayName("pronta-entrega: aceito → em_producao direto (sem arte) → pronto → saiu_entrega → entregue")
    void readyEntregaFlow() throws Exception {
        seedPapelariaOrder("entrega");
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"aceito\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"em_producao\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("em_producao"));
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"pronto\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"saiu_entrega\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("saiu_entrega"));
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"entregue\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("entregue"));
    }

    @Test
    @DisplayName("recusa: aguardando → recusado com motivo → 200 + rejectionReason gravado")
    void patchStatusReject() throws Exception {
        seedPapelariaOrder("retirada");
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"recusado\",\"rejectionReason\":\"prazo inviável\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("recusado"))
            .andExpect(jsonPath("$.rejectionReason").value("prazo inviável"));
    }

    @Test
    @DisplayName("PATCH status aguardando → entregue (inválido) → 409 invalid_status_transition")
    void patchStatusInvalidTransition() throws Exception {
        seedPapelariaOrder("retirada");
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"entregue\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("PATCH status inexistente → 400 invalid_status")
    void patchStatusInvalidValue() throws Exception {
        seedPapelariaOrder("retirada");
        mockMvc.perform(patch("/api/papelaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"voando\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_status"));
    }

    @Test
    @DisplayName("tenant NÃO-papelaria (legal) batendo no /api/papelaria/orders → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID otherCompany = seedTenantAdmin("legal@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'legal' where id = ?", otherCompany);
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/papelaria/orders").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
