-- WALLET: one row per user, running balance. VERSION backs optimistic locking;
-- the actual balance mutation additionally takes a pessimistic row lock (see
-- WalletRepository.findByUserIdForUpdate) so concurrent debits can never overdraw.
CREATE TABLE SO_WALLET
(
    ID         UUID           PRIMARY KEY,
    USER_ID    UUID           NOT NULL,
    BALANCE    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    VERSION    BIGINT         NOT NULL DEFAULT 0,
    CREATED_AT TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT TIMESTAMP,
    CONSTRAINT SO_FK_WALLET_USER_ID FOREIGN KEY (USER_ID) REFERENCES SO_USERS (ID)
);

-- WALLET_TRANSACTION: append-only ledger. Every balance change (top-up credit,
-- shipment debit, refund credit) gets a row here; the wallet's BALANCE column is
-- a derived cache of this ledger, never mutated without a matching row.
CREATE TABLE SO_WALLET_TRANSACTION
(
    ID             UUID           PRIMARY KEY,
    WALLET_ID      UUID           NOT NULL,
    TYPE           VARCHAR(20)    NOT NULL, -- TOPUP, DEBIT, CREDIT
    STATUS         VARCHAR(20)    NOT NULL, -- PENDING, SUCCESS, FAILED
    AMOUNT         NUMERIC(12, 2) NOT NULL,
    BALANCE_AFTER  NUMERIC(12, 2),          -- null while PENDING
    GATEWAY        VARCHAR(20),             -- PAYU; null for internal DEBIT/CREDIT
    GATEWAY_TXN_ID VARCHAR(100),            -- PayU's mihpayid; null until the callback lands
    REFERENCE_ID   VARCHAR(100)   NOT NULL, -- our own idempotency key
    REMARKS        VARCHAR(256),
    CREATED_AT     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT     TIMESTAMP,
    CONSTRAINT SO_FK_WALLET_TXN_WALLET_ID FOREIGN KEY (WALLET_ID) REFERENCES SO_WALLET (ID),
    CONSTRAINT SO_UQ_WALLET_TXN_REFERENCE_ID UNIQUE (REFERENCE_ID),
    CONSTRAINT SO_UQ_WALLET_TXN_GATEWAY_TXN_ID UNIQUE (GATEWAY_TXN_ID)
);

CREATE INDEX SO_IDX_WALLET_TXN_WALLET_ID ON SO_WALLET_TRANSACTION (WALLET_ID);
