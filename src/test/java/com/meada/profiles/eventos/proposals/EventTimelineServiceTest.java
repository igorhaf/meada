package com.meada.profiles.eventos.proposals;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.eventos.proposals.EventProposalService.ProposalLockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o CRONOGRAMA (camada 8.2) — A ESCAPADA da SM-P:
 * <ul>
 *   <li>add 3 marcos FORA de ordem de horário → leitura retorna ORDENADO por start_time (prova o
 *       cronograma ordenado);</li>
 *   <li>marco de cronograma NÃO altera total_cents da proposta (prova a separação dos dois tipos de
 *       sub-item — orçamento entra no total, cronograma não);</li>
 *   <li>add/edit/delete de marco sob trava de estado → proposal_locked.</li>
 * </ul>
 */
class EventTimelineServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EventProposalService service;
    @Autowired
    private EventProposalRepository repository;

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000003");
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'eventos')",
            COMPANY, "Eventos Tl", "eventos-tl");
        contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990290", "Marina");
    }

    private EventProposal openProposal() {
        return service.open(COMPANY, contactId, null, null, null, "casamento", null, 100, "Briefing", null);
    }

    @Test
    @DisplayName("3 marcos fora de ordem → leitura retorna ORDENADA por start_time (19:00, 20:00, 23:00)")
    void timeline_orderedByStartTime() {
        EventProposal p = openProposal();
        // inserção FORA de ordem: 23:00, 19:00, 20:00.
        service.addTimelineItem(COMPANY, p.id(), LocalTime.of(23, 0), "Festa", null);
        service.addTimelineItem(COMPANY, p.id(), LocalTime.of(19, 0), "Recepção", null);
        service.addTimelineItem(COMPANY, p.id(), LocalTime.of(20, 0), "Cerimônia", null);

        List<EventTimelineItem> timeline = service.get(COMPANY, p.id()).orElseThrow().timeline();
        assertThat(timeline).extracting(t -> t.startTime().toString())
            .containsExactly("19:00", "20:00", "23:00");
        assertThat(timeline).extracting(EventTimelineItem::title)
            .containsExactly("Recepção", "Cerimônia", "Festa");
    }

    @Test
    @DisplayName("adicionar marco de cronograma NÃO altera total_cents da proposta")
    void timeline_doesNotAffectTotal() {
        EventProposal p = openProposal();
        // 1 item de orçamento → total = 500000.
        service.addItem(COMPANY, p.id(), "Espaço", 1, 500000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
        // adicionar marcos NÃO mexe no total.
        service.addTimelineItem(COMPANY, p.id(), LocalTime.of(19, 0), "Recepção", null);
        service.addTimelineItem(COMPANY, p.id(), LocalTime.of(23, 0), "Festa", null);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
        // e o cronograma tem 2 marcos.
        assertThat(service.get(COMPANY, p.id()).orElseThrow().timeline()).hasSize(2);
    }

    @Test
    @DisplayName("add/edit/delete de marco sob trava de estado (fechada) → proposal_locked")
    void timeline_lockedByState() {
        EventProposal p = openProposal();
        UUID itemId = service.addTimelineItem(COMPANY, p.id(), LocalTime.of(19, 0), "Recepção", null).id();
        // trava: força 'fechada'.
        jdbcTemplate.update("update event_proposals set status = 'fechada' where id = ?", p.id());

        assertThatThrownBy(() -> service.addTimelineItem(COMPANY, p.id(), LocalTime.of(20, 0), "Cerimônia", null))
            .isInstanceOf(ProposalLockedException.class);
        assertThatThrownBy(() -> service.updateTimelineItem(COMPANY, p.id(), itemId, LocalTime.of(21, 0), true, null, null, false))
            .isInstanceOf(ProposalLockedException.class);
        assertThatThrownBy(() -> service.deleteTimelineItem(COMPANY, p.id(), itemId))
            .isInstanceOf(ProposalLockedException.class);
    }
}
