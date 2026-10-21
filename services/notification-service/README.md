# Notification Service

NestJS/TypeScript worker nhận sự kiện RabbitMQ và gửi OTP/biên nhận bằng SMTP. Không mở REST API công khai và không tự xác thực OTP hoặc trừ tiền.

## Chạy

Dùng [Docker Compose gốc](../../README.md). Email demo nằm tại http://localhost:8025 (Mailpit), chưa gửi đến hộp thư thật.

Worker local: dừng container Notification, chạy npm.cmd ci trong thư mục service, cấu hình .env rồi npm.cmd run start:dev. Cần RabbitMQ/SMTP, PAYMENT_SERVICE_URL=http://localhost:8082 và INTERNAL_API_KEY khớp USER_INTERNAL_API_KEY gốc. Không commit khóa; không chạy hai worker cùng lúc vì dedupe hiện hỗ trợ một instance.

## Gửi email

- OtpRequested: kiểm tra mã chưa hết hạn và eventId còn hiện hành qua Payment trước mỗi lần gửi/retry.
- PaymentSucceeded: gửi biên nhận; không phụ thuộc lookup OTP.
- Lỗi tạm thời chuyển retry; lỗi vĩnh viễn/hết lượt chuyển DLQ. Xác nhận publish retry trước khi ACK bản gốc.

Muốn gửi email thật, cấu hình SMTP trong .env gốc và email thật của User. Không đưa mật khẩu SMTP vào Git. demo:otp tạo sự kiện giả không thay cho luồng tích hợp; tạo giao dịch qua Payment để có eventId hợp lệ.

SMTP không bảo đảm exactly-once và không thu hồi được email đã gửi. Dedupe giúp bỏ các event đã ghi nhận hoàn tất.

## Kiểm thử

Trong thư mục service: npm.cmd test, npm.cmd run build, npm.cmd run test:contract. Chi tiết chính sách OTP và cấu hình: [security-hardening](../../docs/security-hardening.md).
