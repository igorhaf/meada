package com.meada.profiles.fotografia.packages;

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
 * Testa os endpoints de pacotes (camada 8.16): CRUD, invalid_duration 400, package_in_use 409, profile
 * guard 403. Clone do DermatologiaProcedureTypeControllerIntegrationTest.
 */
class FotografiaPackageControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria (preço+duração+delivery_days) → GET lista → PATCH edita → toggle → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "foto@test.dev", "fotografia");
        String t = mintValidToken("foto@test.dev", sub);

        mockMvc.perform(post("/api/fotografia/packages").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Ensaio 1h\",\"category\":\"ensaio\",\"durationMinutes\":60,"
                    + "\"priceCents\":50000,\"deliveryDays\":7}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Ensaio 1h"))
            .andExpect(jsonPath("$.durationMinutes").value(60))
            .andExpect(jsonPath("$.priceCents").value(50000))
            .andExpect(jsonPath("$.deliveryDays").value(7));

        UUID id = jdbcTemplate.queryForObject("select id from fotografia_packages where name = 'Ensaio 1h'", UUID.class);

        mockMvc.perform(get("/api/fotografia/packages").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/fotografia/packages/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceCents\":60000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(60000));

        mockMvc.perform(patch("/api/fotografia/packages/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/fotografia/packages/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com duração 0 → 400 invalid_duration")
    void invalidDuration() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "foto@test.dev", "fotografia");
        String t = mintValidToken("foto@test.dev", sub);

        mockMvc.perform(post("/api/fotografia/packages").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Curto\",\"durationMinutes\":0,\"priceCents\":1000}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_duration"));
    }

    @Test
    @DisplayName("DELETE de pacote com sessão → 409 package_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "foto@test.dev", "fotografia");
        String t = mintValidToken("foto@test.dev", sub);

        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into fotografia_professionals (id, company_id, name) values (?, ?, 'Carla')", prof, companyId);
        UUID pkg = UUID.randomUUID();
        jdbcTemplate.update("insert into fotografia_packages (id, company_id, name, duration_minutes, price_cents, delivery_days) "
            + "values (?, ?, 'Ensaio 1h', 60, 50000, 7)", pkg, companyId);
        UUID apptId = UUID.randomUUID();
        java.sql.Timestamp start = java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T14:00:00Z"));
        jdbcTemplate.update(
            "insert into fotografia_session_appointments (id, company_id, professional_id, package_id, "
                + "customer_name, professional_name, package_name, price_cents, duration_minutes, delivery_days, "
                + "start_at, end_at, delivery_due_date, status) "
                + "values (?, ?, ?, ?, 'Marina', 'Carla', 'Ensaio 1h', 50000, 60, 7, ?, ?, ?, 'agendada')",
            apptId, companyId, prof, pkg, start,
            java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T15:00:00Z")),
            java.sql.Date.valueOf("2026-07-08"));

        mockMvc.perform(delete("/api/fotografia/packages/" + pkg).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("package_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-fotografia (pet) → /api/fotografia/packages → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/fotografia/packages").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
