package com.meada.metrics;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa a comparação mês a mês (/admin/metrics/comparison, camada 5.23 #66) via HTTP. Semeia
 * conversas/mensagens no mês ATUAL e no ANTERIOR com created_at explícito (deslocado de
 * date_trunc('month', now())) e assevera as contagens e os deltas. Sem auth → 401.
 *
 * <p>Os created_at usam offsets seguros (5 dias após o início de cada mês) para não esbarrar
 * na virada de mês — quando o teste roda no dia 1º, "início do mês + 5 dias" ainda cai dentro
 * do mês-alvo.
 */
class MetricsComparisonControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("55555555-5555-5555-5555-555555555555");

    /** Cria uma instância + conversa (com created_at no mês-alvo) e devolve o conversationId. */
    private UUID seedConversationInMonth(UUID companyId, int monthsAgo) {
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name, created_at) "
                + "values (?, ?, ?, ?, " + monthExpr(monthsAgo) + ")",
            contactId, companyId, "+5511" + Math.abs(contactId.hashCode() % 100000000), "Contato");
        UUID instanceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) "
                + "values (?, ?, ?, ?)",
            instanceId, companyId, "inst-" + instanceId, "tok-" + instanceId);
        UUID conversationId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, created_at) "
                + "values (?, ?, ?, ?, " + monthExpr(monthsAgo) + ")",
            conversationId, companyId, contactId, instanceId);
        return conversationId;
    }

    /** Insere uma mensagem inbound ou outbound no mês-alvo. */
    private void seedMessage(UUID companyId, UUID conversationId, int monthsAgo, boolean inbound) {
        if (inbound) {
            jdbcTemplate.update(
                "insert into messages (company_id, conversation_id, direction, sender, content, created_at) "
                    + "values (?, ?, 'inbound', 'contact', 'oi', " + monthExpr(monthsAgo) + ")",
                companyId, conversationId);
        } else {
            jdbcTemplate.update(
                "insert into messages (company_id, conversation_id, direction, sender, content, created_at) "
                    + "values (?, ?, 'outbound', 'ai', 'ola', " + monthExpr(monthsAgo) + ")",
                companyId, conversationId);
        }
    }

    /** SQL: 5 dias após o início do mês deslocado de monthsAgo (cai dentro do mês-alvo). */
    private static String monthExpr(int monthsAgo) {
        return "date_trunc('month', now()) - make_interval(months => " + monthsAgo
            + ") + interval '5 days'";
    }

    @Test
    @DisplayName("GET /admin/metrics/comparison → contagens atual/anterior e deltas")
    void comparison_currentVsPrevious() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        // mês ATUAL: 1 conversa, 2 inbound, 1 outbound, 1 contato ativo.
        UUID convCurrent = seedConversationInMonth(companyId, 0);
        seedMessage(companyId, convCurrent, 0, true);
        seedMessage(companyId, convCurrent, 0, true);
        seedMessage(companyId, convCurrent, 0, false);

        // mês ANTERIOR: 1 conversa, 1 inbound, 0 outbound, 1 contato ativo.
        UUID convPrevious = seedConversationInMonth(companyId, 1);
        seedMessage(companyId, convPrevious, 1, true);

        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(get("/admin/metrics/comparison").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            // atual
            .andExpect(jsonPath("$.current.conversations").value(1))
            .andExpect(jsonPath("$.current.messagesInbound").value(2))
            .andExpect(jsonPath("$.current.messagesOutbound").value(1))
            .andExpect(jsonPath("$.current.activeContacts").value(1))
            // anterior
            .andExpect(jsonPath("$.previous.conversations").value(1))
            .andExpect(jsonPath("$.previous.messagesInbound").value(1))
            .andExpect(jsonPath("$.previous.messagesOutbound").value(0))
            .andExpect(jsonPath("$.previous.activeContacts").value(1))
            // deltas (atual - anterior)
            .andExpect(jsonPath("$.deltas.conversations").value(0))
            .andExpect(jsonPath("$.deltas.messagesInbound").value(1))
            .andExpect(jsonPath("$.deltas.messagesOutbound").value(1))
            .andExpect(jsonPath("$.deltas.activeContacts").value(0));
    }

    @Test
    @DisplayName("GET /admin/metrics/comparison sem auth → 401")
    void comparison_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/metrics/comparison"))
            .andExpect(status().isUnauthorized());
    }
}
