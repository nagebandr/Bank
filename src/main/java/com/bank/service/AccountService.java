package com.bank.service;

import com.bank.dto.AccountResponse;
import com.bank.dto.CreateAccountRequest;
import com.bank.dto.TransactionResponse;
import com.bank.entity.*;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.UserNotFoundException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import com.bank.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for user id: {}", request.userId());
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFoundException(request.userId()));

        BigDecimal initialBalance = request.initialBalance() != null
                ? request.initialBalance().setScale(4, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency().toUpperCase()
                : "RUB";

        Account account = Account.builder()
                .user(user)
                .balance(initialBalance)
                .currency(currency)
                .build();

        Account saved = accountRepository.save(account);

        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            Transaction tx = Transaction.builder()
                    .targetAccount(saved)
                    .amount(initialBalance)
                    .type(TransactionType.DEPOSIT)
                    .status(TransactionStatus.SUCCESS)
                    .build();
            transactionRepository.save(tx);
        }

        return AccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse deposit(Long accountId, BigDecimal amount) {
        log.info("Depositing {} to account {}", amount, accountId);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be strictly greater than zero");
        }
        BigDecimal normalizedAmount = amount.setScale(4, RoundingMode.HALF_EVEN);

        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.deposit(normalizedAmount);
        Account updated = accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .targetAccount(account)
                .amount(normalizedAmount)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .build();
        transactionRepository.save(tx);

        return AccountResponse.from(updated);
    }

    @Transactional
    public AccountResponse withdraw(Long accountId, BigDecimal amount) {
        log.info("Withdrawing {} from account {}", amount, accountId);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be strictly greater than zero");
        }
        BigDecimal normalizedAmount = amount.setScale(4, RoundingMode.HALF_EVEN);

        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.withdraw(normalizedAmount);
        Account updated = accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .sourceAccount(account)
                .amount(normalizedAmount)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .build();
        transactionRepository.save(tx);

        return AccountResponse.from(updated);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return transactionRepository.findByAccountId(accountId).stream()
                .map(TransactionResponse::from)
                .toList();
    }
}
