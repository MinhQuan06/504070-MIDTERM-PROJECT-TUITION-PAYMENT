================================================================================
                 USER/AUTH SERVICE - HƯỚNG DẪN CÀI ĐẶT VÀ SỬ DỤNG
================================================================================
Dịch vụ xác thực người dùng và cung cấp thông tin tài khoản cho hệ thống
đóng học phí iBanking theo kiến trúc microservices.

Công nghệ: Python | FastAPI | SQLAlchemy | MySQL 8 | JWT | Docker Compose

Dự án chỉ hướng dẫn chạy bằng Docker Compose. Không cần cài Python hoặc MySQL
riêng trên máy host.

--------------------------------------------------------------------------------
1. CHỨC NĂNG CHÍNH
--------------------------------------------------------------------------------
- Đăng nhập bằng username và password.
- Băm mật khẩu bằng bcrypt.
- Phát hành và xác thực JWT access token.
- Lấy thông tin người dùng hiện tại.
- Phân quyền theo role: student và admin.
- Cung cấp API nội bộ để service khác lấy thông tin liên hệ người dùng.
- Giới hạn số request đăng nhập và tạm khóa tài khoản sau nhiều lần nhập sai.

--------------------------------------------------------------------------------
2. YÊU CẦU HỆ THỐNG
--------------------------------------------------------------------------------
- Docker Desktop đã được cài đặt và đang chạy.
- Docker Compose v2 (lệnh `docker compose`).
- Git (nếu clone dự án từ GitHub).

Không cần cài Python hoặc MySQL riêng trên Windows.
Các lệnh bên dưới sử dụng Windows PowerShell.

--------------------------------------------------------------------------------
3. CẤU HÌNH FILE .env
--------------------------------------------------------------------------------
Bước 1: Mở terminal tại thư mục chứa `docker-compose.yml`.

Bước 2: Tạo file .env từ file mẫu:

    Copy-Item .env.example .env

Bước 3: Tạo hai secret khác nhau bằng PowerShell. Chạy đoạn lệnh:

    $bytes1 = New-Object byte[] 32
    $bytes2 = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes1)
    $rng.GetBytes($bytes2)
    "JWT_SECRET_KEY=" + [System.BitConverter]::ToString($bytes1).Replace("-", "").ToLower()
    "INTERNAL_API_KEY=" + [System.BitConverter]::ToString($bytes2).Replace("-", "").ToLower()

Bước 4: Mở file .env và thay JWT_SECRET_KEY, INTERNAL_API_KEY bằng hai giá trị
vừa tạo. Thay MYSQL_ROOT_PASSWORD bằng một mật khẩu dành cho MySQL trong Docker.
Nên dùng mật khẩu gồm chữ và số để tránh vấn đề mã hóa ký tự trong connection URL.
Không giữ nguyên các giá trị CHANGE_ME trong file mẫu.

Các biến cấu hình:
- JWT_SECRET_KEY: secret dùng để ký JWT, tối thiểu 32 ký tự.
- INTERNAL_API_KEY: key xác thực request tới API nội bộ, tối thiểu 32 ký tự.
- MYSQL_ROOT_PASSWORD: mật khẩu root của MySQL container.
- MYSQL_PORT: cổng MySQL mở ra trên máy host, mặc định 3307.
- AUTH_PORT: cổng User/Auth API trên máy host, mặc định 8001.
- JWT_EXPIRE_MINUTES: thời gian JWT có hiệu lực, mặc định 30 phút.

Không commit file .env hoặc secret thật lên GitHub. Chỉ commit .env.example.

--------------------------------------------------------------------------------
4. KHỞI CHẠY HỆ THỐNG BẰNG DOCKER COMPOSE
--------------------------------------------------------------------------------
Bước 1: Đảm bảo Docker Desktop đang chạy.

Bước 2: Tại thư mục chứa docker-compose.yml, khởi động API và MySQL:

    docker compose up -d --build

Docker Compose sẽ build User/Auth API, khởi chạy MySQL 8 và chờ database sẵn sàng.
File `db/user_db.sql` tạo database và bảng `users` khi MySQL volume được tạo
lần đầu.

Bước 3: Tạo tài khoản demo:

    docker compose exec user-auth-service python seed.py

Tài khoản demo:
- Student: username `demo`, password `Demo123!`
- Admin: username `admin`, password `AdminDemo123!`

Nếu hiện `Created demo account`, tài khoản mới đã được tạo. Nếu hiện
`Skipped existing account`, tài khoản đã tồn tại; script không ghi đè mật khẩu.

Bước 4: Mở tài liệu API:

    http://localhost:8001/docs

Health check:

    http://localhost:8001/health

Các lệnh Docker thường dùng:

    docker compose ps
    docker compose logs -f
    docker compose logs -f user-auth-service
    docker compose down

`docker compose down` dừng và xóa container nhưng giữ dữ liệu trong volume.

CẢNH BÁO: Lệnh dưới đây xóa cả volume MySQL và toàn bộ dữ liệu trong database:

    docker compose down -v

Chỉ dùng lệnh này khi muốn xóa dữ liệu và khởi tạo database từ đầu. File
`db/user_db.sql` chỉ được MySQL image tự nạp khi volume được tạo lần đầu.
Nếu volume đã tồn tại, sửa file SQL rồi khởi động lại Compose sẽ không tự chạy
lại script khởi tạo.

Nếu cổng 8001 hoặc 3307 đang được sử dụng, thay AUTH_PORT hoặc MYSQL_PORT trong
file .env rồi chạy lại:

    docker compose up -d

--------------------------------------------------------------------------------
5. DANH SÁCH API ENDPOINTS
--------------------------------------------------------------------------------
Authentication:
- POST /api/v1/auth/login
  Nhận username và password dưới dạng application/x-www-form-urlencoded; trả về
  JWT access token khi thông tin đăng nhập hợp lệ.

User:
- GET /api/v1/users/me
  Yêu cầu JWT hợp lệ. Trả về hồ sơ tài khoản hiện đang đăng nhập.

Internal API:
- GET /internal/users/{user_id}
  Yêu cầu header X-Internal-API-Key hợp lệ. Dành cho các service nội bộ, ví dụ
  Payment Service, lấy họ tên, số điện thoại và email của người dùng.

Administration:
- GET /api/v1/admin/users
  Yêu cầu JWT của tài khoản có role `admin`. Hỗ trợ limit (mặc định 50, tối đa
  100) và offset (mặc định 0).

Health:
- GET /health
  Kiểm tra service có phản hồi. Endpoint này không kiểm tra sâu kết nối MySQL.

Đề án hiện không có API đăng ký tài khoản công khai. Tài khoản demo được tạo
bằng script seed.py.

--------------------------------------------------------------------------------
6. KIỂM THỬ API BẰNG SWAGGER
--------------------------------------------------------------------------------
Mở Swagger UI tại:

    http://localhost:8001/docs

Đăng nhập:
1. Mở POST /api/v1/auth/login.
2. Nhấn Try it out.
3. Nhập username và password vào form.
4. Nhấn Execute. Đăng nhập thành công trả về 200 và access_token.

Kiểm tra hồ sơ:
1. Nhấn Authorize trong Swagger và xác thực bằng tài khoản cần kiểm thử.
2. Gọi GET /api/v1/users/me.

Kiểm tra phân quyền admin:
- Token admin gọi GET /api/v1/admin/users: dự kiến 200.
- Token student gọi GET /api/v1/admin/users: dự kiến 403.
- Không có token hợp lệ: dự kiến 401.

Kiểm tra API nội bộ:
- Gọi GET /internal/users/{user_id} với user_id tồn tại.
- Gửi header X-Internal-API-Key có giá trị bằng INTERNAL_API_KEY trong .env.
- API key thiếu hoặc sai trả về 401.

Mục `422 Validation Error` trong phần Responses của Swagger chỉ mô tả một phản
hồi có thể xảy ra. Xem mã HTTP trong `Server response` sau khi nhấn Execute để
biết request thực tế có thành công hay không.

--------------------------------------------------------------------------------
7. CƠ CHẾ BẢO MẬT
--------------------------------------------------------------------------------
- Mật khẩu được băm bằng bcrypt.
- JWT sử dụng HS256; server kiểm tra chữ ký, thời hạn, issuer, audience và claim.
- Các API được bảo vệ kiểm tra trạng thái tài khoản trong MySQL.
- Endpoint quản trị yêu cầu role=admin.
- Endpoint nội bộ yêu cầu X-Internal-API-Key.
- Giới hạn tối đa 10 request đăng nhập mỗi phút theo địa chỉ IP.
- Sau 5 lần nhập sai liên tiếp, tài khoản bị khóa tạm thời trong 15 phút.
- Không công khai endpoint /internal/* qua API Gateway.

--------------------------------------------------------------------------------
8. MÃ TRẠNG THÁI HTTP
--------------------------------------------------------------------------------
200 OK                  Request thành công.
401 Unauthorized        Sai thông tin đăng nhập, JWT không hợp lệ/hết hạn hoặc
                        Internal API Key sai/thiếu.
403 Forbidden           Đã xác thực nhưng không đủ quyền, ví dụ student gọi API admin.
404 Not Found           Không tìm thấy user hoặc user không hoạt động.
422 Validation Error    Thiếu trường hoặc dữ liệu đầu vào sai định dạng.
429 Too Many Requests   Đã vượt giới hạn request đăng nhập.

--------------------------------------------------------------------------------
9. XỬ LÝ LỖI THƯỜNG GẶP
--------------------------------------------------------------------------------
[Docker không khởi động]
- Kiểm tra Docker Desktop đang chạy.
- Chạy `docker compose ps` và `docker compose logs -f` để xem lỗi.
- Kiểm tra JWT_SECRET_KEY và INTERNAL_API_KEY trong .env đã được thay bằng
  secret thật, không còn giá trị CHANGE_ME.

[Không kết nối được MySQL]
- Kiểm tra `docker compose ps` để xác nhận container mysql đang chạy.
- Xem log bằng `docker compose logs -f mysql`.
- API trong container phải kết nối tới hostname `mysql`, không phải localhost.
- Nếu volume đã tồn tại, SQL init sẽ không tự chạy lại. Chỉ dùng
  `docker compose down -v` nếu chấp nhận xóa dữ liệu để tạo database mới.

[Đăng nhập trả về 401]
- Kiểm tra username và password demo.
- Chạy `docker compose exec user-auth-service python seed.py`.
- Nếu tài khoản đã tồn tại, seed.py không đặt lại mật khẩu.
- Kiểm tra tài khoản có đang bị khóa tạm thời hay không.

[API admin trả về 403]
- Chỉ token của tài khoản có role=admin mới được truy cập endpoint này.

[API nội bộ trả về 401]
- Kiểm tra header X-Internal-API-Key khớp với INTERNAL_API_KEY mà container đang dùng.
- Nếu vừa sửa .env, chạy lại `docker compose up -d --force-recreate user-auth-service`.

[API trả về 422]
- Xem `Server response` và trường `detail` trong Response body sau khi Execute.

--------------------------------------------------------------------------------
10. CẤU TRÚC THƯ MỤC DỰ ÁN
--------------------------------------------------------------------------------
UserAuthService/
|-- app/
|   |-- core/                 # JWT, API key, phân quyền và rate limiting
|   |-- models/               # SQLAlchemy model bảng users
|   |-- schemas/              # Schema response của API
|   |-- config.py             # Cấu hình từ environment
|   |-- database.py           # Kết nối MySQL và session
|   `-- main.py               # Khai báo các API
|-- db/
|   `-- user_db.sql           # Tạo database và bảng users
|-- tests/                    # Unit test bảo mật
|-- Dockerfile                # Đóng gói FastAPI
|-- docker-compose.yml        # Chạy API cùng MySQL
|-- .dockerignore             # Loại file không cần khi build image
|-- .env.example              # Cấu hình mẫu cho Docker
|-- requirements.txt          # Thư viện Python được cài trong container
|-- seed.py                   # Tạo tài khoản demo
|-- UserAuthService.http      # Request mẫu
`-- README.md                 # Tài liệu hướng dẫn

--------------------------------------------------------------------------------
11. LƯU Ý
--------------------------------------------------------------------------------
- Thay secret mẫu trước khi chạy; không commit .env lên GitHub.
- Không dùng tài khoản demo trong môi trường production.
- Sử dụng HTTPS và quản lý secret đúng cách khi triển khai thực tế.
- Rate limit theo IP hiện dùng bộ nhớ trong process; nhiều replica cần cơ chế
  giới hạn dùng chung.
- Service hiện chưa có refresh token, logout/thu hồi JWT, MFA hoặc audit log.
================================================================================
