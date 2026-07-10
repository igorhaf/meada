package com.framely.telegram;

/**
 * Ponto de integração da camada de IA.
 *
 * <p>Recebe a mensagem em linguagem natural enviada pelo usuário ao bot e devolve os
 * dados estruturados da transação ({@link ExtractedTransaction}). A implementação de IA
 * (Gemini/Claude/etc.) será plugada aqui — basta registrar um bean que implemente esta
 * interface. Nenhuma outra camada precisa mudar.
 *
 * <p>A implementação padrão ({@link StubTransactionExtractor}) é um placeholder que ainda
 * não interpreta nada.
 */
public interface TransactionExtractor {

    /**
     * @param message texto livre do usuário (ex.: "gastei 50 no mercado com o cartão")
     * @return os dados estruturados da transação
     */
    ExtractedTransaction extract(String message);
}
