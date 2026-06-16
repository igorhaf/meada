package com.meada.whatsapp.lgpd;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o {@link LgpdService} (camada 5.24 #89 erase, #90 export) com Postgres real. Semeia
 * company + contato + conversa + mensagem + agendamento + tag (todos da mesma empresa) e
 * verifica que o export traz tudo e que o erase apaga tudo (contagens zeram) deixando uma
 * linha em audit_log com action='lgpd_erase'. Cobre também o caso not-found.
 */
class LgpdServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LgpdService lgpdService;

    private static final UUID ACTOR = UUID.fromString("66666666-6666-6666-6666-666666666666");

    /** Semeia o grafo completo de um contato e devolve os ids relevantes. */
    private Seeded seed(UUID companyId) {
        UUID instanceId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            companyId, "Empresa LGPD", "empresa-lgpd-" + companyId);
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) "
                + "values (?, ?, ?, ?)",
            instanceId, companyId, "inst-lgpd", "tok-lgpd");
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+5511988880001", "Cliente LGPD");
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, "
                + "status, handled_by) values (?, ?, ?, ?, 'open', 'ai')",
            conversationId, companyId, contactId, instanceId);
        jdbcTemplate.update(
            "insert into messages (company_id, conversation_id, direction, sender, content) "
                + "values (?, ?, 'inbound', 'contact', 'Olá, quero um horário')",
            companyId, conversationId);
        jdbcTemplate.update(
            "insert into appointments (company_id, contact_id, conversation_id, scheduled_at) "
                + "values (?, ?, ?, ?)",
            companyId, contactId, conversationId,
            Timestamp.from(Instant.now().plus(2, ChronoUnit.DAYS)));
        jdbcTemplate.update(
            "insert into tags (id, company_id, name, color) values (?, ?, ?, 'blue')",
            tagId, companyId, "VIP");
        jdbcTemplate.update(
            "insert into conversation_tags (conversation_id, tag_id) values (?, ?)",
            conversationId, tagId);

        return new Seeded(contactId, conversationId);
    }

    private record Seeded(UUID contactId, UUID conversationId) {}

    @Test
    @DisplayName("exportContact traz contato + conversas + mensagens + agendamentos + tags")
    void exportContact_returnsEverything() {
        UUID companyId = UUID.randomUUID();
        Seeded s = seed(companyId);

        Map<String, Object> export = lgpdService.exportContact(companyId, s.contactId());

        @SuppressWarnings("unchecked")
        Map<String, Object> contact = (Map<String, Object>) export.get("contact");
        assertThat(contact.get("phoneNumber")).isEqualTo("+5511988880001");
        assertThat(contact.get("name")).isEqualTo("Cliente LGPD");

        assertThat((List<?>) export.get("conversations")).hasSize(1);
        assertThat((List<?>) export.get("messages")).hasSize(1);
        assertThat((List<?>) export.get("appointments")).hasSize(1);
        assertThat((List<?>) export.get("tags")).hasSize(1);
    }

    @Test
    @DisplayName("exportContact de contato inexistente → ContactNotFoundException")
    void exportContact_notFound_throws() {
        UUID companyId = UUID.randomUUID();
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            companyId, "Vazia", "vazia-" + companyId);

        assertThatThrownBy(() -> lgpdService.exportContact(companyId, UUID.randomUUID()))
            .isInstanceOf(ContactNotFoundException.class);
    }

    @Test
    @DisplayName("eraseContact apaga contato/conversas/mensagens/agendamentos/tags e audita lgpd_erase")
    void eraseContact_hardDeletesAndAudits() {
        UUID companyId = UUID.randomUUID();
        Seeded s = seed(companyId);

        lgpdService.eraseContact(companyId, s.contactId(), ACTOR);

        assertThat(count("contacts", "id", s.contactId())).isZero();
        assertThat(count("conversations", "contact_id", s.contactId())).isZero();
        assertThat(count("appointments", "contact_id", s.contactId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from messages where conversation_id = ?", Long.class,
            s.conversationId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from conversation_tags where conversation_id = ?", Long.class,
            s.conversationId())).isZero();

        // Auditoria: uma linha lgpd_erase para o contato apagado, com nome/telefone no metadata.
        Map<String, Object> audit = jdbcTemplate.queryForMap(
            "select action, entity, entity_id, metadata::text as metadata from audit_log "
                + "where action = 'lgpd_erase'");
        assertThat(audit.get("entity")).isEqualTo("contact");
        assertThat(audit.get("entity_id")).isEqualTo(s.contactId());
        assertThat((String) audit.get("metadata")).contains("Cliente LGPD", "+5511988880001");
    }

    @Test
    @DisplayName("eraseContact de contato inexistente → ContactNotFoundException (nada apagado)")
    void eraseContact_notFound_throws() {
        UUID companyId = UUID.randomUUID();
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            companyId, "Vazia", "vazia-" + companyId);

        assertThatThrownBy(() -> lgpdService.eraseContact(companyId, UUID.randomUUID(), ACTOR))
            .isInstanceOf(ContactNotFoundException.class);
    }

    private long count(String table, String column, UUID value) {
        return jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where " + column + " = ?", Long.class, value);
    }
}
