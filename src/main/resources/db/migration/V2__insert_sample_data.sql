INSERT INTO users (id, name, email) VALUES (1, 'Alice Smith', 'alice@example.com');
INSERT INTO users (id, name, email) VALUES (2, 'Bob Johnson', 'bob@example.com');

INSERT INTO accounts (id, user_id, balance, currency, version) VALUES (10, 1, 1000.0000, 'RUB', 0);
INSERT INTO accounts (id, user_id, balance, currency, version) VALUES (20, 2, 200.0000, 'RUB', 0);

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('accounts_id_seq', (SELECT MAX(id) FROM accounts));
