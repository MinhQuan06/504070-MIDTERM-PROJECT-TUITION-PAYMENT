# Giao diện iBanking

React + TypeScript + Vite + Ant Design. Thư mục tên `fontend` theo dự án. Giao diện dùng API thật qua Gateway.

## Chạy

Ưu tiên [hướng dẫn Docker ở thư mục gốc](../README.md), sau đó mở http://localhost:8080.

Phát triển local (backend/Gateway vẫn cần chạy):

```powershell
cd fontend
npm.cmd ci
npm.cmd run dev
```

Mở http://127.0.0.1:5173. Vite chuyển `/api` đến Gateway 8080. Build: `npm.cmd run build`.

## Chức năng

- Đăng nhập, xem số dư và giao dịch gần đây.
- Tra cứu MSSV, chọn học phí, xác nhận điều khoản và nhập OTP.
- Xem lịch sử, lọc trạng thái và chi tiết giao dịch.
- Tiếp tục giao dịch sau reload; tự cập nhật khi PROCESSING; bố cục hỗ trợ điện thoại.

OTP: tối đa 5 lần sai/mã, chờ 60 giây giữa các lần cấp, tối đa 5 lần gửi lại/giao dịch. Server kiểm soát các giới hạn. Khi PROCESSING, chờ kết quả thay vì tạo thanh toán khác.

`src/pages` chứa các màn hình; `components` chứa khung trang/bảng; `api.ts` gọi API; `auth.tsx` quản lý phiên; `bank.tsx` quản lý dữ liệu và cập nhật giao dịch. Không đặt khóa nội bộ vào frontend. Không commit node_modules, dist hoặc .env.
