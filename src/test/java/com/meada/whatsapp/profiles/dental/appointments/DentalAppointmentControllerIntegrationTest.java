package com.meada.whatsapp.profiles.dental.appointments;

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
 * Testa os endpoints de consultas (camada 7.4): lista, POST manual com 409 conflict_slot, 400
 * outside_hours, 404 patient_not_found, PATCH status.
 */
class DentalAppointmentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedPatient(UUID companyId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into dental_patients (id, company_id, name) values (?, ?, ?)",
            id, companyId, name);
        return id;
    }

    @Test
    @DisplayName("POST cria consulta em slot livre → 201 agendada; GET lista mostra 1")
    void createAndList() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        UUID patient = seedPatient(companyId, "Maria Souza");

        // 2026-07-01T15:00-03:00 BRT → 18:00 UTC; dentro de 08–18 BRT.
        mockMvc.perform(post("/api/dental/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"patientId\":\"" + patient + "\",\"type\":\"Limpeza\","
                    + "\"startAt\":\"2026-07-01T18:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("agendada"))
            .andExpect(jsonPath("$.patientName").value("Maria Souza"));

        mockMvc.perform(get("/api/dental/appointments").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("POST no mesmo slot (mesmo company) → 409 conflict_slot (com detalhes)")
    void conflictSlot() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        UUID p1 = seedPatient(companyId, "Maria Souza");
        UUID p2 = seedPatient(companyId, "João Lima");

        mockMvc.perform(post("/api/dental/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"patientId\":\"" + p1 + "\",\"type\":\"Limpeza\","
                    + "\"startAt\":\"2026-07-01T18:00:00Z\"}"))
            .andExpect(status().isCreated());

        // mesmo horário, outro paciente → conflito (1 dentista por tenant).
        mockMvc.perform(post("/api/dental/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"patientId\":\"" + p2 + "\",\"type\":\"Canal\","
                    + "\"startAt\":\"2026-07-01T18:00:00Z\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_slot"))
            .andExpect(jsonPath("$.conflict.patientName").value("Maria Souza"));
    }

    @Test
    @DisplayName("POST fora do horário (06:00 BRT) → 400 outside_hours; patient inexistente → 404")
    void outsideHoursAndNoPatient() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        UUID patient = seedPatient(companyId, "Maria Souza");

        // 06:00 BRT = 09:00 UTC; antes de 08:00.
        mockMvc.perform(post("/api/dental/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"patientId\":\"" + patient + "\",\"type\":\"Limpeza\","
                    + "\"startAt\":\"2026-07-01T09:00:00Z\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("outside_hours"));

        mockMvc.perform(post("/api/dental/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"patientId\":\"" + UUID.randomUUID() + "\",\"type\":\"Limpeza\","
                    + "\"startAt\":\"2026-07-01T18:00:00Z\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("patient_not_found"));
    }

    @Test
    @DisplayName("PATCH status agendada→confirmada → 200; transição inválida → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        UUID patient = seedPatient(companyId, "Maria Souza");

        UUID apptId = UUID.randomUUID();
        java.sql.Timestamp start = java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T18:00:00Z"));
        jdbcTemplate.update(
            "insert into dental_appointments (id, company_id, patient_id, start_at, duration_minutes, end_at, type) "
                + "values (?, ?, ?, ?, 30, ?, 'Limpeza')",
            apptId, companyId, patient, start,
            java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T18:30:00Z")));

        mockMvc.perform(patch("/api/dental/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"confirmada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("confirmada"));

        // confirmada → agendada é inválida.
        mockMvc.perform(patch("/api/dental/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"agendada\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }
}
