package com.meada.whatsapp.profiles.eventos.planners;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de cerimonialistas (camada 8.2): CRUD + toggle + delete-em-uso 409 +
 * profile guard 403.
 */
class EventPlannerControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria → GET lista → PATCH edita → toggle → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);

        mockMvc.perform(post("/api/eventos/planners").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Beatriz\",\"specialty\":\"casamentos\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Beatriz"))
            .andExpect(jsonPath("$.active").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from event_planners where name = 'Beatriz'", UUID.class);

        mockMvc.perform(get("/api/eventos/planners").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/eventos/planners/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"specialty\":\"corporativo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.specialty").value("corporativo"));

        mockMvc.perform(patch("/api/eventos/planners/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/eventos/planners/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE de cerimonialista atribuído a proposta → 409 planner_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);

        UUID plannerId = jdbcTemplate.queryForObject(
            "insert into event_planners (company_id, name) values (?, 'Beatriz') returning id", UUID.class, companyId);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+5511999990293", "Cliente");
        jdbcTemplate.update(
            "insert into event_proposals (company_id, contact_id, planner_id, customer_name, status) "
                + "values (?, ?, ?, 'Cliente', 'rascunho')",
            companyId, contactId, plannerId);

        mockMvc.perform(delete("/api/eventos/planners/" + plannerId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("planner_in_use"));
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "barbearia@test.dev", "barbearia");
        String t = mintValidToken("barbearia@test.dev", sub);

        mockMvc.perform(get("/api/eventos/planners").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
