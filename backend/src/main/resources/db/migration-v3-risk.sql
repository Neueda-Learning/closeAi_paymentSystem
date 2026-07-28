-- Apply this migration to an existing payment_system database after pulling
-- the risk-assessment feature. It is safe to run repeatedly.

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
    INDEX idx_risk_payment_id (payment_id),
    INDEX idx_risk_level (risk_level),
    INDEX idx_risk_decision (risk_decision),
    CONSTRAINT fk_risk_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS account_stats (
    account_number   VARCHAR(50)   PRIMARY KEY,
    avg_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    std_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    median_amount    DECIMAL(15,2) NOT NULL DEFAULT 0,
    q1_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,
    q3_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_count      INT           NOT NULL DEFAULT 0,
    known_payees     TEXT,
    last_updated     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_stats_account FOREIGN KEY (account_number)
        REFERENCES accounts(account_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO account_stats (
    account_number,
    avg_amount,
    std_amount,
    median_amount,
    q1_amount,
    q3_amount,
    total_count,
    known_payees
) VALUES
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
ON DUPLICATE KEY UPDATE
    avg_amount = VALUES(avg_amount),
    std_amount = VALUES(std_amount),
    median_amount = VALUES(median_amount),
    q1_amount = VALUES(q1_amount),
    q3_amount = VALUES(q3_amount),
    total_count = VALUES(total_count),
    known_payees = VALUES(known_payees);
