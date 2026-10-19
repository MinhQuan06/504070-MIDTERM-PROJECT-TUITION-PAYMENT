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

