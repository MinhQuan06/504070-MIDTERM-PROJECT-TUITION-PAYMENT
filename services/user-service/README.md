# User/Auth Service (Python + FastAPI)

Đăng nhập, cấp JWT, thông tin người dùng. DB: User DB (bảng `users`).

API: `POST /api/v1/auth/login`, `GET /api/v1/users/me`, `GET /internal/users/{userId}`.

Việc cần làm: khởi tạo project FastAPI, Dockerfile, migration (Alembic), seed người dùng mẫu (không có chức năng đăng ký).
