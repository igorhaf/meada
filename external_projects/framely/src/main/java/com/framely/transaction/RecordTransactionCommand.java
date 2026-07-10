package com.framely.transaction;

import java.math.BigDecimal;

/**
 * Comando de negócio para registrar uma transação. É a entrada única do
 * {@link TransactionService#record}, compartilhada pela API REST e pelo bot de Telegram.
 *
 * <p>Resolução de conta: {@code accountId} tem prioridade; senão {@code accountName}
 * (por nome, case-insensitive); senão a conta padrão do usuário.
 */
public record RecordTransactionCommand(
        Long userId,
        TransactionType type,
        BigDecimal amount,
        String description,
        String categoryName,
        Long accountId,
        String accountName,
        TransactionOrigin origin) {
}
