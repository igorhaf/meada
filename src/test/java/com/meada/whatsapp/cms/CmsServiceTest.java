package com.meada.whatsapp.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.cms.CmsService.DomainTakenException;
import com.meada.whatsapp.cms.CmsService.InvalidBlocksException;
import com.meada.whatsapp.cms.CmsService.InvalidDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o CmsService (SM-M): getOrCreate, save com normalização/validação de blocks, publish,
 * setDomain (válido/inválido/duplicado/limpar) e resolução pública (só publicada).
 */
class CmsServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CmsService service;
    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID CO_A = UUID.fromString("c1a00000-0000-0000-0000-000000000001");
    private static final UUID CO_B = UUID.fromString("c1a00000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')",
            CO_A, "Empresa A", "empresa-a");
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'oficina')",
            CO_B, "Empresa B", "empresa-b");
    }

    @Test
    @DisplayName("getOrCreate cria página vazia (rascunho, sem blocos, slug da empresa)")
    void getOrCreate_createsEmpty() {
        CmsPage page = service.getOrCreate(CO_A);
        assertThat(page.companyId()).isEqualTo(CO_A);
        assertThat(page.slug()).isEqualTo("empresa-a");
        assertThat(page.published()).isFalse();
        assertThat(page.blocks().isArray()).isTrue();
        assertThat(page.blocks()).isEmpty();
    }

    @Test
    @DisplayName("saveContent normaliza blocks: type válido + props + id gerado")
    void saveContent_normalizes() throws Exception {
        var blocks = objectMapper.readTree("""
            [ {"type":"hero","props":{"title":"Olá"}}, {"type":"text","props":{"body":"oi"}} ]
            """);
        CmsPage page = service.saveContent(CO_A, "Minha Página", blocks);
        assertThat(page.title()).isEqualTo("Minha Página");
        assertThat(page.blocks()).hasSize(2);
        assertThat(page.blocks().get(0).get("type").asText()).isEqualTo("hero");
        assertThat(page.blocks().get(0).get("id").asText()).isNotBlank(); // id gerado
        assertThat(page.blocks().get(0).get("props").get("title").asText()).isEqualTo("Olá");
    }

    @Test
    @DisplayName("saveContent com type inválido → InvalidBlocksException")
    void saveContent_invalidType() throws Exception {
        var blocks = objectMapper.readTree("[ {\"type\":\"naoexiste\",\"props\":{}} ]");
        assertThatThrownBy(() -> service.saveContent(CO_A, "X", blocks))
            .isInstanceOf(InvalidBlocksException.class);
    }

    @Test
    @DisplayName("saveContent com blocks não-array → InvalidBlocksException")
    void saveContent_notArray() throws Exception {
        var blocks = objectMapper.readTree("{\"type\":\"hero\"}");
        assertThatThrownBy(() -> service.saveContent(CO_A, "X", blocks))
            .isInstanceOf(InvalidBlocksException.class);
    }

    @Test
    @DisplayName("setPublished alterna o flag")
    void setPublished_toggles() {
        assertThat(service.setPublished(CO_A, true).published()).isTrue();
        assertThat(service.setPublished(CO_A, false).published()).isFalse();
    }

    @Test
    @DisplayName("setDomain válido grava; limpar com null/blank apaga")
    void setDomain_validAndClear() {
        assertThat(service.setDomain(CO_A, "MinhaEmpresa.com.BR").domain()).isEqualTo("minhaempresa.com.br"); // normaliza lower
        assertThat(service.setDomain(CO_A, "  ").domain()).isNull(); // limpa
    }

    @Test
    @DisplayName("setDomain inválido (host do meada ou formato ruim) → InvalidDomainException")
    void setDomain_invalid() {
        assertThatThrownBy(() -> service.setDomain(CO_A, "foo.meadadigital.com")).isInstanceOf(InvalidDomainException.class);
        assertThatThrownBy(() -> service.setDomain(CO_A, "sem-tld")).isInstanceOf(InvalidDomainException.class);
        assertThatThrownBy(() -> service.setDomain(CO_A, "http://x.com")).isInstanceOf(InvalidDomainException.class);
    }

    @Test
    @DisplayName("setDomain duplicado (dois tenants, mesmo host) → DomainTakenException")
    void setDomain_taken() {
        service.setDomain(CO_A, "loja.com.br");
        assertThatThrownBy(() -> service.setDomain(CO_B, "loja.com.br")).isInstanceOf(DomainTakenException.class);
    }

    @Test
    @DisplayName("publishedBySlug só retorna quando publicada")
    void publishedBySlug_onlyPublished() {
        service.getOrCreate(CO_A);
        assertThat(service.publishedBySlug("empresa-a")).isEmpty(); // rascunho
        service.setPublished(CO_A, true);
        assertThat(service.publishedBySlug("empresa-a")).isPresent();
        assertThat(service.publishedBySlug("nao-existe")).isEmpty();
    }

    @Test
    @DisplayName("publishedByDomain só retorna quando publicada e com domínio")
    void publishedByDomain_onlyPublished() {
        service.setDomain(CO_A, "minhaloja.com.br");
        assertThat(service.publishedByDomain("minhaloja.com.br")).isEmpty(); // rascunho
        service.setPublished(CO_A, true);
        assertThat(service.publishedByDomain("MinhaLoja.com.br")).isPresent(); // case-insensitive
        assertThat(service.publishedByDomain("outra.com")).isEmpty();
    }
}
