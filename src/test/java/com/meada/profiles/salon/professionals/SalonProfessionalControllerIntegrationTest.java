package com.meada.profiles.salon.professionals;

import com.meada.admin.AbstractAdminIntegrationTest;
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
 * Testa os endpoints de profissionais (camada 7.5): CRUD + toggle + profile guard 403.
 */
class SalonProfessionalControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
        seedTenant(sub, "salon@test.dev", "salon");
        String t = mintValidToken("salon@test.dev", sub);

        mockMvc.perform(post("/api/salon/professionals").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Carla\",\"specialty\":\"Cabeleireira\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Carla"))
            .andExpect(jsonPath("$.active").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from salon_professionals where name = 'Carla'", UUID.class);

        mockMvc.perform(get("/api/salon/professionals").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/salon/professionals/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"specialty\":\"Colorista\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.specialty").value("Colorista"));

        mockMvc.perform(patch("/api/salon/professionals/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/salon/professionals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 professional_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "salon@test.dev", "salon");
        String t = mintValidToken("salon@test.dev", sub);
        mockMvc.perform(get("/api/salon/professionals/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("professional_not_found"));
    }

    @Test
    @DisplayName("tenant NÃO-salon (dental) → /api/salon/professionals → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        mockMvc.perform(get("/api/salon/professionals").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
