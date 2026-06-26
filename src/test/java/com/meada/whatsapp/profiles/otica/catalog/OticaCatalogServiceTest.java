package com.meada.whatsapp.profiles.otica.catalog;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.otica.catalog.OticaCatalogService.CatalogItemInUseException;
import com.meada.whatsapp.profiles.otica.catalog.OticaCatalogService.InvalidCategoryException;
import com.meada.whatsapp.profiles.otica.catalog.OticaCatalogService.OptionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o OticaCatalogService (camada 8.12, FLUXO B): create + audit (com made_to_order/lead_time),
 * categoria inválida, update parcial, delete em uso → 409, e o CRUD de OPÇÕES (tipo de
 * lente/tratamento). Clone do FloriculturaCatalogServiceTest + os campos da ótica.
 */
class OticaCatalogServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OticaCatalogService service;

    private static final UUID COMPANY = UUID.fromString("ca120000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("da120000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'otica')",
            COMPANY, "Ótica Cat", "otica-cat");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@otica-cat.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create armação sob encomenda c/ lead → persiste made_to_order/lead + audita")
    void create_madeToOrder() {
        OticaCatalogItem item = service.create(COMPANY, USER, "Armação X", "Acetato", 19900, "armacoes", true, 5);
        assertThat(item.priceCents()).isEqualTo(19900);
        assertThat(item.madeToOrder()).isTrue();
        assertThat(item.leadTimeDays()).isEqualTo(5);
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'otica_catalog_item_created' and entity_id = ?",
            Long.class, item.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create acessório (não sob encomenda, lead null) → persiste made_to_order=false / leadTimeDays null")
    void create_accessory() {
        OticaCatalogItem item = service.create(COMPANY, USER, "Estojo", null, 1500, "acessorios", false, null);
        assertThat(item.madeToOrder()).isFalse();
        assertThat(item.leadTimeDays()).isNull();
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, 100, "hot_rolls", false, null))
            .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    @DisplayName("CRUD opção: add → list → update → delete; deleteOption inexistente → 404")
    void optionCrud() {
        OticaCatalogItem lente = service.create(COMPANY, USER, "Lente X", null, 9900, "lentes", true, null);
        OticaCatalogOption opt = service.addOption(COMPANY, USER, lente.id(), "Tipo de lente", "Multifocal", 15000, 0);
        assertThat(opt.optionLabel()).isEqualTo("Multifocal");

        List<OticaCatalogOption> opts = service.listOptions(COMPANY, lente.id());
        assertThat(opts).hasSize(1);

        OticaCatalogOption updated = service.updateOption(COMPANY, USER, lente.id(), opt.id(),
            null, null, 18000, null, null);
        assertThat(updated.priceDeltaCents()).isEqualTo(18000);

        service.deleteOption(COMPANY, USER, lente.id(), opt.id());
        assertThat(service.listOptions(COMPANY, lente.id())).isEmpty();

        assertThatThrownBy(() -> service.deleteOption(COMPANY, USER, lente.id(), UUID.randomUUID()))
            .isInstanceOf(OptionNotFoundException.class);
    }

    @Test
    @DisplayName("delete de item com order_item → CatalogItemInUseException (409)")
    void delete_inUse() {
        OticaCatalogItem item = service.create(COMPANY, USER, "Armação Z", null, 10000, "armacoes", true, 3);
        UUID instance = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990013", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("insert into otica_orders (id, company_id, conversation_id, contact_id, subtotal_cents, "
            + "total_cents, prescription_pending) values (?, ?, ?, ?, 10000, 10000, true)",
            orderId, COMPANY, conversationId, contactId);
        jdbcTemplate.update("insert into otica_order_items (order_id, catalog_item_id, qtd, unit_price_cents, item_name_snapshot) "
            + "values (?, ?, 1, 10000, 'Armação Z')", orderId, item.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, item.id()))
            .isInstanceOf(CatalogItemInUseException.class);
    }
}
