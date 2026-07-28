CREATE TABLE IF NOT EXISTS payments (
    id                  VARCHAR(36)  PRIMARY KEY,
    idempotency_key     VARCHAR(64)  NOT NULL,
    source_account      VARCHAR(50)  NOT NULL,
    destination_account VARCHAR(50)  NOT NULL,
    amount              DECIMAL(15,2) NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    exchange_rate       DECIMAL(15,6) NOT NULL DEFAULT 1.000000,
    settlement_amount   DECIMAL(15,2) NOT NULL,
    settlement_currency VARCHAR(3)   NOT NULL,
    description         TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    error_code          VARCHAR(50),
    retry_count         INT          NOT NULL DEFAULT 0,
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
    triggered_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
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
    holder_last_name VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(255)  NOT NULL,
    balance         DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed accounts for testing
INSERT INTO accounts (account_number, account_name, holder_last_name, password_hash, balance, currency) VALUES
    ('ACC-00001', 'HSBC Operations',   'Operations', 't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=', 10000000.00, 'USD'),
    ('ACC-00002', 'HSBC Trading Desk',  'Desk',       't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',  5000000.00, 'USD'),
    ('ACC-00003', 'HSBC Custody',       'Custody',    't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',  3000000.00, 'USD'),
    ('ACC-00004', 'HSBC Markets',       'Markets',    't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',  2000000.00, 'EUR'),
    ('ACC-00005', 'HSBC Wealth',        'Wealth',     't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',  1000000.00, 'GBP'),
    ('ACC-00006', 'HSBC Digital',       'Digital',    't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',   500000.00, 'CNY'),
    ('ACC-00007', 'HSBC Retail',        'Retail',     't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',   200000.00, 'USD'),
    ('ACC-00008', 'HSBC Commercial',    'Commercial', 't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',   100000.00, 'USD'),
    ('ACC-00009', 'HSBC Investment',    'Investment', 't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',     5000.00, 'USD'),
    ('ACC-00010', 'HSBC Low Balance',   'Balance',    't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',        0.00, 'USD')
ON DUPLICATE KEY UPDATE account_name = VALUES(account_name),
    holder_last_name = VALUES(holder_last_name), password_hash = VALUES(password_hash);

-- Performance indexes
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_currency ON payments (currency);
CREATE INDEX idx_payments_created_at ON payments (created_at);
CREATE TABLE IF NOT EXISTS exchange_rates (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_currency   VARCHAR(3) NOT NULL,
    to_currency     VARCHAR(3) NOT NULL,
    rate            DECIMAL(15,6) NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_currency_pair (from_currency, to_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO exchange_rates (from_currency, to_currency, rate) VALUES
    ('USD', 'EUR', 0.92), ('USD', 'GBP', 0.79), ('USD', 'CNY', 7.24),
    ('EUR', 'USD', 1.09), ('EUR', 'GBP', 0.86), ('EUR', 'CNY', 7.87),
    ('GBP', 'USD', 1.27), ('GBP', 'EUR', 1.16), ('GBP', 'CNY', 9.15),
    ('CNY', 'USD', 0.138), ('CNY', 'EUR', 0.127), ('CNY', 'GBP', 0.109)
ON DUPLICATE KEY UPDATE rate = VALUES(rate);

CREATE TABLE IF NOT EXISTS notification_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      VARCHAR(36),
    event_type      VARCHAR(50) NOT NULL,
    message         TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS risk_assessments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id        VARCHAR(36)  NOT NULL,
    risk_score        INT          NOT NULL,
    risk_level        VARCHAR(10)  NOT NULL,
    risk_decision     VARCHAR(10)  NOT NULL,
    triggered_rules   TEXT,
    statistical_flags TEXT,
    reasoning         TEXT,
    llm_model_used    VARCHAR(50),
    assessed_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_risk_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_risk_payment_id ON risk_assessments (payment_id);
CREATE INDEX idx_risk_level ON risk_assessments (risk_level);
CREATE INDEX idx_risk_decision ON risk_assessments (risk_decision);

CREATE TABLE IF NOT EXISTS account_stats (
    account_number   VARCHAR(50)   PRIMARY KEY,
    avg_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    std_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    median_amount    DECIMAL(15,2) NOT NULL DEFAULT 0,
    q1_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,
    q3_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_count      INT           NOT NULL DEFAULT 0,
    known_payees     TEXT,
    last_updated     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_stats_account FOREIGN KEY (account_number) REFERENCES accounts(account_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO account_stats (account_number, avg_amount, std_amount, median_amount,
    q1_amount, q3_amount, total_count, known_payees) VALUES
    ('ACC-00001', 50000.00, 30000.00, 45000.00, 20000.00, 75000.00, 120,
     '["ACC-00002","ACC-00003","ACC-00004"]'),
    ('ACC-00002', 20000.00, 15000.00, 18000.00, 8000.00, 30000.00, 85,
     '["ACC-00001","ACC-00005"]'),
    ('ACC-00003', 10000.00, 8000.00, 9000.00, 5000.00, 14000.00, 60,
     '["ACC-00001","ACC-00002"]'),
    ('ACC-00007', 500.00, 300.00, 450.00, 200.00, 700.00, 200,
     '["ACC-00001","ACC-00008"]'),
    ('ACC-00009', 50.00, 30.00, 45.00, 20.00, 70.00, 15,
     '["ACC-00001"]')
ON DUPLICATE KEY UPDATE avg_amount = VALUES(avg_amount);

CREATE INDEX idx_status_history_payment_id ON status_history (payment_id);
