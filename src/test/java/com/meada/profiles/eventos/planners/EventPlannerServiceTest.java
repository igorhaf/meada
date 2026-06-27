package com.meada.profiles.eventos.planners;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.eventos.planners.EventPlannerService.PlannerInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o EventPlannerService (camada 8.2): create+audit, toggle, delete em uso → 409.
 */
class EventPlannerServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EventPlannerService service;

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("df000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'eventos')",
            COMPANY, "Eventos Teste", "eventos-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@eventos.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita event_planner_created")
    void create_persistsAndAudits() {
        EventPlanner p = service.create(COMPANY, USER, "Beatriz", "casamentos", null);
        assertThat(p.name()).isEqualTo("Beatriz");
        assertThat(p.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'event_planner_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        EventPlanner p = service.create(COMPANY, USER, "Rodrigo", "corporativo", null);
        EventPlanner off = service.toggle(COMPANY, USER, p.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de cerimonialista com proposta → PlannerInUseException (409)")
    void delete_inUse() {
        EventPlanner p = service.create(COMPANY, USER, "Beatriz", "casamentos", null);
        // planner_id é ON DELETE SET NULL — uso é checado por hasProposals(): seed de uma proposta.
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990270", "Cliente");
        jdbcTemplate.update(
            "insert into event_proposals (company_id, contact_id, planner_id, customer_name, status) "
                + "values (?, ?, ?, 'Cliente', 'rascunho')",
            COMPANY, contactId, p.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(PlannerInUseException.class);
    }
}
