package com.meada.profiles.sushi.categories;

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
 * Testa os endpoints de categorias (camada 7.1 / sushi funcional): CRUD, duplicate_category,
 * category_in_use, 403 pra tenant não-sushi.
 */
class SushiCategoryControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST → GET → PATCH → toggle → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);

        mockMvc.perform(post("/api/sushi/categories").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Hot rolls\",\"sortOrder\":1}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Hot rolls"));

        UUID id = jdbcTemplate.queryForObject("select id from sushi_categories where name = 'Hot rolls'", UUID.class);

        mockMvc.perform(get("/api/sushi/categories").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/sushi/categories/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Hot Rolls\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Hot Rolls"));

        mockMvc.perform(patch("/api/sushi/categories/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/sushi/categories/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com nome duplicado → 409 duplicate_category")
    void duplicate() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        jdbcTemplate.update("insert into sushi_categories (company_id, name) values (?, 'Sashimi')", companyId);
        mockMvc.perform(post("/api/sushi/categories").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"sashimi\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("duplicate_category"));
    }

    @Test
    @DisplayName("DELETE de categoria com itens → 409 category_in_use")
    void inUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        UUID cat = jdbcTemplate.queryForObject(
            "insert into sushi_categories (company_id, name) values (?, 'Bebidas') returning id", UUID.class, companyId);
        jdbcTemplate.update("insert into sushi_menu_items (company_id, name, price_cents, category) values (?, 'Coca', 600, ?)",
            companyId, cat);
        mockMvc.perform(delete("/api/sushi/categories/" + cat).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("category_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-sushi → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/sushi/categories").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
