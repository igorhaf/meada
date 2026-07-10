package com.framely.web.dto;

import com.framely.transaction.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotNull Long userId,
        @NotNull TransactionType type,
        @NotNull @Positive BigDecimal amount,
        String description,
        String categoryName,
        Long accountId) {
}
