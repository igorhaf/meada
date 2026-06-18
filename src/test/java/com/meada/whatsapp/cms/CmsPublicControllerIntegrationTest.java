package com.meada.whatsapp.cms;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o CMS público (SM-M): /public/cms/by-slug e /public/cms/by-domain, SEM auth. Só serve a
 * página PUBLICADA; rascunho/inexistente → 404. Devolve só a view (title + blocks), sem campos
 * internos. Estas rotas não estão na allowlist do JwtFilter → passam sem token.
 */
class CmsPublicControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CmsService service;

    private static final UUID CO = UUID.fromString("c9000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')",
            CO, "Pública Co", "publica-co");
    }

    @Test
    @DisplayName("página publicada → by-slug 200 com title + blocks (sem campos internos)")
    void bySlug_published() throws Exception {
        jdbcTemplate.update(
            "update cms_pages set title='Olá', blocks='[{\"id\":\"b1\",\"type\":\"hero\",\"props\":{}}]'::jsonb where company_id=?",
            ensure());
        service.setPublished(CO, true);
        mockMvc.perform(get("/public/cms/by-slug/publica-co"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Olá"))
            .andExpect(jsonPath("$.blocks.length()").value(1))
            // a view pública NÃO expõe domain/published/companyId.
            .andExpect(jsonPath("$.published").doesNotExist())
            .andExpect(jsonPath("$.domain").doesNotExist());
    }

    @Test
    @DisplayName("rascunho → by-slug 404 page_not_found")
    void bySlug_draft_404() throws Exception {
        ensure(); // cria mas não publica
        mockMvc.perform(get("/public/cms/by-slug/publica-co"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("page_not_found"));
    }

    @Test
    @DisplayName("slug inexistente → 404")
    void bySlug_unknown_404() throws Exception {
        mockMvc.perform(get("/public/cms/by-slug/nao-existe"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("by-domain publicado → 200; rascunho/host desconhecido → 404")
    void byDomain() throws Exception {
        ensure();
        service.setDomain(CO, "minhaloja.com.br");
        // rascunho ainda → 404
        mockMvc.perform(get("/public/cms/by-domain").param("host", "minhaloja.com.br"))
            .andExpect(status().isNotFound());
        service.setPublished(CO, true);
        mockMvc.perform(get("/public/cms/by-domain").param("host", "minhaloja.com.br"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/public/cms/by-domain").param("host", "outro.com"))
            .andExpect(status().isNotFound());
    }

    /** Garante a linha de cms_pages e devolve o companyId. */
    private UUID ensure() {
        service.getOrCreate(CO);
        return CO;
    }
}
