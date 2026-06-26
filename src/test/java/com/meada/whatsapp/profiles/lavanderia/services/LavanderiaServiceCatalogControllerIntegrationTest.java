package com.meada.whatsapp.profiles.lavanderia.services;

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
 * Testa os endpoints de catálogo do perfil lavanderia (camada 8.10): CRUD de serviço + CRUD de OPÇÕES
 * + profile guard 403 pra tenant não-lavanderia + 400/404. Clone do FloriculturaCatalogControllerIT.
 */
class LavanderiaServiceCatalogControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD serviço: POST cria → GET lista → PATCH edita → toggle → DELETE")
    void crudService() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "lavanderia@test.dev", "lavanderia");
        String t = mintValidToken("lavanderia@test.dev", sub);

        mockMvc.perform(post("/api/lavanderia/services").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Lavar camisa\",\"description\":\"por peça\",\"priceCents\":800,"
                    + "\"category\":\"lavar\",\"turnaroundDays\":2,\"careInstructions\":\"lavagem a frio\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Lavar camisa"))
            .andExpect(jsonPath("$.turnaroundDays").value(2))
            .andExpect(jsonPath("$.available").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from lavanderia_services where name = 'Lavar camisa'", UUID.class);

        mockMvc.perform(get("/api/lavanderia/services").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/lavanderia/services/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceCents\":900}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(900));

        mockMvc.perform(patch("/api/lavanderia/services/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(delete("/api/lavanderia/services/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("CRUD opção: POST opção → GET serviço traz options → PATCH delta → toggle → DELETE")
    void crudOption() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "lavanderia@test.dev", "lavanderia");
        String t = mintValidToken("lavanderia@test.dev", sub);

        mockMvc.perform(post("/api/lavanderia/services").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Lavar e passar\",\"priceCents\":1200,\"category\":\"lavar_passar\",\"turnaroundDays\":2}"))
            .andExpect(status().isCreated());
        UUID serviceId = jdbcTemplate.queryForObject("select id from lavanderia_services where name = 'Lavar e passar'", UUID.class);

        mockMvc.perform(post("/api/lavanderia/services/" + serviceId + "/options").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"groupLabel\":\"Acabamento\",\"optionLabel\":\"Engomar\",\"priceDeltaCents\":300,\"sortOrder\":0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.optionLabel").value("Engomar"))
            .andExpect(jsonPath("$.priceDeltaCents").value(300));

        UUID optId = jdbcTemplate.queryForObject(
            "select id from lavanderia_service_options where option_label = 'Engomar'", UUID.class);

        mockMvc.perform(get("/api/lavanderia/services/" + serviceId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.options.length()").value(1))
            .andExpect(jsonPath("$.options[0].optionLabel").value("Engomar"));

        mockMvc.perform(get("/api/lavanderia/services/" + serviceId + "/options").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.options.length()").value(1));

        mockMvc.perform(patch("/api/lavanderia/services/" + serviceId + "/options/" + optId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceDeltaCents\":400}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceDeltaCents").value(400));

        mockMvc.perform(patch("/api/lavanderia/services/" + serviceId + "/options/" + optId + "/toggle")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(delete("/api/lavanderia/services/" + serviceId + "/options/" + optId).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com categoria inválida → 400 invalid_category")
    void invalidCategory() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "lavanderia@test.dev", "lavanderia");
        String t = mintValidToken("lavanderia@test.dev", sub);
        mockMvc.perform(post("/api/lavanderia/services").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":100,\"category\":\"tingir\",\"turnaroundDays\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_category"));
    }

    @Test
    @DisplayName("tenant NÃO-lavanderia (legal) batendo no /api/lavanderia/services → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/lavanderia/services").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
        mockMvc.perform(post("/api/lavanderia/services").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":1,\"category\":\"lavar\",\"turnaroundDays\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 service_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "lavanderia@test.dev", "lavanderia");
        String t = mintValidToken("lavanderia@test.dev", sub);
        mockMvc.perform(get("/api/lavanderia/services/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("service_not_found"));
    }
}
