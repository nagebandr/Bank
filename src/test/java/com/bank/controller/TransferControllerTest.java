package com.bank.controller;

import com.bank.dto.TransactionResponse;
import com.bank.dto.TransferRequest;
import com.bank.entity.TransactionStatus;
import com.bank.entity.TransactionType;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.IdempotencyKeyConflictException;
import com.bank.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final String KEY = "transfer-123";
    private static final String VALID_BODY = """
            {"from": 10, "to": 20, "amount": 25.00}
            """;
    private static final TransferRequest REQUEST = new TransferRequest(10L, 20L, new BigDecimal("25.00"));
    private static final TransactionResponse RESPONSE = new TransactionResponse(
            42L, 10L, 20L, new BigDecimal("25.0000"), TransactionType.TRANSFER,
            TransactionStatus.SUCCESS, Instant.parse("2026-01-01T12:00:00Z"), null);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferService transferService;

    @Test
    void validRequestPassesHeaderToServiceAndReturnsCreatedTransfer() throws Exception {
        when(transferService.transfer(REQUEST, KEY)).thenReturn(RESPONSE);

        mockMvc.perform(transferPost(VALID_BODY).header("Idempotency-Key", KEY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.sourceAccountId").value(10))
                .andExpect(jsonPath("$.targetAccountId").value(20))
                .andExpect(jsonPath("$.amount").value(25))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T12:00:00Z"));

        verify(transferService).transfer(REQUEST, KEY);
    }

    @Test
    void requestWithoutKeyStillReturnsCreated() throws Exception {
        when(transferService.transfer(REQUEST, null)).thenReturn(RESPONSE);

        mockMvc.perform(transferPost(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42));

        verify(transferService).transfer(REQUEST, null);
    }

    @Test
    void missingRequiredFieldsReturnBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(transferPost("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/transfers"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details", containsInAnyOrder(
                        "from: Source account ID ('from') is required",
                        "to: Target account ID ('to') is required",
                        "amount: Transfer amount is required")));

        verifyNoInteractions(transferService);
    }

    @Test
    void amountBelowMinimumReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(transferPost("""
                {"from": 10, "to": 20, "amount": 0.001}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("amount: Transfer amount must be at least 0.01"));

        verifyNoInteractions(transferService);
    }

    @Test
    void malformedJsonReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(transferPost("{\"from\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/transfers"));

        verifyNoInteractions(transferService);
    }

    @Test
    void reusedKeyWithDifferentRequestReturnsConflict() throws Exception {
        when(transferService.transfer(REQUEST, KEY)).thenThrow(new IdempotencyKeyConflictException());

        mockMvc.perform(transferPost(VALID_BODY).header("Idempotency-Key", KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/transfers"));
    }

    @Test
    void missingAccountReturnsNotFound() throws Exception {
        when(transferService.transfer(REQUEST, null)).thenThrow(new AccountNotFoundException(20L));

        mockMvc.perform(transferPost(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Account not found with id: 20"));
    }

    private MockHttpServletRequestBuilder transferPost(String body) {
        return post("/transfers").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
