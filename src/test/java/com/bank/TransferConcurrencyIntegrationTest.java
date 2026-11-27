package com.bank;

import com.bank.dto.AccountResponse;
import com.bank.dto.CreateAccountRequest;
import com.bank.dto.TransferRequest;
import com.bank.service.TransferService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransferService transferService;

    @Test
    @DisplayName("Concurrent bidirectional transfers between two accounts execute without deadlock and maintain balance invariant")
    void concurrentTransfers_noDeadlocks_balanceConsistent() throws InterruptedException {
        // Setup two accounts with 5,000 each. Total initial money = 10,000
        BigDecimal initialBalance = new BigDecimal("5000.0000");
        AccountResponse accA = restTemplate.postForEntity("/accounts",
                new CreateAccountRequest(1L, initialBalance, "RUB"), AccountResponse.class).getBody();
        AccountResponse accB = restTemplate.postForEntity("/accounts",
                new CreateAccountRequest(2L, initialBalance, "RUB"), AccountResponse.class).getBody();

        Assertions.assertNotNull(accA);
        Long idA = accA.id();
        Assertions.assertNotNull(accB);
        Long idB = accB.id();

        int totalOperations = 20;
        int half = totalOperations / 2;
        BigDecimal transferAmount = new BigDecimal("50.0000");

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalOperations);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Half transfers: A -> B
        for (int i = 0; i < half; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    transferService.transfer(new TransferRequest(idA, idB, transferAmount));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Other half transfers: B -> A (concurrent opposite direction)
        for (int i = 0; i < half; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    transferService.transfer(new TransferRequest(idB, idA, transferAmount));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean completed = finishLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent transfers should complete within timeout");
        assertEquals(totalOperations, successCount.get(), "All transfers must succeed without deadlocks");
        assertEquals(0, failureCount.get(), "There should be zero failed transfers");

        // Verify balance invariant: Total money must still be exactly 10,000.0000
        AccountResponse finalA = restTemplate.getForEntity("/accounts/" + idA, AccountResponse.class).getBody();
        AccountResponse finalB = restTemplate.getForEntity("/accounts/" + idB, AccountResponse.class).getBody();

        BigDecimal expectedTotal = initialBalance.multiply(BigDecimal.valueOf(2));
        Assertions.assertNotNull(finalA);
        Assertions.assertNotNull(finalB);
        BigDecimal actualTotal = finalA.balance().add(finalB.balance());

        assertEquals(0, expectedTotal.compareTo(actualTotal),
                String.format("Total balance must be conserved! Expected: %s, Actual: %s", expectedTotal, actualTotal));

        // Since 10 transfers A->B and 10 transfers B->A of identical amount occurred, balances should be exactly equal to initial
        assertEquals(0, initialBalance.compareTo(finalA.balance()));
        assertEquals(0, initialBalance.compareTo(finalB.balance()));
    }
}
