package com.meada.whatsapp.profiles.otica.config;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de config FUNDIDA do otica (camada 8.12): GET defaults (09:00–18:00 / exame 30 /
 * mínimo 0 / lead 7), PUT atualiza os dois fluxos, PUT com opens >= closes → 400 invalid_hours,
 * profile guard 403.
 */
class OticaConfigControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("GET sem config → defaults (09:00–18:00 / exame 30 / mínimo 0 / lead 7)")
    void getDefault() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "otica@test.dev", "otica");
        String t = mintValidToken("otica@test.dev", sub);

        mockMvc.perform(get("/api/otica/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opensAt").value("09:00:00"))
            .andExpect(jsonPath("$.closesAt").value("18:00:00"))
            .andExpect(jsonPath("$.examDurationMinutes").value(30))
            .andExpect(jsonPath("$.minOrderCents").value(0))
            .andExpect(jsonPath("$.leadTimeDaysDefault").value(7));
    }

    @Test
    @DisplayName("PUT atualiza janela + duração de exame + mínimo + lead")
    void putUpdates() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "otica@test.dev", "otica");
        String t = mintValidToken("otica@test.dev", sub);

        mockMvc.perform(put("/api/otica/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"opensAt\":\"08:00\",\"closesAt\":\"19:00\","
                    + "\"examDurationMinutes\":45,\"minOrderCents\":5000,\"leadTimeDaysDefault\":10}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opensAt").value("08:00:00"))
            .andExpect(jsonPath("$.closesAt").value("19:00:00"))
            .andExpect(jsonPath("$.examDurationMinutes").value(45))
            .andExpect(jsonPath("$.minOrderCents").value(5000))
            .andExpect(jsonPath("$.leadTimeDaysDefault").value(10));
    }

    @Test
    @DisplayName("PUT com opens >= closes → 400 invalid_hours")
    void putInvalidHours() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "otica@test.dev", "otica");
        String t = mintValidToken("otica@test.dev", sub);

        mockMvc.perform(put("/api/otica/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"opensAt\":\"18:00\",\"closesAt\":\"08:00\","
                    + "\"examDurationMinutes\":30,\"minOrderCents\":0,\"leadTimeDaysDefault\":7}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_hours"));
    }

    @Test
    @DisplayName("tenant NÃO-otica (pet) → /api/otica/config → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/otica/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
