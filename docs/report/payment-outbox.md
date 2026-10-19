# Transactional Outbox cho email Payment

## Mục tiêu

Không mất yêu cầu gửi email giữa việc commit nghiệp vụ Payment DB và phát message RabbitMQ. Notification vẫn không có database; bảng outbox_events thuộc MySQL của Payment.

Luồng:

```text
API tạo/cấp lại OTP -> transaction: lưu OTP + Outbox -> commit
API hoàn tất thanh toán -> transaction cục bộ: SUCCESS + Outbox -> commit
Worker -> khóa một row PENDING -> RabbitMQ persistent + mandatory + confirm
       -> ACK và có route: PUBLISHED, xóa ciphertext
       -> lỗi/timeout/nack/no route: giữ PENDING, tăng attempt và backoff
       -> OTP hết hạn: EXPIRED, xóa ciphertext
```

PaymentCompletionService là bean riêng để @Transactional có hiệu lực qua Spring proxy; không dựa vào self-invocation. Nó chỉ bảo đảm SUCCESS và sự kiện biên nhận nguyên tử trong Payment DB, chưa biến debit/Student thành transaction phân tán.

## Dữ liệu và ràng buộc

Migration Flyway V4 thêm outbox_events, khóa chính event_id; transaction_id là FK vào transactions. Các trường chính: event_type, exchange_name, routing_key, payload_ciphertext, status, attempt_count, available_at, expires_at, created_at, published_at, last_error_code.

- PENDING: chờ gửi hoặc retry.
- PUBLISHED: broker đã ACK và không returned; không có nghĩa SMTP đã gửi thành công.
- EXPIRED: OTP quá hạn hoặc được thay bằng OTP mới trước khi xuất broker.
- Tất cả mốc thời gian của bảng outbox là UTC; payload email cũng dùng Instant/ISO 8601 UTC.
- Khi enqueue OTP mới, các OTP Outbox PENDING cũ cùng giao dịch được đánh dấu EXPIRED/OTP_SUPERSEDED trong cùng transaction. Message cũ đã ra broker không được cơ chế này thu hồi; Notification tiếp tục từ chối OTP hết expiresAt.

Không có HTTP API quản trị Outbox công khai. Kiểm tra nội bộ bằng SQL chỉ lấy event_id, transaction_id, event_type, status, attempt_count, available_at, last_error_code; không cần đọc ciphertext để vận hành.

## Bảo vệ OTP

Outbox cần đủ nội dung để gửi email sau restart, nên payload được mã hóa AES-256-GCM với IV ngẫu nhiên; OTP không lưu rõ trong bảng mới. Khóa nằm trong OUTBOX_ENCRYPTION_KEY ở environment, không trong DB hoặc Git. Sau PUBLISHED/EXPIRED, ciphertext được đặt NULL để giảm lưu giữ dữ liệu nhạy cảm. Vẫn cần kiểm soát quyền DB và backup; metadata email nghiệp vụ có trong payload đã mã hóa.

Khởi tạo ở thư mục gốc:

```powershell
node services/payment-service/scripts/init-outbox-key.mjs
```

Script thêm khóa còn thiếu vào .env, không thay khóa có sẵn, không in bí mật. Không copy khóa fixture toàn số 0 từ test sang môi trường thật. Payment từ chối khởi động khi khóa thiếu/sai độ dài. Nếu dùng secret manager, cung cấp Base64 của 32 byte ngẫu nhiên.

Không xoay khóa trực tiếp khi còn PENDING. Cần kế hoạch drain hoặc re-encrypt có phiên bản khóa trước khi thay đổi. Lưu khóa cùng quy trình backup/recovery nhưng tách khỏi bản dump DB. Mất khóa làm các sự kiện chưa gửi không thể phục hồi nội dung.

## Publisher và retry

- Spring RabbitMQ bật publisher-confirm-type=correlated và publisher-returns=true; template mandatory=true.
- Correlation ID mới cho mỗi lần publish; messageId và eventId giữ nguyên cho cùng sự kiện.
- ACK nhưng message bị return vì không có binding vẫn là lỗi UNROUTABLE, giữ PENDING.
- Timeout không khẳng định broker chưa nhận: retry cùng eventId, consumer dedupe.
- Mỗi row được xử lý trong transaction với SELECT ... FOR UPDATE SKIP LOCKED. Instance khác bỏ qua row đang bị khóa; không giữ trạng thái claim trong RAM.
- Giữ lock qua lúc chờ broker confirm; timeout mặc định 5 giây, connection timeout 3 giây. Đây là đánh đổi đơn giản cho quy mô bài tập; cần giám sát DB pool khi tăng tải.
- Một batch tối đa 10 row, poll mặc định 1 giây. Retry backoff tối đa 60 giây. Receipt retry không giới hạn; cần cảnh báo PENDING tồn lâu thay vì bỏ email âm thầm.
- Crash trước commit PUBLISHED làm row vẫn PENDING; lần sau có thể publish lại, phù hợp at-least-once.
- Callback return không log body vì có thể chứa OTP.

Các mã lỗi chỉ lưu code, không lưu exception body: UNROUTABLE, BROKER_NACK, BROKER_UNAVAILABLE_OR_TIMEOUT, PUBLISH_INTERRUPTED, OUTBOX_DELIVERY_ERROR.

## Cấu hình

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| OUTBOX_ENCRYPTION_KEY | Bắt buộc | Base64 32 byte |
| OUTBOX_ENABLED | true | Bật scheduler; false giữ sự kiện chờ trong DB |
| OUTBOX_POLL_DELAY_MS | 1000 | Khoảng nghỉ giữa batch |
| OUTBOX_CONFIRM_TIMEOUT_MS | 5000 | Chờ xác nhận broker |

Không xóa migration cũ, không reset volume hiện có để nâng cấp; Flyway tự thêm V4. Chưa có tác vụ tự xóa metadata Outbox cũ; thống nhất chính sách lưu giữ trước khi chạy lâu dài.

## Kiểm thử

### Unit và contract

Trong services/payment-service:

```powershell
./mvnw.cmd '-Dtest=NotificationContractTest,OutboxSenderTest' test
```

Bao gồm serialization Java/TypeScript, hạn OTP, mã hóa ngẫu nhiên/xác thực khóa, message persistent/stable ID, ACK/NACK/timeout và mandatory return.

### Transaction trên MySQL thật

OutboxIntegrationTest chỉ bật khi OUTBOX_TEST_DATABASE=true và kiểm tra SELECT DATABASE() đúng payment_outbox_test trước khi xóa fixture. Không chạy bằng tài khoản/database nghiệp vụ. Tắt Payment container của môi trường test trước khi chạy để scheduler không xử lý fixture song song.

Dùng MySQL 8 riêng trên cổng thử nghiệm (ví dụ 13307), tạo database payment_outbox_test và tài khoản chỉ dành cho test; đặt SPRING_DATASOURCE_URL/USERNAME/PASSWORD tương ứng; bật OUTBOX_TEST_DATABASE=true rồi chạy mvnw test. Khóa toàn số 0 trong annotation chỉ dùng test. Sáu ca kiểm tra: rollback nghiệp vụ/Outbox cùng nhau; enqueue bắt buộc transaction; retry cùng eventId và scrub sau confirm; expired/superseded; SKIP LOCKED giữa hai transaction; SUCCESS và receipt commit/rollback cùng nhau.

### Kiểm chứng tích hợp

Đã chạy 15/15 test Payment (3 contract, 5 sender/cipher, 6 transaction MySQL và 1 contextLoads), cùng các ca broker ngừng hoạt động, Payment restart, route sai và OTP hết hạn. Đã xác nhận OTP/receipt được gửi tiếp với cùng eventId sau phục hồi; API SUCCESS và receipt Outbox commit cùng nhau. Smoke test tái hiện luồng thông thường nằm trong services/payment-service/scripts/smoke-payment-notification.mjs, chạy theo README gốc.

## Giới hạn còn lại

Outbox giải quyết khoảng mất sự kiện DB -> broker. Không bảo đảm SMTP exactly-once khi Notification chưa ghi dấu sau khi SMTP nhận thư. JWT/quyền sở hữu, OTP uniqueness đồng thời và Saga debit/Student timeout/phục hồi vẫn cần thay đổi riêng.

## Tài liệu nền tảng

- Spring CorrelationData: https://docs.spring.io/spring-amqp/docs/current/api/org/springframework/amqp/rabbit/connection/CorrelationData.html
- MySQL locking reads: https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html
