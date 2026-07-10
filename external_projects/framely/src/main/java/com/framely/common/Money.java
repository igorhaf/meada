package com.framely.common;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formatação monetária em Real (pt-BR): 1230.00 -> "R$ 1.230,00".
 */
public final class Money {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Money() {
    }

    public static String brl(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return NumberFormat.getCurrencyInstance(PT_BR).format(v);
    }
}
