package com.meada.metrics;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o ranking de contatos mais ativos (/admin/contacts/top, camada 5.23 #68) via HTTP.
 * Cobre: ordenação por contagem de mensagens desc, isolamento por empresa, e sem auth → 401.
 */
class TopContactsControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("44444444-4444-4444-4444-444444444444");

    /**
     * Provisiona um contato + conversa + N mensagens inbound numa empresa, devolve o contactId.
     * Reusa o padrão do SearchControllerIntegrationTest (instance + conversa + FKs compostas).
     */
    private UUID seedContactWithMessages(UUID companyId, String name, String phone, int messageCount) {
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, phone, name);
        UUID instanceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) "
                + "values (?, ?, ?, ?)",
            instanceId, companyId, "inst-" + instanceId, "tok-" + instanceId);
        UUID conversationId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id) "
                + "values (?, ?, ?, ?)",
            conversationId, companyId, contactId, instanceId);
        for (int i = 0; i < messageCount; i++) {
            jdbcTemplate.update(
                "insert into messages (company_id, conversation_id, direction, sender, content) "
                    + "values (?, ?, 'inbound', 'contact', ?)",
                companyId, conversationId, "msg " + i);
        }
        return contactId;
    }

    @Test
    @DisplayName("GET /admin/contacts/top → contatos ordenados por nº de mensagens desc")
    void top_ordersByMessageCountDesc() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        seedContactWithMessages(companyId, "Pouco Ativo", "+5511111110000", 2);
        seedContactWithMessages(companyId, "Muito Ativo", "+5511222220000", 7);
        seedContactWithMessages(companyId, "Medio", "+5511333330000", 4);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(get("/admin/contacts/top").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].name").value("Muito Ativo"))
            .andExpect(jsonPath("$[0].messageCount").value(7))
            .andExpect(jsonPath("$[0].phoneNumber").value("+5511222220000"))
            .andExpect(jsonPath("$[1].name").value("Medio"))
            .andExpect(jsonPath("$[1].messageCount").value(4))
            .andExpect(jsonPath("$[2].name").value("Pouco Ativo"))
            .andExpect(jsonPath("$[2].messageCount").value(2));
    }

    @Test
    @DisplayName("GET /admin/contacts/top isola por empresa — contatos de outra empresa não aparecem")
    void top_tenantIsolation() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        UUID otherCompany = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into companies (id, name, slug) values (?, ?, ?)",
            otherCompany, "Outra", "outra-" + otherCompany);
        seedContactWithMessages(otherCompany, "Da Outra", "+5511999990000", 50);
        seedContactWithMessages(companyId, "Da Minha", "+5511888880000", 3);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(get("/admin/contacts/top").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Da Minha"));
    }

    @Test
    @DisplayName("GET /admin/contacts/top sem auth → 401")
    void top_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/contacts/top"))
            .andExpect(status().isUnauthorized());
    }
}
