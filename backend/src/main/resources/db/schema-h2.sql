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
