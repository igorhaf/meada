package com.meada.profiles.casamento.reports;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.casamento.CasamentoProfileGuard;
import com.meada.profiles.casamento.CasamentoProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Dashboard comercial do tenant casamento (onda 1, backlog #14). TENANT + perfil 'casamento' only.
 * Janela em MESES (default 12, clamp 1..24) para o realizado; a receita PREVISTA (fechadas por mês
 * do casamento) e o FUNIL são snapshots sem janela.
 */
@RestController
public class WeddingReportsController {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final WeddingReportsRepository repository;
    private final CasamentoProfileGuard profileGuard;

    public WeddingReportsController(WeddingReportsRepository repository, CasamentoProfileGuard profileGuard) {
        this.repository = repository;
        this.profileGuard = profileGuard;
    }

    @GetMapping("/api/casamento/reports/summary")
    public ResponseEntity<Object> summary(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "12") int months) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden", "reason", "forbidden_wrong_profile"));
        }
        int window = Math.min(Math.max(months, 1), 24);
        Instant since = LocalDate.now(TENANT_ZONE).withDayOfMonth(1).minusMonths(window - 1L)
            .atStartOfDay(TENANT_ZONE).toInstant();
        WeddingReportsRepository.Totals totals = repository.totals(companyId, since);
        return ResponseEntity.ok(Map.of(
            "months", window,
            "totalCount", totals.count(),
            "totalCents", totals.totalCents(),
            "byMonth", repository.byMonth(companyId, since),
            "upcomingByMonth", repository.upcomingByMonth(companyId),
            "byPlanner", repository.byPlanner(companyId, since),
            "funnel", repository.funnel(companyId)));
    }
}
