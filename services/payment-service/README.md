# Payment Service

Java 21+, Spring Boot, MySQL 8.0 và Flyway. Service quản lý số dư, giao dịch và OTP; phát sự kiện email qua Transactional Outbox đến Notification.

## Chạy cùng project

Tại thư mục gốc repository:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
node services/payment-service/scripts/init-outbox-key.mjs
docker compose up -d --build payment-service notification-service gateway
```

Cấu hình hiện tại: `PAYMENT_USER_MOCK_ENABLED=true`, `PAYMENT_STUDENT_MOCK_ENABLED=false` trong `.env` gốc. Chạy `node services/payment-service/scripts/smoke-payment-student.mjs` tại gốc để kiểm tra Student thật đến email biên nhận. Script tạo dữ liệu demo riêng và giữ lại để xem.

Để chạy bài test cũ với cả hai mock, cần ghi đè cả hai cờ:

```powershell
$env:PAYMENT_USER_MOCK_ENABLED = 'true'
$env:PAYMENT_STUDENT_MOCK_ENABLED = 'true'
docker compose up -d --build payment-service notification-service gateway
node services/payment-service/scripts/smoke-payment-notification.mjs
Remove-Item Env:PAYMENT_USER_MOCK_ENABLED
Remove-Item Env:PAYMENT_STUDENT_MOCK_ENABLED
```

Mock chỉ phục vụ thử nghiệm: tuitionId 999 là đã đóng, 888 xung đột khi ghi PAID. Các ID khác dùng studentId/amount từ request, không chứng minh dữ liệu học phí hoặc concurrency của Student thật.

| Thành phần | Cổng máy | Cổng container |
| --- | --- | --- |
| Gateway | 8080 | 80 |
| Payment | 8082 | 8082 |
| Payment MySQL | 3307 | 3306 |
| RabbitMQ / Management | 5672 / 15672 | 5672 / 15672 |
| Mailpit SMTP / Web | 1025 / 8025 | 1025 / 8025 |

MySQL dùng PAYMENT_DB_NAME/USER/PASSWORD và PAYMENT_DB_ROOT_PASSWORD từ .env gốc. Không dùng volume PostgreSQL cũ cho MySQL; dữ liệu cũ không tự chuyển đổi hoặc xóa. Flyway tự tạo schema và seed tài khoản userId 1/2. Ưu tiên Compose gốc, không chạy đồng thời file Compose cũ trong thư mục service vì trùng cổng.

## API hiện tại

| Method | URI | Request / ý nghĩa |
| --- | --- | --- |
| GET | /api/v1/accounts/{userId} | Số dư |
| POST | /api/v1/accounts | userId, balance; seed demo |
| POST | /api/v1/transactions | payerUserId, tuitionId, studentId, amount |
| POST | /api/v1/transactions/{id}/resend-otp | Cấp lại OTP |
| POST | /api/v1/transactions/{id}/verify-otp | otpCode; thanh toán |
| GET | /api/v1/transactions/{id} | Chi tiết |
| GET | /api/v1/transactions/user/{userId} | Lịch sử |

API/Gateway hiện chưa có JWT/phân quyền và seed vẫn là API demo; chưa dùng cho môi trường thật. Các tên URI phản ánh code hiện tại, chưa hoàn toàn trùng đặc tả ban đầu.

## Chạy local

Cần JDK 21+ và JAVA_HOME đúng. Bật payment-db/rabbitmq/mailpit bằng Compose gốc, dừng container Payment để giải phóng 8082. Java không tự đọc .env, cần truyền các biến sau cho tiến trình:

- SPRING_DATASOURCE_URL: JDBC MySQL tại localhost:3307, đúng database trong .env.
- SPRING_DATASOURCE_USERNAME/PASSWORD: khớp .env gốc.
- SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD: localhost:5672 và credential .env.
- OUTBOX_ENCRYPTION_KEY: khóa đã tạo trong .env.
- USER_MOCK_ENABLED=true và STUDENT_MOCK_ENABLED=false để ghép Student thật khi chạy Java trực tiếp.

Trong thư mục service chạy `./mvnw.cmd spring-boot:run`. Gọi trực tiếp http://localhost:8082; Gateway Docker vẫn trỏ container payment-service, không trỏ Java trên Windows.
