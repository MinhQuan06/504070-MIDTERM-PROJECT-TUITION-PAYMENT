ALTER TABLE transactions DROP CHECK chk_tx_status;
ALTER TABLE transactions ADD CONSTRAINT chk_tx_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED'));
ALTER TABLE transactions
  ADD COLUMN tuition_version INT NULL,
  ADD COLUMN payer_email VARCHAR(254) NULL,
  ADD COLUMN payer_full_name VARCHAR(100) NULL,
  ADD COLUMN next_attempt_at DATETIME(6) NULL,
  ADD COLUMN saga_attempts INT NOT NULL DEFAULT 0;
CREATE INDEX idx_transactions_recovery ON transactions(status, next_attempt_at);
