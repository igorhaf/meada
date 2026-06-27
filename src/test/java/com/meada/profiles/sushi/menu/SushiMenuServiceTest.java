package com.meada.profiles.sushi.menu;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.sushi.menu.SushiMenuService.InvalidCategoryException;
import com.meada.profiles.sushi.menu.SushiMenuService.MenuItemInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o SushiMenuService (camada 7.1 / sushi funcional): create + audit, update parcial, categoria
 * validada contra a TABELA sushi_categories (não mais o enum), null category OK, delete em uso → 409.
 */
class SushiMenuServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SushiMenuService service;

    private static final UUID COMPANY = UUID.fromString("c5000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d5000000-0000-0000-0000-000000000001");
    private UUID categoryId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'sushi')",
            COMPANY, "Sushi Teste", "sushi-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sushi.dev', 'admin')",
            USER, COMPANY);
        categoryId = jdbcTemplate.queryForObject(
            "insert into sushi_categories (company_id, name) values (?, 'Hot rolls') returning id",
            UUID.class, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita sushi_menu_item_created")
    void create_persistsAndAudits() {
        SushiMenuItem item = service.create(COMPANY, USER, "Hot Filadélfia", "Cream + salmão",
            3200, categoryId.toString());
        assertThat(item.name()).isEqualTo("Hot Filadélfia");
        assertThat(item.priceCents()).isEqualTo(3200);
        assertThat(item.available()).isTrue();
        assertThat(item.category()).isEqualTo(categoryId.toString());

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'sushi_menu_item_created' and entity_id = ?",
            Long.class, item.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create sem categoria (null) → OK (item sem categoria é permitido)")
    void create_nullCategory() {
        SushiMenuItem item = service.create(COMPANY, USER, "Combinado", null, 9000, null);
        assertThat(item.category()).isNull();
    }

    @Test
    @DisplayName("create com categoria inexistente → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, 100, UUID.randomUUID().toString()))
            .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    @DisplayName("create com categoria inativa → InvalidCategoryException")
    void create_inactiveCategory() {
        UUID inactive = jdbcTemplate.queryForObject(
            "insert into sushi_categories (company_id, name, active) values (?, 'Off', false) returning id",
            UUID.class, COMPANY);
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, 100, inactive.toString()))
            .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só preço) preserva os demais campos")
    void update_partial() {
        SushiMenuItem item = service.create(COMPANY, USER, "Edamame", "Com sal", 1800, categoryId.toString());
        SushiMenuItem updated = service.update(COMPANY, USER, item.id(), null, null, 2000, null, false, null);
        assertThat(updated.priceCents()).isEqualTo(2000);
        assertThat(updated.name()).isEqualTo("Edamame");                  // preservado
        assertThat(updated.category()).isEqualTo(categoryId.toString());  // preservado
    }

    @Test
    @DisplayName("update com categoria inválida → invalid_category")
    void update_invalidCategory() {
        SushiMenuItem item = service.create(COMPANY, USER, "Edamame", null, 1800, categoryId.toString());
        assertThatThrownBy(() -> service.update(COMPANY, USER, item.id(), null, null, null,
                UUID.randomUUID().toString(), true, null))
            .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update clear category (categoryProvided + null) limpa a categoria")
    void update_clearCategory() {
        SushiMenuItem item = service.create(COMPANY, USER, "Edamame", null, 1800, categoryId.toString());
        SushiMenuItem updated = service.update(COMPANY, USER, item.id(), null, null, null, null, true, null);
        assertThat(updated.category()).isNull();
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        SushiMenuItem item = service.create(COMPANY, USER, "Sashimi", null, 4000, categoryId.toString());
        SushiMenuItem off = service.toggle(COMPANY, USER, item.id(), false);
        assertThat(off.available()).isFalse();
    }

    @Test
    @DisplayName("delete de item referenciado por pedido → MenuItemInUseException (409)")
    void delete_inUse() {
        SushiMenuItem item = service.create(COMPANY, USER, "Combinado 20", null, 9000, categoryId.toString());
        UUID statusId = jdbcTemplate.queryForObject(
            "insert into sushi_order_statuses (company_id, name, is_initial) values (?, 'Recebido', true) returning id",
            UUID.class, COMPANY);
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990001", "C");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, instance);
        UUID order = jdbcTemplate.queryForObject(
            "insert into sushi_orders (company_id, conversation_id, contact_id, status, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, ?, 9000, 9000, 'Rua X') returning id", UUID.class, COMPANY, conv, contact, statusId);
        jdbcTemplate.update("insert into sushi_order_items (order_id, menu_item_id, qtd, unit_price_cents, item_name_snapshot) "
            + "values (?, ?, 1, 9000, 'Combinado 20')", order, item.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, item.id()))
            .isInstanceOf(MenuItemInUseException.class);
    }
}
