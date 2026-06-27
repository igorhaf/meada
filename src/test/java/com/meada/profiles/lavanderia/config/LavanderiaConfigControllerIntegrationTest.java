package com.meada.profiles.lavanderia.config;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de config do lavanderia (camada 8.10): GET fallback (ausente → 0/0/1), PUT upsert
 * com turnaround default, profile guard 403.
 */
class LavanderiaConfigControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("GET sem config gravada → fallback 0/0/1")
    void getFallback() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "lavanderia@test.dev", "lavanderia");
        String t = mintValidToken("lavanderia@test.dev", sub);

        mockMvc.perform(get("/api/lavanderia/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryFeeCents").value(0))
            .andExpect(jsonPath("$.minOrderCents").value(0))
            .andExpect(jsonPath("$.turnaroundDaysDefault").value(1));
    }

    @Test
    @DisplayName("PUT upsert grava taxa+mínimo+turnaround default e GET reflete")
    void putUpsert() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "lavanderia@test.dev", "lavanderia");
        String t = mintValidToken("lavanderia@test.dev", sub);

        mockMvc.perform(put("/api/lavanderia/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"deliveryFeeCents\":700,\"minOrderCents\":3000,\"turnaroundDaysDefault\":3}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryFeeCents").value(700))
            .andExpect(jsonPath("$.turnaroundDaysDefault").value(3));

        mockMvc.perform(get("/api/lavanderia/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.minOrderCents").value(3000))
            .andExpect(jsonPath("$.turnaroundDaysDefault").value(3));
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);

        mockMvc.perform(get("/api/lavanderia/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
