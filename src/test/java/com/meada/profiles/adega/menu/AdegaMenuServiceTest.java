package com.meada.profiles.adega.menu;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.adega.menu.AdegaMenuService.MenuItemInUseException;
import com.meada.profiles.adega.menu.AdegaMenuService.OptionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AdegaMenuService (camada 8.9): create + audit, update parcial, toggle, delete em uso → 409,
 * e o CRUD de OPÇÕES (modifiers: addOption/updateOption/toggleOption/deleteOption). Clone do
 * ComidaMenuServiceTest, adaptado pras categorias de adega (vinhos/cervejas/...).
 */
class AdegaMenuServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AdegaMenuService service;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000091");
    private static final UUID USER = UUID.fromString("d8000000-0000-0000-0000-000000000091");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'adega')",
            COMPANY, "Adega Teste", "adega-teste");
        // USER precisa existir em users (FK audit_log_user_id_fkey) — senão o INSERT de audit
        // falha e, mesmo engolido, marca a transação @Transactional como rollback-only.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@adega.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita adega_menu_item_created")
    void create_persistsAndAudits() {
        AdegaMenuItem item = service.create(COMPANY, USER, "Cabernet Reserva", "Tinto seco 750ml",
            8900, "vinhos");
        assertThat(item.name()).isEqualTo("Cabernet Reserva");
        assertThat(item.priceCents()).isEqualTo(8900);
        assertThat(item.available()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'adega_menu_item_created' and entity_id = ?",
            Long.class, item.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, 100, "hot_rolls"))
            .isInstanceOf(AdegaMenuService.InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só preço) preserva os demais campos")
    void update_partial() {
        AdegaMenuItem item = service.create(COMPANY, USER, "Heineken", "Long neck 330ml", 900, "cervejas");
        AdegaMenuItem updated = service.update(COMPANY, USER, item.id(), null, null, 1100, null, null);
        assertThat(updated.priceCents()).isEqualTo(1100);
        assertThat(updated.name()).isEqualTo("Heineken");      // preservado
        assertThat(updated.category()).isEqualTo("cervejas");  // preservado
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        AdegaMenuItem item = service.create(COMPANY, USER, "Whisky 12 anos", null, 18000, "destilados");
        AdegaMenuItem off = service.toggle(COMPANY, USER, item.id(), false);
        assertThat(off.available()).isFalse();
    }

    @Test
    @DisplayName("delete de item referenciado por pedido → MenuItemInUseException (409)")
    void delete_inUse() {
        AdegaMenuItem item = service.create(COMPANY, USER, "Kit Espumante", null, 12000, "espumantes");
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
        // age_confirmed é NOT NULL — sempre true em pedido criado.
        UUID order = jdbcTemplate.queryForObject(
            "insert into adega_orders (company_id, conversation_id, contact_id, subtotal_cents, total_cents, delivery_address, age_confirmed) "
                + "values (?, ?, ?, 12000, 12000, 'Rua X', true) returning id", UUID.class, COMPANY, conv, contact);
        jdbcTemplate.update("insert into adega_order_items (order_id, menu_item_id, qtd, unit_price_cents, item_name_snapshot) "
            + "values (?, ?, 1, 12000, 'Kit Espumante')", order, item.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, item.id()))
            .isInstanceOf(MenuItemInUseException.class);
    }

    // ---- Opções (modifiers) -------------------------------------------------

    @Test
    @DisplayName("addOption → persiste a opção + audita adega_menu_option_created")
    void addOption_persistsAndAudits() {
        AdegaMenuItem item = service.create(COMPANY, USER, "Vodka Premium", null, 9000, "destilados");
        AdegaMenuOption opt = service.addOption(COMPANY, USER, item.id(), "Volume", "1L", 3000, 0);
        assertThat(opt.groupLabel()).isEqualTo("Volume");
        assertThat(opt.optionLabel()).isEqualTo("1L");
        assertThat(opt.priceDeltaCents()).isEqualTo(3000);
        assertThat(opt.available()).isTrue();

        List<AdegaMenuOption> options = service.listOptions(COMPANY, item.id());
        assertThat(options).hasSize(1);

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'adega_menu_option_created' and entity_id = ?",
            Long.class, opt.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("updateOption parcial (só delta) preserva os demais campos")
    void updateOption_partial() {
        AdegaMenuItem item = service.create(COMPANY, USER, "Gin Tônica", null, 5000, "destilados");
        AdegaMenuOption opt = service.addOption(COMPANY, USER, item.id(), "Temperatura", "Gelado", 0, 1);
        AdegaMenuOption updated = service.updateOption(COMPANY, USER, item.id(), opt.id(),
            null, null, 200, null, null);
        assertThat(updated.priceDeltaCents()).isEqualTo(200);
        assertThat(updated.groupLabel()).isEqualTo("Temperatura");  // preservado
        assertThat(updated.optionLabel()).isEqualTo("Gelado");      // preservado
    }

    @Test
    @DisplayName("toggleOption desliga available; deleteOption remove; opção inexistente → OptionNotFoundException")
    void toggleAndDeleteOption() {
        AdegaMenuItem item = service.create(COMPANY, USER, "Cerveja Artesanal", null, 1800, "cervejas");
        AdegaMenuOption opt = service.addOption(COMPANY, USER, item.id(), "Volume", "600ml", 500, 0);

        AdegaMenuOption off = service.toggleOption(COMPANY, USER, item.id(), opt.id(), false);
        assertThat(off.available()).isFalse();

        service.deleteOption(COMPANY, USER, item.id(), opt.id());
        assertThat(service.listOptions(COMPANY, item.id())).isEmpty();

        // deletar de novo (já não existe) → OptionNotFoundException.
        assertThatThrownBy(() -> service.deleteOption(COMPANY, USER, item.id(), opt.id()))
            .isInstanceOf(OptionNotFoundException.class);
    }
}
