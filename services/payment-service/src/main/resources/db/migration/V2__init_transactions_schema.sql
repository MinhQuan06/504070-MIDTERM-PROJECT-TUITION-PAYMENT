-- Tạo bảng transactions theo đúng thiết kế ERD & Data Persistence
CREATE TABLE transactions (
                              transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              payer_user_id BIGINT NOT NULL,
                              tuition_id BIGINT NOT NULL,
                              student_id VARCHAR(10) NOT NULL,
                              amount BIGINT NOT NULL,
                              status VARCHAR(10) NOT NULL,
                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              completed_at TIMESTAMP NULL,
                              CONSTRAINT chk_tx_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
                              CONSTRAINT chk_tx_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Đánh index (payer_user_id, created_at DESC) để tối ưu truy vấn lịch sử giao dịch
CREATE INDEX idx_transactions_payer_created
    ON transactions (payer_user_id, created_at DESC);