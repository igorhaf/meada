package com.meada.cms;

import com.meada.admin.AbstractAdminIntegrationTest;
import com.meada.profiles.features.ProfileFeatureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints do CMS do tenant (SM-N) e o GATE de feature: CMS desligado → 403
 * feature_disabled; ligado → site + páginas CRUD; validação de blocks/domínio.
 */
class CmsTenantControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUB = UUID.fromString("cf500000-0000-0000-0000-000000000001");
    private static final UUID ROOT = UUID.fromString("cf500000-0000-0000-0000-0000000000aa");
    private static final String JSON = "application/json";

    @Autowired
    private ProfileFeatureService featureService;

    private String seedTenant(String email, boolean cmsOn) {
        UUID companyId = seedTenantAdmin(email, SUB);
        jdbcTemplate.update("update companies set profile_id = 'nutri' where id = ?", companyId);
        if (cmsOn) {
            featureService.setFlag("nutri", "cms", true, ROOT);
        }
        return mintValidToken(email, SUB);
    }

    private UUID pageId(String email, String pageSlug) {
        UUID companyId = jdbcTemplate.queryForObject("select company_id from users where email = ?", UUID.class, email);
        return jdbcTemplate.queryForObject("select id from cms_pages where company_id = ? and page_slug = ?",
            UUID.class, companyId, pageSlug);
    }

    @Test
    @DisplayName("CMS desligado → GET /api/cms/site → 403 feature_disabled")
    void off_forbidden() throws Exception {
        String t = seedTenant("t@cms.dev", false);
        mockMvc.perform(get("/api/cms/site").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("feature_disabled"));
    }

    @Test
    @DisplayName("CMS ligado: cria página (vira home), salva blocks, publica; GET reflete")
    void on_pagesFlow() throws Exception {
        String t = seedTenant("t@cms.dev", true);

        mockMvc.perform(post("/api/cms/pages").header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"pageSlug\":\"home\",\"title\":\"Início\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isHome").value(true))
            .andExpect(jsonPath("$.pageSlug").value("home"));

        UUID id = pageId("t@cms.dev", "home");
        mockMvc.perform(put("/api/cms/pages/" + id).header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"title\":\"Início\",\"blocks\":[{\"type\":\"hero\",\"props\":{\"title\":\"Oi\"}}],\"published\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocks.length()").value(1))
            .andExpect(jsonPath("$.published").value(true));

        mockMvc.perform(get("/api/cms/site").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pages.length()").value(1))
            .andExpect(jsonPath("$.site.slug").exists());
    }

    @Test
    @DisplayName("CMS ligado: bloco inválido → 400 invalid_blocks; slug dup → 409 page_slug_taken")
    void on_validation() throws Exception {
        String t = seedTenant("t@cms.dev", true);
        mockMvc.perform(post("/api/cms/pages").header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"pageSlug\":\"home\",\"title\":\"H\"}")).andExpect(status().isCreated());
        UUID id = pageId("t@cms.dev", "home");
        mockMvc.perform(put("/api/cms/pages/" + id).header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"blocks\":[{\"type\":\"naoexiste\",\"props\":{}}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_blocks"));
        mockMvc.perform(post("/api/cms/pages").header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"pageSlug\":\"home\",\"title\":\"H2\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("page_slug_taken"));
    }

    @Test
    @DisplayName("CMS ligado: domínio válido 200; host do Meada 400; verify/start sem domínio 400 no_domain")
    void on_domain() throws Exception {
        String t = seedTenant("t@cms.dev", true);
        mockMvc.perform(post("/api/cms/site/verify/start").header("Authorization", "Bearer " + t))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("no_domain"));
        mockMvc.perform(put("/api/cms/site/domain").header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"domain\":\"minhaloja.com.br\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain").value("minhaloja.com.br"));
        mockMvc.perform(put("/api/cms/site/domain").header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"domain\":\"x.meadadigital.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_domain"));
    }

    @Test
    @DisplayName("sem token → 401")
    void noToken_401() throws Exception {
        mockMvc.perform(get("/api/cms/site")).andExpect(status().isUnauthorized());
    }
}
