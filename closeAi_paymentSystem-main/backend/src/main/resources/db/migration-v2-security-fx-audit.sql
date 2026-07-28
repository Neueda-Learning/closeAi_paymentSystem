-- Run this once against an existing database before deploying this version.
ALTER TABLE accounts
    ADD COLUMN holder_last_name VARCHAR(50) NULL AFTER account_name,
    ADD COLUMN password_hash VARCHAR(255) NULL AFTER holder_last_name;

ALTER TABLE payments
    ADD COLUMN exchange_rate DECIMAL(15,6) NULL AFTER currency,
    ADD COLUMN settlement_amount DECIMAL(15,2) NULL AFTER exchange_rate,
    ADD COLUMN settlement_currency VARCHAR(3) NULL AFTER settlement_amount;

ALTER TABLE status_history
    ADD COLUMN triggered_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM' AFTER error_code;

-- Existing demo accounts use Payment@123. Replace these credentials outside demo environments.
UPDATE accounts
SET holder_last_name = SUBSTRING_INDEX(account_name, ' ', -1),
    password_hash = 't29S90CTJzNLYQvkGiTMwQ==:DTeumao0VTDLzIerH/JW9dWQW96oMJy+hP/XJ+PCmfg='
WHERE holder_last_name IS NULL OR password_hash IS NULL;

UPDATE payments p
JOIN accounts a ON a.account_number = p.destination_account
SET p.exchange_rate = 1.000000,
    p.settlement_amount = p.amount,
    p.settlement_currency = a.currency
WHERE p.exchange_rate IS NULL;

-- Legacy payments may reference accounts that no longer exist. Preserve them
-- using a 1:1 settlement in the original payment currency.
UPDATE payments
SET exchange_rate = 1.000000,
    settlement_amount = amount,
    settlement_currency = currency
WHERE exchange_rate IS NULL
   OR settlement_amount IS NULL
   OR settlement_currency IS NULL;

ALTER TABLE accounts
    MODIFY holder_last_name VARCHAR(50) NOT NULL,
    MODIFY password_hash VARCHAR(255) NOT NULL;

ALTER TABLE payments
    MODIFY exchange_rate DECIMAL(15,6) NOT NULL,
    MODIFY settlement_amount DECIMAL(15,2) NOT NULL,
    MODIFY settlement_currency VARCHAR(3) NOT NULL;
