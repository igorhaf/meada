package com.meada.profiles.nutri.patients;

import com.meada.admin.AbstractAdminIntegrationTest;
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
 * Testa os endpoints de pacientes (camada 8.0): POST cria, GET com filtro de contactId, PATCH edita,
 * PATCH /archive, DELETE, profile guard 403, DELETE em uso → 409 patient_in_use.
 */
class NutriPatientControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedContact(UUID companyId, String phone, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            id, companyId, phone, name);
        return id;
    }

    @Test
    @DisplayName("POST cria → GET filtro por contactId mostra 1 → PATCH edita → archive → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "nutri@test.dev", "nutri");
        String t = mintValidToken("nutri@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990081", "Marina");

        mockMvc.perform(post("/api/nutri/patients").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"contactId\":\"" + contactId + "\",\"name\":\"Marina\",\"goal\":\"Emagrecimento\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Marina"))
            .andExpect(jsonPath("$.goal").value("Emagrecimento"));

        UUID id = jdbcTemplate.queryForObject("select id from nutri_patients where name = 'Marina'", UUID.class);

        mockMvc.perform(get("/api/nutri/patients?contactId=" + contactId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/nutri/patients/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"goal\":\"Manutenção\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.goal").value("Manutenção"));

        mockMvc.perform(patch("/api/nutri/patients/" + id + "/archive").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/nutri/patients/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE de paciente com consulta → 409 patient_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "nutri@test.dev", "nutri");
        String t = mintValidToken("nutri@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990082", "Marina");

        UUID patientId = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Marina')",
            patientId, companyId, contactId);
        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_professionals (id, company_id, name) values (?, ?, 'Carla')", prof, companyId);
        Instant start = Instant.parse("2026-07-01T14:00:00Z");
        jdbcTemplate.update(
            "insert into nutri_appointments (company_id, professional_id, patient_id, contact_id, patient_name, "
                + "professional_name, appointment_type, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, 'Marina', 'Carla', 'primeira', 60, ?, ?, 'agendado')",
            companyId, prof, patientId, contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(3600)));

        mockMvc.perform(delete("/api/nutri/patients/" + patientId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("patient_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-nutri (pet) → /api/nutri/patients → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/nutri/patients").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
