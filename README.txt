================================================================================
          STUDENT & TUITION MANAGEMENT SERVICE (SOA) - HƯỚNG DẪN SỬ DỤNG
================================================================================

Dịch vụ quản lý thông tin sinh viên và quy trình tra cứu, đóng học phí theo
kiến trúc hướng dịch vụ (Service-Oriented Architecture - SOA).
Phiên bản: .NET 10 (C# 13) | MySQL 8.0+ | Entity Framework Core 10 | Docker


--------------------------------------------------------------------------------
1. YÊU CẦU HỆ THỐNG
--------------------------------------------------------------------------------
Bạn có thể chọn 1 trong 2 cách chạy:
- Nếu chạy bằng Docker (Khuyên dùng): Chỉ cần cài ứng dụng Docker Desktop.
- Nếu chạy bằng .NET trực tiếp:
  + .NET SDK 10.0 (hoặc .NET 8.0/9.0 trở lên).
  + MySQL Server 8.0+ hoặc XAMPP / Laragon (cổng mặc định: 3306).


--------------------------------------------------------------------------------
2. KHỞI TẠO CƠ SỞ DỮ LIỆU MYSQL
--------------------------------------------------------------------------------
Toàn bộ các câu lệnh tạo Database, bảng `students`, `tuitions` và dữ liệu mẫu
đã được viết sẵn trong thư mục `db/`:

    File: db/student_db.sql

Cách khởi tạo:
- Nếu dùng Docker: Hệ thống TỰ ĐỘNG nạp file này vào, bạn KHÔNG cần làm gì cả!
- Nếu dùng MySQL ngoài máy thật:
  + Cách 1: Mở phpMyAdmin (hoặc MySQL Workbench), Import file `db/student_db.sql`.
  + Cách 2: Chạy bằng dòng lệnh MySQL:
      mysql -u root -p < db/student_db.sql


--------------------------------------------------------------------------------
3. CẤU HÌNH KẾT NỐI DATABASE (KHI CHẠY NGOÀI MÁY THẬT)
--------------------------------------------------------------------------------
Thông tin kết nối được cấu hình trong file `appsettings.json`:

    "ConnectionStrings": {
      "student_db": "Server=localhost;Port=3306;Database=student_db;User ID=root;Password=;"
    }

Giải thích thông số:
- Server=localhost    : Database chạy trên chính máy đang chạy API.
- Port=3306           : Cổng kết nối MySQL mặc định.
- Database=student_db : Tên cơ sở dữ liệu.
- User ID=root        : Tài khoản kết nối.
- Password=           : Mật khẩu (đang để trống, nếu có mật khẩu hãy điền vào).


--------------------------------------------------------------------------------
4. HƯỚNG DẪN KHỞI CHẠY HỆ THỐNG
--------------------------------------------------------------------------------

>>> [CÁCH 1: CHẠY BẰNG DOCKER COMPOSE (KHUYÊN DÙNG NHẤT - 1 LỆNH ĂN NGAY)] <<<
Không cần cài .NET 10, không cần cài MySQL trên máy thật.
Đảm bảo máy đã bật Docker Desktop. Mở Terminal / CMD tại thư mục `Student`:

    Bước 1: Khởi động hệ thống (Docker tự nạp MySQL và build API)
        docker compose up -d --build

    Bước 2: Mở trình duyệt và truy cập:
        http://localhost:5071
        (Hệ thống tự động chuyển hướng vào giao diện Swagger UI để test)

    Bước 3: Khi muốn dừng toàn bộ dịch vụ:
        docker compose down


>>> [CÁCH 2: CHẠY BẰNG .NET SDK TRUYỀN THỐNG (CHẠY TRÊN MÁY THẬT)] <<<
Yêu cầu máy thật đã bật sẵn MySQL ở cổng 3306.
Mở Terminal / PowerShell tại thư mục `Student`:

    Bước 1: Khôi phục thư viện và build
        dotnet restore
        dotnet build

    Bước 2: Chạy dịch vụ
        dotnet run

    Sau khi chạy thành công:
        Now listening on: http://localhost:5071


--------------------------------------------------------------------------------
5. HƯỚNG DẪN KIỂM THỬ API
--------------------------------------------------------------------------------

[CÁCH 1: Dùng Swagger UI trên trình duyệt]
Mở trình duyệt và truy cập:
    http://localhost:5071
Tại đây bạn có thể:
- Xem danh sách đầy đủ các Endpoint API.
- Chọn từng API -> Bấm "Try it out" -> Nhập thông tin -> Bấm "Execute" để test.

[CÁCH 2: Dùng file Student.http]
Mở file `Student.http` có sẵn trong dự án bằng VS Code hoặc Visual Studio.
Bấm chuột vào dòng chữ "Send Request" phía trên từng API để chạy trực tiếp 
14 kịch bản kiểm thử đã được soạn sẵn.


--------------------------------------------------------------------------------
6. DANH MỤC API ENDPOINTS
--------------------------------------------------------------------------------

[Nhóm API Sinh viên - Student]
- GET    /api/student           : Lấy danh sách tất cả sinh viên (200 OK)
- GET    /api/student/{id}      : Lấy chi tiết sinh viên theo mã (200 OK, 404 Not Found)
- POST   /api/student           : Thêm sinh viên mới (201 Created, 409 Conflict)
- PUT    /api/student/{id}      : Cập nhật họ tên sinh viên (200 OK, 404 Not Found)
- DELETE /api/student/{id}      : Xóa sinh viên (204 NoContent, 409 Conflict nếu đang nợ học phí)

[Nhóm API Học phí - Tuition]
- GET    /api/tuition                   : Lấy danh sách tất cả học phí (200 OK)
- GET    /api/tuition/{id}              : Tra cứu chi tiết học phí theo ID (200 OK, 404 Not Found)
- GET    /api/tuition/student/{studentId}: Lấy học phí theo mã sinh viên (200 OK, 404 Not Found)
- POST   /api/tuition                   : Khởi tạo khoản học phí mới (201 Created, 400 Bad Request)
- PUT    /api/tuition/{id}              : Sửa học phí khi chưa nộp (200 OK, 409 Conflict)
- POST   /api/tuition/{id}/pay          : Xác nhận đóng học phí (200 OK, 409 Conflict)
- DELETE /api/tuition/{id}              : Xóa khoản học phí (204 NoContent, 409 Conflict)

[Nhóm API Kiểm tra Kết nối CSDL]
- GET    /api/database/test     : Kiểm tra trạng thái kết nối MySQL (200 OK, 500 Error)


--------------------------------------------------------------------------------
7. CẤU TRÚC THƯ MỤC DỰ ÁN
--------------------------------------------------------------------------------
Student/
|-- Controllers/
|   |-- DatabaseController.cs      # API kiểm tra kết nối CSDL
|   |-- StudentController.cs       # Controller quản lý Sinh viên
|   `-- TuitionController.cs       # Controller quản lý Học phí & Thanh toán
|-- Data/
|   `-- StudentDbContext.cs        # DbContext và Entity Mapping cho MySQL
|-- DTOs/                          # Các lớp hứng dữ liệu Request
|   |-- CreateStudentRequest.cs
|   |-- UpdateStudentRequest.cs
|   |-- CreateTuitionRequest.cs
|   |-- UpdateTuitionRequest.cs
|   `-- PayTuitionRequest.cs
|-- Models/                        # Các Entity CSDL
|   |-- Student.cs
|   `-- Tuition.cs
|-- db/
|   `-- student_db.sql             # Script SQL khởi tạo CSDL và dữ liệu mẫu
|-- Properties/
|   `-- launchSettings.json        # Cấu hình cổng chạy
|-- Dockerfile                     # Chỉ dẫn đóng gói Docker cho Student API
|-- docker-compose.yml             # Tự động chạy cả Student API lẫn MySQL
|-- .dockerignore                  # Bỏ qua file rác khi build Docker
|-- appsettings.json               # Chuỗi kết nối CSDL MySQL
|-- Program.cs                     # File khởi chạy chính của ứng dụng
|-- Student.csproj                 # Khai báo gói thư viện NuGet
|-- Student.http                   # 14 kịch bản test API mẫu
`-- README.txt                     # File hướng dẫn sử dụng này
================================================================================
