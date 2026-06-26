package com.meada.whatsapp.profiles.concessionaria.config;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de config (camada 8.17): GET com fallback aos defaults (45/0/09:00/18:00),
 * PUT upsert, e profile guard 403.
 */
class ConcessionariaConfigControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("GET sem config gravada → defaults (45/0/09:00/18:00)")
    void getDefaults() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);

        mockMvc.perform(get("/api/concessionaria/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.durationMinutes").value(45))
            .andExpect(jsonPath("$.bufferMinutes").value(0))
            .andExpect(jsonPath("$.opensAt").value("09:00:00"))
            .andExpect(jsonPath("$.closesAt").value("18:00:00"));
    }

    @Test
    @DisplayName("PUT grava → GET reflete os valores")
    void putThenGet() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);

        mockMvc.perform(put("/api/concessionaria/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"businessName\":\"Loja X\",\"durationMinutes\":60,"
                    + "\"bufferMinutes\":0,\"opensAt\":\"08:00\",\"closesAt\":\"19:00\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.durationMinutes").value(60))
            .andExpect(jsonPath("$.businessName").value("Loja X"));

        mockMvc.perform(get("/api/concessionaria/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.durationMinutes").value(60))
            .andExpect(jsonPath("$.opensAt").value("08:00:00"));
    }

    @Test
    @DisplayName("PUT com janela inválida (opens >= closes) → 400 invalid_hours")
    void putInvalidHours() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);

        mockMvc.perform(put("/api/concessionaria/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"durationMinutes\":45,\"bufferMinutes\":0,"
                    + "\"opensAt\":\"18:00\",\"closesAt\":\"09:00\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_hours"));
    }

    @Test
    @DisplayName("tenant NÃO-concessionaria (pet) → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/concessionaria/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
