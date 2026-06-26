package com.meada.whatsapp.profiles.concessionaria.testdrives;

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
 * Acesso a {@code concessionaria_test_drives} (camada 8.17). Opera via service_role; o escopo por
 * company_id no WHERE é a defesa. A criação re-verifica conflito DENTRO da transação — defesa contra
 * race (a IA viu disponibilidade no cache; no instante de persistir, outro test-drive pode ter
 * ocupado o slot DO MESMO VENDEDOR). O conflito é decidido em SQL (janela materializada).
 *
 * <p>Conflito é POR salesperson_id (paralelismo entre vendedores): 2 test-drives no mesmo horário com
 * vendedores DIFERENTES NÃO conflitam.
 */
@Repository
public class ConcessionariaTestDriveRepository {

    private static final RowMapper<ConcessionariaTestDrive> MAPPER = (rs, rn) -> new ConcessionariaTestDrive(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("vehicle_id"),
        (UUID) rs.getObject("salesperson_id"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("customer_name"),
        rs.getString("vehicle_brand"),
        rs.getString("vehicle_model"),
        (Integer) rs.getObject("vehicle_year"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getInt("duration_minutes"),
        rs.getTimestamp("end_at").toInstant(),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, company_id, vehicle_id, salesperson_id, conversation_id, contact_id, customer_name, "
            + "vehicle_brand, vehicle_model, vehicle_year, start_at, duration_minutes, end_at, status, "
            + "notes, created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public ConcessionariaTestDriveRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ConcessionariaTestDrive> listByCompany(UUID companyId, String status, Instant dateFrom,
                                                       Instant dateTo, UUID salespersonId, UUID vehicleId,
                                                       int limit, int offset) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from concessionaria_test_drives where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (salespersonId != null) { sql.append(" and salesperson_id = ?"); args.add(salespersonId); }
        if (vehicleId != null) { sql.append(" and vehicle_id = ?"); args.add(vehicleId); }
        sql.append(" order by start_at asc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                               UUID salespersonId, UUID vehicleId) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from concessionaria_test_drives where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (salespersonId != null) { sql.append(" and salesperson_id = ?"); args.add(salespersonId); }
        if (vehicleId != null) { sql.append(" and vehicle_id = ?"); args.add(vehicleId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<ConcessionariaTestDrive> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from concessionaria_test_drives where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Nome do contato (cliente) — para snapshot no test-drive. */
    public Optional<String> contactName(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("select name from contacts where id = ? and company_id = ?",
                (rs, rn) -> rs.getString("name"), contactId, companyId)
            .stream().findFirst();
    }

    /** Test-drives ATIVOS (agendado/confirmado) de UM VENDEDOR na janela [from,to) — p/ o contexto IA. */
    public List<ConcessionariaTestDrive> listActiveBySalesperson(UUID companyId, UUID salespersonId,
                                                                 Instant from, Instant to) {
        return jdbcTemplate.query(
            "select " + COLS + " from concessionaria_test_drives where company_id = ? "
                + "and salesperson_id = ? and status in ('agendado','confirmado') "
                + "and start_at >= ? and start_at < ? order by start_at asc",
            MAPPER, companyId, salespersonId, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Conflito de slot: test-drive ATIVO (agendado/confirmado) do MESMO VENDEDOR cuja janela sobrepõe
     * [newStart, newEnd). Sobreposição = NOT (existing.end <= newStart OR existing.start >= newEnd).
     * Cálculo em SQL (defesa contra race dentro da transação). Conflito é POR salesperson_id.
     */
    public Optional<TestDriveConflict> findConflict(UUID companyId, UUID salespersonId,
                                                    Instant newStart, Instant newEnd) {
        return jdbcTemplate.query(
                "select id, customer_name, start_at, end_at "
                    + "from concessionaria_test_drives "
                    + "where company_id = ? and salesperson_id = ? and status in ('agendado','confirmado') "
                    + "and not (end_at <= ? or start_at >= ?) "
                    + "order by start_at asc limit 1",
                (rs, rn) -> new TestDriveConflict(
                    (UUID) rs.getObject("id"),
                    rs.getString("customer_name"),
                    rs.getTimestamp("start_at").toInstant(),
                    rs.getTimestamp("end_at").toInstant()),
                companyId, salespersonId, Timestamp.from(newStart), Timestamp.from(newEnd))
            .stream().findFirst();
    }

    /**
     * Cria o test-drive numa transação que RE-VERIFICA o conflito (por vendedor) imediatamente antes
     * do insert. end_at materializado (startAt + durationMinutes). Snapshots de veículo. Lança
     * {@link SlotConflictException} se houver conflito — o service a mapeia.
     */
    @Transactional
    public ConcessionariaTestDrive insertTestDrive(UUID companyId, UUID vehicleId, UUID salespersonId,
                                                   UUID conversationId, UUID contactId, String customerName,
                                                   String vehicleBrand, String vehicleModel, Integer vehicleYear,
                                                   Instant startAt, int durationMinutes, String notes) {
        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        Optional<TestDriveConflict> conflict = findConflict(companyId, salespersonId, startAt, endAt);
        if (conflict.isPresent()) {
            throw new SlotConflictException(conflict.get());
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into concessionaria_test_drives (company_id, vehicle_id, salesperson_id, "
                + "conversation_id, contact_id, customer_name, vehicle_brand, vehicle_model, vehicle_year, "
                + "start_at, duration_minutes, end_at, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, vehicleId, salespersonId, conversationId, contactId, customerName,
            vehicleBrand, vehicleModel, vehicleYear, Timestamp.from(startAt), durationMinutes,
            Timestamp.from(endAt), notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Persiste a transição de status + status_updated_at. Service já validou a transição. */
    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update(
            "update concessionaria_test_drives set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatus, companyId, id);
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de slot (por vendedor). */
    public static class SlotConflictException extends RuntimeException {
        private final transient TestDriveConflict conflict;

        public SlotConflictException(TestDriveConflict conflict) {
            this.conflict = conflict;
        }

        public TestDriveConflict conflict() {
            return conflict;
        }
    }
}
