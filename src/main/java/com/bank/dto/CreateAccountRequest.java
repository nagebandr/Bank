package com.bank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateAccountRequest(
    @NotNull(message = "User ID is required")
    Long userId,

    @DecimalMin(value = "0.00", inclusive = true, message = "Initial balance cannot be negative")
    BigDecimal initialBalance,

    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter code, e.g. RUB, USD")
    String currency
) {}
