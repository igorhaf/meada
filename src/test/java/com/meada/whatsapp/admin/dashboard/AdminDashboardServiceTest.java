package com.meada.whatsapp.admin.dashboard;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link AdminDashboardService} contra PostgreSQL real (Testcontainers).
 * As contagens são GLOBAIS (todas as empresas) — o serviço roda como service_role sem filtro
 * de tenant. Seeds explícitos com created_at controlado para os recortes temporais.
 */
class AdminDashboardServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AdminDashboardService service;

    private UUID seedCompany(String slug, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into companies (id, name, slug, status) values (?, ?, ?, ?)",
            id, "Empresa " + slug, slug + "-" + id, status);
        return id;
    }

    private UUID seedContactAndConversation(UUID companyId, String convStatus) {
        UUID instance = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, companyId, "inst-" + instance, "tok");
        UUID contact = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, companyId, "+5511" + Math.abs(contact.hashCode()), "C");
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
                + "values (?, ?, ?, ?, ?, 'ai')",
            conv, companyId, contact, instance, convStatus);
        return conv;
    }

    private void seedMessage(UUID companyId, UUID conversationId, Instant createdAt) {
        jdbcTemplate.update(
            "insert into messages (company_id, conversation_id, direction, sender, content, created_at) "
                + "values (?, ?, 'inbound', 'contact', 'oi', ?)",
            companyId, conversationId, Timestamp.from(createdAt));
    }

    @Test
    @DisplayName("getOverview: contagens corretas (3 empresas ativas, 2 com msg hoje)")
    void getOverview_correctCounts() {
        UUID a = seedCompany("a", "active");
        UUID b = seedCompany("b", "active");
        seedCompany("c", "active");
        UUID convA = seedContactAndConversation(a, "open");
        UUID convB = seedContactAndConversation(b, "open");
        seedMessage(a, convA, Instant.now());
        seedMessage(b, convB, Instant.now());

        AdminOverviewResponse r = service.getOverview();

        assertThat(r.activeCompanies()).isEqualTo(3);
        assertThat(r.messagesToday()).isEqualTo(2);
        assertThat(r.openConversations()).isEqualTo(2);
        assertThat(r.openConversationsCompanyCount()).isEqualTo(2);
        // tokens não persistidos → 0 honesto (ver DTO).
        assertThat(r.geminiTokensThisMonth()).isZero();
    }

    @Test
    @DisplayName("companiesCreatedThisMonth: filtra pelo mês corrente")
    void companiesThisMonth_filtersCurrentMonth() {
        seedCompany("novo", "active"); // created_at default now() → mês corrente
        // empresa antiga: força created_at no mês passado
        UUID old = seedCompany("velho", "active");
        jdbcTemplate.update("update companies set created_at = ? where id = ?",
            Timestamp.from(Instant.now().minus(40, ChronoUnit.DAYS)), old);

        AdminOverviewResponse r = service.getOverview();

        // 'novo' conta; 'velho' não. (pode haver outras do seed global, mas a velha NÃO conta)
        assertThat(r.companiesCreatedThisMonth()).isGreaterThanOrEqualTo(1);
        long thisMonth = jdbcTemplate.queryForObject(
            "select count(*) from companies where created_at >= date_trunc('month', now())", Long.class);
        assertThat(r.companiesCreatedThisMonth()).isEqualTo(thisMonth);
    }

    @Test
    @DisplayName("messagesYesterday: filtra o dia anterior (não conta hoje)")
    void messagesYesterday_filtersPreviousDay() {
        UUID a = seedCompany("a", "active");
        UUID conv = seedContactAndConversation(a, "open");
        seedMessage(a, conv, Instant.now());                                   // hoje
        seedMessage(a, conv, Instant.now().minus(1, ChronoUnit.DAYS));         // ontem (24h atrás)

        AdminOverviewResponse r = service.getOverview();

        assertThat(r.messagesToday()).isEqualTo(1);
        assertThat(r.messagesYesterday()).isEqualTo(1);
    }

    @Test
    @DisplayName("alerts: lista vazia em estado saudável (instâncias connected, dry-run false em teste)")
    void alerts_emptyWhenHealthy() {
        UUID a = seedCompany("a", "active");
        // instância CONNECTED (saudável) — não dispara o warning de offline.
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token, status) "
                + "values (?, ?, ?, ?, 'connected')",
            UUID.randomUUID(), a, "inst-ok", "tok");

        AdminOverviewResponse r = service.getOverview();

        // dry-run default false no contexto de teste → sem alerta de webhook; instância
        // connected → sem warning. Estado saudável = lista vazia.
        assertThat(r.alerts()).isEmpty();
    }
}
