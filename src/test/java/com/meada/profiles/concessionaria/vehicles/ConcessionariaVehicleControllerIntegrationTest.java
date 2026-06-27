package com.meada.profiles.concessionaria.vehicles;

import com.meada.admin.AbstractAdminIntegrationTest;
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
 * Testa os endpoints de veículos (camada 8.17): CRUD, PATCH status (ciclo de estoque), 409
 * invalid_status_transition, vitrine (?available), 409 vehicle_in_use, 403 forbidden_wrong_profile.
 */
class ConcessionariaVehicleControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria → GET lista → PATCH edita → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);

        mockMvc.perform(post("/api/concessionaria/vehicles").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"brand\":\"Toyota\",\"model\":\"Corolla\",\"priceCents\":9000000}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("disponivel"))
            .andExpect(jsonPath("$.brand").value("Toyota"));

        UUID id = jdbcTemplate.queryForObject(
            "select id from concessionaria_vehicles where model = 'Corolla'", UUID.class);

        mockMvc.perform(get("/api/concessionaria/vehicles").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/concessionaria/vehicles/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"color\":\"Preto\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.color").value("Preto"));

        mockMvc.perform(delete("/api/concessionaria/vehicles/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH status disponivel→vendido → 200; vendido→disponivel → 409 invalid_status_transition")
    void patchStatusCycle() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);

        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, price_cents) "
            + "values (?, ?, 'Toyota', 'Corolla', 9000000)", id, companyId);

        mockMvc.perform(patch("/api/concessionaria/vehicles/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"vendido\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("vendido"));

        mockMvc.perform(patch("/api/concessionaria/vehicles/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"disponivel\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("GET ?available=true (vitrine) só mostra veículo disponível — vendido some")
    void vitrine() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);

        UUID disp = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, price_cents) "
            + "values (?, ?, 'Toyota', 'Corolla', 9000000)", disp, companyId);
        UUID sold = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, price_cents, status) "
            + "values (?, ?, 'Toyota', 'Yaris', 7000000, 'vendido')", sold, companyId);

        mockMvc.perform(get("/api/concessionaria/vehicles?available=true").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(disp.toString()));
    }

    @Test
    @DisplayName("delete de veículo com lead → 409 vehicle_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "conc@test.dev", "concessionaria");
        String t = mintValidToken("conc@test.dev", sub);

        UUID v = UUID.randomUUID();
        jdbcTemplate.update("insert into concessionaria_vehicles (id, company_id, brand, model, price_cents) "
            + "values (?, ?, 'Toyota', 'Corolla', 9000000)", v, companyId);
        jdbcTemplate.update(
            "insert into concessionaria_leads (company_id, vehicle_id, vehicle_brand, vehicle_model, "
                + "vehicle_price_cents) values (?, ?, 'Toyota', 'Corolla', 9000000)",
            companyId, v);

        mockMvc.perform(delete("/api/concessionaria/vehicles/" + v).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("vehicle_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-concessionaria (pet) → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/concessionaria/vehicles").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
