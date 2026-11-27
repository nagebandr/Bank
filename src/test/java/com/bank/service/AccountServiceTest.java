package com.bank.service;

import com.bank.dto.AccountResponse;
import com.bank.dto.CreateAccountRequest;
import com.bank.dto.TransactionResponse;
import com.bank.entity.*;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.UserNotFoundException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import com.bank.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AccountService accountService;

    private User user;
    private Account account;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@test.com")
                .createdAt(Instant.now())
                .build();

        account = Account.builder()
                .id(10L)
                .user(user)
                .balance(new BigDecimal("500.0000"))
                .currency("RUB")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Create account successfully with initial balance")
    void createAccount_withInitialBalance_success() {
        CreateAccountRequest request = new CreateAccountRequest(1L, new BigDecimal("250.00"), "RUB");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account a = invocation.getArgument(0);
            setField(a, "id", 10L);
            return a;
        });

        AccountResponse response = accountService.createAccount(request);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals(0, new BigDecimal("250.0000").compareTo(response.balance()));
        assertEquals("RUB", response.currency());

        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Create account throws UserNotFoundException when user does not exist")
    void createAccount_userNotFound_throwsException() {
        CreateAccountRequest request = new CreateAccountRequest(999L, BigDecimal.ZERO, "RUB");
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> accountService.createAccount(request));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get account returns AccountResponse when found")
    void getAccount_existingId_success() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getAccount(10L);

        assertEquals(10L, response.id());
        assertEquals(0, new BigDecimal("500.0000").compareTo(response.balance()));
    }

    @Test
    @DisplayName("Get account throws AccountNotFoundException when account does not exist")
    void getAccount_notFound_throwsException() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountService.getAccount(999L));
    }

    @Test
    @DisplayName("Deposit increases balance and logs transaction")
    void deposit_validAmount_success() {
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.deposit(10L, new BigDecimal("100.00"));

        assertEquals(0, new BigDecimal("600.0000").compareTo(response.balance()));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Withdraw valid amount decreases balance and logs transaction")
    void withdraw_sufficientFunds_success() {
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.withdraw(10L, new BigDecimal("200.00"));

        assertEquals(0, new BigDecimal("300.0000").compareTo(response.balance()));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Withdraw throws InsufficientFundsException when balance is too low")
    void withdraw_insufficientFunds_throwsException() {
        when(accountRepository.findByIdWithLock(10L)).thenReturn(Optional.of(account));

        assertThrows(InsufficientFundsException.class, () -> accountService.withdraw(10L, new BigDecimal("1000.00")));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get transactions returns list of transactions for account")
    void getTransactions_success() {
        Transaction tx = Transaction.builder()
                .id(1L)
                .sourceAccount(account)
                .amount(new BigDecimal("50.0000"))
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .createdAt(Instant.now())
                .build();

        when(accountRepository.existsById(10L)).thenReturn(true);
        when(transactionRepository.findByAccountId(10L)).thenReturn(List.of(tx));

        List<TransactionResponse> result = accountService.getTransactions(10L);

        assertEquals(1, result.size());
        assertEquals(1L, result.getFirst().id());
        assertEquals(TransactionType.WITHDRAWAL, result.getFirst().type());
    }
}
