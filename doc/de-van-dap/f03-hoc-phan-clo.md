# Hồ Sơ Vấn Đáp Bảo Vệ Đồ Án Tốt Nghiệp — Phân Hệ F03: Học Phần, Đề Cương, CLO và Liên Kết CLO–PI (LMS-API)

**Đề tài:** Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra chương trình đào tạo (Learning Outcomes Management and Analytics System)  
**Phân hệ:** F03 — Quản lý Học phần, Vị trí học phần trong CTĐT (US-09), Đề cương theo phiên bản (US-10), Chuẩn đầu ra học phần CLO (US-11), và Liên kết CLO–PI (US-12)  
**Tài liệu liên quan:** `doc/plan/F03-hoc-phan-clo.md`, `doc/adr/0002-nen-tang-chung-step-00.md`, `doc/adr/0005-phan-quyen-chuong-trinh-dao-tao.md`, `doc/GiaiThich_CoSoDuLieu.md` (§2 nguyên tắc 2 & 12, §4.6–4.7, §4.9–4.11, §7), `LMS-UI/doc/API-CONTRACT.md`  
**Nhóm sinh viên thực hiện:** Sinh viên Công nghệ thông tin (IT) & Sinh viên Kinh doanh số (IBS)

---

## Bối Cảnh và Mục Đích Tài Liệu

Tài liệu này chuẩn bị bộ câu hỏi và câu trả lời học thuật chuyên sâu phục vụ buổi bảo vệ Đồ án tốt nghiệp trước Hội đồng đánh giá khoa học cho phân hệ **F03**. Nội dung được cấu trúc song song theo hai góc nhìn bổ trợ mật thiết cho nhau:
- **Góc nhìn Sinh viên chuyên ngành Công nghệ thông tin (IT):** Tập trung vào thiết kế cơ sở dữ liệu NoSQL tối ưu hóa cho truy vấn phân tán, phân rã quan hệ Many-to-Many giữa Học phần và Phiên bản CTĐT thông qua collection trung gian `program_courses`, toàn vẹn tham chiếu cấp dịch vụ (Application-level Referential Integrity), kiểm soát truy cập phân tầng theo phạm vi quản trị (`ScopeAuthorizationService` - fail-closed), và cơ chế bảo vệ xóa an toàn (Restrict Delete).
- **Góc nhìn Sinh viên chuyên ngành Kinh doanh số (IBS):** Tập trung vào triết lý Giáo dục dựa trên chuẩn đầu ra (Outcome-Based Education - OBE), tính tái sử dụng và quản trị danh mục học phần cấp trường (Course Master Catalog Management), thiết kế cấu trúc chương trình đào tạo phân tầng (Khối kiến thức cơ sở khối ngành, cơ sở ngành, chuyên ngành, đồ án/khóa luận tốt nghiệp), ràng buộc logic lộ trình học tập (Học phần tiên quyết - Prerequisite Chains), và tính bất biến lịch sử của đề cương môn học theo từng niên khóa.

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
   - **Xác thực học phần tiên quyết (`prerequisiteCourseIds`) và học phần học trước (`previousCourseIds`):** Duyệt danh sách mã tiên quyết và học trước, kiểm tra từng học phần phải tồn tại trong CSDL và không được là chính học phần đang gán (tự tham chiếu) (HTTP 422).

3. **Hiện thực API RESTful và Phân quyền Scope Authorization (ADR 0005):**
   - [CourseController.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/controller/CourseController.java) (`/api/v1/courses`): `ADMIN` và `PROGRAM_MANAGER` được phép thêm/sửa danh mục; chỉ `ADMIN` được xóa danh mục; tất cả người dùng xác thực được phép tra cứu (`GET`).
   - [ProgramCourseController.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/controller/ProgramCourseController.java) (`/api/v1/program-versions/{programVersionId}/courses`): Sử dụng `@PreAuthorize("@scopeAuthorizationService.canAccessProgramVersion(principal, #programVersionId)")` bảo đảm kiểm soát truy cập phân quyền chặt chẽ theo nguyên tắc fail-closed.

4. **Kiểm thử tự động toàn diện:**
   - Viết bộ kiểm thử Unit Tests và Bean Validation riêng biệt: `CourseServiceImplTest`, `ProgramCourseServiceImplTest`, `CourseValidationTest`.
   - Viết bài kiểm thử tích hợp thực tế [CourseIntegrationTest.java](file:///D:/Code/Project/LMS-API/src/test/java/com/hcmute/lms/lmsapi/controller/CourseIntegrationTest.java) chạy trên MongoDB Replica Set thật (`rs0`), bao phủ 100% các Acceptance Criteria: US-09/AC-1 (tạo học phần, trùng mã 409), US-09/AC-2 (gán vào CTĐT, trùng 409), US-09/AC-3 (học phần tiên quyết không hợp lệ 422), kiểm tra RBAC, phạm vi scope rejection (HTTP 403), và vòng đời CRUD.

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
   - **Danh mục Master `courses`:** Là tài nguyên dùng chung toàn trường, do đó các thao tác `POST` (Tạo mới), `PUT` (Cập nhật) chỉ cho phép vai trò quản trị `ADMIN` và `PROGRAM_MANAGER`. Thao tác `DELETE` chỉ dành riêng cho `ADMIN`. Thao tác tra cứu `GET` mở cho tất cả người dùng hợp lệ trong hệ thống (`@PreAuthorize("isAuthenticated()")`).
   - **Gán học phần vào CTĐT `program_courses`:** Là tài nguyên gắn liền với một phiên bản CTĐT cụ thể. Do đó, các endpoint tại `/api/v1/program-versions/{programVersionId}/courses` được bảo vệ bằng SpEL:  
     `@PreAuthorize("@scopeAuthorizationService.canAccessProgramVersion(principal, #programVersionId)")`
2. **Cơ chế Fail-Closed (Đóng mặc định):**
   - Nếu `principal` không có quyền (ví dụ `STUDENT`, `LECTURER`), hoặc `PROGRAM_MANAGER` cố tình can thiệp vào `programVersionId` của một ngành khác ngoài danh sách `scope.programIds` được cấp phép, Spring Security sẽ chặn lại ngay tại tầng Filter và trả về HTTP 403 `FORBIDDEN`.

---

## Câu Hỏi 6: Vì sao các User Story còn lại của F03 (US-10, US-11, US-12) chưa thực hiện trong đợt này và cần những điều kiện gì để triển khai tiếp?

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin & Kinh doanh số:

1. **Tính trung thực học thuật và Nguyên tắc không giả lập dữ liệu (Rule 2):**
   - **US-10 (Đề cương môn học - `syllabi`):** AC-2 yêu cầu kiểm tra ràng buộc `effectiveToTermId` không được trước `effectiveFromTermId`. Dữ liệu này phụ thuộc vào collection `terms` thuộc phân hệ **F05a** (US-18, Issue #28) hiện chưa được hiện thực. Đồng thời, AC-3 yêu cầu đóng băng (freeze) sửa CLO khi đề cương ở trạng thái `CONFIRMED`, đòi hỏi module CLO (US-11) phải có sẵn. Ngoài ra, nội dung đề cương chính thức vẫn đang chờ cung cấp từ Khoa (Q02).
   - **US-11 (Chuẩn đầu ra học phần - `clos`):** Phụ thuộc vào `syllabusId` từ US-10. Đồng thời, trường `clos.measurement` (tỷ trọng, ngưỡng đạt) theo quy tắc nghiệp vụ §4 và câu hỏi nghiệp vụ Q12 phải để `null` cho tới khi Khoa xác nhận, không được tự ý bịa đặt số liệu giả (ví dụ: 60%, 20/40/40).
   - **US-12 (Liên kết CLO–PI và Ma trận CLO × PI):** Đây là liên kết mang tính pháp lý sư phạm cốt lõi. Theo quy tắc nghiệp vụ §4.5 và câu hỏi Q14, hệ thống chỉ chấp nhận ma trận CLO–PI chính thức do Khoa/Bộ môn ban hành và phê duyệt; nghiêm cấm nhóm phát triển tự suy diễn ánh xạ từ câu chữ của CLO và PI. Hơn nữa, công thức trọng số `weight` của liên kết vẫn đang chờ câu trả lời Q22.

2. **Kết luận đề xuất:**
   - Nhóm IT & IBS đã hoàn thành 100% US-09 (Issue #20) với đầy đủ mã nguồn, unit test, integration test, và tài liệu vấn đáp.
   - Các User Story #21, #22, #23 được tổng kết chi tiết kèm theo các câu hỏi nghiệp vụ đang chờ để Thầy/Cô và Hội đồng định hướng phương án (triển khai F05a trước, hoặc cung cấp mẫu ma trận CLO–PI nghiệm thu).


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

## Câu Hỏi 10: Các nội dung chưa được xác nhận (Q02, Q12, Q14, Q22, F05a) được hệ thống xử lý như thế nào để đảm bảo tính toàn vẹn và trung thực học thuật (Rule 2)?

### Trả lời — Góc nhìn Sinh viên Công nghệ thông tin & Kinh doanh số:

1. **Q02 (Đề cương chính thức từ Khoa):**
   - Khoa chưa ban hành văn bản chính thức các đề cương môn học thử nghiệm.
   - **Xử lý:** Trường `contentOriginal` của CLO cho phép để trống chuỗi rỗng `""` hoặc do người dùng truyền vào khi khai báo. Tuyệt đối không tự bịa đặt nội dung câu chữ CLO vào mã nguồn hay database seed.
2. **Q12 (Tỷ trọng và ngưỡng đạt CLO):**
   - Khoa chưa cung cấp tỷ lệ % và ngưỡng điểm tối thiểu để đạt từng CLO.
   - **Xử lý:** Trường `clos.measurement` (`defaultWeight`, `defaultPassThreshold`) được thiết kế cho phép `null` (US-11/AC-4). Hệ thống không áp đặt các con số tùy tiện (như 60% hay 20/40/40). Nếu người dùng truyền giá trị thì bắt buộc phải nằm trong đoạn $[0, 1]$.
3. **Q14 (Bảng ánh xạ CLO-PI được duyệt):**
   - Khoa chưa ban hành bảng ma trận nghiệm thu giữa CLO của các môn thử nghiệm và 17 PI.
   - **Xử lý:** Toàn bộ liên kết `clo_pi_links` chỉ được lưu khi người quản trị chủ động thiết lập qua API. Hệ thống không tự động suy diễn liên kết dựa trên từ khóa ngữ nghĩa câu chữ (NLP Inference).
4. **Q22 (Công thức trọng số weight CLO-PI):**
   - Chưa có văn bản xác nhận công thức tính trọng số khi chuyển đổi kết quả từ CLO lên PI theo phương pháp `FROM_CLOS`.
   - **Xử lý:** Trường `weight` trong `clo_pi_links` được phép để `null` (US-12/AC-5). Nếu có truyền thì kiểm tra biên $0 < weight \le 1.0$.
5. **F05a (Collection `terms`):**
   - Phân hệ quản lý học kỳ `terms` (F05a / Issue #28) chưa được triển khai.
   - **Xử lý:** `effectiveFromTermId` và `effectiveToTermId` trong `syllabi` cho phép nhận `ObjectId` hợp lệ hoặc để `null`. Ràng buộc so sánh niên khóa `effectiveTo >= effectiveFrom` được ghi nhận chờ kết nối với collection `terms` khi hoàn thiện F05a.
