package com.meada.whatsapp.profiles.dental.patients;

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
 * Testa os endpoints de pacientes (camada 7.4): CRUD + profile guard 403 pra tenant não-dental.
 */
class DentalPatientControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria → GET lista/busca → PATCH edita → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);

        mockMvc.perform(post("/api/dental/patients").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Maria Souza\",\"phone\":\"+5511988887777\",\"document\":\"12345678901\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Maria Souza"));

        UUID id = jdbcTemplate.queryForObject("select id from dental_patients where name = 'Maria Souza'", UUID.class);

        mockMvc.perform(get("/api/dental/patients?search=Maria").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/dental/patients/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"email\":\"maria@email.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("maria@email.com"));

        mockMvc.perform(delete("/api/dental/patients/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 patient_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        mockMvc.perform(get("/api/dental/patients/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("patient_not_found"));
    }

    @Test
    @DisplayName("tenant NÃO-dental (sushi) batendo no /api/dental/patients → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        mockMvc.perform(get("/api/dental/patients").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
