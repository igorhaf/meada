package com.meada.whatsapp.cms;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import com.meada.whatsapp.profiles.features.ProfileFeatureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints do CMS do tenant (SM-M) e o GATE de feature: com o CMS LIGADO pro nicho, os
 * endpoints respondem; DESLIGADO (default), 403 feature_disabled. Mais validação de blocks/domínio.
 */
class CmsTenantControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUB = UUID.fromString("c5000000-0000-0000-0000-000000000001");
    private static final UUID ROOT = UUID.fromString("c5000000-0000-0000-0000-0000000000aa");
    private static final String JSON = "application/json";

    @Autowired
    private ProfileFeatureService featureService;

    /** Seed: tenant-admin do nicho dado; opcionalmente liga o CMS pro nicho. */
    private String seedTenant(String email, String profileId, boolean cmsOn) {
        UUID companyId = seedTenantAdmin(email, SUB);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        if (cmsOn) {
            featureService.setFlag(profileId, "cms", true, ROOT);
        }
        return mintValidToken(email, SUB);
    }

    @Test
    @DisplayName("CMS desligado pro nicho → GET /api/cms/page → 403 feature_disabled")
    void featureOff_forbidden() throws Exception {
        String t = seedTenant("t@cms.dev", "nutri", false);
        mockMvc.perform(get("/api/cms/page").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("feature_disabled"));
    }

    @Test
    @DisplayName("CMS ligado → GET cria página vazia; PUT salva blocks; publish; tudo 200")
    void featureOn_fullFlow() throws Exception {
        String t = seedTenant("t@cms.dev", "nutri", true);

        mockMvc.perform(get("/api/cms/page").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.published").value(false))
            .andExpect(jsonPath("$.slug").value("empresa-teste-" + companyIdOf("t@cms.dev")));

        mockMvc.perform(put("/api/cms/page").header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"title\":\"Loja\",\"blocks\":[{\"type\":\"hero\",\"props\":{\"title\":\"Oi\"}}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Loja"))
            .andExpect(jsonPath("$.blocks.length()").value(1))
            .andExpect(jsonPath("$.blocks[0].type").value("hero"));

        mockMvc.perform(put("/api/cms/page/publish").header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"published\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.published").value(true));
    }

    @Test
    @DisplayName("CMS ligado → PUT com bloco inválido → 400 invalid_blocks")
    void invalidBlocks_400() throws Exception {
        String t = seedTenant("t@cms.dev", "nutri", true);
        mockMvc.perform(put("/api/cms/page").header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"title\":\"X\",\"blocks\":[{\"type\":\"naoexiste\",\"props\":{}}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_blocks"));
    }

    @Test
    @DisplayName("CMS ligado → PUT domínio válido 200; inválido 400 invalid_domain")
    void domain() throws Exception {
        String t = seedTenant("t@cms.dev", "nutri", true);
        mockMvc.perform(put("/api/cms/page/domain").header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"domain\":\"minhaloja.com.br\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain").value("minhaloja.com.br"));
        mockMvc.perform(put("/api/cms/page/domain").header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"domain\":\"foo.meadadigital.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_domain"));
    }

    @Test
    @DisplayName("sem token → 401 (endpoint atrás do filtro /api/cms/**)")
    void noToken_401() throws Exception {
        mockMvc.perform(get("/api/cms/page"))
            .andExpect(status().isUnauthorized());
    }

    private UUID companyIdOf(String email) {
        return jdbcTemplate.queryForObject("select company_id from users where email = ?", UUID.class, email);
    }
}
