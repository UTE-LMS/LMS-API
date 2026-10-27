# F01 — Người dùng và phân quyền

Story: **US-01** đăng nhập/đăng xuất (C), **US-02** tạo người dùng + vai trò + phạm vi (C), **US-03** backend kiểm tra quyền
theo phạm vi ở mọi API (C), **US-04** màn hình nhật ký thao tác (MR; *ghi* audit vẫn C), **NEW-03** vai trò `STUDENT` (MR).
Giai đoạn P2. Chủ trì đề xuất: IT (IBS cho ma trận quyền). Package: `auth`. Phụ thuộc: `00-nen-tang-chung.md`.

## 1. Mục tiêu

Người dùng đăng nhập, có vai trò gắn với **phạm vi** (CTĐT/học phần/lớp). Mọi API khác phải kiểm tra quyền ở backend; ẩn nút
ở UI không thay cho việc này.

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` §4.1 (`users`), §4.29 (`audit_logs`), nguyên tắc 11; `chot-feature-do-an.md` §6 (phân quyền).
- `database_import.js`: khối `users`.

## 3. Collection: `users`

| Trường | Ghi chú |
|---|---|
| `username` (duy nhất), `email` (duy nhất), `fullName` | `PLO_DB_UPDATE.md` ghi `username` ≥ 3 ký tự, email theo RFC 5322; validator MongoDB chưa ép ⇒ **service/Bean Validation phải kiểm tra** |
| `passwordHash` | **chỉ lưu băm** (BCrypt hoặc Argon2); không bao giờ trả ra API |
| `status` | `ACTIVE` / `LOCKED` |
| `roleAssignments[]` | `{ role, scope }` |
| `role` | `ADMIN`, `PROGRAM_MANAGER`, `DEPARTMENT_HEAD`, `QA_OFFICER`, `LECTURER`, `STUDENT`, `VIEWER` |
| `scope` | `{ programIds[], courseIds[], sectionIds[] }` (có thể rỗng/null: ý nghĩa xem 4.2) |
| `createdAt`, `updatedAt` | |

Chỉ mục: `username` duy nhất, `email` duy nhất. Xem validator thật trong `database_import.js`.

## 4. Quy tắc nghiệp vụ

### 4.1 Vai trò dùng trong cam kết
`ADMIN`, `PROGRAM_MANAGER`, `DEPARTMENT_HEAD`, `QA_OFFICER`, `LECTURER`, `VIEWER`. `STUDENT` có trong CSDL nhưng chỉ làm ở NEW-03.

### 4.2 Ma trận quyền **[ĐỀ XUẤT, chờ Q08]**
| Thao tác | ADMIN | PROGRAM_MANAGER | DEPARTMENT_HEAD | QA_OFFICER | LECTURER | VIEWER |
|---|---|---|---|---|---|---|
| Quản lý người dùng | ✔ | | | | | |
| CTĐT, PLO/PI, I/R/M, học phần, đề cương, CLO, liên kết CLO–PI | | ✔ | | | | |
| Lập và gửi duyệt bài đánh giá lớp | | | | | ✔ | |
| Duyệt / từ chối ma trận đề thi (F04, NEW-01) | | | ✔ | | | |
| Nhập điểm, xác nhận lô | | | | | ✔ (lớp mình) | |
| Xác nhận quy tắc đo | | ? | | ? | | |
| Chạy đo lường, xem báo cáo | | ✔ | ✔ | ✔ | ✔ (lớp mình) | đọc |
| Tạo / theo dõi hành động cải tiến | | ✔ | ✔ | ✔ | | |

"Lớp mình" nghĩa là `sectionId` nằm trong `scope.sectionIds` của người đó **hoặc** người đó có trong
`course_sections.lecturerIds`. **[CHỜ KHOA]** ai được xác nhận quy tắc đo (`RULE_CONFIRM`): ô "?" chưa quyết, làm dạng cấu hình.

### 4.3 Phạm vi
- Quyền kiểm tra theo cặp (vai trò, phạm vi). Phạm vi rỗng với vai trò toàn cục (`ADMIN`, `VIEWER` tùy chính sách) là **[ĐỀ XUẤT]**
  hiểu là "toàn hệ thống"; ghi rõ trong ADR nếu chọn khác.
- Truy cập ngoài phạm vi → **403**, không phải 404 (trừ khi cố ý giấu sự tồn tại; ghi lại lựa chọn).

## 5. Stories và acceptance criteria (nháp)

| ID | AC |
|---|---|
| US-01/AC-1 | Đúng tài khoản `ACTIVE` + đúng mật khẩu → nhận phiên/token; ghi `audit_logs` (`LOGIN`) |
| US-01/AC-2 | Sai mật khẩu hoặc không tồn tại → 401, thông báo **không** nói rõ lý do nào sai |
| US-01/AC-3 | Tài khoản `LOCKED` không vào được dù đúng mật khẩu |
| US-01/AC-4 | `passwordHash` không xuất hiện trong bất kỳ phản hồi hay log nào |
| US-02/AC-1 | ADMIN tạo người dùng với ≥ 1 `roleAssignment`; mật khẩu lưu băm |
| US-02/AC-2 | Trùng `username` hoặc `email` → 409 |
| US-02/AC-3 | `scope` chứa `programIds`/`courseIds`/`sectionIds` không tồn tại → 422 (service kiểm tra tham chiếu) |
| US-02/AC-4 | Người không phải ADMIN gọi API quản lý người dùng → 403 |
| US-03/AC-1 | LECTURER chỉ thấy và nhập điểm cho lớp trong phạm vi của mình; lớp khác → 403 |
| US-03/AC-2 | Mọi endpoint (trừ đăng nhập, health) từ chối khi chưa xác thực (401) |
| US-03/AC-3 | Có test tự động kiểm tra **mỗi** endpoint nghiệp vụ với vai trò sai (mục tiêu NFR: 100% endpoint có kiểm tra quyền) |
| NEW-03/AC-1 (MR) | STUDENT chỉ đọc `student_results` có `studentId` thuộc `students` mà `students.userId` = chính mình; của người khác → 403 |

## 6. API đề xuất **[ĐỀ XUẤT]**

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| POST | `/api/auth/login` | công khai |
| POST | `/api/auth/logout` | đã đăng nhập |
| GET | `/api/auth/me` | đã đăng nhập |
| POST / GET / PUT | `/api/users`, `/api/users/{id}` | ADMIN |
| PUT | `/api/users/{id}/status` (khóa/mở) | ADMIN |

## 7. Quyết định cần ADR

- Xác thực: JWT hay session (Spring Security có sẵn). Nêu phương án khác, lý do, hệ quả với Angular.
- Thuật toán băm mật khẩu (BCrypt có sẵn trong Spring Security; Argon2 cần thư viện thêm).
- Cách biểu diễn quyền trong code: `@PreAuthorize` + bộ kiểm tra phạm vi dùng chung (một chỗ, không rải `if` theo controller).

## 8. Test bắt buộc

Sai mật khẩu; `LOCKED`; trùng khóa; thiếu quyền (mỗi vai trò × mỗi nhóm endpoint); phạm vi sai (giảng viên lớp khác);
token hết hạn/giả mạo; mật khẩu không lộ trong phản hồi. Lõi phân quyền thuộc "mô-đun chính" (coverage ≥ 70%).

## 9. Không làm / chưa quyết

- Không làm: đăng ký tự do, quên mật khẩu qua email (`spring-boot-starter-mail` đang chưa có lý do dùng), đăng nhập mạng xã hội.
- Chờ: Q08 (ai rà soát nghiệp vụ/kỹ thuật, ai xác nhận quy tắc); US-04 màn hình xem nhật ký là MR.

## 10. Để vấn đáp

Vì sao kiểm tra quyền ở backend (UI chỉ ẩn nút, gọi thẳng API vẫn qua được); vì sao lưu băm (lộ CSDL không lộ mật khẩu); vì
sao phạm vi nằm trong `roleAssignments` (cùng vai trò nhưng khác lớp/khoa); bỏ kiểm tra phạm vi thì giảng viên A sửa được
điểm lớp của giảng viên B.
