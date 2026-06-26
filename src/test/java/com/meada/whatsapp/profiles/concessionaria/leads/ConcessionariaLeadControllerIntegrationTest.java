package com.meada.whatsapp.profiles.concessionaria.leads;

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
 * Testa os endpoints de lead (camada 8.17): POST manual (201 + 422 vehicle_not_available), GET
 * lista/detalhe, PATCH status, PATCH assign, 403 forbidden_wrong_profile.
 */
class ConcessionariaLeadControllerIntegrationTest extends AbstractAdminIntegrationTest {

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

    @Test
    @DisplayName("POST cria lead → 201 novo; GET lista mostra 1")
    void createAndList() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);
        UUID vehicle = seedVehicle(companyId, "disponivel");

        mockMvc.perform(post("/api/concessionaria/leads").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"vehicleId\":\"" + vehicle + "\",\"paymentCondition\":\"financiado\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("novo"))
            .andExpect(jsonPath("$.vehiclePriceCents").value(9000000))
            .andExpect(jsonPath("$.paymentCondition").value("financiado"));

        mockMvc.perform(get("/api/concessionaria/leads").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("POST de veículo NÃO-disponível (vendido) → 422 vehicle_not_available")
    void vehicleNotAvailable() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);
        UUID vehicle = seedVehicle(companyId, "vendido");

        mockMvc.perform(post("/api/concessionaria/leads").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"vehicleId\":\"" + vehicle + "\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reason").value("vehicle_not_available"));
    }

    @Test
    @DisplayName("PATCH status novo→em_negociacao → 200; transição inválida (novo→fechado) → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);
        UUID vehicle = seedVehicle(companyId, "disponivel");

        UUID leadId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into concessionaria_leads (id, company_id, vehicle_id, vehicle_brand, vehicle_model, "
                + "vehicle_price_cents) values (?, ?, ?, 'Toyota', 'Corolla', 9000000)",
            leadId, companyId, vehicle);

        mockMvc.perform(patch("/api/concessionaria/leads/" + leadId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"em_negociacao\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("em_negociacao"));

        mockMvc.perform(patch("/api/concessionaria/leads/" + leadId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"novo\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("PATCH assign atribui o vendedor → 200")
    void patchAssign() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);
        UUID vehicle = seedVehicle(companyId, "disponivel");

        UUID leadId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into concessionaria_leads (id, company_id, vehicle_id, vehicle_brand, vehicle_model, "
                + "vehicle_price_cents) values (?, ?, ?, 'Toyota', 'Corolla', 9000000)",
            leadId, companyId, vehicle);
        UUID sp = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_salespeople (id, company_id, name) values (?, ?, 'Carlos')",
            sp, companyId);

        mockMvc.perform(patch("/api/concessionaria/leads/" + leadId + "/assign").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"salespersonId\":\"" + sp + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.salespersonId").value(sp.toString()));
    }

    @Test
    @DisplayName("detalhe inexistente → 404 lead_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);
        mockMvc.perform(get("/api/concessionaria/leads/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("lead_not_found"));
    }

    @Test
    @DisplayName("tenant NÃO-concessionaria (pet) → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/concessionaria/leads").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
