package com.meada.profiles.dental.appointments;

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
 * Acesso a {@code dental_appointments} (camada 7.4). Opera via service_role; o escopo por company_id
 * no WHERE é a defesa. A criação re-verifica conflito DENTRO da transação (decisão 4) — defesa
 * contra race (a IA viu disponibilidade no cache de 30s; no instante de persistir, outra consulta
 * pode ter ocupado o slot). O conflito é decidido em SQL (janela materializada), não em Java.
 *
 * <p>Conflito é por COMPANY (1 dentista por tenant nesta SM) — sem dentist_id. Fase futura:
 * adicionar dentist_id e mudar o WHERE do findConflict.
 */
@Repository
public class DentalAppointmentRepository {

    private static final RowMapper<DentalAppointment> MAPPER = (rs, rn) -> new DentalAppointment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("patient_id"),
        rs.getString("patient_name"),
        (UUID) rs.getObject("conversation_id"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getTimestamp("end_at").toInstant(),
        rs.getInt("duration_minutes"),
        rs.getString("type"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String SELECT =
        "select a.id, a.patient_id, p.name as patient_name, a.conversation_id, a.start_at, a.end_at, "
            + "a.duration_minutes, a.type, a.status, a.notes, a.created_at, a.status_updated_at "
            + "from dental_appointments a join dental_patients p on p.id = a.patient_id ";

    private final JdbcTemplate jdbcTemplate;

    public DentalAppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lista consultas do tenant com filtros opcionais (status, janela [from,to), paciente), paginado. */
    public List<DentalAppointment> listByCompany(UUID companyId, String status, Instant dateFrom,
                                                 Instant dateTo, UUID patientId, int limit, int offset) {
        StringBuilder sql = new StringBuilder(SELECT + "where a.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and a.status = ?");
            args.add(status);
        }
        if (dateFrom != null) {
            sql.append(" and a.start_at >= ?");
            args.add(Timestamp.from(dateFrom));
        }
        if (dateTo != null) {
            sql.append(" and a.start_at < ?");
            args.add(Timestamp.from(dateTo));
        }
        if (patientId != null) {
            sql.append(" and a.patient_id = ?");
            args.add(patientId);
        }
        sql.append(" order by a.start_at asc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                               UUID patientId) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from dental_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        if (dateFrom != null) {
            sql.append(" and start_at >= ?");
            args.add(Timestamp.from(dateFrom));
        }
        if (dateTo != null) {
            sql.append(" and start_at < ?");
            args.add(Timestamp.from(dateTo));
        }
        if (patientId != null) {
            sql.append(" and patient_id = ?");
            args.add(patientId);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<DentalAppointment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(SELECT + "where a.company_id = ? and a.id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Consultas de um paciente (futureOnly = só as a partir de agora). Usado pela IA e pela tela. */
    public List<DentalAppointment> listByPatient(UUID companyId, UUID patientId, boolean futureOnly) {
        StringBuilder sql = new StringBuilder(SELECT + "where a.company_id = ? and a.patient_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.add(patientId);
        if (futureOnly) {
            sql.append(" and a.start_at >= now() and a.status in ('agendada','confirmada')");
        }
        sql.append(" order by a.start_at asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    /** Consultas ATIVAS (agendada/confirmada) do tenant na janela [from,to) — para o contexto IA. */
    public List<DentalAppointment> listActiveInRange(UUID companyId, Instant from, Instant to) {
        return jdbcTemplate.query(
            SELECT + "where a.company_id = ? and a.status in ('agendada','confirmada') "
                + "and a.start_at >= ? and a.start_at < ? order by a.start_at asc",
            MAPPER, companyId, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Conflito de slot (decisão 4): consulta ATIVA (agendada/confirmada) no MESMO consultório
     * (company) cuja janela sobrepõe [newStart, newEnd). Sobreposição = NOT (existing.end <= newStart
     * OR existing.start >= newEnd). Cálculo em SQL (defesa contra race dentro da transação).
     */
    public Optional<AppointmentConflict> findConflict(UUID companyId, Instant newStart, Instant newEnd) {
        return jdbcTemplate.query(
                "select a.id, p.name as patient_name, a.start_at, a.end_at "
                    + "from dental_appointments a join dental_patients p on p.id = a.patient_id "
                    + "where a.company_id = ? and a.status in ('agendada','confirmada') "
                    + "and not (a.end_at <= ? or a.start_at >= ?) "
                    + "order by a.start_at asc limit 1",
                (rs, rn) -> new AppointmentConflict(
                    (UUID) rs.getObject("id"),
                    rs.getString("patient_name"),
                    rs.getTimestamp("start_at").toInstant(),
                    rs.getTimestamp("end_at").toInstant()),
                companyId, Timestamp.from(newStart), Timestamp.from(newEnd))
            .stream().findFirst();
    }

    /**
     * Cria a consulta numa transação que RE-VERIFICA o conflito imediatamente antes do insert
     * (decisão 4). end_at materializado (startAt + durationMinutes). Lança {@link SlotConflictException}
     * se houver conflito — o service a mapeia.
     */
    @Transactional
    public DentalAppointment insertAppointment(UUID companyId, UUID patientId, UUID conversationId,
                                               Instant startAt, int durationMinutes, String type,
                                               String notes) {
        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        Optional<AppointmentConflict> conflict = findConflict(companyId, startAt, endAt);
        if (conflict.isPresent()) {
            throw new SlotConflictException(conflict.get());
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into dental_appointments (company_id, patient_id, conversation_id, start_at, "
                + "duration_minutes, end_at, type, notes) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, patientId, conversationId, Timestamp.from(startAt), durationMinutes,
            Timestamp.from(endAt), type.trim(), notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Persiste a transição de status + status_updated_at. Service já validou a transição. */
    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update(
            "update dental_appointments set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatus, companyId, id);
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de slot. */
    public static class SlotConflictException extends RuntimeException {
        private final transient AppointmentConflict conflict;

        public SlotConflictException(AppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public AppointmentConflict conflict() {
            return conflict;
        }
    }
}
