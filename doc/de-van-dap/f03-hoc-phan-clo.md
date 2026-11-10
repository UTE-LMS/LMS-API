# Hồ Sơ Vấn Đáp Bảo Vệ Đồ Án Tốt Nghiệp — Phân Hệ F03: Học Phần, Đề Cương, CLO và Liên Kết CLO–PI (LMS-API)

**Đề tài:** Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra chương trình đào tạo (Learning Outcomes Management and Analytics System)  
**Phân hệ:** F03 — Quản lý Học phần, Vị trí học phần trong CTĐT (US-09), Đề cương theo phiên bản (US-10), Chuẩn đầu ra học phần CLO (US-11), và Liên kết CLO–PI (US-12), tích hợp Quản lý Học kỳ (F05a / US-18)  
**Tài liệu liên quan:** `doc/plan/F03-hoc-phan-clo.md`, `doc/plan/F05a-hoc-ky-lop-sinh-vien.md`, `doc/adr/0002-nen-tang-chung-step-00.md`, `doc/adr/0005-phan-quyen-chuong-trinh-dao-tao.md`, `doc/adr/0010-phan-quyen-de-cuong-theo-bo-mon-va-giang-vien.md` (ADR 0010 thay thế ADR 0009), `doc/GiaiThich_CoSoDuLieu.md` (§2 nguyên tắc 2 & 12, §4.6–4.7, §4.9–4.11, §4.16, §7), `LMS-UI/doc/API-CONTRACT.md`  
**Nhóm sinh viên thực hiện:** Sinh viên Công nghệ thông tin (IT) & Sinh viên Kinh doanh số (IBS)

---

## Bối Cảnh và Mục Đích Tài Liệu

Tài liệu này chuẩn bị bộ câu hỏi và câu trả lời học thuật chuyên sâu phục vụ buổi bảo vệ Đồ án tốt nghiệp trước Hội đồng đánh giá khoa học cho phân hệ **F03** và bước làm trước của **F05a (US-18: Học kỳ)**. Nội dung được cấu trúc song song theo hai góc nhìn bổ trợ mật thiết cho nhau:
- **Góc nhìn Sinh viên chuyên ngành Công nghệ thông tin (IT):** Tập trung vào thiết kế cơ sở dữ liệu NoSQL tối ưu hóa cho truy vấn phân tán, phân rã quan hệ Many-to-Many giữa Học phần và Phiên bản CTĐT thông qua collection trung gian `program_courses`, toàn vẹn tham chiếu cấp dịch vụ (Application-level Referential Integrity), kiểm soát truy cập phân tầng theo phạm vi quản trị (`ScopeAuthorizationService` - fail-closed), cơ chế bảo vệ xóa an toàn (Restrict Delete), xác thực tính toàn vẹn thời gian niên khóa giữa `terms` và `syllabi`, và đóng băng đề cương `CONFIRMED`.
- **Góc nhìn Sinh viên chuyên ngành Kinh doanh số (IBS):** Tập trung vào triết lý Giáo dục dựa trên chuẩn đầu ra (Outcome-Based Education - OBE), tính tái sử dụng và quản trị danh mục học phần cấp trường (Course Master Catalog Management), thiết kế cấu trúc chương trình đào tạo phân tầng, ràng buộc logic lộ trình học tập (Học phần tiên quyết và học trước), tính bất biến lịch sử của đề cương môn học theo từng niên khóa, khung thẩm định ma trận CLO–PI chuẩn mực, và nguyên tắc không giả mạo số liệu/chuẩn đầu ra khi chưa được Khoa phê duyệt.

---

## Câu Hỏi 1: Bạn đã làm gì trong phân hệ F03 (Đặc biệt là US-09: Quản lý học phần và gán học phần vào CTĐT)? (What did you do?)

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

Trong phân hệ F03, nhóm IT đã hiện thực hóa trọn vẹn User Story **US-09** (quản lý học phần và vị trí trong CTĐT) theo đúng chuẩn kiến trúc 3 tầng (ADR 0002) và chuẩn bảo mật Spring Security 6 với JWT phân quyền theo phạm vi (ADR 0005):

1. **Thiết kế Collection và Mô hình hóa Dữ liệu NoSQL:**
   - **`courses` (Master Catalog):** Lưu trữ danh mục học phần chuẩn toàn trường ([Course.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/document/Course.java)). Thiết lập Unique Index trên trường `code` (ví dụ: `OOPR230279`). Hỗ trợ tìm kiếm từ khóa không phân biệt hoa thường theo `code` và `name` thông qua Spring Data MongoDB Criteria API.
   - **`program_courses` (Junction Collection):** Liên kết Many-to-Many giữa Phiên bản CTĐT ([ProgramVersion.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/document/ProgramVersion.java)) và Học phần ([Course.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/document/Course.java)).
   - Thiết lập `@CompoundIndex(def = "{'programVersionId': 1, 'courseId': 1}", unique = true)` nhằm bảo đảm một học phần chỉ xuất hiện tối đa một lần trong cùng một phiên bản CTĐT ở cấp độ MongoDB engine.
   - Định nghĩa các Enum chuẩn: [CourseType.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/enums/CourseType.java) (`COMPULSORY`, `ELECTIVE`), [CourseCategory.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/enums/CourseCategory.java) (`FOUNDATION`, `MAJOR_FOUNDATION`, `SPECIALIZED`, `CAPSTONE_PROJECT`, `THESIS`).

2. **Hiện thực Tầng Dịch vụ (Service Layer) & Toàn vẹn tham chiếu:**
   - [CourseServiceImpl.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/service/impl/CourseServiceImpl.java): Xử lý CRUD cho danh mục học phần. Kiểm tra trùng mã học phần (HTTP 409 `COURSE_CODE_ALREADY_EXISTS`). Cơ chế bảo vệ xóa an toàn: chặn xóa học phần khi đã được gán vào `program_courses` (HTTP 422 `COURSE_HAS_PROGRAM_COURSES`) hoặc đã có trong ma trận đóng góp `course_pi_contributions` (HTTP 422 `COURSE_HAS_PI_CONTRIBUTIONS`).
   - [ProgramCourseServiceImpl.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/service/impl/ProgramCourseServiceImpl.java): Quản lý vị trí học phần trong CTĐT. Xác thực sự tồn tại của `programVersionId` và `courseId` (HTTP 404/422). Kiểm tra trùng lặp cặp khóa (HTTP 409 `PROGRAM_COURSE_ALREADY_EXISTS`).
   - **Xác thực học phần tiên quyết (`prerequisiteCourseIds`) và học phần học trước (`previousCourseIds`):** Duyệt danh sách mã tiên quyết và học trước, kiểm tra từng học phần phải tồn tại trong CSDL và không được là chính học phần đang gán (tự tham chiếu) (HTTP 422). Đồng thời kiểm tra xung đột loại trừ giữa tiên quyết và học trước (`PREREQUISITE_AND_PREVIOUS_CONFLICT`).

3. **Hiện thực API RESTful và Phân quyền Scope Authorization (ADR 0005):**
   - [CourseController.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/controller/CourseController.java) (`/api/v1/courses`): Theo mô hình chuẩn quản trị đại học (ADR 0010), danh mục Master Catalog `courses` thuộc quyền quản trị tập trung của `ADMIN` (Phòng Đào tạo) để đảm bảo tính nhất quán toàn trường và đồng bộ dữ liệu tín chỉ; thu hồi quyền tạo/sửa môn của `PROGRAM_MANAGER`. Tất cả người dùng xác thực được phép tra cứu (`GET`).
   - [ProgramCourseController.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/controller/ProgramCourseController.java) (`/api/v1/program-versions/{programVersionId}/courses`): Sử dụng `@PreAuthorize("@scopeAuthorizationService.canAccessProgramVersion(principal, #programVersionId)")` bảo đảm kiểm soát truy cập phân quyền chặt chẽ theo nguyên tắc fail-closed.

4. **Nhập danh mục học phần hàng loạt qua CSV (US-09/AC-4, `POST /api/v1/courses/import`):**
   - Viết parser CSV thuần Java tuân thủ chuẩn RFC 4180 (`CsvUtils.java`), tự động xử lý dấu nháy kép `""`, dấu phẩy trong tên môn, xuống dòng CRLF/LF và loại bỏ UTF-8 BOM.
   - Cơ chế xử lý **Best-effort**: Dòng hợp lệ được nạp vào MongoDB, dòng lỗi được ghi nhận số thứ tự dòng và nguyên nhân chi tiết trả về client để người dùng hiệu chỉnh.
   - Phân quyền nghiêm ngặt `@PreAuthorize("hasRole('ADMIN')")` đảm bảo chỉ Quản trị viên cấp trường có quyền import danh mục Master Catalog.

5. **Kiểm thử tự động toàn diện:**
   - Viết bộ kiểm thử Unit Tests và Bean Validation riêng biệt: `CsvUtilsTest`, `CourseServiceImplTest`, `ProgramCourseServiceImplTest`, `CourseValidationTest`.
   - Viết bài kiểm thử tích hợp thực tế [CourseIntegrationTest.java](file:///D:/Code/Project/LMS-API/src/test/java/com/hcmute/lms/lmsapi/controller/CourseIntegrationTest.java) chạy trên MongoDB Replica Set thật (`rs0`), bao phủ 100% các Acceptance Criteria: US-09/AC-1 (tạo học phần, trùng mã 409), US-09/AC-2 (gán vào CTĐT, trùng 409), US-09/AC-3 (học phần tiên quyết/học trước không hợp lệ 422), US-09/AC-4 (import CSV hàng loạt, best-effort và phân quyền ADMIN), kiểm tra RBAC, phạm vi scope rejection (HTTP 403), và vòng đời CRUD.

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

Dưới góc độ quản trị đào tạo và kinh doanh số, US-09 giải quyết bài toán cốt lõi về **tối ưu hóa tài nguyên học thuật và quản trị cấu trúc chương trình đào tạo**:

1. **Chuẩn hóa Danh mục Học phần Cấp Trường (Master Catalog Management):**
   - Trong một trường đại học quy mô lớn, một học phần (ví dụ: *Toán cao cấp*, *Lập trình hướng đối tượng*, *Triết học Mác - Lênin*) được giảng dạy cho nhiều ngành đào tạo khác nhau.
   - Việc tách riêng danh mục `courses` giúp nhà trường quản lý tập trung mã học phần, tên học phần tiếng Việt/tiếng Anh, và số tín chỉ cơ bản. Tránh việc mỗi khoa tự tạo một mã môn học riêng gây phân mảnh dữ liệu đào tạo.

2. **Tùy biến Vị trí và Vai trò Môn học theo Từng Chương trình Đào tạo (`program_courses`):**
   - Cùng một môn học `OOPR230279`, khi đưa vào CTĐT ngành Công nghệ thông tin có thể là học phần **Bắt buộc** (`COMPULSORY`) thuộc khối **Cơ sở ngành** (`MAJOR_FOUNDATION`) học ở học kỳ 3; nhưng khi đưa vào CTĐT ngành Thương mại điện tử có thể là môn **Tự chọn** (`ELECTIVE`) học ở học kỳ 5.
   - Bảng `program_courses` ghi nhận chính xác ngữ cảnh đào tạo này (`semester`, `credits`, `type`, `category`).

3. **Thiết lập Chuỗi Tiên Quyết (Prerequisite Dependency Graph):**
   - Đảm bảo tính khoa học của lộ trình học tập theo tiêu chuẩn kiểm định quốc tế (AUN-QA). Sinh viên không thể học *Kiến trúc phần mềm* nếu chưa qua *Lập trình hướng đối tượng*.
   - Ràng buộc nghiệp vụ ở US-09/AC-3 ngăn ngừa sai lệch dữ liệu sư phạm ngay từ khâu thiết kế CTĐT: cấm một môn học tự làm tiên quyết cho chính nó (Self-dependency Loop) và cấm gắn mã môn học ma (Ghost Prerequisites).

---

## Câu Hỏi 2: Vì sao tách rời `courses` (Master Catalog) và `program_courses` (Curriculum Placement) mà không nhúng thẳng thông tin vào một Collection duy nhất?

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

1. **Chuẩn hóa Dữ liệu (Database Normalization) & Chống Dư Thừa:**
   - Quan hệ giữa `courses` và `program_versions` là quan hệ Nhiều - Nhiều ($N - N$). Một phiên bản CTĐT có khoảng 40–50 học phần; ngược lại một học phần có thể xuất hiện trong 10–20 phiên bản CTĐT khác nhau qua các niên khóa và các ngành đào tạo.
   - Nếu nhúng danh sách CTĐT vào trong `courses` (Embedded Array): Tài liệu `courses` sẽ phình to không giới hạn (Unbounded Array Anti-pattern), tiềm ẩn rủi ro ghi đồng thời (Concurrency Write Conflicts) khi nhiều Trưởng bộ môn/Ban biên soạn cùng cập nhật CTĐT của mình.
   - Nếu nhúng chi tiết môn học vào trong từng `program_versions`: Dữ liệu tên môn, mã môn bị nhân bản hàng chục lần. Khi nhà trường cần cập nhật tên môn học (ví dụ: đổi tên môn *Kỹ thuật lập trình* thành *Nhập môn lập trình*), hệ thống phải quét và cập nhật hàng trăm CTĐT, vi phạm tính nhất quán dữ liệu (Data Inconsistency Risk).

2. **Phân rã Quyền Hạn (Separation of Concerns & Access Control):**
   - Master Catalog `courses` thuộc quyền sở hữu cấp Trường / Phòng Đào tạo (`ADMIN` quản lý chung).
   - Vị trí môn học `program_courses` thuộc quyền biên soạn của Ban điều hành ngành đào tạo (`PROGRAM_MANAGER` theo scope `programIds`).
   - Việc tách rời 2 collection phản ánh chính xác mô hình phân quyền dữ liệu (Row-level / Scope-level Security) theo ADR 0005.

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

1. **Tối ưu hóa Chi phí Vận hành và Chia sẻ Nguồn lực (Resource Sharing):**
   - Việc dùng chung danh mục học phần giúp Nhà trường dễ dàng mở các lớp học phần ghép (Combined Classes) giữa các ngành có cùng môn đại cương hoặc cơ sở khối ngành, tối ưu hóa sĩ số giảng đường và chi phí thù lao giảng viên.
2. **Linh hoạt thích ứng với các Thay đổi Khung Đào tạo:**
   - Khi tái cấu trúc một ngành học (sinh ra phiên bản mới `K2024` thay cho `K2023`), người quản lý chương trình chỉ cần thiết lập danh sách liên kết mới trên `program_courses` mà không làm xáo trộn kho học phần gốc đã được vận hành ổn định.

---

## Câu Hỏi 3: Vì sao dùng Compound Unique Index `(programVersionId, courseId)` trên MongoDB và kiểm tra tham chiếu ở tầng Service thay vì dùng Foreign Key constraint?

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

1. **Đặc thù của Cơ sở dữ liệu NoSQL (MongoDB):**
   - MongoDB là cơ sở dữ liệu hướng tài liệu (Document-oriented NoSQL), không có cơ chế RDBMS Foreign Key Constraint kiểu `ON DELETE RESTRICT` hay `FOREIGN KEY REFERENCES`.
   - Do đó, việc bảo đảm toàn vẹn tham chiếu bắt buộc phải chia làm 2 lớp:
     - **Tầng CSDL (Engine-level Invariant):** Tạo `@CompoundIndex(def = "{'programVersionId': 1, 'courseId': 1}", unique = true)`. Chỉ mục này bảo đảm tính Idempotent và triệt tiêu xung đột ghi song song (Race Condition / Concurrency). Nếu có 2 request đồng thời gán cùng một môn học vào cùng một phiên bản CTĐT, MongoDB sẽ ném `DuplicateKeyException` (E11000), hệ thống bắt lại và chuyển thành HTTP 409 `PROGRAM_COURSE_ALREADY_EXISTS`.
     - **Tầng Ứng dụng (Application-level Integrity):** Thực hiện kiểm tra khóa ngoại trước khi ghi (Kiểm tra `programVersionRepository.existsById(...)`, `courseRepository.existsById(...)`). Nếu khóa tham chiếu không tồn tại, trả về HTTP 404/422 ngay tại tầng service mà không cần gửi query ghi xuống DB.

2. **Cơ chế Xóa An Toàn (Restrict Delete Pattern):**
   - Khi xóa một học phần trong `CourseServiceImpl.deleteCourse(id)`, hệ thống kiểm tra:
     1. `programCourseRepository.existsByCourseId(id)` $\to$ Nếu có, chặn bằng ngoại lệ `COURSE_HAS_PROGRAM_COURSES` (HTTP 422).
     2. `coursePiContributionRepository.existsByCourseId(id)` $\to$ Nếu có, chặn bằng ngoại lệ `COURSE_HAS_PI_CONTRIBUTIONS` (HTTP 422).
   - Cơ chế này bảo vệ dữ liệu chống mồ côi (Orphan Records), tương đương hoàn toàn với `ON DELETE RESTRICT` trong hệ CSDL quan hệ.

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

- Trong quản lý đào tạo, việc vô tình xóa một môn học đang nằm trong khung chương trình của sinh viên đang học là một lỗi thảm họa (Disaster Error), làm sụp đổ toàn bộ bảng điểm, ma trận chuẩn đầu ra và kế hoạch mở lớp.
- Ràng buộc này đảm bảo một nguyên tắc quản trị dữ liệu sống còn: **Dữ liệu danh mục đã đưa vào sử dụng trong chương trình thì không được phép xóa vật lý**, chỉ có thể ngưng sử dụng hoặc điều chỉnh ở các phiên bản tiếp theo.

---

## Câu Hỏi 4: Kiểm tra điều kiện học phần tiên quyết (`prerequisiteCourseIds`) và học phần học trước (`previousCourseIds`) được thiết kế như thế nào? Tác động gì nếu bỏ qua kiểm tra này?

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

1. **Thuật toán Xác thực tại `ProgramCourseServiceImpl`:**
   - Khi nhận danh sách `prerequisiteCourseIds` và `previousCourseIds`:
     - **Kiểm tra tự tham chiếu (Self-reference):** Chặn không cho học phần tự tham chiếu chính nó làm tiên quyết hoặc học phần học trước (ném ngoại lệ `BusinessRuleViolationException` HTTP 422: `PREREQUISITE_SELF_REFERENCE`, `PREVIOUS_COURSE_SELF_REFERENCE`).
     - **Kiểm tra tính tồn tại (Existence Validation):** Kiểm tra từng ID học phần tham chiếu trong `courseRepository`; nếu có bất kỳ ID nào không tồn tại, trả về lỗi HTTP 422 (`PREREQUISITE_NOT_FOUND`, `PREVIOUS_COURSE_NOT_FOUND`).
     - **Kiểm tra xung đột tiên quyết và học trước (Mutual Exclusivity / Conflict Validation):** Một học phần không thể đồng thời vừa là môn tiên quyết (bắt buộc đậu) vừa là môn học trước (chỉ cần đã học) của cùng một môn học. Nếu tồn tại bất kỳ học phần nào nằm trong cả hai danh sách, hệ thống ném ngoại lệ `PREREQUISITE_AND_PREVIOUS_CONFLICT` (HTTP 422).
2. **Hậu quả nếu bỏ qua kiểm tra:**
   - **Treo thuật toán xếp thời khóa biểu / Đăng ký môn học:** Khi sinh viên đăng ký môn học, hệ thống portal sẽ duyệt đồ thị tiên quyết (Dependency Graph Traversal). Nếu tồn tại Self-loop ($A \to A$) hoặc chu trình khép kín ($A \to B \to A$), thuật toán đệ quy (DFS/BFS) sẽ bị tràn ngăn xếp (StackOverflowError) hoặc vòng lặp vô tận (Infinite Loop).
   - **Dữ liệu rác (Corrupted References):** Nếu lưu các ID học phần không tồn tại, giao diện Portal của sinh viên sẽ hiển thị lỗi màn hình trắng (White Screen of Death / NullPointerException) khi cố gắng render danh sách môn tiên quyết hoặc môn học trước.

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

- Học phần tiên quyết là cấu trúc xương sống định hình tiến độ đào tạo của sinh viên.
- Nếu không kiểm tra nghiêm ngặt, sinh viên có thể đăng ký học các môn chuyên ngành phức tạp (ví dụ: *Học máy chuyên sâu*) mà chưa hề có kiến thức nền tảng (như *Giải tích*, *Xác suất thống kê*). Điều này dẫn tới tỷ lệ rớt môn tăng vọt, vi phạm tiêu chuẩn bảo đảm chất lượng của nhà trường.

---

## Câu Hỏi 5: Phân hệ F03 được phân quyền (RBAC & Scope Authorization) như thế nào theo chuẩn ADR 0005?

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

1. **Phân cấp Thẩm quyền Rõ ràng:**
   - **Danh mục Master `courses` và `terms`:** Là tài nguyên dùng chung toàn trường, do đó các thao tác `POST` (Tạo mới), `PUT` (Cập nhật), `DELETE` (Xóa) chỉ dành riêng cho `ADMIN` (Phòng Đào tạo) theo ADR 0010. `PROGRAM_MANAGER` không có quyền tạo/sửa môn học trong kho tổng mà chỉ gán môn vào CTĐT (`program_courses`). Thao tác tra cứu `GET` mở cho tất cả người dùng hợp lệ trong hệ thống (`@PreAuthorize("isAuthenticated()")`).
   - **Gán học phần vào CTĐT `program_courses`:** Là tài nguyên gắn liền với một phiên bản CTĐT cụ thể. Do đó, các endpoint tại `/api/v1/program-versions/{programVersionId}/courses` được bảo vệ bằng SpEL:  
     `@PreAuthorize("@scopeAuthorizationService.canAccessProgramVersion(principal, #programVersionId)")`
   - **Thẩm định liên kết CLO-PI:** Được phân quyền chặt chẽ cho vai trò `QA_OFFICER` theo phạm vi ngành đào tạo (`programIds`).
   - **Phân quyền đề cương và chuẩn đầu ra theo ADR 0010 (thay thế ADR 0009):** Admin gán danh mục môn cho Trưởng bộ môn (`DEPARTMENT_HEAD`). Trưởng bộ môn phân công môn cho Giảng viên (`LECTURER`) biên soạn đề cương và CLO ở trạng thái `DRAFT`. Trưởng bộ môn là người thẩm định và duyệt `CONFIRMED`. Trưởng ngành (`PROGRAM_MANAGER`) chỉ thiết lập liên kết CLO–PI (`clo_pi_links`) cho ngành của mình. Xem chi tiết tại Câu hỏi 11.
2. **Cơ chế Fail-Closed (Đóng mặc định):**
   - Nếu `principal` không có quyền (ví dụ `STUDENT`, `LECTURER`), hoặc `PROGRAM_MANAGER` cố tình can thiệp vào `programVersionId` của một ngành khác ngoài danh sách `scope.programIds` được cấp phép, Spring Security sẽ chặn lại ngay tại tầng Filter và trả về HTTP 403 `FORBIDDEN`.

---

## Câu Hỏi 6: Phân hệ F03 (Học phần, đề cương, CLO, liên kết CLO–PI) và phân hệ F05a (Học kỳ) đã được hiện thực như thế nào? Vì sao quản lý học kỳ (`terms`) phải được làm trước đề cương môn học (`syllabi`)?

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

1. **Thực hiện trước module Quản lý Học kỳ `terms` của F05a (US-18, Issue #28):**
   - **Mô hình hóa NoSQL & Ràng buộc toàn vẹn:** Tạo collection `terms` ([Term.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/document/Term.java)) với các trường `code` (Unique Index), `academicYear`, `startDate`, `endDate`, và `status` ([TermStatus.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/enums/TermStatus.java) gồm `OPEN`, `SCORE_LOCKED`, `CLOSED`).
   - **Xác thực logic thời gian (US-18/AC-1):** Tại [TermServiceImpl.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/service/impl/TermServiceImpl.java), kiểm tra `startDate <= endDate` (ném HTTP 422 `BUSINESS_RULE_VIOLATION` nếu vi phạm), kiểm tra chống trùng mã học kỳ (HTTP 409 `CONFLICT`).
   - **Cơ chế Khóa sổ điểm & Mở lại (US-18/AC-2 & AC-3):** Hỗ trợ đổi trạng thái sang `SCORE_LOCKED`. Khi chuyển trạng thái từ `SCORE_LOCKED` quay lại `OPEN`, bắt buộc phải cung cấp lý do giải trình (`reason` không được rỗng/trống). Lưu ý: vế "từ chối nhập/sửa điểm khi học kỳ bị khóa" của US-18/AC-2 **chờ F05b** (chưa có mã nhập điểm), nên AC-2 chưa hoàn thành.
   - **Bảo vệ toàn vẹn xóa (Restrict Delete):** Chặn xóa học kỳ khi đang được đề cương môn học áp dụng (`existsByEffectiveFromTermIdOrEffectiveToTermId`) hoặc đang có lớp học phần mở trong kỳ (`existsByTermId`) với HTTP 422.

2. **Hiện thực trọn vẹn các User Story cốt lõi của F03 (US-10, US-11, US-12):**
   - **US-10 (Đề cương môn học - `syllabi` / Issue #21):**
     - Nhờ có collection `terms` được triển khai trước, [SyllabusServiceImpl.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/service/impl/SyllabusServiceImpl.java) đã tích hợp `TermRepository` để giải quyết trọn vẹn **US-10/AC-2**: kiểm tra sự tồn tại của `effectiveFromTermId` và `effectiveToTermId` (HTTP 404 nếu không tìm thấy), đồng thời đối chiếu thời gian hiệu lực bảo đảm `effectiveToTermId` không được diễn ra trước `effectiveFromTermId` (HTTP 422 nếu niên khóa kết thúc đi trước niên khóa bắt đầu).
     - Đảm bảo tính bất biến: Đề cương ở trạng thái `CONFIRMED` sẽ khóa không cho sửa đổi CLO của nó (US-10/AC-3).
   - **US-11 (Chuẩn đầu ra học phần - `clos` / Issue #22):**
     - Quản lý danh mục CLO gắn theo từng đề cương cụ thể ([Clo.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/document/Clo.java)), mã định dạng `^CLO[0-9]+$`, thang đo Bloom cấp độ 1–6 (US-11/AC-1 & AC-3).
     - Ràng buộc Unique `(syllabusId, code)` ở cấp MongoDB engine: Chặn trùng mã CLO trong cùng một đề cương (HTTP 409), nhưng cho phép cùng mã ở hai đề cương khác nhau (US-11/AC-2).
     - Trường `measurement` để `null` cho tới khi Khoa phê duyệt quy tắc đo lường (US-11/AC-4).
   - **US-12 (Liên kết CLO–PI và Ma trận CLO × PI / Issue #23):**
     - Tạo collection `clo_pi_links` ([CloPiLink.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/document/CloPiLink.java)), kiểm tra tính tương thích giữa CLO và PI: `piId` bắt buộc phải thuộc về đúng `programVersionId` của liên kết (HTTP 422), chặn trùng lặp cặp `(cloId, piId)` với HTTP 409 (US-12/AC-1 & AC-2).
     - Cung cấp API ma trận 2 chiều CLO × PI (`GET /api/v1/syllabi/{syllabusId}/clo-pi-matrix?programVersionId=...`) hiển thị trạng thái liên kết và thẩm định từng ô ma trận (US-12/AC-4).
     - Thẩm định pháp lý: Cho phép vai trò `QA_OFFICER` cập nhật trạng thái thẩm định sang `VERIFIED` kèm ghi nhận người duyệt và thời điểm (US-12/AC-3).

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

1. **Vì sao phải làm trước Quản lý Học kỳ `terms` của F05a?**
   - Trong quản trị đại học theo chuẩn kiểm định OBE, một đề cương môn học (Syllabus) không tồn tại độc lập mà luôn gắn với một **khung thời gian áp dụng pháp lý** (Thời điểm bắt đầu áp dụng từ học kỳ nào, kết thúc ở học kỳ nào).
   - Nếu không có thực thể `terms` quản lý niên khóa và mốc thời gian chuẩn hóa trước, việc khai báo `effectiveFromTermId` và `effectiveToTermId` trong đề cương sẽ chỉ là những chuỗi mã vô nghĩa, không thể kiểm tra được nghịch lý logic (như đề cương có kỳ hết hạn lại đi trước kỳ ban hành).
   - Do đó, việc triển khai `terms` trước là điều kiện tiên quyết (Prerequisite Invariant) để đảm bảo tính hợp lệ của dữ liệu sư phạm trước khi bất kỳ đề cương nào được lưu vào hệ sinh thái.

2. **Ý nghĩa kiểm soát chất lượng đào tạo của chuỗi F03:**
   - Chuỗi nghiệp vụ `Học phần -> Đề cương -> CLO -> Liên kết CLO-PI` tạo thành một hành lang kiểm soát chất lượng chặt chẽ:
     - Giảng viên không thể tự ý bịa đặt chuẩn đầu ra hay đưa các yêu cầu vượt quá khung Bloom quy định.
     - Sinh viên được bảo vệ quyền lợi thông qua nguyên tắc đóng băng đề cương `CONFIRMED`.
     - Nhà trường kiểm soát được 100% việc câu hỏi thi đo lường đúng chỉ số PI đã cam kết trong chương trình đào tạo.

---

## Câu Hỏi 7: Vì sao Chuẩn đầu ra học phần (CLO) phải được nhận diện bằng `_id` thay vì dùng mã nghiệp vụ `code`?

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

1. **Phạm vi Không gian Tên Cục bộ (Local Namespace Scope):**
   - Mã CLO (như `CLO1`, `CLO2`) không phải là mã định danh toàn cục (Globally Unique Identifier).
   - Trong cùng một trường đại học, hàng trăm học phần khác nhau đều có `CLO1` (ví dụ: `CLO1` của môn *Lập trình hướng đối tượng* và `CLO1` của môn *Cơ sở dữ liệu*).
   - Thậm chí trong cùng một học phần, hai phiên bản đề cương khác nhau (ví dụ: `version 2023.1` và `version 2024.1`) đều có `CLO1` nhưng định nghĩa hai chuẩn năng lực hoàn toàn khác nhau.
2. **Toàn vẹn Khóa Ngoại NoSQL:**
   - Nếu dùng `code` làm khóa tham chiếu, các collection liên quan như `clo_pi_links`, `questions`, `outcomeMappings`, `student_results` sẽ bị xung đột hoặc phải lưu khóa phức hợp cồng kềnh `(courseId, syllabusVersion, cloCode)`.
   - Sử dụng MongoDB `_id` (ObjectId 12-byte bất biến) làm Primary Key cho phép trỏ chính xác tuyệt đối tới định nghĩa CLO của đúng phiên bản đề cương mà không bao giờ bị nhầm lẫn. Cặp chỉ mục duy nhất `@CompoundIndex(def = "{'syllabusId': 1, 'code': 1}", unique = true)` bảo đảm trong phạm vi một đề cương không bao giờ có 2 CLO trùng mã (US-11/AC-2).

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

- Trong thiết kế sư phạm OBE, CLO là đơn vị đo lường cơ bản nhất của một học phần.
- Việc nhận diện bằng `_id` giúp hệ thống truy vết chính xác sinh viên học ở niên khóa nào, theo đề cương phiên bản nào, và đạt chuẩn đầu ra theo đúng cam kết chuẩn đầu ra tại thời điểm họ học, bảo đảm tính minh chứng phục vụ kiểm định chất lượng (AUN-QA, ABET).

---

## Câu Hỏi 8: Vì sao Đề cương môn học (`syllabi`) phải được phiên bản hóa và áp dụng cơ chế đóng băng (Freeze / Immutability) khi ở trạng thái `CONFIRMED`?

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

1. **Bảo vệ Tính Bất Biến của Dữ liệu Lịch sử (Historical Data Immutability):**
   - Khi một đề cương đã chuyển sang `CONFIRMED`, nó trở thành căn cứ pháp lý để tổ chức giảng dạy, ra đề thi và đo lường điểm số sinh viên.
   - Nếu cho phép sửa hoặc xóa CLO của một đề cương đã `CONFIRMED`, các kỳ thi đã diễn ra hoặc bảng điểm của các học kỳ trước tham chiếu tới CLO đó sẽ bị sai lệch, mồ côi hoặc thay đổi kết quả đo lường trong quá khứ.
2. **Cơ chế Ngăn Chặn ở Tầng Dịch vụ (US-10/AC-3):**
   - Tại `CloServiceImpl`, mọi hành vi `createClo`, `updateClo`, `deleteClo` đều kiểm tra `syllabus.getStatus() == SyllabusStatus.CONFIRMED`.
   - Nếu phát hiện đề cương đã `CONFIRMED`, hệ thống lập tức chặn lại và ném `BusinessRuleViolationException` (HTTP 422), buộc người quản lý phải tạo một phiên bản đề cương mới (`Syllabus` mới với version kế tiếp).

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

- Đề cương môn học là "hợp đồng học tập" giữa Nhà trường, Giảng viên và Sinh viên.
- Việc đóng băng đề cương đảm bảo tính khách quan trong đánh giá chuẩn đầu ra. Giảng viên không thể tùy tiện thay đổi chuẩn đầu ra giữa chừng để "hợp thức hóa" kết quả thi của sinh viên. Mọi điều chỉnh về mục tiêu đào tạo bắt buộc phải thông qua quy trình tái thẩm định và ban hành phiên bản đề cương mới.

---

## Câu Hỏi 9: Vì sao cần Collection trung gian `clo_pi_links` riêng biệt mà không để giảng viên tự gán PI trực tiếp cho từng câu hỏi kiểm tra?

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

1. **Mô hình Khung Thẩm Định Trước (Pre-approved Governance Matrix):**
   - Nếu không có bảng liên kết `clo_pi_links`, khi nhập ngân hàng câu hỏi đề thi, giảng viên có thể chọn bất kỳ PI nào trong toàn bộ chương trình đào tạo để gán cho một câu hỏi.
   - Khi đó, hệ thống không thể kiểm soát tính hợp lệ sư phạm ở tầng dữ liệu.
2. **Ràng buộc Kiểm tra Hợp Lệ Đa Chiều (Cross-dimensional Validation):**
   - `clo_pi_links` lưu trữ bộ 4 trường: `(programVersionId, syllabusId, cloId, piId)`.
   - Thuật toán tại `CloPiLinkServiceImpl` kiểm tra: `cloId` phải thuộc về đúng `syllabusId`, và `piId` phải thuộc về đúng `programVersionId` (US-12/AC-2).
   - Khi giảng viên ra câu hỏi ở phân hệ F04/F05b, API chỉ cho phép chọn cặp (CLO, PI) đã tồn tại trong danh sách `clo_pi_links` đã được phê duyệt (`verification.status = VERIFIED`). Bỏ qua kiểm tra này sẽ dẫn tới việc một câu hỏi đo lường một chỉ số PI không hề liên quan gì đến nội dung học phần.

---

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

- **Nguyên tắc Đồng bộ Sư phạm (Constructive Alignment) của GS. John Biggs:** Mục tiêu bài thi (Assessment Tasks) phải đo Chuẩn đầu ra học phần (CLO), và CLO phải đóng góp trực tiếp vào Chỉ số hiệu năng (PI) của CTĐT đã được Hội đồng Khoa học phê duyệt.
- `clo_pi_links` chính là bản số hóa "Ma trận ánh xạ chuẩn đầu ra môn học vào chuẩn đầu ra chương trình". Giảng viên giảng dạy môn học không có thẩm quyền tự ý thay đổi mối liên kết này, mà phải tuân thủ tuyệt đối khung ma trận do Bộ môn và Khoa ban hành.

---

## Câu Hỏi 10: Báo cáo chi tiết các nội dung CHƯA ĐƯỢC XÁC NHẬN (chưa làm) trong đợt này và lý do vì sao?

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin & Kinh doanh số:

Tuân thủ tuyệt đối **Quy tắc cứng số 2 (Không bịa minh chứng/dữ liệu giả)** và **Quy tắc số 8 (Bất biến nghiệp vụ & Xử lý câu hỏi chưa xác nhận)** trong `CLAUDE.md`, nhóm sinh viên chủ động dừng và báo cáo rõ các hạng mục chưa làm do thiếu xác nhận chính thức từ Hội đồng/Khoa:

1. **Chưa làm: Seed nội dung văn bản gốc của CLO và Đề cương chính thức (Chờ Q02)**
   - **Lý do:** Khoa chưa cung cấp tệp đề cương môn học chính thức có đóng dấu duyệt.
   - **Cách xử lý:** Hệ thống mở trường `contentOriginal` để người dùng nhập thật qua API hoặc giao diện, tuyệt đối không tự ý viết các câu chữ CLO giả định từ trí nhớ vào dữ liệu seed chính thức. Các bài kiểm thử tự động chỉ dùng dữ liệu giả lập có nhãn rõ ràng `[DỮ LIỆU THỬ NGHIỆM]`.
2. **Chưa làm: Tỷ trọng mặc định (`defaultWeight`) và Ngưỡng đạt mặc định (`defaultPassThreshold`) của CLO (Chờ Q12)**
   - **Lý do:** Khoa chưa ban hành văn bản quy định tỷ trọng và điểm sàn đạt CLO (ví dụ có bắt buộc đạt mọi CLO hay không, ngưỡng 50% hay 60%).
   - **Cách xử lý:** Trường `clos.measurement` được để `null` theo mặc định (US-11/AC-4). Khi Khoa ban hành quy chế chính thức, người quản trị có thể cập nhật qua API với giá trị hợp lệ trong đoạn $[0, 1]$.
3. **Chưa làm: Tự động gán sẵn bảng ánh xạ CLO–PI vào cơ sở dữ liệu mẫu (Chờ Q14)**
   - **Lý do:** Khoa chưa phê duyệt bảng ma trận ánh xạ CLO–PI của các môn thử nghiệm.
   - **Cách xử lý:** Nhóm xây dựng đầy đủ API thiết lập `clo_pi_links`, ma trận CLO × PI và chức năng phê duyệt của `QA_OFFICER`, nhưng không đưa vào seed các liên kết giả mạo khi chưa có quyết định của Khoa.
4. **Chưa làm: Thuật toán quy đổi trọng số cứng `weight` trong liên kết CLO-PI (Chờ Q22)**
   - **Lý do:** Công thức tính toán tổng hợp gián tiếp theo phương pháp `FROM_CLOS` vẫn đang trong giai đoạn dự thảo.
   - **Cách xử lý:** Trường `weight` trong `clo_pi_links` được phép mang giá trị `null` (US-12/AC-5). Hệ thống không ép buộc tính toán số học khi quy tắc chưa được chuẩn hóa.
5. **Chưa làm: Module Lớp học phần (`course_sections`) và Nhập sinh viên/Ghi danh (US-19, US-20 của F05a)**
   - **Lý do:** Đợt này chỉ thực hiện trước phần Quản lý học kỳ `terms` (US-18) để đáp ứng ràng buộc cho `syllabi` của F03. Phần sinh viên và ghi danh còn phụ thuộc vào quyết định của Khoa về việc sử dụng mã sinh viên thật hay mã ẩn danh `studentRef` (Q09) để đảm bảo quyền riêng tư.

---

## Câu Hỏi 11: Phân quyền theo phạm vi (scope) cho việc tạo môn học, phân công biên soạn và phê duyệt đề cương, CLO và liên kết CLO–PI được thiết kế thế nào? (ADR 0010 — THAY THẾ TOÀN DIỆN ADR 0009)

**Trạng thái:** Đã chính thức thông qua kiến trúc tại ADR 0010 (bãi bỏ hoàn toàn ADR 0009); đã cập nhật toàn bộ tài liệu thiết kế (`doc/plan/F03-hoc-phan-clo.md`, `doc/sdd/`, `doc/srs/`, `doc/test-plan.md`), sẵn sàng triển khai mã nguồn và kiểm thử.

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin (IT):

1. **Vì sao bãi bỏ ADR 0009?**
   - *Lỗ hổng chiếm quyền (TD-29):* ADR 0009 suy quyền ghi đề cương cho `PROGRAM_MANAGER` thông qua `program_courses`. Do đó, một PM có thể tự ý gán một môn học bất kỳ vào CTĐT của mình (`POST /program-versions/{id}/courses`) để chiếm quyền sửa đề cương và CLO của môn đó.
   - *Xung đột môn dùng chung:* Khi một môn học (như *Toán cao cấp*, *Lập trình căn bản*) được dùng chung bởi nhiều CTĐT của nhiều khoa/ngành khác nhau, việc PM ngành này sửa đề cương sẽ trực tiếp làm thay đổi hoặc phá vỡ cấu trúc đào tạo của PM ngành khác.
   - *Độ phức tạp kỹ thuật:* ADR 0009 đòi hỏi truy vấn ngược (Reverse Lookup) đắt đỏ từ `courseId` $\to$ quét `program_courses` $\to$ lấy danh sách `programVersionId` $\to$ kiểm tra đối chiếu `scope.programIds`.

2. **Kiến trúc Phân quyền Mới theo ADR 0010:**
   - **Tách bạch quyền sở hữu và phạm vi quản trị:**
     - `ADMIN` (Phòng Đào tạo): Đơn vị duy nhất tạo/sửa/xóa môn học trong Master Catalog `courses`. Admin gán `courseIds` vào `scope.courseIds` của Trưởng bộ môn (`DEPARTMENT_HEAD`).
     - `DEPARTMENT_HEAD` (Trưởng bộ môn): Quản lý chuyên môn của các môn học thuộc bộ môn mình. Phân công môn học cho Giảng viên (`LECTURER`) trong bộ môn biên soạn đề cương bằng cách gán `courseId` vào `scope.courseIds` của Giảng viên. Trưởng bộ môn là người duy nhất có thẩm quyền thẩm định và duyệt chuyển trạng thái đề cương từ `DRAFT` sang `CONFIRMED`.
     - `LECTURER` (Giảng viên bộ môn): Biên soạn nội dung đề cương (`syllabi`) và chuẩn đầu ra (`clos`) ở trạng thái `DRAFT` cho các môn học nằm trong `scope.courseIds` của mình. Giảng viên **không có quyền** tự duyệt đề cương sang `CONFIRMED`. Khi đề cương đã `CONFIRMED` hoặc `ARCHIVED`, giảng viên bị chặn hoàn toàn không được sửa CLO (HTTP 422).
     - `PROGRAM_MANAGER` (Trưởng ngành): Không có quyền tạo môn học trong kho tổng, không có quyền can thiệp vào nội dung đề cương/CLO của bộ môn. PM tập trung vào cấp độ CTĐT: chọn môn đưa vào CTĐT (`program_courses`), xác định mức đóng góp I/R/M (`course_pi_contributions`), và thiết lập ma trận liên kết $CLO \to PI$ (`clo_pi_links`) theo `scope.programIds`.
     - `QA_OFFICER` (Cán bộ Đảm bảo chất lượng): Thẩm định tính tương thích sư phạm của các liên kết CLO–PI (`verification.status = VERIFIED`).
   - **Tối ưu hóa Hiệu năng & Bảo mật:**
     - Phương thức `canAccessCourse(principal, courseId)` chuyển thành phép kiểm tra tập hợp $O(1)$ trong bộ nhớ: `principal.getScope().getCourseIds().contains(courseId)` (kết hợp kiểm tra vai trò `DEPARTMENT_HEAD` hoặc `LECTURER`).
     - Triệt tiêu hoàn toàn truy vấn phân tán đắt đỏ và bịt kín 100% lỗ hổng TD-29.

### Trả lời — Góc nhìn Sinh viên Kinh doanh số (IBS):

1. **Chuẩn hóa Mô hình Quản trị Đại học Thực tế:**
   - Trong cấu trúc quản trị đại học hiện đại (và theo quy định của Bộ Giáo dục & Đào tạo Việt Nam):
     - **Phòng Đào tạo (`ADMIN`)** giữ vai trò kiến trúc sư dữ liệu tổng, quản lý danh mục mã môn học chính thức phục vụ xếp thời khóa biểu và tính học phí.
     - **Bộ môn chuyên môn (`DEPARTMENT_HEAD` & `LECTURER`)** là nơi sở hữu tri thức học thuật sâu. Trưởng bộ môn giao nhiệm vụ cho giảng viên có chuyên môn biên soạn đề cương chi tiết (Syllabus) và các chuẩn đầu ra học phần (CLO). Hội đồng chuyên môn của Bộ môn họp thẩm định và Trưởng bộ môn ký duyệt.
     - **Ban điều hành Ngành đào tạo (`PROGRAM_MANAGER`)** đóng vai trò tích hợp hệ thống. Trưởng ngành chọn các môn học do các bộ môn cung cấp để ghép thành khung chương trình hoàn chỉnh, và xác định xem các CLO của môn học đó đóng góp vào những Chuẩn đầu ra chương trình (PLO/PI) nào của ngành mình.
2. **Loại bỏ Rủi ro Xung đột Học thuật:**
   - Việc giao quyền quản lý đề cương về đúng Bộ môn giúp chấm dứt tình trạng các ngành "giành nhau" sửa đề cương môn dùng chung, bảo đảm tính nhất quán và chất lượng học thuật cao nhất theo tiêu chuẩn kiểm định quốc tế AUN-QA và ABET.

### Câu hỏi hội đồng có thể hỏi thêm:

- *Vì sao không để Trưởng ngành (PM) tự tạo môn học và viết đề cương như thiết kế ban đầu?*  
  Nếu để PM tự tạo môn học, hệ thống sẽ rơi vào tình trạng "loạn mã môn học" và trùng lặp nội dung khi nhiều ngành cùng tạo môn giống nhau. Hơn nữa, PM chỉ chịu trách nhiệm về ngành đào tạo của mình, không có thẩm quyền chuyên môn đại diện cho cả Bộ môn giảng dạy.
- *Nếu Giảng viên chuyển công tác hoặc không tiếp tục biên soạn đề cương, Trưởng bộ môn xử lý thế nào?*  
  Trưởng bộ môn chỉ cần gọi API phân công lại để thu hồi `courseId` từ `scope.courseIds` của giảng viên cũ và gán cho giảng viên mới. Dữ liệu đề cương ở trạng thái `DRAFT` vẫn được bảo toàn nguyên vẹn trên hệ thống.
- *Trưởng ngành muốn môn học bổ sung thêm một chuẩn đầu ra CLO để đáp ứng PI mới của ngành mình thì làm sao?*  
  Trưởng ngành không được tự ý sửa CLO trong hệ thống, mà gửi đề xuất qua kênh phối hợp học thuật cho Trưởng bộ môn. Trưởng bộ môn chỉ đạo Giảng viên cập nhật đề cương (tạo phiên bản mới) và duyệt `CONFIRMED`. Sau khi có CLO mới, Trưởng ngành sẽ tạo liên kết $CLO \to PI$ tương ứng.
