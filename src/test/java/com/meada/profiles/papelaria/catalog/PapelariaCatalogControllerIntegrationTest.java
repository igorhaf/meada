package com.meada.profiles.papelaria.catalog;

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
 * Testa os endpoints de catálogo do perfil papelaria (camada 8.15 / perfil papelaria): CRUD de item
 * (incl. made_to_order/lead_time + specs) + CRUD de OPÇÕES + profile guard 403 pra tenant não-papelaria
 * + 400/404. Clone do PadariaMenuControllerIntegrationTest (camada 8.8) — menu→catalog, allergens→specs.
 */
class PapelariaCatalogControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    /** Provisiona um tenant do perfil dado e devolve (companyId, token). */
    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD item sob encomenda: POST cria (made_to_order+lead+specs) → GET lista → PATCH preço → toggle → DELETE")
    void crudItem() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "papelaria@test.dev", "papelaria");
        String t = mintValidToken("papelaria@test.dev", sub);

        // POST (convite sob encomenda).
        mockMvc.perform(post("/api/papelaria/catalog").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Convite Casamento\",\"description\":\"clássico\",\"priceCents\":800,"
                    + "\"category\":\"convites\",\"madeToOrder\":true,\"leadTimeDays\":7,\"specs\":\"Couché 250g\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Convite Casamento"))
            .andExpect(jsonPath("$.madeToOrder").value(true))
            .andExpect(jsonPath("$.leadTimeDays").value(7))
            .andExpect(jsonPath("$.available").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from papelaria_catalog_items where name = 'Convite Casamento'", UUID.class);

        // GET lista
        mockMvc.perform(get("/api/papelaria/catalog").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        // PATCH preço
        mockMvc.perform(patch("/api/papelaria/catalog/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceCents\":900}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(900));

        // toggle desliga
        mockMvc.perform(patch("/api/papelaria/catalog/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        // DELETE
        mockMvc.perform(delete("/api/papelaria/catalog/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("CRUD opção: POST opção → GET item traz options → PATCH delta → toggle → DELETE")
    void crudOption() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "papelaria@test.dev", "papelaria");
        String t = mintValidToken("papelaria@test.dev", sub);

        // item base
        mockMvc.perform(post("/api/papelaria/catalog").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Convite Festa\",\"priceCents\":1200,\"category\":\"convites\",\"madeToOrder\":true}"))
            .andExpect(status().isCreated());
        UUID itemId = jdbcTemplate.queryForObject("select id from papelaria_catalog_items where name = 'Convite Festa'", UUID.class);

        // POST opção
        mockMvc.perform(post("/api/papelaria/catalog/" + itemId + "/options").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"groupLabel\":\"Papel\",\"optionLabel\":\"Perolado\",\"priceDeltaCents\":500,\"sortOrder\":0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.optionLabel").value("Perolado"))
            .andExpect(jsonPath("$.priceDeltaCents").value(500));

        UUID optId = jdbcTemplate.queryForObject(
            "select id from papelaria_catalog_item_options where option_label = 'Perolado'", UUID.class);

        // GET do item já traz a opção embutida (hidratação)
        mockMvc.perform(get("/api/papelaria/catalog/" + itemId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.options.length()").value(1))
            .andExpect(jsonPath("$.options[0].optionLabel").value("Perolado"));

        // GET lista de opções do item (envelope "options")
        mockMvc.perform(get("/api/papelaria/catalog/" + itemId + "/options").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.options.length()").value(1));

        // PATCH delta da opção
        mockMvc.perform(patch("/api/papelaria/catalog/" + itemId + "/options/" + optId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceDeltaCents\":600}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceDeltaCents").value(600));

        // toggle opção
        mockMvc.perform(patch("/api/papelaria/catalog/" + itemId + "/options/" + optId + "/toggle")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        // DELETE opção
        mockMvc.perform(delete("/api/papelaria/catalog/" + itemId + "/options/" + optId).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com categoria inválida → 400 invalid_category")
    void invalidCategory() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "papelaria@test.dev", "papelaria");
        String t = mintValidToken("papelaria@test.dev", sub);
        mockMvc.perform(post("/api/papelaria/catalog").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":100,\"category\":\"banner_gigante\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_category"));
    }

    @Test
    @DisplayName("tenant NÃO-papelaria (legal) batendo no /api/papelaria/catalog → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/papelaria/catalog").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
        mockMvc.perform(post("/api/papelaria/catalog").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":1,\"category\":\"convites\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 catalog_item_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "papelaria@test.dev", "papelaria");
        String t = mintValidToken("papelaria@test.dev", sub);
        mockMvc.perform(get("/api/papelaria/catalog/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("catalog_item_not_found"));
    }
}
