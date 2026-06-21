package com.meada.whatsapp.profiles.eventos.proposals;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de propostas (camada 8.2): POST abre, GET list/detalhe com itens+cronograma,
 * PATCH item recalcula total, PATCH status orcada/aprovada, empty_budget, proposal_locked, 409
 * transição inválida, CRONOGRAMA ordenado por start_time via HTTP, profile guard 403.
 */
class EventProposalControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID openProposalId(String token) throws Exception {
        mockMvc.perform(post("/api/eventos/proposals").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"customerName\":\"Pedro\",\"eventType\":\"corporativo\","
                    + "\"guestCount\":80,\"briefing\":\"Confraternização\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("rascunho"))
            .andExpect(jsonPath("$.totalCents").value(0));
        return jdbcTemplate.queryForObject(
            "select id from event_proposals where customer_name = 'Pedro' order by opened_at desc limit 1", UUID.class);
    }

    @Test
    @DisplayName("POST abre rascunho; add item recalcula total; orcada com total>0")
    void openAndBudget() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);

        UUID id = openProposalId(t);

        // C2: item de R$ 5000 → total 500000.
        mockMvc.perform(post("/api/eventos/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Espaço\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/eventos/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(jsonPath("$.totalCents").value(500000));
        // 2º item de R$ 3000 → total 800000.
        mockMvc.perform(post("/api/eventos/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Buffet\",\"quantity\":1,\"unitPriceCents\":300000}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/eventos/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(jsonPath("$.totalCents").value(800000));

        // C3: rascunho → orcada com total>0.
        mockMvc.perform(patch("/api/eventos/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"orcada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("orcada"));
    }

    @Test
    @DisplayName("orçar proposta sem item → 400 empty_budget")
    void emptyBudget() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(patch("/api/eventos/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"orcada\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("empty_budget"));
    }

    @Test
    @DisplayName("PATCH item numa proposta fechada → 409 proposal_locked")
    void proposalLocked() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);
        UUID id = openProposalId(t);
        // adiciona item, captura o item id, então trava a proposta.
        mockMvc.perform(post("/api/eventos/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Espaço\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());
        UUID itemId = jdbcTemplate.queryForObject(
            "select id from event_proposal_items where proposal_id = ?", UUID.class, id);
        jdbcTemplate.update("update event_proposals set status = 'fechada' where id = ?", id);

        mockMvc.perform(patch("/api/eventos/proposals/" + id + "/items/" + itemId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"unitPriceCents\":999999}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("proposal_locked"));
    }

    @Test
    @DisplayName("transição inválida (rascunho→aprovada) → 409 invalid_status_transition")
    void invalidTransition() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(patch("/api/eventos/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"aprovada\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("CRONOGRAMA: 3 marcos fora de ordem via HTTP → detalhe ORDENADO por horário; total intacto")
    void timelineOrderedViaHttp() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);
        UUID id = openProposalId(t);

        // 1 item de orçamento → total 500000.
        mockMvc.perform(post("/api/eventos/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Espaço\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());

        // 3 marcos FORA de ordem: 23:00, 19:00, 20:00.
        mockMvc.perform(post("/api/eventos/proposals/" + id + "/timeline").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"startTime\":\"23:00\",\"title\":\"Festa\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/eventos/proposals/" + id + "/timeline").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"startTime\":\"19:00\",\"title\":\"Recepção\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/eventos/proposals/" + id + "/timeline").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"startTime\":\"20:00\",\"title\":\"Cerimônia\"}")).andExpect(status().isCreated());

        // detalhe: cronograma ORDENADO por start_time + total intacto (cronograma não entra no total).
        mockMvc.perform(get("/api/eventos/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline.length()").value(3))
            .andExpect(jsonPath("$.timeline[0].title").value("Recepção"))
            .andExpect(jsonPath("$.timeline[1].title").value("Cerimônia"))
            .andExpect(jsonPath("$.timeline[2].title").value("Festa"))
            .andExpect(jsonPath("$.totalCents").value(500000));
    }

    @Test
    @DisplayName("GET list devolve as propostas do tenant")
    void listProposals() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);
        openProposalId(t);

        mockMvc.perform(get("/api/eventos/proposals").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);

        mockMvc.perform(get("/api/eventos/proposals").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
