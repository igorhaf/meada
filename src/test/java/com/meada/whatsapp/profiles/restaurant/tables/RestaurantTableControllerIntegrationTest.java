package com.meada.whatsapp.profiles.restaurant.tables;

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
 * Testa os endpoints de mesas (camada 7.3): CRUD + profile guard 403 pra tenant não-restaurant.
 */
class RestaurantTableControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
        seedTenant(sub, "rest@test.dev", "restaurant");
        String t = mintValidToken("rest@test.dev", sub);

        mockMvc.perform(post("/api/restaurant/tables").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"label\":\"Mesa 1\",\"capacity\":4,\"notes\":\"janela\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.label").value("Mesa 1"))
            .andExpect(jsonPath("$.available").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from restaurant_tables where label = 'Mesa 1'", UUID.class);

        mockMvc.perform(get("/api/restaurant/tables").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/restaurant/tables/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"capacity\":6}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capacity").value(6));

        mockMvc.perform(patch("/api/restaurant/tables/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(delete("/api/restaurant/tables/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com label duplicado → 409 label_in_use")
    void duplicateLabel() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "rest@test.dev", "restaurant");
        String t = mintValidToken("rest@test.dev", sub);
        jdbcTemplate.update("insert into restaurant_tables (company_id, label, capacity) values (?, 'Mesa 1', 2)", companyId);

        mockMvc.perform(post("/api/restaurant/tables").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"label\":\"Mesa 1\",\"capacity\":4}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("label_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-restaurant (sushi) batendo no /api/restaurant/tables → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        mockMvc.perform(get("/api/restaurant/tables").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
