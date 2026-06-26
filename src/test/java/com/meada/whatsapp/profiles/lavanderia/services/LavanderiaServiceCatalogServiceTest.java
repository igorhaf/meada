package com.meada.whatsapp.profiles.lavanderia.services;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.lavanderia.services.LavanderiaServiceCatalogService.OptionNotFoundException;
import com.meada.whatsapp.profiles.lavanderia.services.LavanderiaServiceCatalogService.ServiceInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o LavanderiaServiceCatalogService (camada 8.10): create + audit, update parcial, toggle,
 * delete em uso → 409 service_in_use, e o CRUD de OPÇÕES. Clone do FloriculturaCatalogServiceTest +
 * turnaround_days/care_instructions.
 */
class LavanderiaServiceCatalogServiceTest extends AbstractIntegrationTest {

    @Autowired
    private LavanderiaServiceCatalogService service;

    private static final UUID COMPANY = UUID.fromString("1a000000-0000-0000-0000-000000000071");
    private static final UUID USER = UUID.fromString("1b000000-0000-0000-0000-000000000071");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'lavanderia')",
            COMPANY, "Lavanderia Teste", "lavanderia-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@lavanderia.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita lavanderia_service_created")
    void create_persistsAndAudits() {
        LavanderiaService item = service.create(COMPANY, USER, "Lavar camisa", "Por peça", 800,
            "lavar", 2, "lavagem a frio");
        assertThat(item.name()).isEqualTo("Lavar camisa");
        assertThat(item.priceCents()).isEqualTo(800);
        assertThat(item.turnaroundDays()).isEqualTo(2);
        assertThat(item.careInstructions()).isEqualTo("lavagem a frio");
        assertThat(item.available()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'lavanderia_service_created' and entity_id = ?",
            Long.class, item.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, 100, "tingir", 1, null))
            .isInstanceOf(LavanderiaServiceCatalogService.InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só preço) preserva os demais campos")
    void update_partial() {
        LavanderiaService item = service.create(COMPANY, USER, "Passar calça", null, 500, "passar", 1, null);
        LavanderiaService updated = service.update(COMPANY, USER, item.id(), null, null, 700, null, null, null, null);
        assertThat(updated.priceCents()).isEqualTo(700);
        assertThat(updated.name()).isEqualTo("Passar calça");
        assertThat(updated.category()).isEqualTo("passar");
        assertThat(updated.turnaroundDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        LavanderiaService item = service.create(COMPANY, USER, "Lavagem a seco", null, 3000, "lavagem_seco", 3, null);
        LavanderiaService off = service.toggle(COMPANY, USER, item.id(), false);
        assertThat(off.available()).isFalse();
    }

    @Test
    @DisplayName("delete de serviço referenciado por pedido → ServiceInUseException (409)")
    void delete_inUse() {
        LavanderiaService item = service.create(COMPANY, USER, "Edredom casal", null, 4500, "edredom_pesados", 4, null);
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990171", "C");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, instance);
        UUID order = jdbcTemplate.queryForObject(
            "insert into lavanderia_orders (company_id, conversation_id, contact_id, subtotal_cents, total_cents, "
                + "delivery_address, collect_date, delivery_date, period) "
                + "values (?, ?, ?, 4500, 4500, 'Rua X', current_date + 1, current_date + 5, 'manha') returning id",
            UUID.class, COMPANY, conv, contact);
        jdbcTemplate.update("insert into lavanderia_order_items (order_id, service_id, qty, unit_price_cents, "
            + "service_name_snapshot, turnaround_snapshot) values (?, ?, 1, 4500, 'Edredom casal', 4)", order, item.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, item.id()))
            .isInstanceOf(ServiceInUseException.class);
    }

    // ---- Opções -------------------------------------------------------------

    @Test
    @DisplayName("addOption → persiste a opção + audita lavanderia_service_option_created")
    void addOption_persistsAndAudits() {
        LavanderiaService item = service.create(COMPANY, USER, "Lavar e passar", null, 1200, "lavar_passar", 2, null);
        LavanderiaServiceOption opt = service.addOption(COMPANY, USER, item.id(), "Acabamento", "Engomar", 300, 0);
        assertThat(opt.groupLabel()).isEqualTo("Acabamento");
        assertThat(opt.optionLabel()).isEqualTo("Engomar");
        assertThat(opt.priceDeltaCents()).isEqualTo(300);
        assertThat(opt.available()).isTrue();

        List<LavanderiaServiceOption> options = service.listOptions(COMPANY, item.id());
        assertThat(options).hasSize(1);

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'lavanderia_service_option_created' and entity_id = ?",
            Long.class, opt.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("updateOption parcial (só delta) preserva os demais campos")
    void updateOption_partial() {
        LavanderiaService item = service.create(COMPANY, USER, "Lavar", null, 800, "lavar", 1, null);
        LavanderiaServiceOption opt = service.addOption(COMPANY, USER, item.id(), "Cuidado", "Hipoalergênico", 200, 1);
        LavanderiaServiceOption updated = service.updateOption(COMPANY, USER, item.id(), opt.id(),
            null, null, 400, null, null);
        assertThat(updated.priceDeltaCents()).isEqualTo(400);
        assertThat(updated.groupLabel()).isEqualTo("Cuidado");
        assertThat(updated.optionLabel()).isEqualTo("Hipoalergênico");
    }

    @Test
    @DisplayName("toggleOption desliga; deleteOption remove; opção inexistente → OptionNotFoundException")
    void toggleAndDeleteOption() {
        LavanderiaService item = service.create(COMPANY, USER, "Passar", null, 500, "passar", 1, null);
        LavanderiaServiceOption opt = service.addOption(COMPANY, USER, item.id(), "Acabamento", "Vapor", 150, 0);

        LavanderiaServiceOption off = service.toggleOption(COMPANY, USER, item.id(), opt.id(), false);
        assertThat(off.available()).isFalse();

        service.deleteOption(COMPANY, USER, item.id(), opt.id());
        assertThat(service.listOptions(COMPANY, item.id())).isEmpty();

        assertThatThrownBy(() -> service.deleteOption(COMPANY, USER, item.id(), opt.id()))
            .isInstanceOf(OptionNotFoundException.class);
    }
}
