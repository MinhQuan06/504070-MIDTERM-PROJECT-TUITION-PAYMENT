# Payment Service

Java 21+, Spring Boot, MySQL 8.0 và Flyway. Service quản lý số dư, giao dịch và OTP; phát sự kiện email qua Transactional Outbox đến Notification.

## Chạy cùng project

Tại thư mục gốc repository:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
node services/payment-service/scripts/init-outbox-key.mjs
docker compose up -d --build payment-service notification-service gateway
```

Mặc định cần User/Student thật. Khi demo local với mock:

```powershell
$env:PAYMENT_MOCK_ENABLED = 'true'
docker compose up -d --build payment-service notification-service gateway
node services/payment-service/scripts/smoke-payment-notification.mjs
Remove-Item Env:PAYMENT_MOCK_ENABLED
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
- MOCK_ENABLED=true nếu thử bằng mock.

Trong thư mục service chạy `./mvnw.cmd spring-boot:run`. Gọi trực tiếp http://localhost:8082; Gateway Docker vẫn trỏ container payment-service, không trỏ Java trên Windows.

## Sự kiện và Outbox

Exchange `ibanking.events`, queue `notification.email`, routing keys `OtpRequested` / `PaymentSucceeded`; JSON theo README Notification. ID giao dịch là chuỗi, mốc thời gian dùng ISO 8601 UTC. Mỗi OTP có eventId mới; biên nhận dùng `payment-succeeded-{transactionId}`. Không reset Payment DB độc lập với dấu dedupe nếu tái sử dụng ID trong demo.

Sự kiện OTP ghi cùng transaction tạo/cấp lại OTP; SUCCESS và receipt ghi cùng transaction cục bộ qua PaymentCompletionService. Publisher nền retry khi broker lỗi và chỉ đánh dấu PUBLISHED sau ACK không có mandatory return. Payload mã hóa AES-GCM, xóa khi PUBLISHED/EXPIRED. Khóa trong .env không commit; giữ nguyên qua restart và backup. Script init không ghi đè khóa đã có hoặc in khóa ra console.

Chi tiết: [Transactional Outbox](../../docs/report/payment-outbox.md).

## Kiểm thử

```powershell
# Trong thư mục Payment; contract/sender unit test không cần DB.
./mvnw.cmd '-Dtest=NotificationContractTest,OutboxSenderTest' test
# contextLoads cần cấu hình MySQL phù hợp.
./mvnw.cmd test
```

OutboxIntegrationTest là test MySQL có fixture, mặc định tắt. Chỉ bật OUTBOX_TEST_DATABASE=true với database riêng tên payment_outbox_test; không chạy trên dữ liệu nghiệp vụ. Test xác nhận tên database trước khi xóa fixture. Tắt publisher khác đang dùng cùng DB test. Xem tài liệu Outbox để cấu hình.

## Giới hạn

- Chưa có JWT/quyền sở hữu hoặc kiểm soát seed API.
- Saga debit/Student chưa có worker phục hồi; timeout Student vẫn có thể bị xử lý như từ chối.
- Sinh/resend OTP chưa bảo đảm uniqueness khi chạy đồng thời.
- Outbox bảo đảm at-least-once DB -> broker, không bảo đảm SMTP exactly-once.
- Cần theo dõi PENDING lâu, bảo vệ khóa mã hóa và thống nhất lưu giữ metadata Outbox.
