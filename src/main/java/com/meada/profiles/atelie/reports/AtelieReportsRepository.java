package com.meada.profiles.atelie.reports;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agregações do relatório de faturamento do atelie (onda 2, backlog #14). service_role. Faturamento
 * = propostas REALIZADAS (peça entregue), valor LÍQUIDO (total − desconto), agrupado por mês do
 * fechamento ({@code closed_at}), por tipo de projeto e por artesão. Sem DDL — só leitura.
 */
@Repository
public class AtelieReportsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AtelieReportsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record Totals(long count, long totalCents) {}

    public Totals totals(UUID companyId, Instant since) {
        return jdbcTemplate.queryForObject(
            "select count(*) as n, coalesce(sum(total_cents - discount_cents), 0) as revenue "
                + "from atelie_proposals where company_id = ? and status = 'realizada' and closed_at >= ?",
            (rs, rn) -> new Totals(rs.getLong("n"), rs.getLong("revenue")),
            companyId, Timestamp.from(since));
    }

    /** Uma linha por mês (yyyy-MM, fuso America/Sao_Paulo), ordenada do mais antigo ao mais novo. */
    public List<Map<String, Object>> byMonth(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select to_char(closed_at at time zone 'America/Sao_Paulo', 'YYYY-MM') as month, "
                + "count(*) as n, coalesce(sum(total_cents - discount_cents), 0) as revenue "
                + "from atelie_proposals where company_id = ? and status = 'realizada' and closed_at >= ? "
                + "group by 1 order by 1 asc",
            (rs, rn) -> Map.<String, Object>of(
                "month", rs.getString("month"),
                "count", rs.getLong("n"),
                "totalCents", rs.getLong("revenue")),
            companyId, Timestamp.from(since));
    }

    public List<Map<String, Object>> byProjectType(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select project_type, count(*) as n, coalesce(sum(total_cents - discount_cents), 0) as revenue "
                + "from atelie_proposals where company_id = ? and status = 'realizada' and closed_at >= ? "
                + "group by project_type order by revenue desc",
            (rs, rn) -> Map.<String, Object>of(
                "projectType", rs.getString("project_type"),
                "count", rs.getLong("n"),
                "totalCents", rs.getLong("revenue")),
            companyId, Timestamp.from(since));
    }

    /** Propostas sem artesão atribuído aparecem como artisanName null (o painel rotula "Sem atribuição"). */
    public List<Map<String, Object>> byArtisan(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select ar.name as artisan_name, count(*) as n, "
                + "coalesce(sum(p.total_cents - p.discount_cents), 0) as revenue "
                + "from atelie_proposals p left join atelie_artisans ar on ar.id = p.artisan_id "
                + "where p.company_id = ? and p.status = 'realizada' and p.closed_at >= ? "
                + "group by ar.name order by revenue desc",
            (rs, rn) -> {
                java.util.HashMap<String, Object> row = new java.util.HashMap<>();
                row.put("artisanName", rs.getString("artisan_name"));
                row.put("count", rs.getLong("n"));
                row.put("totalCents", rs.getLong("revenue"));
                return row;
            },
            companyId, Timestamp.from(since));
    }
}
