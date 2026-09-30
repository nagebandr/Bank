package com.bank.dto;

import com.bank.entity.Transaction;
import com.bank.entity.TransactionStatus;
import com.bank.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
    Long id,
    Long sourceAccountId,
    Long targetAccountId,
    BigDecimal amount,
    TransactionType type,
    TransactionStatus status,
    Instant createdAt,
    String failureReason
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
            tx.getId(),
            tx.getSourceAccount() != null ? tx.getSourceAccount().getId() : null,
            tx.getTargetAccount() != null ? tx.getTargetAccount().getId() : null,
            tx.getAmount(),
            tx.getType(),
            tx.getStatus(),
            tx.getCreatedAt(),
            tx.getFailureReason()
        );
    }
}
