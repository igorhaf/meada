package com.meada.whatsapp.profiles.otica.professionals;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de optometristas do otica (camada 8.12, FLUXO A): CRUD + toggle, detalhe 404,
 * delete em uso → 409 professional_in_use, profile guard 403.
 */
class OticaProfessionalControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
        seedTenant(sub, "otica@test.dev", "otica");
        String t = mintValidToken("otica@test.dev", sub);

        mockMvc.perform(post("/api/otica/professionals").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Dra. Lia\",\"notes\":\"manhã\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Dra. Lia"))
            .andExpect(jsonPath("$.active").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from otica_professionals where name = 'Dra. Lia'", UUID.class);

        mockMvc.perform(get("/api/otica/professionals").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/otica/professionals/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Dra. Lia M.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Dra. Lia M."));

        mockMvc.perform(patch("/api/otica/professionals/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/otica/professionals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 professional_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "otica@test.dev", "otica");
        String t = mintValidToken("otica@test.dev", sub);
        mockMvc.perform(get("/api/otica/professionals/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("professional_not_found"));
    }

    @Test
    @DisplayName("DELETE de optometrista com exame → 409 professional_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "otica@test.dev", "otica");
        String t = mintValidToken("otica@test.dev", sub);

        UUID profId = UUID.randomUUID();
        jdbcTemplate.update("insert into otica_professionals (id, company_id, name) values (?, ?, 'Dr. Uso')",
            profId, companyId);
        // Um exame referenciando o profissional (FK restrict). INSERT direto (service_role no teste).
        Instant start = Instant.parse("2026-07-01T18:00:00Z");
        jdbcTemplate.update(
            "insert into otica_exam_appointments (company_id, professional_id, customer_name, professional_name, "
                + "start_at, duration_minutes, end_at) values (?, ?, 'Cliente', 'Dr. Uso', ?, 30, ?)",
            companyId, profId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(1800)));

        mockMvc.perform(delete("/api/otica/professionals/" + profId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("professional_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-otica (pet) → /api/otica/professionals → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/otica/professionals").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
