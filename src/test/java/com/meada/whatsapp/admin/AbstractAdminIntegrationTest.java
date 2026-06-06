package com.meada.whatsapp.admin;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Base dos testes de integração do painel admin. Herda toda a infra do
 * {@link AbstractIntegrationTest} (Testcontainers + @DynamicPropertySource com o
 * supabase.jwt-secret de teste) e adiciona:
 * <ul>
 *   <li>allowlist FIXA de super-admin ({@code superadmin@test.dev}) via @TestPropertySource
 *       — um contexto admin compartilhado por todos os testes admin (evita N contextos);
 *   <li>helpers {@code mintToken*} que geram JWT HS256 via nimbus (mesma lib do prod),
 *       assinados com {@link #TEST_JWT_SECRET} (o mesmo que o filtro verifica);
 *   <li>helper {@code seedTenantAdmin} que provisiona company + linha em public.users.
 * </ul>
 *
 * <p>A allowlist é definida via @DynamicPropertySource AQUI (não @TestPropertySource):
 * o @DynamicPropertySource tem precedência sobre @TestPropertySource, então a allowlist
 * só "vence" se vier por DynamicPropertySource. A classe-mãe NÃO registra
 * admin.super-admin-emails (justamente para não vencer este override).
 */
public abstract class AbstractAdminIntegrationTest extends AbstractIntegrationTest {

    /** Email na allowlist — cenário super-admin. */
    protected static final String SUPER_ADMIN_EMAIL = "superadmin@test.dev";
    /** Email FORA da allowlist — cenário tenant-admin (resolvido via public.users). */
    protected static final String TENANT_ADMIN_EMAIL = "tenant@test.dev";

    /** Allowlist de teste: SUPER_ADMIN_EMAIL é super-admin. Via DynamicPropertySource
     *  (não TestPropertySource) por causa da precedência — ver javadoc da classe. */
    @DynamicPropertySource
    static void adminAllowlist(DynamicPropertyRegistry registry) {
        registry.add("admin.super-admin-emails", () -> SUPER_ADMIN_EMAIL);
    }

    /** Secret diferente do TEST_JWT_SECRET, também >= 32 bytes (43) — MACSigner valida
     *  tamanho no assinar, não só no verificar. Para o caso invalid_signature. */
    private static final String WRONG_SECRET = "wrong-secret-also-32-bytes-long-please-yes";

    // ---- mint de tokens (API tipada) ----------------------------------------

    /**
     * Gera um JWT HS256 assinado com {@code secret}, claims email+sub e o exp dado.
     * {@code email} nullable: quando null, o claim é omitido (cobre invalid_claims).
     */
    protected String mintToken(String email, UUID sub, String secret, Date exp) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject(sub.toString());
            if (email != null) {
                claims.claim("email", email);
            }
            if (exp != null) {
                claims.expirationTime(exp);
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to mint test JWT", e);
        }
    }

    // ---- fronteira de segurança: API "raw" só para claims malformados -------

    /**
     * Variação "raw" para cobrir os 3 ramos de invalid_claims do filtro (email ausente,
     * sub ausente, sub não-UUID). API insegura: aceita {@code rawSub} como String para
     * permitir null/blank/não-UUID. Use só nos testes negativos de claims malformados;
     * para casos positivos, use {@link #mintToken(String, UUID, String, Date)}.
     */
    protected String mintTokenRawSub(String email, String rawSub, String secret, Date exp) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
            if (rawSub != null) {
                claims.subject(rawSub);
            }
            if (email != null) {
                claims.claim("email", email);
            }
            if (exp != null) {
                claims.expirationTime(exp);
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to mint raw test JWT", e);
        }
    }

    // ---- convenções por cenário ---------------------------------------------

    /** Token válido: exp = agora + 1h, assinado com o secret correto. */
    protected String mintValidToken(String email, UUID sub) {
        return mintToken(email, sub, TEST_JWT_SECRET, oneHourFromNow());
    }

    /** Token expirado: exp = agora - 1h, secret correto. */
    protected String mintExpiredToken(String email, UUID sub) {
        return mintToken(email, sub, TEST_JWT_SECRET, oneHourAgo());
    }

    /** Token assinado com secret ERRADO (→ invalid_signature). exp válido. */
    protected String mintTokenWithWrongSecret(String email, UUID sub) {
        return mintToken(email, sub, WRONG_SECRET, oneHourFromNow());
    }

    /** protected (não private): as subclasses de teste usam direto para montar exp. */
    protected static Date oneHourFromNow() {
        return new Date(System.currentTimeMillis() + 3_600_000L);
    }

    protected static Date oneHourAgo() {
        return new Date(System.currentTimeMillis() - 3_600_000L);
    }

    // ---- seed ---------------------------------------------------------------

    /**
     * Provisiona uma company + uma linha em public.users (id=userId, role 'admin') para
     * o cenário tenant-admin. Chamado só pelos testes que precisam da provisão (o caso
     * "user_not_provisioned" justamente NÃO chama). O TRUNCATE CASCADE do @BeforeEach da
     * classe-mãe limpa entre testes.
     *
     * @return o companyId criado, para o teste asseverar contra o MeResponse.
     */
    protected UUID seedTenantAdmin(String email, UUID userId) {
        UUID companyId = UUID.randomUUID();
        // public.users.id tem FK para auth.users(id) — a linha em auth.users precisa
        // existir antes (no bootstrap de teste, auth.users tem só a coluna id). O
        // truncate() da classe-mãe NÃO limpa auth.users, então ON CONFLICT DO NOTHING
        // torna o seed idempotente caso o mesmo userId reapareça entre testes.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", userId);
        jdbcTemplate.update(
            "insert into companies (id, name, slug) values (?, ?, ?)",
            companyId, "Empresa Teste", "empresa-teste-" + companyId);
        jdbcTemplate.update(
            "insert into users (id, company_id, email, role) values (?, ?, ?, 'admin')",
            userId, companyId, email);
        return companyId;
    }
}
