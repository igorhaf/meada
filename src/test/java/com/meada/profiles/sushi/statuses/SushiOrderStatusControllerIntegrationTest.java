package com.meada.profiles.sushi.statuses;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de status do pedido (camada 7.1 / sushi funcional): CRUD, único inicial,
 * status_in_use, initial_status_undeletable, notify fields editáveis, 403 pra tenant não-sushi.
 */
class SushiOrderStatusControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD + notify fields editáveis no PATCH")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);

        mockMvc.perform(post("/api/sushi/order-statuses").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Em preparo\",\"sortOrder\":1,\"notifyEnabled\":true,\"notifyText\":\"preparando\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.notifyEnabled").value(true))
            .andExpect(jsonPath("$.notifyText").value("preparando"));

        UUID id = jdbcTemplate.queryForObject("select id from sushi_order_statuses where name = 'Em preparo'", UUID.class);

        mockMvc.perform(get("/api/sushi/order-statuses").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        // edita o texto da notificação.
        mockMvc.perform(patch("/api/sushi/order-statuses/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"notifyText\":\"já vai sair\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notifyText").value("já vai sair"));

        mockMvc.perform(delete("/api/sushi/order-statuses/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("único inicial: POST de 2º inicial zera o anterior")
    void singleInitial() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        jdbcTemplate.update("insert into sushi_order_statuses (company_id, name, is_initial) values (?, 'Recebido', true)", companyId);

        mockMvc.perform(post("/api/sushi/order-statuses").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Aberto\",\"isInitial\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isInitial").value(true));

        Long initials = jdbcTemplate.queryForObject(
            "select count(*) from sushi_order_statuses where company_id = ? and is_initial = true", Long.class, companyId);
        org.assertj.core.api.Assertions.assertThat(initials).isEqualTo(1L);
    }

    @Test
    @DisplayName("delete do inicial → 409 initial_status_undeletable")
    void initialUndeletable() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        UUID initial = jdbcTemplate.queryForObject(
            "insert into sushi_order_statuses (company_id, name, is_initial) values (?, 'Recebido', true) returning id",
            UUID.class, companyId);
        mockMvc.perform(delete("/api/sushi/order-statuses/" + initial).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("initial_status_undeletable"));
    }

    @Test
    @DisplayName("delete de status com pedidos → 409 status_in_use")
    void statusInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        UUID st = jdbcTemplate.queryForObject(
            "insert into sushi_order_statuses (company_id, name) values (?, 'Em preparo') returning id", UUID.class, companyId);
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, companyId, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, companyId, "+5511999990011", "C");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, companyId, contact, instance);
        jdbcTemplate.update("insert into sushi_orders (company_id, conversation_id, contact_id, status, subtotal_cents, total_cents) "
            + "values (?, ?, ?, ?, 1000, 1000)", companyId, conv, contact, st);

        mockMvc.perform(delete("/api/sushi/order-statuses/" + st).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("status_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-sushi → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/sushi/order-statuses").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
