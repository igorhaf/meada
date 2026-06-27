package com.meada.profiles.sushi.categories;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.sushi.categories.SushiCategoryService.CategoryInUseException;
import com.meada.profiles.sushi.categories.SushiCategoryService.DuplicateCategoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o SushiCategoryService (camada 7.1 / sushi funcional): CRUD + audit, duplicate_category,
 * category_in_use (categoria com itens de cardápio).
 */
class SushiCategoryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SushiCategoryService service;

    private static final UUID COMPANY = UUID.fromString("c8a00000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d8a00000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'sushi')",
            COMPANY, "Sushi Cat", "sushi-cat");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sushi-cat.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create → persiste + audita; toggle; update")
    void crud() {
        SushiCategoryEntity cat = service.create(COMPANY, USER, "Hot rolls", 1, true);
        assertThat(cat.name()).isEqualTo("Hot rolls");
        assertThat(cat.sortOrder()).isEqualTo(1);
        assertThat(cat.active()).isTrue();

        SushiCategoryEntity updated = service.update(COMPANY, USER, cat.id(), "Hot Rolls Premium", 2, null);
        assertThat(updated.name()).isEqualTo("Hot Rolls Premium");
        assertThat(updated.sortOrder()).isEqualTo(2);

        SushiCategoryEntity off = service.toggle(COMPANY, USER, cat.id(), false);
        assertThat(off.active()).isFalse();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'sushi_category_created' and entity_id = ?",
            Long.class, cat.id());
        assertThat(audit).isEqualTo(1L);

        assertThat(service.list(COMPANY, false)).hasSize(1);
    }

    @Test
    @DisplayName("create com nome duplicado (case-insensitive) → DuplicateCategoryException")
    void duplicate() {
        service.create(COMPANY, USER, "Sashimi", 0, true);
        assertThatThrownBy(() -> service.create(COMPANY, USER, "sashimi", 0, true))
            .isInstanceOf(DuplicateCategoryException.class);
    }

    @Test
    @DisplayName("delete de categoria com itens → CategoryInUseException")
    void deleteInUse() {
        SushiCategoryEntity cat = service.create(COMPANY, USER, "Bebidas", 0, true);
        jdbcTemplate.update("insert into sushi_menu_items (company_id, name, price_cents, category) values (?, 'Coca', 600, ?)",
            COMPANY, cat.id());
        assertThatThrownBy(() -> service.delete(COMPANY, USER, cat.id()))
            .isInstanceOf(CategoryInUseException.class);
    }

    @Test
    @DisplayName("delete de categoria sem itens → OK")
    void deleteOk() {
        SushiCategoryEntity cat = service.create(COMPANY, USER, "Sobremesas", 0, true);
        service.delete(COMPANY, USER, cat.id());
        assertThat(service.get(COMPANY, cat.id())).isEmpty();
    }
}
