# Phân hệ Đóng học phí (iBanking) - CS504070

Dự án giữa kỳ môn 504070 (HK1/2026-2027), Đại học Tôn Đức Thắng.
Người dùng dùng số dư tài khoản để thanh toán toàn bộ học phí của một sinh viên TDTU (cho mình hoặc cho sinh viên khác), xác thực bằng OTP qua email.

## Thành viên

| Họ tên | MSSV | Phụ trách |
| --- | --- | --- |
| (điền) | (điền) | (điền) |

## Kiến trúc

| Thành phần | Công nghệ | Thư mục | Trách nhiệm |
| --- | --- | --- | --- |
| API Gateway | Nginx | `gateway/` | Điểm vào duy nhất, routing,... |
| User/Auth Service | Python + FastAPI | `services/user-service/` | Đăng nhập, JWT, thông tin người dùng |
| Payment Service | Java + Spring Boot | `services/payment-service/` | Số dư, giao dịch, OTP, lịch sử, điều phối  |
| Student Service | C# + ASP.NET Core | `services/student-service/` | Sinh viên, học phí, đánh dấu đã thanh toán |
| Notification Service | TypeScript + NestJS | `services/notification-service/` | Gửi email OTP và email xác nhận (nhận sự kiện từ RabbitMQ) |
| Web App | ... | `frontend/` | Giao diện người dùng |

Mỗi service có database  riêng (User DB, Payment DB, Student DB). Chi tiết ở `docs/`.

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
│   └── notification-service/ # TypeScript + NestJS
├── frontend/                 # Web App
├── db/                       # script khởi tạo và dữ liệu mẫu (seed) cho từng DB
└── docs/
    ├── diagrams/             # Use Case, DFD, ERD, Microservices
    ├── api/                  # thiết kế REST API
    └── report/               # báo cáo
```

## Chạy hạ tầng (giai đoạn đầu)

```bash
cp .env.example .env
docker compose up -d
```

- RabbitMQ management: http://localhost:15672
- Mailpit (xem email OTP khi dev): http://localhost:8025
- Gateway: http://localhost:8080

Payment và Notification đã tích hợp trong Compose. Payment dùng MySQL và cổng 8082; Gateway dùng 8080. User/Student hiện chưa có triển khai.

## Quy trình làm việc nhóm

- Nhánh `main` luôn ổn định, không push trực tiếp.
- Mỗi việc làm trên nhánh riêng: `feature/<service>-<mô-tả>`, `docs/<mô-tả>`, `fix/<mô-tả>`.
- Gộp vào `main` bằng Pull Request, có ít nhất 1 bạn trong nhóm review.
- Commit ngắn gọn, bắt đầu bằng động từ: `add login endpoint`, `fix balance check`.
- Không commit file `.env`, mật khẩu hay khóa bí mật.


Payment dùng Transactional Outbox để phục hồi sự kiện email khi broker gián đoạn. Trước lần chạy đầu, tạo khóa bằng `node services/payment-service/scripts/init-outbox-key.mjs`; không commit .env hoặc đổi khóa tùy tiện. Xem [thiết kế và kiểm thử Outbox](docs/report/payment-outbox.md).
Smoke test tạo tài khoản demo mới, đọc OTP từ Mailpit và kiểm tra biên nhận, số dư, resend và hoàn tiền mock. Xem README của từng service để biết giới hạn hiện tại.
