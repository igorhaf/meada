package com.meada.availability;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de disponibilidade via camada HTTP (filtro + controller). Cobre o
 * admin (/admin/availability-slots: create/list/update/delete, auth). Modelado no
 * InvitationControllerIntegrationTest (seedTenantAdmin + mintValidToken + mockMvc).
 */
class AvailabilityControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    @DisplayName("POST /admin/availability-slots autenticado (tenant) → 201 com a janela")
    void createSlot_authenticated_returns201() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(post("/admin/availability-slots")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"weekday\":1,\"startsAt\":\"09:00\",\"endsAt\":\"12:00\",\"slotMinutes\":30}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.weekday").value(1))
            .andExpect(jsonPath("$.startsAt").value("09:00"))
            .andExpect(jsonPath("$.endsAt").value("12:00"))
            .andExpect(jsonPath("$.slotMinutes").value(30))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("POST /admin/availability-slots sem auth → 401")
    void createSlot_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/admin/availability-slots")
                .contentType("application/json")
                .content("{\"weekday\":1,\"startsAt\":\"09:00\",\"endsAt\":\"12:00\",\"slotMinutes\":30}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /admin/availability-slots como super-admin → 403 (não é tenant-admin)")
    void createSlot_superAdmin_returns403() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, ADMIN_SUB);
        mockMvc.perform(post("/admin/availability-slots")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"weekday\":1,\"startsAt\":\"09:00\",\"endsAt\":\"12:00\",\"slotMinutes\":30}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_tenant_admin"));
    }

    @Test
    @DisplayName("GET /admin/availability-slots lista só janelas da própria empresa")
    void listSlots_returnsOwnCompanyOnly() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        // cria 1 janela da própria empresa via o próprio endpoint.
        mockMvc.perform(post("/admin/availability-slots")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"weekday\":2,\"startsAt\":\"08:00\",\"endsAt\":\"10:00\",\"slotMinutes\":15}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/admin/availability-slots").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].weekday").value(2))
            .andExpect(jsonPath("$[0].startsAt").value("08:00"));
    }

    @Test
    @DisplayName("PUT /admin/availability-slots/{id} altera um campo → 200")
    void updateSlot_changesField_returns200() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String id = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(mockMvc.perform(post("/admin/availability-slots")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"weekday\":3,\"startsAt\":\"09:00\",\"endsAt\":\"12:00\",\"slotMinutes\":30}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
            .get("id").asText();

        mockMvc.perform(put("/admin/availability-slots/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"weekday\":3,\"startsAt\":\"09:00\",\"endsAt\":\"18:00\",\"slotMinutes\":60,\"active\":false}"))
            .andExpect(status().isOk());

        // confirma a mudança via list.
        mockMvc.perform(get("/admin/availability-slots").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].endsAt").value("18:00"))
            .andExpect(jsonPath("$[0].slotMinutes").value(60))
            .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    @DisplayName("DELETE /admin/availability-slots/{id} remove a janela → 204")
    void deleteSlot_removes_returns204() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String id = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(mockMvc.perform(post("/admin/availability-slots")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"weekday\":4,\"startsAt\":\"09:00\",\"endsAt\":\"12:00\",\"slotMinutes\":30}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
            .get("id").asText();

        mockMvc.perform(delete("/admin/availability-slots/" + id)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/admin/availability-slots").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
