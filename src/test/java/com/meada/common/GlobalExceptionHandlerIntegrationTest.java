package com.meada.common;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste do {@link GlobalExceptionHandler} no profile DEV (default do
 * AbstractIntegrationTest). Payload inválido COM secret válido → 400 com corpo
 * detalhado (violations presente). O prod (corpo opaco) é coberto em classe
 * separada com @ActiveProfiles("prod").
 *
 * <p>Secret válido (header apikey=test-secret) para passar do WebhookSecretFilter
 * — queremos chegar à validação @Valid, não parar no 401.
 */
class GlobalExceptionHandlerIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/webhooks/evolution";
    private static final String SECRET = "test-secret";

    // Inválido: falta 'data' (@NotNull) e 'event' (@NotBlank).
    private static final String INVALID_PAYLOAD = """
        {"instance":"x"}
        """;

    @Test
    @DisplayName("dev: payload inválido com secret → 400, corpo detalhado com violations")
    void invalidPayload_dev_detailedBody() throws Exception {
        mockMvc.perform(post(URL)
                .header("apikey", SECRET)
                .contentType("application/json")
                .content(INVALID_PAYLOAD))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("validation_failed"))
            .andExpect(jsonPath("$.violations").isArray())
            .andExpect(jsonPath("$.violations[0].field").exists())
            .andExpect(jsonPath("$.violations[0].message").exists());
    }
}
