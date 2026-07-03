---
name: spring-error-handling
description: Padrão de tratamento de erros do backend do Meada. Use ao lançar/mapear exceções, definir códigos reason, mexer em GlobalExceptionHandler ou desenhar respostas de erro da API Spring.
---

# Tratamento de erros (contrato `{error, reason}`)

## O contrato de erro

TODA resposta de erro da API tem o mesmo corpo:

```json
{ "error": "Conflict", "reason": "duplicate_coupon" }
```

- `error`: o nome HTTP genérico ("Bad Request", "Forbidden", "Not Found", "Conflict").
- `reason`: código snake_case ESTÁVEL, específico do domínio — é o contrato que o frontend
  usa no `ApiError.reason` para mensagens amigáveis. NUNCA renomear um reason existente.

## Duas camadas (ambas canônicas — não substituir uma pela outra)

1. **Catch local no controller** (padrão dominante): exceções de DOMÍNIO declaradas como nested
   static RuntimeException no Service e traduzidas no endpoint que as provoca:

```java
// Service — a exceção documenta a regra
public static class DepositRequiredException extends RuntimeException {}
...
if (newStatus == FECHADA && current.depositCents() > 0 && !current.depositPaid()) {
    throw new DepositRequiredException();
}

// Controller — tradução explícita, um catch por reason
} catch (DepositRequiredException e) {
    return error(409, "Conflict", "deposit_required");
} catch (InvalidStatusTransitionException e) {
    return error(409, "Conflict", "invalid_status_transition");
}
```

2. **`com.meada.common.GlobalExceptionHandler` (@RestControllerAdvice)**: rede de segurança para
   erros GENÉRICOS (validação @Valid, JSON malformado, erro inesperado). Não adicionar regra de
   domínio lá — domínio fica no catch local, perto do endpoint.

## Reasons — convenções de nome

- Prefixo pelo problema, não pela tabela: `invalid_*` (400), `*_not_found` (404),
  `duplicate_*`/`*_in_use`/`*_locked`/`conflict_*`/`*_required` (409), pré-condição de negócio
  (422: `address_required`, `age_not_confirmed`, `lead_time_violation`).
- Guard de perfil: sempre `forbidden_wrong_profile` (403).

## Best-effort nos fluxos da IA (handlers de tag)

Handler de tag NUNCA propaga exceção pro pipeline de outbound: falha → `Optional.empty()` + log
`warn` (a mensagem da IA segue sem o efeito). Notificações outbound são best-effort: falha de envio
NUNCA reverte a transação da regra de negócio (padrão dos Notifiers).

```java
// ✅ CERTO (handler de tag)
} catch (RuntimeException e) {
    log.warn("nicho: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
        conversationId, e.getMessage());
    return Optional.empty();
}
```
