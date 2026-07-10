package com.framely.telegram;

import com.framely.transaction.TransactionType;

import java.math.BigDecimal;

/**
 * Resultado estruturado da interpretação de uma mensagem em linguagem natural.
 * Produzido pela camada de IA ({@link TransactionExtractor}) e consumido pelo bot,
 * que o traduz em um comando de negócio para o TransactionService.
 *
 * <p>{@code categoryName} e {@code accountName} são opcionais (podem ser nulos):
 * quando ausentes, o serviço aplica categoria "Outros" e a conta padrão do usuário.
 */
public record ExtractedTransaction(
        TransactionType type,
        BigDecimal amount,
        String description,
        String categoryName,
        String accountName) {
}
