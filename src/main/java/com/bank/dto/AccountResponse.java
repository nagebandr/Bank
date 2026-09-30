package com.bank.dto;

import com.bank.entity.Account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
    Long id,
    Long userId,
    BigDecimal balance,
    String currency,
    Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getUser().getId(),
            account.getBalance(),
            account.getCurrency(),
            account.getCreatedAt()
        );
    }
}
