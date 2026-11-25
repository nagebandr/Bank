package com.bank.service;

import com.bank.dto.TransactionResponse;
import com.bank.dto.TransferRequest;
import com.bank.entity.Account;
import com.bank.entity.Transaction;
import com.bank.entity.TransactionStatus;
import com.bank.entity.TransactionType;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.IdempotencyKeyConflictException;
import com.bank.exception.InvalidTransferException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import com.bank.repository.TransferIdempotencyRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransferIdempotencyRepository idempotencyRepository;
    private final EntityManager entityManager;

    /**
     * Executes an atomic transfer between two accounts.
     * Deadlock-free guarantee is achieved by consistently locking accounts in ascending order of their IDs.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse transfer(TransferRequest request) {
        return TransactionResponse.from(executeTransfer(request));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey) {
        if (idempotencyKey == null) {
            return TransactionResponse.from(executeTransfer(request));
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new IllegalArgumentException("Idempotency-Key must be non-blank and at most 255 characters");
        }

        // A conflicting insert waits for the transaction that first reserved the key.
        if (!idempotencyRepository.reserve(idempotencyKey, request)) {
            // At READ COMMITTED, this separate statement sees the committed result.
            var saved = idempotencyRepository.findByKey(idempotencyKey);
            if (!saved.matches(request)) {
                throw new IdempotencyKeyConflictException();
            }
            return transactionRepository.findById(saved.transactionId())
                    .map(TransactionResponse::from)
                    .orElseThrow();
        }

        Transaction transaction = executeTransfer(request);
        idempotencyRepository.complete(idempotencyKey, transaction.getId());
        // Build the first response from persisted values, including PostgreSQL's timestamp precision.
        entityManager.refresh(transaction);
        return TransactionResponse.from(transaction);
    }

    private Transaction executeTransfer(TransferRequest request) {
        log.info("Starting atomic transfer of {} from account {} to account {}",
                request.amount(), request.from(), request.to());

        if (request.from().equals(request.to())) {
            throw new InvalidTransferException("Source and target accounts must be different");
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be strictly greater than zero");
        }

        BigDecimal normalizedAmount = request.amount().setScale(4, RoundingMode.HALF_EVEN);

        // Lock accounts in consistent ascending order of ID to completely prevent deadlocks
        Long firstId = Math.min(request.from(), request.to());
        Long secondId = Math.max(request.from(), request.to());

        Account firstAccount = accountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));

        Account secondAccount = accountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));

        Account sourceAccount = request.from().equals(firstId) ? firstAccount : secondAccount;
        Account targetAccount = request.to().equals(firstId) ? firstAccount : secondAccount;

        if (!sourceAccount.getCurrency().equalsIgnoreCase(targetAccount.getCurrency())) {
            throw new InvalidTransferException(String.format(
                    "Currency mismatch: source account currency is %s, target account currency is %s",
                    sourceAccount.getCurrency(), targetAccount.getCurrency()
            ));
        }

        // Atomic balance update
        sourceAccount.withdraw(normalizedAmount);
        targetAccount.deposit(normalizedAmount);

        accountRepository.save(sourceAccount);
        accountRepository.save(targetAccount);

        // Record transaction
        Transaction transaction = Transaction.builder()
                .sourceAccount(sourceAccount)
                .targetAccount(targetAccount)
                .amount(normalizedAmount)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .build();

        Transaction savedTx = transactionRepository.save(transaction);
        log.info("Transfer completed successfully. Transaction ID: {}", savedTx.getId());

        return savedTx;
    }
}
