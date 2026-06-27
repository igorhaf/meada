package com.meada.profiles.adega.menu;

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
 * Testa os endpoints de cardápio do perfil adega (camada 8.9): CRUD de item + CRUD de OPÇÕES
 * (modifiers) + profile guard 403 pra tenant não-adega + 400/404. Clone do
 * ComidaMenuControllerIntegrationTest, adaptado pras categorias de adega.
 */
class AdegaMenuControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
        seedTenant(sub, "adega@test.dev", "adega");
        String t = mintValidToken("adega@test.dev", sub);

        // POST
        mockMvc.perform(post("/api/adega/menu").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Cabernet\",\"description\":\"tinto 750ml\",\"priceCents\":8900,\"category\":\"vinhos\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Cabernet"))
            .andExpect(jsonPath("$.available").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from adega_menu_items where name = 'Cabernet'", UUID.class);

        // GET lista
        mockMvc.perform(get("/api/adega/menu").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        // PATCH preço
        mockMvc.perform(patch("/api/adega/menu/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceCents\":9500}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(9500));

        // toggle desliga
        mockMvc.perform(patch("/api/adega/menu/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        // DELETE
        mockMvc.perform(delete("/api/adega/menu/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("CRUD opção (modifiers): POST opção → GET item traz options → PATCH delta → toggle → DELETE")
    void crudOption() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "adega@test.dev", "adega");
        String t = mintValidToken("adega@test.dev", sub);

        // item base
        mockMvc.perform(post("/api/adega/menu").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Vodka\",\"priceCents\":9000,\"category\":\"destilados\"}"))
            .andExpect(status().isCreated());
        UUID itemId = jdbcTemplate.queryForObject("select id from adega_menu_items where name = 'Vodka'", UUID.class);

        // POST opção
        mockMvc.perform(post("/api/adega/menu/" + itemId + "/options").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"groupLabel\":\"Volume\",\"optionLabel\":\"1L\",\"priceDeltaCents\":3000,\"sortOrder\":0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.optionLabel").value("1L"))
            .andExpect(jsonPath("$.priceDeltaCents").value(3000));

        UUID optId = jdbcTemplate.queryForObject(
            "select id from adega_menu_item_options where option_label = '1L'", UUID.class);

        // GET do item já traz a opção embutida (hidratação)
        mockMvc.perform(get("/api/adega/menu/" + itemId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.options.length()").value(1))
            .andExpect(jsonPath("$.options[0].optionLabel").value("1L"));

        // GET lista de opções do item (envelope "options", não "items")
        mockMvc.perform(get("/api/adega/menu/" + itemId + "/options").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.options.length()").value(1));

        // PATCH delta da opção
        mockMvc.perform(patch("/api/adega/menu/" + itemId + "/options/" + optId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceDeltaCents\":3500}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceDeltaCents").value(3500));

        // toggle opção
        mockMvc.perform(patch("/api/adega/menu/" + itemId + "/options/" + optId + "/toggle")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        // DELETE opção
        mockMvc.perform(delete("/api/adega/menu/" + itemId + "/options/" + optId).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com categoria inválida → 400 invalid_category")
    void invalidCategory() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "adega@test.dev", "adega");
        String t = mintValidToken("adega@test.dev", sub);
        mockMvc.perform(post("/api/adega/menu").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":100,\"category\":\"hot_rolls\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_category"));
    }

    @Test
    @DisplayName("tenant NÃO-adega (legal) batendo no /api/adega/menu → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/adega/menu").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
        mockMvc.perform(post("/api/adega/menu").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":1,\"category\":\"vinhos\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 menu_item_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "adega@test.dev", "adega");
        String t = mintValidToken("adega@test.dev", sub);
        mockMvc.perform(get("/api/adega/menu/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("menu_item_not_found"));
    }
}
