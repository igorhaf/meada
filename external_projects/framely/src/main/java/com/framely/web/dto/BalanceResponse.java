package com.framely.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record BalanceResponse(BigDecimal total, List<AccountResponse> accounts) {
}
