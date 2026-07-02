package com.meada.profiles.atelie.reports;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa as agregações do AtelieReportsRepository (onda 2, backlog #14): faturamento = propostas
 * REALIZADAS, valor LÍQUIDO (total − desconto); cancelada/aberta ficam de fora; agrupamento por
 * tipo e por artesão (null → "Sem atribuição" no painel).
 */
class AtelieReportsRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private AtelieReportsRepository repository;

    private static final UUID COMPANY = UUID.fromString("a7e00000-0000-0000-0000-000000000086");
    private UUID artisanId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'atelie')",
            COMPANY, "Atelie Rep", "atelie-rep");
        artisanId = UUID.randomUUID();
        jdbcTemplate.update("insert into atelie_artisans (id, company_id, name) values (?, ?, 'Rosa')",
            artisanId, COMPANY);
    }

    private void seedProposal(String status, String projectType, UUID artisan, int totalCents,
                              int discountCents, boolean closed) {
        jdbcTemplate.update(
            "insert into atelie_proposals (company_id, artisan_id, customer_name, project_type, status, "
                + "total_cents, discount_cents, closed_at) values (?, ?, 'Cliente', ?, ?, ?, ?, ?)",
            COMPANY, artisan, projectType, status, totalCents, discountCents,
            closed ? java.sql.Timestamp.from(Instant.now()) : null);
    }

    @Test
    @DisplayName("totais somam só REALIZADAS, com valor líquido; cancelada/aberta ficam de fora")
    void totals_netRevenue_onlyRealizada() {
        seedProposal("realizada", "costura", artisanId, 100000, 10000, true);   // líquido 90000
        seedProposal("realizada", "arte", null, 50000, 0, true);                // líquido 50000
        seedProposal("cancelada", "costura", artisanId, 999999, 0, true);       // fora (terminal ≠ realizada)
        seedProposal("orcada", "design", null, 888888, 0, false);               // fora (aberta)

        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        AtelieReportsRepository.Totals totals = repository.totals(COMPANY, since);
        assertThat(totals.count()).isEqualTo(2);
        assertThat(totals.totalCents()).isEqualTo(140000);

        List<Map<String, Object>> byType = repository.byProjectType(COMPANY, since);
        assertThat(byType).hasSize(2);
        assertThat(byType.get(0)).containsEntry("projectType", "costura").containsEntry("totalCents", 90000L);
        assertThat(byType.get(1)).containsEntry("projectType", "arte").containsEntry("totalCents", 50000L);

        List<Map<String, Object>> byArtisan = repository.byArtisan(COMPANY, since);
        assertThat(byArtisan).hasSize(2);
        assertThat(byArtisan.get(0)).containsEntry("artisanName", "Rosa").containsEntry("totalCents", 90000L);
        assertThat(byArtisan.get(1).get("artisanName")).isNull();   // sem atribuição

        List<Map<String, Object>> byMonth = repository.byMonth(COMPANY, since);
        assertThat(byMonth).hasSize(1);
        assertThat(byMonth.get(0)).containsEntry("totalCents", 140000L).containsEntry("count", 2L);
    }

    @Test
    @DisplayName("realizada FORA da janela (closed_at antigo) não entra")
    void outsideWindow_excluded() {
        jdbcTemplate.update(
            "insert into atelie_proposals (company_id, customer_name, project_type, status, total_cents, "
                + "discount_cents, closed_at) values (?, 'Cliente', 'costura', 'realizada', 10000, 0, "
                + "now() - interval '90 days')",
            COMPANY);
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        assertThat(repository.totals(COMPANY, since).count()).isZero();
    }
}
