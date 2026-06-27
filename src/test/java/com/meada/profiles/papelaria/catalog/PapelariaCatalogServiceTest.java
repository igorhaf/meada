package com.meada.profiles.papelaria.catalog;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogService.CatalogItemInUseException;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogService.OptionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o PapelariaCatalogService (camada 8.15 / perfil papelaria): create + audit, validação de
 * categoria, update parcial (incl. made_to_order/lead_time da escapada), toggle, delete em uso → 409
 * catalog_item_in_use, e o CRUD de OPÇÕES. Clone do PadariaMenuServiceTest (camada 8.8) — menu→catalog,
 * allergens→specs.
 */
class PapelariaCatalogServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PapelariaCatalogService service;

    private static final UUID COMPANY = UUID.fromString("c8150000-0000-0000-0000-000000000071");
    private static final UUID USER = UUID.fromString("d8150000-0000-0000-0000-000000000071");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'papelaria')",
            COMPANY, "Papelaria Teste", "papelaria-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@papelaria.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita papelaria_catalog_item_created")
    void create_persistsAndAudits() {
        PapelariaCatalogItem item = service.create(COMPANY, USER, "Cartão de Visita", "Couché", 100, "cartoes",
            false, null, "Couché 300g");
        assertThat(item.name()).isEqualTo("Cartão de Visita");
        assertThat(item.priceCents()).isEqualTo(100);
        assertThat(item.madeToOrder()).isFalse();
        assertThat(item.specs()).isEqualTo("Couché 300g");
        assertThat(item.available()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'papelaria_catalog_item_created' and entity_id = ?",
            Long.class, item.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create de convite SOB ENCOMENDA com lead próprio → persiste made_to_order + lead")
    void create_madeToOrderWithLead() {
        PapelariaCatalogItem convite = service.create(COMPANY, USER, "Convite Casamento", null, 800,
            "convites", true, 7, null);
        assertThat(convite.madeToOrder()).isTrue();
        assertThat(convite.leadTimeDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, 100, "banner_gigante", false, null, null))
            .isInstanceOf(PapelariaCatalogService.InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só preço) preserva os demais campos")
    void update_partial() {
        PapelariaCatalogItem item = service.create(COMPANY, USER, "Adesivo Redondo", "Vinil", 600, "adesivos", false, null, null);
        PapelariaCatalogItem updated = service.update(COMPANY, USER, item.id(), null, null, 700, null,
            null, null, false, null, null);
        assertThat(updated.priceCents()).isEqualTo(700);
        assertThat(updated.name()).isEqualTo("Adesivo Redondo");   // preservado
        assertThat(updated.category()).isEqualTo("adesivos");      // preservado
    }

    @Test
    @DisplayName("update clearLeadTime zera lead_time_days (volta ao default da config)")
    void update_clearLeadTime() {
        PapelariaCatalogItem convite = service.create(COMPANY, USER, "Save the Date", null, 500, "save_the_date", true, 5, null);
        assertThat(convite.leadTimeDays()).isEqualTo(5);
        PapelariaCatalogItem cleared = service.update(COMPANY, USER, convite.id(), null, null, null, null,
            null, null, true, null, null);
        assertThat(cleared.leadTimeDays()).isNull();
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        PapelariaCatalogItem item = service.create(COMPANY, USER, "Tag de Embalagem", null, 200, "embalagens", false, null, null);
        PapelariaCatalogItem off = service.toggle(COMPANY, USER, item.id(), false);
        assertThat(off.available()).isFalse();
    }

    @Test
    @DisplayName("delete de item referenciado por pedido → CatalogItemInUseException (409)")
    void delete_inUse() {
        PapelariaCatalogItem item = service.create(COMPANY, USER, "Convite Luxo", null, 1500, "convites", true, 10, null);
        // Semeia uma conversa+contato+pedido+order_item referenciando o item.
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990071", "C");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, instance);
        UUID order = jdbcTemplate.queryForObject(
            "insert into papelaria_orders (company_id, conversation_id, contact_id, fulfillment, subtotal_cents, total_cents, "
                + "pickup_or_delivery_date, delivery_period) "
                + "values (?, ?, ?, 'retirada', 150000, 150000, current_date + 10, 'manha') returning id",
            UUID.class, COMPANY, conv, contact);
        jdbcTemplate.update("insert into papelaria_order_items (order_id, catalog_item_id, quantity, unit_price_cents, item_name_snapshot, made_to_order_snapshot) "
            + "values (?, ?, 100, 1500, 'Convite Luxo', true)", order, item.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, item.id()))
            .isInstanceOf(CatalogItemInUseException.class);
    }

    // ---- Opções -------------------------------------------------------------

    @Test
    @DisplayName("addOption → persiste a opção + audita papelaria_catalog_option_created; cache invalidado implícito")
    void addOption_persistsAndAudits() {
        PapelariaCatalogItem item = service.create(COMPANY, USER, "Convite", null, 700, "convites", true, 7, null);
        PapelariaCatalogOption opt = service.addOption(COMPANY, USER, item.id(), "Papel", "Perolado", 500, 0);
        assertThat(opt.groupLabel()).isEqualTo("Papel");
        assertThat(opt.optionLabel()).isEqualTo("Perolado");
        assertThat(opt.priceDeltaCents()).isEqualTo(500);
        assertThat(opt.available()).isTrue();

        List<PapelariaCatalogOption> options = service.listOptions(COMPANY, item.id());
        assertThat(options).hasSize(1);

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'papelaria_catalog_option_created' and entity_id = ?",
            Long.class, opt.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("updateOption parcial (só delta) preserva os demais campos")
    void updateOption_partial() {
        PapelariaCatalogItem item = service.create(COMPANY, USER, "Convite Grande", null, 900, "convites", true, 7, null);
        PapelariaCatalogOption opt = service.addOption(COMPANY, USER, item.id(), "Acabamento", "Verniz", 800, 1);
        PapelariaCatalogOption updated = service.updateOption(COMPANY, USER, item.id(), opt.id(),
            null, null, 1000, null, null);
        assertThat(updated.priceDeltaCents()).isEqualTo(1000);
        assertThat(updated.groupLabel()).isEqualTo("Acabamento");   // preservado
        assertThat(updated.optionLabel()).isEqualTo("Verniz");      // preservado
    }

    @Test
    @DisplayName("toggleOption desliga available; deleteOption remove; opção inexistente → OptionNotFoundException")
    void toggleAndDeleteOption() {
        PapelariaCatalogItem item = service.create(COMPANY, USER, "Convite Festa", null, 1200, "convites", true, 7, null);
        PapelariaCatalogOption opt = service.addOption(COMPANY, USER, item.id(), "Cor", "Dourado", 300, 0);

        PapelariaCatalogOption off = service.toggleOption(COMPANY, USER, item.id(), opt.id(), false);
        assertThat(off.available()).isFalse();

        service.deleteOption(COMPANY, USER, item.id(), opt.id());
        assertThat(service.listOptions(COMPANY, item.id())).isEmpty();

        // deletar de novo (já não existe) → OptionNotFoundException.
        assertThatThrownBy(() -> service.deleteOption(COMPANY, USER, item.id(), opt.id()))
            .isInstanceOf(OptionNotFoundException.class);
    }
}
