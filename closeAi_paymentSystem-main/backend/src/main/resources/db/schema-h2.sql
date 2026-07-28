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
    triggered_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
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
    holder_last_name VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(255)  NOT NULL,
    balance         DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed accounts
MERGE INTO accounts (account_number, account_name, holder_last_name, password_hash, balance, currency) KEY(account_number) VALUES
    ('ACC-00001', 'HSBC Operations',   'Operations', 't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=', 10000000.00, 'USD'),
    ('ACC-00002', 'HSBC Trading Desk',  'Desk',       't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',  5000000.00, 'USD'),
    ('ACC-00003', 'HSBC Custody',       'Custody',    't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',  3000000.00, 'USD'),
    ('ACC-00004', 'HSBC Markets',       'Markets',    't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',  2000000.00, 'EUR'),
    ('ACC-00005', 'HSBC Wealth',        'Wealth',     't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',  1000000.00, 'GBP'),
    ('ACC-00006', 'HSBC Digital',       'Digital',    't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',   500000.00, 'CNY'),
    ('ACC-00007', 'HSBC Retail',        'Retail',     't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',   200000.00, 'USD'),
    ('ACC-00008', 'HSBC Commercial',    'Commercial', 't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',   100000.00, 'USD'),
    ('ACC-00009', 'HSBC Investment',    'Investment', 't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',     5000.00, 'USD'),
    ('ACC-00010', 'HSBC Low Balance',   'Balance',    't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg=',        0.00, 'USD');

CREATE TABLE IF NOT EXISTS exchange_rates (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_currency   VARCHAR(3) NOT NULL,
    to_currency     VARCHAR(3) NOT NULL,
    rate            DECIMAL(15,6) NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (from_currency, to_currency)
);

MERGE INTO exchange_rates (from_currency, to_currency, rate) KEY(from_currency, to_currency) VALUES
    ('USD', 'EUR', 0.92), ('USD', 'GBP', 0.79), ('USD', 'CNY', 7.24),
    ('EUR', 'USD', 1.09), ('EUR', 'GBP', 0.86), ('EUR', 'CNY', 7.87),
    ('GBP', 'USD', 1.27), ('GBP', 'EUR', 1.16), ('GBP', 'CNY', 9.15),
    ('CNY', 'USD', 0.138), ('CNY', 'EUR', 0.127), ('CNY', 'GBP', 0.109);
