package com.bank.repository;

import com.bank.entity.TransferIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface TransferIdempotencyRepository extends JpaRepository<TransferIdempotencyKey, String> {

    @Modifying
    @Query(value = """
            INSERT INTO transfer_idempotency_keys
                (idempotency_key, source_account_id, target_account_id, request_amount)
            VALUES (:key, :source, :target, :amount)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int reserve(@Param("key") String key, @Param("source") Long source,
                @Param("target") Long target, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("""
            UPDATE TransferIdempotencyKey reservation
            SET reservation.transactionId = :transactionId
            WHERE reservation.idempotencyKey = :key
            """)
    void complete(@Param("key") String key, @Param("transactionId") Long transactionId);
}
