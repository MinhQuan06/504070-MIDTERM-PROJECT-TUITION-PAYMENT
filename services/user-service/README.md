# Tích hợp với project chính

Ưu tiên Docker Compose ở thư mục gốc. User dùng API 8001 và MySQL riêng 3309, tên container service là user-service.

```powershell
node services/payment-service/scripts/init-outbox-key.mjs
node services/user-service/scripts/init-secrets.mjs
docker compose up -d --build --wait user-service student-service payment-service notification-service gateway
docker compose exec user-service python seed.py
node services/user-service/scripts/smoke-full-stack.mjs
```

Khóa JWT_SECRET_KEY và USER_INTERNAL_API_KEY nằm trong `.env` gốc được Git bỏ qua. Script chỉ tạo khóa khi thiếu, không thay khóa đã có hoặc in giá trị. Compose truyền USER_INTERNAL_API_KEY cho cả User và Student; Payment gửi header X-Internal-API-Key khi gọi nội bộ. Gateway không mở /internal/. Không dùng JWT_SECRET/JWT_EXPIRES_MINUTES cũ; tên biến là JWT_SECRET_KEY/JWT_EXPIRE_MINUTES.

Payment xác thực Bearer JWT qua User /api/v1/users/me trên mỗi request để kiểm tra chữ ký, hạn dùng, trạng thái active và role hiện tại. Chỉ chủ sở hữu được đọc/thao tác tài khoản và giao dịch; Admin được seed số dư. Mất kết nối User làm Payment trả 503. Mock dữ liệu liên hệ không bỏ qua xác thực.

Smoke test tạo User/Admin riêng bằng mật khẩu ngẫu nhiên, không đặt lại số dư hoặc mật khẩu của người dùng hiện có. Kiểm tra quyền truy cập và thanh toán đến email. Hướng dẫn phía dưới dành cho chạy riêng service; Compose riêng mặc định MySQL 3307 trùng Payment, cần đổi nếu chạy cùng project. SQL user_db.sql chạy trong database đã chọn; khi import thủ công phải chọn DB trước.

---

================================================================================
                 USER/AUTH SERVICE - HƯỚNG DẪN CÀI ĐẶT VÀ SỬ DỤNG
================================================================================
Dịch vụ xác thực người dùng và quản lý thông tin tài khoản cho hệ thống
đóng học phí iBanking theo kiến trúc microservices.

Công nghệ: Python | FastAPI | SQLAlchemy | MySQL 8 | JWT | Docker Compose

Có thể chạy dự án theo một trong hai cách:
- Cách 1: Docker Compose tự khởi chạy MySQL và User/Auth API.
- Cách 2: Chạy FastAPI trực tiếp trên máy, kết nối tới MySQL có sẵn.

--------------------------------------------------------------------------------
1. CHỨC NĂNG CHÍNH
--------------------------------------------------------------------------------
- Đăng nhập bằng username và password.
- Băm mật khẩu bằng bcrypt.
- Phát hành và xác thực access token JWT.
- Lấy thông tin người dùng hiện tại.
- Phân quyền theo role: student và admin.
- Cung cấp API nội bộ để service khác lấy thông tin người dùng.
- Giới hạn request đăng nhập và tạm khóa tài khoản sau nhiều lần nhập sai.

--------------------------------------------------------------------------------
2. YÊU CẦU HỆ THỐNG
--------------------------------------------------------------------------------
Cách 1 - Docker Compose:
- Docker Desktop đã được cài đặt và đang chạy.
- Không cần cài Python hoặc MySQL riêng để chạy service trong container.

Cách 2 - Chạy trực tiếp:
- Python 3.11 trở lên.
- MySQL 8.0+ hoặc MySQL của XAMPP / Laragon.
- phpMyAdmin, MySQL Workbench hoặc công cụ quản lý MySQL tương đương.

Các lệnh ví dụ sử dụng Windows PowerShell.

--------------------------------------------------------------------------------
3. CẤU HÌNH FILE .env
--------------------------------------------------------------------------------
Tại thư mục gốc dự án, tạo file .env từ file mẫu:

    Copy-Item .env.example .env

Tạo hai secret khác nhau bằng cách chạy lệnh sau hai lần trong PowerShell:

    python -c "import secrets; print(secrets.token_hex(32))"

Dán một giá trị vào JWT_SECRET_KEY và giá trị còn lại vào INTERNAL_API_KEY
trong file .env. Không giữ nguyên giá trị CHANGE_ME trong file mẫu.

Các biến thường dùng:
- JWT_SECRET_KEY: secret dùng ký JWT, tối thiểu 32 ký tự.
- INTERNAL_API_KEY: key bảo vệ endpoint nội bộ, tối thiểu 32 ký tự.
- MYSQL_ROOT_PASSWORD: mật khẩu root của MySQL do Docker Compose khởi chạy.
- MYSQL_PORT: cổng MySQL được mở ra trên máy host, mặc định 3307.
- AUTH_PORT: cổng API trên máy host, mặc định 8001.
- DATABASE_URL: chuỗi kết nối MySQL khi chạy FastAPI trực tiếp trên máy.

Lưu ý: Khi dùng Docker Compose, cấu hình DATABASE_URL trong container sẽ tự
được đặt để kết nối tới hostname mysql bên trong mạng Docker; dòng DATABASE_URL
trỏ tới localhost trong .env.example dành cho cách chạy trực tiếp.

Không commit file .env lên GitHub. Chỉ commit file .env.example không chứa secret
thật.

--------------------------------------------------------------------------------
4. CÁCH 1 - CHẠY BẰNG DOCKER COMPOSE (KHUYÊN DÙNG ĐỂ THỬ NGHIỆM)
--------------------------------------------------------------------------------
Docker Compose sẽ build User/Auth API, khởi động MySQL 8 và tạo bảng từ file
 db/user_db.sql khi volume database được khởi tạo lần đầu.

Bước 1: Đảm bảo Docker Desktop đang chạy. Mở terminal tại thư mục chứa
        docker-compose.yml.

Bước 2: Khởi động MySQL và User/Auth Service:

    docker compose up -d --build

Lệnh này yêu cầu JWT_SECRET_KEY và INTERNAL_API_KEY đã được thay bằng secret
thật trong file .env.

Bước 3: Tạo tài khoản demo bên trong container API:

    docker compose exec user-auth-service python seed.py

Nếu terminal báo "Created demo account", tài khoản demo đã được tạo. Nếu báo
"Skipped existing account", tài khoản đã tồn tại và mật khẩu không bị ghi đè.

Bước 4: Mở Swagger UI:

    http://localhost:8001/docs

Kiểm tra service:

    http://localhost:8001/health

Một số lệnh quản lý:

    docker compose ps                 # Xem trạng thái container
    docker compose logs -f             # Xem log các service
    docker compose logs -f user-auth-service
    docker compose down                # Dừng và xóa container, giữ dữ liệu MySQL

Dữ liệu MySQL được lưu trong named volume user_auth_mysql_data. Nếu muốn xóa
hoàn toàn database Docker và dữ liệu trong đó, dùng lệnh sau:

    docker compose down -v

CẢNH BÁO: `docker compose down -v` sẽ xóa volume database của dự án. Chỉ dùng
khi chấp nhận mất dữ liệu và muốn khởi tạo database từ đầu.

File db/user_db.sql chỉ được MySQL image nạp tự động khi volume dữ liệu được tạo
lần đầu. Nếu volume đã tồn tại, sửa file SQL rồi chạy lại compose sẽ không tự
chạy lại script này.

Nếu cổng 8001 hoặc 3307 đang được sử dụng, đổi AUTH_PORT hoặc MYSQL_PORT trong
.env rồi chạy lại `docker compose up -d`.

--------------------------------------------------------------------------------
5. CÁCH 2 - CHẠY FASTAPI TRỰC TIẾP TRÊN MÁY
--------------------------------------------------------------------------------
Cách này dùng MySQL đã cài trên Windows, XAMPP hoặc Laragon.

Bước 1: Khởi động MySQL và import file db/user_db.sql bằng phpMyAdmin:
- Mở http://localhost/phpmyadmin.
- Chọn tab Import.
- Chọn file db/user_db.sql rồi nhấn Import.

Có thể import bằng MySQL CLI nếu đã cấu hình lệnh mysql:

    mysql -u root -p < db/user_db.sql

Bước 2: Trong file .env, đặt DATABASE_URL trỏ tới MySQL trên máy. Ví dụ cho
        MySQL chạy cổng 3306 với user root không có mật khẩu:

    DATABASE_URL=mysql+pymysql://root:@localhost:3306/user_db?charset=utf8mb4

Nếu MySQL có mật khẩu hoặc dùng cổng khác, cập nhật chuỗi kết nối tương ứng.
Không dùng MYSQL_ROOT_PASSWORD của Docker làm mật khẩu MySQL cài sẵn trừ khi
bạn đã đặt hai mật khẩu đó giống nhau.

Bước 3: Tạo môi trường ảo và cài thư viện:

    python -m venv .venv
    .venv\Scripts\Activate.ps1
    pip install -r requirements.txt

Nếu PowerShell chặn việc chạy script, có thể dùng Command Prompt:

    .venv\Scripts\activate.bat

Bước 4: Tạo tài khoản demo:

    python seed.py

Bước 5: Chạy API:

    uvicorn app.main:app --reload --port 8001

Mở Swagger UI tại http://127.0.0.1:8001/docs và health check tại
http://127.0.0.1:8001/health.

--------------------------------------------------------------------------------
6. TÀI KHOẢN DEMO
--------------------------------------------------------------------------------
Tài khoản sinh viên:
    Username: student
    Password: Admin123

Tài khoản quản trị viên:
    Username: admin
    Password: Admin123

Chạy `python seed.py` để tạo demo khi chạy trực tiếp, hoặc
`docker compose exec user-auth-service python seed.py` khi chạy bằng Docker.
Script chỉ tạo tài khoản nếu username chưa tồn tại; không đặt lại mật khẩu của
tài khoản đã tồn tại.

Đây là tài khoản dùng cho môi trường phát triển, không dùng cho production.

--------------------------------------------------------------------------------
7. DANH SÁCH API ENDPOINTS
--------------------------------------------------------------------------------
Authentication:
- POST /api/v1/auth/login
  Đăng nhập bằng username và password gửi dưới dạng form fields. Trả về JWT,
  token_type và expires_in.

User profile:
- GET /api/v1/users/me
  Yêu cầu access token JWT hợp lệ. Trả về thông tin tài khoản hiện tại.

Admin:
- GET /api/v1/admin/users
  Chỉ role=admin được truy cập. Hỗ trợ limit (mặc định 50, tối đa 100) và
offset (mặc định 0).

Internal API:
- GET /internal/users/{user_id}
  Yêu cầu header X-Internal-API-Key đúng. Dùng để service nội bộ, ví dụ Payment
  Service, lấy thông tin người dùng được phép truy cập.

Health:
- GET /health
  Kiểm tra nhanh service có phản hồi.

--------------------------------------------------------------------------------
8. KIỂM THỬ API BẰNG SWAGGER
--------------------------------------------------------------------------------
Mở Swagger UI:
- Khi chạy Docker: http://localhost:8001/docs
- Khi chạy trực tiếp: http://127.0.0.1:8001/docs

Đăng nhập:
1. Mở POST /api/v1/auth/login.
2. Nhấn Try it out.
3. Nhập username và password vào các trường form.
4. Nhấn Execute và kiểm tra Server response. Khi thành công phải là 200 và
   response trả về access_token.

Kiểm tra API hồ sơ:
1. Dùng Authorize trong Swagger theo hướng dẫn trên giao diện.
2. Gọi GET /api/v1/users/me bằng access token hợp lệ.

Kiểm tra phân quyền:
- Gọi GET /api/v1/admin/users với token admin: dự kiến 200.
- Gọi endpoint này với token student: dự kiến 403.
- Gọi endpoint cần xác thực mà không có token hợp lệ: dự kiến 401.

Kiểm tra API nội bộ:
- Mở GET /internal/users/{user_id}.
- Nhập user_id tồn tại.
- Gửi header X-Internal-API-Key đúng với INTERNAL_API_KEY trong .env.

Mục 422 Validation Error trong phần Responses của Swagger chỉ mô tả một phản
hồi có thể xảy ra. Để biết request có thực sự lỗi hay không, hãy đọc mã HTTP
trong mục Server response sau khi nhấn Execute.

--------------------------------------------------------------------------------
9. CƠ CHẾ BẢO MẬT
--------------------------------------------------------------------------------
- Mật khẩu được băm bằng bcrypt.
- JWT dùng HS256 và được kiểm tra chữ ký, hạn dùng, issuer, audience và claim.
- API yêu cầu xác thực sẽ kiểm tra trạng thái tài khoản trong MySQL.
- Endpoint quản trị yêu cầu role=admin.
- Endpoint nội bộ yêu cầu X-Internal-API-Key.
- Giới hạn tối đa 10 request đăng nhập mỗi phút theo địa chỉ IP.
- Sau 5 lần nhập sai liên tiếp, tài khoản bị khóa trong 15 phút.

--------------------------------------------------------------------------------
10. MÃ TRẠNG THÁI HTTP 
--------------------------------------------------------------------------------
200 OK                  Request thành công.
401 Unauthorized        Đăng nhập sai, JWT không hợp lệ/hết hạn hoặc API key sai.
403 Forbidden           Đã xác thực nhưng không đủ quyền, ví dụ student gọi API admin.
404 Not Found           Không tìm thấy người dùng được yêu cầu.
422 Validation Error    Thiếu trường hoặc dữ liệu request sai định dạng.
429 Too Many Requests   Đã vượt giới hạn request đăng nhập.

--------------------------------------------------------------------------------
11. XỬ LÝ LỖI THƯỜNG GẶP
--------------------------------------------------------------------------------
[Docker không khởi động]
- Kiểm tra Docker Desktop đang chạy.
- Chạy `docker compose ps` và `docker compose logs -f` để xem lỗi.
- Kiểm tra JWT_SECRET_KEY và INTERNAL_API_KEY trong .env không còn giá trị mẫu.

[Không kết nối được MySQL]
- Khi chạy Docker, service API phải dùng hostname `mysql` trong DATABASE_URL;
  không dùng localhost bên trong container để kết nối MySQL container khác.
- Khi chạy trực tiếp, kiểm tra host, port, username, password và database URL.
- Khi Docker volume đã cũ, SQL init sẽ không tự chạy lại. Chỉ dùng
  `docker compose down -v` nếu chấp nhận xóa dữ liệu rồi khởi tạo lại từ đầu.

[Đăng nhập trả về 401]
- Kiểm tra username và password.
- Chạy seed.py bằng cách tương ứng với phương thức đang dùng để tạo tài khoản demo.
- Nếu tài khoản đã tồn tại, seed.py không ghi đè mật khẩu.
- Kiểm tra tài khoản có đang bị khóa tạm thời hay không.

[API admin trả về 403]
- Chỉ token thuộc tài khoản có role=admin mới được truy cập.

[API nội bộ trả về 401]
- Kiểm tra header X-Internal-API-Key khớp với INTERNAL_API_KEY đang được service dùng.

[API trả về 422]
- Đọc Server response và trường detail trong Response body sau khi Execute.

--------------------------------------------------------------------------------
12. CẤU TRÚC THƯ MỤC DỰ ÁN
--------------------------------------------------------------------------------
UserAuthService_security_update/
|-- app/
|   |-- api/                 # Authentication, user profile, admin
|   |-- core/                # JWT, RBAC, API key và rate limiting
|   |-- models/              # SQLAlchemy models
|   |-- schemas/              # Request/response schemas
|   |-- config.py             # Cấu hình đọc từ biến môi trường hoặc .env
|   |-- database.py           # Kết nối MySQL và session
|   `-- main.py               # Khởi tạo FastAPI
|-- db/
|   `-- user_db.sql           # Tạo database, bảng users
|-- tests/                    # Unit tests bảo mật
|-- Dockerfile                # Đóng gói FastAPI service
|-- docker-compose.yml        # Chạy API cùng MySQL
|-- .dockerignore             # Loại file không cần khi build image
|-- .env.example              # Cấu hình mẫu
|-- requirements.txt          # Python dependencies
|-- seed.py                   # Tạo tài khoản demo
|-- UserAuthService.http      # Request mẫu
`-- README.md                  # Tài liệu hướng dẫn

--------------------------------------------------------------------------------
13. LƯU Ý TRIỂN KHAI
--------------------------------------------------------------------------------
- Thay các secret mẫu trước khi chạy.
- Không commit .env hoặc thông tin đăng nhập thực lên GitHub.
- Không công khai endpoint /internal/* qua API Gateway.
- Không dùng tài khoản demo trong production.
- Khi triển khai thực tế, cần cấu hình TLS, giới hạn mạng, quản lý secret và
  giám sát log phù hợp.
- Rate limit theo IP hiện dùng bộ nhớ trong process; nhiều worker hoặc nhiều
  replica cần cơ chế giới hạn dùng chung.
- Service hiện chưa có refresh token, thu hồi JWT khi logout, MFA hoặc audit log.

