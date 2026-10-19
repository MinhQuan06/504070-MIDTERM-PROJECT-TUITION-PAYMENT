CREATE TABLE outbox_events (
    event_id VARCHAR(128) PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    exchange_name VARCHAR(128) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload_ciphertext MEDIUMTEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    published_at DATETIME(6) NULL,
    last_error_code VARCHAR(64) NULL,
    CONSTRAINT fk_outbox_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'EXPIRED')),
    INDEX idx_outbox_pending (status, available_at, event_id),
    INDEX idx_outbox_transaction (transaction_id, event_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
