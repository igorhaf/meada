package com.meada.profiles.fotografia.appointments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code fotografia_session_appointments} (camada 8.16). Opera via service_role. Conflito por
 * PROFISSIONAL (half-open, espelho dermatologia/salon): {@link #insertAppointment} re-verifica na
 * transação (defesa race), materializa end_at (start_at + duration_minutes) E delivery_due_date
 * (date(start_at) + delivery_days, no fuso do tenant) e os snapshots de pacote/profissional/cliente.
 * {@link #updateSession} deixa o estúdio gravar o delivery_link DEPOIS da sessão.
 */
@Repository
public class FotografiaAppointmentRepository {

    /** Fuso do tenant — usado pra materializar a delivery_due_date a partir do dia local da sessão. */
    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final RowMapper<FotografiaSessionAppointment> MAPPER = (rs, rn) -> new FotografiaSessionAppointment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("professional_id"),
        rs.getString("professional_name"),
        (UUID) rs.getObject("package_id"),
        rs.getString("package_name"),
        rs.getInt("price_cents"),
        rs.getInt("duration_minutes"),
        rs.getInt("delivery_days"),
        (UUID) rs.getObject("contact_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("customer_name"),
        rs.getString("customer_phone"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getTimestamp("end_at").toInstant(),
        rs.getObject("delivery_due_date", LocalDate.class),
        rs.getString("delivery_link"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, professional_id, professional_name, package_id, package_name, price_cents, duration_minutes, "
            + "delivery_days, contact_id, conversation_id, customer_name, customer_phone, start_at, end_at, "
            + "delivery_due_date, delivery_link, status, notes, created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public FotografiaAppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FotografiaSessionAppointment> listByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                                            UUID professionalId, UUID packageId, UUID contactId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from fotografia_session_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        if (packageId != null) { sql.append(" and package_id = ?"); args.add(packageId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        sql.append(" order by start_at asc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                               UUID professionalId, UUID packageId, UUID contactId) {
        StringBuilder sql = new StringBuilder("select count(*) from fotografia_session_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        if (packageId != null) { sql.append(" and package_id = ?"); args.add(packageId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<FotografiaSessionAppointment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from fotografia_session_appointments where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Resolve uma sessão para a ENTREGA do material (mesma forma que findById; nome explícito p/ o handler). */
    public Optional<FotografiaSessionAppointment> findDeliverable(UUID companyId, UUID sessionId) {
        return findById(companyId, sessionId);
    }

    public List<FotografiaSessionAppointment> listByContact(UUID companyId, UUID contactId, int limit) {
        return jdbcTemplate.query("select " + COLS + " from fotografia_session_appointments where company_id = ? and contact_id = ? "
                + "order by start_at desc limit ?", MAPPER, companyId, contactId, limit);
    }

    public List<FotografiaSessionAppointment> listActiveByProfessional(UUID companyId, UUID professionalId, Instant from, Instant to) {
        return jdbcTemplate.query("select " + COLS + " from fotografia_session_appointments where company_id = ? and professional_id = ? "
                + "and status in ('agendada','confirmada') and start_at >= ? and start_at < ? order by start_at asc",
            MAPPER, companyId, professionalId, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Conflito de slot por PROFISSIONAL: sessão ATIVA (agendada/confirmada) do MESMO profissional cuja
     * janela sobrepõe [newStart, newEnd). Half-open. Cálculo em SQL (defesa contra race).
     */
    public Optional<FotografiaAppointmentConflict> findConflict(UUID professionalId, Instant newStart, Instant newEnd) {
        return jdbcTemplate.query(
                "select id, customer_name, start_at, end_at from fotografia_session_appointments "
                    + "where professional_id = ? and status in ('agendada','confirmada') "
                    + "and not (end_at <= ? or start_at >= ?) order by start_at asc limit 1",
                (rs, rn) -> new FotografiaAppointmentConflict(
                    (UUID) rs.getObject("id"), rs.getString("customer_name"),
                    rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant()),
                professionalId, Timestamp.from(newStart), Timestamp.from(newEnd))
            .stream().findFirst();
    }

    /**
     * Cria a sessão numa transação que RE-VERIFICA o conflito (por profissional) antes do insert.
     * end_at materializado (start + duration); delivery_due_date materializada (dia local da sessão +
     * delivery_days); snapshots de cliente/profissional/pacote. Lança {@link SlotConflictException}.
     */
    @Transactional
    public FotografiaSessionAppointment insertAppointment(UUID companyId, UUID professionalId, String professionalName,
                                                          UUID packageId, String packageName, int priceCents,
                                                          int durationMinutes, int deliveryDays,
                                                          UUID contactId, UUID conversationId,
                                                          String customerName, String customerPhone,
                                                          Instant startAt, String notes) {
        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        Optional<FotografiaAppointmentConflict> conflict = findConflict(professionalId, startAt, endAt);
        if (conflict.isPresent()) {
            throw new SlotConflictException(conflict.get());
        }
        LocalDate deliveryDueDate = startAt.atZone(TENANT_ZONE).toLocalDate().plusDays(deliveryDays);
        UUID id = jdbcTemplate.queryForObject(
            "insert into fotografia_session_appointments (company_id, professional_id, package_id, conversation_id, "
                + "contact_id, customer_name, customer_phone, professional_name, package_name, price_cents, "
                + "duration_minutes, delivery_days, start_at, end_at, delivery_due_date, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, professionalId, packageId, conversationId, contactId,
            customerName, customerPhone, professionalName, packageName, priceCents,
            durationMinutes, deliveryDays, Timestamp.from(startAt), Timestamp.from(endAt),
            Date.valueOf(deliveryDueDate), notes);
        return findById(companyId, id).orElseThrow();
    }

    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update("update fotografia_session_appointments set status = ?, status_updated_at = now() "
            + "where company_id = ? and id = ?", newStatus, companyId, id);
    }

    /**
     * Grava o delivery_link e/ou notes DEPOIS da sessão (ação do estúdio). Só atualiza os campos
     * fornecidos (null = não tocar). Devolve o número de linhas afetadas (0 = sessão inexistente).
     */
    public int updateSession(UUID companyId, UUID id, String deliveryLink, boolean linkProvided, String notes) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (linkProvided) { sets.add("delivery_link = ?"); args.add(deliveryLink); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (sets.isEmpty()) {
            return findById(companyId, id).isPresent() ? 1 : 0;
        }
        args.add(companyId);
        args.add(id);
        return jdbcTemplate.update("update fotografia_session_appointments set " + String.join(", ", sets)
            + " where company_id = ? and id = ?", args.toArray());
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de slot. */
    public static class SlotConflictException extends RuntimeException {
        private final transient FotografiaAppointmentConflict conflict;

        public SlotConflictException(FotografiaAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public FotografiaAppointmentConflict conflict() {
            return conflict;
        }
    }
}
