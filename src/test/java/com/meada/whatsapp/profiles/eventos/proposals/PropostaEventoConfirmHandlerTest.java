package com.meada.whatsapp.profiles.eventos.proposals;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PropostaEventoConfirmHandler (camada 8.2): cria a proposta em 'rascunho' pela tag,
 * resolvendo o snapshot do contato da conversa; sem tag → empty; briefing vazio → empty.
 */
class PropostaEventoConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PropostaEventoConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000004");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'eventos')",
            COMPANY, "Eventos H1", "eventos-h1");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990291", "Marina Costa");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("tag <proposta_evento> cria proposta em rascunho com snapshot do contato")
    void parseAndCreate_createsRascunho() {
        String aiText = "Perfeito, registrei o briefing do seu casamento!\n"
            + "<proposta_evento>{\"event_type\":\"casamento\",\"event_date\":\"2026-12-20\","
            + "\"guest_count\":150,\"briefing\":\"Casamento ao ar livre\",\"planner_id\":null,\"notes\":null}"
            + "</proposta_evento>";

        Optional<EventProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isPresent();
        EventProposal p = o.get();
        assertThat(p.status()).isEqualTo("rascunho");
        assertThat(p.totalCents()).isZero();
        assertThat(p.customerName()).isEqualTo("Marina Costa");
        assertThat(p.eventType()).isEqualTo("casamento");
        assertThat(p.guestCount()).isEqualTo(150);
        assertThat(p.eventDate().toString()).isEqualTo("2026-12-20");
        assertThat(p.items()).isEmpty();
        assertThat(p.timeline()).isEmpty();
    }

    @Test
    @DisplayName("sem tag → Optional.empty (nada criado)")
    void parseAndCreate_noTag() {
        Optional<EventProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId,
            "Claro, posso te ajudar com seu evento!");
        assertThat(o).isEmpty();
    }

    @Test
    @DisplayName("briefing vazio na tag → Optional.empty (não cria)")
    void parseAndCreate_emptyBriefing() {
        String aiText = "Vou abrir aqui.\n"
            + "<proposta_evento>{\"event_type\":\"casamento\",\"briefing\":\"\"}</proposta_evento>";
        Optional<EventProposal> o = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(o).isEmpty();
        Long n = jdbcTemplate.queryForObject("select count(*) from event_proposals where company_id = ?",
            Long.class, COMPANY);
        assertThat(n).isZero();
    }
}
