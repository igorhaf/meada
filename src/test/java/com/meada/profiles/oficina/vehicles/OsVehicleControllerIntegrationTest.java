package com.meada.profiles.oficina.vehicles;

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
 * Testa os endpoints de veículos (camada 7.9): POST cria, GET com filtro de contato, PATCH edita,
 * PATCH /archive, DELETE, placa duplicada → 409 plate_taken, DELETE em uso → 409 vehicle_in_use,
 * profile guard 403.
 */
class OsVehicleControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
    @DisplayName("POST cria → GET filtro por contato mostra 1 → PATCH edita → archive → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990181", "Cliente");

        mockMvc.perform(post("/api/oficina/vehicles").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"contactId\":\"" + contactId + "\",\"plate\":\"ABC1D23\",\"brand\":\"Fiat\",\"model\":\"Uno\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.plate").value("ABC1D23"))
            .andExpect(jsonPath("$.brand").value("Fiat"));

        UUID id = jdbcTemplate.queryForObject("select id from os_vehicles where plate = 'ABC1D23'", UUID.class);

        mockMvc.perform(get("/api/oficina/vehicles?contactId=" + contactId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/oficina/vehicles/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"model\":\"Palio\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.model").value("Palio"));

        mockMvc.perform(patch("/api/oficina/vehicles/" + id + "/archive").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/oficina/vehicles/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST placa duplicada → 409 plate_taken")
    void duplicatePlate() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990182", "Cliente");

        mockMvc.perform(post("/api/oficina/vehicles").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"contactId\":\"" + contactId + "\",\"plate\":\"DUP1A11\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/oficina/vehicles").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"contactId\":\"" + contactId + "\",\"plate\":\"DUP1A11\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("plate_taken"));
    }

    @Test
    @DisplayName("DELETE de veículo com OS → 409 vehicle_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990183", "Cliente");

        UUID vehicleId = UUID.randomUUID();
        jdbcTemplate.update("insert into os_vehicles (id, company_id, contact_id, plate) values (?, ?, ?, 'USE1B22')",
            vehicleId, companyId, contactId);
        jdbcTemplate.update(
            "insert into service_orders (company_id, contact_id, vehicle_id, customer_name, "
                + "vehicle_plate, complaint, status) "
                + "values (?, ?, ?, 'Cliente', 'USE1B22', 'Barulho no motor', 'aberta')",
            companyId, contactId, vehicleId);

        mockMvc.perform(delete("/api/oficina/vehicles/" + vehicleId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("vehicle_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-oficina (pet) → /api/oficina/vehicles → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/oficina/vehicles").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
