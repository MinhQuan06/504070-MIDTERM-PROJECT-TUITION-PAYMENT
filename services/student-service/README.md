# Student Service (C# + ASP.NET Core)

Sinh viên, khoản học phí, trạng thái thanh toán. DB: Student DB (bảng `students`, `tuitions`).

API: `GET /api/v1/students/{studentId}/tuition`, `POST /internal/tuitions/{tuitionId}/pay`.

Việc cần làm: khởi tạo project ASP.NET Core, Dockerfile, migration (EF Core), đánh dấu đã thanh toán bằng cập nhật có điều kiện `status = 'UNPAID'`, seed sinh viên và học phí mẫu.
