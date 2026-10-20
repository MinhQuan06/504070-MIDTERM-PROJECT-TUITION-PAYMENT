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

Payment và Notification đã tích hợp trong Compose. Payment dùng MySQL và cổng 8082; Gateway dùng 8080. Student dùng MySQL/.NET ở cổng 5071; User dùng FastAPI/MySQL ở cổng 8001.

## Quy trình làm việc nhóm

- Nhánh `main` luôn ổn định, không push trực tiếp.
- Mỗi việc làm trên nhánh riêng: `feature/<service>-<mô-tả>`, `docs/<mô-tả>`, `fix/<mô-tả>`.
- Gộp vào `main` bằng Pull Request, có ít nhất 1 bạn trong nhóm review.
- Commit ngắn gọn, bắt đầu bằng động từ: `add login endpoint`, `fix balance check`.
- Không commit file `.env`, mật khẩu hay khóa bí mật.


Payment dùng Transactional Outbox để phục hồi sự kiện email khi broker gián đoạn. Trước lần chạy đầu, tạo khóa bằng `node services/payment-service/scripts/init-outbox-key.mjs`; không commit .env hoặc đổi khóa tùy tiện. Xem [thiết kế và kiểm thử Outbox](docs/report/payment-outbox.md).

## Chạy toàn bộ backend

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
node services/payment-service/scripts/init-outbox-key.mjs
node services/user-service/scripts/init-secrets.mjs
docker compose up -d --build --wait user-service student-service payment-service notification-service gateway
docker compose exec user-service python seed.py
node services/user-service/scripts/smoke-full-stack.mjs
```

User/MySQL dùng cổng 8001/3309, Payment/MySQL 8082/3307, Student/MySQL 5071/3308; Gateway 8080 và Mailpit 8025. Đặt hai cờ PAYMENT_USER_MOCK_ENABLED/PAYMENT_STUDENT_MOCK_ENABLED=false để dùng service thật. File `.env` và khóa tạo ngẫu nhiên không commit; giữ nguyên khóa qua restart. Mật khẩu DB truyền trong DATABASE_URL cần URL-encode nếu có ký tự đặc biệt.

Payment yêu cầu Bearer JWT, xác thực danh tính/role qua User mỗi request. Chỉ chủ sở hữu xem/thao tác giao dịch, Admin được tạo/sửa số dư demo. Student `/pay` yêu cầu khóa nội bộ và xử lý idempotent. Payment giữ PROCESSING khi kết quả Student chưa rõ, tự đối soát sau timeout/restart; verify có thể trả 202 để client poll. CRUD Student ngoài `/pay` vẫn là API quản trị demo chưa có JWT.

Kiểm thử đồng thời bằng `node services/payment-service/scripts/smoke-concurrency.mjs`. Kiểm thử mất phản hồi sau PAID bằng cách đặt PAYMENT_SAGA_TEST=true rồi chạy smoke-full-stack.mjs. Hai bài test tạo dữ liệu riêng, giữ lại để xem; không chạy đồng thời vì bài saga tạm đổi URL Student của container Payment. Xem README Payment để biết chi tiết và cách phục hồi URL nếu cưỡng bức dừng test.

Compose gốc dùng volume MySQL mới, giữ nguyên volume PostgreSQL cũ; không tự chuyển dữ liệu. Ưu tiên Compose gốc, không chạy Compose riêng cùng lúc vì có thể trùng cổng.
