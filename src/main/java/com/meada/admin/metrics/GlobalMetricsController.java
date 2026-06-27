package com.meada.admin.metrics;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Métricas GLOBAIS (cross-tenant) da plataforma para o super-admin (camada 6.3).
 * SUPER-ADMIN ONLY. READ-only (sem mutações, sem AdminActionLogger).
 *
 * <p>Substitui o placeholder da tela /dashboard/metrics no branch super_admin. Consome os
 * tokens persistidos pela 6.2.5 (messages.tokens_in/tokens_out) — {@code geminiTokensThisMonth}
 * e {@code geminiTokensLast30d} são SOMAS reais de coalesce(tokens, 0), então mensagens sem IA
 * (NULL) não inflam o consumo.
 *
 * <p>Response (GET /admin/metrics/global):
 * <ul>
 *   <li>KPIs: totalCompanies, activeCompanies, totalMessages30d, totalConversations30d,
 *       geminiTokensThisMonth (mês calendário), geminiTokensLast30d.
 *   <li>comparison: messages/companies do mês atual vs anterior + delta %.
 *   <li>topTenants: top 5 empresas por mensagens nos últimos 30d.
 *   <li>atRisk: empresas sem mensagem há > 30 dias (lastActivityAt nullable).
 *   <li>companiesCreatedPerMonth: últimos 6 meses (array {month, count}).
 * </ul>
 *
 * <p>Janelas: "30 dias" = {@code now() - interval '30 days'} (rolling). "Mês"/"mês anterior" =
 * mês calendário via {@code date_trunc('month', now())}. Consistente com a comparação do tenant.
 */
@RestController
public class GlobalMetricsController {

    private final JdbcTemplate jdbcTemplate;

    public GlobalMetricsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static boolean notSuper(AuthenticatedUser u) {
        return u.role() != AdminRole.SUPER_ADMIN;
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_super_admin"));
    }

    /** % de variação (atual vs anterior) arredondado a 1 casa; 0 quando o anterior é 0. */
    private static double deltaPct(long current, long previous) {
        if (previous == 0) {
            return 0.0;
        }
        return Math.round(((double) (current - previous) / previous) * 1000.0) / 10.0;
    }

    @GetMapping("/admin/metrics/global")
    public ResponseEntity<Object> global(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (notSuper(user)) return forbidden();

        // ---- KPIs ------------------------------------------------------------
        long totalCompanies = count("select count(*) from companies");
        long activeCompanies = count("select count(*) from companies where status = 'active'");
        long totalMessages30d = count(
            "select count(*) from messages where created_at >= now() - interval '30 days'");
        long totalConversations30d = count(
            "select count(*) from conversations where created_at >= now() - interval '30 days'");
        long tokensThisMonth = count(
            "select coalesce(sum(coalesce(tokens_in,0) + coalesce(tokens_out,0)), 0) "
                + "from messages where created_at >= date_trunc('month', now())");
        long tokensLast30d = count(
            "select coalesce(sum(coalesce(tokens_in,0) + coalesce(tokens_out,0)), 0) "
                + "from messages where created_at >= now() - interval '30 days'");

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("totalCompanies", totalCompanies);
        kpis.put("activeCompanies", activeCompanies);
        kpis.put("totalMessages30d", totalMessages30d);
        kpis.put("totalConversations30d", totalConversations30d);
        kpis.put("geminiTokensThisMonth", tokensThisMonth);
        kpis.put("geminiTokensLast30d", tokensLast30d);

        // ---- comparação mês a mês -------------------------------------------
        long messagesThisMonth = count(
            "select count(*) from messages where created_at >= date_trunc('month', now())");
        long messagesLastMonth = count(
            "select count(*) from messages where created_at >= date_trunc('month', now()) - interval '1 month' "
                + "and created_at < date_trunc('month', now())");
        long companiesThisMonth = count(
            "select count(*) from companies where created_at >= date_trunc('month', now())");
        long companiesLastMonth = count(
            "select count(*) from companies where created_at >= date_trunc('month', now()) - interval '1 month' "
                + "and created_at < date_trunc('month', now())");

        Map<String, Object> comparison = new HashMap<>();
        comparison.put("messagesThisMonth", messagesThisMonth);
        comparison.put("messagesLastMonth", messagesLastMonth);
        comparison.put("messagesDeltaPct", deltaPct(messagesThisMonth, messagesLastMonth));
        comparison.put("companiesThisMonth", companiesThisMonth);
        comparison.put("companiesLastMonth", companiesLastMonth);
        comparison.put("companiesDeltaPct", deltaPct(companiesThisMonth, companiesLastMonth));

        // ---- top tenants por volume (últimos 30d) ---------------------------
        List<Map<String, Object>> topTenants = jdbcTemplate.query(
            "select c.id, c.name, count(m.id) as cnt from companies c "
                + "join messages m on m.company_id = c.id and m.created_at >= now() - interval '30 days' "
                + "group by c.id, c.name order by cnt desc, c.name asc limit 5",
            (rs, rn) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("name", rs.getString("name"));
                m.put("messagesLast30d", rs.getLong("cnt"));
                return m;
            });

        // ---- tenants em risco (sem mensagem há > 30 dias) -------------------
        // LEFT JOIN para o max(created_at); HAVING captura tanto "última mensagem < 30d atrás"
        // quanto "nunca teve mensagem" (max IS NULL). lastActivityAt nullable no payload.
        List<Map<String, Object>> atRisk = jdbcTemplate.query(
            "select c.id, c.name, max(m.created_at) as last_activity from companies c "
                + "left join messages m on m.company_id = c.id "
                + "group by c.id, c.name "
                + "having max(m.created_at) is null or max(m.created_at) < now() - interval '30 days' "
                + "order by last_activity asc nulls first, c.name asc",
            (rs, rn) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("name", rs.getString("name"));
                java.sql.Timestamp ts = rs.getTimestamp("last_activity");
                m.put("lastActivityAt", ts != null ? ts.toInstant().toString() : null);
                return m;
            });

        // ---- crescimento: empresas criadas por mês (últimos 6 meses) --------
        // generate_series garante 6 buckets mesmo nos meses sem nenhuma empresa criada (count 0).
        List<Map<String, Object>> growth = jdbcTemplate.query(
            "select to_char(g.month, 'YYYY-MM') as month, count(c.id) as cnt "
                + "from generate_series(date_trunc('month', now()) - interval '5 months', "
                + "date_trunc('month', now()), interval '1 month') as g(month) "
                + "left join companies c on date_trunc('month', c.created_at) = g.month "
                + "group by g.month order by g.month asc",
            (rs, rn) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("month", rs.getString("month"));
                m.put("count", rs.getLong("cnt"));
                return m;
            });

        Map<String, Object> body = new HashMap<>();
        body.put("kpis", kpis);
        body.put("comparison", comparison);
        body.put("topTenants", topTenants);
        body.put("atRisk", atRisk);
        body.put("companiesCreatedPerMonth", growth);
        return ResponseEntity.ok(body);
    }

    private long count(String sql) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class);
        return n == null ? 0L : n;
    }
}
