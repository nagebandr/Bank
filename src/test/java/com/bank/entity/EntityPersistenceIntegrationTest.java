package com.bank.entity;

import com.bank.BaseIntegrationTest;
import com.bank.repository.TransferIdempotencyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class EntityPersistenceIntegrationTest extends BaseIntegrationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("12.3456");

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransferIdempotencyRepository idempotencyRepository;

    @Test
    void userGetsGeneratedIdAndCreationTimeAndCanBeReloaded() {
        String email = "entity-" + UUID.randomUUID() + "@example.com";
        User user = User.builder().name("Test User").email(email).build();
        assertNull(user.getId());
        assertNull(user.getCreatedAt());

        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        assertNotNull(user.getId());
        assertNotNull(user.getCreatedAt());
        User saved = entityManager.find(User.class, user.getId());
        assertNotNull(saved);
        assertEquals("Test User", saved.getName());
        assertEquals(email, saved.getEmail());
        assertNotNull(saved.getCreatedAt());
    }

    @ParameterizedTest
    @CsvSource({"DEPOSIT,,20", "WITHDRAWAL,10,", "TRANSFER,10,20"})
    void transactionKeepsItsTypeAndOptionalAccountReferences(
            TransactionType type, Long sourceAccountId, Long targetAccountId) {
        Transaction operation = transaction(type, sourceAccountId, targetAccountId);
        assertNull(operation.getCreatedAt());

        entityManager.persist(operation);
        entityManager.flush();
        entityManager.clear();

        assertNotNull(operation.getId());
        assertNotNull(operation.getCreatedAt());
        Transaction saved = entityManager.find(Transaction.class, operation.getId());
        assertNotNull(saved);
        assertEquals(type, saved.getType());
        assertEquals(TransactionStatus.SUCCESS, saved.getStatus());
        assertEquals(AMOUNT, saved.getAmount());
        assertEquals(sourceAccountId, accountId(saved.getSourceAccount()));
        assertEquals(targetAccountId, accountId(saved.getTargetAccount()));
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void idempotencyKeyKeepsOriginalAmountAndLinksToCompletedTransfer() {
        String key = UUID.randomUUID().toString();
        BigDecimal requestedAmount = new BigDecimal("12.34561");
        assertEquals(1, idempotencyRepository.reserve(key, 10L, 20L, requestedAmount));
        assertNull(idempotencyRepository.findById(key).orElseThrow().getTransactionId());

        Transaction transfer = transaction(TransactionType.TRANSFER, 10L, 20L);
        entityManager.persist(transfer);
        idempotencyRepository.complete(key, transfer.getId());
        entityManager.flush();
        entityManager.clear();

        TransferIdempotencyKey saved = idempotencyRepository.findById(key).orElseThrow();
        assertEquals(transfer.getId(), saved.getTransactionId());
        assertTrue(saved.matches(10L, 20L, new BigDecimal("12.345610")));
        assertFalse(saved.matches(20L, 20L, requestedAmount));
        assertFalse(saved.matches(10L, 10L, requestedAmount));
        assertFalse(saved.matches(10L, 20L, AMOUNT));
    }

    private Transaction transaction(TransactionType type, Long sourceAccountId, Long targetAccountId) {
        return Transaction.builder()
                .type(type)
                .status(TransactionStatus.SUCCESS)
                .amount(AMOUNT)
                .sourceAccount(sourceAccountId == null ? null : entityManager.getReference(Account.class, sourceAccountId))
                .targetAccount(targetAccountId == null ? null : entityManager.getReference(Account.class, targetAccountId))
                .build();
    }

    private Long accountId(Account account) {
        return account == null ? null : account.getId();
    }
}
