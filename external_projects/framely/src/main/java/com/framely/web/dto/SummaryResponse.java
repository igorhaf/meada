package com.framely.web.dto;

import com.framely.transaction.MonthlySummary;

import java.math.BigDecimal;

public record SummaryResponse(String month, BigDecimal receitas, BigDecimal despesas, BigDecimal saldo) {

    public static SummaryResponse from(MonthlySummary s) {
        return new SummaryResponse(s.month().toString(), s.receitas(), s.despesas(), s.saldo());
    }
}
