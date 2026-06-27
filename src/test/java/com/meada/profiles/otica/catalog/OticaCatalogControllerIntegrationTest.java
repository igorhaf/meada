package com.meada.profiles.otica.catalog;

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
 * Testa os endpoints de catálogo do otica (camada 8.12, FLUXO B): CRUD de item (com made_to_order +
 * lead_time_days), categoria inválida → 400, CRUD de opção (tipo de lente/tratamento),
 * catalog_item_in_use → 409, profile guard 403.
 */
class OticaCatalogControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD item: POST cria armação sob encomenda c/ lead → GET → PATCH → toggle → DELETE")
    void crudItem() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "otica@test.dev", "otica");
        String t = mintValidToken("otica@test.dev", sub);

        mockMvc.perform(post("/api/otica/catalog").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Armação X\",\"priceCents\":19900,"
                    + "\"category\":\"armacoes\",\"madeToOrder\":true,\"leadTimeDays\":5}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Armação X"))
            .andExpect(jsonPath("$.madeToOrder").value(true))
            .andExpect(jsonPath("$.leadTimeDays").value(5))
            .andExpect(jsonPath("$.available").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from otica_catalog_items where name = 'Armação X'", UUID.class);

        mockMvc.perform(get("/api/otica/catalog").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/otica/catalog/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceCents\":24900}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(24900));

        mockMvc.perform(patch("/api/otica/catalog/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(delete("/api/otica/catalog/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST item com categoria inválida → 400 invalid_category")
    void invalidCategory() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "otica@test.dev", "otica");
        String t = mintValidToken("otica@test.dev", sub);
        mockMvc.perform(post("/api/otica/catalog").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Y\",\"priceCents\":100,\"category\":\"hot_rolls\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_category"));
    }

    @Test
    @DisplayName("CRUD opção (tipo de lente/tratamento): POST → GET → PATCH → DELETE")
    void crudOption() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "otica@test.dev", "otica");
        String t = mintValidToken("otica@test.dev", sub);

        mockMvc.perform(post("/api/otica/catalog").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Lente X\",\"priceCents\":9900,\"category\":\"lentes\","
                    + "\"madeToOrder\":true}"))
            .andExpect(status().isCreated());
        UUID itemId = jdbcTemplate.queryForObject("select id from otica_catalog_items where name = 'Lente X'", UUID.class);

        mockMvc.perform(post("/api/otica/catalog/" + itemId + "/options").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"groupLabel\":\"Tipo de lente\",\"optionLabel\":\"Multifocal\","
                    + "\"priceDeltaCents\":15000,\"sortOrder\":0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.optionLabel").value("Multifocal"))
            .andExpect(jsonPath("$.priceDeltaCents").value(15000));
        UUID optId = jdbcTemplate.queryForObject("select id from otica_catalog_item_options where option_label = 'Multifocal'", UUID.class);

        mockMvc.perform(get("/api/otica/catalog/" + itemId + "/options").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.options.length()").value(1));

        mockMvc.perform(patch("/api/otica/catalog/" + itemId + "/options/" + optId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceDeltaCents\":18000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceDeltaCents").value(18000));

        mockMvc.perform(delete("/api/otica/catalog/" + itemId + "/options/" + optId).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE de item com encomenda → 409 catalog_item_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "otica@test.dev", "otica");
        String t = mintValidToken("otica@test.dev", sub);

        // Item + conversa/contato + pedido + order_item referenciando o item (FK restrict).
        UUID itemId = UUID.randomUUID();
        jdbcTemplate.update("insert into otica_catalog_items (id, company_id, name, price_cents, category) "
            + "values (?, ?, 'Armação Z', 10000, 'armacoes')", itemId, companyId);
        UUID instance = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, companyId, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+5511999990012", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, companyId, contactId, instance);
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("insert into otica_orders (id, company_id, conversation_id, contact_id, subtotal_cents, "
            + "total_cents, prescription_pending) values (?, ?, ?, ?, 10000, 10000, true)",
            orderId, companyId, conversationId, contactId);
        jdbcTemplate.update("insert into otica_order_items (order_id, catalog_item_id, qtd, unit_price_cents, item_name_snapshot) "
            + "values (?, ?, 1, 10000, 'Armação Z')", orderId, itemId);

        mockMvc.perform(delete("/api/otica/catalog/" + itemId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("catalog_item_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-otica (pet) → /api/otica/catalog → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/otica/catalog").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
