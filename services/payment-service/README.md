# Payment Service (Java + Spring Boot)

Số dư, giao dịch, OTP, lịch sử giao dịch; điều phối luồng thanh toán và xử lý concurrency. DB: Payment DB (bảng `accounts`, `transactions`, `otps`).

API: `GET /api/v1/accounts/me`, `POST /api/v1/transactions`, `POST /api/v1/transactions/{id}/confirm`, `GET /api/v1/transactions`, `GET /api/v1/transactions/{id}`.

Phát sự kiện `OtpRequested`, `PaymentSucceeded` lên RabbitMQ.

Việc cần làm: khởi tạo project Spring Boot, Dockerfile, migration (Flyway), trừ số dư bằng cập nhật có điều kiện, seed số dư mẫu.
