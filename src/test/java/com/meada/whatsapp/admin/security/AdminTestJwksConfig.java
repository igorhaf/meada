package com.meada.whatsapp.admin.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Sobrescreve o {@link JWKSource} de produção (RemoteJWKSet do Supabase) por um local
 * que serve só a chave PÚBLICA de teste ({@link TestJwtKeys}). Assim os testes admin
 * verificam ES256 sem bater no endpoint JWKS real — o transporte HTTP do JWKS é do
 * nimbus (testá-lo seria testar a lib, não o nosso código).
 *
 * Importado por @Import no AbstractAdminIntegrationTest. @Primary garante que este bean
 * vence o de prod (que fica instanciado mas inerte — o RemoteJWKSet é lazy).
 */
@TestConfiguration
public class AdminTestJwksConfig {

    @Bean
    @Primary
    public JWKSource<SecurityContext> testJwkSource() {
        // só a public key (toPublicJWK) entra no JWKSet — o verificador não precisa da private.
        return new ImmutableJWKSet<>(new JWKSet(TestJwtKeys.SIGNING_KEY.toPublicJWK()));
    }
}
