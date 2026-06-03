package com.meada.whatsapp.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso de escrita a {@code messages}. Insere mensagens de forma idempotente
 * (reentrega de webhook não duplica).
 *
 * <p>Toca SÓ a tabela messages — NÃO atualiza conversations.last_message_at (isso
 * é {@link ConversationRepository#touchLastMessageAt}, chamado pelo WebhookService
 * na mesma transação). Fronteira: cada repositório cuida da sua tabela.
 */
@Repository
public class MessageRepository {

    private static final RowMapper<Message> ROW_MAPPER = (rs, rowNum) ->
        new Message(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"));

    // Insert idempotente. ON CONFLICT repete o predicado parcial
    // (WHERE evolution_message_id IS NOT NULL) para o Postgres reconhecer
    // uq_messages_evolution_id como arbiter.
    //   - evolution_message_id NÃO-NULL, novo  → insere, RETURNING traz a linha;
    //   - evolution_message_id NÃO-NULL, repetido (reentrega) → DO NOTHING,
    //     RETURNING VAZIO → caller trata como "já processada";
    //   - evolution_message_id NULL (mensagens internas ai/human antes do envio)
    //     → o índice parcial não cobre NULL, então NUNCA conflita: sempre insere.
    // Idempotência é GLOBAL (índice sem company_id): evolution_message_id é o id
    //   da mensagem no WhatsApp, único por natureza. Mesmo id em 2 tenants → só o
    //   1º insere. Correto e mais defensivo (reentrega cross-instance não duplica).
    private static final String INSERT_IF_NEW =
        "insert into messages (company_id, conversation_id, direction, sender, content, evolution_message_id) "
            + "values (?, ?, ?, ?, ?, ?) "
            + "on conflict (evolution_message_id) where evolution_message_id is not null "
            + "do nothing "
            + "returning id, company_id";

    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Insere a mensagem se ela ainda não existe (por evolution_message_id).
     *
     * @param evolutionMessageId id externo da Evolution; pode ser null para
     *                           mensagens internas (ai/human) sem id de envio ainda
     *                           — nesse caso não há idempotência (sempre insere).
     * @return a mensagem inserida, ou {@link Optional#empty()} se já existia
     *         (reentrega de webhook) — o caller pula o reprocessamento.
     */
    public Optional<Message> insertIfNew(UUID companyId,
                                         UUID conversationId,
                                         MessageDirection direction,
                                         MessageSender sender,
                                         String content,
                                         String evolutionMessageId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(sender, "sender must not be null");
        Objects.requireNonNull(content, "content must not be null");
        // evolutionMessageId pode ser null (mensagens internas) — sem requireNonNull.

        List<Message> inserted = jdbcTemplate.query(
            INSERT_IF_NEW, ROW_MAPPER,
            companyId, conversationId, direction.dbValue(), sender.dbValue(), content, evolutionMessageId);

        return inserted.stream().findFirst();
    }
}
