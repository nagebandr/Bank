package com.bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "transfer_idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferIdempotencyKey {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private Long sourceAccountId;

    @Column(name = "target_account_id", nullable = false, updatable = false)
    private Long targetAccountId;

    @Column(name = "request_amount", nullable = false, updatable = false)
    private BigDecimal requestAmount;

    @Column(name = "transaction_id", unique = true)
    private Long transactionId;

    public boolean matches(Long source, Long target, BigDecimal amount) {
        return sourceAccountId.equals(source)
                && targetAccountId.equals(target)
                && requestAmount.compareTo(amount) == 0;
    }
}
