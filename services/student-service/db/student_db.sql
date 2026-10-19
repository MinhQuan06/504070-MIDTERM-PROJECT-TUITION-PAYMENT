-- Run against the selected database; Docker creates MYSQL_DATABASE before this script.
-- Initialize only a fresh volume; do not replay seed updates over payment data.

CREATE TABLE IF NOT EXISTS students (
    student_id VARCHAR(10) PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tuitions (
    tuition_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id VARCHAR(10) NOT NULL,
    semester VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL,
    status ENUM('UNPAID', 'PAID') NOT NULL DEFAULT 'UNPAID',
    version INT NOT NULL DEFAULT 0,
    paid_by_transaction_id BIGINT NULL,
    FOREIGN KEY (student_id) REFERENCES students(student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO students (student_id, full_name) VALUES
('SV001', 'Nguyễn Bảo Khang'),
('SV002', 'Tôn Thanh Thiên'),
('SV003', 'Thạch Hoàng Quân'),
('SV004', 'Minh Quân'),
('SV005', 'Nguyễn Văn A')
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);


INSERT INTO tuitions (tuition_id, student_id, semester, amount, status, version, paid_by_transaction_id) VALUES
(1, 'SV001', 'HK1_2026', 12000000, 'UNPAID', 0, NULL),
(2, 'SV001', 'HK2_2026', 13500000, 'UNPAID', 0, NULL),
(3, 'SV002', 'HK1_2026', 10500000, 'PAID',   1, 10001),
(4, 'SV003', 'HK1_2026', 15000000, 'UNPAID', 0, NULL),
(5, 'SV004', 'HK1_2026', 9800000,  'PAID',   1, 10002),
(6, 'SV005', 'HK1_2026', 11000000, 'UNPAID', 0, NULL)
ON DUPLICATE KEY UPDATE amount = VALUES(amount), status = VALUES(status);