package com.meada.cms;

import java.util.List;

/**
 * Resolve registros TXT de um host (SM-N, verificação de posse de domínio). Interface pra permitir
 * mock nos testes — o impl de produção ({@link JndiDnsTxtResolver}) consulta o DNS de verdade via
 * JNDI (sem dependência externa).
 */
public interface DnsTxtResolver {
    /** Os valores TXT do host (lista vazia se não há, ou em erro de resolução). */
    List<String> txtRecords(String host);
}
