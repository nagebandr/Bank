package com.bank.repository;

import com.bank.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class TransferIdempotencyRepository {

    private final JdbcClient jdbcClient;

    public boolean reserve(String key, TransferRequest request) {
        return jdbcClient.sql("""
                INSERT INTO transfer_idempotency_keys
                    (idempotency_key, source_account_id, target_account_id, request_amount)
                VALUES (:key, :source, :target, :amount)
                ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .param("key", key)
                .param("source", request.from())
                .param("target", request.to())
                .param("amount", request.amount())
                .update() == 1;
    }

    public SavedTransfer findByKey(String key) {
        return jdbcClient.sql("""
                SELECT source_account_id, target_account_id, request_amount, transaction_id
                FROM transfer_idempotency_keys
                WHERE idempotency_key = :key
                """)
                .param("key", key)
                .query((rs, rowNum) -> new SavedTransfer(
                        rs.getLong("source_account_id"), rs.getLong("target_account_id"),
                        rs.getBigDecimal("request_amount"), rs.getObject("transaction_id", Long.class)))
                .single();
    }

    public void complete(String key, Long transactionId) {
        jdbcClient.sql("""
                UPDATE transfer_idempotency_keys
                SET transaction_id = :transactionId
                WHERE idempotency_key = :key
                """)
                .param("key", key)
                .param("transactionId", transactionId)
                .update();
    }

    public record SavedTransfer(Long sourceAccountId, Long targetAccountId, BigDecimal amount, Long transactionId) {
        public boolean matches(TransferRequest request) {
            return sourceAccountId.equals(request.from())
                    && targetAccountId.equals(request.to())
                    && amount.compareTo(request.amount()) == 0;
        }
    }
}
