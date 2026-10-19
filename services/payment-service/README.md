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

MySQL dùng PAYMENT_DB_NAME/USER/PASSWORD và PAYMENT_DB_ROOT_PASSWORD từ .env gốc. 

## Chạy local

Cần JDK 21+ và JAVA_HOME đúng. Bật payment-db/rabbitmq/mailpit bằng Compose gốc, dừng container Payment để giải phóng 8082. Java không tự đọc .env, cần truyền các biến sau cho tiến trình:

- SPRING_DATASOURCE_URL: JDBC MySQL tại localhost:3307, đúng database trong .env.
- SPRING_DATASOURCE_USERNAME/PASSWORD: khớp .env gốc.
- SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD: localhost:5672 và credential .env.
- OUTBOX_ENCRYPTION_KEY: khóa đã tạo trong .env.
- MOCK_ENABLED=true nếu thử bằng mock.

Trong thư mục service chạy `./mvnw.cmd spring-boot:run`. Gọi trực tiếp http://localhost:8082; Gateway Docker vẫn trỏ container payment-service, không trỏ Java trên Windows.
.
