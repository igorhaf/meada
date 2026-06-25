package com.meada.whatsapp.profiles.floricultura.catalog;

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
 * Testa os endpoints de cardápio do perfil floricultura (camada 8.4): CRUD de item + CRUD de OPÇÕES
 * (ESCAPADA 2) + profile guard 403 pra tenant não-floricultura + 400/404. Clone do
 * SushiCatalogControllerIntegrationTest + as opções.
 */
class FloriculturaCatalogControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    /** Provisiona um tenant do perfil dado e devolve (companyId, token). */
    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD item: POST cria → GET lista → PATCH edita → toggle → DELETE")
    void crudItem() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "floricultura@test.dev", "floricultura");
        String t = mintValidToken("floricultura@test.dev", sub);

        // POST
        mockMvc.perform(post("/api/floricultura/catalog").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"X-Burger\",\"description\":\"pão+carne\",\"priceCents\":2500,\"category\":\"buques\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("X-Burger"))
            .andExpect(jsonPath("$.available").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from floricultura_catalog_items where name = 'X-Burger'", UUID.class);

        // GET lista
        mockMvc.perform(get("/api/floricultura/catalog").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        // PATCH preço
        mockMvc.perform(patch("/api/floricultura/catalog/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceCents\":2800}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(2800));

        // toggle desliga
        mockMvc.perform(patch("/api/floricultura/catalog/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        // DELETE
        mockMvc.perform(delete("/api/floricultura/catalog/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("CRUD opção (ESCAPADA 2): POST opção → GET item traz options → PATCH delta → toggle → DELETE")
    void crudOption() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "floricultura@test.dev", "floricultura");
        String t = mintValidToken("floricultura@test.dev", sub);

        // item base
        mockMvc.perform(post("/api/floricultura/catalog").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"X-Salada\",\"priceCents\":2700,\"category\":\"buques\"}"))
            .andExpect(status().isCreated());
        UUID itemId = jdbcTemplate.queryForObject("select id from floricultura_catalog_items where name = 'X-Salada'", UUID.class);

        // POST opção
        mockMvc.perform(post("/api/floricultura/catalog/" + itemId + "/options").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"groupLabel\":\"Adicionais\",\"optionLabel\":\"Bacon\",\"priceDeltaCents\":300,\"sortOrder\":0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.optionLabel").value("Bacon"))
            .andExpect(jsonPath("$.priceDeltaCents").value(300));

        UUID optId = jdbcTemplate.queryForObject(
            "select id from floricultura_catalog_item_options where option_label = 'Bacon'", UUID.class);

        // GET do item já traz a opção embutida (hidratação)
        mockMvc.perform(get("/api/floricultura/catalog/" + itemId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.options.length()").value(1))
            .andExpect(jsonPath("$.options[0].optionLabel").value("Bacon"));

        // GET lista de opções do item (envelope "options", não "items")
        mockMvc.perform(get("/api/floricultura/catalog/" + itemId + "/options").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.options.length()").value(1));

        // PATCH delta da opção
        mockMvc.perform(patch("/api/floricultura/catalog/" + itemId + "/options/" + optId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceDeltaCents\":400}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceDeltaCents").value(400));

        // toggle opção
        mockMvc.perform(patch("/api/floricultura/catalog/" + itemId + "/options/" + optId + "/toggle")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        // DELETE opção
        mockMvc.perform(delete("/api/floricultura/catalog/" + itemId + "/options/" + optId).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com categoria inválida → 400 invalid_category")
    void invalidCategory() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "floricultura@test.dev", "floricultura");
        String t = mintValidToken("floricultura@test.dev", sub);
        mockMvc.perform(post("/api/floricultura/catalog").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":100,\"category\":\"hot_rolls\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_category"));
    }

    @Test
    @DisplayName("tenant NÃO-floricultura (legal) batendo no /api/floricultura/catalog → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/floricultura/catalog").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
        mockMvc.perform(post("/api/floricultura/catalog").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":1,\"category\":\"buques\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 catalog_item_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "floricultura@test.dev", "floricultura");
        String t = mintValidToken("floricultura@test.dev", sub);
        mockMvc.perform(get("/api/floricultura/catalog/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("catalog_item_not_found"));
    }
}
