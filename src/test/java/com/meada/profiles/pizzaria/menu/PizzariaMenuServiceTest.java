package com.meada.profiles.pizzaria.menu;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.pizzaria.menu.PizzariaMenuService.MenuItemInUseException;
import com.meada.profiles.pizzaria.menu.PizzariaMenuService.OptionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o PizzariaMenuService (camada 8.6): create + audit, update parcial, toggle, delete em uso → 409,
 * e o CRUD de OPÇÕES (ESCAPADA 2: addOption/updateOption/toggleOption/deleteOption). Clone do
 * ComidaMenuServiceTest, adaptado às categorias do pizzaria.
 */
class PizzariaMenuServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PizzariaMenuService service;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000091");
    private static final UUID USER = UUID.fromString("d8000000-0000-0000-0000-000000000091");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pizzaria')",
            COMPANY, "Pizzaria Teste", "pizzaria-teste");
        // USER precisa existir em users (FK audit_log_user_id_fkey) — senão o INSERT de audit
        // falha e, mesmo engolido, marca a transação @Transactional como rollback-only.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@pizzaria.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita pizzaria_menu_item_created")
    void create_persistsAndAudits() {
        PizzariaMenuItem item = service.create(COMPANY, USER, "Portuguesa", "Presunto, ovo, cebola",
            5200, "pizzas_salgadas");
        assertThat(item.name()).isEqualTo("Portuguesa");
        assertThat(item.priceCents()).isEqualTo(5200);
        assertThat(item.available()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'pizzaria_menu_item_created' and entity_id = ?",
            Long.class, item.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, 100, "hot_rolls"))
            .isInstanceOf(PizzariaMenuService.InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só preço) preserva os demais campos")
    void update_partial() {
        PizzariaMenuItem item = service.create(COMPANY, USER, "Coca-Cola", "Lata 350ml", 600, "bebidas");
        PizzariaMenuItem updated = service.update(COMPANY, USER, item.id(), null, null, 700, null, null);
        assertThat(updated.priceCents()).isEqualTo(700);
        assertThat(updated.name()).isEqualTo("Coca-Cola");      // preservado
        assertThat(updated.category()).isEqualTo("bebidas");    // preservado
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        PizzariaMenuItem item = service.create(COMPANY, USER, "Calabresa", null, 4900, "pizzas_salgadas");
        PizzariaMenuItem off = service.toggle(COMPANY, USER, item.id(), false);
        assertThat(off.available()).isFalse();
    }

    @Test
    @DisplayName("delete de item referenciado por pedido → MenuItemInUseException (409)")
    void delete_inUse() {
        PizzariaMenuItem item = service.create(COMPANY, USER, "Combo Família", null, 9000, "combos");
        // Semeia uma conversa+contato+pedido+order_item referenciando o item.
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990091", "C");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, instance);
        UUID order = jdbcTemplate.queryForObject(
            "insert into pizzaria_orders (company_id, conversation_id, contact_id, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, 9000, 9000, 'Rua X') returning id", UUID.class, COMPANY, conv, contact);
        jdbcTemplate.update("insert into pizzaria_order_items (order_id, menu_item_id, qtd, unit_price_cents, item_name_snapshot) "
            + "values (?, ?, 1, 9000, 'Combo Família')", order, item.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, item.id()))
            .isInstanceOf(MenuItemInUseException.class);
    }

    // ---- Opções (ESCAPADA 2) ------------------------------------------------

    @Test
    @DisplayName("addOption → persiste a opção + audita pizzaria_menu_option_created")
    void addOption_persistsAndAudits() {
        PizzariaMenuItem item = service.create(COMPANY, USER, "Marguerita", null, 4700, "pizzas_salgadas");
        PizzariaMenuOption opt = service.addOption(COMPANY, USER, item.id(), "Tamanho", "Grande", 1200, 0);
        assertThat(opt.groupLabel()).isEqualTo("Tamanho");
        assertThat(opt.optionLabel()).isEqualTo("Grande");
        assertThat(opt.priceDeltaCents()).isEqualTo(1200);
        assertThat(opt.available()).isTrue();

        List<PizzariaMenuOption> options = service.listOptions(COMPANY, item.id());
        assertThat(options).hasSize(1);

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'pizzaria_menu_option_created' and entity_id = ?",
            Long.class, opt.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("updateOption parcial (só delta) preserva os demais campos")
    void updateOption_partial() {
        PizzariaMenuItem item = service.create(COMPANY, USER, "Quatro Queijos", null, 5500, "pizzas_salgadas");
        PizzariaMenuOption opt = service.addOption(COMPANY, USER, item.id(), "Borda", "Catupiry", 800, 1);
        PizzariaMenuOption updated = service.updateOption(COMPANY, USER, item.id(), opt.id(),
            null, null, 1000, null, null);
        assertThat(updated.priceDeltaCents()).isEqualTo(1000);
        assertThat(updated.groupLabel()).isEqualTo("Borda");        // preservado
        assertThat(updated.optionLabel()).isEqualTo("Catupiry");    // preservado
    }

    @Test
    @DisplayName("toggleOption desliga available; deleteOption remove; opção inexistente → OptionNotFoundException")
    void toggleAndDeleteOption() {
        PizzariaMenuItem item = service.create(COMPANY, USER, "Brigadeiro", null, 4000, "pizzas_doces");
        PizzariaMenuOption opt = service.addOption(COMPANY, USER, item.id(), "Tamanho", "Média", 0, 0);

        PizzariaMenuOption off = service.toggleOption(COMPANY, USER, item.id(), opt.id(), false);
        assertThat(off.available()).isFalse();

        service.deleteOption(COMPANY, USER, item.id(), opt.id());
        assertThat(service.listOptions(COMPANY, item.id())).isEmpty();

        // deletar de novo (já não existe) → OptionNotFoundException.
        assertThatThrownBy(() -> service.deleteOption(COMPANY, USER, item.id(), opt.id()))
            .isInstanceOf(OptionNotFoundException.class);
    }
}
