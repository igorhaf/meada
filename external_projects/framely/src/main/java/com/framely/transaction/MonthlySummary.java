package com.framely.transaction;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlySummary(YearMonth month, BigDecimal receitas, BigDecimal despesas, BigDecimal saldo) {
}
