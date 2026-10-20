# Payment Service

Java 21+, Spring Boot, MySQL 8.0 và Flyway. Service quản lý số dư, giao dịch và OTP; phát sự kiện email qua Transactional Outbox đến Notification.

## Chạy cùng project

Tại thư mục gốc repository:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
node services/payment-service/scripts/init-outbox-key.mjs
node services/user-service/scripts/init-secrets.mjs
docker compose up -d --build payment-service notification-service gateway
```

Cấu hình hiện tại: `PAYMENT_USER_MOCK_ENABLED=false`, `PAYMENT_STUDENT_MOCK_ENABLED=false` trong `.env` gốc. Tạo khóa bằng `node services/user-service/scripts/init-secrets.mjs`; chạy `node services/user-service/scripts/smoke-full-stack.mjs` để kiểm tra cả User và Student thật. Script tạo dữ liệu demo riêng và giữ lại để xem. Không chạy smoke-payment-student trực tiếp nếu chưa cung cấp token User/Admin kiểm thử.

Các script smoke cũ với mock không còn là lệnh kiểm thử mặc định. API Payment luôn yêu cầu JWT thật, kể cả khi bật mock dữ liệu liên hệ. Dùng `node services/user-service/scripts/smoke-full-stack.mjs` tại gốc để tạo User/Admin riêng và chạy luồng đầy đủ.

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
| POST | /api/v1/transactions | tuitionId, studentId, amount; payer lấy từ JWT |
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
- USER_MOCK_ENABLED=false, STUDENT_MOCK_ENABLED=false và USER_INTERNAL_API_KEY khớp User để ghép cả hai service thật khi chạy Java trực tiếp.

Trong thư mục service chạy `./mvnw.cmd spring-boot:run`. Gọi trực tiếp http://localhost:8082; Gateway Docker vẫn trỏ container payment-service, không trỏ Java trên Windows.

## JWT và phân quyền

Gửi `Authorization: Bearer <access_token>` lấy từ POST `/api/v1/auth/login`. Payment xác thực bằng GET User `/api/v1/users/me` trên từng request, timeout kết nối 3 giây/đọc 5 giây. Không chia sẻ khóa ký JWT sang Payment và không cache role/trạng thái active. Không dùng mock để bỏ xác thực.

| API | Quyền |
| --- | --- |
| GET /api/v1/accounts/{userId} | Chính chủ |
| POST /api/v1/accounts | Admin; tạo/cập nhật số dư demo |
| POST /api/v1/transactions | Payer lấy từ token; bỏ payerUserId trong body |
| GET /api/v1/transactions/user/{userId} | Chính chủ |
| GET /api/v1/transactions/{id} | Chủ giao dịch |
| POST /api/v1/transactions/{id}/resend-otp | Chủ giao dịch |
| POST /api/v1/transactions/{id}/verify-otp | Chủ giao dịch |
| GET /health | Công khai; không trả dữ liệu tài khoản |

Nếu vẫn truyền payerUserId cũ, giá trị phải trùng token, kể cả Admin. Admin không có quyền đọc/xác nhận giao dịch của người khác. Thiếu/token không hợp lệ/hết hạn/tài khoản inactive trả 401; khác chủ hoặc không đủ role trả 403; User mất kết nối trả 503. Cổng trực tiếp 8082 cũng kiểm tra như Gateway. Các URI cũ được giữ để tương thích client.

```json
{"tuitionId": 1, "studentId": "SV001", "amount": 12000000}
```

`smoke-full-stack.mjs` tạo tài khoản User/Admin riêng với mật khẩu ngẫu nhiên, kiểm tra token hết hạn/sửa chữ ký, truy cập khác chủ (xem, history, resend, verify), sửa payer và quyền seed số dư, rồi thực hiện thanh toán đến biên nhận. Không in token/mật khẩu/khóa vào log.

## Saga và phục hồi sau gián đoạn

Luồng trạng thái: PENDING -> PROCESSING -> SUCCESS hoặc FAILED. Khi OTP hợp lệ, Payment khóa dòng giao dịch và commit chung việc dùng OTP, trừ tiền, chuyển PROCESSING. Không có HTTP nào cho phép gọi bước trừ tiền riêng. Xác nhận lặp không trừ lại; nếu không đủ tiền, transaction rollback cả việc dùng OTP.

Sau đó Payment gọi Student `/api/Tuition/{id}/pay` với khóa nội bộ và snapshot `{transaction_ID, student_ID, amount, expected_Version}`. Student trả 200 khi đã nhận cùng giao dịch với cùng MSSV/số tiền, kể cả phiên bản tăng do lần thanh toán đầu. Giao dịch khác, sai số tiền/MSSV hoặc phiên bản cũ khi còn UNPAID trả 409. Nếu hai request cùng transaction_ID chạy đồng thời, request thua concurrency đọc lại kết quả đã ghi và trả 200.

- Student trả 2xx: Payment commit SUCCESS cùng sự kiện biên nhận vào Outbox.
- Student trả 400/404/409/422: hoàn tiền và FAILED commit cùng transaction cục bộ, chỉ một lần.
- Timeout, lỗi mạng, 5xx, 401/403 hoặc 429: chưa biết kết quả, giữ PROCESSING; không hoàn tiền. Worker lưu lịch retry UTC, backoff 2–60 giây và thử lại cùng transaction_ID. Khi Payment restart, worker đọc lại DB và tiếp tục. Worker và HTTP dùng cùng khóa dòng để tránh xử lý song song; nhiều worker dùng SKIP LOCKED.

API verify trả 202 với status PROCESSING nếu đang đối soát, 200 khi SUCCESS, 409 khi FAILED; client poll GET giao dịch bằng JWT chính chủ. Không tạo giao dịch thay thế khi đang PROCESSING. API tạo mới vẫn trả 201 PENDING. Thông tin email/tên được snapshot khi khởi tạo để phục hồi không phụ thuộc việc gọi lại User.

Flyway V5 bổ sung trạng thái và metadata; không sửa migration cũ. SAGA_RECOVERY_ENABLED=true mặc định, SAGA_POLL_DELAY_MS=1000. Giữ worker bật; khi Student/khóa nội bộ bị lỗi kéo dài, cần theo dõi PROCESSING và khắc phục nguyên nhân, không tự hoàn tiền cho kết quả chưa rõ. Các giao dịch FAILED/SUCCESS từ phiên bản cũ không được tự sửa lại.

```powershell
# Tại gốc; stack đang chạy. Tạo dữ liệu demo riêng và giữ lại để xem.
$env:PAYMENT_SAGA_TEST='true'
node services/user-service/scripts/smoke-full-stack.mjs
Remove-Item Env:PAYMENT_SAGA_TEST
```

Test này mở proxy tạm trên máy, tạm đổi URL Student của container Payment, làm mất phản hồi sau khi Student ghi PAID, restart Payment, rồi kiểm tra phục hồi, idempotency và hoàn tiền khi học phí đổi. URL được phục hồi trong finally. Nếu tiến trình bị cưỡng bức dừng, chạy `docker compose up -d --no-deps --wait payment-service` để phục hồi URL trong `.env`.

SagaIntegrationTest mặc định tắt; chỉ bật SAGA_TEST_DATABASE=true với DB riêng payment_saga_test (test xác nhận tên DB trước khi tạo/xóa fixture). Không chạy trên database nghiệp vụ. Kiểm tra nguyên tử OTP/debit, rollback khi thiếu tiền, phục hồi UNKNOWN và hoàn tiền một lần.

## Kiểm thử đồng thời

```powershell
# Tại gốc repository, stack đang chạy.
node services/payment-service/scripts/smoke-concurrency.mjs
```

Script tạo 6 người dùng và 1 Admin riêng với mật khẩu ngẫu nhiên, không đổi tài khoản hiện có. Mỗi trong 3 vòng gồm:

1. Một tài khoản có 1500, đồng thời xác nhận hai học phí 1000: chỉ một SUCCESS, số dư 500. Giao dịch thiếu tiền giữ PENDING, OTP chưa bị tiêu thụ vì transaction rollback; không tự tạo FAILED hoặc gửi biên nhận.
2. Hai người cùng trả khoản học phí 400: chỉ một SUCCESS, bên còn lại FAILED và được hoàn tiền đúng một lần. Học phí PAID tăng version một lần và gắn đúng transaction_ID.

Đối chiếu cả lịch sử, số dư, trạng thái học phí và 6 email biên nhận của 6 giao dịch thành công trong tổng 12 giao dịch. Gọi lặp đồng thời giao dịch đã thành công cũng không trừ thêm tiền. Dữ liệu demo giữ lại để xem. Script đăng nhập 7 lần; nếu vừa chạy các bài test đăng nhập khác, có thể cần đợi hạn mức 10 lần/phút của User.

Flyway V6 thêm khóa cấp OTP dùng chung trên DB và index tra cứu OTP. Tạo giao dịch/cấp lại OTP dùng READ COMMITTED để tránh gap-lock deadlock khi thêm OTP cho các giao dịch khác nhau. Khóa được giữ đến khi transaction commit, vì vậy nhiều instance cùng kiểm tra mã OTP còn hiệu lực theo một thứ tự. SagaIntegrationTest có test cấp OTP cho 6 giao dịch song song trên DB test riêng.
