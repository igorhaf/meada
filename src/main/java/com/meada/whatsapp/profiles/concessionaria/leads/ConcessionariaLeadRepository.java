package com.meada.whatsapp.profiles.concessionaria.leads;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code concessionaria_leads} (camada 8.17). Opera via service_role; escopo por company_id
 * no WHERE é a defesa. Snapshots de veículo (marca/modelo/ano + PREÇO do catálogo) + cliente são
 * gravados no INSERT — alterar/vender o veículo depois NÃO altera leads passados.
 */
@Repository
public class ConcessionariaLeadRepository {

    private static final RowMapper<ConcessionariaLead> MAPPER = (rs, rn) -> new ConcessionariaLead(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("vehicle_id"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("customer_name"),
        rs.getString("customer_phone"),
        rs.getString("vehicle_brand"),
        rs.getString("vehicle_model"),
        (Integer) rs.getObject("vehicle_year"),
        rs.getInt("vehicle_price_cents"),
        rs.getString("payment_condition"),
        rs.getString("status"),
        (UUID) rs.getObject("salesperson_id"),
        rs.getString("lost_reason"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, company_id, vehicle_id, conversation_id, contact_id, customer_name, customer_phone, "
            + "vehicle_brand, vehicle_model, vehicle_year, vehicle_price_cents, payment_condition, "
            + "status, salesperson_id, lost_reason, notes, created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public ConcessionariaLeadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Nome do contato (cliente) — para snapshot no lead. */
    public Optional<String> contactName(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("select name from contacts where id = ? and company_id = ?",
                (rs, rn) -> rs.getString("name"), contactId, companyId)
            .stream().findFirst();
    }

    /** Telefone do contato (cliente) — para snapshot no lead. */
    public Optional<String> contactPhone(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("select phone_number from contacts where id = ? and company_id = ?",
                (rs, rn) -> rs.getString("phone_number"), contactId, companyId)
            .stream().findFirst();
    }

    public List<ConcessionariaLead> listByCompany(UUID companyId, String status, UUID vehicleId,
                                                  UUID contactId, UUID salespersonId, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from concessionaria_leads where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (vehicleId != null) { sql.append(" and vehicle_id = ?"); args.add(vehicleId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (salespersonId != null) { sql.append(" and salesperson_id = ?"); args.add(salespersonId); }
        sql.append(" order by created_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, UUID vehicleId, UUID contactId, UUID salespersonId) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from concessionaria_leads where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (vehicleId != null) { sql.append(" and vehicle_id = ?"); args.add(vehicleId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (salespersonId != null) { sql.append(" and salesperson_id = ?"); args.add(salespersonId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<ConcessionariaLead> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from concessionaria_leads where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Leads ATIVOS (novo/em_negociacao) do contato — p/ o contexto IA (anti-duplicidade leve). */
    public List<ConcessionariaLead> listActiveByContact(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
            "select " + COLS + " from concessionaria_leads where company_id = ? and contact_id = ? "
                + "and status in ('novo','em_negociacao') order by created_at desc",
            MAPPER, companyId, contactId);
    }

    /** Cria o lead 'novo' com snapshots (veículo + PREÇO do catálogo + cliente). */
    public ConcessionariaLead insertLead(UUID companyId, UUID vehicleId, UUID conversationId, UUID contactId,
                                         String customerName, String customerPhone, String vehicleBrand,
                                         String vehicleModel, Integer vehicleYear, int vehiclePriceCents,
                                         String paymentCondition, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into concessionaria_leads (company_id, vehicle_id, conversation_id, contact_id, "
                + "customer_name, customer_phone, vehicle_brand, vehicle_model, vehicle_year, "
                + "vehicle_price_cents, payment_condition, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, vehicleId, conversationId, contactId, customerName, customerPhone,
            vehicleBrand, vehicleModel, vehicleYear, vehiclePriceCents, paymentCondition, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Persiste a transição de status + lost_reason (se 'perdido') + status_updated_at. */
    public Optional<ConcessionariaLead> updateStatus(UUID companyId, UUID id, String newStatus, String lostReason) {
        int n = jdbcTemplate.update(
            "update concessionaria_leads set status = ?, lost_reason = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatus, lostReason, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Atribui (ou desvincula, se null) o vendedor do lead. */
    public Optional<ConcessionariaLead> assignSalesperson(UUID companyId, UUID id, UUID salespersonId) {
        int n = jdbcTemplate.update(
            "update concessionaria_leads set salesperson_id = ? where company_id = ? and id = ?",
            salespersonId, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }
}
