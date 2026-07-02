package com.meada.profiles.atelie.reports;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.atelie.AtelieProfileGuard;
import com.meada.profiles.atelie.AtelieProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Relatório de faturamento do tenant atelie (onda 2, backlog #14). TENANT + perfil 'atelie' only.
 * Janela em MESES (default 6, clamp 1..24), contada do dia 01 do mês mais antigo (fuso
 * America/Sao_Paulo). Faturamento = propostas REALIZADAS, valor líquido (total − desconto).
 */
@RestController
public class AtelieReportsController {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AtelieReportsRepository repository;
    private final AtelieProfileGuard profileGuard;

    public AtelieReportsController(AtelieReportsRepository repository, AtelieProfileGuard profileGuard) {
        this.repository = repository;
        this.profileGuard = profileGuard;
    }

    @GetMapping("/api/atelie/reports/summary")
    public ResponseEntity<Object> summary(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "6") int months) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden", "reason", "forbidden_wrong_profile"));
        }
        int window = Math.min(Math.max(months, 1), 24);
        Instant since = LocalDate.now(TENANT_ZONE).withDayOfMonth(1).minusMonths(window - 1L)
            .atStartOfDay(TENANT_ZONE).toInstant();
        AtelieReportsRepository.Totals totals = repository.totals(companyId, since);
        return ResponseEntity.ok(Map.of(
            "months", window,
            "totalCount", totals.count(),
            "totalCents", totals.totalCents(),
            "byMonth", repository.byMonth(companyId, since),
            "byType", repository.byProjectType(companyId, since),
            "byArtisan", repository.byArtisan(companyId, since)));
    }
}
