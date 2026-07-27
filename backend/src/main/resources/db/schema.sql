CREATE TABLE IF NOT EXISTS payments (
    id                  VARCHAR(36)  PRIMARY KEY,
    idempotency_key     VARCHAR(64)  NOT NULL,
    source_account      VARCHAR(50)  NOT NULL,
    destination_account VARCHAR(50)  NOT NULL,
    amount              DECIMAL(15,2) NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    description         TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    error_code          VARCHAR(50),
    version             INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id  VARCHAR(36)  NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    changed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason      TEXT,
    error_code  VARCHAR(50),
    CONSTRAINT fk_history_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS idempotency_keys (
    key_record  VARCHAR(64) PRIMARY KEY,
    payment_id  VARCHAR(36) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS accounts (
    account_number  VARCHAR(50)   PRIMARY KEY,
    account_name    VARCHAR(100)  NOT NULL,
    balance         DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed accounts for testing
INSERT INTO accounts (account_number, account_name, balance, currency) VALUES
    ('ACC-00001', 'HSBC Operations',   10000000.00, 'USD'),
    ('ACC-00002', 'HSBC Trading Desk',  5000000.00, 'USD'),
    ('ACC-00003', 'HSBC Custody',       3000000.00, 'USD'),
    ('ACC-00004', 'HSBC Markets',       2000000.00, 'EUR'),
    ('ACC-00005', 'HSBC Wealth',        1000000.00, 'GBP'),
    ('ACC-00006', 'HSBC Digital',        500000.00, 'CNY'),
    ('ACC-00007', 'HSBC Retail',         200000.00, 'USD'),
    ('ACC-00008', 'HSBC Commercial',     100000.00, 'USD'),
    ('ACC-00009', 'HSBC Investment',       5000.00, 'USD'),
    ('ACC-00010', 'HSBC Low Balance',         0.00, 'USD')
ON DUPLICATE KEY UPDATE account_name = VALUES(account_name);

-- Performance indexes
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_currency ON payments (currency);
CREATE INDEX idx_payments_created_at ON payments (created_at);
CREATE INDEX idx_status_history_payment_id ON status_history (payment_id);
