# ADR 0003 — Xác thực Nimbus Resource Server, Quản lý Phiên Đăng xuất Toàn cầu qua tokenVersion, và Phân quyền Phạm vi canAccessSection [ĐỀ XUẤT, chờ Q08]

- **Trạng thái:** [ĐỀ XUẤT, chờ Q08]
- **Ngày:** 2026-10-27
- **Người đề xuất:** TinMH (Sinh viên IT), sinh viên IBS, Giảng viên hướng dẫn
- **Phạm vi:** `LMS-API` (phân hệ F01: Người dùng và phân quyền) và `LMS-UI` (hợp đồng API liên repo `LMS-UI/doc/API-CONTRACT.md`)

---

## 1. Bối cảnh (Context)

Tính năng F01 (Người dùng và phân quyền) đặt nền móng bảo mật cho toàn bộ hệ thống đo lường chuẩn đầu ra đào tạo (`LMS-API`). Phân hệ này chịu trách nhiệm:
1. **US-01 (Xác thực người dùng):** Cho phép người dùng đăng nhập bằng tài khoản và mật khẩu, cấp JWT token an toàn, hỗ trợ đăng xuất vô hiệu hóa tức thời mọi phiên làm việc, và lấy thông tin cá nhân.
2. **US-02 (Quản lý người dùng):** Quản trị viên (`ADMIN`) quản lý danh sách người dùng, cấp phát vai trò và phạm vi phụ trách (`scope`), khóa/mở khóa tài khoản, bảo vệ tính bất biến của tên đăng nhập và bảo vệ tài khoản quản trị tối cao.
3. **US-03 (Phân quyền theo vai trò và phạm vi):** Kiểm soát truy cập tài nguyên lớp học phần (`CourseSection`) dựa trên cặp (vai trò, phạm vi) theo hai hành vi rõ rệt: Ghi dữ liệu (`WRITE`) và Đọc dữ liệu (`READ`).

Để đáp ứng các yêu cầu khắt khe của hệ thống giáo dục đại học và tuân thủ chuẩn kiến trúc ADR 0002, hệ thống cần giải quyết các bài toán kỹ thuật trọng yếu sau:
- **Xác thực phi trạng thái nhưng hỗ trợ thu hồi tức thời (Stateless JWT with Instant Revocation):** JWT truyền thống có nhược điểm không thể thu hồi trước khi hết hạn (TTL). Khi người dùng đăng xuất hoặc khi tài khoản bị quản trị viên khóa khẩn cấp (`LOCKED`), mọi token đã cấp phải bị vô hiệu hóa ngay lập tức trên mọi thiết bị.
- **Bảo mật mật khẩu và phòng chống tấn công:**
  - Thuật toán băm BCrypt có giới hạn độ dài tối đa là 72 bytes. Các ký tự tiếng Việt có dấu chiếm 2–3 bytes trong UTF-8, nếu chỉ dùng `@Size` đếm ký tự sẽ dẫn đến lỗ hổng cắt cụt mật khẩu (BCrypt password truncation vulnerability).
  - Tấn công dò quét thời gian phản hồi (Timing Attack / User Enumeration) khi đăng nhập với tài khoản không tồn tại.
- **Phân quyền truy cập tài nguyên lớp học phần (`canAccessSection`) [ĐỀ XUẤT, chờ Q08]:** Phân định rạch ròi giữa quyền WRITE (nhập điểm, sửa bài đánh giá) và READ (xem lớp, bảng điểm). Đảm bảo tính độc lập của phân công giảng dạy (`lecturerIds`), định nghĩa scope rỗng nghiêm ngặt và xử lý quy tắc fail-closed.
- **Bảo vệ tài khoản quản trị (ADMIN Protection Guard):** Ngăn chặn tình trạng hệ thống rơi vào trạng thái vô chủ (không còn quản trị viên active nào).
- **Tuân thủ kiến trúc phân tầng 3 lớp của ADR 0002:** Phân tách rõ ràng controller, service, repository, dto, mapper; cấm tuyệt đối tạo package `security` ở cấp root; mọi thao tác `MongoTemplate` bắt buộc phải đóng gói trong `repository.impl`.

---

## 2. Các Quyết Định Kiến Trúc (Decisions)

### QĐ-1: Xác thực Nimbus OAuth2 Resource Server với JWT HS256 và Nạp Quyền Động từ CSDL

1. **Cơ chế xác thực:**
   - Sử dụng thư viện chính thức `spring-boot-starter-oauth2-resource-server` (Nimbus JOSE JWT) được quản lý tự động bởi Spring Boot 4.1 Parent BOM.
   - Sử dụng thuật toán ký đối xứng **HS256** với khóa bí mật `JWT_SECRET` có độ dài tối thiểu 32 bytes (256 bits). Ứng dụng thực hiện kiểm tra fail-fast ngay khi khởi động: nếu khóa bí mật không đủ 32 bytes, ứng dụng sẽ từ chối khởi chạy.
   - Thời gian sống (TTL) mặc định của JWT Access Token là **60 phút** (`app.security.jwt.ttl=60m`), Issuer là `lms-api` (`app.security.jwt.issuer=lms-api`).
2. **Payload của JWT Token:**
   - Token chỉ mang hai thông tin thiết yếu: `sub` (userId) và `tokenVersion` (số phiên bảo mật).
   - **Tuyệt đối không lưu vai trò (`roles`) hoặc phạm vi (`scope`) vào Claims của JWT token.**
3. **Nạp quyền động từ CSDL mỗi request (Dynamic Authorization Reload):**
   - Ở mỗi HTTP request mang Bearer token hợp lệ, hệ thống nạp thông tin người dùng tươi từ MongoDB và đóng gói vào đối tượng `UserPrincipal` đặt trong package `com.hcmute.lms.lmsapi.dto`.
   - Lợi ích: Mọi sự thay đổi về vai trò, phạm vi phụ trách hoặc trạng thái khóa tài khoản do ADMIN thực hiện sẽ có hiệu lực ngay lập tức ở request kế tiếp mà không phải chờ token cũ hết hạn.

---

### QĐ-2: Dịch Vụ Quản Lý Phiên `AuthSessionService` và Xử Lý Ngoại Lệ Xác Thực Tập Trung

1. **Tách biệt logic kiểm tra phiên:**
   - Xây dựng service riêng biệt `AuthSessionService` (Interface trong `com.hcmute.lms.lmsapi.service` và hiện thực tại `service.impl.AuthSessionServiceImpl`).
   - Phương thức kiểm tra: `validateUserSession(String userId, long tokenVersionInToken)`.
   - Nếu người dùng không tồn tại, tài khoản không ở trạng thái `ACTIVE` (bị `LOCKED`), hoặc `tokenVersion` trong token khác với `tokenVersion` lưu trong CSDL: ném ngoại lệ `AuthenticationException` (ví dụ `InvalidBearerTokenException`).
2. **Xử lý lỗi qua `AuthenticationEntryPoint` chuẩn:**
   - Toàn bộ lỗi xác thực (thiếu token, token hết hạn, sai chữ ký, tài khoản bị khóa, tokenVersion không khớp) được chuyển qua `AuthenticationEntryPoint` tùy biến, trả về cấu trúc lỗi chuẩn `ErrorResponse` (HTTP 401, mã `UNAUTHORIZED`) theo đúng quy định tại ADR 0002 và US-40:
     ```json
     {
       "timestamp": "2026-10-27T10:15:30.000Z",
       "status": 401,
       "code": "UNAUTHORIZED",
       "message": "Phiên đăng nhập không hợp lệ hoặc tài khoản đã bị khóa",
       "path": "/api/v1/users",
       "fieldErrors": null
     }
     ```

---

### QĐ-3: Đăng Xuất Toàn Cầu và Khóa Tài Khoản qua Tăng Nguyên Tử `tokenVersion`

1. **Nguyên lý hoạt động:**
   - Trường `tokenVersion` (kiểu số nguyên `long`, mặc định 0L) được lưu trữ trong document `users`.
   - Khi người dùng thực hiện Đăng xuất (`POST /api/v1/auth/logout`) hoặc khi tài khoản bị khóa (`PUT /api/v1/users/{id}/status` sang `LOCKED`), hệ thống thực hiện tăng nguyên tử giá trị `tokenVersion` lên 1 đơn vị:
     $$\text{tokenVersion}_{\text{mới}} = \text{tokenVersion}_{\text{cũ}} + 1$$
   - Khi đó, mọi JWT token đã được phát hành trước đó (chỉ mang `tokenVersion` cũ) lập tức trở nên không hợp lệ ở tất cả các request tiếp theo trên mọi thiết bị và trình duyệt.
   - Thao tác mở khóa lại tài khoản (`ACTIVE`) không làm giảm hay phục hồi `tokenVersion`; do đó các token cấp trước thời điểm khóa vẫn vĩnh viễn bị từ chối.
2. **Hiện thực tuân thủ nghiêm ngặt ADR 0002:**
   - Cấm gọi trực tiếp `MongoTemplate` từ tầng Service.
   - Khai báo interface `UserRepositoryCustom` trong package `com.hcmute.lms.lmsapi.repository` với phương thức `long incrementTokenVersion(String userId)`.
   - Hiện thực tại `UserRepositoryCustomImpl` trong package `com.hcmute.lms.lmsapi.repository.impl` sử dụng toán tử `$inc` của `MongoTemplate`:
     ```java
     mongoTemplate.updateFirst(
         Query.query(Criteria.where("_id").is(userId)),
         new Update().inc("tokenVersion", 1),
         User.class
     );
     ```

---

### QĐ-4: Bộ Lọc `BearerTokenResolver` Khớp Chính Xác Danh Sách permitAll

1. **Vấn đề cần giải quyết:**
   - Mặc định, `BearerTokenAuthenticationFilter` của Spring Security sẽ cố gắng giải mã và xác thực header `Authorization` trên mọi request. Nếu client vô tình gửi kèm token cũ đã hết hạn hoặc không hợp lệ vào endpoint đăng nhập (`POST /api/v1/auth/login`), bộ lọc mặc định sẽ từ chối với HTTP 401 trước khi request tới được `AuthController`.
2. **Quyết định cấu hình:**
   - Xây dựng bean `BearerTokenResolver` tùy biến kiểm tra URI của request: Nếu URI thuộc danh sách công khai (`permitAll`), `BearerTokenResolver` trả về `null`.
   - Danh sách công khai được chuẩn hóa chính xác gồm:
     - `POST /api/v1/auth/login`
     - `GET /actuator/health`
     - `GET /actuator/health/**`
     - `GET /actuator/info`
     - Các request preflight HTTP `OPTIONS`.
   - Tất cả các endpoint quản trị Actuator nhạy cảm khác (`/actuator/beans`, `/actuator/env`...) và toàn bộ endpoint nghiệp vụ vẫn yêu cầu Bearer token bình thường.

---

### QĐ-5: Ma Trận Phân Quyền Chi Tiết trên Tài Nguyên Lớp Học (`canAccessSection`) [ĐỀ XUẤT, chờ Q08]

1. **Phân định hai hành vi nghiệp vụ:**
   - **Hành vi WRITE (Ghi):** Nhập điểm sinh viên, cập nhật điểm, lập/sửa bài đánh giá của lớp học phần.
   - **Hành vi READ (Đọc):** Xem thông tin lớp học, danh sách sinh viên ghi danh, bảng điểm và kết quả đo lường.
2. **Bảng Ma trận Phân quyền:**

| Vai trò (`Role`) | Hành vi WRITE trên Lớp (`sectionId`) | Hành vi READ trên Lớp (`sectionId`) | Điều kiện áp dụng & Ràng buộc Scope |
|---|---|---|---|
| `ADMIN` | **CHO PHÉP** (Toàn bộ) | **CHO PHÉP** (Toàn bộ) | Toàn quyền hệ thống, không phụ thuộc vào `scope`. |
| `LECTURER` | **CHO PHÉP** (Trong phạm vi) | **CHO PHÉP** (Trong phạm vi) | Thỏa mãn khi user còn role `LECTURER` VÀ: (1) `sectionId` ∈ `scope.sectionIds`, HOẶC (2) `courseId` ∈ `scope.courseIds`, HOẶC (3) `userId` ∈ `CourseSection.lecturerIds`. |
| `PROGRAM_MANAGER` | **TỪ CHỐI (403)** | **CHO PHÉP** (Trong phạm vi) | READ: `sectionId` ∈ `scope.sectionIds` HOẶC `courseId` ∈ `scope.courseIds`. Không có quyền WRITE. Nếu chỉ có `programIds` thì từ chối 403 (hạn chế F01). |
| `DEPARTMENT_HEAD` | **TỪ CHỐI (403)** | **CHO PHÉP** (Trong phạm vi) | READ: `sectionId` ∈ `scope.sectionIds` HOẶC `courseId` ∈ `scope.courseIds`. Không có quyền WRITE trên lớp (duyệt ma trận đề thi là endpoint duyệt riêng ở F04). |
| `QA_OFFICER` | **TỪ CHỐI (403)** | **CHO PHÉP** (Trong phạm vi) | READ: `sectionId` ∈ `scope.sectionIds` HOẶC `courseId` ∈ `scope.courseIds`. Không có quyền WRITE. |
| `VIEWER` | **TỪ CHỐI (403)** | **CHO PHÉP** (Tùy Scope) | Tuyệt đối không có quyền WRITE. Nếu scope rỗng (`scope == null` hoặc 3 mảng rỗng) → READ toàn hệ thống. Nếu scope có `sectionIds`/`courseIds` → chỉ READ trong phạm vi đó. Nếu chỉ có `programIds` → TỪ CHỐI 403. |
| `STUDENT` | **TỪ CHỐI (403)** | **TỪ CHỐI (403)** | Từ chối 403 trên tài nguyên lớp học (chờ story NEW-03 sinh viên xem kết quả cá nhân qua `students.userId`). |

3. **Bốn nguyên tắc vận hành cốt lõi:**
   - **Quy tắc `lecturerIds` độc lập:** Trường `lecturerIds` trong collection `course_sections` là nguồn ủy quyền trực tiếp từ bảng phân công giảng dạy. Giảng viên có `scope == null` hoặc scope rỗng nhưng có tên trong `CourseSection.lecturerIds` của lớp đó vẫn có toàn quyền READ và WRITE trên lớp đó (với điều kiện bắt buộc là người dùng vẫn đang sở hữu vai trò `LECTURER`).
   - **Định nghĩa Scope rỗng nghiêm ngặt (Strict Empty Scope):**
     $$\text{isEmpty}(scope) \iff (scope == null) \lor (\text{programIds rỗng} \land \text{courseIds rỗng} \land \text{sectionIds rỗng})$$
     Nếu bất kỳ mảng nào có chứa ít nhất 1 phần tử thì scope đó **không phải** là scope rỗng. Do đó, tài khoản `VIEWER` chỉ có `programIds` sẽ không được rơi vào nhánh đọc toàn hệ thống.
   - **Hạn chế kỹ thuật đã biết của F01 (Known Limitation for `programIds`):**
     Trong F01, hệ thống chỉ hỗ trợ so khớp trực tiếp qua `sectionIds`, `courseIds` và `lecturerIds`. Việc suy diễn quan hệ từ CTĐT (`programIds`) xuống các học phần/lớp học phần thuộc phạm vi của F02. Vì vậy trong F01, tài khoản chỉ có `programIds` (kể cả `PROGRAM_MANAGER` và `VIEWER`) khi kiểm tra quyền trên lớp học sẽ bị từ chối với HTTP 403 theo nguyên lý Fail-Closed.
   - **Quyền tổng hợp là phép Hợp (UNION):** Người dùng có nhiều phân công vai trò (`roleAssignments`) sẽ nhận được quyền hạn là phép hợp của tất cả các vai trò.

---

### QĐ-6: An Toàn Mật Khẩu, Chống Tấn Công Timing Attack và Chuẩn Hóa Dữ Liệu

1. **Chính sách mật khẩu đo bằng byte UTF-8 (`PasswordPolicyValidator`):**
   - Thuật toán băm BCrypt giới hạn chiều dài mật khẩu tối đa là 72 bytes. Các ký tự tiếng Việt có dấu chiếm 2 đến 3 bytes trong mã hóa UTF-8. Nếu dùng `@Size(min=8, max=72)` đếm ký tự, một chuỗi mật khẩu 30 ký tự tiếng Việt có thể vượt quá 72 bytes, dẫn đến việc BCrypt âm thầm cắt ngắn mật khẩu (password truncation attack).
   - Xây dựng bean `PasswordPolicyValidator` đo độ dài thực tế:
     $$8 \le \text{rawPassword.getBytes(StandardCharsets.UTF_8).length} \le 72$$
2. **Chống tấn công đo thời gian (Timing Attack Defense):**
   - Khi người dùng đăng nhập bằng tên tài khoản không tồn tại trong CSDL, hệ thống vẫn bắt buộc gọi `passwordEncoder.matches(rawPassword, DUMMY_BCRYPT_HASH)` với một chuỗi hash giả định nhằm giữ thời gian thực thi ổn định (~100–200ms), ngăn ngừa kẻ tấn công dò quét sự tồn tại của tài khoản (User Enumeration).
3. **Chuẩn hóa Username và Email:**
   - Toàn bộ `username` và `email` trước khi truy vấn hoặc lưu trữ đều phải chạy qua hàm chuẩn hóa: `input.trim().toLowerCase(Locale.ROOT)`.
   - `username` là bất biến (immutable) sau khi tạo, không cho phép sửa đổi qua API cập nhật người dùng. Trùng lặp `username` hoặc `email` trong MongoDB phát sinh `DuplicateKeyException`, được bắt và chuyển thành HTTP 409 `CONFLICT`.

---

### QĐ-7: Bảo Vệ Tài Khoản Quản Trị (ADMIN Guard) và Ghi Nhận Nợ Kỹ Thuật

1. **Các chốt chặn bảo vệ quản trị viên:**
   - Chặn ADMIN tự khóa tài khoản của chính mình (HTTP 422 `BUSINESS_RULE_VIOLATION`).
   - Chặn ADMIN tự gỡ bỏ vai trò ADMIN của chính mình (HTTP 422).
   - Chặn thao tác khóa hoặc hạ quyền của tài khoản `ADMIN` ở trạng thái `ACTIVE` duy nhất còn lại trong hệ thống (HTTP 422).
2. **Chấp nhận rủi ro đồng thời Snapshot Isolation (Nợ kỹ thuật TD-13):**
   - Do MongoDB áp dụng Snapshot Isolation cho multi-document transactions, hai giao dịch đồng thời của hai admin cùng hạ quyền nhau có thể cùng commit thành công, dẫn đến tình trạng hệ thống không còn admin nào.
   - Hệ thống chấp nhận rủi ro này trong giai đoạn F01 do tần suất xảy ra cực thấp, ghi nhận vào `doc/tech-debt.md` (TD-13) và thực thi các bài test kiểm thử Guard một cách tuần tự.

---

### QĐ-8: Khởi Tạo Quản Trị Viên Ban Đầu và Cấu Hình Cách Ly Môi Trường Test

1. **Khởi tạo ADMIN ban đầu (`InitialAdminSeeder`):**
   - Khi khởi động, nếu collection `users` đang rỗng (`count == 0`):
     - Đọc các biến môi trường `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_EMAIL`, `INITIAL_ADMIN_PASSWORD`.
     - Nếu có đầy đủ: kiểm tra qua `PasswordPolicyValidator`, băm BCrypt, tạo tài khoản ADMIN `ACTIVE` với `tokenVersion = 0L`, ghi log INFO (tuyệt đối không in mật khẩu ra log).
     - Nếu thiếu biến môi trường: ghi log cảnh báo WARN ở cấp hệ thống.
2. **Cách ly môi trường kiểm thử (Test Isolation):**
   - Tạo file `src/test/resources/application.properties` và cấu hình `maven-surefire-plugin` với `systemPropertyVariables` ghi đè tường minh các biến `INITIAL_ADMIN_*` về chuỗi rỗng:
     ```properties
     INITIAL_ADMIN_USERNAME=
     INITIAL_ADMIN_EMAIL=
     INITIAL_ADMIN_PASSWORD=
     app.security.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123
     app.security.jwt.ttl=60m
     app.security.jwt.issuer=lms-api
     ```
   - Điều này ngăn chặn việc biến môi trường của hệ điều hành trên máy developer kích hoạt seeder chèn admin vào CSDL thật trong quá trình chạy test suite.

---

### QĐ-9: Cấu Trúc Package và Phạm Vi Kiểm Toán F01

1. **Tuân thủ cấu trúc package ADR 0002:**
   - Tuyệt đối không tạo package `security` ở cấp root. Các thành phần được phân bổ đúng tầng:
     - `config/`: `SecurityConfig`, `JwtConfig`, `CorsConfig`, `InitialAdminSeeder`.
     - `controller/`: `AuthController`, `UserController`.
     - `document/`: `User`, `Role`, `UserStatus`, `RoleAssignment`, `UserScope`, `CourseSection`.
     - `dto/`: `LoginRequest`, `LoginResponse`, `UserPrincipal`, `CreateUserRequest`, `UpdateUserRequest`, `UserResponse`, `PageResponse<T>`.
     - `repository/` & `repository.impl/`: `UserRepository`, `UserRepositoryCustom`, `UserRepositoryCustomImpl`.
     - `service/` & `service.impl/`: `AuthService`, `UserService`, `AuthSessionService`, `ScopeAuthorizationService`.
     - `validator/` & `util/`: `PasswordPolicyValidator`, `StringNormalizationUtils`.
2. **Phạm vi kiểm toán (Audit Scope):**
   - Trong phân hệ F01, hệ thống chỉ ghi nhận kiểm toán vào `audit_logs` đối với hành động Đăng nhập thành công (`AuditAction.LOGIN`), ghi nhận đầy đủ thực thể `{ collection: "users", id: userId }`, client IP và timestamp.

---

## 3. Các Phương Án Đã Cân Nhắc (Alternatives Considered)

| Tiêu chí | Phương án đã chọn | Phương án thay thế | Lý do bác bỏ phương án thay thế |
|---|---|---|---|
| **Cơ chế quản lý phiên** | JWT Access Token kèm `tokenVersion` trong MongoDB | JWT hoàn toàn phi trạng thái (Fully Stateless) | Không thể vô hiệu hóa phiên ngay lập tức khi đăng xuất hoặc khóa tài khoản khẩn cấp. |
| **Lưu trữ quyền hạn** | Nạp quyền động từ DB mỗi request qua `UserPrincipal` | Lưu toàn bộ `roles` và `scope` trong JWT Claims | Khi ADMIN thay đổi quyền hoặc khóa tài khoản, người dùng vẫn giữ nguyên quyền cũ cho tới khi token hết hạn (tối đa 60 phút). |
| **Kiểm tra độ dài mật khẩu** | Đo chiều dài byte UTF-8 (8–72 bytes) | Dùng `@Size(min=8, max=72)` đếm ký tự | Gây lỗi bảo mật cắt ngắn mật khẩu (BCrypt truncation attack) khi mật khẩu chứa ký tự tiếng Việt nhiều byte. |
| **Chống race condition Guard ADMIN** | Chấp nhận rủi ro Snapshot Isolation (TD-13) | Sử dụng Distributed Lock (Redis Redisson) | Làm tăng độ phức tạp kiến trúc không cần thiết, vi phạm nguyên tắc giữ hệ thống tinh gọn ở giai đoạn đồ án tốt nghiệp. |

---

## 4. Hệ Quả (Consequences)

### Tích cực (Positive)
- Hỗ trợ đăng xuất toàn cầu tức thời và khóa tài khoản có hiệu lực ngay lập tức mà vẫn tận dụng được định dạng chuẩn JWT Bearer.
- Thay đổi phân quyền của người dùng có hiệu lực ngay ở request tiếp theo.
- Loại trừ triệt để lỗ hổng BCrypt password truncation với ký tự tiếng Việt có dấu.
- Bảo vệ hệ thống khỏi tấn công timing attack dò quét tài khoản.
- Đảm bảo 100% tuân thủ mô hình 3 tầng và các nguyên lý SOLID theo ADR 0002.

### Tiêu cực / Chi phí đánh đổi (Negative / Trade-offs)
- Mỗi request yêu cầu truy vấn MongoDB một lần để nạp `UserPrincipal` và kiểm tra `tokenVersion` (chi phí này không đáng kể do truy vấn theo khóa chính `_id` có chỉ mục B-Tree và bộ nhớ đệm MongoDB WiredTiger).
- Trong F01, quyền hạn trên lớp học phần chưa tự động ánh xạ từ `programIds` xuống (cần chờ phân hệ F02).

---

## 5. Tài Liệu Tham Khảo Liên Quan

- `D:\TLCN\LMS-API\doc\adr\0002-nen-tang-chung-step-00.md`
- `D:\TLCN\LMS-API\doc\GiaiThich_CoSoDuLieu.md` (§4.1, §4.17, §4.29)
- `D:\TLCN\LMS-API\doc\database_import.js`
- `D:\TLCN\LMS-UI\doc\API-CONTRACT.md`
- `D:\TLCN\LMS-API\doc\tech-debt.md` (TD-13)
- `D:\TLCN\LMS-API\doc\de-van-dap\f01-xac-thuc-phan-quyen.md`
