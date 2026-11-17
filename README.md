# Phân hệ Đóng học phí (iBanking) - CS504070

Dự án giữa kỳ môn 504070 (HK1/2026-2027), Đại học Tôn Đức Thắng.
Người dùng dùng số dư tài khoản để thanh toán toàn bộ học phí của một sinh viên TDTU (cho mình hoặc cho sinh viên khác), xác thực bằng OTP qua email.

## Thành viên

1.  Đỗ Minh Quân - 52400030
2. Nguyễn Bảo Khang - 52400015
3. Thạch Hoàng Quân - 52400032
4. Tôn Thanh Thiên - 52400163

## Kiến trúc

| Thành phần | Công nghệ | Thư mục | Trách nhiệm |
| --- | --- | --- | --- |
| API Gateway | Nginx | `gateway/` | Điểm vào duy nhất, routing,... |
| User/Auth Service | Python + FastAPI | `services/user-service/` | Đăng nhập, JWT, thông tin người dùng |
| Payment Service | Java + Spring Boot | `services/payment-service/` | Số dư, giao dịch, OTP, lịch sử, điều phối  |
| Student Service | C# + ASP.NET Core | `services/student-service/` | Sinh viên, học phí, đánh dấu đã thanh toán |
| Notification Service | TypeScript + RabbitMQ | `services/notification-service/` | Gửi email OTP và email xác nhận (nhận sự kiện từ RabbitMQ) |
| Web App | React + Vite | `frontend/` | Giao diện người dùng |


## Cấu trúc thư mục

```
tuition-payment/
├── README.md
├── docker-compose.yml        # hạ tầng: 3 database, RabbitMQ, Mailpit, Gateway
├── .env.example              # mẫu biến môi trường (copy thành .env)
├── gateway/                  # cấu hình Nginx
├── services/
│   ├── user-service/         # Python + FastAPI
│   ├── payment-service/      # Java + Spring Boot
│   ├── student-service/      # C# + ASP.NET Core
│   └── notification-service/ # TypeScript + RabbitMQ
├── frontend/                 # Web App
```

## Yêu cầu

- Docker Desktop (hoặc Docker Engine) có Docker Compose v2; Docker phải đang chạy.
- Node.js 22 trở lên (chỉ dùng để tạo các khóa bí mật ban đầu).

## Chạy toàn bộ hệ thống bằng Docker

Mở PowerShell tại thư mục gốc dự án. Tạo file cấu hình local và sinh các khóa bí mật bắt buộc:

```powershell
Copy-Item .env.example .env
node services/user-service/scripts/init-secrets.mjs
node services/payment-service/scripts/init-outbox-key.mjs
```

Build và khởi động các database, RabbitMQ, Mailpit, các service, frontend và gateway:

```powershell
docker compose up --build -d
```

Đợi các container khởi động xong, sau đó truy cập:

- Web app: http://localhost:8080
- Hộp thư thử nghiệm OTP: http://localhost:8025
- RabbitMQ Management: http://localhost:15672 (tài khoản/mật khẩu mặc định trong `.env.example`: `notification` / `notification_dev`)

Compose nạp schema và dữ liệu sinh viên/học phí mẫu khi tạo volume database lần đầu. Tạo hai tài khoản đăng nhập mẫu bằng lệnh:

```powershell
docker compose exec user-service python seed.py
```

Đăng nhập bằng `student` / `Admin123` hoặc `admin` / `Admin123`. Để tài khoản `student` có thể thanh toán, cần đăng nhập admin rồi tạo số dư ban đầu cho user ID `1` qua API quản trị tài khoản. Ví dụ PowerShell:

```powershell
$adminToken = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login -ContentType 'application/x-www-form-urlencoded' -Body 'username=admin&password=Admin123').access_token
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/accounts -Headers @{ Authorization = "Bearer $adminToken" } -ContentType 'application/json' -Body '{"userId":1,"balance":50000000}'
```

OTP gửi qua email được xem trong Mailpit. Tài khoản và mật khẩu mẫu chỉ dành cho môi trường demo local; không dùng khi triển khai thật.

Xem trạng thái và log:

```powershell
docker compose ps
docker compose logs -f
```

Dừng các container nhưng giữ dữ liệu database:

```powershell
docker compose down
```

Chạy lại hệ thống bằng `docker compose up -d`. Muốn xóa cả dữ liệu demo trong volume thì dùng `docker compose down -v`.

## Chạy frontend riêng để phát triển

Khi các backend và gateway đang chạy bằng Docker, có thể mở frontend với Vite:

```powershell
cd frontend
npm ci
npm run dev
```

Mở URL Vite in trong terminal (mặc định http://localhost:5173). Proxy `/api` của Vite chuyển request tới gateway ở `http://127.0.0.1:8080`.
