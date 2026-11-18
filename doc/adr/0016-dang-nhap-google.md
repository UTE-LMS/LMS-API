# ADR 0016 — Đăng nhập bằng Google (OAuth2 / OpenID Connect) bằng ID token

- **Trạng thái:** ĐÃ CHỐT theo lựa chọn của sinh viên TinMH trong phiên làm việc ngày 2026-11-18 (xử lý tài khoản, cờ đổi
  mật khẩu, luồng kỹ thuật). **Chưa hỏi GVHD/Khoa**; sinh viên rà soát lại: [ĐIỀN]
- **Ngày:** 2026-11-18
- **Người soạn:** Claude Code theo yêu cầu của sinh viên.
- **Liên quan:** ADR 0003 (xác thực JWT, `tokenVersion`); ADR 0015 (mẫu quy trình ADR + dependency); tính năng "đổi
  mật khẩu lần đầu" (`mustChangePassword`).

## 1. Bối cảnh

Hệ thống chỉ có đăng nhập bằng mật khẩu. Mọi tài khoản do Admin tạo (thủ công hoặc import) và có vai trò, khoa riêng.
Giao diện đã cài sẵn `@abacritt/angularx-social-login` (Google Identity Services) nhưng nút Google chưa nối với API.

## 2. Quyết định

1. **Luồng: UI lấy ID token, API xác minh.** Nút Google ở giao diện trả về *ID token* (JWT). UI gửi `POST
   /api/v1/auth/google {idToken}`; API kiểm tra token rồi cấp JWT của hệ thống y như đăng nhập mật khẩu
   (`LoginResponse`). Các API khác không đổi.
2. **API tự kiểm tra ID token bằng bộ giải mã JWT của Spring Security (Nimbus), không thêm dependency.** Khóa công
   khai lấy từ JWKS của Google (`https://www.googleapis.com/oauth2/v3/certs`, cấu hình được qua
   `app.security.google.jwk-set-uri`), chỉ nhận RS256. Bắt buộc: chữ ký, chưa hết hạn, `iss` là
   `https://accounts.google.com` hoặc `accounts.google.com`, `aud` chứa đúng client ID của hệ thống, `email` có và
   `email_verified = true`.
3. **Chỉ khớp tài khoản có sẵn theo email.** Email Google (chuẩn hóa chữ thường) phải trùng email của một tài khoản
   `ACTIVE` do Admin tạo. **Không tự tạo tài khoản**, nên vai trò và khoa vẫn do Admin kiểm soát. Email lạ hoặc tài
   khoản bị khóa bị từ chối 401 kèm thông báo rõ.
4. **Giữ nguyên cờ `mustChangePassword`.** Đăng nhập Google không gỡ cờ: filter vẫn chặn API khác tới khi đổi mật
   khẩu bằng mật khẩu Admin cấp. Lý do: mật khẩu Admin biết vẫn còn hiệu lực nên cờ vẫn có nghĩa.
5. **Cấu hình: biến môi trường `GOOGLE_CLIENT_ID`** (API) và `GOOGLE_CLIENT_ID` (UI, nạp lúc chạy). Client ID là giá
   trị công khai; **không cần client secret**. Để trống = tắt tính năng: API trả 422 "chưa được cấu hình", UI ẩn nút.
6. Mỗi lần đăng nhập thành công ghi `audit_logs` (`LOGIN`, lý do "Đăng nhập bằng Google"). Không đổi
   `tokenVersion` (giống đăng nhập mật khẩu).

## 3. Phương án đã cân nhắc

| Phương án | Ưu | Nhược | Kết luận |
|---|---|---|---|
| UI lấy ID token, API xác minh | Không dependency mới, không giữ secret, giữ nguyên cơ chế JWT Bearer, khớp thư viện UI đã cài | UI phải nạp script Google | **Chọn** |
| API làm toàn bộ luồng redirect (`spring-boot-starter-oauth2-client`) | Chuẩn authorization code | Thêm dependency, cần client secret, phiên/redirect khác mô hình stateless hiện tại | Loại |
| Tự tạo tài khoản khi email lạ | Người mới vào ngay | Phải quyết vai trò mặc định, giới hạn tên miền; trái mô hình Admin quản lý người dùng | Loại |
| Gỡ cờ đổi mật khẩu sau khi đăng nhập Google | Vào thẳng | Mật khẩu Admin biết vẫn dùng được nên lỗ hổng mà cờ ngăn vẫn còn | Loại |

## 4. Hệ quả

- Admin phải nhập đúng email Google (Gmail hoặc tài khoản Google Workspace của trường) khi tạo người dùng; email
  trong hệ thống khác email Google thì không đăng nhập được.
- Người dùng chỉ đăng nhập Google vẫn phải biết mật khẩu ban đầu để qua bước đổi mật khẩu lần đầu (quyết định 4).
  Nếu thấy bất tiện, cần ADR riêng (ví dụ cho đặt mật khẩu mới sau khi xác thực Google).
- Cần tạo OAuth client (loại Web) trên Google Cloud Console và khai báo "Authorized JavaScript origins" là địa chỉ
  frontend. Việc này do người vận hành làm; mã không chứa client ID thật.
- API gọi ra `www.googleapis.com` để lấy khóa công khai (được lưu đệm); môi trường không ra Internet được thì đăng
  nhập Google không dùng được, đăng nhập mật khẩu vẫn bình thường.

## 5. Câu hỏi còn mở

1. Trường có dùng Google Workspace (tên miền riêng) không? Nếu có, nên giới hạn thêm theo tên miền email.
2. Có cần cho người dùng tự liên kết/ngắt liên kết Google trong trang "Tài khoản của tôi" không? Hiện không có
   khái niệm liên kết: khớp theo email mỗi lần đăng nhập.
3. GVHD/Khoa có chấp nhận phụ thuộc vào dịch vụ Google để đăng nhập không?
