================================================================================
                 USER/AUTH SERVICE - HƯỚNG DẪN CÀI ĐẶT VÀ SỬ DỤNG
================================================================================
Dịch vụ xác thực người dùng và quản lý thông tin tài khoản cho hệ thống
đóng học phí iBanking theo kiến trúc microservices.

Công nghệ: Python | FastAPI | SQLAlchemy | MySQL | JWT
--------------------------------------------------------------------------------
1. GIỚI THIỆU
--------------------------------------------------------------------------------
User/Auth Service chịu trách nhiệm xác thực tài khoản, phát hành access token
JWT và cung cấp thông tin người dùng cho các service khác trong hệ thống.

Các chức năng chính:
- Đăng nhập bằng username và password.
- Mã hóa mật khẩu bằng bcrypt trước khi lưu vào database.
- Phát hành và xác thực access token JWT.
- Lấy thông tin người dùng đang đăng nhập.
- Phân quyền theo role: student và admin.
- Cung cấp API nội bộ để service khác lấy thông tin người dùng.
- Giới hạn số lần gọi API đăng nhập và tạm khóa tài khoản sau nhiều lần sai.

--------------------------------------------------------------------------------
2. YÊU CẦU HỆ THỐNG
--------------------------------------------------------------------------------
Trước khi chạy dự án, hãy cài đặt:
- Python 3.11 trở lên.
- MySQL 8.0+ hoặc MySQL đi kèm XAMPP / Laragon.
- phpMyAdmin, MySQL Workbench hoặc công cụ tương đương để quản lý database.
- VS Code, PyCharm hoặc IDE Python tương đương.

Các ví dụ lệnh trong tài liệu này sử dụng Windows PowerShell.

--------------------------------------------------------------------------------
3. TẠO CƠ SỞ DỮ LIỆU MYSQL
--------------------------------------------------------------------------------
File SQL khởi tạo database nằm tại:
    db/user_db.sql

Cách thực hiện bằng phpMyAdmin:
1. Khởi động MySQL trong XAMPP / Laragon nếu đang sử dụng.
2. Mở http://localhost/phpmyadmin.
3. Chọn tab Import.
4. Chọn file db/user_db.sql trong thư mục dự án.
5. Nhấn Import để tạo database user_db và bảng users.

File SQL đã tạo database và bảng với các cột cần cho chức năng hiện tại, gồm:
- user_id: mã người dùng.
- username: tên đăng nhập, không được trùng.
- password_hash: mật khẩu đã được băm.
- full_name: họ tên người dùng.
- phone: số điện thoại.
- email: địa chỉ email.
- role: vai trò của người dùng (student hoặc admin).
- is_active: trạng thái hoạt động của tài khoản.
- failed_login_attempts: số lần nhập sai mật khẩu liên tiếp.
- locked_until: thời điểm kết thúc khóa đăng nhập tạm thời.
- created_at: thời điểm tạo tài khoản.

User/Auth Service quản lý database user_db riêng. Các service khác không nên
truy cập trực tiếp bảng users; hãy gọi API phù hợp của service này.

--------------------------------------------------------------------------------
4. CẤU HÌNH MÔI TRƯỜNG
--------------------------------------------------------------------------------
Tại thư mục gốc của dự án, tạo file .env từ file mẫu .env.example:

    Copy-Item .env.example .env

Mở file .env và kiểm tra cấu hình:

    DATABASE_URL=mysql+pymysql://root:@localhost:3306/user_db?charset=utf8mb4
    JWT_SECRET_KEY=THAY_BANG_SECRET_NGAU_NHIEN
    JWT_ISSUER=ibanking-user-auth
    JWT_AUDIENCE=ibanking-api
    JWT_EXPIRE_MINUTES=30
    INTERNAL_API_KEY=THAY_BANG_SECRET_NOI_BO_KHAC
    AUTO_CREATE_TABLES=false

Cần lưu ý:
- DATABASE_URL phải trỏ đến đúng database MySQL vừa tạo.
- Dòng mẫu giả định MySQL dùng user root, không có mật khẩu và chạy cổng 3306.
  Nếu cấu hình máy bạn khác, hãy sửa username, password, host hoặc port tương ứng.
- JWT_SECRET_KEY và INTERNAL_API_KEY phải là hai chuỗi ngẫu nhiên khác nhau,
  mỗi chuỗi dài ít nhất 32 ký tự. Không giữ nguyên giá trị mẫu.
- JWT_EXPIRE_MINUTES là thời gian hiệu lực của access token, mặc định 30 phút.
- AUTO_CREATE_TABLES=false vì database được tạo từ file db/user_db.sql.
- Không chia sẻ file .env và không commit file này lên GitHub.

Có thể tạo hai secret ngẫu nhiên bằng PowerShell:

    python -c "import secrets; print(secrets.token_hex(32))"

Chạy lệnh hai lần. Dán một giá trị vào JWT_SECRET_KEY và giá trị còn lại vào
INTERNAL_API_KEY.

--------------------------------------------------------------------------------
5. CÀI ĐẶT VÀ CHẠY SERVICE
--------------------------------------------------------------------------------
Mở PowerShell tại thư mục gốc của dự án và thực hiện theo thứ tự.

Bước 1: Tạo môi trường ảo Python:

    python -m venv .venv

Bước 2: Kích hoạt môi trường ảo:

    .venv\Scripts\Activate.ps1

Nếu PowerShell không cho phép chạy script, có thể mở Command Prompt và chạy:

    .venv\Scripts\activate.bat

Bước 3: Cài đặt thư viện:

    pip install -r requirements.txt

Bước 4: Đảm bảo MySQL đang chạy và database user_db đã được tạo bằng file
        db/user_db.sql như hướng dẫn ở mục 3.

Bước 5: Tạo tài khoản demo:

    python seed.py

Bước 6: Khởi chạy API:

    uvicorn app.main:app --reload --port 8001

Khi service chạy thành công, mở Swagger UI tại:
    http://127.0.0.1:8001/docs

Endpoint kiểm tra trạng thái service:
    http://127.0.0.1:8001/health

--------------------------------------------------------------------------------
6. TÀI KHOẢN DEMO
--------------------------------------------------------------------------------
Script seed.py tạo hai tài khoản dùng cho kiểm thử local nếu username chưa tồn tại:

    Tài khoản sinh viên
    Username: demo
    Password: Demo123!

    Tài khoản quản trị viên
    Username: admin
    Password: AdminDemo123!

Nếu terminal báo "Skipped existing account", tài khoản đó đã có trong database
và seed.py sẽ không ghi đè mật khẩu hoặc thông tin hiện có.

Đây là tài khoản demo, không sử dụng cho môi trường production.

--------------------------------------------------------------------------------
7. DANH SÁCH API ENDPOINTS
--------------------------------------------------------------------------------
[1] Đăng nhập - Authentication
    POST /api/v1/auth/login
    Nhận username và password dưới dạng form fields. Nếu thông tin hợp lệ,
    API trả về access_token dạng JWT, token_type và expires_in.

[2] Thông tin người dùng hiện tại - User Profile
    GET /api/v1/users/me
    Yêu cầu access token JWT hợp lệ. Trả về user_id, username, full_name,
    phone, email và role của tài khoản đang đăng nhập.

[3] Danh sách người dùng - Admin
    GET /api/v1/admin/users
    Chỉ tài khoản có role=admin mới được truy cập. Có hỗ trợ query parameters
    limit (mặc định 50, tối đa 100) và offset (mặc định 0).

[4] Thông tin người dùng cho service nội bộ
    GET /internal/users/{user_id}
    Yêu cầu header X-Internal-API-Key hợp lệ. Trả về user_id, full_name và email.
    Endpoint này dành cho service nội bộ như Payment Service.

[5] Kiểm tra trạng thái service
    GET /health
    Trả về trạng thái đơn giản để kiểm tra service có phản hồi hay không.

--------------------------------------------------------------------------------
8. CÁCH KIỂM THỬ API BẰNG SWAGGER
--------------------------------------------------------------------------------
Mở địa chỉ:
    http://127.0.0.1:8001/docs

Đăng nhập:
1. Mở POST /api/v1/auth/login.
2. Nhấn Try it out.
3. Nhập username và password vào các trường form.
4. Nhấn Execute và kiểm tra response. Khi thành công, response có access_token.

Kiểm tra API yêu cầu đăng nhập:
1. Nhấn Authorize trong Swagger.
2. Làm theo hộp thoại xác thực được hiển thị. Với OAuth2 password form, nhập
   username và password để Swagger lấy token; nếu được yêu cầu nhập Bearer token,
   hãy dùng access_token vừa nhận được.
3. Gọi GET /api/v1/users/me.
4. Dùng tài khoản demo để kiểm tra role student và tài khoản admin để kiểm tra
   GET /api/v1/admin/users.

Kiểm tra API nội bộ:
1. Mở GET /internal/users/{user_id}.
2. Nhập user_id đang tồn tại và đang hoạt động.
3. Gửi header X-Internal-API-Key với đúng giá trị INTERNAL_API_KEY trong file .env.

Lưu ý: Dòng 422 Validation Error có thể xuất hiện trong phần Responses của Swagger
dưới dạng tài liệu mô tả. Điều đó tự nó không có nghĩa request đã bị lỗi 422.
Hãy kiểm tra Server response sau khi nhấn Execute để biết mã HTTP thực tế.

--------------------------------------------------------------------------------
9. CƠ CHẾ BẢO MẬT
--------------------------------------------------------------------------------
- Mật khẩu được băm bằng bcrypt; database không lưu mật khẩu dạng văn bản gốc.
- JWT dùng thuật toán HS256 và được kiểm tra chữ ký, thời hạn, issuer, audience
  cùng các claim bắt buộc.
- Access token mặc định có hiệu lực 30 phút.
- API yêu cầu xác thực sẽ kiểm tra tài khoản và trạng thái hoạt động trong MySQL.
- Endpoint quản trị yêu cầu role=admin.
- API nội bộ yêu cầu header X-Internal-API-Key.
- Tối đa 10 request đăng nhập mỗi phút theo địa chỉ IP.
- Sau 5 lần nhập sai mật khẩu liên tiếp, tài khoản bị khóa trong 15 phút.
- Khi đăng nhập thành công, bộ đếm lần nhập sai và trạng thái khóa được xóa.

--------------------------------------------------------------------------------
10. MÃ TRẠNG THÁI HTTP THƯỜNG GẶP
--------------------------------------------------------------------------------
200 OK
    Request thành công.

401 Unauthorized
    Thông tin đăng nhập không hợp lệ; JWT thiếu, sai hoặc hết hạn; hoặc internal
    API key không đúng.

403 Forbidden
    Đã xác thực nhưng role không có quyền truy cập, ví dụ tài khoản student gọi
    GET /api/v1/admin/users.

404 Not Found
    Không tìm thấy người dùng được yêu cầu qua API nội bộ hoặc tài khoản không
    hoạt động.

422 Unprocessable Entity
    Dữ liệu request thiếu hoặc sai định dạng. Đọc Response body để xác định trường
    gây lỗi; mục 422 trong phần Responses của Swagger chỉ là mô tả có thể xảy ra.

429 Too Many Requests
    Đã vượt giới hạn request đăng nhập theo IP.

--------------------------------------------------------------------------------
11. XỬ LÝ MỘT SỐ LỖI THƯỜNG GẶP
--------------------------------------------------------------------------------
[Không kết nối được MySQL]
- Kiểm tra MySQL đã chạy chưa.
- Kiểm tra host, port, username, password và DATABASE_URL trong file .env.
- Kiểm tra database user_db đã được tạo chưa.

[Lỗi Unknown column 'users.role' hoặc thiếu cột trong bảng users]
- Kiểm tra đã import đúng file db/user_db.sql vào database user_db hay chưa.
- Kiểm tra DATABASE_URL có đang trỏ đến database khác với database vừa import không.

[Đăng nhập trả về 401]
- Kiểm tra username và password.
- Chạy python seed.py để tạo tài khoản demo nếu tài khoản chưa tồn tại.
- Nếu tài khoản đã tồn tại, seed.py không đặt lại mật khẩu.
- Sau 5 lần nhập sai liên tiếp, tài khoản bị khóa 15 phút.

[GET /api/v1/users/me trả về 401]
- Kiểm tra đã cung cấp access token hợp lệ chưa.
- Kiểm tra token còn hạn và service dùng đúng JWT_SECRET_KEY, JWT_ISSUER,
  JWT_AUDIENCE như khi phát hành token.

[GET /api/v1/admin/users trả về 403]
- Endpoint này chỉ dành cho role=admin. Đăng nhập bằng tài khoản admin để thử lại.

[API nội bộ trả về 401]
- Kiểm tra header X-Internal-API-Key có được gửi hay chưa.
- Giá trị key phải khớp chính xác với INTERNAL_API_KEY trong file .env.

[API trả về 422]
- Xem Server response và Response body sau khi Execute.
- Kiểm tra trường nằm trong detail[].loc và thông báo detail[].msg.

--------------------------------------------------------------------------------
12. CẤU TRÚC THƯ MỤC DỰ ÁN
--------------------------------------------------------------------------------
UserAuthService_security_update/
|
|-- app/
|   |-- api/
|   |   |-- auth.py              # Đăng nhập và xử lý khóa tài khoản
|   |   |-- users.py             # Hồ sơ người dùng và API nội bộ
|   |   `-- admin.py             # API dành cho quản trị viên
|   |
|   |-- core/
|   |   |-- dependencies.py      # JWT validation, RBAC, internal API key
|   |   |-- rate_limit.py        # Giới hạn request đăng nhập
|   |   `-- security.py          # Băm mật khẩu và tạo JWT
|   |
|   |-- models/
|   |   `-- user.py              # SQLAlchemy model User
|   |-- schemas/
|   |   `-- user.py              # Schema request/response
|   |-- config.py                # Đọc cấu hình .env
|   |-- database.py              # Kết nối MySQL và session
|   `-- main.py                  # Khởi tạo FastAPI và các router
|
|-- db/
|   `-- user_db.sql              # Tạo database user_db và bảng users
|-- tests/
|   `-- test_security.py         # Kiểm thử các hàm bảo mật
|-- seed.py                      # Tạo tài khoản demo
|-- UserAuthService.http         # Request mẫu cho REST Client
|-- requirements.txt             # Thư viện Python
|-- .env.example                 # File cấu hình mẫu
|-- .gitignore
`-- README.md                   # Tài liệu hướng dẫn dự án

--------------------------------------------------------------------------------
13. LƯU Ý KHI TRIỂN KHAI
--------------------------------------------------------------------------------
- Thay tất cả secret mẫu bằng secret ngẫu nhiên và giữ kín file .env.
- Không sử dụng tài khoản demo trong môi trường production.
- Không công khai endpoint /internal/* qua API Gateway.
- Nên sử dụng HTTPS/TLS và giới hạn mạng cho giao tiếp giữa các service.
- Rate limit theo IP hiện hoạt động theo process; khi triển khai nhiều worker hoặc
  nhiều container, nên dùng gateway hoặc một hệ thống giới hạn dùng chung.
- Service hiện tại chưa có refresh token, đăng xuất/thu hồi JWT, MFA hoặc audit log.
