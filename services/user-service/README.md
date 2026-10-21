# User Service

FastAPI + MySQL. Quản lý đăng nhập, JWT và thông tin người nộp tiền; không quản lý số dư.

## Chạy

Dùng [Docker Compose gốc](../../README.md). API http://localhost:8001; Swagger http://localhost:8001/docs.

```powershell
node services/user-service/scripts/init-secrets.mjs
docker compose up -d --build --wait user-service
docker compose exec user-service python seed.py
```

Seed tạo `student/Admin123` và `admin/Admin123` nếu chưa có, không ghi đè dữ liệu. Khóa JWT/nội bộ nằm trong .env gốc, không commit.

## API

- POST `/api/v1/auth/login`: Body x-www-form-urlencoded, username/password; trả access_token.
- GET `/api/v1/users/me`: Bearer JWT, trả hồ sơ hiện tại.
- GET `/api/v1/admin/users`: yêu cầu Admin.
- GET `/internal/users/{id}`: khóa X-Internal-API-Key, chỉ dùng nội bộ.

Đăng nhập giới hạn 10 lần/phút/IP; sai 5 lần khóa tài khoản 15 phút. JWT được kiểm tra chữ ký, hạn dùng và trạng thái tài khoản. Bộ đếm đăng nhập dùng khóa dòng MySQL; chỉ tin IP chuyển tiếp từ Gateway đã cấu hình.

Kiểm thử tích hợp: `node services/user-service/scripts/smoke-full-stack.mjs` tại thư mục gốc.
