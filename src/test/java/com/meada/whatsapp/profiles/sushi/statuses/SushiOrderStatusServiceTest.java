package com.meada.whatsapp.profiles.sushi.statuses;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.sushi.statuses.SushiOrderStatusService.DuplicateStatusException;
import com.meada.whatsapp.profiles.sushi.statuses.SushiOrderStatusService.InitialStatusUndeletableException;
import com.meada.whatsapp.profiles.sushi.statuses.SushiOrderStatusService.StatusInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o SushiOrderStatusService (camada 7.1 / sushi funcional): CRUD, ÚNICO inicial (criar/editar
 * outro inicial zera o anterior), duplicate_status, status_in_use, initial_status_undeletable,
 * notify_enabled/notify_text editáveis.
 */
class SushiOrderStatusServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SushiOrderStatusService service;

    private static final UUID COMPANY = UUID.fromString("c8b00000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d8b00000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'sushi')",
            COMPANY, "Sushi St", "sushi-st");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sushi-st.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create + notify fields editáveis + update")
    void crud() {
        SushiOrderStatusEntity st = service.create(COMPANY, USER, "Em preparo", 1, false, false,
            true, "Seu pedido entrou em preparo.", "#f90");
        assertThat(st.notifyEnabled()).isTrue();
        assertThat(st.notifyText()).isEqualTo("Seu pedido entrou em preparo.");
        assertThat(st.color()).isEqualTo("#f90");

        SushiOrderStatusEntity upd = service.update(COMPANY, USER, st.id(), null, null, null, null,
            false, null, true, null, false);
        assertThat(upd.notifyEnabled()).isFalse();
        assertThat(upd.notifyText()).isNull();   // clearNotifyText=true → null
    }

    @Test
    @DisplayName("único inicial: criar um 2º inicial zera o anterior")
    void singleInitial_create() {
        SushiOrderStatusEntity first = service.create(COMPANY, USER, "Recebido", 0, true, false, false, null, null);
        SushiOrderStatusEntity second = service.create(COMPANY, USER, "Aberto", 1, true, false, false, null, null);
        assertThat(second.isInitial()).isTrue();
        // o primeiro deixou de ser inicial.
        assertThat(service.get(COMPANY, first.id()).orElseThrow().isInitial()).isFalse();
        Long initials = jdbcTemplate.queryForObject(
            "select count(*) from sushi_order_statuses where company_id = ? and is_initial = true", Long.class, COMPANY);
        assertThat(initials).isEqualTo(1L);
    }

    @Test
    @DisplayName("único inicial: editar outro p/ inicial zera o anterior")
    void singleInitial_update() {
        SushiOrderStatusEntity first = service.create(COMPANY, USER, "Recebido", 0, true, false, false, null, null);
        SushiOrderStatusEntity other = service.create(COMPANY, USER, "Em preparo", 1, false, false, false, null, null);
        service.update(COMPANY, USER, other.id(), null, null, true, null, null, null, false, null, false);
        assertThat(service.get(COMPANY, first.id()).orElseThrow().isInitial()).isFalse();
        assertThat(service.get(COMPANY, other.id()).orElseThrow().isInitial()).isTrue();
    }

    @Test
    @DisplayName("nome duplicado → DuplicateStatusException")
    void duplicate() {
        service.create(COMPANY, USER, "Recebido", 0, true, false, false, null, null);
        assertThatThrownBy(() -> service.create(COMPANY, USER, "recebido", 1, false, false, false, null, null))
            .isInstanceOf(DuplicateStatusException.class);
    }

    @Test
    @DisplayName("delete do status inicial → InitialStatusUndeletableException")
    void initialUndeletable() {
        SushiOrderStatusEntity initial = service.create(COMPANY, USER, "Recebido", 0, true, false, false, null, null);
        assertThatThrownBy(() -> service.delete(COMPANY, USER, initial.id()))
            .isInstanceOf(InitialStatusUndeletableException.class);
    }

    @Test
    @DisplayName("delete de status com pedidos → StatusInUseException")
    void statusInUse() {
        SushiOrderStatusEntity initial = service.create(COMPANY, USER, "Recebido", 0, true, false, false, null, null);
        SushiOrderStatusEntity preparo = service.create(COMPANY, USER, "Em preparo", 1, false, false, false, null, null);
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990010", "C");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, instance);
        jdbcTemplate.update("insert into sushi_orders (company_id, conversation_id, contact_id, status, subtotal_cents, total_cents) "
            + "values (?, ?, ?, ?, 1000, 1000)", COMPANY, conv, contact, preparo.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, preparo.id()))
            .isInstanceOf(StatusInUseException.class);
        // o inicial sem pedidos ainda não pode ser deletado por ser inicial (testado acima); aqui só o in-use.
        assertThat(service.get(COMPANY, initial.id())).isPresent();
    }
}
