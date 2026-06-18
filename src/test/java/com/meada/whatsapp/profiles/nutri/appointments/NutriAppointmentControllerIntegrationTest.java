package com.meada.whatsapp.profiles.nutri.appointments;

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
 * Testa os endpoints de consultas (camada 8.0): POST cria manual, 409 conflict_slot (mesmo
 * profissional, com detalhes), PATCH status (válido + inválido → 409), profile guard 403,
 * invalid_type 400.
 */
class NutriAppointmentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedProfessional(UUID companyId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_professionals (id, company_id, name) values (?, ?, ?)", id, companyId, name);
        return id;
    }

    private UUID seedContact(UUID companyId, String phone, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            id, companyId, phone, name);
        return id;
    }

    private UUID seedPatient(UUID companyId, UUID contactId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_patients (id, company_id, contact_id, name) values (?, ?, ?, ?)",
            id, companyId, contactId, name);
        return id;
    }

    @Test
    @DisplayName("POST cria → 201 agendado; GET filtro por profissional mostra 1")
    void createAndFilter() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "nutri@test.dev", "nutri");
        String t = mintValidToken("nutri@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla");
        UUID contactId = seedContact(companyId, "+5511999990091", "Marina");
        UUID patient = seedPatient(companyId, contactId, "Marina");

        // 2026-07-01T11:00-03:00 BRT → 14:00 UTC; dentro de 08–18 BRT.
        mockMvc.perform(post("/api/nutri/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"patientId\":\"" + patient
                    + "\",\"appointmentType\":\"primeira\",\"startAt\":\"2026-07-01T14:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("agendado"))
            .andExpect(jsonPath("$.professionalName").value("Carla"))
            .andExpect(jsonPath("$.patientName").value("Marina"));

        mockMvc.perform(get("/api/nutri/appointments?professionalId=" + prof).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("POST mesmo profissional + mesmo horário → 409 conflict_slot (com detalhes)")
    void conflictSameProfessional() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "nutri@test.dev", "nutri");
        String t = mintValidToken("nutri@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla");
        UUID contactId = seedContact(companyId, "+5511999990092", "Marina");
        UUID patient1 = seedPatient(companyId, contactId, "Marina");
        UUID patient2 = seedPatient(companyId, contactId, "Bruno");

        mockMvc.perform(post("/api/nutri/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"patientId\":\"" + patient1
                    + "\",\"appointmentType\":\"primeira\",\"startAt\":\"2026-07-01T14:00:00Z\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/nutri/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"patientId\":\"" + patient2
                    + "\",\"appointmentType\":\"retorno\",\"startAt\":\"2026-07-01T14:30:00Z\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_slot"))
            .andExpect(jsonPath("$.conflict.patientName").value("Marina"));
    }

    @Test
    @DisplayName("POST tipo inválido ('foo') → 400 invalid_type")
    void invalidType() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "nutri@test.dev", "nutri");
        String t = mintValidToken("nutri@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla");
        UUID contactId = seedContact(companyId, "+5511999990093", "Marina");
        UUID patient = seedPatient(companyId, contactId, "Marina");

        mockMvc.perform(post("/api/nutri/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"patientId\":\"" + patient
                    + "\",\"appointmentType\":\"foo\",\"startAt\":\"2026-07-01T14:00:00Z\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_type"));
    }

    @Test
    @DisplayName("PATCH status agendado→confirmado → 200; transição inválida → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "nutri@test.dev", "nutri");
        String t = mintValidToken("nutri@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla");
        UUID contactId = seedContact(companyId, "+5511999990094", "Marina");
        UUID patient = seedPatient(companyId, contactId, "Marina");

        UUID apptId = UUID.randomUUID();
        java.sql.Timestamp start = java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T14:00:00Z"));
        jdbcTemplate.update(
            "insert into nutri_appointments (id, company_id, professional_id, patient_id, contact_id, patient_name, "
                + "professional_name, appointment_type, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, ?, 'Marina', 'Carla', 'primeira', 60, ?, ?, 'agendado')",
            apptId, companyId, prof, patient, contactId, start,
            java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T15:00:00Z")));

        mockMvc.perform(patch("/api/nutri/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"confirmado\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("confirmado"));

        // confirmado → agendado é inválida.
        mockMvc.perform(patch("/api/nutri/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"agendado\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("tenant NÃO-nutri (pet) → /api/nutri/appointments → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/nutri/appointments").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
