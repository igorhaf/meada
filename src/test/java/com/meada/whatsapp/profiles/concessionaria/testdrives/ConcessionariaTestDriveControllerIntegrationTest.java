package com.meada.whatsapp.profiles.concessionaria.testdrives;

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
 * Testa os endpoints de test-drive (camada 8.17): POST manual (201 + conflito 409 conflict_slot +
 * 422 vehicle_not_available), GET lista/detalhe, PATCH status, 403 forbidden_wrong_profile.
 */
class ConcessionariaTestDriveControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedVehicle(UUID companyId, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, model_year, "
            + "price_cents, status) values (?, ?, 'Toyota', 'Corolla', 2024, 9000000, ?)", id, companyId, status);
        return id;
    }

    private UUID seedSalesperson(UUID companyId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_salespeople (id, company_id, name) values (?, ?, ?)",
            id, companyId, name);
        return id;
    }

    @Test
    @DisplayName("POST cria test-drive em slot livre → 201 agendado; GET lista mostra 1")
    void createAndList() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);
        UUID vehicle = seedVehicle(companyId, "disponivel");
        UUID sp = seedSalesperson(companyId, "Carlos");

        mockMvc.perform(post("/api/concessionaria/testdrives").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"vehicleId\":\"" + vehicle + "\",\"salespersonId\":\"" + sp + "\","
                    + "\"startAt\":\"2026-07-01T18:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("agendado"))
            .andExpect(jsonPath("$.vehicleBrand").value("Toyota"));

        mockMvc.perform(get("/api/concessionaria/testdrives").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("POST no mesmo slot/mesmo vendedor → 409 conflict_slot (com detalhes)")
    void conflictSlot() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);
        UUID vehicle = seedVehicle(companyId, "disponivel");
        UUID sp = seedSalesperson(companyId, "Carlos");

        mockMvc.perform(post("/api/concessionaria/testdrives").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"vehicleId\":\"" + vehicle + "\",\"salespersonId\":\"" + sp + "\","
                    + "\"startAt\":\"2026-07-01T18:00:00Z\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/concessionaria/testdrives").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"vehicleId\":\"" + vehicle + "\",\"salespersonId\":\"" + sp + "\","
                    + "\"startAt\":\"2026-07-01T18:00:00Z\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_slot"))
            .andExpect(jsonPath("$.conflict.testDriveId").exists());
    }

    @Test
    @DisplayName("POST de veículo NÃO-disponível (vendido) → 422 vehicle_not_available")
    void vehicleNotAvailable() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);
        UUID vehicle = seedVehicle(companyId, "vendido");
        UUID sp = seedSalesperson(companyId, "Carlos");

        mockMvc.perform(post("/api/concessionaria/testdrives").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"vehicleId\":\"" + vehicle + "\",\"salespersonId\":\"" + sp + "\","
                    + "\"startAt\":\"2026-07-01T18:00:00Z\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reason").value("vehicle_not_available"));
    }

    @Test
    @DisplayName("PATCH status agendado→confirmado → 200; transição inválida → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);
        UUID vehicle = seedVehicle(companyId, "disponivel");
        UUID sp = seedSalesperson(companyId, "Carlos");

        UUID tdId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into concessionaria_test_drives (id, company_id, vehicle_id, salesperson_id, vehicle_brand, "
                + "vehicle_model, vehicle_year, start_at, duration_minutes, end_at) "
                + "values (?, ?, ?, ?, 'Toyota', 'Corolla', 2024, timestamptz '2026-07-01T18:00:00Z', 45, "
                + "timestamptz '2026-07-01T18:45:00Z')",
            tdId, companyId, vehicle, sp);

        mockMvc.perform(patch("/api/concessionaria/testdrives/" + tdId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"confirmado\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("confirmado"));

        // confirmado → agendado é inválida.
        mockMvc.perform(patch("/api/concessionaria/testdrives/" + tdId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"agendado\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("detalhe inexistente → 404 testdrive_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);
        mockMvc.perform(get("/api/concessionaria/testdrives/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("testdrive_not_found"));
    }

    @Test
    @DisplayName("tenant NÃO-concessionaria (pet) → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/concessionaria/testdrives").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
