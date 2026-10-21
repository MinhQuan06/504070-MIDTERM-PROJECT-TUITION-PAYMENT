# Kiểm thử transaction và concurrency — 21/10/2026

## Phạm vi

Kiểm thử bản code hiện tại trong workspace, không chỉ dựa trên kết quả cũ. Bao gồm MySQL/InnoDB thật cho tính nguyên tử và khóa, API thật giữa Gateway/User/Payment/Student/Notification, và proxy thử nghiệm làm mất phản hồi của Student. Không có thanh toán tiền thật.

Không reset database hoặc số dư demo. Trước khi chạy đã lưu baseline của 10 tài khoản, 14 giao dịch và 19 khoản học phí. Sau bài test API, đối chiếu xác nhận toàn bộ các bản ghi cũ này giữ nguyên. Các bản ghi mới đều là dữ liệu QA riêng.

## Kết quả API thật

| Ca kiểm thử | Điều kiện và kết quả đã quan sát | Kết quả |
| --- | --- | --- |
| Hai giao dịch cùng tài khoản | Số dư 1.500; hai khoản 1.000 xác nhận song song. Một SUCCESS, một PENDING; số dư còn 500; chỉ khoản thành công PAID. Lặp 3 vòng. | PASS |
| Hai tài khoản cùng khoản học phí | Cùng xác nhận khoản 400. Một SUCCESS, một FAILED và được hoàn tiền; học phí PAID, version=1, mã giao dịch khớp bên thắng. Lặp 3 vòng. | PASS |
| Xác nhận lại giao dịch thành công | Gửi hai request verify đồng thời cho bên thắng; số dư không giảm thêm. | PASS |
| Lịch sử và biên nhận | 12 giao dịch concurrency; 6 thành công và đúng 6 biên nhận quan sát được; trạng thái lịch sử khớp API chi tiết. | PASS |
| Student đã PAID nhưng mất phản hồi | Proxy chuyển pay đến Student rồi ngắt phản hồi thành công. Payment trả 202/PROCESSING, giữ debit 1.000, không hoàn tiền nhầm. | PASS |
| Restart Payment khi đang PROCESSING | Khởi động lại Payment, cho proxy hoạt động bình thường. Cùng giao dịch tự về SUCCESS; số dư vẫn 49.999.000 từ ban đầu 50.000.000; Student version vẫn 1. | PASS |
| Replay Student | Hai replay cùng transaction ID được chấp nhận, version không tăng thêm. Transaction khác, sai amount hoặc sai khóa bị từ chối. | PASS |
| Học phí thay đổi trước verify | Đổi amount sau khi cấp OTP. Verify FAILED, học phí vẫn UNPAID, tiền hoàn đúng một lần; verify lại không hoàn thêm. | PASS |
| Biên nhận khi phục hồi | Giao dịch phục hồi #27 có một biên nhận quan sát được; giao dịch thất bại #28 không tạo biên nhận thành công. | PASS |

Sau các bài API có thêm 7 tài khoản số dư, 14 giao dịch và 11 khoản học phí QA. Giữ lại để xem kết quả. Các User/Admin QA dùng mật khẩu ngẫu nhiên, không ghi mật khẩu/token/OTP vào báo cáo.

## Kiểm thử MySQL riêng

MySQL tạm chạy ở 127.0.0.1:13317, tách khỏi database dự án. Test Saga dùng payment_saga_test; Outbox dùng payment_outbox_test. Test bật scheduler recovery/Outbox theo cấu hình test, không để scheduler nền can thiệp kết quả.

SagaIntegrationTest kiểm tra:

1. Cấp OTP cho nhiều giao dịch đồng thời không deadlock và không trùng mã.
2. OTP consume, debit và PROCESSING nguyên tử; xác nhận lại không debit thêm.
3. Thiếu số dư rollback consume OTP, giữ giao dịch PENDING.
4. Kết quả chưa rõ được lưu bền vững, phục hồi không hoàn nhầm hoặc debit lần hai.
5. Từ chối nghiệp vụ hoàn tiền đúng một lần.
6. Thử OTP sai được lưu, khóa sau 5 lượt, không debit.
7. Cooldown và hạn mức resend được lưu trong DB.
8. **Bổ sung trong lượt này:** hai request cùng xác nhận một giao dịch còn PENDING, cùng xuất phát qua barrier hai thread; chỉ một OTP được consume, chỉ trừ một lần.
9. **Bổ sung trong lượt này:** hai giao dịch PENDING tranh cùng số dư 1.500; chỉ một giao dịch được debit 1.000, giao dịch còn lại giữ OTP chưa dùng và PENDING, còn 500.

Kết quả: **9/9 PASS**, 0 lỗi, 0 bỏ qua. StudentServiceClient được mock trong nhóm test này; không dùng nhóm test này thay cho bằng chứng cạnh tranh qua Student thật ở bảng trên.

OutboxIntegrationTest kiểm tra rollback cùng nghiệp vụ, bắt buộc transaction hiện hữu, retry giữ eventId/ciphertext, loại OTP hết hạn/bị thay thế, SKIP LOCKED khi publisher khác giữ khóa, và SUCCESS/biên nhận commit hoặc rollback cùng nhau.

Kết quả Outbox: **6/6 PASS**, 0 lỗi, 0 bỏ qua. Tổng hai nhóm MySQL: **15/15 PASS**. Container MySQL tạm đã được dọn sau khi hoàn tất; các volume database dự án được giữ nguyên.

## Cách chạy lại bài API

Docker Desktop và stack phải đang chạy. Từ thư mục gốc:

```powershell
node services/payment-service/scripts/smoke-concurrency.mjs
```

Bài phục hồi cố tình đổi URL Student của Payment sang proxy tạm và restart container Payment; chạy riêng sau bài concurrency:

```powershell
$env:PAYMENT_SAGA_TEST = "true"
try {
    node services/user-service/scripts/smoke-full-stack.mjs
} finally {
    Remove-Item Env:PAYMENT_SAGA_TEST -ErrorAction SilentlyContinue
}
```

Không chạy hai script trên cùng lúc. User giới hạn đăng nhập theo IP; nếu chạy lặp quá nhanh gặp 429, chờ hết cửa sổ một phút rồi chạy lại. Script tạo dữ liệu QA mới, không reset dữ liệu cũ. Cuối bài phục hồi, script khôi phục cấu hình Payment trong finally; đã kiểm tra URL cuối là http://student-service:5071 và Payment healthy.

## Giới hạn kết luận

Đây là các ca được kiểm thử có kiểm soát, không phải stress test tải lớn hoặc chứng minh hệ thống không còn lỗi. Việc quan sát đúng số biên nhận trong bài test không tạo bảo đảm exactly-once cho SMTP khi crash đúng thời điểm sau khi SMTP đã nhận email. Không sửa code nghiệp vụ, không commit/push trong lượt kiểm thử; chỉ bổ sung hai test concurrency nêu trên và báo cáo.
