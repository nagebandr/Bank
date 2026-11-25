CREATE TABLE transfer_idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    source_account_id BIGINT NOT NULL,
    target_account_id BIGINT NOT NULL,
    request_amount NUMERIC NOT NULL,
    transaction_id BIGINT UNIQUE REFERENCES transactions(id)
);
