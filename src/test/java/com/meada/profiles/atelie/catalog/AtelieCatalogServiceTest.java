package com.meada.profiles.atelie.catalog;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.atelie.catalog.AtelieCatalogService.CatalogItemNotFoundException;
import com.meada.profiles.atelie.catalog.AtelieCatalogService.InvalidCatalogItemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AtelieCatalogService (onda 2, backlog #15): CRUD + onlyActive (fonte do autofill/upsell) +
 * validações (nome vazio / preço negativo → invalid_item).
 */
class AtelieCatalogServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AtelieCatalogService service;

    private static final UUID COMPANY = UUID.fromString("a7e00000-0000-0000-0000-000000000083");
    private static final UUID USER = UUID.fromString("d7e00000-0000-0000-0000-000000000083");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'atelie')",
            COMPANY, "Atelie Cat", "atelie-cat");
        // USER em auth.users + users (FK audit_log_user_id_fkey) — lição AuditLogger.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@atelie-cat.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create + update + delete; onlyActive filtra inativos (fonte do autofill/upsell)")
    void crud() {
        AtelieCatalogItem bordado = service.create(COMPANY, USER, "Bordado à mão", "acabamento", 15000, true, null);
        assertThat(bordado.category()).isEqualTo("acabamento");
        assertThat(bordado.unitPriceCents()).isEqualTo(15000);

        AtelieCatalogItem forro = service.create(COMPANY, USER, "Forro de cetim", "tecido", 8000, true, null);

        AtelieCatalogItem updated = service.update(COMPANY, USER, bordado.id(), null, null, false, 18000,
            false, null, false);
        assertThat(updated.unitPriceCents()).isEqualTo(18000);
        assertThat(updated.active()).isFalse();

        assertThat(service.list(COMPANY, false)).hasSize(2);
        assertThat(service.list(COMPANY, true)).extracting(AtelieCatalogItem::name)
            .containsExactly("Forro de cetim");

        service.delete(COMPANY, USER, forro.id());
        assertThat(service.list(COMPANY, false)).hasSize(1);
    }

    @Test
    @DisplayName("nome vazio ou preço negativo → InvalidCatalogItemException; id inexistente → not found")
    void invalid() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "  ", null, 1000, true, null))
            .isInstanceOf(InvalidCatalogItemException.class);
        assertThatThrownBy(() -> service.create(COMPANY, USER, "Renda", null, -1, true, null))
            .isInstanceOf(InvalidCatalogItemException.class);
        assertThatThrownBy(() -> service.delete(COMPANY, USER, UUID.randomUUID()))
            .isInstanceOf(CatalogItemNotFoundException.class);
    }
}
