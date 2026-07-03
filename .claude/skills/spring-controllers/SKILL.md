---
name: spring-controllers
description: Padrões de Controller/Service/Repository do backend Spring Boot do Meada. Use ao criar ou editar classes em src/main/java — camadas por feature, DTOs como records, guard de perfil, Bean Validation, DI por construtor, JdbcTemplate (não JPA), REST.
---

# Backend Spring Boot (camadas e REST)

Padrão canônico (auditoria 2026-07: 186 controllers, 100% injeção por construtor — zero
`@Autowired`; DTOs 100% records; JdbcTemplate — o projeto NÃO usa JPA nem Lombok nem WebFlux).

## Pacote por feature, dentro do domínio

```
com.meada.profiles.<nicho>.<recurso>/
  <Recurso>.java                 // record de saída (espelha a tabela)
  <Recurso>Repository.java       // JdbcTemplate + RowMapper
  <Recurso>Service.java          // regras + exceções nested + auditoria
  <Recurso>Controller.java       // rotas + DTOs de request + mapeamento de erro
```

Core fora de profiles segue o mesmo shape (`com.meada.admin.companies`, `com.meada.outbound`...).

## Onde a lógica mora

- **Controller**: parse/validação de formato (datas ISO → `LocalDate`, flags `clearX`), guard de
  perfil, tradução exceção→HTTP. NADA de regra de negócio.
- **Service**: regras, validações de domínio, `@Transactional`, `AuditLogger`, invalidação de
  cache de contexto da IA. Exceções de domínio como **nested static RuntimeException**.
- **Repository**: SQL via `JdbcTemplate`, `RowMapper` estático, escopo `company_id` em TODO WHERE
  (defesa multi-tenant), derivados MATERIALIZADOS na mesma transação (totais, end_at).

## DI por construtor (sem @Autowired)

```java
// ✅ CERTO — único construtor, campos final
private final AtelieCouponRepository repository;
private final AuditLogger auditLogger;

public AtelieCouponService(AtelieCouponRepository repository, AuditLogger auditLogger) {
    this.repository = repository;
    this.auditLogger = auditLogger;
}

// ❌ ERRADO — field injection
@Autowired private AtelieCouponRepository repository;
```

## Controller: shape canônico

```java
@RestController
public class AtelieCouponController {

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    // DTOs de request como records aninhados, com Bean Validation jakarta:
    public record CreateRequest(
        @NotBlank @Size(max = 40) String code,
        @NotNull Integer value) {}

    @PostMapping("/api/atelie/coupons")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);   // guard SEMPRE primeiro
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), ...));
        } catch (InvalidCouponException e) {
            return error(400, "Bad Request", "invalid_coupon");
        } catch (DuplicateCouponException e) {
            return error(409, "Conflict", "duplicate_coupon");
        }
    }
}
```

## Convenções REST

- Rotas: `/api/<nicho>/<recurso>` (tenant, plural kebab-case) e `/admin/...` (root). Toda rota
  nova de tenant precisa estar autenticada pelo `JwtAuthenticationFilter` (prefixo do perfil).
- Verbos: GET lista/detalhe; POST cria (201); PATCH parcial (flags `clearX` para anular campo);
  PUT upsert de config 1:1; DELETE → 204. Sub-recurso de status: `PATCH .../{id}/status`.
- Listas paginadas: `Map.of("items", ..., "total", total, "page", page, "pageSize", size)`;
  listas simples: `Map.of("items", ...)`.
- Status codes: 400 formato/domínio inválido · 403 perfil errado · 404 não encontrado ·
  409 conflito (duplicado, transição inválida, slot ocupado) · 422 pré-condição de negócio.
