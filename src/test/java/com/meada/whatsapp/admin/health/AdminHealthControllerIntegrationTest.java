package com.meada.whatsapp.admin.health;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de saúde/jobs/erros (camada 6.4, super-admin): health resumo, lista de
 * jobs, lista de erros, e o guard de super-admin.
 */
class AdminHealthControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUPER_SUB = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private String superToken() {
        return mintValidToken(SUPER_ADMIN_EMAIL, SUPER_SUB);
    }

    @Test
    @DisplayName("GET /admin/health retorna webhookOff + contadores 1h")
    void health_summary() throws Exception {
        // dry-run no contexto de teste é false (default) → webhookOff=false. Validamos que os
        // campos existem e os contadores são 0 (tabelas vazias após o truncate).
        mockMvc.perform(get("/admin/health").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.webhookOff").exists())
            .andExpect(jsonPath("$.lastHeartbeatAt").doesNotExist())
            .andExpect(jsonPath("$.heartbeatsLastHour").value(0))
            .andExpect(jsonPath("$.jobsLastHour").value(0))
            .andExpect(jsonPath("$.errorsLastHour").value(0));
    }

    @Test
    @DisplayName("GET /admin/jobs lista execuções de jobs (mais recente primeiro)")
    void jobs_list() throws Exception {
        jdbcTemplate.update(
            "insert into scheduled_job_runs (job_name, status, finished_at) "
                + "values ('ReminderJob', 'success', now())");
        jdbcTemplate.update(
            "insert into scheduled_job_runs (job_name, status, error_message) "
                + "values ('ReactivationJob', 'failed', 'boom')");

        mockMvc.perform(get("/admin/jobs").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[?(@.jobName == 'ReactivationJob')].status").value("failed"))
            .andExpect(jsonPath("$.items[?(@.jobName == 'ReactivationJob')].errorMessage").value("boom"));
    }

    @Test
    @DisplayName("GET /admin/errors lista erros + filtro por source")
    void errors_listAndFilter() throws Exception {
        jdbcTemplate.update(
            "insert into error_log (source, message) values ('OutboundService', 'evolution down')");
        jdbcTemplate.update(
            "insert into error_log (source, message) values ('GeminiProvider', 'ai 400')");

        mockMvc.perform(get("/admin/errors").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/admin/errors?source=GeminiProvider")
                .header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].source").value("GeminiProvider"))
            .andExpect(jsonPath("$.items[0].message").value("ai 400"));
    }

    @Test
    @DisplayName("tenant → 403 nos três endpoints de saúde")
    void tenant_forbidden() throws Exception {
        UUID tenantSub = UUID.randomUUID();
        seedTenantAdmin(TENANT_ADMIN_EMAIL, tenantSub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, tenantSub);
        mockMvc.perform(get("/admin/health").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/jobs").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/errors").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
    }
}
