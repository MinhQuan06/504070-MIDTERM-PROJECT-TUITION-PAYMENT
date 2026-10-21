# iBanking — Đóng học phí

Ứng dụng mô phỏng thanh toán học phí TDTU: đăng nhập, tra cứu MSSV, xác nhận OTP qua email và xem lịch sử giao dịch.

## Chạy nhanh

Cần Docker Desktop đang chạy và Node.js 22+. Mở PowerShell tại thư mục dự án:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
node services/payment-service/scripts/init-outbox-key.mjs
node services/user-service/scripts/init-secrets.mjs
docker compose up -d --build --wait
docker compose exec user-service python seed.py
```

Mở **http://localhost:8080**. Tài khoản demo: `student / Admin123`; Admin: `admin / Admin123`. Seed giữ nguyên tài khoản đã có. Nếu User đã có từ trước nhưng chưa có số dư, Admin cần tạo tài khoản số dư demo.

OTP và biên nhận mặc định nằm trong **Mailpit**, chưa gửi đến hộp thư thật. Muốn gửi email thật, cấu hình SMTP và email người dùng; không đưa mật khẩu SMTP vào Git.

| Thành phần | Địa chỉ |
| --- | --- |
| Web và Gateway | http://localhost:8080 |
| Mailpit | http://localhost:8025 |
| User Swagger | http://localhost:8001/docs |
| Payment Swagger | http://localhost:8082/swagger-ui.html |
| Student Swagger | http://localhost:5071/swagger |
| RabbitMQ Management | http://localhost:15672 |

## Demo

Đăng nhập → Đóng học phí → nhập MSSV (ví dụ SV001) → chọn khoản chưa trả → đồng ý điều khoản → lấy OTP từ Mailpit → xác nhận → kiểm tra số dư và lịch sử. Nếu giao dịch đang PROCESSING, chờ kết quả; không tạo giao dịch thay thế.

## Kiểm thử

```powershell
node services/user-service/scripts/smoke-full-stack.mjs
node services/payment-service/scripts/smoke-concurrency.mjs
```

Chạy lần lượt; script tạo dữ liệu riêng và giữ lại để xem. Nếu đăng nhập bị 429 do chạy lặp nhanh, chờ một phút. Xem [kết quả transaction/concurrency](docs/tests/transaction-concurrency-2026-10-21.md).

## Cập nhật và dừng

Lần đầu đổi sang cấu hình mạng mới: `docker compose down`, rồi chạy lại lệnh khởi động. Không thêm `-v`, không xóa volume và không đổi khóa Outbox nếu còn dữ liệu cũ. Dừng tạm bằng `docker compose stop`.

Không commit `.env`, `node_modules`, `dist`, `target`, `bin`, `obj` hoặc file tạm. Xem [cấu hình bảo mật và OTP](docs/security-hardening.md).

## Mã nguồn

- [fontend](fontend/README.md): React/TypeScript, gọi API qua Gateway.
- [User](services/user-service/README.md): đăng nhập, JWT và thông tin người dùng.
- [Student](services/student-service/README.md): sinh viên và học phí.
- [Payment](services/payment-service/README.md): số dư, OTP, Saga và Outbox.
- [Notification](services/notification-service/README.md): nhận sự kiện và gửi email.
