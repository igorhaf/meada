package com.meada.whatsapp.messaging;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ServiceRepository repository;

    private static final UUID COMPANY_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COMPANY_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void seedCompanies() {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY_A, "Empresa A", "empresa-a");
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY_B, "Empresa B", "empresa-b");
    }

    private void insertService(UUID company, String name, Integer priceCents, boolean active, boolean deleted) {
        jdbcTemplate.update(
            "insert into services (company_id, name, description, price_cents, active, deleted_at) "
                + "values (?, ?, ?, ?, ?, ?)",
            company, name, "desc " + name, priceCents, active, deleted ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
    }

    @Test
    @DisplayName("retorna ativos do tenant, ordenados por name; não vaza outro tenant; preço null preservado")
    void findActive_happyPath_withIsolationAndOrdering() {
        insertService(COMPANY_A, "Corte", 5000, true, false);
        insertService(COMPANY_A, "Barba", null, true, false);   // sem preço
        insertService(COMPANY_B, "Massagem", 9000, true, false); // outro tenant

        List<Service> result = repository.findActiveByCompany(COMPANY_A);

        // só os 2 de A, ordenados por name (Barba antes de Corte), sem Massagem de B
        assertThat(result).extracting(Service::name).containsExactly("Barba", "Corte");
        assertThat(result.get(0).priceCents()).isNull();           // Barba sem preço
        assertThat(result.get(1).priceCents()).isEqualTo(5000);    // Corte
    }

    @Test
    @DisplayName("exclui soft-deleted e inativos")
    void findActive_excludesDeletedAndInactive() {
        insertService(COMPANY_A, "Ativo", 100, true, false);
        insertService(COMPANY_A, "Inativo", 200, false, false);
        insertService(COMPANY_A, "Deletado", 300, true, true);

        List<Service> result = repository.findActiveByCompany(COMPANY_A);

        assertThat(result).extracting(Service::name).containsExactly("Ativo");
    }

    @Test
    @DisplayName("tenant sem serviços → lista vazia")
    void findActive_empty() {
        assertThat(repository.findActiveByCompany(COMPANY_A)).isEmpty();
    }
}
