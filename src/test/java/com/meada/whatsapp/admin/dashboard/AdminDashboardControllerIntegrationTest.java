package com.meada.whatsapp.admin.dashboard;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa GET /admin/dashboard/overview pela camada HTTP. super-admin → 200 com todos os
 * campos do DTO. tenant-admin → 403. Sem auth → 401.
 */
class AdminDashboardControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUB = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    @DisplayName("super-admin → 200 com todos os campos do overview")
    void superAdmin_returnsOverview() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/dashboard/overview").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeCompanies").exists())
            .andExpect(jsonPath("$.companiesCreatedThisMonth").exists())
            .andExpect(jsonPath("$.messagesToday").exists())
            .andExpect(jsonPath("$.messagesYesterday").exists())
            .andExpect(jsonPath("$.openConversations").exists())
            .andExpect(jsonPath("$.openConversationsCompanyCount").exists())
            .andExpect(jsonPath("$.geminiTokensThisMonth").exists())
            .andExpect(jsonPath("$.alerts").isArray());
    }

    @Test
    @DisplayName("tenant-admin → 403 forbidden_not_super_admin")
    void tenantAdmin_returns403() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/dashboard/overview").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_super_admin"));
    }

    @Test
    @DisplayName("sem auth → 401")
    void noAuth_returns401() throws Exception {
        mockMvc.perform(get("/admin/dashboard/overview"))
            .andExpect(status().isUnauthorized());
    }
}
