package com.meada.whatsapp.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Autentica requests a {@code /admin/**} validando o JWT do Supabase (HS256) e
 * resolvendo a identidade de forma EAGER (decisão B2): produz um {@link AuthenticatedUser}
 * completo que os controllers leem via {@code @RequestAttribute("authenticatedUser")}.
 *
 * <p>@Order(2): roda depois do WebhookSecretFilter (@Order(1)); cada filtro só atua no
 * seu prefixo de path (shouldNotFilter). Espelha o padrão do WebhookSecretFilter.
 *
 * <p>Divisão 401 vs 403 (ver DEVELOPMENT.md seção 4.1): 401 = autenticação (quem é?);
 * 403 user_not_provisioned = token válido mas tenant-admin sem linha em public.users
 * (o filtro não consegue construir o AuthenticatedUser). O 403 forbidden_not_super_admin
 * é de AUTORIZAÇÃO e mora no controller, não aqui.
 *
 * <p>Controle de fluxo via {@link AuthRejectException} interna: cada validação lança;
 * doFilterInternal captura num ponto e escreve a resposta de erro (reject). Mantém o
 * happy path linear.
 */
@Component
@Order(2)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String ADMIN_PATH_PREFIX = "/admin/";
    private static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTH_USER_ATTRIBUTE = "authenticatedUser";

    private static final String SELECT_COMPANY_ID =
        "select company_id from users where id = ?";

    private final MACVerifier macVerifier;
    private final Set<String> allowlistLower;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(AdminProperties adminProperties,
                                   @Value("${supabase.jwt-secret}") String jwtSecret,
                                   JdbcTemplate jdbcTemplate,
                                   ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        try {
            // HS256 exige secret >= 256 bits (32 bytes); MACVerifier lança no boot se curto.
            this.macVerifier = new MACVerifier(jwtSecret.getBytes(StandardCharsets.UTF_8));
        } catch (JOSEException e) {
            throw new IllegalStateException(
                "Invalid supabase.jwt-secret for HS256 (must be >= 32 bytes)", e);
        }
        // allowlist normalizada (lowercase) uma vez no boot; null-safe se a key faltar no YAML.
        this.allowlistLower = Objects.requireNonNullElse(
                adminProperties.superAdminEmails(), List.<String>of())
            .stream()
            .map(s -> s.toLowerCase())
            .collect(Collectors.toSet());
    }

    /** Só filtra /admin/**. Demais rotas (webhook, futuro health) passam direto. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuthenticatedUser user;
        try {
            String token = extractBearerToken(request);
            VerifiedClaims claims = parseAndVerify(token);
            user = resolveUser(claims);
        } catch (AuthRejectException e) {
            reject(request, response, e.status, e.reason);
            return;
        }
        request.setAttribute(AUTH_USER_ATTRIBUTE, user);
        filterChain.doFilter(request, response);
    }

    /**
     * Token do header Authorization. Ausente → 401 missing; presente mas sem "Bearer "
     * → 401 malformed.
     *
     * <p>Comparação de "Bearer " é CASE-SENSITIVE intencionalmente. A RFC 6750 permite
     * case-insensitive, mas nosso ecossistema é fechado (o apiFetch do frontend sempre
     * manda "Bearer " exato); qualquer caller que mande "bearer "/"BEARER " está fora do
     * padrão e merece o 401 — permissividade só mascararia cliente mal configurado.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            throw new AuthRejectException(401, "missing_auth_header");
        }
        if (!header.startsWith(BEARER_PREFIX)) {
            throw new AuthRejectException(401, "malformed_auth_header");
        }
        return header.substring(BEARER_PREFIX.length());
    }

    /** Parseia, verifica assinatura HS256, valida exp e extrai email+userId. */
    private VerifiedClaims parseAndVerify(String token) {
        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new AuthRejectException(401, "malformed_token");
        }

        try {
            if (!signedJWT.verify(macVerifier)) {
                throw new AuthRejectException(401, "invalid_signature");
            }
        } catch (JOSEException e) {
            throw new AuthRejectException(401, "invalid_signature");
        }

        JWTClaimsSet claims;
        try {
            claims = signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new AuthRejectException(401, "malformed_token");
        }

        // verify() só checa assinatura — exp é validado manualmente.
        Date exp = claims.getExpirationTime();
        if (exp == null || exp.before(new Date())) {
            throw new AuthRejectException(401, "token_expired");
        }

        String email;
        try {
            email = claims.getStringClaim("email");
        } catch (ParseException e) {
            throw new AuthRejectException(401, "invalid_claims");
        }
        if (email == null || email.isBlank()) {
            throw new AuthRejectException(401, "invalid_claims");
        }

        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new AuthRejectException(401, "invalid_claims");
        }

        UUID userId;
        try {
            userId = UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new AuthRejectException(401, "invalid_claims");
        }

        return new VerifiedClaims(email, userId);
    }

    /**
     * Resolve a identidade (eager). Allowlist (lowercase) checada ANTES do banco:
     * super-admin pula o SELECT (otimização B2). Tenant-admin sem linha em public.users
     * → 403 user_not_provisioned.
     */
    private AuthenticatedUser resolveUser(VerifiedClaims claims) {
        if (allowlistLower.contains(claims.email().toLowerCase())) {
            return new AuthenticatedUser(claims.email(), claims.userId(), AdminRole.SUPER_ADMIN, null);
        }
        try {
            UUID companyId = jdbcTemplate.queryForObject(
                SELECT_COMPANY_ID, (rs, rowNum) -> (UUID) rs.getObject("company_id"),
                claims.userId());
            return new AuthenticatedUser(
                claims.email(), claims.userId(), AdminRole.TENANT_ADMIN, companyId);
        } catch (EmptyResultDataAccessException e) {
            throw new AuthRejectException(403, "user_not_provisioned");
        }
    }

    /** Escreve a resposta de erro direto (o filtro não passa pelo GlobalExceptionHandler). */
    private void reject(HttpServletRequest request, HttpServletResponse response,
                        int status, String reason) throws IOException {
        log.warn("admin auth rejected status={} reason={} path={}",
            status, reason, request.getRequestURI());
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String errorText = status == 403 ? "Forbidden" : "Unauthorized";
        objectMapper.writeValue(
            response.getWriter(), Map.of("error", errorText, "reason", reason));
    }

    /** Claims já validadas e tipadas — detalhe interno do filtro. */
    private record VerifiedClaims(String email, UUID userId) {
    }

    /** Sinaliza rejeição com status HTTP + reason; capturada em doFilterInternal. */
    private static final class AuthRejectException extends RuntimeException {
        final int status;
        final String reason;

        AuthRejectException(int status, String reason) {
            super(reason);
            this.status = status;
            this.reason = reason;
        }
    }
}
