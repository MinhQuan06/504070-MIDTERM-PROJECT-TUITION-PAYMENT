# 🎓 STUDENT & TUITION MANAGEMENT SERVICE (SOA)

> Dịch vụ quản lý thông tin sinh viên và quy trình tra cứu, thanh toán học phí theo kiến trúc hướng dịch vụ (**Service-Oriented Architecture - SOA**).

![.NET 10](https://img.shields.io/badge/.NET-10.0-512BD4?logo=dotnet)
![C#](https://img.shields.io/badge/C%23-13.0-239120?logo=csharp)
![EF Core](https://img.shields.io/badge/EF%20Core-10.0-512BD4)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI%203.0-85EA2D?logo=swagger&logoColor=black)

---

## 📑 MỤC LỤC
1. [Yêu cầu hệ thống](#1-yêu-cầu-hệ-thống)
2. [Cài đặt cơ sở dữ liệu MySQL](#2-cài-đặt-cơ-sở-dữ-liệu-mysql)
3. [Cấu hình dự án](#3-cấu-hình-dự-án)
4. [Hướng dẫn biên dịch và khởi chạy](#4-hướng-dẫn-biên-dịch-và-khởi-chạy)
5. [Hướng dẫn sử dụng & Kiểm thử API](#5-hướng-dẫn-sử-dụng--kiểm-thử-api)
6. [Danh mục API Endpoints](#6-danh-mục-api-endpoints)
7. [Cấu trúc thư mục dự án](#7-cấu-trúc-thư-mục-dự-án)


---

## 1. YÊU CẦU HỆ THỐNG

Trước khi khởi chạy dự án, hãy đảm bảo máy tính của bạn đã cài đặt:
- **[.NET SDK 10.0](https://dotnet.microsoft.com/download)** (hoặc .NET 8.0/9.0 trở lên).
- **MySQL Server 8.0+** hoặc **XAMPP / Laragon** (cổng mặc định `3306`).
- Công cụ phát triển: **Visual Studio 2022**, **VS Code** (kèm C# Dev Kit), hoặc **JetBrains Rider**.

---

## 2. CÀI ĐẶT CƠ SỞ DỮ LIỆU MYSQL

Mở công cụ quản lý MySQL (như **phpMyAdmin**, **MySQL Workbench**, hoặc **Navicat**) và chạy đoạn mã SQL dưới đây để tạo cơ sở dữ liệu `student_db` cùng bảng dữ liệu mẫu:

```sql
-- 1. Tạo Database
CREATE DATABASE IF NOT EXISTS `student_db` 
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `student_db`;

-- 2. Tạo bảng Sinh viên (students)
CREATE TABLE IF NOT EXISTS `students` (
    `student_id` VARCHAR(10) NOT NULL,
    `full_name` VARCHAR(100) NOT NULL,
    PRIMARY KEY (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Tạo bảng Học phí (tuitions)
CREATE TABLE IF NOT EXISTS `tuitions` (
    `tuition_id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` VARCHAR(10) NOT NULL,
    `semester` VARCHAR(10) NOT NULL,
    `amount` BIGINT NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    `version` INT NOT NULL DEFAULT 0,
    `paid_by_transaction_id` BIGINT NULL,
    PRIMARY KEY (`tuition_id`),
    CONSTRAINT `fk_tuitions_students` 
        FOREIGN KEY (`student_id`) 
        REFERENCES `students` (`student_id`) 
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Chèn dữ liệu mẫu (Seed Data)
INSERT INTO `students` (`student_id`, `full_name`) VALUES
('SV001', 'Nguyen Van A'),
('SV002', 'Tran Thi B'),
('SV003', 'Le Van C');

INSERT INTO `tuitions` (`student_id`, `semester`, `amount`, `status`, `version`, `paid_by_transaction_id`) VALUES
('SV001', 'HK1_2026', 12000000, 'UNPAID', 0, NULL),
('SV002', 'HK1_2026', 10500000, 'PAID', 1, 10001),
('SV003', 'HK1_2026', 15000000, 'UNPAID', 0, NULL);
```

---

## 3. CẤU HÌNH DỰ ÁN

Kiểm tra thông tin tài khoản MySQL của bạn trong file `appsettings.json`:

```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning"
    }
  },
  "AllowedHosts": "*",
  "ConnectionStrings": {
    "student_db": "Server=localhost;Port=3306;Database=student_db;User ID=root;Password=;"
  }
}
```
> **Lưu ý:** Nếu MySQL của bạn có mật khẩu, hãy điền vào sau `Password=...`.

---

## 4. HƯỚNG DẪN BIÊN DỊCH VÀ KHỞI CHẠY

Mở cửa sổ dòng lệnh (Terminal / PowerShell) tại thư mục `d:\SOA\API\Student`:

### Bước 1: Khôi phục thư viện và build dự án
```bash
dotnet restore
dotnet build
```

### Bước 2: Khởi chạy dịch vụ
```bash
dotnet run
```

Sau khi chạy thành công, console sẽ hiển thị:
```text
info: Microsoft.Hosting.Lifetime[14]
      Now listening on: http://localhost:5071
info: Microsoft.Hosting.Lifetime[0]
      Application started. Press Ctrl+C to shut down.
```

---

## 5. HƯỚNG DẪN SỬ DỤNG & KIỂM THỬ API

### Cách 1: Sử dụng Swagger UI trực tiếp trên trình duyệt (Khuyên dùng)
Mở trình duyệt bất kỳ và truy cập vào:
👉 **`http://localhost:5071`** *(Hệ thống tự động chuyển hướng vào Swagger UI)*  
Hoặc: `http://localhost:5071/swagger`

Tại đây, bạn có thể:
1. Nhìn thấy đầy đủ các nhóm API: **Database**, **Student**, **Tuition**.
2. Nhấn vào từng API $\rightarrow$ Chọn **Try it out** $\rightarrow$ Điền dữ liệu $\rightarrow$ Bấm **Execute** để xem phản hồi JSON ngay lập tức.

### Cách 2: Sử dụng file `Student.http` (Trong VS Code hoặc Visual Studio)
Mở file `Student.http` có sẵn trong dự án. Bạn chỉ cần click chuột vào nút **Send Request** nằm ngay phía trên từng API để kiểm thử:
- Kiểm tra kết nối CSDL (`GET /api/database/test`)
- Thêm sinh viên mới, sửa tên sinh viên, xem danh sách.
- Tạo học phí, đóng học phí, demo khóa ngoại chống xóa sinh viên đang nợ tiền.

---

## 6. DANH MỤC API ENDPOINTS

### 🔹 Nhóm API Sinh viên (Student)
| Phương thức | Đường dẫn (Path) | Mô tả chức năng | Mã phản hồi |
|:---:|:---|:---|:---:|
| `GET` | `/api/student` | Lấy danh sách tất cả sinh viên | `200 OK` |
| `GET` | `/api/student/{id}` | Lấy chi tiết sinh viên theo mã | `200`, `404` |
| `POST` | `/api/student` | Thêm mới sinh viên | `201`, `409` |
| `PUT` | `/api/student/{id}` | Cập nhật họ tên sinh viên | `200`, `404` |
| `DELETE`| `/api/student/{id}` | Xóa sinh viên (chặn nếu còn nợ học phí) | `204`, `409` |

### 🔹 Nhóm API Học phí (Tuition)
| Phương thức | Đường dẫn (Path) | Mô tả chức năng | Mã phản hồi |
|:---:|:---|:---|:---:|
| `GET` | `/api/tuition` | Lấy danh sách toàn bộ học phí | `200 OK` |
| `GET` | `/api/tuition/{id}` | Tra cứu chi tiết học phí theo ID | `200`, `404` |
| `GET` | `/api/tuition/student/{studentId}` | Lấy danh sách học phí của 1 sinh viên | `200`, `404` |
| `POST` | `/api/tuition` | Khởi tạo khoản học phí mới | `201`, `400` |
| `PUT` | `/api/tuition/{id}` | Sửa học phí (chỉ khi chưa nộp) | `200`, `409` |
| `POST` | `/api/tuition/{id}/pay` | Xác nhận đóng học phí (Dành cho Payment Service) | `200`, `409` |
| `DELETE`| `/api/tuition/{id}` | Xóa khoản học phí (chỉ khi chưa nộp) | `204`, `409` |

### 🔹 Nhóm API Hệ thống
| Phương thức | Đường dẫn (Path) | Mô tả chức năng | Mã phản hồi |
|:---:|:---|:---|:---:|
| `GET` | `/api/database/test` | Kiểm tra kết nối tới MySQL | `200`, `500` |

---

## 7. CẤU TRÚC THƯ MỤC DỰ ÁN

```text
d:\SOA\API\Student/
├── Controllers/
│   ├── DatabaseController.cs      # API kiểm tra kết nối CSDL
│   ├── StudentController.cs       # Controller quản lý Sinh viên (CRUD)
│   └── TuitionController.cs       # Controller quản lý Học phí & Thanh toán
├── Data/
│   └── StudentDbContext.cs        # Cấu hình Entity Framework Core & MySQL Mapping
├── DTOs/                          # Các lớp hứng dữ liệu đầu vào (Data Transfer Objects)
│   ├── CreateStudentRequest.cs
│   ├── UpdateStudentRequest.cs
│   ├── CreateTuitionRequest.cs
│   ├── UpdateTuitionRequest.cs
│   └── PayTuitionRequest.cs
├── Models/                        # Các Entity phản chiếu bảng trong Database
│   ├── Student.cs
│   └── Tuition.cs
├── Properties/
│   └── launchSettings.json        # Cấu hình cổng chạy (Port 5071 / 7088)
├── appsettings.json               # Cấu hình chuỗi kết nối MySQL
├── Program.cs                     # Điểm khởi chạy ứng dụng & nạp Middleware
├── Student.csproj                 # Khai báo thư viện NuGet (.NET 10)
├── Student.http                   # File kịch bản gọi API mẫu
└── README.md                      # Hướng dẫn sử dụng (File này)
```

---


