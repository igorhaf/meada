package com.meada.profiles.otica.appointments;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o ExameOticaConfirmHandler (camada 8.12, FLUXO A): parse OK + create (customer_name resolvido
 * do contato), conflito → empty, sem tag → empty.
 */
class ExameOticaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private ExameOticaConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("ca120000-0000-0000-0000-000000000005");
    private UUID profId;
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'otica')",
            COMPANY, "Ótica Handler", "otica-handler");
        profId = UUID.randomUUID();
        jdbcTemplate.update("insert into otica_professionals (id, company_id, name) values (?, ?, 'Dra. A')", profId, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990016", "Maria");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("tag <exame_otica> válida → cria exame agendado (customer_name do contato)")
    void parseAndCreate_ok() {
        String aiText = "Perfeito, Maria! Marquei seu exame pra 01/07 às 15h.\n"
            + "<exame_otica>{\"professional_id\":\"" + profId + "\",\"date\":\"2026-07-01\","
            + "\"start_time\":\"15:00\",\"notes\":null}</exame_otica>";
        Optional<OticaExamAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(a).isPresent();
        assertThat(a.get().status()).isEqualTo("agendado");
        assertThat(a.get().professionalId()).isEqualTo(profId);
        assertThat(a.get().customerName()).isEqualTo("Maria");
    }

    @Test
    @DisplayName("hasTag/stripTag removem a tag do texto enviado ao cliente")
    void stripTag() {
        String aiText = "Marcado!\n<exame_otica>{\"professional_id\":\"" + profId + "\",\"date\":\"2026-07-01\","
            + "\"start_time\":\"15:00\"}</exame_otica>";
        assertThat(handler.hasTag(aiText)).isTrue();
        assertThat(handler.stripTag(aiText)).isEqualTo("Marcado!");
    }

    @Test
    @DisplayName("conflito de slot (mesmo prof/horário já ocupado) → Optional.empty + 1 exame")
    void parseAndCreate_conflict() {
        String tag = "<exame_otica>{\"professional_id\":\"" + profId + "\",\"date\":\"2026-07-01\","
            + "\"start_time\":\"15:00\"}</exame_otica>";
        assertThat(handler.parseAndCreate(COMPANY, conversationId, contactId, "Marcado!\n" + tag)).isPresent();
        // Mesmo horário, mesmo profissional → conflito → empty.
        Optional<OticaExamAppointment> second = handler.parseAndCreate(COMPANY, conversationId, contactId, "De novo!\n" + tag);
        assertThat(second).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from otica_exam_appointments", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<OticaExamAppointment> a = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer marcar um exame de vista?");
        assertThat(a).isEmpty();
    }
}
