package com.bank;

import com.bank.dto.*;
import com.bank.entity.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("GET /accounts/{id} returns account details")
    void getAccount_success() {
        ResponseEntity<AccountResponse> response = restTemplate.getForEntity("/accounts/10", AccountResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(10L, response.getBody().id());
        assertEquals("RUB", response.getBody().currency());
    }

    @Test
    @DisplayName("GET /accounts/{id} returns 404 when account does not exist")
    void getAccount_notFound() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/accounts/999999", ErrorResponse.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("Account not found"));
    }

    @Test
    @DisplayName("POST /accounts creates new account")
    void createAccount_success() {
        CreateAccountRequest request = new CreateAccountRequest(1L, new BigDecimal("350.00"), "RUB");
        ResponseEntity<AccountResponse> response = restTemplate.postForEntity("/accounts", request, AccountResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id());
        assertEquals(0, new BigDecimal("350.0000").compareTo(response.getBody().balance()));
    }

    @Test
    @DisplayName("POST /accounts/{id}/deposit increases balance")
    void deposit_success() {
        // Create account
        CreateAccountRequest createReq = new CreateAccountRequest(1L, new BigDecimal("100.00"), "RUB");
        ResponseEntity<AccountResponse> created = restTemplate.postForEntity("/accounts", createReq, AccountResponse.class);
        assertNotNull(created.getBody());
        Long accountId = created.getBody().id();

        // Deposit
        DepositRequest depositReq = new DepositRequest(new BigDecimal("75.50"));
        ResponseEntity<AccountResponse> response = restTemplate.postForEntity(
                "/accounts/" + accountId + "/deposit", depositReq, AccountResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, new BigDecimal("175.5000").compareTo(response.getBody().balance()));
    }

    @Test
    @DisplayName("POST /accounts/{id}/withdraw decreases balance")
    void withdraw_success() {
        // Create account
        CreateAccountRequest createReq = new CreateAccountRequest(1L, new BigDecimal("200.00"), "RUB");
        ResponseEntity<AccountResponse> created = restTemplate.postForEntity("/accounts", createReq, AccountResponse.class);
        assertNotNull(created.getBody());
        Long accountId = created.getBody().id();

        // Withdraw
        WithdrawRequest withdrawReq = new WithdrawRequest(new BigDecimal("50.00"));
        ResponseEntity<AccountResponse> response = restTemplate.postForEntity(
                "/accounts/" + accountId + "/withdraw", withdrawReq, AccountResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, new BigDecimal("150.0000").compareTo(response.getBody().balance()));
    }

    @Test
    @DisplayName("POST /accounts/{id}/withdraw returns 422 on insufficient funds")
    void withdraw_insufficientFunds() {
        // Create account with 10.00
        CreateAccountRequest createReq = new CreateAccountRequest(1L, new BigDecimal("10.00"), "RUB");
        ResponseEntity<AccountResponse> created = restTemplate.postForEntity("/accounts", createReq, AccountResponse.class);
        assertNotNull(created.getBody());
        Long accountId = created.getBody().id();

        // Attempt withdrawal of 100.00
        WithdrawRequest withdrawReq = new WithdrawRequest(new BigDecimal("100.00"));
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/accounts/" + accountId + "/withdraw", withdrawReq, ErrorResponse.class);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("Insufficient funds"));
    }

    @Test
    @DisplayName("POST /transfers transfers money between accounts atomically")
    void transfer_success() {
        // Create source account with 1000
        AccountResponse src = restTemplate.postForEntity("/accounts",
                new CreateAccountRequest(1L, new BigDecimal("1000.00"), "RUB"), AccountResponse.class).getBody();

        // Create target account with 200
        AccountResponse dst = restTemplate.postForEntity("/accounts",
                new CreateAccountRequest(2L, new BigDecimal("200.00"), "RUB"), AccountResponse.class).getBody();

        assertNotNull(src);
        assertNotNull(dst);
        TransferRequest transferReq = new TransferRequest(src.id(), dst.id(), new BigDecimal("500.00"));
        ResponseEntity<TransactionResponse> response = restTemplate.postForEntity("/transfers", transferReq, TransactionResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id());
        assertEquals(src.id(), response.getBody().sourceAccountId());
        assertEquals(dst.id(), response.getBody().targetAccountId());
        assertEquals(0, new BigDecimal("500.0000").compareTo(response.getBody().amount()));

        // Check source balance is now 500
        AccountResponse updatedSrc = restTemplate.getForEntity("/accounts/" + src.id(), AccountResponse.class).getBody();
        assertNotNull(updatedSrc);
        assertEquals(0, new BigDecimal("500.0000").compareTo(updatedSrc.balance()));

        // Check target balance is now 700
        AccountResponse updatedDst = restTemplate.getForEntity("/accounts/" + dst.id(), AccountResponse.class).getBody();
        assertNotNull(updatedDst);
        assertEquals(0, new BigDecimal("700.0000").compareTo(updatedDst.balance()));

        // Check transactions history
        ResponseEntity<List<TransactionResponse>> txHistory = restTemplate.exchange(
                "/accounts/" + src.id() + "/transactions",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, txHistory.getStatusCode());
        assertNotNull(txHistory.getBody());
        assertTrue(txHistory.getBody().stream().anyMatch(transaction ->
                response.getBody().id().equals(transaction.id()) && transaction.type() == TransactionType.TRANSFER));
    }
}
