-- One short DB lock coordinates OTP allocation across all Payment instances.
-- READ COMMITTED issuance sees the last allocator commit and avoids empty-range gap lock cycles.
CREATE TABLE otp_issuance_lock (
  lock_id INT PRIMARY KEY,
  CONSTRAINT chk_otp_lock_singleton CHECK (lock_id=1)
) ENGINE=InnoDB;
INSERT INTO otp_issuance_lock(lock_id) VALUES(1);
CREATE INDEX idx_otps_active_hash ON otps(code_hash, used_at, expires_at);
