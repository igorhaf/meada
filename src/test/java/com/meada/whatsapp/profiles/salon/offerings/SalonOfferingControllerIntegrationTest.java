package com.meada.whatsapp.profiles.salon.offerings;

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
 * Testa os endpoints de serviços (camada 7.5): CRUD básico + profile guard 403.
 */
class SalonOfferingControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD básico: POST cria → GET lista → toggle desliga")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "salon@test.dev", "salon");
        String t = mintValidToken("salon@test.dev", sub);

        mockMvc.perform(post("/api/salon/services").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Manicure\",\"category\":\"Unha\",\"durationMinutes\":45,\"priceCents\":3500}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Manicure"))
            .andExpect(jsonPath("$.durationMinutes").value(45));

        UUID id = jdbcTemplate.queryForObject("select id from salon_offerings where name = 'Manicure'", UUID.class);

        mockMvc.perform(get("/api/salon/services").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/salon/services/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("tenant NÃO-salon (sushi) → /api/salon/services → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        mockMvc.perform(get("/api/salon/services").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
