package com.meada.profiles.casamento.reports;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agregações do dashboard comercial do casamento (onda 1, backlog #14). service_role. Receita
 * REALIZADA = propostas 'realizada' (líquido: total − desconto) por mês do fechamento; receita
 * PREVISTA = propostas 'fechada' por mês do wedding_date (contratos assinados a realizar); funil =
 * contagem por status atual; por assessor = realizadas líquidas (null = "Sem atribuição").
 */
@Repository
public class WeddingReportsRepository {

    private final JdbcTemplate jdbcTemplate;

    public WeddingReportsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record Totals(long count, long totalCents) {}

    public Totals totals(UUID companyId, Instant since) {
        return jdbcTemplate.queryForObject(
            "select count(*) as n, coalesce(sum(total_cents - discount_cents), 0) as revenue "
                + "from wedding_proposals where company_id = ? and status = 'realizada' and closed_at >= ?",
            (rs, rn) -> new Totals(rs.getLong("n"), rs.getLong("revenue")),
            companyId, Timestamp.from(since));
    }

    /** Receita realizada por mês (yyyy-MM do closed_at, fuso America/Sao_Paulo). */
    public List<Map<String, Object>> byMonth(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select to_char(closed_at at time zone 'America/Sao_Paulo', 'YYYY-MM') as month, "
                + "count(*) as n, coalesce(sum(total_cents - discount_cents), 0) as revenue "
                + "from wedding_proposals where company_id = ? and status = 'realizada' and closed_at >= ? "
                + "group by 1 order by 1 asc",
            (rs, rn) -> Map.<String, Object>of(
                "month", rs.getString("month"),
                "count", rs.getLong("n"),
                "totalCents", rs.getLong("revenue")),
            companyId, Timestamp.from(since));
    }

    /** Receita PREVISTA: contratos fechados a realizar, por mês do wedding_date (líquido). */
    public List<Map<String, Object>> upcomingByMonth(UUID companyId) {
        return jdbcTemplate.query(
            "select to_char(wedding_date, 'YYYY-MM') as month, count(*) as n, "
                + "coalesce(sum(total_cents - discount_cents), 0) as revenue "
                + "from wedding_proposals where company_id = ? and status = 'fechada' and wedding_date is not null "
                + "group by 1 order by 1 asc",
            (rs, rn) -> Map.<String, Object>of(
                "month", rs.getString("month"),
                "count", rs.getLong("n"),
                "totalCents", rs.getLong("revenue")),
            companyId);
    }

    /** Realizadas líquidas por assessor (null = "Sem atribuição" no painel). */
    public List<Map<String, Object>> byPlanner(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select pl.name as planner_name, count(*) as n, "
                + "coalesce(sum(p.total_cents - p.discount_cents), 0) as revenue "
                + "from wedding_proposals p left join wedding_planners pl on pl.id = p.planner_id "
                + "where p.company_id = ? and p.status = 'realizada' and p.closed_at >= ? "
                + "group by pl.name order by revenue desc",
            (rs, rn) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("plannerName", rs.getString("planner_name"));
                row.put("count", rs.getLong("n"));
                row.put("totalCents", rs.getLong("revenue"));
                return row;
            },
            companyId, Timestamp.from(since));
    }

    /** Funil comercial: contagem de propostas por status ATUAL (snapshot, sem janela). */
    public List<Map<String, Object>> funnel(UUID companyId) {
        return jdbcTemplate.query(
            "select status, count(*) as n, coalesce(sum(total_cents - discount_cents), 0) as revenue "
                + "from wedding_proposals where company_id = ? group by status order by n desc",
            (rs, rn) -> Map.<String, Object>of(
                "status", rs.getString("status"),
                "count", rs.getLong("n"),
                "totalCents", rs.getLong("revenue")),
            companyId);
    }
}
