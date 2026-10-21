# Payment Service

Java 21 + Spring Boot + MySQL/Flyway. Quản lý số dư, giao dịch, OTP, Saga và Transactional Outbox.

## Chạy

Dùng [Docker Compose gốc](../../README.md). API http://localhost:8082; Swagger http://localhost:8082/swagger-ui.html. Các khóa được tạo trong .env gốc bằng init-outbox-key.mjs và init-secrets.mjs; không đổi khóa khi còn dữ liệu cũ.

## API

Tất cả API dưới đây yêu cầu Bearer JWT. Payer được lấy từ token.

| Method | URI | Quyền/chức năng |
| --- | --- | --- |
| GET | /api/v1/accounts/{userId} | Chính chủ xem số dư |
| POST | /api/v1/accounts | Admin tạo/cập nhật số dư demo |
| POST | /api/v1/transactions | Tạo giao dịch: tuitionId, studentId, amount |
| POST | /api/v1/transactions/{id}/resend-otp | Chủ giao dịch gửi lại OTP |
| POST | /api/v1/transactions/{id}/verify-otp | Chủ giao dịch xác nhận: otpCode |
| GET | /api/v1/transactions/{id} | Chủ giao dịch xem chi tiết |
| GET | /api/v1/transactions/user/{userId} | Chính chủ xem lịch sử |

## Xử lý giao dịch

PENDING → PROCESSING → SUCCESS hoặc FAILED. OTP consume, debit và PROCESSING commit cùng nhau; thiếu tiền rollback. Student từ chối nghiệp vụ thì hoàn tiền đúng một lần. Timeout/kết quả chưa rõ giữ PROCESSING và tự retry; xác nhận lại không debit thêm.

OTP tối đa 5 phút, 5 lần nhập sai/mã, cooldown 60 giây và tối đa 5 lượt gửi lại/giao dịch. Notification kiểm tra eventId hiện hành qua API nội bộ trước khi gửi OTP. Outbox ghi cùng transaction nghiệp vụ và mã hóa nội dung trước khi gửi RabbitMQ.

## Kiểm thử

Tại gốc chạy lần lượt smoke-full-stack và smoke-concurrency theo [README gốc](../../README.md). Có bài Saga/Outbox trên MySQL riêng; không trỏ test vào DB demo. Xem [kết quả và cách chạy](../../docs/tests/transaction-concurrency-2026-10-21.md), [cấu hình bảo mật](../../docs/security-hardening.md).
