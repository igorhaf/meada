package com.meada.whatsapp.messaging;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiSettingsRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AiSettingsRepository repository;

    private static final UUID COMPANY_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COMPANY_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void seedCompanies() {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY_A, "Empresa A", "empresa-a");
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY_B, "Empresa B", "empresa-b");
    }

    @Test
    @DisplayName("retorna settings do tenant correto, não vaza outro tenant")
    void findByCompany_returnsOwn_notOther() {
        jdbcTemplate.update(
            "insert into ai_settings (company_id, tone, system_rules, restrictions, handoff_triggers, model_provider) "
                + "values (?, 'casual', 'seja breve', 'nao fale de concorrentes', 'pedir humano', 'gemini')",
            COMPANY_A);
        jdbcTemplate.update(
            "insert into ai_settings (company_id, tone, model_provider) values (?, 'formal', 'openai')",
            COMPANY_B);

        Optional<AiSettings> a = repository.findByCompany(COMPANY_A);

        assertThat(a).isPresent();
        assertThat(a.get().tone()).isEqualTo("casual");
        assertThat(a.get().systemRules()).isEqualTo("seja breve");
        assertThat(a.get().restrictions()).isEqualTo("nao fale de concorrentes");
        assertThat(a.get().handoffTriggers()).isEqualTo("pedir humano");
        assertThat(a.get().modelProvider()).isEqualTo("gemini");
    }

    @Test
    @DisplayName("tenant sem ai_settings → Optional.empty()")
    void findByCompany_notConfigured_empty() {
        // nenhuma linha de ai_settings para COMPANY_A
        assertThat(repository.findByCompany(COMPANY_A)).isEmpty();
    }

    @Test
    @DisplayName("campos nullable preservados (não viram default no repo)")
    void findByCompany_nullableFieldsPreserved() {
        // só company_id e model_provider (NOT NULL default); resto null
        jdbcTemplate.update("insert into ai_settings (company_id) values (?)", COMPANY_A);

        Optional<AiSettings> a = repository.findByCompany(COMPANY_A);

        assertThat(a).isPresent();
        assertThat(a.get().tone()).isNull();
        assertThat(a.get().systemRules()).isNull();
        assertThat(a.get().restrictions()).isNull();
        assertThat(a.get().handoffTriggers()).isNull();
        assertThat(a.get().modelProvider()).isEqualTo("gemini");   // NOT NULL default
    }
}
