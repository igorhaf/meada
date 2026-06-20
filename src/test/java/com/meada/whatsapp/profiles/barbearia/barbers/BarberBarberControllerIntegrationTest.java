package com.meada.whatsapp.profiles.barbearia.barbers;

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
 * Testa os endpoints de barbeiros (camada 8.1): CRUD + toggle + delete-em-uso 409 + profile guard 403.
 */
class BarberBarberControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
        seedTenant(sub, "barber@test.dev", "barbearia");
        String t = mintValidToken("barber@test.dev", sub);

        mockMvc.perform(post("/api/barbearia/barbers").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Marcelo\",\"specialty\":\"corte/barba\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Marcelo"))
            .andExpect(jsonPath("$.active").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from barber_barbers where name = 'Marcelo'", UUID.class);

        mockMvc.perform(get("/api/barbearia/barbers").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/barbearia/barbers/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"specialty\":\"degradê\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.specialty").value("degradê"));

        mockMvc.perform(patch("/api/barbearia/barbers/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/barbearia/barbers/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("delete de barbeiro com agendamento → 409 barber_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "barber@test.dev", "barbearia");
        String t = mintValidToken("barber@test.dev", sub);
        UUID barber = UUID.randomUUID();
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'João')", barber, companyId);
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 30)", svc, companyId);
        jdbcTemplate.update(
            "insert into barber_appointments (company_id, barber_id, service_id, guest_name, start_at, "
                + "duration_minutes, end_at, service_name, barber_name) "
                + "values (?, ?, ?, 'Cli', now(), 30, now() + interval '30 min', 'Corte', 'João')",
            companyId, barber, svc);
        mockMvc.perform(delete("/api/barbearia/barbers/" + barber).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("barber_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-barbearia (nutri) → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "nutri@test.dev", "nutri");
        String t = mintValidToken("nutri@test.dev", sub);
        mockMvc.perform(get("/api/barbearia/barbers").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
