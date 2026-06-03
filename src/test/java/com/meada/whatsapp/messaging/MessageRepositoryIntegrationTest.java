package com.meada.whatsapp.messaging;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test do {@link MessageRepository} contra PostgreSQL real
 * (Testcontainers), como service_role, com as migrations de produção.
 *
 * <p>messages tem FK composta para conversations(id, company_id), então o seed
 * cria a cadeia completa (company + contact + instance + conversation) por tenant.
 */
class MessageRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MessageRepository repository;

    private static final UUID COMPANY_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CONTACT_A = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID INSTANCE_A = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID CONV_A = UUID.fromString("a3000000-0000-0000-0000-000000000001");

    private static final UUID COMPANY_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CONTACT_B = UUID.fromString("b2000000-0000-0000-0000-000000000001");
    private static final UUID INSTANCE_B = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID CONV_B = UUID.fromString("b3000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        seedTenant(COMPANY_A, "empresa-a", INSTANCE_A, "inst-a", CONTACT_A, "+5511999990001", CONV_A);
        seedTenant(COMPANY_B, "empresa-b", INSTANCE_B, "inst-b", CONTACT_B, "+5511999990002", CONV_B);
    }

    private void seedTenant(UUID companyId, String slug, UUID instanceId, String instanceName,
                            UUID contactId, String phone, UUID conversationId) {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            companyId, "Empresa " + slug, slug);
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instanceId, companyId, instanceName, "tok-" + instanceName);
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, phone, "Cliente " + slug);
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
                + "values (?, ?, ?, ?, 'open', 'ai')",
            conversationId, companyId, contactId, instanceId);
    }

    private long countMessages(UUID companyId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from messages where company_id = ?", Long.class, companyId);
    }

    @Test
    @DisplayName("(a) mensagem nova é inserida; last_message_at da conversa NÃO muda (resp. do service)")
    void newMessage_isInserted_lastMessageAtUntouched() {
        Optional<Message> result = repository.insertIfNew(
            COMPANY_A, CONV_A, MessageDirection.INBOUND, MessageSender.CONTACT, "Oi", "EVT-A-1");

        assertThat(result).isPresent();
        assertThat(result.get().companyId()).isEqualTo(COMPANY_A);
        assertThat(countMessages(COMPANY_A)).isEqualTo(1);

        // o repositório NÃO toca conversations.last_message_at
        Object lastMsgAt = jdbcTemplate.queryForObject(
            "select last_message_at from conversations where id = ?", Object.class, CONV_A);
        assertThat(lastMsgAt).isNull();
    }

    @Test
    @DisplayName("(b) idempotência: mesmo evolution_message_id 2x → 1ª insere, 2ª empty, 1 message")
    void sameEvolutionId_isIdempotent() {
        Optional<Message> first = repository.insertIfNew(
            COMPANY_A, CONV_A, MessageDirection.INBOUND, MessageSender.CONTACT, "Oi", "EVT-A-1");
        Optional<Message> second = repository.insertIfNew(
            COMPANY_A, CONV_A, MessageDirection.INBOUND, MessageSender.CONTACT, "Oi de novo", "EVT-A-1");

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(countMessages(COMPANY_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("(c) evolution_message_id null não conflita: duas nulls ambas inserem")
    void nullEvolutionId_doesNotConflict() {
        Optional<Message> first = repository.insertIfNew(
            COMPANY_A, CONV_A, MessageDirection.OUTBOUND, MessageSender.AI, "Resposta 1", null);
        Optional<Message> second = repository.insertIfNew(
            COMPANY_A, CONV_A, MessageDirection.OUTBOUND, MessageSender.AI, "Resposta 2", null);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get().id()).isNotEqualTo(second.get().id());
        assertThat(countMessages(COMPANY_A)).isEqualTo(2);
    }

    @Test
    @DisplayName("(d) CHECK direction/sender: inbound+ai é rejeitado pelo banco")
    void invalidDirectionSender_isRejected() {
        assertThatThrownBy(() -> repository.insertIfNew(
            COMPANY_A, CONV_A, MessageDirection.INBOUND, MessageSender.AI, "incoerente", "EVT-X"))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countMessages(COMPANY_A)).isZero();
    }

    @Test
    @DisplayName("(e) idempotência é GLOBAL: mesmo evolution_message_id em A e B → só A insere")
    void evolutionId_isGloballyUnique() {
        Optional<Message> inA = repository.insertIfNew(
            COMPANY_A, CONV_A, MessageDirection.INBOUND, MessageSender.CONTACT, "em A", "EVT-SHARED");
        Optional<Message> inB = repository.insertIfNew(
            COMPANY_B, CONV_B, MessageDirection.INBOUND, MessageSender.CONTACT, "em B", "EVT-SHARED");

        assertThat(inA).isPresent();
        assertThat(inB).isEmpty();   // global: o mesmo id já existe (em A), B vira DO NOTHING
        assertThat(countMessages(COMPANY_A)).isEqualTo(1);
        assertThat(countMessages(COMPANY_B)).isZero();
    }
}
