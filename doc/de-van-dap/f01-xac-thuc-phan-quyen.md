# Hồ Sơ Vấn Đáp Bảo Vệ Đồ Án Tốt Nghiệp — Phân Hệ F01: Xác Thực và Phân Quyền (LMS-API)

**Đề tài:** Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra chương trình đào tạo (Learning Outcomes Management and Analytics System)  
**Phân hệ:** F01 — Quản lý người dùng, Xác thực phiên toàn cầu và Phân quyền theo vai trò và phạm vi (US-01, US-02, US-03)  
**Tài liệu liên quan:** `doc/adr/0003-xac-thuc-phan-quyen-f01.md`, `doc/GiaiThich_CoSoDuLieu.md` (§4.1, §4.17, §4.29), `LMS-UI/doc/API-CONTRACT.md`  
**Nhóm sinh viên thực hiện:** Sinh viên Công nghệ thông tin (IT) & Sinh viên Kinh doanh số (IBS)

---

## Bối Cảnh và Mục Đích Tài Liệu

Tài liệu này chuẩn bị bộ câu hỏi và câu trả lời học thuật chuyên sâu phục vụ buổi bảo vệ Đồ án tốt nghiệp trước Hội đồng đánh giá. Nội dung được cấu trúc song song theo hai góc nhìn bổ trợ lẫn nhau:
- **Góc nhìn Sinh viên chuyên ngành Công nghệ thông tin (IT):** Tập trung vào giải pháp kiến trúc phần mềm (Software Architecture), các nguyên lý thiết kế hệ thống (SOLID, Clean Architecture), an toàn bảo mật thông tin (OWASP API Security, RFC Standards), cơ chế xử lý dữ liệu và độ tin cậy của mã nguồn (Testability & Code Coverage).
- **Góc nhìn Sinh viên chuyên ngành Kinh doanh số (IBS):** Tập trung vào quy trình nghiệp vụ giáo dục đại học (Academic Business Processes), tính pháp lý và chuẩn mực kiểm định chất lượng đào tạo quốc tế (AUN-QA, ABET), phân định thẩm quyền và trách nhiệm (Governance, Risk & Compliance - GRC), tối ưu hóa trải nghiệm người dùng (UX) và giá trị vận hành thực tiễn.

---

## Câu Hỏi 1: Bạn đã làm gì trong phân hệ F01 (Xác thực và Phân quyền)? (What did you do?)

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

Trong phân hệ F01, nhóm phát triển IT đã thiết kế và triển khai trọn vẹn kiến trúc bảo mật trung tâm cho toàn bộ backend `LMS-API`, tuân thủ nghiêm ngặt mô hình 3 tầng (Layered Architecture) và kiến trúc ADR 0002 / ADR 0003:

1. **Xác thực Nimbus OAuth2 Resource Server & Quản lý phiên không trạng thái (US-01):**
   - Tích hợp `spring-boot-starter-oauth2-resource-server` sử dụng JWT ký đối xứng thuật toán **HS256** với khóa bí mật `JWT_SECRET` (tối thiểu 32 bytes, fail-fast ngay khi khởi động).
   - Thiết kế JWT Token tối giản: Token chỉ chứa định danh người dùng `sub` (userId) và phiên bản bảo mật `tokenVersion`. Tuyệt đối không lưu quyền hạn (`roles`) hay phạm vi (`scope`) vào Token Payload.
   - Hiện thực cơ chế **Đăng xuất toàn cầu (Global Revocation)** và **Khóa tài khoản tức thời** thông qua việc tăng nguyên tử giá trị `tokenVersion` trong MongoDB bằng toán tử `$inc` của MongoTemplate (`UserRepositoryCustomImpl`), cách ly hoàn toàn tầng Service khỏi MongoTemplate.
   - Cấu hình tùy biến `BearerTokenResolver`: Tự động bỏ qua việc phân giải token trên danh sách endpoint công khai (`permitAll`: `/api/v1/auth/login`, `/actuator/health/**`, `/actuator/info`, OPTIONS), giải quyết triệt để lỗi 401 khi client gửi kèm token cũ hết hạn vào endpoint đăng nhập.

2. **Quản lý người dùng, Bảo vệ Quản trị viên & Chống tấn công (US-02):**
   - Xây dựng hệ thống CRUD người dùng với cơ chế phân trang chuẩn (zero-indexed `page`, `size` mặc định 20 tối đa 100) kèm bộ xác thực tham số ngoài biên (`PageableValidationUtils`, trả về HTTP 400 `VALIDATION_ERROR`).
   - Đảm bảo tính bất biến (`immutable`) của `username`; chuẩn hóa `username` và `email` (trim, lowercase theo `Locale.ROOT`); bắt lỗi trùng khóa `DuplicateKeyException` trả về HTTP 409 `CONFLICT`.
   - Xây dựng bean `PasswordPolicyValidator` đo độ dài thực tế theo byte UTF-8 (từ 8 đến 72 bytes), loại trừ hoàn toàn lỗ hổng cắt cụt mật khẩu (BCrypt truncation vulnerability) khi mật khẩu chứa ký tự tiếng Việt có dấu.
   - Phòng chống tấn công dò quét thời gian (Timing Attack / User Enumeration): Khi username không tồn tại, hệ thống vẫn ép buộc thực thi `passwordEncoder.matches` với chuỗi hash BCrypt giả định.
   - Thiết lập bộ ba chốt chặn bảo vệ Quản trị viên tối cao (ADMIN Protection Guards): Chặn ADMIN tự khóa tài khoản, chặn ADMIN tự gỡ vai trò của chính mình, và chặn thao tác khóa/hạ quyền của tài khoản `ADMIN` ở trạng thái `ACTIVE` duy nhất còn lại trong hệ thống (HTTP 422).
   - Kiểm tra tham chiếu tính hợp lệ của `scope` (`programIds`, `courseIds`, `sectionIds`) qua interface `ScopeValidationRepository` trước khi lưu vào CSDL (HTTP 422).

3. **Phân quyền truy cập tài nguyên Lớp học phần theo Vai trò và Phạm vi (US-03):**
   - Xây dựng document và repository chỉ đọc `CourseSection` và `CourseSectionRepository` (collection `course_sections` §4.17).
   - Hiện thực `ScopeAuthorizationService` và `ScopeAuthorizationServiceImpl` đóng gói toàn bộ ma trận phân quyền: 7 vai trò (`Role`) × 3 cấp độ phạm vi (`UserScope`) × 2 hành vi (`AccessType`: `READ`, `WRITE`).
   - Hiện thực quy tắc `lecturerIds` độc lập: Giảng viên có tên trong danh sách phân công giảng dạy của lớp học phần luôn có quyền READ và WRITE trên lớp đó, kể cả khi `scope` bị rỗng hoặc null (miễn là user vẫn giữ vai trò `LECTURER`).
   - Xử lý định nghĩa Strict Empty Scope: Scope chỉ rỗng khi `scope == null` HOẶC cả 3 mảng `programIds`, `courseIds`, `sectionIds` đều rỗng. Nếu chỉ có `programIds`, scope không rỗng nhưng do hạn chế F01 sẽ bị từ chối fail-closed (HTTP 403), không bị rơi vào nhánh đọc toàn hệ thống.
   - Hợp quyền đa vai trò (Multi-Role UNION): Người dùng sở hữu nhiều `roleAssignments` sẽ nhận được quyền hạn là phép hợp của tất cả các phân công.
   - Nạp quyền động từ CSDL mỗi request (Dynamic Authorization Reload): Thông tin vai trò và phạm vi được nạp tươi từ MongoDB vào `UserPrincipal` ở mỗi request mang Bearer token hợp lệ. Mọi thay đổi về phân quyền hoặc trạng thái tài khoản trong CSDL có hiệu lực ngay lập tức ở request tiếp theo mà không cần cấp lại token.
   - Tích hợp Spring Method Security `@PreAuthorize` kết hợp SpEL gọi `@scopeAuthorizationService.canAccessSection(...)` và xây dựng controller giả lập `ScopeAuthorizationTestController` trong `src/test/java/`.

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

Trong phân hệ F01, nhóm phát triển IBS tập trung vào việc chuẩn hóa quy trình nghiệp vụ đào tạo đại học, bảo đảm tính pháp lý của dữ liệu học thuật và nâng cao trải nghiệm người dùng:

1. **Phân định rõ ràng Thẩm quyền và Trách nhiệm (Governance & Role-Based Segregation of Duties):**
   - Trong môi trường đại học, việc nhập điểm và đánh giá chuẩn đầu ra là nghiệp vụ pháp lý nhạy cảm. Hệ thống phân định rạch ròi giữa hành vi Ghi (`WRITE`: nhập điểm, chỉnh sửa điểm, tạo bài đánh giá) và hành vi Đọc (`READ`: tra cứu danh sách lớp, xem bảng điểm, thống kê chuẩn đầu ra).
   - Chỉ có Giảng viên được phân công giảng dạy (`LECTURER` nằm trong `lecturerIds` hoặc trong `scope`) và Quản trị viên kỹ thuật (`ADMIN`) mới được phép thực hiện hành vi WRITE.
   - Các cấp quản lý chuyên môn như Trưởng chương trình (`PROGRAM_MANAGER`), Trưởng bộ môn (`DEPARTMENT_HEAD`), Chuyên viên bảo đảm chất lượng (`QA_OFFICER`) và Người quan sát (`VIEWER`) chỉ có quyền READ để phục vụ công tác thanh tra, giám sát, tuyệt đối không được tự ý can thiệp vào điểm số của lớp học phần nếu không có thẩm quyền giảng dạy.

2. **Tối ưu hóa quy trình phân công giảng dạy (Operational Agility via Independent `lecturerIds`):**
   - Trong thực tế quản lý đào tạo, các tình huống giảng viên dạy thay đột xuất, giảng viên thỉnh giảng hoặc nhiều giảng viên cùng phụ trách chung một lớp học phần diễn ra thường xuyên.
   - Quy tắc `lecturerIds` độc lập cho phép Phòng Đào tạo phân công giảng viên trực tiếp trên lớp học phần mà không cần phải thực hiện quy trình phức tạp điều chỉnh cây phân quyền tổng thể (`UserScope`) của giảng viên đó. Giảng viên được gán tên vào lớp lập tức có quyền mở sổ điểm và nhập điểm bình thường.

3. **Bảo đảm tính bảo mật không có độ trễ (Zero-latency Policy Enforcement):**
   - Khi một nhân sự nghỉ việc, chuyển công tác, hoặc khi phát hiện tài khoản có dấu hiệu bị lộ lọt mật khẩu, Quản trị viên thực hiện khóa tài khoản (`LOCKED`) hoặc gỡ quyền.
   - Nhờ cơ chế `tokenVersion` và nạp quyền động từ CSDL, quyết định hành chính này có hiệu lực ngay lập tức trong vòng 1 giây trên mọi thiết bị (máy tính văn phòng, điện thoại của nhân viên đó), triệt tiêu hoàn toàn rủi ro kẻ xấu lợi dụng thời gian sống còn lại của JWT (thường là 60 phút) để tiếp tục thao tác trái phép.

4. **Chuẩn hóa thông điệp lỗi và trải nghiệm người dùng (UX Error Standardization):**
   - Toàn bộ các phản hồi lỗi từ xác thực (401), phân quyền (403), dữ liệu không hợp lệ (400, 422) hay trùng lặp (409) đều được đóng gói theo cấu trúc chung `ErrorResponse` đã thống nhất với đội ngũ Frontend tại `LMS-UI/doc/API-CONTRACT.md`. Thông điệp lỗi rõ ràng bằng tiếng Việt giúp người dùng hiểu chính xác nguyên nhân bị từ chối mà không làm lộ cấu trúc hệ thống.

---

## Câu Hỏi 2: Vì sao phần này có mặt và đóng vai trò gì trong đề tài đo lường chuẩn đầu ra? (Why in thesis?)

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

1. **Giải quyết bài toán Kiểm soát truy cập mức bản ghi (Row-Level / Record-Level Access Control):**
   - Các hệ thống thông thường chỉ áp dụng RBAC đơn giản (ví dụ: kiểm tra `@PreAuthorize("hasRole('LECTURER')")`). Mô hình này hoàn toàn bất lực trong bài toán giáo dục: Mọi giảng viên đều có vai trò `LECTURER`, nhưng Giảng viên A không được phép nhập điểm cho lớp của Giảng viên B.
   - Phân hệ F01 thiết lập mô hình kết hợp ReBAC (Relationship-Based Access Control dựa trên quan hệ giảng dạy `lecturerIds`) và ABAC (Attribute-Based Access Control dựa trên thuộc tính phạm vi `UserScope`). `ScopeAuthorizationService` đóng vai trò là "Cổng gác an ninh" (Policy Enforcement Point - PEP) tập trung, bảo vệ hệ thống khỏi lỗ hổng BOLA (Broken Object Level Authorization - lỗ hổng số 1 trong OWASP Top 10 API Security).

2. **Bảo đảm tính toàn vẹn và không thể chối bỏ (Data Integrity & Non-repudiation):**
   - Dữ liệu kết quả đo lường chuẩn đầu ra (CLO/PI/PLO) được suy diễn từ điểm số từng câu hỏi của sinh viên. Nếu khâu xác thực danh tính và phân quyền nhập điểm bị lỏng lẻo, kẻ tấn công có thể tiêm dữ liệu điểm sai lệch, làm sai lệch toàn bộ báo cáo kiểm định chất lượng cấp khoa và cấp trường.
   - Việc ghi nhận nhật ký kiểm toán `audit_logs` ngay khi đăng nhập (`LOGIN`) và liên kết vết người dùng qua `X-Correlation-Id` đảm bảo khả năng truy vết nguồn gốc (Traceability) xuyên suốt hệ thống.

3. **Cung cấp nền tảng tái sử dụng vững chắc cho các phân hệ tiếp theo (F04, F05):**
   - Thay vì phải lặp lại logic kiểm tra quyền giảng viên ở hàng chục endpoint trong phân hệ Bài đánh giá (F04) và Nhập điểm (F05), các lập trình viên chỉ cần chú thích `@PreAuthorize("@scopeAuthorizationService.canAccessSection(authentication.principal, #sectionId, T(AccessType).WRITE)")` là hoàn thành việc bảo mật ở tầng nghiệp vụ.

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

1. **Đáp ứng các tiêu chuẩn kiểm định chất lượng giáo dục quốc tế (AUN-QA & ABET):**
   - Trong các bộ tiêu chuẩn kiểm định quốc tế như AUN-QA (Tiêu chuẩn 8: Đánh giá người học) và ABET (Tiêu chí 5: Đội ngũ giảng viên và quy trình đánh giá), quy trình đo lường chuẩn đầu ra chỉ được công nhận khi có cơ chế kiểm soát chất lượng nội bộ nghiêm ngặt.
   - Hội đồng kiểm định luôn thẩm định câu hỏi: *"Làm thế nào nhà trường bảo đảm rằng điểm số và mức độ đạt chuẩn đầu ra không bị can thiệp bởi những người không có trách nhiệm?"*. Phân hệ F01 chính là câu trả lời mang tính hệ thống cho yêu cầu này.

2. **Tuân thủ quy định bảo vệ quyền riêng tư học thuật (FERPA & Luật ATTTM):**
   - Điểm số và quá trình rèn luyện của sinh viên là dữ liệu cá nhân nhạy cảm. Sinh viên không được phép xem điểm tổng hợp của toàn lớp (`STUDENT` bị chặn truy cập tài nguyên lớp học phần), mà chỉ được xem kết quả đo lường cá nhân thông qua giao diện riêng.
   - Phân quyền chặt chẽ giúp trường đại học tránh khỏi các rủi ro pháp lý về việc làm lộ lọt thông tin học tập của người học.

3. **Bảo toàn tính liên tục trong quản trị hệ thống (System Governance Continuity):**
   - Chốt chặn ADMIN Guard bảo vệ hệ thống khỏi sai sót vận hành tai hại: Ngăn chặn tình trạng quản trị viên lỡ tay tự khóa chính mình hoặc hạ quyền của tài khoản Admin cuối cùng, dẫn đến việc hệ thống rơi vào trạng thái "vô chủ", tê liệt toàn bộ hoạt động quản lý.

---

## Câu Hỏi 3: Các tham số, cấu hình, thư viện từ đâu mà có? (Where do params/libs come from?)

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

Mọi tham số, cấu hình và thư viện được lựa chọn trong F01 đều dựa trên cơ sở tiêu chuẩn kỹ thuật quốc tế và tài liệu chính thức:

1. **`spring-boot-starter-oauth2-resource-server` & Nimbus JOSE JWT:**
   - Kế thừa từ bộ khung Spring Boot 4.1.0 và hệ sinh thái Spring Security 6/7; phiên bản được quản lý tập trung bởi Spring Boot Dependencies BOM.
   - Nimbus là thư viện đạt chuẩn khuyến nghị của OpenID Foundation và IETF RFC 7519 (JSON Web Token), RFC 6750 (Bearer Token Usage).

2. **Thuật toán ký HS256 và tham số `JWT_SECRET` tối thiểu 32 bytes (256 bits):**
   - Căn cứ theo quy định tại IETF RFC 7518 (JSON Web Algorithms - JWA) Mục 3.2: Thuật toán HMAC-SHA256 yêu cầu khóa bí mật phải có độ dài tối thiểu bằng độ dài đầu ra băm (256 bits = 32 bytes) để đảm bảo độ phức tạp chống lại các cuộc tấn công vét cạn (brute-force attack). Hệ thống thực hiện fail-fast ngay khi khởi động nếu secret không đủ độ dài.

3. **Thời gian sống của Access Token `app.security.jwt.ttl=60m`:**
   - Khuyến nghị an toàn của NIST SP 800-63B (Digital Identity Guidelines) về giới hạn thời gian hiệu lực của Bearer Token trong các ứng dụng web nghiệp vụ nội bộ.

4. **Ngưỡng độ dài mật khẩu 8–72 bytes UTF-8 trong `PasswordPolicyValidator`:**
   - **8 bytes:** Đáp ứng khuyến nghị tối thiểu của NIST SP 800-63B và OWASP Authentication Cheat Sheet.
   - **72 bytes:** Bắt nguồn trực tiếp từ hạn chế thuật toán cốt lõi của BCrypt (OpenBSD BCrypt implementation giới hạn xử lý tối đa 72 bytes mật khẩu đầu vào, phần dư thừa sau byte thứ 72 sẽ bị âm thầm bỏ qua).
   - **Đo bằng byte UTF-8:** Ký tự tiếng Việt có dấu (như 'ấ', 'đ', 'ệ') chiếm 2 đến 3 bytes trong UTF-8. Nếu đo bằng ký tự (`@Size`), một mật khẩu 30 ký tự tiếng Việt có thể lên tới 75 bytes, gây lỗ hổng cắt cụt mật khẩu. Do đó, việc đo chính xác theo byte UTF-8 là giải pháp kỹ thuật triệt để.

5. **`@EnableMethodSecurity` và SpEL expression:**
   - Cơ chế bảo mật phương thức chuẩn của Spring Security, hỗ trợ đánh giá quyền tiền điều kiện (`@PreAuthorize`) bằng Spring Expression Language (SpEL), cho phép liên kết trực tiếp với Spring Beans (`@scopeAuthorizationService.canAccessSection(...)`).

6. **Ngưỡng đo độ bao phủ JaCoCo >= 70%:**
   - Cấu hình qua plugin `jacoco-maven-plugin:0.8.15` (phiên bản tương thích hoàn toàn với Java 25 bytecode), thực thi kiểm tra tỷ lệ bao phủ dòng lệnh (Line Coverage) trên các gói mô-đun lõi xác thực và phân quyền (`service.impl`, `controller`, `filter`).

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

1. **Danh mục 7 vai trò người dùng trong hệ thống:**
   - Được trích xuất từ Quy chế tổ chức và hoạt động của Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCMUTE) và mô hình quản trị đào tạo theo chuẩn OBE:
     - `ADMIN`: Quản trị viên kỹ thuật hệ thống.
     - `PROGRAM_MANAGER`: Trưởng / Chủ nhiệm chương trình đào tạo (phụ trách CTĐT, không đồng nhất với Trưởng khoa).
     - `DEPARTMENT_HEAD`: Trưởng bộ môn (duyệt ma trận đề thi và phân công giảng dạy).
     - `QA_OFFICER`: Chuyên viên phòng Đảm bảo chất lượng (giám sát, kiểm định chuẩn đầu ra).
     - `LECTURER`: Giảng viên trực tiếp giảng dạy và đánh giá lớp học phần.
     - `VIEWER`: Thanh tra đào tạo, chuyên gia đánh giá ngoài (AUN-QA/ABET) với quyền quan sát độc lập.
     - `STUDENT`: Sinh viên theo học (người thụ hưởng kết quả đào tạo).

2. **Cấu trúc trường dữ liệu lớp học phần (`CourseSection` §4.17):**
   - Dựa trên tài liệu khảo sát CSDL đào tạo `doc/GiaiThich_CoSoDuLieu.md` §4.17 và schema `doc/database_import.js`:
     - `code`: Mã lớp học phần (ví dụ `SE401.T01`).
     - `courseId`: Học phần trong chương trình (ví dụ: Công nghệ phần mềm).
     - `syllabusId`: Đề cương chi tiết phiên bản áp dụng cho lớp.
     - `termId`: Học kỳ mở lớp (ví dụ: Học kỳ 1 năm học 2026-2027).
     - `lecturerIds`: Danh sách giảng viên được phân công giảng dạy chính thức từ phòng Đào tạo.

3. **Giới hạn kỹ thuật đã biết của F01 đối với `programIds`:**
   - Căn cứ vào lộ trình triển khai tính năng của đồ án: Ở bước F01, hệ thống chỉ hỗ trợ liên kết trực tiếp qua `sectionIds`, `courseIds` và `lecturerIds`. Việc phân rã từ CTĐT (`programIds`) xuống các lớp học phần cụ thể đòi hỏi các bảng liên kết phức tạp của phân hệ F02. Do đó, việc từ chối fail-closed đối với tài khoản chỉ có `programIds` là một quyết định nghiệp vụ thận trọng và có chủ đích nhằm ngăn chặn việc cấp quyền vượt cấp không có kiểm soát.

---

## Câu Hỏi 4: Nếu bỏ thành phần này đi thì hệ thống hoặc đề tài sẽ bị ảnh hưởng/phá vỡ ra sao? (What breaks if removed?)

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

1. **Nếu bỏ `ScopeAuthorizationService` và `@PreAuthorize`:**
   - Hệ thống lập tức mắc lỗi bảo mật **BOLA / IDOR (Broken Object Level Authorization)** — lỗ hổng bảo mật xếp hạng #1 trong OWASP Top 10 API Security Risks.
   - Bất kỳ người dùng nào có vai trò Giảng viên (`ROLE_LECTURER`) đều có thể gọi API nhập điểm hoặc sửa điểm của bất kỳ lớp học phần nào trong toàn trường chỉ bằng cách thay đổi giá trị `sectionId` trên đường dẫn HTTP request. Toàn bộ tính bảo mật của ứng dụng bị sụp đổ hoàn toàn.

2. **Nếu bỏ cơ chế Nạp quyền động từ CSDL (Dynamic Permission Reload) và lưu quyền trong JWT Token:**
   - Khi Quản trị viên thay đổi vai trò hoặc thu hẹp phạm vi của một người dùng, người dùng đó vẫn giữ nguyên các quyền hạn cũ trên hệ thống cho tới khi JWT token hết hạn (tối đa 60 phút).
   - Nếu tài khoản của một giảng viên bị nghi ngờ xâm nhập, quản trị viên không có cách nào tước quyền ngay lập tức nếu không có cơ chế đối soát CSDL và kiểm tra `tokenVersion`.

3. **Nếu bỏ quy tắc Strict Empty Scope:**
   - Khi một chuyên gia kiểm định hoặc thanh tra được cấp tài khoản `VIEWER` phụ trách riêng một ngành đào tạo (`programIds = ["IT_PROGRAM"]`), hệ thống nếu kiểm tra rỗng sơ sài sẽ nhận định đây là scope rỗng và vô tình cấp quyền cho tài khoản này xem dữ liệu của toàn bộ trường đại học (bao gồm các ngành Cơ khí, Điện tử, May mặc...). Đây là sự cố vi phạm phân vùng dữ liệu nghiêm trọng.

4. **Nếu bỏ `PasswordPolicyValidator` đo bằng byte UTF-8:**
   - Hệ thống sẽ đối mặt với lỗ hổng **BCrypt Truncation Attack**: Giả sử mật khẩu là `MậtKhẩuCựcKỳBảoMậtTiếngViệtRấtDài123456789`, phần vượt quá 72 bytes sẽ bị BCrypt bỏ qua mà không báo lỗi. Kẻ tấn công chỉ cần nhập 72 bytes đầu tiên kèm bất kỳ ký tự rác nào phía sau vẫn có thể đăng nhập thành công.

5. **Nếu bỏ các chốt chặn ADMIN Guard:**
   - Hai quản trị viên thao tác đồng thời hoặc một quản trị viên nhầm lẫn tự khóa tài khoản của chính mình có thể làm hệ thống mất sạch tài khoản quản trị hoạt động (`ACTIVE`). Hệ thống rơi vào trạng thái tê liệt quản trị và buộc kỹ sư dữ liệu phải can thiệp thủ công vào MongoDB để cứu dữ liệu.

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

1. **Nếu bỏ phân định rạch ròi giữa READ và WRITE:**
   - Xung đột thẩm quyền nghiêm trọng trong vận hành nhà trường: Các cán bộ kiểm định (`QA_OFFICER`) hoặc Trưởng chương trình đào tạo (`PROGRAM_MANAGER`) trong quá trình xem báo cáo có thể vô tình hoặc cố ý can thiệp vào bảng điểm của giảng viên. Điều này vi phạm nghiêm trọng Quy chế đào tạo của Bộ GD&ĐT về tính độc lập và trách nhiệm của giảng viên trực tiếp chấm thi.

2. **Nếu bỏ quy tắc `lecturerIds` độc lập:**
   - Quy trình phân công giảng viên dạy thay hoặc dạy phối hợp bị đình trệ: Phòng Đào tạo sẽ không thể phân công giảng viên một cách linh hoạt mà phải gửi văn bản yêu cầu Quản trị viên cập nhật lại cấu trúc phân quyền hệ thống cho từng giảng viên. Chi phí vận hành hành chính tăng vọt và làm chậm tiến độ công bố điểm cho sinh viên.

3. **Hậu quả trực tiếp đối với Đề tài tốt nghiệp:**
   - Trong buổi bảo vệ trước Hội đồng, nếu giảng viên phản biện phát hiện sinh viên này có thể sửa điểm lớp của sinh viên khác, hoặc tài khoản đã bị khóa vẫn tiếp tục gọi được API chấm điểm, đề tài sẽ bị **đánh trượt ngay lập tức (Fail)** do vi phạm các nguyên tắc an toàn dữ liệu cơ bản của một hệ thống quản lý đào tạo đại học.
   - Toàn bộ kết quả tính toán chuẩn đầu ra (CLO, PI, PLO) ở các phân hệ sau sẽ bị coi là vô giá trị vì dữ liệu đầu vào không bảo đảm được tính xác thực (Authenticity) và tính toàn vẹn (Integrity).

---

## Bảng Tóm Tắt Ma Trận Phân Quyền canAccessSection [ĐỀ XUẤT, chờ Q08]

| Vai trò (`Role`) | Hành vi Ghi (`WRITE`) trên Lớp | Hành vi Đọc (`READ`) trên Lớp | Điều kiện áp dụng & Ràng buộc Scope |
|---|---|---|---|
| `ADMIN` | **CHO PHÉP** (Toàn bộ) | **CHO PHÉP** (Toàn bộ) | Toàn quyền hệ thống, không phụ thuộc vào `scope`. |
| `LECTURER` | **CHO PHÉP** (Trong phạm vi) | **CHO PHÉP** (Trong phạm vi) | Thỏa mãn khi user còn role `LECTURER` VÀ: (1) `sectionId` ∈ `scope.sectionIds`, HOẶC (2) `courseId` ∈ `scope.courseIds`, HOẶC (3) `userId` ∈ `CourseSection.lecturerIds`. |
| `PROGRAM_MANAGER` | **TỪ CHỐI (403)** | **CHO PHÉP** (Trong phạm vi) | READ: `sectionId` ∈ `scope.sectionIds` HOẶC `courseId` ∈ `scope.courseIds`. Không có quyền WRITE. Nếu chỉ có `programIds` thì từ chối 403 (hạn chế F01). |
| `DEPARTMENT_HEAD` | **TỪ CHỐI (403)** | **CHO PHÉP** (Trong phạm vi) | READ: `sectionId` ∈ `scope.sectionIds` HOẶC `courseId` ∈ `scope.courseIds`. Không có quyền WRITE trên lớp. |
| `QA_OFFICER` | **TỪ CHỐI (403)** | **CHO PHÉP** (Trong phạm vi) | READ: `sectionId` ∈ `scope.sectionIds` HOẶC `courseId` ∈ `scope.courseIds`. Không có quyền WRITE. |
| `VIEWER` | **TỪ CHỐI (403)** | **CHO PHÉP** (Tùy Scope) | Tuyệt đối không có quyền WRITE. Nếu scope rỗng (`scope == null` hoặc 3 mảng rỗng) → READ toàn hệ thống. Nếu scope có `sectionIds`/`courseIds` → chỉ READ trong phạm vi đó. Nếu chỉ có `programIds` → TỪ CHỐI 403. |
| `STUDENT` | **TỪ CHỐI (403)** | **TỪ CHỐI (403)** | Từ chối 403 trên tài nguyên lớp học (chờ story NEW-03 sinh viên xem kết quả cá nhân). |
