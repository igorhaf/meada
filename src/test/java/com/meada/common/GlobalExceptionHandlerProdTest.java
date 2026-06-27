package com.meada.common;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste do {@link GlobalExceptionHandler} no profile PROD — comportamento de
 * SEGURANÇA: o corpo de erro NÃO revela a estrutura interna (sem violations) a um
 * cliente externo. Classe separada porque @ActiveProfiles é por-classe e força um
 * 2º contexto Spring (prod) — os testes dev seguem no contexto dev.
 *
 * <p>Este teste existe justamente para travar regressão de segurança: se alguém
 * inverter o if do profile no handler, o dev test passaria e só ESTE pegaria o
 * vazamento de estrutura em prod.
 */
@ActiveProfiles("prod")
class GlobalExceptionHandlerProdTest extends AbstractIntegrationTest {

    private static final String URL = "/webhooks/evolution";
    private static final String SECRET = "test-secret";

    private static final String INVALID_PAYLOAD = """
        {"instance":"x"}
        """;

    @Test
    @DisplayName("prod: payload inválido com secret → 400, corpo OPACO (sem violations)")
    void invalidPayload_prod_opaqueBody() throws Exception {
        mockMvc.perform(post(URL)
                .header("apikey", SECRET)
                .contentType("application/json")
                .content(INVALID_PAYLOAD))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("validation_failed"))
            // violations NÃO deve existir em prod (não vaza estrutura interna)
            .andExpect(jsonPath("$.violations").doesNotExist());
    }
}
