package com.meada.profiles.concessionaria.reports;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.concessionaria.ConcessionariaProfileGuard;
import com.meada.profiles.concessionaria.ConcessionariaProfileGuard.WrongProfileException;
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
 * Dashboard comercial do tenant concessionaria (onda 1, backlog #10). TENANT + perfil
 * 'concessionaria' only. Janela em MESES (default 6, clamp 1..24); o funil de leads é snapshot.
 */
@RestController
public class ConcessionariaReportsController {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ConcessionariaReportsRepository repository;
    private final ConcessionariaProfileGuard profileGuard;

    public ConcessionariaReportsController(ConcessionariaReportsRepository repository,
                                           ConcessionariaProfileGuard profileGuard) {
        this.repository = repository;
        this.profileGuard = profileGuard;
    }

    @GetMapping("/api/concessionaria/reports/summary")
    public ResponseEntity<Object> summary(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "6") int months) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden", "reason", "forbidden_wrong_profile"));
        }
        int window = Math.min(Math.max(months, 1), 24);
        Instant since = LocalDate.now(TENANT_ZONE).withDayOfMonth(1).minusMonths(window - 1L)
            .atStartOfDay(TENANT_ZONE).toInstant();
        ConcessionariaReportsRepository.Conversion conv = repository.conversion(companyId, since);
        return ResponseEntity.ok(Map.of(
            "months", window,
            "leadsCreated", conv.created(),
            "leadsClosed", conv.closed(),
            "funnel", repository.leadFunnel(companyId),
            "bySalesperson", repository.bySalesperson(companyId, since),
            "salesByMonth", repository.salesByMonth(companyId, since),
            "testDrivesByStatus", repository.testDrivesByStatus(companyId, since)));
    }
}
