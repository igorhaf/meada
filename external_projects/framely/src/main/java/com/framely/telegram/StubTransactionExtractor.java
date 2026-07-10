package com.framely.telegram;

import org.springframework.stereotype.Component;

/**
 * PLACEHOLDER da camada de IA — implementação stub do {@link TransactionExtractor}.
 *
 * <pre>
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │  >>> AQUI ENTRA A CAMADA DE IA <<<                                    │
 *   │  Substitua este stub (ou registre outro bean @Primary/@Component     │
 *   │  implementando TransactionExtractor) por uma implementação que chame  │
 *   │  o modelo de IA para extrair tipo, valor, descrição, categoria e      │
 *   │  conta a partir da mensagem em linguagem natural.                     │
 *   └─────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * Enquanto isso, lança {@link UnsupportedOperationException}; o bot captura essa exceção
 * e responde ao usuário que a interpretação por IA ainda não está ativa. A tubulação
 * (bot → extractor → TransactionService) já está 100% ligada.
 */
@Component
public class StubTransactionExtractor implements TransactionExtractor {

    @Override
    public ExtractedTransaction extract(String message) {
        // TODO(ia): conectar o modelo de IA aqui e devolver um ExtractedTransaction real.
        throw new UnsupportedOperationException("Extração de transação por IA ainda não implementada");
    }
}
