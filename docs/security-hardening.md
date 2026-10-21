# Bản sửa sau review 21/10/2026

## Thay đổi

- Student: GET vẫn cho tra cứu; POST/PUT/DELETE ngoài pay bắt buộc Bearer JWT có role admin. Xác minh qua User, không tin role client gửi. Pay giữ khóa nội bộ và cơ chế replay.
- Payment: mỗi mã có tối đa 5 lần thử sai, chờ 60 giây giữa các lần cấp mã, tối đa 5 lần resend/giao dịch. Thử sai được commit mà không debit. Lỗi debit vẫn rollback việc consume OTP. Bộ đếm nằm trong MySQL, không mất khi restart.
- Notification: kiểm tra eventId với Payment trước mỗi lần gửi OTP, gồm retry; mã đã thay thế/đã dùng/bị khóa không gửi. Lookup không khả dụng thì retry, không bỏ qua kiểm tra. Biên nhận không gọi lookup OTP.
- Gateway: rate limit các POST Payment theo Bearer token, không giới hạn GET/poll; chuyển IP client bằng header do Gateway ghi đè. User chỉ tin địa chỉ Gateway đã cấu hình, tránh dùng chung hạn mức đăng nhập cho mọi IP.
- User: khóa dòng người dùng khi cập nhật bộ đếm đăng nhập để tránh mất lượt thử khi nhiều request đồng thời.
- Mailpit và RabbitMQ chỉ publish cổng host ở 127.0.0.1. Các container vẫn liên lạc bằng DNS Docker.
- Lỗi Student/User upstream không còn bị chuyển toàn bộ thành 404; timeout/5xx trả 503. MSSV khi tạo giao dịch được chuẩn hóa theo bản ghi Student.

## Cập nhật môi trường đang có

Không xóa volume và không chạy lại seed SQL trên dữ liệu thanh toán. Flyway tự áp dụng V7, thêm cột chính sách OTP và eventId.

Compose dùng Gateway IP 172.29.70.2, subnet 172.29.70.0/24 và dải cấp phát động 172.29.70.128/25. Dải động tách khỏi Gateway để tránh trùng IP. Nếu xung đột mạng khác, đổi đồng bộ DOCKER_SUBNET, DOCKER_DYNAMIC_RANGE và GATEWAY_INTERNAL_IP trong .env.

Lần đầu chuyển cấu hình mạng:

```powershell
docker compose build
docker compose down
docker compose up -d --wait
```

Không thêm `-v`: các database và volume notification được giữ nguyên. Các lần sau có thể dùng `docker compose up -d --build --wait`.

Notification chờ Payment healthy trước khi chạy. Worker local cần PAYMENT_SERVICE_URL và INTERNAL_API_KEY khớp USER_INTERNAL_API_KEY gốc. Không đưa khóa vào Git hoặc bundle frontend. GET /internal/notifications/otp-current/{eventId} chỉ dùng service-to-service, Gateway tiếp tục chặn /internal/.

OTP tạo trước V7 chưa có eventId; email OTP cũ còn chờ có thể bị bỏ qua. Người dùng có thể yêu cầu mã mới khi giao dịch vẫn PENDING. Không tạo giao dịch thay thế khi PROCESSING.

## Hành vi cần biết

- Giao diện hiển thị thời gian chờ gửi lại; server vẫn là nguồn kiểm soát chính.
- Sai 5 lần: mã bị khóa, không trừ tiền. Có thể cấp lại khi hết cooldown và còn hạn mức.
- Dùng hết 5 lượt resend: kiểm tra giao dịch vẫn PENDING trước khi tạo yêu cầu mới; không được thay thế giao dịch PROCESSING.
- Mailpit là hộp thư demo local. Muốn trình diễn web qua LAN không đồng nghĩa phải mở hộp thư OTP ra LAN.
- SMTP không có exactly-once và không thể thu hồi email đã được SMTP nhận. Kiểm tra current chặn các lần retry mã đã bị thay thế; không bảo đảm email không thể đến trễ nếu resend diễn ra sau khi SMTP đã nhận email cũ.

## Kiểm thử

Các test trong SagaIntegrationTest kiểm tra bộ đếm thử sai commit, khóa sau 5 lượt, không debit, cooldown và giới hạn resend. Chạy trên database riêng payment_saga_test; OutboxIntegrationTest yêu cầu payment_outbox_test. Không trỏ các bài test này vào database demo.

Script smoke-full-stack dùng user/Admin riêng, kiểm tra 401/403 của Student, cooldown resend, OTP sai không debit và thanh toán/biên nhận. smoke-concurrency kiểm tra hai yêu cầu bắt buộc của đề bằng dữ liệu riêng. Các script giữ lại dữ liệu kiểm thử để xem kết quả, không reset dữ liệu người dùng.
