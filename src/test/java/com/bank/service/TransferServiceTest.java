package com.bank.service;

import com.bank.dto.TransactionResponse;
import com.bank.dto.TransferRequest;
import com.bank.entity.Account;
import com.bank.entity.Transaction;
import com.bank.entity.TransactionStatus;
import com.bank.entity.TransactionType;
import com.bank.entity.User;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidTransferException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransferService transferService;

    private Account accountA;
    private Account accountB;

    @BeforeEach
    void setUp() {
        User user1 = User.builder().id(1L).name("User 1").email("u1@test.com").build();
        User user2 = User.builder().id(2L).name("User 2").email("u2@test.com").build();

        accountA = Account.builder()
                .id(10L)
                .user(user1)
                .balance(new BigDecimal("1000.0000"))
                .currency("RUB")
                .createdAt(Instant.now())
                .build();

        accountB = Account.builder()
                .id(20L)
                .user(user2)
                .balance(new BigDecimal("200.0000"))
                .currency("RUB")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Transfer 500 from 10 to 20 succeeds atomically")
    void transfer_success() {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("500.00"));

        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(accountA));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(accountB));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            setField(tx, "id", 100L);
            return tx;
        });

        TransactionResponse response = transferService.transfer(request);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals(10L, response.sourceAccountId());
        assertEquals(20L, response.targetAccountId());
        assertEquals(0, new BigDecimal("500.0000").compareTo(response.amount()));
        assertEquals(TransactionType.TRANSFER, response.type());
        assertEquals(TransactionStatus.SUCCESS, response.status());

        // Check balances
        assertEquals(0, new BigDecimal("500.0000").compareTo(accountA.getBalance()));
        assertEquals(0, new BigDecimal("700.0000").compareTo(accountB.getBalance()));

        verify(accountRepository).save(accountA);
        verify(accountRepository).save(accountB);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Transfer from account with higher ID to lower ID locks lower ID first")
    void transfer_reverseOrderLocking_success() {
        TransferRequest request = new TransferRequest(20L, 10L, new BigDecimal("100.00"));

        // Both accounts exist
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(accountA));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(accountB));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        TransactionResponse response = transferService.transfer(request);

        assertNotNull(response);
        assertEquals(20L, response.sourceAccountId());
        assertEquals(10L, response.targetAccountId());
        assertEquals(0, new BigDecimal("100.0000").compareTo(accountB.getBalance()));
        assertEquals(0, new BigDecimal("1100.0000").compareTo(accountA.getBalance()));

        // Verification order: 10L is locked before 20L
        var order = inOrder(accountRepository);
        order.verify(accountRepository).findByIdWithLock(10L);
        order.verify(accountRepository).findByIdWithLock(20L);
    }

    @Test
    @DisplayName("Transfer to the same account throws InvalidTransferException")
    void transfer_sameAccount_throwsException() {
        TransferRequest request = new TransferRequest(10L, 10L, new BigDecimal("100.00"));

        assertThrows(InvalidTransferException.class, () -> transferService.transfer(request));
        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Transfer non-positive amount throws InvalidTransferException")
    void transfer_nonPositiveAmount_throwsException() {
        TransferRequest requestZero = new TransferRequest(10L, 20L, BigDecimal.ZERO);
        assertThrows(InvalidTransferException.class, () -> transferService.transfer(requestZero));

        TransferRequest requestNegative = new TransferRequest(10L, 20L, new BigDecimal("-50.00"));
        assertThrows(InvalidTransferException.class, () -> transferService.transfer(requestNegative));

        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Transfer with insufficient funds throws InsufficientFundsException")
    void transfer_insufficientFunds_throwsException() {
        TransferRequest request = new TransferRequest(10L, 20L, new BigDecimal("1000.01"));

        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(accountA));
        when(accountRepository.findByIdWithLock(20L)).thenReturn(Optional.of(accountB));

        assertThrows(InsufficientFundsException.class, () -> transferService.transfer(request));

        // Balances must remain unchanged
        assertEquals(0, new BigDecimal("1000.0000").compareTo(accountA.getBalance()));
        assertEquals(0, new BigDecimal("200.0000").compareTo(accountB.getBalance()));

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Transfer throws AccountNotFoundException when account does not exist")
    void transfer_accountNotFound_throwsException() {
        TransferRequest request = new TransferRequest(10L, 999L, new BigDecimal("50.00"));

        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(accountA));
        when(accountRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> transferService.transfer(request));
        verify(transactionRepository, never()).save(any());
    }
}
