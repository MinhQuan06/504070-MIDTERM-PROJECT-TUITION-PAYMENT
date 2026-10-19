# Notification Service

NestJS/TypeScript worker nhận JSON từ RabbitMQ và gửi email qua Nodemailer. Không mở REST API công khai, không có database nghiệp vụ; không sinh/xác thực OTP hoặc xử lý số dư.

## Chạy với project

Tại thư mục gốc:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d --build notification-service
```

Compose tự bật RabbitMQ và Mailpit. Xem email tại http://localhost:8025; RabbitMQ Management http://localhost:15672, credential theo .env gốc. Mailpit giữ email local, không gửi đến hộp thư thật.

Để demo cùng Payment, xem README gốc hoặc README Payment. Payment cần khóa Outbox: chạy `node services/payment-service/scripts/init-outbox-key.mjs` trước lần khởi động đầu.

## Chạy local

Node.js 22+, npm, Docker Desktop/Compose v2. Trong thư mục service:

```powershell
npm.cmd ci
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
npm.cmd run smtp:check
npm.cmd run start:dev
```

Trước đó bật RabbitMQ/Mailpit bằng Compose gốc và dừng notification-service container. .env service dùng localhost:5672, SMTP localhost:1025; user/password RabbitMQ phải khớp .env gốc. Không chạy worker local và container đồng thời: cơ chế dedupe hiện chỉ hỗ trợ một instance.

Trong terminal thứ hai cùng thư mục:

```powershell
npm.cmd run demo:otp
npm.cmd run demo:payment
```

Có thể đặt DEMO_EMAIL để đổi người nhận, EVENT_ID để thử phát lại cùng sự kiện. Bỏ EVENT_ID sau khi thử để các lượt tiếp theo có ID mới.

## Cấu trúc

| File | Vai trò |
| --- | --- |
| src/main.ts | NestJS application context, không mở HTTP |
| src/config.ts | Đọc/kiểm tra cấu hình |
| src/events.ts | Schema JSON và hạn OTP |
| src/topology.ts | Exchange, queue, retry, DLQ |
| src/worker.ts | Consume, ACK, reconnect và retry |
| src/email.ts | SMTP và mẫu email HTML/plain text |
| src/dedupe.ts | Dấu eventId đã gửi trong thư mục data/sent |
| scripts/ | Phát sự kiện mẫu, kiểm tra SMTP và contract Java |
| test/ | Unit test |

## Hợp đồng sự kiện

Exchange topic durable `ibanking.events`; queue durable `notification.email`, bind routing key `OtpRequested` và `PaymentSucceeded`. JSON UTF-8 thuần, content_type application/json, message persistent. Không bọc {pattern,data}. Payment đã đồng bộ hợp đồng này và dùng Outbox để retry cùng eventId; cấp OTP mới phải có eventId mới. Khuyến nghị message_id=eventId và publisher confirms.

### OtpRequested

```json
{
  "eventId": "evt-otp-001",
  "type": "OtpRequested",
  "transactionId": "10001",
  "email": "student@example.com",
  "fullName": "Nguyễn Văn An",
  "otp": "123456",
  "expiresAt": "2030-01-01T00:05:00Z"
}
```

OTP là chuỗi 6 chữ số; ví dụ chỉ minh họa schema, dùng script demo để có hạn mới. Payment chịu trách nhiệm hiệu lực tối đa 5 phút, tính duy nhất và dùng thành công một lần. Notification từ chối gửi khi expiresAt đã hết. ID dùng chuỗi để tránh mất chính xác BIGINT giữa Java/JavaScript.

### PaymentSucceeded

```json
{
  "eventId": "payment-succeeded-10001",
  "type": "PaymentSucceeded",
  "transactionId": "10001",
  "email": "student@example.com",
  "fullName": "Nguyễn Văn An",
  "studentId": "52300001",
  "amount": 15000000,
  "completedAt": "2030-01-01T00:01:00Z"
}
```

amount là số nguyên dương an toàn JavaScript, đơn vị VND. Mốc thời gian có offset; email hiển thị giờ Việt Nam. Không nhận userId thay email, Payment phải lấy email người nộp trước khi enqueue.

## Cấu hình

Chạy local đọc .env trong service. Container nhận các biến chọn lọc từ Compose gốc, không đọc toàn bộ bí mật của project.

| Biến | Ý nghĩa |
| --- | --- |
| RABBITMQ_HOST/PORT | localhost:5672 local; rabbitmq:5672 trong Docker |
| RABBITMQ_USER/PASSWORD | Khớp broker |
| RABBITMQ_URL | Tùy chọn, ghi đè các trường host/port/user/password |
| SMTP_HOST/PORT | localhost:1025 local; mailpit:1025 trong Docker |
| SMTP_SECURE | true khi TLS trực tiếp, thường cổng 465 |
| SMTP_REQUIRE_TLS | true để yêu cầu STARTTLS, thường cổng 587 |
| SMTP_USER/PASSWORD | Trống với Mailpit; khai báo đồng thời nếu cần đăng nhập |
| MAIL_FROM | Người gửi; Compose ánh xạ từ SMTP_FROM |
| MAX_RETRIES | Mặc định 3 lần sau lần gửi đầu |
| RETRY_DELAY_MS | Mặc định 5000 ms |
| DEDUPE_DIR | ./data/sent local; /app/data/sent trong container |

Compose gốc dùng NOTIFICATION_MAX_RETRIES/NOTIFICATION_RETRY_DELAY_MS để truyền hai cấu hình retry. Với SMTP thật, đổi host, port, TLS, tài khoản và địa chỉ gửi theo nhà cung cấp; không tắt kiểm tra chứng chỉ TLS.

## Retry và giới hạn

- Manual ACK sau khi SMTP chấp nhận thư và ghi dấu dedupe, hoặc sau khi broker xác nhận bản chuyển sang retry/DLQ.
- SMTP lỗi: notification.email.retry, chờ TTL rồi quay về queue chính; hết retry: notification.email.dlq.
- JSON sai hoặc OTP hết hạn: DLQ ngay. DLQ không tự replay; khi phát lại giữ eventId, không replay OTP quá hạn.
- Không log payload/OTP/email người nhận. Retry/DLQ chứa nội dung email nên phải giới hạn quyền broker; dùng TLS khi triển khai ngoài máy local.
- Dedupe dùng file SHA-256 của eventId, volume giữ qua restart; chỉ hỗ trợ một worker instance, chưa phải inbox phân tán.
- SMTP đã nhận thư nhưng process chết trước ghi dấu vẫn có thể gửi trùng. Message-ID ổn định không bảo đảm nhà cung cấp tự loại trùng.
- Đổi RETRY_DELAY_MS khi queue tồn tại có thể gây PRECONDITION_FAILED. Chỉ xóa queue retry trống hoặc giữ nguyên cấu hình.
- Chưa tự dọn dấu dedupe; cần chính sách lưu giữ khi chạy lâu dài. Mailpit demo không gắn volume giữ email khi tạo lại container.

## Kiểm thử và xử lý lỗi

```powershell
npm.cmd run build
npm.cmd test
npm.cmd run smtp:check
```

Unit test kiểm tra validation, expiry, escape HTML, ACK, duplicate, retry/DLQ và lỗi publish retry. Contract liên ngôn ngữ: chạy NotificationContractTest của Java trước, sau đó `npm.cmd run test:contract` trong thư mục này.

Đã kiểm chứng trên container: SMTP outage/recovery, hết retry vào DLQ, dedupe sau restart, OTP hết hạn trước/trong retry, payload sai và message unacked được giao lại khi worker bị dừng trước SMTP acceptance. Các ca này không chứng minh SMTP exactly-once.

Kiểm tra lỗi: mở Docker Desktop; đọc log notification-service, xác nhận host/port/credential và eventId chưa được gửi. Windows dùng localhost, container dùng service name. Xem queue DLQ và OTP expiry nếu không có email. Không chạy đồng thời Compose riêng cũ của Payment vì trùng cổng RabbitMQ.

Chi tiết Outbox bên Payment: [Transactional Outbox](../../docs/report/payment-outbox.md).
