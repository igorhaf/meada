package com.meada.whatsapp.profiles.concessionaria.salespeople;

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
 * Testa os endpoints de vendedores (camada 8.17): CRUD + toggle + delete-in-use 409 + profile guard 403.
 */
class ConcessionariaSalespersonControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
        seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);

        mockMvc.perform(post("/api/concessionaria/salespeople").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Carlos\",\"phone\":\"+5511999990000\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Carlos"))
            .andExpect(jsonPath("$.active").value(true));

        UUID id = jdbcTemplate.queryForObject(
            "select id from concessionaria_salespeople where name = 'Carlos'", UUID.class);

        mockMvc.perform(get("/api/concessionaria/salespeople").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/concessionaria/salespeople/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Carlos Souza\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Carlos Souza"));

        mockMvc.perform(patch("/api/concessionaria/salespeople/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/concessionaria/salespeople/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("delete de vendedor com test-drive → 409 salesperson_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);

        UUID sp = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_salespeople (id, company_id, name) values (?, ?, 'João')",
            sp, companyId);
        UUID vehicle = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, price_cents) "
            + "values (?, ?, 'Toyota', 'Corolla', 9000000)", vehicle, companyId);
        jdbcTemplate.update(
            "insert into concessionaria_test_drives (company_id, vehicle_id, salesperson_id, vehicle_brand, "
                + "vehicle_model, start_at, duration_minutes, end_at) "
                + "values (?, ?, ?, 'Toyota', 'Corolla', timestamptz '2026-07-01T18:00:00Z', 45, "
                + "timestamptz '2026-07-01T18:45:00Z')",
            companyId, vehicle, sp);

        mockMvc.perform(delete("/api/concessionaria/salespeople/" + sp).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("salesperson_in_use"));
    }

    @Test
    @DisplayName("detalhe inexistente → 404 salesperson_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);
        mockMvc.perform(get("/api/concessionaria/salespeople/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("salesperson_not_found"));
    }

    @Test
    @DisplayName("tenant NÃO-concessionaria (pet) → /api/concessionaria/salespeople → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/concessionaria/salespeople").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
