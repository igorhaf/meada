package com.meada.profiles.barbearia.appointments;

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
 * Testa os endpoints de agenda (camada 8.1): POST manual, 409 conflict_slot (mesmo barbeiro),
 * paralelismo (barbeiro diferente OK), transição de status, profile guard 403.
 */
class BarberAppointmentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("POST marca → 201; mesmo barbeiro+horário → 409 conflict_slot; barbeiro diferente → 201")
    void postAndConflict() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "barber@test.dev", "barbearia");
        String t = mintValidToken("barber@test.dev", sub);
        UUID marcelo = UUID.randomUUID();
        UUID junior = UUID.randomUUID();
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')", marcelo, companyId);
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Júnior')", junior, companyId);
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 30)", svc, companyId);

        String start = "2026-07-01T15:00:00Z";   // 12:00 BRT
        String body = "{\"barberId\":\"" + marcelo + "\",\"serviceId\":\"" + svc
            + "\",\"guestName\":\"Cli\",\"startAt\":\"" + start + "\"}";
        mockMvc.perform(post("/api/barbearia/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("agendado"));

        // mesmo barbeiro, mesmo horário → 409.
        mockMvc.perform(post("/api/barbearia/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_slot"));

        // barbeiro diferente, mesmo horário → 201 (paralelismo).
        String bodyJunior = "{\"barberId\":\"" + junior + "\",\"serviceId\":\"" + svc
            + "\",\"guestName\":\"Cli2\",\"startAt\":\"" + start + "\"}";
        mockMvc.perform(post("/api/barbearia/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content(bodyJunior))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("transição inválida → 409 invalid_status_transition")
    void invalidTransition() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "barber@test.dev", "barbearia");
        String t = mintValidToken("barber@test.dev", sub);
        UUID marcelo = UUID.randomUUID();
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')", marcelo, companyId);
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 30)", svc, companyId);
        UUID appt = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into barber_appointments (id, company_id, barber_id, service_id, guest_name, start_at, "
                + "duration_minutes, end_at, service_name, barber_name, status) "
                + "values (?, ?, ?, ?, 'Cli', now(), 30, now() + interval '30 min', 'Corte', 'Marcelo', 'realizado')",
            appt, companyId, marcelo, svc);
        // realizado é terminal → não pode ir pra confirmado.
        mockMvc.perform(patch("/api/barbearia/appointments/" + appt + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"confirmado\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("tenant NÃO-barbearia (oficina) → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);
        mockMvc.perform(get("/api/barbearia/appointments").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
