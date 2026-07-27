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
    retry_count         INT          NOT NULL DEFAULT 0,
    version             INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id  VARCHAR(36)  NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    changed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason      TEXT,
    error_code  VARCHAR(50),
    CONSTRAINT fk_history_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    key_record  VARCHAR(64) PRIMARY KEY,
    payment_id  VARCHAR(36) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS accounts (
    account_number  VARCHAR(50)   PRIMARY KEY,
    account_name    VARCHAR(100)  NOT NULL,
    balance         DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed accounts
MERGE INTO accounts (account_number, account_name, balance, currency) KEY(account_number) VALUES
    ('ACC-00001', 'HSBC Operations',   10000000.00, 'USD'),
    ('ACC-00002', 'HSBC Trading Desk',  5000000.00, 'USD'),
    ('ACC-00003', 'HSBC Custody',       3000000.00, 'USD'),
    ('ACC-00004', 'HSBC Markets',       2000000.00, 'EUR'),
    ('ACC-00005', 'HSBC Wealth',        1000000.00, 'GBP'),
    ('ACC-00006', 'HSBC Digital',        500000.00, 'CNY'),
    ('ACC-00007', 'HSBC Retail',         200000.00, 'USD'),
    ('ACC-00008', 'HSBC Commercial',     100000.00, 'USD'),
    ('ACC-00009', 'HSBC Investment',       5000.00, 'USD'),
    ('ACC-00010', 'HSBC Low Balance',         0.00, 'USD');

CREATE TABLE IF NOT EXISTS risk_assessments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id        VARCHAR(36)  NOT NULL,
    risk_score        INT          NOT NULL,
    risk_level        VARCHAR(10)  NOT NULL,
    risk_decision     VARCHAR(10)  NOT NULL,
    triggered_rules   CLOB,
    statistical_flags CLOB,
    reasoning         CLOB,
    llm_model_used    VARCHAR(50),
    assessed_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS account_stats (
    account_number   VARCHAR(50)   PRIMARY KEY,
    avg_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    std_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    median_amount    DECIMAL(15,2) NOT NULL DEFAULT 0,
    q1_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,
    q3_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_count      INT           NOT NULL DEFAULT 0,
    known_payees     CLOB,
    last_updated     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

MERGE INTO account_stats (account_number, avg_amount, std_amount, median_amount,
    q1_amount, q3_amount, total_count, known_payees) KEY(account_number) VALUES
    ('ACC-00001', 50000.00, 30000.00, 45000.00, 20000.00, 75000.00, 120,
     '["ACC-00002","ACC-00003","ACC-00004"]'),
    ('ACC-00002', 20000.00, 15000.00, 18000.00, 8000.00, 30000.00, 85,
     '["ACC-00001","ACC-00005"]'),
    ('ACC-00003', 10000.00, 8000.00, 9000.00, 5000.00, 14000.00, 60,
     '["ACC-00001","ACC-00002"]'),
    ('ACC-00007', 500.00, 300.00, 450.00, 200.00, 700.00, 200,
     '["ACC-00001","ACC-00008"]'),
    ('ACC-00009', 50.00, 30.00, 45.00, 20.00, 70.00, 15,
     '["ACC-00001"]');
