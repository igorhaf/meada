package com.meada.whatsapp.admin.audit;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa as visões globais de auditoria/segurança/ações (camada 6.5, super-admin).
 */
class AdminAuditControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUPER_SUB = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private String superToken() {
        return mintValidToken(SUPER_ADMIN_EMAIL, SUPER_SUB);
    }

    @Test
    @DisplayName("GET /admin/audit/all retorna audit_log global cross-tenant")
    void auditAll_global() throws Exception {
        UUID c1 = seedTenantAdmin("a@x.com", UUID.randomUUID());
        UUID c2 = seedTenantAdmin("b@y.com", UUID.randomUUID());
        jdbcTemplate.update(
            "insert into audit_log (company_id, action, entity, metadata) values (?, 'insert', 'services', '{}'::jsonb)", c1);
        jdbcTemplate.update(
            "insert into audit_log (company_id, action, entity, metadata) values (?, 'update', 'faqs', '{}'::jsonb)", c2);

        mockMvc.perform(get("/admin/audit/all").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].companyName").isNotEmpty());
    }

    @Test
    @DisplayName("GET /admin/audit/all filtro por entity")
    void auditAll_filterEntity() throws Exception {
        UUID c1 = seedTenantAdmin("a@x.com", UUID.randomUUID());
        jdbcTemplate.update("insert into audit_log (company_id, action, entity, metadata) values (?, 'insert', 'services', '{}'::jsonb)", c1);
        jdbcTemplate.update("insert into audit_log (company_id, action, entity, metadata) values (?, 'insert', 'faqs', '{}'::jsonb)", c1);

        mockMvc.perform(get("/admin/audit/all?entity=faqs").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].entity").value("faqs"));
    }

    @Test
    @DisplayName("GET /admin/security/access-logs/all retorna access_logs global")
    void accessLogsAll_global() throws Exception {
        UUID c1 = seedTenantAdmin("a@x.com", UUID.randomUUID());
        jdbcTemplate.update(
            "insert into access_logs (company_id, email, action, ip) values (?, 'a@x.com', 'login_success', '1.2.3.4')", c1);

        mockMvc.perform(get("/admin/security/access-logs/all").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].action").value("login_success"));
    }

    @Test
    @DisplayName("GET /admin/actions retorna admin_action_log")
    void adminActions_list() throws Exception {
        UUID admin = SUPER_SUB;
        jdbcTemplate.update(
            "insert into admin_action_log (super_admin_user_id, action, target_type, target_id, payload) "
                + "values (?, 'COMPANY_SUSPENDED', 'company', ?, '{\"reason\":\"x\"}'::jsonb)",
            admin, UUID.randomUUID());

        mockMvc.perform(get("/admin/actions").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].action").value("COMPANY_SUSPENDED"));
    }

    @Test
    @DisplayName("tenant → 403 nos três endpoints")
    void tenant_forbidden() throws Exception {
        UUID tenantSub = UUID.randomUUID();
        seedTenantAdmin(TENANT_ADMIN_EMAIL, tenantSub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, tenantSub);
        mockMvc.perform(get("/admin/audit/all").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/security/access-logs/all").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/actions").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
    }
}
