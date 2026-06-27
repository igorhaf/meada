package com.meada.profiles.oficina.orders;

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
 * Testa os endpoints de ordens de serviço (camada 7.9): POST abre, GET lista + detalhe (com itens),
 * POST item recalcula total, PATCH status aberta→orcada, transição inválida → 409, orçar sem item →
 * 400 empty_budget, mutar item em OS travada → 409 order_locked, profile guard 403.
 */
class ServiceOrderControllerIntegrationTest extends AbstractAdminIntegrationTest {

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

    private UUID seedVehicle(UUID companyId, UUID contactId, String plate, String model) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into os_vehicles (id, company_id, contact_id, plate, model) values (?, ?, ?, ?, ?)",
            id, companyId, contactId, plate, model);
        return id;
    }

    @Test
    @DisplayName("POST abre → 201 aberta; GET lista mostra 1; GET detalhe traz itens")
    void openListDetail() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990191", "João");
        UUID vehicle = seedVehicle(companyId, contactId, "ABC1D23", "Uno");

        mockMvc.perform(post("/api/oficina/orders").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"vehicleId\":\"" + vehicle + "\",\"complaint\":\"Barulho no motor\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("aberta"))
            .andExpect(jsonPath("$.vehiclePlate").value("ABC1D23"));

        UUID orderId = jdbcTemplate.queryForObject(
            "select id from service_orders where vehicle_plate = 'ABC1D23'", UUID.class);

        mockMvc.perform(get("/api/oficina/orders").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        // POST item recalcula o total (2 x 5000 = 10000).
        mockMvc.perform(post("/api/oficina/orders/" + orderId + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"kind\":\"peca\",\"description\":\"Filtro\",\"quantity\":2,\"unitPriceCents\":5000}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/oficina/orders/" + orderId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCents").value(10000))
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("PATCH status aberta→orcada (após item) → 200; transição inválida → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990192", "João");
        UUID vehicle = seedVehicle(companyId, contactId, "EFG2H34", "Gol");

        mockMvc.perform(post("/api/oficina/orders").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"vehicleId\":\"" + vehicle + "\",\"complaint\":\"Revisão\"}"))
            .andExpect(status().isCreated());
        UUID orderId = jdbcTemplate.queryForObject(
            "select id from service_orders where vehicle_plate = 'EFG2H34'", UUID.class);

        mockMvc.perform(post("/api/oficina/orders/" + orderId + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"kind\":\"mao_de_obra\",\"description\":\"Mão de obra\",\"quantity\":1,\"unitPriceCents\":12000}"))
            .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/oficina/orders/" + orderId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"orcada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("orcada"));

        // orcada → aberta é inválida.
        mockMvc.perform(patch("/api/oficina/orders/" + orderId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"aberta\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("orçar OS sem item → 400 empty_budget")
    void orcarEmpty() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990193", "João");
        UUID vehicle = seedVehicle(companyId, contactId, "IJK3L56", "Palio");

        mockMvc.perform(post("/api/oficina/orders").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"vehicleId\":\"" + vehicle + "\",\"complaint\":\"Diagnóstico\"}"))
            .andExpect(status().isCreated());
        UUID orderId = jdbcTemplate.queryForObject(
            "select id from service_orders where vehicle_plate = 'IJK3L56'", UUID.class);

        mockMvc.perform(patch("/api/oficina/orders/" + orderId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"orcada\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("empty_budget"));
    }

    @Test
    @DisplayName("mutar item em OS travada (em_execucao) → 409 order_locked")
    void itemOnLockedOrder() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990194", "João");
        UUID vehicle = seedVehicle(companyId, contactId, "MNO4P78", "Onix");

        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into service_orders (id, company_id, contact_id, vehicle_id, customer_name, "
                + "vehicle_plate, vehicle_model, complaint, total_cents, status) "
                + "values (?, ?, ?, ?, 'João', 'MNO4P78', 'Onix', 'Troca de óleo', 5000, 'em_execucao')",
            orderId, companyId, contactId, vehicle);

        mockMvc.perform(post("/api/oficina/orders/" + orderId + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"kind\":\"peca\",\"description\":\"Vela\",\"quantity\":1,\"unitPriceCents\":3000}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("order_locked"));
    }

    @Test
    @DisplayName("tenant NÃO-oficina (pet) → /api/oficina/orders → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/oficina/orders").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
