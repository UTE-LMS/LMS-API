# F01 — Người dùng và phân quyền

Story: **US-01** đăng nhập/đăng xuất (C), **US-02** tạo người dùng + vai trò + phạm vi (C), **US-03** backend kiểm tra quyền
theo phạm vi ở mọi API (C), **US-04** màn hình nhật ký thao tác (MR; *ghi* audit vẫn C), **NEW-03** vai trò `STUDENT` (MR).
Giai đoạn P2. Chủ trì đề xuất: IT (IBS cho ma trận quyền). Cấu trúc package tuân thủ ADR 0002: `config`, `filter`, `service`, `repository`, `controller`, `dto`. Phụ thuộc: `00-nen-tang-chung.md`, `ADR 0002`.

## 1. Mục tiêu

Người dùng đăng nhập nhận JWT token, có vai trò gắn với **phạm vi** (CTĐT/học phần/lớp). Mọi API nghiệp vụ khác phải kiểm tra quyền ở backend theo cặp (vai trò, phạm vi) và phân định hành vi READ/WRITE; ẩn nút ở UI không thay cho việc này.

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` §4.1 (`users`), §4.29 (`audit_logs`), nguyên tắc 11; `chot-feature-do-an.md` §6 (phân quyền).
- `ADR 0002`: Chuẩn kiến trúc 3 tầng, quy ước package (Service phụ thuộc Repository interface, truy vấn MongoTemplate nằm trong `repository.impl`), replica set `rs0`, hợp đồng liên repo.
- `database_import.js`: khối `users`.

## 3. Collection: `users`

| Trường | Ghi chú |
|---|---|
| `username` (duy nhất), `email` (duy nhất), `fullName` | `username` ≥ 3 ký tự (bất biến sau khi tạo), email theo RFC 5322; lưu dạng lowercase + trim. Trùng lặp bắt qua `DuplicateKeyException` → **409** |
| `passwordHash` | **chỉ lưu băm BCrypt** (mật khẩu gốc từ 8 đến 72 bytes UTF-8 qua `PasswordPolicyValidator`); không bao giờ trả ra API hay ghi log |
| `status` | `ACTIVE` / `LOCKED` |
| `tokenVersion` | Số nguyên (`number` / `['int','long']`, mặc định 0), tăng nguyên tử qua `UserRepositoryCustom.incrementTokenVersion` khi logout hoặc khi tài khoản bị khóa (`LOCKED`). Kiểm tra để vô hiệu hóa vĩnh viễn các token cấp trước đó |
| `roleAssignments[]` | Danh sách `{ role, scope }` (quyền người dùng là phép hợp UNION của các assignment, nạp từ DB mỗi request) |
| `role` | `ADMIN`, `PROGRAM_MANAGER`, `DEPARTMENT_HEAD`, `QA_OFFICER`, `LECTURER`, `STUDENT`, `VIEWER` (đủ 7 enum) |
| `scope` | `{ programIds[], courseIds[], sectionIds[] }` hoặc `null` (ý nghĩa xem mục 4.2 & 4.3) |
| `createdAt`, `updatedAt` | Thời gian hệ thống |

Chỉ mục: `username` duy nhất, `email` duy nhất.

## 4. Quy tắc nghiệp vụ

### 4.1 Vai trò dùng trong cam kết
`ADMIN`, `PROGRAM_MANAGER`, `DEPARTMENT_HEAD`, `QA_OFFICER`, `LECTURER`, `VIEWER`. `STUDENT` có trong enum CSDL và Java nhưng nghiệp vụ sinh viên xem kết quả làm ở NEW-03.

### 4.2 Ma trận quyền trên tài nguyên Lớp học (`canAccessSection`) **[ĐỀ XUẤT, chờ Q08]**

| Hành vi trên lớp | Ai được phép |
|---|---|
| **WRITE** (nhập điểm, tạo bài đánh giá) | `ADMIN` (toàn bộ lớp), `LECTURER` (lớp trong `scope.sectionIds`, thuộc `scope.courseIds`, hoặc có trong `CourseSection.lecturerIds` **với điều kiện** user vẫn còn vai trò `LECTURER` trong `roleAssignments`) |
| **READ** (xem lớp, bảng điểm, bài đánh giá) | `ADMIN` (toàn bộ), `PROGRAM_MANAGER` (lớp trong `sectionIds` hoặc `courseIds`), `DEPARTMENT_HEAD` (lớp trong `sectionIds` hoặc `courseIds`), `QA_OFFICER` (lớp trong `sectionIds` hoặc `courseIds`), `LECTURER` (lớp trong `sectionIds`, `courseIds` hoặc `lecturerIds`), `VIEWER` (scope rỗng = toàn bộ, scope có giá trị = trong scope) |
| **Từ chối (403)** | `STUDENT` (chờ NEW-03), các vai trò khác ngoài phạm vi hoặc vai trò không có quyền WRITE; vai trò chỉ có `programIds` trong F01 (kể cả VIEWER và PROGRAM_MANAGER, hạn chế đã biết, chờ ánh xạ CTĐT → lớp ở feature sau) |

Ghi chú:
- `lecturerIds` là nguồn cấp quyền độc lập: LECTURER có scope rỗng nhưng nằm trong `CourseSection.lecturerIds` vẫn được phép truy cập lớp đó (miễn là user vẫn giữ vai trò LECTURER).
- `VIEWER`: Chỉ có quyền READ, tuyệt đối không có quyền WRITE. Nếu scope rỗng thì READ toàn hệ thống; nếu scope chỉ định thì chỉ READ trong phạm vi đó. Nếu VIEWER chỉ có `programIds` thì bị từ chối 403 (không được coi là scope rỗng).
- Định nghĩa Scope rỗng: `scope == null` HOẶC cả ba mảng `programIds`, `courseIds`, `sectionIds` đều rỗng hoặc null (`{ programIds: [], courseIds: [], sectionIds: [] }`). Nếu có bất kỳ ID nào trong bất kỳ mảng nào thì KHÔNG phải là scope rỗng.
- Trong task F01, khớp lớp chỉ thực hiện qua `sectionIds`, `courseIds` và `lecturerIds`. Chưa suy diễn lớp từ `programIds` (hạn chế đã biết, ghi vào ADR 0003).
- Khi người dùng có nhiều `roleAssignments`, quyền tổng hợp là phép hợp (**UNION**) của toàn bộ các assignment. Quyền và vai trò được nạp từ CSDL mỗi request (không nhúng role vào JWT), chuyển vào principal (`UserPrincipal` đặt trong package `dto`).

### 4.3 Quy tắc phạm vi (Scope Rules)
- Kiểm tra quyền theo bộ ba: `(vai trò, phạm vi, hành vi READ/WRITE)`.
- Phạm vi rỗng (`scope == null` hoặc cả 3 mảng đều rỗng/null):
  - `ADMIN`: Toàn quyền hệ thống (READ/WRITE).
  - `VIEWER`: Toàn quyền đọc toàn hệ thống (READ).
  - Các vai trò nghiệp vụ khác: Scope rỗng fail-closed đối với việc khớp phạm vi theo scope. Riêng với LECTURER, nếu có trong `lecturerIds` của lớp thì vẫn được cấp quyền.
- Truy cập ngoài phạm vi hoặc sai vai trò → **403**. Chưa xác thực → **401**.

### 4.4 Bảo vệ tài khoản quản trị (ADMIN Guard)
- Chặn ADMIN tự khóa tài khoản của chính mình hoặc tự gỡ bỏ vai trò ADMIN của mình.
- Chặn thao tác khóa hoặc gỡ vai trò của tài khoản `ADMIN` ở trạng thái `ACTIVE` duy nhất còn lại trong hệ thống → **422**.
- Rủi ro đồng thời (concurrency race condition): Do MongoDB áp dụng snapshot isolation, hai giao dịch đồng thời hạ quyền hai admin khác nhau có thể cùng commit trong các tình huống song song cực đoan. Rủi ro này được chấp nhận có chủ ý và ghi vào ADR 0003 cùng `doc/tech-debt.md`; kiểm thử guard được thực hiện tuần tự.

## 5. Stories và Acceptance Criteria

| ID | AC |
|---|---|
| US-01/AC-1 | Đăng nhập đúng tài khoản `ACTIVE` + đúng mật khẩu → nhận JWT token; ghi `audit_logs` (`LOGIN`) |
| US-01/AC-2 | Sai mật khẩu hoặc username không tồn tại → 401 thông báo chung. Khi username không tồn tại, vẫn gọi so sánh hash giả (dummy hash) qua `PasswordEncoder.matches` để chống timing attack (kiểm chứng bằng Mockito) |
| US-01/AC-3 | Tài khoản `LOCKED` nhận mã 401 với cùng thông điệp chung như sai mật khẩu |
| US-01/AC-4 | `passwordHash` không xuất hiện trong bất kỳ phản hồi hay dòng log nào |
| US-01/AC-5 | Khi user gọi `POST /api/v1/auth/logout` hoặc khi tài khoản bị khóa `PUT /users/{id}/status` sang `LOCKED`, hệ thống tăng `tokenVersion` qua `UserRepositoryCustom.incrementTokenVersion`; toàn bộ token cũ lập tức bị 401 vĩnh viễn (kể cả sau đó tài khoản được mở khóa trở lại) |
| US-01/AC-6 | Resource server cấu hình `BearerTokenResolver` bỏ qua các endpoint permitAll (`/api/v1/auth/login`, `/actuator/health/**`, `/actuator/info`, OPTIONS), đảm bảo đăng nhập thành công ngay cả khi client gửi kèm token cũ đã hết hạn; các endpoint actuator khác (`/actuator/beans`, v.v.) vẫn kiểm tra Bearer token bình thường |
| US-02/AC-1 | ADMIN tạo người dùng với ≥ 1 `roleAssignment`; mật khẩu băm BCrypt thỏa chính sách 8–72 bytes UTF-8 (đo bằng byte qua `PasswordPolicyValidator`); username/email chuẩn hóa trim + lowercase |
| US-02/AC-2 | Trùng `username` hoặc `email` → 409 |
| US-02/AC-3 | `scope` chứa `programIds`/`courseIds`/`sectionIds` không tồn tại → 422 (kiểm tra tham chiếu qua repository interface) |
| US-02/AC-4 | Người không phải ADMIN gọi API quản lý người dùng → 403 |
| US-02/AC-5 | Chặn tự khóa / tự hạ quyền của chính mình hoặc của ADMIN ACTIVE cuối cùng → 422 (kiểm thử tuần tự) |
| US-02/AC-6 | Lấy danh sách `GET /api/v1/users` hỗ trợ phân trang chuẩn zero-indexed (`page`, `size`, `totalElements`, `totalPages`, `last`, `content`); nếu `page < 0` hoặc `size < 1` hoặc `size > 100` trả về 400 VALIDATION_ERROR |
| US-03/AC-1 | LECTURER chỉ thấy và thao tác trên lớp trong phạm vi phụ trách (`canAccessSection` khớp ma trận WRITE); lớp khác → 403 |
| US-03/AC-2 | Mọi endpoint (trừ permitAll) từ chối khi chưa xác thực (401 qua AuthenticationEntryPoint) |
| US-03/AC-3 | Có test tham số hóa kiểm tra toàn diện ma trận vai trò × phạm vi × READ/WRITE trên controller giả lập theo bảng ma trận mục 4.2 |
| US-03/AC-4 | Kiểm thử độc lập: LECTURER có `scope == null` hoặc scope rỗng nhưng nằm trong `lecturerIds` được cấp quyền; LECTURER có scope rỗng và không nằm trong `lecturerIds` bị từ chối 403; VIEWER chỉ có `programIds` bị từ chối 403 (không bị coi là scope rỗng); PROGRAM_MANAGER chỉ có `programIds` bị từ chối 403 (hạn chế đã biết) |
| US-03/AC-5 | Kiểm thử: Đổi vai trò hoặc scope của người dùng trong CSDL thì request kế tiếp với token hợp lệ hiện tại sử dụng ngay quyền hạn mới |

## 6. Danh sách API chính thức (`/api/v1/`)

| Phương thức | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Công khai | Đăng nhập nhận JWT token |
| POST | `/api/v1/auth/logout` | Đã đăng nhập | Đăng xuất (tăng `tokenVersion`, vô hiệu hóa mọi phiên) |
| GET | `/api/v1/auth/me` | Đã đăng nhập | Xem thông tin tài khoản đang đăng nhập |
| GET | `/api/v1/users` | ADMIN | Danh sách người dùng (phân trang page, size; 400 nếu sai biên) |
| POST | `/api/v1/users` | ADMIN | Tạo người dùng mới |
| GET | `/api/v1/users/{id}` | ADMIN | Xem chi tiết người dùng |
| PUT | `/api/v1/users/{id}` | ADMIN | Cập nhật thông tin/vai trò (không đổi username/password) |
| PUT | `/api/v1/users/{id}/status` | ADMIN | Khóa hoặc mở khóa tài khoản (khi khóa: tăng `tokenVersion`) |

## 7. Quyết định Kiến trúc (ADR 0003)

- **Xác thực:** Sử dụng `spring-boot-starter-oauth2-resource-server` (Nimbus) xác thực JWT HS256 với secret tối thiểu 32 bytes (fail-fast lúc khởi động), TTL mặc định 60 phút (`app.security.jwt.ttl=60m`). Tách logic kiểm tra user/tokenVersion sang `AuthSessionService` (thuộc `service.impl`) ném `AuthenticationException` để đảm bảo trả về 401. Chủ thể đã xác thực được đóng gói thành `UserPrincipal` đặt trong package `dto`.
- **Đăng xuất và Khóa tài khoản:** Cả hai thao tác đều tăng `tokenVersion` nguyên tử bằng `$inc` qua interface `UserRepositoryCustom.incrementTokenVersion(userId)` tại `repository.impl/UserRepositoryCustomImpl`, tuân thủ phân tầng ADR 0002.
- **Bảo vệ endpoint đăng nhập:** `BearerTokenResolver` bỏ qua kiểm tra token trên đúng danh sách permitAll (`/api/v1/auth/login`, `/actuator/health/**`, `/actuator/info`, OPTIONS).
- **Băm mật khẩu & Chuẩn hóa:** Bean dùng chung `PasswordPolicyValidator` kiểm tra mật khẩu 8–72 bytes UTF-8 (đo bằng byte, có test chuỗi ký tự Unicode có dấu) và chuẩn hóa username/email được cấu hình từ PR 2.
- **Phân quyền:** `ScopeAuthorizationService` trong `service` và `service.impl` kiểm tra theo cặp (role, scope) phân định READ/WRITE, UNION các assignments, kiểm tra vai trò LECTURER còn hiệu lực đối với `lecturerIds`. Đánh dấu nhãn `[ĐỀ XUẤT, chờ Q08]` cho ma trận quyền.
- **Định nghĩa scope rỗng:** `scope == null` HOẶC cả 3 mảng `programIds`, `courseIds`, `sectionIds` đều rỗng hoặc null.
- **CORS:** Cấu hình trong `SecurityConfig` expose header `X-Correlation-Id` và cho phép preflight OPTIONS.
- **Rủi ro chấp nhận:** F01 chỉ ghi audit cho action `LOGIN`; chấp nhận rủi ro đồng thời snapshot isolation đối với guard ADMIN cuối cùng và ghi vào ADR 0003 cùng `doc/tech-debt.md`.
- **Tài liệu vấn đáp:** Lưu trữ tại `doc/de-van-dap/f01-xac-thuc-phan-quyen.md`.

## 8. Kiểm thử & Tiêu chuẩn nghiệm thu

- Lệnh `./mvnw verify` hoàn thành thành công (BUILD SUCCESS) trên môi trường có MongoDB replica set `rs0` chạy từ `docker/mongo`.
- Tất cả các test hiện có (`contextLoads`, `ActuatorHealthTest`, `TransactionRollbackIntegrationTest`) vẫn chạy xanh: cấu hình test profile ghi đè các biến `INITIAL_ADMIN_*` về rỗng để ngăn seeder tạo admin vào DB thật, kèm test secret an toàn.
- Các unit test Mockito kiểm thử độc lập logic seed ADMIN khi users rỗng và guard ADMIN cuối cùng, không xóa dữ liệu thật trên DB `learning_outcomes`.
- Plugin JaCoCo đo lường độ bao phủ (coverage) cho các gói mô-đun lõi xác thực và phân quyền (`service.impl`, `controller`, `filter`) đạt tối thiểu 70%.

## 9. Không làm / Nợ kỹ thuật

- Không làm: Đăng ký tự do, quên mật khẩu qua email, đăng nhập mạng xã hội, phân quyền suy diễn theo `programIds` cho `canAccessSection` (hạn chế đã biết của F01).
- Nợ kỹ thuật: Rate limit cho endpoint đăng nhập, cơ chế khóa bi quan/write-conflict ép buộc cho guard ADMIN đồng thời (ghi vào `doc/tech-debt.md`).
- Chuyển giao cho F02: F01 chỉ có `canAccessSection` (quyền trên lớp). Quyền trên CTĐT (ví dụ `QA_OFFICER` xác nhận
  `program_versions`/PLO/PI theo `scope.programIds`, xem ADR 0004) cần hàm kiểm tra riêng (ví dụ `canAccessProgram`,
  fail-closed) do F02 bổ sung, kèm việc ánh xạ CTĐT → lớp để vai trò chỉ có `programIds` không bị 403 trên lớp.
- US-04 (Màn hình xem nhật ký kiểm toán) và NEW-03 (Giao diện sinh viên xem kết quả) thuộc các giai đoạn mở rộng tiếp theo.

## 10. Để vấn đáp

- Vì sao kiểm tra quyền ở backend: UI chỉ ẩn nút, gọi trực tiếp API bằng cURL/Postman vẫn phải chặn.
- Vì sao khóa tài khoản cũng tăng `tokenVersion`: Đảm bảo khi mở khóa tài khoản sau đó, các token cũ đã cấp trước thời điểm khóa không thể sống lại để gọi API.
- Vì sao nạp vai trò từ DB thay vì claim trong JWT: Cho phép thay đổi phân quyền hoặc khóa tài khoản có hiệu lực tức thì ở request kế tiếp mà không phải chờ token hết hạn.
- Vì sao dùng Custom Repository cho `$inc`: Tuân thủ ADR 0002, Service không gọi trực tiếp MongoTemplate mà ủy quyền qua UserRepositoryCustom.
- Vì sao đo độ dài mật khẩu bằng byte UTF-8: Thuật toán BCrypt giới hạn ở mức 72 bytes, không phải 72 ký tự; các ký tự có dấu trong tiếng Việt chiếm nhiều byte hơn trong bảng mã UTF-8.
- Vì sao định nghĩa scope rỗng chặt chẽ (scope null hoặc cả 3 mảng rỗng): Tránh lỗi fail-open bảo mật khi VIEWER chỉ có `programIds` mà bị lầm tưởng là scope rỗng để đọc toàn bộ hệ thống.
