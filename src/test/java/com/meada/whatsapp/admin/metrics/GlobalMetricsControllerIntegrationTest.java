package com.meada.whatsapp.admin.metrics;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa as métricas globais (camada 6.3, super-admin): KPIs reais, top tenants ordenado,
 * empresas em risco filtradas por 30d, e o guard de super-admin.
 */
class GlobalMetricsControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUPER_SUB = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private String superToken() {
        return mintValidToken(SUPER_ADMIN_EMAIL, SUPER_SUB);
    }

    /** Cria uma empresa direta (sem tenant-admin) e devolve o id. */
    private UUID seedCompany(String name, String slug) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)", id, name, slug);
        return id;
    }

    /** Insere N mensagens inbound recentes para uma empresa (com tokens p/ exercitar a soma). */
    private void seedMessages(UUID companyId, int count, Integer tokensEach) {
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, companyId, "inst-" + companyId, "tok");
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, companyId, "+55119" + Math.abs(companyId.hashCode() % 100000000), "C");
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
                + "values (?, ?, ?, ?, 'open', 'ai')",
            conv, companyId, contact, instance);
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                "insert into messages (company_id, conversation_id, direction, sender, content, tokens_in, tokens_out) "
                    + "values (?, ?, 'inbound', 'contact', ?, ?, ?)",
                companyId, conv, "msg-" + i, tokensEach, tokensEach);
        }
    }

    @Test
    @DisplayName("GET /admin/metrics/global retorna KPIs reais (companies, messages, tokens somados)")
    void global_kpis() throws Exception {
        UUID c1 = seedCompany("Alpha", "alpha-m");
        seedCompany("Beta", "beta-m");
        seedMessages(c1, 3, 10);   // 3 mensagens, 10+10 tokens cada = 60 tokens

        mockMvc.perform(get("/admin/metrics/global").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpis.totalCompanies").value(2))
            .andExpect(jsonPath("$.kpis.activeCompanies").value(2))
            .andExpect(jsonPath("$.kpis.totalMessages30d").value(3))
            .andExpect(jsonPath("$.kpis.geminiTokensLast30d").value(60))
            .andExpect(jsonPath("$.comparison").exists())
            .andExpect(jsonPath("$.companiesCreatedPerMonth").isArray());
    }

    @Test
    @DisplayName("top tenants ordenado por volume de mensagens (maior primeiro)")
    void global_topTenantsOrdered() throws Exception {
        UUID small = seedCompany("Small", "small-m");
        UUID big = seedCompany("Big", "big-m");
        seedMessages(small, 2, null);
        seedMessages(big, 5, null);

        mockMvc.perform(get("/admin/metrics/global").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topTenants[0].name").value("Big"))
            .andExpect(jsonPath("$.topTenants[0].messagesLast30d").value(5))
            .andExpect(jsonPath("$.topTenants[1].name").value("Small"));
    }

    @Test
    @DisplayName("empresas em risco: sem mensagem há > 30 dias OU nunca (filtro por 30d)")
    void global_atRiskFilteredBy30d() throws Exception {
        UUID active = seedCompany("Active", "active-m");
        UUID silent = seedCompany("Silent", "silent-m");
        seedMessages(active, 1, null);   // mensagem recente → NÃO em risco
        // 'silent' nunca teve mensagem → em risco (max IS NULL).

        mockMvc.perform(get("/admin/metrics/global").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            // Active (com mensagem recente) não aparece; Silent (sem mensagem) aparece.
            .andExpect(jsonPath("$.atRisk[?(@.name == 'Silent')]").isNotEmpty())
            .andExpect(jsonPath("$.atRisk[?(@.name == 'Active')]").isEmpty());
    }

    @Test
    @DisplayName("tenant → 403")
    void tenant_forbidden() throws Exception {
        UUID tenantSub = UUID.randomUUID();
        seedTenantAdmin(TENANT_ADMIN_EMAIL, tenantSub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, tenantSub);
        mockMvc.perform(get("/admin/metrics/global").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden());
    }
}
