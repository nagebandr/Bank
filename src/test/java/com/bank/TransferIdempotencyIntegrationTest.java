package com.bank;

import com.bank.dto.AccountResponse;
import com.bank.dto.CreateAccountRequest;
import com.bank.dto.ErrorResponse;
import com.bank.dto.TransactionResponse;
import com.bank.dto.TransferRequest;
import com.bank.entity.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TransferIdempotencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private Long sourceId;
    private Long targetId;

    @BeforeEach
    void createAccounts() {
        sourceId = createAccount(1L, "100.00");
        targetId = createAccount(2L, "0.00");
    }

    @Test
    void concurrentRequestsWithSameKeyReturnSameTransferAndMoveMoneyOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        var start = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return transfer(key, "25.00", TransactionResponse.class);
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return transfer(key, "25.0000", TransactionResponse.class);
            });

            TransactionResponse original = requireCreated(first.get(15, TimeUnit.SECONDS));
            assertEquals(original, requireCreated(second.get(15, TimeUnit.SECONDS)));
        }

        assertSingleTransfer();
    }

    @Test
    void sameKeyWithDifferentAmountReturnsConflictEvenWhenAmountsRoundToSameValue() {
        String key = UUID.randomUUID().toString();
        requireCreated(transfer(key, "25.0000", TransactionResponse.class));

        ResponseEntity<ErrorResponse> conflict = transfer(key, "25.00001", ErrorResponse.class);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());

        assertSingleTransfer();
    }

    @Test
    void failedTransferRollsBackKeyReservation() {
        String key = UUID.randomUUID().toString();
        ResponseEntity<ErrorResponse> failed = transfer(key, "125.00", ErrorResponse.class);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, failed.getStatusCode());

        requireCreated(transfer(key, "25.00", TransactionResponse.class));

        assertSingleTransfer();
    }

    private Long createAccount(Long userId, String initialBalance) {
        var request = new CreateAccountRequest(userId, new BigDecimal(initialBalance), "RUB");
        var response = restTemplate.postForEntity("/accounts", request, AccountResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().id();
    }

    private <T> ResponseEntity<T> transfer(String key, String amount, Class<T> responseType) {
        var headers = new HttpHeaders();
        headers.set("Idempotency-Key", key);
        var request = new TransferRequest(sourceId, targetId, new BigDecimal(amount));
        return restTemplate.postForEntity("/transfers", new HttpEntity<>(request, headers), responseType);
    }

    private TransactionResponse requireCreated(ResponseEntity<TransactionResponse> response) {
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id());
        return response.getBody();
    }

    private void assertSingleTransfer() {
        var source = restTemplate.getForObject("/accounts/" + sourceId, AccountResponse.class);
        var target = restTemplate.getForObject("/accounts/" + targetId, AccountResponse.class);
        assertEquals(0, new BigDecimal("75.00").compareTo(source.balance()));
        assertEquals(0, new BigDecimal("25.00").compareTo(target.balance()));

        var history = restTemplate.getForObject("/accounts/" + targetId + "/transactions",
                TransactionResponse[].class);
        assertNotNull(history);
        assertEquals(1, history.length);
        assertEquals(TransactionType.TRANSFER, history[0].type());
    }
}
