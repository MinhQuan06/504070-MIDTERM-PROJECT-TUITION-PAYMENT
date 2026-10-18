-- 1. Tạo bảng accounts sử dụng InnoDB để hỗ trợ Transaction và Row-level locking
-- MySQL 8.0.16+ hỗ trợ đầy đủ ràng buộc CHECK (balance >= 0)
CREATE TABLE accounts (
                          user_id BIGINT PRIMARY KEY,
                          balance BIGINT NOT NULL,
                          version INT NOT NULL DEFAULT 0,
                          CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Seed dữ liệu mẫu (Giả sử user_id 1 và 2 là tài khoản mẫu)
INSERT INTO accounts (user_id, balance, version) VALUES
                                                     (1, 50000000, 0), -- 50 triệu VND
                                                     (2, 10000000, 0); -- 10 triệu VND