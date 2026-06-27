package com.meada.admin.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

/**
 * Chave EC P-256 de teste (FONTE ÚNICA), gerada uma vez por JVM. Usada nos dois lados da
 * verificação dos testes admin:
 *  - {@link #SIGNING_KEY} (com private) → o mintToken assina os tokens ES256;
 *  - a public correspondente → o AdminTestJwksConfig serve no JWKSource, e o filtro
 *    seleciona por kid e verifica.
 *
 * O kid é fixo ({@code test-key-id-1}); o header do token e a key no JWKSource usam o
 * mesmo kid → o JWSVerificationKeySelector resolve. Entre execuções da JVM o material
 * cripto muda, mas os testes não dependem do valor específico — só da consistência
 * assina-com-X / verifica-com-X dentro da mesma execução.
 */
public final class TestJwtKeys {

    public static final String KEY_ID = "test-key-id-1";

    /** Par EC P-256 (private + public) que assina os tokens de teste. */
    public static final ECKey SIGNING_KEY;

    static {
        try {
            SIGNING_KEY = new ECKeyGenerator(Curve.P_256).keyID(KEY_ID).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to generate test EC key", e);
        }
    }

    private TestJwtKeys() {
    }
}
