package com.meada.whatsapp.profiles.nutri.plans;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de planos (camada 8.0): POST cria, GET ?patientId, GET /active, 2º ativo
 * arquiva o 1º, PATCH /archive, PATCH /activate, profile guard 403.
 */
class NutriPlanControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedContact(UUID companyId, String phone, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            id, companyId, phone, name);
        return id;
    }

    private UUID seedPatient(UUID companyId, UUID contactId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_patients (id, company_id, contact_id, name) values (?, ?, ?, ?)",
            id, companyId, contactId, name);
        return id;
    }

    @Test
    @DisplayName("POST cria → GET ?patientId lista → GET /active devolve o ativo")
    void createAndList() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "nutri@test.dev", "nutri");
        String t = mintValidToken("nutri@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990084", "Marina");
        UUID patientId = seedPatient(companyId, contactId, "Marina");

        mockMvc.perform(post("/api/nutri/plans").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"patientId\":\"" + patientId + "\",\"title\":\"Plano 1\",\"body\":\"Corpo 1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Plano 1"))
            .andExpect(jsonPath("$.status").value("ativo"));

        mockMvc.perform(get("/api/nutri/plans?patientId=" + patientId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/nutri/plans/active?patientId=" + patientId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Plano 1"));
    }

    @Test
    @DisplayName("2º plano ativo → 1º vira 'arquivado' (1 ativo só)")
    void secondActiveArchivesFirst() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "nutri@test.dev", "nutri");
        String t = mintValidToken("nutri@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990085", "Marina");
        UUID patientId = seedPatient(companyId, contactId, "Marina");

        mockMvc.perform(post("/api/nutri/plans").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"patientId\":\"" + patientId + "\",\"title\":\"Plano 1\",\"body\":\"Corpo 1\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/nutri/plans").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"patientId\":\"" + patientId + "\",\"title\":\"Plano 2\",\"body\":\"Corpo 2\"}"))
            .andExpect(status().isCreated());

        // só 1 ativo, e é o Plano 2.
        mockMvc.perform(get("/api/nutri/plans/active?patientId=" + patientId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Plano 2"));
        mockMvc.perform(get("/api/nutri/plans?patientId=" + patientId + "&status=arquivado")
                .header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Plano 1"));
    }

    @Test
    @DisplayName("PATCH /archive arquiva, depois PATCH /activate reativa")
    void archiveThenActivate() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "nutri@test.dev", "nutri");
        String t = mintValidToken("nutri@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990086", "Marina");
        UUID patientId = seedPatient(companyId, contactId, "Marina");

        mockMvc.perform(post("/api/nutri/plans").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"patientId\":\"" + patientId + "\",\"title\":\"Plano 1\",\"body\":\"Corpo 1\"}"))
            .andExpect(status().isCreated());
        UUID planId = jdbcTemplate.queryForObject("select id from nutri_plans where title = 'Plano 1'", UUID.class);

        mockMvc.perform(patch("/api/nutri/plans/" + planId + "/archive").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("arquivado"));

        mockMvc.perform(patch("/api/nutri/plans/" + planId + "/activate").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ativo"));
    }

    @Test
    @DisplayName("tenant NÃO-nutri (pet) → /api/nutri/plans → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/nutri/plans?patientId=" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
