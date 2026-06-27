package com.meada.admin;

import com.meada.AbstractIntegrationTest;
import com.meada.admin.security.AdminTestJwksConfig;
import com.meada.admin.security.TestJwtKeys;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.Date;
import java.util.UUID;

/**
 * Base dos testes de integração do painel admin. Herda toda a infra do
 * {@link AbstractIntegrationTest} (Testcontainers + @DynamicPropertySource) e adiciona:
 * <ul>
 *   <li>allowlist FIXA de super-admin ({@code superadmin@test.dev}) via @TestPropertySource —
 *       funciona porque a classe-mãe NÃO registra admin.super-admin-emails no
 *       @DynamicPropertySource (que teria precedência); sem esse conflito, o
 *       @TestPropertySource vale;
 *   <li>{@link AdminTestJwksConfig} (via @Import) sobrescrevendo o JWKSource de prod por um
 *       local com a chave pública de teste ({@link TestJwtKeys});
 *   <li>helpers {@code mintToken*} que geram JWT ES256 via nimbus (mesma lib do prod),
 *       assinados com {@link TestJwtKeys#SIGNING_KEY} (a public correspondente está no
 *       JWKSource de teste → o filtro verifica por kid);
 *   <li>helper {@code seedTenantAdmin} que provisiona company + linha em public.users.
 * </ul>
 */
@Import(AdminTestJwksConfig.class)
@TestPropertySource(properties = "admin.super-admin-emails=superadmin@test.dev")
public abstract class AbstractAdminIntegrationTest extends AbstractIntegrationTest {

    /** Email na allowlist — cenário super-admin. */
    protected static final String SUPER_ADMIN_EMAIL = "superadmin@test.dev";
    /** Email FORA da allowlist — cenário tenant-admin (resolvido via public.users). */
    protected static final String TENANT_ADMIN_EMAIL = "tenant@test.dev";

    /** 2º par EC P-256, kid DIFERENTE do que está no JWKSource de teste — para o caso
     *  invalid_signature: o JWSVerificationKeySelector não acha a kid → falha. */
    private static final ECKey WRONG_SIGNING_KEY;

    static {
        try {
            WRONG_SIGNING_KEY = new ECKeyGenerator(Curve.P_256).keyID("test-key-id-wrong").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to generate wrong test EC key", e);
        }
    }

    // ---- mint de tokens (API tipada) ----------------------------------------

    /**
     * Gera um JWT ES256 assinado com {@code signingKey}, claims email+sub e o exp dado.
     * O header carrega a kid da {@code signingKey} — o filtro a usa para selecionar a chave
     * pública no JWKSource. {@code email} nullable: quando null, o claim é omitido.
     */
    protected String mintToken(String email, UUID sub, ECKey signingKey, Date exp) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject(sub.toString());
            if (email != null) {
                claims.claim("email", email);
            }
            if (exp != null) {
                claims.expirationTime(exp);
            }
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingKey.getKeyID())
                .build();
            SignedJWT jwt = new SignedJWT(header, claims.build());
            jwt.sign(new ECDSASigner(signingKey));
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
     * para casos positivos, use {@link #mintToken(String, UUID, ECKey, Date)}.
     */
    protected String mintTokenRawSub(String email, String rawSub, ECKey signingKey, Date exp) {
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
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingKey.getKeyID())
                .build();
            SignedJWT jwt = new SignedJWT(header, claims.build());
            jwt.sign(new ECDSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to mint raw test JWT", e);
        }
    }

    // ---- convenções por cenário ---------------------------------------------

    /** Token válido: exp = agora + 1h, assinado com a chave de teste (kid no JWKSource). */
    protected String mintValidToken(String email, UUID sub) {
        return mintToken(email, sub, TestJwtKeys.SIGNING_KEY, oneHourFromNow());
    }

    /** Token expirado: exp = agora - 1h, chave correta. */
    protected String mintExpiredToken(String email, UUID sub) {
        return mintToken(email, sub, TestJwtKeys.SIGNING_KEY, oneHourAgo());
    }

    /** Token assinado com chave ERRADA (kid ausente do JWKSource) → invalid_signature. */
    protected String mintTokenWithWrongKey(String email, UUID sub) {
        return mintToken(email, sub, WRONG_SIGNING_KEY, oneHourFromNow());
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
