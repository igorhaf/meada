package com.meada.messaging;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FaqRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FaqRepository repository;

    private static final UUID COMPANY_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COMPANY_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void seedCompanies() {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY_A, "Empresa A", "empresa-a");
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY_B, "Empresa B", "empresa-b");
    }

    private void insertFaq(UUID company, String question, boolean active, boolean deleted) {
        jdbcTemplate.update(
            "insert into faqs (company_id, question, answer, active, deleted_at) values (?, ?, ?, ?, ?)",
            company, question, "resposta de " + question, active,
            deleted ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
    }

    @Test
    @DisplayName("retorna ativas do tenant na ordem de criação; não vaza outro tenant")
    void findActive_happyPath_withIsolationAndOrdering() {
        insertFaq(COMPANY_A, "Primeira", true, false);
        insertFaq(COMPANY_A, "Segunda", true, false);
        insertFaq(COMPANY_B, "DeOutroTenant", true, false);

        List<Faq> result = repository.findActiveByCompany(COMPANY_A);

        // ordem de criação (created_at, id), só de A
        assertThat(result).extracting(Faq::question).containsExactly("Primeira", "Segunda");
        assertThat(result.get(0).answer()).isEqualTo("resposta de Primeira");
    }

    @Test
    @DisplayName("exclui soft-deleted e inativas")
    void findActive_excludesDeletedAndInactive() {
        insertFaq(COMPANY_A, "Ativa", true, false);
        insertFaq(COMPANY_A, "Inativa", false, false);
        insertFaq(COMPANY_A, "Deletada", true, true);

        List<Faq> result = repository.findActiveByCompany(COMPANY_A);

        assertThat(result).extracting(Faq::question).containsExactly("Ativa");
    }

    @Test
    @DisplayName("tenant sem FAQs → lista vazia")
    void findActive_empty() {
        assertThat(repository.findActiveByCompany(COMPANY_A)).isEmpty();
    }
}
