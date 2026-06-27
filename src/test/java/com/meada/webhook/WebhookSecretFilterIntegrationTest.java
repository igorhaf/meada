package com.meada.webhook;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste E2E do {@link WebhookSecretFilter} via MockMvc (cadeia HTTP completa:
 * filtro → controller). Reproduz os 5 cenários antes validados só por curl manual,
 * tornando-os reproduzíveis no CI — regressão no filtro passa a ser pega.
 *
 * <p>Foco no FILTRO, não no service: os casos de secret VÁLIDO usam um payload
 * estruturalmente válido (passa o @Valid) mas com event != messages.upsert, que o
 * WebhookService ignora benignamente (IGNORED_NON_MESSAGE_EVENT → 200) sem tocar o
 * banco. Assim o 200 prova "a request atravessou o filtro", sem depender de seed
 * nem virar teste do service.
 *
 * <p>O caso sem-secret usa payload INVÁLIDO de propósito: se o filtro cortar (401),
 * prova que cortou ANTES da validação @Valid (que daria 400) e do service.
 *
 * <p>webhook.secret = "test-secret" (registrado no AbstractIntegrationTest).
 */
class WebhookSecretFilterIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/webhooks/evolution";
    private static final String SECRET = "test-secret";

    // Estruturalmente válido (passa @Valid): event/instance/data.key.id/remoteJid.
    // event != messages.upsert → service retorna IGNORED_NON_MESSAGE_EVENT (200, sem persistir).
    private static final String VALID_PAYLOAD = """
        {"event":"presence.update","instance":"x",
         "data":{"key":{"id":"EVT-1","remoteJid":"5511999990000@s.whatsapp.net","fromMe":false}}}
        """;

    // Estruturalmente inválido (falta data) → se passasse do filtro, daria 400.
    private static final String INVALID_PAYLOAD = """
        {"instance":"x"}
        """;

    // mockMvc herdado de AbstractIntegrationTest

    @Test
    @DisplayName("T1: payload inválido SEM secret → 401 (filtro corta antes do @Valid)")
    void noSecret_invalidPayload_unauthorized() throws Exception {
        mockMvc.perform(post(URL)
                .contentType("application/json")
                .content(INVALID_PAYLOAD))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T2: válido + header apikey correto → 200")
    void validHeader_ok() throws Exception {
        mockMvc.perform(post(URL)
                .header("apikey", SECRET)
                .contentType("application/json")
                .content(VALID_PAYLOAD))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("T3: válido + query param apikey correto (sem header) → 200")
    void validQueryParam_ok() throws Exception {
        mockMvc.perform(post(URL)
                .param("apikey", SECRET)
                .contentType("application/json")
                .content(VALID_PAYLOAD))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("T4: header apikey errado → 401")
    void wrongHeader_unauthorized() throws Exception {
        mockMvc.perform(post(URL)
                .header("apikey", "secret-errado")
                .contentType("application/json")
                .content(VALID_PAYLOAD))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T5: header correto + query param errado → 200 (header tem precedência)")
    void headerPrecedesQuery_ok() throws Exception {
        mockMvc.perform(post(URL)
                .header("apikey", SECRET)
                .param("apikey", "secret-errado")
                .contentType("application/json")
                .content(VALID_PAYLOAD))
            .andExpect(status().isOk());
    }
}
