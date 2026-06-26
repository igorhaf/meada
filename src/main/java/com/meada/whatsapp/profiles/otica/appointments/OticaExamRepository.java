package com.meada.whatsapp.profiles.otica.appointments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code otica_exam_appointments} (camada 8.12, FLUXO A). Opera via service_role; o escopo
 * por company_id no WHERE é a defesa. Conflito POR PROFISSIONAL (half-open, espelho
 * fotografia/dermatologia): {@link #insertAppointment} re-verifica na transação (defesa race) e
 * materializa end_at (start_at + duration_minutes — NÃO gerada, pois timestamptz+interval não é
 * IMMUTABLE). O conflito é decidido em SQL (janela materializada), não em Java.
 *
 * <p>2 clientes no mesmo horário com profissionais DIFERENTES NÃO conflitam (paralelismo).
 */
@Repository
public class OticaExamRepository {

    private static final RowMapper<OticaExamAppointment> MAPPER = (rs, rn) -> new OticaExamAppointment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("professional_id"),
        rs.getString("professional_name"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("customer_name"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getTimestamp("end_at").toInstant(),
        rs.getInt("duration_minutes"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, professional_id, professional_name, conversation_id, contact_id, customer_name, "
            + "start_at, end_at, duration_minutes, status, notes, created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public OticaExamRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lista exames do tenant com filtros opcionais (status, janela [from,to), profissional), paginado. */
    public List<OticaExamAppointment> listByCompany(UUID companyId, String status, Instant dateFrom,
                                                    Instant dateTo, UUID professionalId, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from otica_exam_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        sql.append(" order by start_at asc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                               UUID professionalId) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from otica_exam_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<OticaExamAppointment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from otica_exam_appointments where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Exames do contato (futureOnly = só os ativos a partir de agora). Usado pelo contexto da IA. */
    public List<OticaExamAppointment> listByContact(UUID companyId, UUID contactId, boolean futureOnly) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from otica_exam_appointments where company_id = ? and contact_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.add(contactId);
        if (futureOnly) {
            sql.append(" and start_at >= now() and status in ('agendado','confirmado')");
        }
        sql.append(" order by start_at asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    /** Exames ATIVOS (agendado/confirmado) de um profissional na janela [from,to) — para o contexto IA. */
    public List<OticaExamAppointment> listActiveByProfessional(UUID companyId, UUID professionalId,
                                                               Instant from, Instant to) {
        return jdbcTemplate.query(
            "select " + COLS + " from otica_exam_appointments where company_id = ? and professional_id = ? "
                + "and status in ('agendado','confirmado') and start_at >= ? and start_at < ? order by start_at asc",
            MAPPER, companyId, professionalId, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Conflito de slot por PROFISSIONAL: exame ATIVO (agendado/confirmado) do MESMO profissional cuja
     * janela sobrepõe [newStart, newEnd). Sobreposição = NOT (end <= newStart OR start >= newEnd).
     * Cálculo em SQL (defesa contra race dentro da transação).
     */
    public Optional<OticaExamConflict> findConflict(UUID companyId, UUID professionalId,
                                                    Instant newStart, Instant newEnd) {
        return jdbcTemplate.query(
                "select id, customer_name, start_at, end_at from otica_exam_appointments "
                    + "where company_id = ? and professional_id = ? and status in ('agendado','confirmado') "
                    + "and not (end_at <= ? or start_at >= ?) order by start_at asc limit 1",
                (rs, rn) -> new OticaExamConflict(
                    (UUID) rs.getObject("id"),
                    rs.getString("customer_name"),
                    rs.getTimestamp("start_at").toInstant(),
                    rs.getTimestamp("end_at").toInstant()),
                companyId, professionalId, Timestamp.from(newStart), Timestamp.from(newEnd))
            .stream().findFirst();
    }

    /**
     * Cria o exame numa transação que RE-VERIFICA o conflito (por profissional) imediatamente antes
     * do insert. end_at materializado (startAt + durationMinutes). customerName/professionalName são
     * snapshots resolvidos pelo service. Lança {@link SlotConflictException} se houver conflito.
     */
    @Transactional
    public OticaExamAppointment insertAppointment(UUID companyId, UUID professionalId, String professionalName,
                                                  UUID conversationId, UUID contactId, String customerName,
                                                  Instant startAt, int durationMinutes, String notes) {
        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        Optional<OticaExamConflict> conflict = findConflict(companyId, professionalId, startAt, endAt);
        if (conflict.isPresent()) {
            throw new SlotConflictException(conflict.get());
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into otica_exam_appointments (company_id, professional_id, conversation_id, contact_id, "
                + "customer_name, professional_name, start_at, duration_minutes, end_at, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, professionalId, conversationId, contactId, customerName, professionalName,
            Timestamp.from(startAt), durationMinutes, Timestamp.from(endAt), notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Persiste a transição de status + status_updated_at. Service já validou a transição. */
    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update(
            "update otica_exam_appointments set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatus, companyId, id);
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de slot. */
    public static class SlotConflictException extends RuntimeException {
        private final transient OticaExamConflict conflict;

        public SlotConflictException(OticaExamConflict conflict) {
            this.conflict = conflict;
        }

        public OticaExamConflict conflict() {
            return conflict;
        }
    }
}
