package com.meada.whatsapp.metrics;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o export PDF de métricas (/admin/metrics/export.pdf, camada 5.23 #65) via HTTP. Leve:
 * só assevera status 200 + content-type application/pdf + corpo não-vazio para um tenant
 * autenticado; e 401 sem auth. NÃO inspeciona o interior do PDF (texto/layout) — basta provar
 * que o endpoint produz um PDF.
 */
class MetricsExportControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Test
    @DisplayName("GET /admin/metrics/export.pdf → 200 application/pdf não-vazio")
    void export_returnsPdf() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(get("/admin/metrics/export.pdf").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(result -> {
                int length = result.getResponse().getContentAsByteArray().length;
                org.hamcrest.MatcherAssert.assertThat(length, greaterThan(0));
            });
    }

    @Test
    @DisplayName("GET /admin/metrics/export.pdf sem auth → 401")
    void export_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/metrics/export.pdf"))
            .andExpect(status().isUnauthorized());
    }
}
