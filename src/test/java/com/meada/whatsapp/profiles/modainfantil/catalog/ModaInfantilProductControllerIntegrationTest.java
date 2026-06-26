package com.meada.whatsapp.profiles.modainfantil.catalog;

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
 * Testa os endpoints de catálogo do perfil moda_infantil (camada 8.22): CRUD de produto + CRUD de
 * VARIANTES (a grade faixa-etária×cor) + profile guard 403 pra tenant não-moda_infantil + 400/404/409.
 * Clone do LingerieProductControllerIntegrationTest, adaptado às categorias + faixas etárias.
 */
class ModaInfantilProductControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    /** Provisiona um tenant do perfil dado e devolve o companyId. */
    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD produto: POST cria → GET lista → PATCH edita → toggle → DELETE")
    void crudProduct() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "moda-infantil@test.dev", "moda_infantil");
        String t = mintValidToken("moda-infantil@test.dev", sub);

        mockMvc.perform(post("/api/moda-infantil/products").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Body Manga Longa\",\"description\":\"algodão\",\"category\":\"bebe\",\"basePriceCents\":5990}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Body Manga Longa"))
            .andExpect(jsonPath("$.available").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from moda_infantil_products where name = 'Body Manga Longa'", UUID.class);

        mockMvc.perform(get("/api/moda-infantil/products").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/moda-infantil/products/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"basePriceCents\":6500}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.basePriceCents").value(6500));

        mockMvc.perform(patch("/api/moda-infantil/products/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(delete("/api/moda-infantil/products/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("CRUD variante: POST variante → GET produto traz variants → PATCH estoque → toggle → DELETE")
    void crudVariant() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "moda-infantil@test.dev", "moda_infantil");
        String t = mintValidToken("moda-infantil@test.dev", sub);

        mockMvc.perform(post("/api/moda-infantil/products").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Conjunto\",\"category\":\"menina\",\"basePriceCents\":6990}"))
            .andExpect(status().isCreated());
        UUID productId = jdbcTemplate.queryForObject("select id from moda_infantil_products where name = 'Conjunto'", UUID.class);

        mockMvc.perform(post("/api/moda-infantil/products/" + productId + "/variants").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"size\":\"2a\",\"color\":\"Rosa\",\"sku\":\"SKU-1\",\"priceCents\":7490,\"stockQty\":5}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.size").value("2a"))
            .andExpect(jsonPath("$.color").value("Rosa"))
            .andExpect(jsonPath("$.stockQty").value(5));

        UUID variantId = jdbcTemplate.queryForObject(
            "select id from moda_infantil_variants where size = '2a' and color = 'Rosa'", UUID.class);

        // GET do produto já traz a variante embutida (hidratação).
        mockMvc.perform(get("/api/moda-infantil/products/" + productId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants.length()").value(1))
            .andExpect(jsonPath("$.variants[0].size").value("2a"));

        // GET lista de variantes do produto (envelope "variants").
        mockMvc.perform(get("/api/moda-infantil/products/" + productId + "/variants").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants.length()").value(1));

        // PATCH estoque da variante.
        mockMvc.perform(patch("/api/moda-infantil/products/" + productId + "/variants/" + variantId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"stockQty\":12}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockQty").value(12));

        // toggle variante.
        mockMvc.perform(patch("/api/moda-infantil/products/" + productId + "/variants/" + variantId + "/toggle")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        // DELETE variante.
        mockMvc.perform(delete("/api/moda-infantil/products/" + productId + "/variants/" + variantId).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST variante duplicada (mesma faixa+cor) → 409 duplicate_variant")
    void duplicateVariant() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "moda-infantil@test.dev", "moda_infantil");
        String t = mintValidToken("moda-infantil@test.dev", sub);

        mockMvc.perform(post("/api/moda-infantil/products").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Body\",\"category\":\"bebe\",\"basePriceCents\":1990}"))
            .andExpect(status().isCreated());
        UUID productId = jdbcTemplate.queryForObject("select id from moda_infantil_products where name = 'Body'", UUID.class);

        mockMvc.perform(post("/api/moda-infantil/products/" + productId + "/variants").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"size\":\"RN\",\"color\":\"Branco\",\"stockQty\":3}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/moda-infantil/products/" + productId + "/variants").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"size\":\"RN\",\"color\":\"Branco\",\"stockQty\":9}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("duplicate_variant"));
    }

    @Test
    @DisplayName("POST variante com faixa etária inválida → 400 invalid_size")
    void invalidSize() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "moda-infantil@test.dev", "moda_infantil");
        String t = mintValidToken("moda-infantil@test.dev", sub);

        mockMvc.perform(post("/api/moda-infantil/products").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Meia\",\"category\":\"acessorios\",\"basePriceCents\":1990}"))
            .andExpect(status().isCreated());
        UUID productId = jdbcTemplate.queryForObject("select id from moda_infantil_products where name = 'Meia'", UUID.class);

        // 'M' é tamanho de lingerie, não faixa etária — rejeitado pelo KidsSize.
        mockMvc.perform(post("/api/moda-infantil/products/" + productId + "/variants").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"size\":\"M\",\"color\":\"Preto\",\"stockQty\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_size"));
    }

    @Test
    @DisplayName("POST produto com categoria inválida → 400 invalid_category")
    void invalidCategory() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "moda-infantil@test.dev", "moda_infantil");
        String t = mintValidToken("moda-infantil@test.dev", sub);
        mockMvc.perform(post("/api/moda-infantil/products").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"category\":\"sutias\",\"basePriceCents\":100}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_category"));
    }

    @Test
    @DisplayName("tenant NÃO-moda_infantil (legal) batendo no /api/moda-infantil/products → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/moda-infantil/products").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
        mockMvc.perform(post("/api/moda-infantil/products").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"category\":\"bebe\",\"basePriceCents\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 product_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "moda-infantil@test.dev", "moda_infantil");
        String t = mintValidToken("moda-infantil@test.dev", sub);
        mockMvc.perform(get("/api/moda-infantil/products/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("product_not_found"));
    }
}
