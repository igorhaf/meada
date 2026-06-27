package com.meada.profiles.fotografia.appointments;

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
 * Testa os endpoints de sessões (camada 8.16): POST cria manual (snapshots pacote), 409 conflict_slot
 * (mesmo profissional, com detalhes), PATCH status (válido + inválido → 409), PATCH sessão (grava
 * delivery_link), profile guard 403. Clone do DermatologiaAppointmentControllerIntegrationTest.
 */
class FotografiaAppointmentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedProfessional(UUID companyId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into fotografia_professionals (id, company_id, name) values (?, ?, ?)", id, companyId, name);
        return id;
    }

    private UUID seedPackage(UUID companyId, String name, int dur, int price, int deliveryDays) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into fotografia_packages (id, company_id, name, duration_minutes, price_cents, delivery_days) "
            + "values (?, ?, ?, ?, ?, ?)", id, companyId, name, dur, price, deliveryDays);
        return id;
    }

    @Test
    @DisplayName("POST cria → 201 agendada (snapshot pacote+preço+duração); GET filtro por profissional mostra 1")
    void createAndFilter() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "foto@test.dev", "fotografia");
        String t = mintValidToken("foto@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla");
        UUID pkg = seedPackage(companyId, "Ensaio 1h", 60, 50000, 7);

        mockMvc.perform(post("/api/fotografia/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"packageId\":\"" + pkg
                    + "\",\"startAt\":\"2026-07-01T14:00:00Z\",\"customerName\":\"Marina\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("agendada"))
            .andExpect(jsonPath("$.professionalName").value("Carla"))
            .andExpect(jsonPath("$.customerName").value("Marina"))
            .andExpect(jsonPath("$.packageName").value("Ensaio 1h"))
            .andExpect(jsonPath("$.priceCents").value(50000))
            .andExpect(jsonPath("$.durationMinutes").value(60));

        mockMvc.perform(get("/api/fotografia/appointments?professionalId=" + prof).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("POST mesmo profissional + horário sobreposto → 409 conflict_slot (com detalhes)")
    void conflictSameProfessional() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "foto@test.dev", "fotografia");
        String t = mintValidToken("foto@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla");
        UUID pkg = seedPackage(companyId, "Ensaio 1h", 60, 50000, 7);

        mockMvc.perform(post("/api/fotografia/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"packageId\":\"" + pkg
                    + "\",\"startAt\":\"2026-07-01T14:00:00Z\",\"customerName\":\"Marina\"}"))
            .andExpect(status().isCreated());

        // 11:30 BRT (14:30 UTC) cai dentro da janela 11:00–12:00 da primeira.
        mockMvc.perform(post("/api/fotografia/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"packageId\":\"" + pkg
                    + "\",\"startAt\":\"2026-07-01T14:30:00Z\",\"customerName\":\"Bruno\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_slot"))
            .andExpect(jsonPath("$.conflict.customerName").value("Marina"));
    }

    @Test
    @DisplayName("PATCH status agendada→confirmada → 200; transição inválida → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "foto@test.dev", "fotografia");
        String t = mintValidToken("foto@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla");
        UUID pkg = seedPackage(companyId, "Ensaio 1h", 60, 50000, 7);

        UUID apptId = seedSessionRow(companyId, prof, pkg, "Marina");

        mockMvc.perform(patch("/api/fotografia/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"confirmada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("confirmada"));

        // confirmada → agendada é inválida.
        mockMvc.perform(patch("/api/fotografia/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"agendada\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("PATCH sessão grava o delivery_link → 200 (link ecoado)")
    void patchSessionDeliveryLink() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "foto@test.dev", "fotografia");
        String t = mintValidToken("foto@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla");
        UUID pkg = seedPackage(companyId, "Ensaio 1h", 60, 50000, 7);
        UUID apptId = seedSessionRow(companyId, prof, pkg, "Marina");

        mockMvc.perform(patch("/api/fotografia/appointments/" + apptId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"deliveryLink\":\"https://galeria.studio/marina\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryLink").value("https://galeria.studio/marina"));
    }

    @Test
    @DisplayName("tenant NÃO-fotografia (pet) → /api/fotografia/appointments → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/fotografia/appointments").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }

    private UUID seedSessionRow(UUID companyId, UUID prof, UUID pkg, String customerName) {
        UUID apptId = UUID.randomUUID();
        java.sql.Timestamp start = java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T14:00:00Z"));
        jdbcTemplate.update(
            "insert into fotografia_session_appointments (id, company_id, professional_id, package_id, "
                + "customer_name, professional_name, package_name, price_cents, duration_minutes, delivery_days, "
                + "start_at, end_at, delivery_due_date, status) "
                + "values (?, ?, ?, ?, ?, 'Carla', 'Ensaio 1h', 50000, 60, 7, ?, ?, ?, 'agendada')",
            apptId, companyId, prof, pkg, customerName, start,
            java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T15:00:00Z")),
            java.sql.Date.valueOf("2026-07-08"));
        return apptId;
    }
}
