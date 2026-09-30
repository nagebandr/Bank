package com.bank.entity;

import com.bank.exception.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .id(1L)
                .balance(new BigDecimal("100.00"))
                .currency("RUB")
                .build();
    }

    @Test
    @DisplayName("Deposit positive amount increases balance")
    void deposit_positiveAmount_success() {
        account.deposit(new BigDecimal("50.00"));
        assertEquals(0, new BigDecimal("150.00").compareTo(account.getBalance()));
    }

    @Test
    @DisplayName("Deposit zero or negative amount throws IllegalArgumentException")
    void deposit_nonPositiveAmount_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> account.deposit(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> account.deposit(new BigDecimal("-10.00")));
        assertThrows(IllegalArgumentException.class, () -> account.deposit(null));
    }

    @Test
    @DisplayName("Withdraw valid amount decreases balance")
    void withdraw_sufficientFunds_success() {
        account.withdraw(new BigDecimal("40.00"));
        assertEquals(0, new BigDecimal("60.00").compareTo(account.getBalance()));
    }

    @Test
    @DisplayName("Withdraw exact balance decreases balance to zero")
    void withdraw_exactBalance_success() {
        account.withdraw(new BigDecimal("100.00"));
        assertEquals(0, BigDecimal.ZERO.compareTo(account.getBalance()));
    }

    @Test
    @DisplayName("Withdraw more than balance throws InsufficientFundsException")
    void withdraw_insufficientFunds_throwsException() {
        assertThrows(InsufficientFundsException.class, () -> account.withdraw(new BigDecimal("100.01")));
    }

    @Test
    @DisplayName("Withdraw zero or negative amount throws IllegalArgumentException")
    void withdraw_nonPositiveAmount_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> account.withdraw(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> account.withdraw(new BigDecimal("-10.00")));
        assertThrows(IllegalArgumentException.class, () -> account.withdraw(null));
    }
}
