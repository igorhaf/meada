package com.meada.lgpd;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints LGPD (camada 5.24 #89/#90) via camada HTTP (filtro + controller). Cobre:
 * export 200 com o contato; erase 204 + linha de fato apagada; contato de outra empresa → 404;
 * sem auth → 401. Semeia um contato direto no banco (service_role) na empresa do tenant.
 */
class LgpdControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("77777777-7777-7777-7777-777777777777");

    /** Semeia um contato simples na empresa e devolve o id. */
    private UUID seedContact(UUID companyId, String phone, String name) {
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, phone, name);
        return contactId;
    }

    @Test
    @DisplayName("GET /admin/contacts/{id}/export → 200 com os dados do contato")
    void export_returns200() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        UUID contactId = seedContact(companyId, "+5511966660001", "Fulano");
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(get("/admin/contacts/" + contactId + "/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contact.phoneNumber").value("+5511966660001"))
            .andExpect(jsonPath("$.contact.name").value("Fulano"))
            .andExpect(jsonPath("$.conversations").isArray())
            .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    @DisplayName("DELETE /admin/contacts/{id}/erase → 204 e apaga o contato")
    void erase_returns204AndDeletes() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        UUID contactId = seedContact(companyId, "+5511966660002", "Sicrano");
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(delete("/admin/contacts/" + contactId + "/erase")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        Long count = jdbcTemplate.queryForObject(
            "select count(*) from contacts where id = ?", Long.class, contactId);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("GET export de contato de outra empresa → 404 contact_not_found")
    void export_otherCompany_returns404() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        // Contato pertence a OUTRA empresa.
        UUID otherCompany = UUID.randomUUID();
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            otherCompany, "Outra", "outra-" + otherCompany);
        UUID otherContact = seedContact(otherCompany, "+5511966660003", "Alheio");

        mockMvc.perform(get("/admin/contacts/" + otherContact + "/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("contact_not_found"));
    }

    @Test
    @DisplayName("GET export sem auth → 401")
    void export_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/contacts/" + UUID.randomUUID() + "/export"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE erase sem auth → 401")
    void erase_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/admin/contacts/" + UUID.randomUUID() + "/erase"))
            .andExpect(status().isUnauthorized());
    }
}
