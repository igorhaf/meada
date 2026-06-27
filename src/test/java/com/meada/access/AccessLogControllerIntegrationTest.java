package com.meada.access;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os logs de acesso (camada 5.24 #92) via camada HTTP. Cobre: POST público grava uma
 * linha (verificada por select direto); POST com action inválido → 400; GET do tenant lista
 * só os da própria empresa; GET sem auth → 401. POST é PÚBLICO (não passa pelo filtro) — não
 * manda Bearer.
 */
class AccessLogControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    @DisplayName("POST /api/access-logs (público) grava uma linha de acesso")
    void post_recordsRow() throws Exception {
        mockMvc.perform(post("/api/access-logs")
                .contentType("application/json")
                .content("{\"action\":\"login_failed\",\"email\":\"alguem@exemplo.dev\"}"))
            .andExpect(status().isNoContent());

        Long count = jdbcTemplate.queryForObject(
            "select count(*) from access_logs where email = ? and action = 'login_failed'",
            Long.class, "alguem@exemplo.dev");
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("POST /api/access-logs resolve company_id/user_id pelo email quando o usuário existe")
    void post_resolvesCompanyFromEmail() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(post("/api/access-logs")
                .contentType("application/json")
                .content("{\"action\":\"login_success\",\"email\":\"" + TENANT_ADMIN_EMAIL + "\"}"))
            .andExpect(status().isNoContent());

        UUID storedCompany = jdbcTemplate.queryForObject(
            "select company_id from access_logs where email = ? and action = 'login_success'",
            UUID.class, TENANT_ADMIN_EMAIL);
        assertThat(storedCompany).isEqualTo(companyId);
    }

    @Test
    @DisplayName("POST /api/access-logs com action inválido → 400 invalid_action (nada gravado)")
    void post_invalidAction_returns400() throws Exception {
        mockMvc.perform(post("/api/access-logs")
                .contentType("application/json")
                .content("{\"action\":\"banana\",\"email\":\"x@y.dev\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_action"));

        Long count = jdbcTemplate.queryForObject(
            "select count(*) from access_logs", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("GET /admin/access-logs devolve os acessos da empresa do tenant")
    void list_returnsOwnCompanyRows() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        jdbcTemplate.update(
            "insert into access_logs (company_id, email, action, ip, user_agent) "
                + "values (?, ?, 'login_success', ?, ?)",
            companyId, TENANT_ADMIN_EMAIL, "10.0.0.1", "JUnit-UA");

        mockMvc.perform(get("/admin/access-logs").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].action").value("login_success"))
            .andExpect(jsonPath("$[0].email").value(TENANT_ADMIN_EMAIL))
            .andExpect(jsonPath("$[0].ip").value("10.0.0.1"))
            .andExpect(jsonPath("$[0].userAgent").value("JUnit-UA"))
            .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET /admin/access-logs não vaza acessos de outra empresa")
    void list_doesNotLeakOtherCompany() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        jdbcTemplate.update(
            "insert into access_logs (company_id, email, action) values (?, ?, 'login_success')",
            companyId, TENANT_ADMIN_EMAIL);

        UUID otherCompany = UUID.randomUUID();
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            otherCompany, "Outra", "outra-" + otherCompany);
        jdbcTemplate.update(
            "insert into access_logs (company_id, email, action) values (?, ?, 'login_failed')",
            otherCompany, "outro@x.dev");

        mockMvc.perform(get("/admin/access-logs").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].email").value(TENANT_ADMIN_EMAIL));
    }

    @Test
    @DisplayName("GET /admin/access-logs sem auth → 401")
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/access-logs"))
            .andExpect(status().isUnauthorized());
    }
}
