package com.meada.profiles.barbearia.queue;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code barber_queue_tickets} (camada 8.1) — a fila de walk-in.
 *
 * <p>NÃO há coluna de posição: a posição é DERIVADA. {@link #countAheadGeneral} e
 * {@link #countAheadForBarber} fazem o count de tickets 'aguardando' à frente (enqueued_at menor) no
 * escopo correto — é a base do cálculo no {@link BarberQueueService}. Opera via service_role; escopo
 * por company_id.
 */
@Repository
public class BarberQueueRepository {

    private static final RowMapper<BarberQueueTicket> MAPPER = (rs, rn) -> new BarberQueueTicket(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("barber_id"),
        rs.getString("barber_name"),
        (UUID) rs.getObject("service_id"),
        rs.getString("service_name"),
        rs.getInt("duration_minutes"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("guest_name"),
        rs.getString("guest_phone"),
        rs.getString("status"),
        rs.getTimestamp("enqueued_at").toInstant(),
        rs.getTimestamp("called_at") == null ? null : rs.getTimestamp("called_at").toInstant(),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant(),
        null, null);  // position/eta preenchidos na leitura pelo service

    private static final String COLS =
        "id, barber_id, barber_name, service_id, service_name, duration_minutes, conversation_id, "
            + "contact_id, guest_name, guest_phone, status, enqueued_at, called_at, notes, created_at, "
            + "status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public BarberQueueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<BarberQueueTicket> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from barber_queue_tickets where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Tickets ATIVOS (aguardando + chamado) da empresa, ordenados por enqueued_at (FIFO). */
    public List<BarberQueueTicket> listActive(UUID companyId) {
        return jdbcTemplate.query(
            "select " + COLS + " from barber_queue_tickets where company_id = ? "
                + "and status in ('aguardando','chamado') order by enqueued_at asc",
            MAPPER, companyId);
    }

    /** Tickets 'aguardando' da empresa, ordenados por enqueued_at (FIFO). */
    public List<BarberQueueTicket> listWaiting(UUID companyId) {
        return jdbcTemplate.query(
            "select " + COLS + " from barber_queue_tickets where company_id = ? "
                + "and status = 'aguardando' order by enqueued_at asc",
            MAPPER, companyId);
    }

    /** Tamanho atual da fila (aguardando) — total da empresa. */
    public int countWaiting(UUID companyId) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from barber_queue_tickets where company_id = ? and status = 'aguardando'",
            Long.class, companyId);
        return n == null ? 0 : n.intValue();
    }

    /**
     * Tickets 'aguardando' À FRENTE de um ticket GERAL (barber_id null): um ticket geral pode ser
     * atendido por qualquer barbeiro, então concorre com TODOS os 'aguardando' enfileirados antes dele
     * (gerais E de barbeiro específico). Count de enqueued_at menor.
     */
    public int countAheadGeneral(UUID companyId, Instant enqueuedAt) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from barber_queue_tickets where company_id = ? and status = 'aguardando' "
                + "and enqueued_at < ?",
            Long.class, companyId, Timestamp.from(enqueuedAt));
        return n == null ? 0 : n.intValue();
    }

    /**
     * Tickets 'aguardando' À FRENTE de um ticket de BARBEIRO específico: concorre com os 'aguardando'
     * daquele barbeiro E com os GERAIS (barber_id null) enfileirados antes dele (um geral à frente pode
     * "pegar" aquele barbeiro). Count de enqueued_at menor.
     */
    public int countAheadForBarber(UUID companyId, UUID barberId, Instant enqueuedAt) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from barber_queue_tickets where company_id = ? and status = 'aguardando' "
                + "and enqueued_at < ? and (barber_id = ? or barber_id is null)",
            Long.class, companyId, Timestamp.from(enqueuedAt), barberId);
        return n == null ? 0 : n.intValue();
    }

    /**
     * Duração (snapshot) dos tickets 'aguardando' À FRENTE no mesmo escopo — soma usada pelo ETA. Para
     * geral: todos antes; para barbeiro específico: daquele barbeiro + gerais antes.
     */
    public int sumDurationAhead(UUID companyId, UUID barberId, Instant enqueuedAt) {
        String scope = barberId == null
            ? ""
            : " and (barber_id = ? or barber_id is null)";
        StringBuilder sql = new StringBuilder(
            "select coalesce(sum(duration_minutes), 0) from barber_queue_tickets "
                + "where company_id = ? and status = 'aguardando' and enqueued_at < ?");
        sql.append(scope);
        Integer n = barberId == null
            ? jdbcTemplate.queryForObject(sql.toString(), Integer.class, companyId, Timestamp.from(enqueuedAt))
            : jdbcTemplate.queryForObject(sql.toString(), Integer.class, companyId, Timestamp.from(enqueuedAt), barberId);
        return n == null ? 0 : n;
    }

    /** Insere um ticket 'aguardando' (enqueued_at = now()). Snapshots vêm do service. */
    public BarberQueueTicket insert(UUID companyId, UUID barberId, String barberName, UUID serviceId,
                                    String serviceName, int durationMinutes, UUID conversationId,
                                    UUID contactId, String guestName, String guestPhone, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into barber_queue_tickets (company_id, barber_id, barber_name, service_id, "
                + "service_name, duration_minutes, conversation_id, contact_id, guest_name, guest_phone, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, barberId, barberName, serviceId, serviceName, durationMinutes,
            conversationId, contactId, guestName, guestPhone, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Atualiza status; quando vira 'chamado', grava called_at = now(). */
    public void updateStatus(UUID companyId, UUID id, String newStatus, boolean setCalledAt) {
        if (setCalledAt) {
            jdbcTemplate.update(
                "update barber_queue_tickets set status = ?, called_at = now(), status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        } else {
            jdbcTemplate.update(
                "update barber_queue_tickets set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }
}
