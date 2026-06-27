package com.meada.profiles.salon.appointments;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de agendamentos (camada 7.5): list+filtro por profissional, POST 409 conflict
 * (mesmo profissional), 400 inactive_professional, PATCH status.
 */
class SalonAppointmentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedProfessional(UUID companyId, String name, boolean active) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into salon_professionals (id, company_id, name, active) values (?, ?, ?, ?)",
            id, companyId, name, active);
        return id;
    }

    private UUID seedOffering(UUID companyId, String name, int duration) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into salon_offerings (id, company_id, name, duration_minutes) values (?, ?, ?, ?)",
            id, companyId, name, duration);
        return id;
    }

    @Test
    @DisplayName("POST cria → 201 agendado; GET filtro por profissional mostra 1")
    void createAndFilter() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "salon@test.dev", "salon");
        String t = mintValidToken("salon@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla", true);
        UUID offering = seedOffering(companyId, "Corte", 45);

        // 2026-07-01T12:00-03:00 BRT → 15:00 UTC; dentro de 09–20 BRT.
        mockMvc.perform(post("/api/salon/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"serviceId\":\"" + offering
                    + "\",\"guestName\":\"Joana\",\"startAt\":\"2026-07-01T15:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("agendado"))
            .andExpect(jsonPath("$.professionalName").value("Carla"));

        mockMvc.perform(get("/api/salon/appointments?professionalId=" + prof).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("POST mesmo profissional + mesmo horário → 409 conflict_slot (com detalhes)")
    void conflictSameProfessional() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "salon@test.dev", "salon");
        String t = mintValidToken("salon@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla", true);
        UUID offering = seedOffering(companyId, "Corte", 45);

        mockMvc.perform(post("/api/salon/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"serviceId\":\"" + offering
                    + "\",\"guestName\":\"Joana\",\"startAt\":\"2026-07-01T15:00:00Z\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/salon/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"serviceId\":\"" + offering
                    + "\",\"guestName\":\"Maria\",\"startAt\":\"2026-07-01T15:15:00Z\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_slot"))
            .andExpect(jsonPath("$.conflict.guestName").value("Joana"));
    }

    @Test
    @DisplayName("POST com profissional inativo → 400 inactive_professional")
    void inactiveProfessional() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "salon@test.dev", "salon");
        String t = mintValidToken("salon@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Inativa", false);
        UUID offering = seedOffering(companyId, "Corte", 45);

        mockMvc.perform(post("/api/salon/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"serviceId\":\"" + offering
                    + "\",\"guestName\":\"Joana\",\"startAt\":\"2026-07-01T15:00:00Z\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("inactive_professional"));
    }

    @Test
    @DisplayName("PATCH status agendado→confirmado → 200; transição inválida → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "salon@test.dev", "salon");
        String t = mintValidToken("salon@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla", true);
        UUID offering = seedOffering(companyId, "Corte", 45);

        UUID apptId = UUID.randomUUID();
        java.sql.Timestamp start = java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T15:00:00Z"));
        jdbcTemplate.update(
            "insert into salon_appointments (id, company_id, professional_id, service_id, guest_name, start_at, "
                + "duration_minutes, end_at, service_name, professional_name) "
                + "values (?, ?, ?, ?, 'Joana', ?, 45, ?, 'Corte', 'Carla')",
            apptId, companyId, prof, offering, start,
            java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T15:45:00Z")));

        mockMvc.perform(patch("/api/salon/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"confirmado\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("confirmado"));

        // confirmado → agendado é inválida.
        mockMvc.perform(patch("/api/salon/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"agendado\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }
}
