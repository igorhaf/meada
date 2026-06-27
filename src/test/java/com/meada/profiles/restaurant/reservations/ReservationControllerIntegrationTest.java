package com.meada.profiles.restaurant.reservations;

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
 * Testa os endpoints de reservas (camada 7.3): lista, POST manual com 409 conflict_slot e 400
 * outside_hours, PATCH de status. O bloco C da SM (conflito) é coberto aqui.
 */
class ReservationControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedTable(UUID companyId, String label) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into restaurant_tables (id, company_id, label, capacity) values (?, ?, ?, 4)",
            id, companyId, label);
        return id;
    }

    @Test
    @DisplayName("POST cria reserva em slot livre → 201 pendente; GET lista mostra 1")
    void createAndList() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "rest@test.dev", "restaurant");
        String t = mintValidToken("rest@test.dev", sub);
        UUID table = seedTable(companyId, "Mesa 1");

        // 2026-07-01T20:00-03:00 BRT → 23:00 UTC; dentro da janela 11–23 BRT.
        mockMvc.perform(post("/api/restaurant/reservations").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"tableId\":\"" + table + "\",\"guestName\":\"Maria\","
                    + "\"startAt\":\"2026-07-01T23:00:00Z\",\"numPeople\":4}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("pendente"))
            .andExpect(jsonPath("$.guestName").value("Maria"));

        mockMvc.perform(get("/api/restaurant/reservations").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("POST no mesmo slot+mesa → 409 conflict_slot (com detalhes do conflito)")
    void conflictSlot() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "rest@test.dev", "restaurant");
        String t = mintValidToken("rest@test.dev", sub);
        UUID table = seedTable(companyId, "Mesa 1");

        mockMvc.perform(post("/api/restaurant/reservations").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"tableId\":\"" + table + "\",\"guestName\":\"Maria\","
                    + "\"startAt\":\"2026-07-01T23:00:00Z\",\"numPeople\":2}"))
            .andExpect(status().isCreated());

        // 20:30 BRT (23:30 UTC) sobrepõe a reserva 20:00–22:00 na mesma mesa.
        mockMvc.perform(post("/api/restaurant/reservations").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"tableId\":\"" + table + "\",\"guestName\":\"João\","
                    + "\"startAt\":\"2026-07-01T23:30:00Z\",\"numPeople\":2}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_slot"))
            .andExpect(jsonPath("$.conflict.guestName").value("Maria"));
    }

    @Test
    @DisplayName("POST fora do horário (08:00 BRT) → 400 outside_hours")
    void outsideHours() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "rest@test.dev", "restaurant");
        String t = mintValidToken("rest@test.dev", sub);
        UUID table = seedTable(companyId, "Mesa 1");

        // 2026-07-01T08:00-03:00 → 11:00 UTC; antes de opens_at 11:00 BRT.
        mockMvc.perform(post("/api/restaurant/reservations").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"tableId\":\"" + table + "\",\"guestName\":\"Maria\","
                    + "\"startAt\":\"2026-07-01T11:00:00Z\",\"numPeople\":2}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("outside_hours"));
    }

    @Test
    @DisplayName("PATCH status pendente→confirmada → 200; transição inválida → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "rest@test.dev", "restaurant");
        String t = mintValidToken("rest@test.dev", sub);
        UUID table = seedTable(companyId, "Mesa 1");

        UUID resId = UUID.randomUUID();
        java.sql.Timestamp start = java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T23:00:00Z"));
        jdbcTemplate.update(
            "insert into table_reservations (id, company_id, table_id, guest_name, start_at, duration_minutes, end_at, num_people) "
                + "values (?, ?, ?, 'Maria', ?, 120, ?, 2)",
            resId, companyId, table, start,
            java.sql.Timestamp.from(java.time.Instant.parse("2026-07-02T01:00:00Z")));

        mockMvc.perform(patch("/api/restaurant/reservations/" + resId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"confirmada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("confirmada"));

        // confirmada → pendente é inválida.
        mockMvc.perform(patch("/api/restaurant/reservations/" + resId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"pendente\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }
}
