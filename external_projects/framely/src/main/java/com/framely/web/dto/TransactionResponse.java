package com.framely.web.dto;

import com.framely.transaction.Transaction;
import com.framely.transaction.TransactionOrigin;
import com.framely.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        TransactionType type,
        BigDecimal amount,
        String description,
        String category,
        String account,
        BigDecimal accountBalance,
        TransactionOrigin origin,
        LocalDateTime occurredAt) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getType(),
                t.getAmount(),
                t.getDescription(),
                t.getCategory() == null ? null : t.getCategory().getName(),
                t.getAccount().getName(),
                t.getAccount().getBalance(),
                t.getOrigin(),
                t.getOccurredAt());
    }
}
