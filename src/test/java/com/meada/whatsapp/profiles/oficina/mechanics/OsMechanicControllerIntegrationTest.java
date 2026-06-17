package com.meada.whatsapp.profiles.oficina.mechanics;

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
 * Testa os endpoints de mecânicos (camada 7.9): CRUD + toggle + profile guard 403.
 */
class OsMechanicControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
        seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);

        mockMvc.perform(post("/api/oficina/mechanics").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Carlos\",\"specialty\":\"Motor/suspensão\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Carlos"))
            .andExpect(jsonPath("$.active").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from os_mechanics where name = 'Carlos'", UUID.class);

        mockMvc.perform(get("/api/oficina/mechanics").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/oficina/mechanics/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"specialty\":\"Elétrica/ar\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.specialty").value("Elétrica/ar"));

        mockMvc.perform(patch("/api/oficina/mechanics/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/oficina/mechanics/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 mechanic_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);
        mockMvc.perform(get("/api/oficina/mechanics/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("mechanic_not_found"));
    }

    @Test
    @DisplayName("tenant NÃO-oficina (pet) → /api/oficina/mechanics → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/oficina/mechanics").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
