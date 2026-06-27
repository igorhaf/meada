package com.meada.admin.plans;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o CRUD de planos (camada 6.8): criar (+log), editar, slug duplicado → 409, soft
 * delete, e o guard de super-admin. O TRUNCATE do @BeforeEach limpa os seeds da migration,
 * então cada teste semeia o que precisa.
 */
class AdminPlanControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUPER_SUB = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private String superToken() {
        return mintValidToken(SUPER_ADMIN_EMAIL, SUPER_SUB);
    }

    @Test
    @DisplayName("CRUD: POST cria (201 + PLAN_CREATED) → GET lista → PATCH edita preço")
    void crud() throws Exception {
        mockMvc.perform(post("/admin/plans").header("Authorization", "Bearer " + superToken())
                .contentType("application/json")
                .content("{\"name\":\"Smoke\",\"slug\":\"smoke\",\"monthlyPriceCents\":1000,\"maxAdmins\":2}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Smoke"))
            .andExpect(jsonPath("$.monthlyPriceCents").value(1000))
            .andExpect(jsonPath("$.active").value(true));

        Long logged = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action = 'PLAN_CREATED'", Long.class);
        assertThat(logged).isEqualTo(1L);

        UUID id = jdbcTemplate.queryForObject("select id from plans where slug = 'smoke'", UUID.class);

        mockMvc.perform(get("/admin/plans").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/admin/plans/" + id).header("Authorization", "Bearer " + superToken())
                .contentType("application/json").content("{\"monthlyPriceCents\":2500}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.monthlyPriceCents").value(2500));
    }

    @Test
    @DisplayName("slug duplicado → 409 plan_slug_exists")
    void slugDuplicate() throws Exception {
        jdbcTemplate.update("insert into plans (name, slug) values ('Existente', 'dup')");

        mockMvc.perform(post("/admin/plans").header("Authorization", "Bearer " + superToken())
                .contentType("application/json").content("{\"name\":\"Outro\",\"slug\":\"dup\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("plan_slug_exists"));
    }

    @Test
    @DisplayName("DELETE → soft delete (active=false), PLAN_DELETED logado")
    void softDelete() throws Exception {
        UUID id = jdbcTemplate.queryForObject(
            "insert into plans (name, slug) values ('Apagar', 'apagar') returning id", UUID.class);

        mockMvc.perform(delete("/admin/plans/" + id).header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isNoContent());

        Boolean active = jdbcTemplate.queryForObject(
            "select active from plans where id = ?", Boolean.class, id);
        assertThat(active).isFalse();
        Long logged = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action = 'PLAN_DELETED'", Long.class);
        assertThat(logged).isEqualTo(1L);
    }

    @Test
    @DisplayName("tenant → 403 nos endpoints de planos")
    void tenant_forbidden() throws Exception {
        UUID tenantSub = UUID.randomUUID();
        seedTenantAdmin(TENANT_ADMIN_EMAIL, tenantSub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, tenantSub);
        mockMvc.perform(get("/admin/plans").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/plans").header("Authorization", "Bearer " + t)
                .contentType("application/json").content("{\"name\":\"x\",\"slug\":\"y\"}"))
            .andExpect(status().isForbidden());
    }
}
