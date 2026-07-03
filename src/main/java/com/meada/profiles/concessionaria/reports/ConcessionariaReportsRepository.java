package com.meada.profiles.concessionaria.reports;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agregações do dashboard comercial da concessionária (onda 1, backlog #10). service_role.
 * Funil de leads (snapshot por status), conversão lead→fechado na janela, desempenho por vendedor
 * (leads fechados + test-drives realizados), vendas por mês (veículo 'vendido' pelo
 * status_updated_at) e test-drives por status na janela.
 */
@Repository
public class ConcessionariaReportsRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConcessionariaReportsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Funil: contagem de leads por status ATUAL (snapshot, sem janela) + valor snapshotado. */
    public List<Map<String, Object>> leadFunnel(UUID companyId) {
        return jdbcTemplate.query(
            "select status, count(*) as n, coalesce(sum(vehicle_price_cents), 0) as amount "
                + "from concessionaria_leads where company_id = ? group by status order by n desc",
            (rs, rn) -> Map.<String, Object>of(
                "status", rs.getString("status"),
                "count", rs.getLong("n"),
                "totalCents", rs.getLong("amount")),
            companyId);
    }

    public record Conversion(long created, long closed) {}

    /** Conversão na janela: leads CRIADOS vs leads FECHADOS (status atual) desde {@code since}. */
    public Conversion conversion(UUID companyId, Instant since) {
        return jdbcTemplate.queryForObject(
            "select count(*) as created, "
                + "count(*) filter (where status = 'fechado') as closed "
                + "from concessionaria_leads where company_id = ? and created_at >= ?",
            (rs, rn) -> new Conversion(rs.getLong("created"), rs.getLong("closed")),
            companyId, Timestamp.from(since));
    }

    /** Por vendedor: leads fechados (valor) + test-drives realizados na janela. */
    public List<Map<String, Object>> bySalesperson(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select sp.name as salesperson, "
                + "count(distinct l.id) filter (where l.status = 'fechado') as closed_leads, "
                + "coalesce(sum(l.vehicle_price_cents) filter (where l.status = 'fechado'), 0) as closed_amount, "
                + "count(distinct t.id) filter (where t.status = 'realizado') as testdrives "
                + "from concessionaria_salespeople sp "
                + "left join concessionaria_leads l on l.salesperson_id = sp.id and l.status_updated_at >= ? "
                + "left join concessionaria_test_drives t on t.salesperson_id = sp.id and t.start_at >= ? "
                + "where sp.company_id = ? "
                + "group by sp.name order by closed_amount desc",
            (rs, rn) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("salesperson", rs.getString("salesperson"));
                row.put("closedLeads", rs.getLong("closed_leads"));
                row.put("closedCents", rs.getLong("closed_amount"));
                row.put("testDrives", rs.getLong("testdrives"));
                return row;
            },
            Timestamp.from(since), Timestamp.from(since), companyId);
    }

    /** Vendas por mês: veículos com status 'vendido', pelo mês do status_updated_at (valor de catálogo). */
    public List<Map<String, Object>> salesByMonth(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select to_char(status_updated_at at time zone 'America/Sao_Paulo', 'YYYY-MM') as month, "
                + "count(*) as n, coalesce(sum(price_cents), 0) as amount "
                + "from concessionaria_vehicles "
                + "where company_id = ? and status = 'vendido' and status_updated_at >= ? "
                + "group by 1 order by 1 asc",
            (rs, rn) -> Map.<String, Object>of(
                "month", rs.getString("month"),
                "count", rs.getLong("n"),
                "totalCents", rs.getLong("amount")),
            companyId, Timestamp.from(since));
    }

    /** Test-drives por status na janela (agendados desde {@code since}). */
    public List<Map<String, Object>> testDrivesByStatus(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select status, count(*) as n from concessionaria_test_drives "
                + "where company_id = ? and start_at >= ? group by status order by n desc",
            (rs, rn) -> Map.<String, Object>of(
                "status", rs.getString("status"),
                "count", rs.getLong("n")),
            companyId, Timestamp.from(since));
    }
}
