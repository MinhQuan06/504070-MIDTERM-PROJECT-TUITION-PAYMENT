# Student Service

.NET 10 + MySQL. Quản lý sinh viên, khoản học phí và trạng thái đã thanh toán.

## Chạy

Dùng [Docker Compose gốc](../../README.md). API http://localhost:5071; Swagger http://localhost:5071/swagger. Dữ liệu ban đầu gồm MSSV SV001–SV005.

## API và quyền

- GET `/api/Student`, `/api/Student/{id}`: tra cứu sinh viên.
- GET `/api/Tuition`, `/api/Tuition/{id}`, `/api/Tuition/student/{studentId}`: tra cứu học phí.
- POST/PUT/DELETE Student/Tuition: Bearer JWT có role Admin, kiểm tra qua User.
- POST `/api/Tuition/{id}/pay`: chỉ Payment gọi với X-Internal-API-Key.

Pay nhận transaction_ID, student_ID, amount và expected_Version. Chỉ gạch nợ một lần bằng optimistic concurrency. Replay cùng transaction ID trả kết quả cũ, không tăng version; transaction khác trả 409 nếu học phí đã PAID.

Compose tự cấu hình UserService__Url và khóa Payment__InternalApiKey. Chạy local ngoài Docker dùng User tại http://localhost:8001. Không đưa khóa vào Git. Gateway chỉ mở các route Student tra cứu.
