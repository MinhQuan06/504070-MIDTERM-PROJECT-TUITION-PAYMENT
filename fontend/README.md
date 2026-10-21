# Giao diện iBanking

Mã nguồn đặt trong **fontend/** theo tên thư mục đã thống nhất. Thư mục frontend/ cũ được giữ nguyên. Đây là ứng dụng React + TypeScript + Vite, dùng Ant Design, Axios và Lucide; dữ liệu lấy từ backend thật, không chèn giao dịch/số dư mẫu vào giao diện.

## Chạy cùng hệ thống bằng Docker

Từ thư mục gốc repository:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
node services/payment-service/scripts/init-outbox-key.mjs
node services/user-service/scripts/init-secrets.mjs
docker compose up -d --build --wait fontend user-service student-service payment-service notification-service gateway
docker compose exec user-service python seed.py
```

Mở **http://localhost:8080**. Gateway phục vụ web và chuyển /api/v1/... đến các service cùng origin; trình duyệt không cần gọi trực tiếp cổng backend hoặc biết khóa API nội bộ. Container fontend chỉ dùng mạng Docker, không mở thêm cổng máy.

Tài khoản seed local: student/Admin123 và admin/Admin123. Seed không ghi đè tài khoản đã có. Hai tài khoản chỉ được tạo khi chạy seed.py. Payment có số dư mẫu cho ID 1 và 2; nếu database đã chứa User từ trước với ID khác, cần Admin cấp số dư demo có chủ đích. Các khóa JWT/Outbox được giữ trong .env gốc, không đưa vào bundle frontend.

Không cần reset dữ liệu để chạy web. Đặt PAYMENT_USER_MOCK_ENABLED=false và PAYMENT_STUDENT_MOCK_ENABLED=false để dùng API thật; các API Payment luôn yêu cầu JWT.

## Phát triển local

Cần Node.js 22.20+ và npm. Tại gốc:

```powershell
Set-Location fontend
npm.cmd ci
npm.cmd run dev
```

Mở http://127.0.0.1:5173. Vite proxy /api sang Gateway http://127.0.0.1:8080 nên backend/Gateway vẫn cần chạy. Không đổi code để gọi trực tiếp User/Payment: việc gọi khác origin có thể gây CORS và bỏ qua Gateway. Không có endpoint SMTP hoặc khóa RabbitMQ trong frontend.

```powershell
npm.cmd run build
```

Lệnh này kiểm tra TypeScript và sinh dist/. node_modules/, dist/, .env và tsbuildinfo được Git bỏ qua. Giữ package-lock.json trong Git để npm ci cài đúng dependency đã khóa.

## Các màn hình

- Đăng nhập: username/password, thông báo lỗi, khôi phục phiên sau tải lại trang.
- Tổng quan: profile, số dư thật, số giao dịch thành công, tổng học phí đã đóng và lịch sử gần đây.
- Đóng học phí: nhập MSSV, tra cứu sinh viên/các khoản học phí qua Gateway; chỉ chọn khoản UNPAID và thanh toán toàn bộ số tiền.
- Xác nhận: người nộp tiền chỉ đọc từ User hiện tại, số tiền từ Student, checkbox và nội dung điều khoản; khóa nút khi chưa đủ thông tin, thiếu tiền hoặc đang gửi request.
- OTP: 6 chữ số, đếm thời gian ước tính tối đa 5 phút, gửi lại OTP, hiển thị lỗi và tiếp tục giao dịch cũ sau reload.
- Kết quả: SUCCESS/FAILED/PROCESSING; tự poll 2–3 giây khi PROCESSING, không tạo thanh toán thay thế.
- Lịch sử: lọc mã giao dịch/MSSV và trạng thái, chi tiết giao dịch, tiếp tục giao dịch PENDING/PROCESSING thuộc người dùng.
- Menu profile và đăng xuất; bố cục responsive với sidebar thu gọn trên điện thoại.

Chưa có màn hình quản trị tạo/sửa số dư hoặc CRUD Student. Role Admin vẫn dùng được các màn hình cá nhân, nhưng thao tác quản trị thực hiện qua API có phân quyền như tài liệu backend. User chưa ánh xạ trực tiếp MSSV nên web không tự nhận diện học phí của bản thân.


## Cấu trúc

```text
fontend/
  src/
    api.ts                 # HTTP client và thông báo lỗi
    auth.tsx               # Đăng nhập/khôi phục phiên
    bank.tsx               # Số dư, lịch sử, giao dịch active và poll
    types.ts               # Hợp đồng dữ liệu
    ui.tsx                 # Tiền tệ, thời gian, trạng thái, empty state
    components/
      Shell.tsx            # Sidebar/header/profile
      Transactions.tsx     # Bảng và modal chi tiết
    pages/
      Login.tsx
      Dashboard.tsx
      Payment.tsx
      History.tsx
    App.tsx
    main.tsx
    styles.css
  public/favicon.svg
  Dockerfile
  nginx.conf
  vite.config.ts
```

Gateway chỉ mở các route web /, /thanh-toan, /lich-su và asset; route API/internal không tồn tại vẫn trả 404. Truy cập /thanh-toan hoặc /lich-su trực tiếp sau reload vẫn được phục vụ SPA. Swagger User/Payment/Student giữ nguyên cổng và đường dẫn riêng.
