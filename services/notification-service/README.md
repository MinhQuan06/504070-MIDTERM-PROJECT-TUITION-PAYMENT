# Notification Service (TypeScript + NestJS)

Nhận sự kiện từ RabbitMQ và gửi email. Không có DB, không có REST công khai.

Sự kiện: `OtpRequested` (gửi OTP), `PaymentSucceeded` (email xác nhận).

Việc cần làm: khởi tạo project NestJS, Dockerfile, consumer RabbitMQ, gửi email qua SMTP (dev: Mailpit).
