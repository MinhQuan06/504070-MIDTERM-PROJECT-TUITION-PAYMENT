ALTER TABLE transactions
  ADD COLUMN otp_failed_attempts INT NOT NULL DEFAULT 0,
  ADD COLUMN otp_resend_count INT NOT NULL DEFAULT 0,
  ADD COLUMN otp_issued_at DATETIME(6) NULL;
ALTER TABLE otps ADD COLUMN event_id VARCHAR(128) NULL;
CREATE UNIQUE INDEX idx_otps_event_id ON otps(event_id);
