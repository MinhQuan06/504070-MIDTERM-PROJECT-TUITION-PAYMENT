-- Tạo bảng otps liên kết khóa ngoại với bảng transactions trong cùng Payment DB
CREATE TABLE otps (
                      otp_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      transaction_id BIGINT NOT NULL,
                      code_hash VARCHAR(255) NOT NULL,
                      expires_at TIMESTAMP NOT NULL,
                      used_at TIMESTAMP NULL,
                      CONSTRAINT fk_otps_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Đánh index (transaction_id) theo thiết kế mục 4.1
CREATE INDEX idx_otps_transaction_id ON otps (transaction_id);