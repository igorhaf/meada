package com.meada.admin.users;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa /admin/users (camada 6.2) pela camada HTTP. super-admin opera; tenant → 403.
 * seedTenantAdmin cria company + auth.users + users row e retorna o companyId.
 */
class UserAdminControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUPER_SUB = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private String superToken() {
        return mintValidToken(SUPER_ADMIN_EMAIL, SUPER_SUB);
    }

    @Test
    @DisplayName("GET /admin/users lista global com filtro por email (q)")
    void list_filterByEmail() throws Exception {
        seedTenantAdmin("alice@x.com", UUID.fromString("a0000000-0000-0000-0000-000000000001"));
        seedTenantAdmin("bob@y.com", UUID.fromString("b0000000-0000-0000-0000-000000000002"));

        mockMvc.perform(get("/admin/users?q=alice").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].email").value("alice@x.com"))
            .andExpect(jsonPath("$.items[0].companyName").isNotEmpty());
    }

    @Test
    @DisplayName("GET /admin/users/{id} detalhe com recentActions (vazio inicialmente)")
    void detail_withRecentActions() throws Exception {
        UUID uid = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
        seedTenantAdmin("carol@x.com", uid);

        mockMvc.perform(get("/admin/users/" + uid).header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("carol@x.com"))
            .andExpect(jsonPath("$.suspended").value(false))
            .andExpect(jsonPath("$.recentActions").isArray());
    }

    @Test
    @DisplayName("suspend → 204 + USER_SUSPENDED logado; segundo suspend → 409")
    void suspend_thenConflict() throws Exception {
        UUID uid = UUID.fromString("a0000000-0000-0000-0000-0000000000bb");
        seedTenantAdmin("dan@x.com", uid);

        mockMvc.perform(post("/admin/users/" + uid + "/suspend")
                .header("Authorization", "Bearer " + superToken())
                .contentType("application/json").content("{\"reason\":\"abuso\"}"))
            .andExpect(status().isNoContent());

        Boolean suspended = jdbcTemplate.queryForObject(
            "select suspended from users where id = ?", Boolean.class, uid);
        org.assertj.core.api.Assertions.assertThat(suspended).isTrue();
        Long logged = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action='USER_SUSPENDED' and target_id=?",
            Long.class, uid);
        org.assertj.core.api.Assertions.assertThat(logged).isEqualTo(1);

        mockMvc.perform(post("/admin/users/" + uid + "/suspend")
                .header("Authorization", "Bearer " + superToken())
                .contentType("application/json").content("{}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("user_already_suspended"));
    }

    @Test
    @DisplayName("reactivate → 204 limpa suspensão")
    void reactivate_clearsSuspension() throws Exception {
        UUID uid = UUID.fromString("a0000000-0000-0000-0000-0000000000cc");
        seedTenantAdmin("eve@x.com", uid);
        jdbcTemplate.update("update users set suspended=true, suspended_at=now() where id=?", uid);

        mockMvc.perform(post("/admin/users/" + uid + "/reactivate")
                .header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isNoContent());

        Boolean suspended = jdbcTemplate.queryForObject(
            "select suspended from users where id = ?", Boolean.class, uid);
        org.assertj.core.api.Assertions.assertThat(suspended).isFalse();
    }

    @Test
    @DisplayName("DELETE → soft delete (deleted_at set, sai da lista, USER_DELETED logado)")
    void softDelete_excludesFromList() throws Exception {
        UUID uid = UUID.fromString("a0000000-0000-0000-0000-0000000000dd");
        seedTenantAdmin("frank@x.com", uid);

        mockMvc.perform(delete("/admin/users/" + uid).header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isNoContent());

        Object deletedAt = jdbcTemplate.queryForMap("select deleted_at from users where id=?", uid).get("deleted_at");
        org.assertj.core.api.Assertions.assertThat(deletedAt).isNotNull();
        // sai da lista filtrada por email
        mockMvc.perform(get("/admin/users?q=frank").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
        Long logged = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action='USER_DELETED' and target_id=?", Long.class, uid);
        org.assertj.core.api.Assertions.assertThat(logged).isEqualTo(1);
    }

    @Test
    @DisplayName("password-reset → 501 (SERVICE_ROLE_KEY ausente)")
    void passwordReset_notImplemented() throws Exception {
        UUID uid = UUID.fromString("a0000000-0000-0000-0000-0000000000ee");
        seedTenantAdmin("grace@x.com", uid);

        mockMvc.perform(post("/admin/users/" + uid + "/password-reset")
                .header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isNotImplemented())
            .andExpect(jsonPath("$.reason").value("service_role_key_not_configured"));
    }

    @Test
    @DisplayName("sem auth → 401; tenant → 403")
    void authGuards() throws Exception {
        mockMvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());

        UUID tenantSub = UUID.fromString("a0000000-0000-0000-0000-0000000000ff");
        seedTenantAdmin(TENANT_ADMIN_EMAIL, tenantSub);
        mockMvc.perform(get("/admin/users")
                .header("Authorization", "Bearer " + mintValidToken(TENANT_ADMIN_EMAIL, tenantSub)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_super_admin"));
    }
}
