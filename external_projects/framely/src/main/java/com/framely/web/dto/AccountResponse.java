package com.framely.web.dto;

import com.framely.account.Account;
import com.framely.account.AccountType;

import java.math.BigDecimal;

public record AccountResponse(Long id, String name, AccountType type, BigDecimal balance) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getName(), account.getType(), account.getBalance());
    }
}
