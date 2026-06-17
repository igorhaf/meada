package com.meada.whatsapp.profiles.salon.appointments;

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
 * Acesso a {@code salon_appointments} (camada 7.5). Opera via service_role.
 *
 * <p>EVOLUÇÃO do padrão: o conflito é por PROFISSIONAL (não company). {@link #findConflict} filtra
 * por {@code professional_id} — 2 clientes no mesmo horário com profissionais DIFERENTES não
 * conflitam. O {@link #insertAppointment} re-verifica o conflito DENTRO da transação (defesa race) e
 * materializa o end_at + os snapshots.
 */
@Repository
public class SalonAppointmentRepository {

    private static final RowMapper<SalonAppointment> MAPPER = (rs, rn) -> new SalonAppointment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("professional_id"),
        rs.getString("professional_name"),
        (UUID) rs.getObject("service_id"),
        rs.getString("service_name"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("guest_name"),
        rs.getString("guest_phone"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getTimestamp("end_at").toInstant(),
        rs.getInt("duration_minutes"),
        (Integer) rs.getObject("price_cents"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, professional_id, professional_name, service_id, service_name, conversation_id, contact_id, "
            + "guest_name, guest_phone, start_at, end_at, duration_minutes, price_cents, status, notes, "
            + "created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public SalonAppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SalonAppointment> listByCompany(UUID companyId, String status, Instant dateFrom,
                                                Instant dateTo, UUID professionalId, UUID contactId,
                                                int limit, int offset) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from salon_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        sql.append(" order by start_at asc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                               UUID professionalId, UUID contactId) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from salon_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<SalonAppointment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from salon_appointments where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Histórico do contato (mais recentes primeiro) — usado pela IA e pela tela. */
    public List<SalonAppointment> listByContact(UUID companyId, UUID contactId, int limit) {
        return jdbcTemplate.query(
            "select " + COLS + " from salon_appointments where company_id = ? and contact_id = ? "
                + "order by start_at desc limit ?",
            MAPPER, companyId, contactId, limit);
    }

    /** Agendamentos ATIVOS de um profissional na janela [from,to) — para slots livres da IA. */
    public List<SalonAppointment> listActiveByProfessional(UUID companyId, UUID professionalId,
                                                           Instant from, Instant to) {
        return jdbcTemplate.query(
            "select " + COLS + " from salon_appointments where company_id = ? and professional_id = ? "
                + "and status in ('agendado','confirmado') and start_at >= ? and start_at < ? "
                + "order by start_at asc",
            MAPPER, companyId, professionalId, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Conflito de slot por PROFISSIONAL (decisão 5): agendamento ATIVO (agendado/confirmado) do MESMO
     * profissional cuja janela sobrepõe [newStart, newEnd). Sobreposição = NOT (existing.end <=
     * newStart OR existing.start >= newEnd). NÃO filtra por company aqui — o professional_id já é de
     * um company (o service valida). Cálculo em SQL (defesa contra race na transação).
     */
    public Optional<SalonAppointmentConflict> findConflict(UUID professionalId, Instant newStart, Instant newEnd) {
        return jdbcTemplate.query(
                "select id, guest_name, start_at, end_at from salon_appointments "
                    + "where professional_id = ? and status in ('agendado','confirmado') "
                    + "and not (end_at <= ? or start_at >= ?) "
                    + "order by start_at asc limit 1",
                (rs, rn) -> new SalonAppointmentConflict(
                    (UUID) rs.getObject("id"),
                    rs.getString("guest_name"),
                    rs.getTimestamp("start_at").toInstant(),
                    rs.getTimestamp("end_at").toInstant()),
                professionalId, Timestamp.from(newStart), Timestamp.from(newEnd))
            .stream().findFirst();
    }

    /**
     * Cria o agendamento numa transação que RE-VERIFICA o conflito (por profissional) antes do insert
     * (decisão 5). end_at materializado; professional_name/service_name/price_cents/duration_minutes
     * são snapshots. Lança {@link SlotConflictException} se conflitar.
     */
    @Transactional
    public SalonAppointment insertAppointment(UUID companyId, UUID professionalId, String professionalName,
                                              UUID serviceId, String serviceName, Integer priceCents,
                                              int durationMinutes, UUID conversationId, UUID contactId,
                                              String guestName, String guestPhone, Instant startAt,
                                              String notes) {
        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        Optional<SalonAppointmentConflict> conflict = findConflict(professionalId, startAt, endAt);
        if (conflict.isPresent()) {
            throw new SlotConflictException(conflict.get());
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into salon_appointments (company_id, professional_id, service_id, conversation_id, "
                + "contact_id, guest_name, guest_phone, start_at, duration_minutes, end_at, service_name, "
                + "professional_name, price_cents, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, professionalId, serviceId, conversationId, contactId, guestName,
            guestPhone, Timestamp.from(startAt), durationMinutes, Timestamp.from(endAt), serviceName,
            professionalName, priceCents, notes);
        return findById(companyId, id).orElseThrow();
    }

    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update(
            "update salon_appointments set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatus, companyId, id);
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de slot. */
    public static class SlotConflictException extends RuntimeException {
        private final transient SalonAppointmentConflict conflict;

        public SlotConflictException(SalonAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public SalonAppointmentConflict conflict() {
            return conflict;
        }
    }
}
