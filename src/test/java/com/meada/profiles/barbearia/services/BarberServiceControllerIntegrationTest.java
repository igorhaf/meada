package com.meada.profiles.barbearia.services;

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
 * Testa os endpoints de serviços (camada 8.1): CRUD + toggle + delete-em-uso 409 + profile guard 403.
 */
class BarberServiceControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria → GET lista → toggle → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "barber@test.dev", "barbearia");
        String t = mintValidToken("barber@test.dev", sub);

        mockMvc.perform(post("/api/barbearia/services").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Corte\",\"durationMinutes\":30,\"priceCents\":4000}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Corte"))
            .andExpect(jsonPath("$.durationMinutes").value(30));

        UUID id = jdbcTemplate.queryForObject("select id from barber_services where name = 'Corte'", UUID.class);

        mockMvc.perform(get("/api/barbearia/services").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/barbearia/services/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/barbearia/services/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("delete de serviço com agendamento → 409 service_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "barber@test.dev", "barbearia");
        String t = mintValidToken("barber@test.dev", sub);
        UUID barber = UUID.randomUUID();
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')", barber, companyId);
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 30)", svc, companyId);
        jdbcTemplate.update(
            "insert into barber_appointments (company_id, barber_id, service_id, guest_name, start_at, "
                + "duration_minutes, end_at, service_name, barber_name) "
                + "values (?, ?, ?, 'Cli', now(), 30, now() + interval '30 min', 'Corte', 'Marcelo')",
            companyId, barber, svc);
        mockMvc.perform(delete("/api/barbearia/services/" + svc).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("service_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-barbearia (salon) → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "salon@test.dev", "salon");
        String t = mintValidToken("salon@test.dev", sub);
        mockMvc.perform(get("/api/barbearia/services").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
