package com.meada.whatsapp.admin.security;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o {@link JwtAuthenticationFilter} de ponta a ponta, contra o endpoint mais
 * simples atrás do filtro ({@code GET /admin/me}). Cobre os 7 reasons cravados no
 * JavaDoc do filtro, com invalid_claims expandido em 3 sub-casos + 2 positivos = 11.
 *
 * <p>Casos negativos asseveram só status + body {error, reason} (responsabilidade do
 * filtro). O conteúdo do MeResponse é coberto pelo MeControllerIntegrationTest.
 */
class JwtAuthenticationFilterIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUB = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // ---- problemas de header ------------------------------------------------

    @Test
    @DisplayName("sem header Authorization → 401 missing_auth_header")
    void noAuthHeader_returns401() throws Exception {
        mockMvc.perform(get("/admin/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("missing_auth_header"));
    }

    @Test
    @DisplayName("header sem 'Bearer ' prefix → 401 malformed_auth_header")
    void headerWithoutBearerPrefix_returns401() throws Exception {
        // intencionalmente sem "Bearer " — pode ser qualquer outra string
        mockMvc.perform(get("/admin/me").header("Authorization", "Basic dXNlcjpwYXNz"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("malformed_auth_header"));
    }

    // ---- problemas de token -------------------------------------------------

    @Test
    @DisplayName("token não-parseável (gibberish) → 401 malformed_token")
    void tokenGibberish_returns401() throws Exception {
        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("malformed_token"));
    }

    @Test
    @DisplayName("assinatura inválida (secret errado) → 401 invalid_signature")
    void wrongSecret_returns401() throws Exception {
        String token = mintTokenWithWrongSecret(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("invalid_signature"));
    }

    @Test
    @DisplayName("token expirado → 401 token_expired")
    void expiredToken_returns401() throws Exception {
        String token = mintExpiredToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("token_expired"));
    }

    // ---- claims inválidos (3 ramos) -----------------------------------------

    @Test
    @DisplayName("token sem claim email → 401 invalid_claims")
    void tokenWithoutEmailIsInvalidClaims() throws Exception {
        String token = mintTokenRawSub(null, SUB.toString(), TEST_JWT_SECRET, oneHourFromNow());
        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("invalid_claims"));
    }

    @Test
    @DisplayName("token sem claim sub → 401 invalid_claims")
    void tokenWithoutSubIsInvalidClaims() throws Exception {
        String token = mintTokenRawSub(SUPER_ADMIN_EMAIL, null, TEST_JWT_SECRET, oneHourFromNow());
        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("invalid_claims"));
    }

    @Test
    @DisplayName("token com sub não-UUID → 401 invalid_claims")
    void tokenWithNonUuidSubIsInvalidClaims() throws Exception {
        String token = mintTokenRawSub(SUPER_ADMIN_EMAIL, "not-a-uuid", TEST_JWT_SECRET, oneHourFromNow());
        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("invalid_claims"));
    }

    // ---- provisão -----------------------------------------------------------

    @Test
    @DisplayName("token válido + sem provisão → 403 user_not_provisioned")
    void validTokenNotProvisioned_returns403() throws Exception {
        String token = mintValidToken(TENANT_ADMIN_EMAIL, SUB);   // não na allowlist, sem seed
        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("user_not_provisioned"));
    }

    // ---- positivos ----------------------------------------------------------

    @Test
    @DisplayName("token válido + email na allowlist → 200 super-admin")
    void validTokenAllowlist_returnsOk() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("super_admin"));
    }

    @Test
    @DisplayName("token válido + linha em public.users → 200 tenant-admin")
    void validTokenProvisioned_returnsOk() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("tenant_admin"));
    }
}
