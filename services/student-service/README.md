
## Thanh toán nội bộ và idempotency

POST `/api/Tuition/{id}/pay` yêu cầu header `X-Internal-API-Key` khớp `Payment__InternalApiKey` (Compose gốc dùng USER_INTERNAL_API_KEY chung). Thiếu khóa cấu hình trả 503, sai khóa trả 401. Gateway không mở route pay.

Request mới gồm transaction_ID, student_ID, amount và expected_Version (version optional cho giao dịch cũ). MSSV/số tiền phải trùng DB. Khi UNPAID, version phải trùng nếu được truyền. Khi PAID bởi chính transaction_ID đó, trả lại 200 và dữ liệu cũ, không tăng version; giao dịch khác trả 409. Optimistic concurrency ngăn hai giao dịch khác nhau cùng thành công.

Ưu tiên chạy Compose gốc. Khi chạy Compose riêng cần đặt USER_INTERNAL_API_KEY và truyền cùng khóa cho Payment. API CRUD Student/Tuition ngoài pay vẫn là API quản trị demo chưa có JWT; bước này chỉ bảo vệ lệnh gạch nợ nội bộ.
