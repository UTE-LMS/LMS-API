# TÀI LIỆU ĐẶC TẢ KỸ THUẬT CƠ SỞ DỮ LIỆU MONGODB (DATABASE SPECIFICATION)
**Hệ thống Quản lý, Đo lường và Đánh giá Chuẩn đầu ra (OBE Analytics & Measurement Engine)**  
*Phiên bản đặc tả: v0.5 | Chuẩn công nghệ: MongoDB 7.0+ (Replica Set) – Spring Boot 3.x – Angular 17+*

---

## 1. TỔNG QUAN HỆ THỐNG VÀ NGUYÊN TẮC THIẾT KẾ (SYSTEM PRINCIPLES)

Tài liệu này đóng vai trò là kim chỉ nam kỹ thuật (Technical Source of Truth) dành cho lập trình viên và các AI Developer Agent (Antigravity). Mọi cấu trúc dữ liệu, quan hệ và bộ quy tắc kiểm tra (validation) được xây dựng để phục vụ chuỗi nghiệp vụ đo lường theo chiều nghịch:

$$\text{Điểm từng Item (Câu hỏi/Rubric)} \longrightarrow \text{CLO} \longrightarrow \text{PI} \longrightarrow \text{PLO} \longrightarrow \text{Dashboard/Báo cáo} \longrightarrow \text{Hành động cải tiến (CQI)}$$

### Các nguyên tắc bất biến (Invariants):
1. **Quản lý phiên bản độc lập (Strict Versioning):** Không cập nhật đè (mutate in-place) lên các bản ghi chuẩn đầu ra đã duyệt. Khi CTĐT, đề cương hoặc quy tắc đo lường thay đổi, sinh phiên bản mới (`program_versions`, `syllabi`, `measurement_rules`). Mọi kết quả đo lường cũ (`student_results`, `aggregate_results`) luôn liên kết với ID phiên bản tương ứng để đảm bảo tính tái lập (reproducibility).
2. **Không khóa ngoại vật lý – Ràng buộc ở tầng dịch vụ (Application-enforced Integrity):** MongoDB không hỗ trợ Foreign Key Cascading. Tầng Application (Spring Boot) chịu trách nhiệm kiểm tra tính hợp lệ trước khi ghi. Các giao dịch ghi đồng thời nhiều collection (nhập điểm, duyệt đề, chạy đo lường) bắt buộc sử dụng Multi-document ACID Transactions (yêu cầu cụm MongoDB Replica Set).
3. **Phân tách rạch ròi giữa Điểm số, Thang Bloom và Vai trò I/R/M:**
   - Mức Bloom (1–6) là mức độ nhận thức của câu hỏi, không phải điểm số và không phải trọng số.
   - Ký hiệu $I$ (Introduced), $R$ (Reinforced), $M$ (Mastered) là vai trò sư phạm; mặc định không được quy đổi thành hệ số số học trừ khi được kích hoạt qua cờ `useIrmAsWeight = true` trong `measurement_rules`.
4. **Xử lý nghiêm ngặt dữ liệu khuyết (Strict Missing Data Handling):**
   - Không tự ý gán $0$ cho các ô trống.
   - Trạng thái `BLANK` (có nộp bài nhưng bỏ trống câu): Tính $0$ điểm và có ghi nhận.
   - Trạng thái `ABSENT`, `NOT_GRADED`, `EXEMPT`, `MISSING`: Điểm số bắt buộc là `null`, không tính $0$, và được xử lý theo cấu hình mẫu số trong `measurement_rules.missingDataPolicy`.
5. **Truy vết một cấp liền kề (Single-hop Lineage):** Nhằm tránh phình to kích thước document (BSON 16MB limit), mảng `inputs[]` trong `student_results` chỉ tham chiếu một cấp trực tiếp: Cấp CLO trỏ về `scores`; Cấp PI trỏ về `scores` hoặc `student_results (CLO)`; Cấp PLO trỏ về `student_results (PI)`.

---

## 2. BẢN ĐỒ KIẾN TRÚC VÀ QUAN HỆ 30 COLLECTIONS

Cơ sở dữ liệu bao gồm 30 collections được phân chia thành 8 phân hệ chức năng:

```
[NHÓM A: Người dùng] 
  users 

[NHÓM B: CTĐT & Chuẩn đầu ra] 
  programs ──< program_versions ──┬──< plos ──< pis
                                   ├──< program_courses >── courses
                                   └──< course_pi_contributions (I/R/M) >── courses, pis

[NHÓM C: Đề cương & CLO]
  courses ──< syllabi ──┬──< clos ──< clo_pi_links >── pis
                        └──< question_banks ──< question_categories (Cây 3 cấp)
                                             └──< questions

[NHÓM D: Ngân hàng câu hỏi & Đề thi]
  questions, question_banks, question_categories
  assessments (scope = SYLLABUS_TEMPLATE | SECTION) ── nhúng items[] (câu hỏi/tiêu chí + outcomeMappings)

[NHÓM E: Lớp học phần & Sinh viên]
  terms ──< course_sections ──< enrollments >── students
             │
[NHÓM F: Chấm điểm & Nhập liệu Moodle]
             ├──< import_batches ──< import_row_errors
             └──< scores (Điểm từng câu hỏi x sinh viên)

[NHÓM G: Động cơ đo lường]
  measurement_rules ──< calculation_runs ──┬──< student_results (CLO, PI, COURSE, PLO)
                                           └──< aggregate_results (Lớp, Học phần, CTĐT)

[NHÓM H: Báo cáo, Giám sát & Cải tiến]
  reports ──< improvement_actions
  audit_logs
  ml_experiments (Tùy chọn)
```

---

## 3. ĐẶC TẢ CHI TIẾT TỪNG COLLECTION

### NHÓM A: NGƯỜI DÙNG & PHÂN QUYỀN (ACCESS CONTROL)

#### 3.1. `users`
Lưu trữ tài khoản và ma trận phân quyền hệ thống.
* **Fields:**
  - `_id`: `ObjectId` – Khóa chính.
  - `username`: `String` (Unique, min 3 chars).
  - `email`: `String` (Unique, RFC 5322).
  - `passwordHash`: `String` (BCrypt / Argon2).
  - `fullName`: `String`.
  - `status`: `String` Enum: `["ACTIVE", "LOCKED"]`.
  - `roleAssignments`: `Array<Object>`:
    - `role`: `String` Enum: `["ADMIN", "PROGRAM_MANAGER", "DEPARTMENT_HEAD", "QA_OFFICER", "LECTURER", "STUDENT", "VIEWER"]`.
    - `scope`: `Object` `{ programIds: [ObjectId], courseIds: [ObjectId], sectionIds: [ObjectId] }`.
  - `createdAt`, `updatedAt`: `Date`.
* **Business Rule:** Trưởng bộ môn (`DEPARTMENT_HEAD`) có thẩm quyền duyệt đề thi (`assessments`) và ma trận ánh xạ; Sinh viên (`STUDENT`) chỉ có quyền đọc `student_results` thuộc về mã sinh viên của chính mình.
* **Indexes:** `{ username: 1 }` (unique), `{ email: 1 }` (unique).

---

### NHÓM B: CHƯƠNG TRÌNH ĐÀO TẠO & CHUẨN ĐẦU RA CẤP CAO

#### 3.2. `programs`
* **Fields:** `_id`, `code` (`String`, unique, e.g., `"CNTT"`), `name` (`String`), `level` (`String`), `degree` (`String`), `createdAt`, `updatedAt`.
* **Indexes:** `{ code: 1 }` (unique).

#### 3.3. `program_versions`
Phiên bản CTĐT tương ứng với các khóa tuyển sinh.
* **Fields:**
  - `_id`: `ObjectId`.
  - `programId`: `ObjectId` $\rightarrow$ `programs._id`.
  - `version`: `String` (e.g., `"2026-v1.0"`).
  - `totalCredits`: `Int` (e.g., $150$) – Tổng số tín chỉ tốt nghiệp theo quy định.
  - `durationYears`: `Double` (e.g., $4.0$).
  - `applicableCohorts`: `Array<String>` (e.g., `["K2026", "K2027"]`).
  - `status`: `String` Enum: `["DRAFT", "ACTIVE", "ARCHIVED"]`.
  - `verification`: `Object` `{ status: Enum["UNVERIFIED", "VERIFIED", "NEEDS_CLARIFICATION"], verifiedBy: ObjectId, verifiedAt: Date, note: String }`.
  - `extra`: `Object` (chứa các trường mở rộng).
  - `createdAt`, `updatedAt`: `Date`.
* **Indexes:** `{ programId: 1, version: 1 }` (unique).

#### 3.4. `plos` (Program Learning Outcomes)
* **Fields:**
  - `_id`: `ObjectId`.
  - `programVersionId`: `ObjectId` $\rightarrow$ `program_versions._id`.
  - `code`: `String` (Pattern: `^PLO[1-9][0-9]*$`, e.g., `"PLO1"`).
  - `displayOrder`: `Int` (Phục vụ sắp xếp hiển thị, tách rời khỏi thứ tự mã).
  - `category`: `String` Enum: `["GENERAL", "SPECIALIZED", "UNSPECIFIED"]` (Phân định chuẩn chung và chuẩn chuyên biệt).
  - `contentOriginal`: `String` (Nguyên văn quyết định ban hành).
  - `contentSummary`: `String` (Mô tả ngắn gọn).
  - `capabilityLevelRaw`: `Int` ($1 \le x \le 6$) – Trình độ năng lực quy định trong CTĐT.
  - `verification`: `Object` (Trạng thái xác thực nội dung).
  - `createdAt`, `updatedAt`: `Date`.
* **Indexes:** `{ programVersionId: 1, code: 1 }` (unique).

#### 3.5. `pis` (Performance Indicators)
* **Fields:**
  - `_id`: `ObjectId`.
  - `ploId`: `ObjectId` $\rightarrow$ `plos._id`.
  - `programVersionId`: `ObjectId` $\rightarrow$ `program_versions._id`.
  - `code`: `String` (Pattern: `^PI[1-9][0-9]*\.[1-9][0-9]*$`, e.g., `"PI1.2"`).
  - `contentOriginal`: `String`.
  - `contentShort`: `String`.
  - `capabilityLevelRaw`: `Int` ($1 \le x \le 6$).
  - `targetPassRate`: `Double` ($0 \le x \le 1$, mục tiêu chuẩn, ví dụ: $0.70$).
  - `verification`: `Object`.
  - `createdAt`, `updatedAt`: `Date`.
* **Indexes:** `{ programVersionId: 1, code: 1 }` (unique), `{ ploId: 1 }`.

#### 3.6. `courses`
Danh mục học phần gốc (Abstract Course).
* **Fields:** `_id`, `code` (`String`, unique, e.g., `"OOPR230279"`), `name` (`String`), `credits` (`Int`), `departmentId` (`ObjectId`), `createdAt`, `updatedAt`.
* **Indexes:** `{ code: 1 }` (unique).

#### 3.7. `program_courses`
Vị trí học phần trong một CTĐT cụ thể.
* **Fields:** `_id`, `programVersionId` (`ObjectId`), `courseId` (`ObjectId`), `semester` (`Int`), `credits` (`Int`), `type` (`Enum["COMPULSORY", "ELECTIVE"]`), `category` (`String`), `prerequisiteCourseIds` (`Array<ObjectId>`), `createdAt`, `updatedAt`.
* **Indexes:** `{ programVersionId: 1, courseId: 1 }` (unique).

#### 3.8. `course_pi_contributions`
Ma trận phân bổ chuẩn đầu ra cấp CTĐT (Phụ lục II).
* **Fields:**
  - `_id`: `ObjectId`.
  - `programVersionId`: `ObjectId`, `courseId`: `ObjectId`, `piId`: `ObjectId`.
  - `contribution`: `String` Enum: `["I", "R", "M"]`.
  - `verification`: `Object`.
* **Business Rule:** Chỉ đóng vai trò thiết kế sư phạm; không tự động sinh trọng số nhân điểm trừ khi có cấu hình cho phép trong `measurement_rules`.
* **Indexes:** `{ programVersionId: 1, courseId: 1, piId: 1 }` (unique), `{ piId: 1 }`.

---

### NHÓM C: ĐỀ CƯƠNG & CHUẨN ĐẦU RA HỌC PHẦN (CLO)

#### 3.9. `syllabi`
* **Fields:** `_id`, `courseId` (`ObjectId`), `version` (`String`), `effectiveFromTermId` (`ObjectId`), `effectiveToTermId` (`ObjectId`, nullable), `status` (`Enum["DRAFT", "CONFIRMED", "ARCHIVED"]`), `createdAt`, `updatedAt`.
* **Indexes:** `{ courseId: 1, version: 1 }` (unique).

#### 3.10. `clos` (Course Learning Outcomes)
* **Fields:**
  - `_id`: `ObjectId`.
  - `syllabusId`: `ObjectId` $\rightarrow$ `syllabi._id`.
  - `courseId`: `ObjectId` $\rightarrow$ `courses._id`.
  - `code`: `String` (Duy nhất trong một Syllabus, e.g., `"CLO1"`).
  - `contentOriginal`: `String`.
  - `bloomLevel`: `Int` ($1 \le x \le 6$).
  - `measurement`: `Object` `{ defaultWeight: Double, defaultPassThreshold: Double }`.
  - `verification`: `Object`.
* **Indexes:** `{ syllabusId: 1, code: 1 }` (unique).

#### 3.11. `clo_pi_links`
Khung ánh xạ chính thức giữa CLO và PI được Hội đồng khoa học duyệt.
* **Fields:**
  - `_id`: `ObjectId`.
  - `cloId`: `ObjectId` $\rightarrow$ `clos._id`.
  - `piId`: `ObjectId` $\rightarrow$ `pis._id`.
  - `syllabusId`: `ObjectId`, `programVersionId`: `ObjectId`.
  - `weight`: `Double` ($0 < x \le 1$, tỷ trọng đóng góp của CLO vào PI khi dùng phương thức tổng hợp gián tiếp `FROM_CLOS`).
* **Business Rule:** Bắt buộc tồn tại liên kết ở đây thì câu hỏi thi mới được phép gán cặp `(cloId, piId)` tương ứng.
* **Indexes:** `{ cloId: 1, piId: 1 }` (unique), `{ syllabusId: 1 }`, `{ piId: 1 }`.

---

### NHÓM D: NGÂN HÀNG CÂU HỎI & BÀI ĐÁNH GIÁ (ASSESSMENTS & BLUEPRINTS)

#### 3.12. `question_banks`
* **Fields:** `_id`, `code` (`String`, unique), `name` (`String`), `courseId` (`ObjectId`), `syllabusId` (`ObjectId`), `sourceSystem` (`Enum["INTERNAL", "MOODLE", "EXCEL_IMPORT"]`), `externalBankId` (`String`), `status` (`Enum["ACTIVE", "ARCHIVED"]`), `createdAt`, `updatedAt`.
* **Indexes:** `{ code: 1 }` (unique), `{ courseId: 1, syllabusId: 1 }`.

#### 3.13. `question_categories` (Cây danh mục tối đa 3 cấp)
* **Fields:**
  - `_id`: `ObjectId`.
  - `bankId`: `ObjectId` $\rightarrow$ `question_banks._id`.
  - `code`: `String`, `name`: `String`.
  - `parentId`: `ObjectId` (Nullable; null đối với Cấp 1).
  - `ancestorIds`: `Array<ObjectId>` (Materialized Path từ gốc đến cha).
  - `level`: `Int` ($1, 2, 3$).
  - `order`: `Int`.
* **Validation:** $level = 1 \iff parentId = \text{null}$; $level \in \{2, 3\} \iff \text{size}(ancestorIds) = level - 1$.
* **Indexes:** `{ bankId: 1, code: 1 }` (unique), `{ ancestorIds: 1 }`.

#### 3.14. `questions`
* **Fields:**
  - `_id`: `ObjectId`, `bankId`: `ObjectId`, `code`: `String`, `version`: `Int` (default 1).
  - `questionType`: `Enum["MCQ", "SHORT_ANSWER", "ESSAY", "PRACTICAL", "PROJECT_TASK"]`.
  - `content`: `String` (Có thể để trống nếu câu hỏi bảo mật).
  - `categoryId`: `ObjectId` $\rightarrow$ `question_categories._id`.
  - `bloomLevel`: `Int` ($1 \le x \le 6$).
  - `defaultMaxScore`: `Double`.
  - `outcomeMappings`: `Array<Object>` (minItems: 1 nếu status="ACTIVE"):
    - `cloId`: `ObjectId`, `piId`: `ObjectId`, `weight`: `Double` ($0 < x \le 1$).
    - Ràng buộc: $\sum weight = 1.0$; Cặp `(cloId, piId)` phải tồn tại trong `clo_pi_links`.
  - `source`: `Object` `{ system: String, externalQuestionId: String }`.
  - `status`: `Enum["DRAFT", "ACTIVE", "RETIRED"]`.
* **Indexes:** `{ bankId: 1, code: 1 }` (unique), `{ categoryId: 1 }`, `{ "outcomeMappings.cloId": 1 }`, `{ "outcomeMappings.piId": 1 }`.

#### 3.15. `assessments` (Tích hợp Ma trận đề thi & Phê duyệt của Trưởng bộ môn)
Quản lý cấu trúc bài thi, đóng vai trò bản đặc tả (Blueprint) và là nơi ghi nhận kết quả điểm số.
* **Fields:**
  - `_id`: `ObjectId`.
  - `syllabusId`: `ObjectId` $\rightarrow$ `syllabi._id`.
  - `sectionId`: `ObjectId` $\rightarrow$ `course_sections._id` (Bắt buộc nếu `scope = "SECTION"`).
  - `templateId`: `ObjectId` $\rightarrow$ `assessments._id` (Trỏ về khung đề cương mẫu nếu là bài lớp).
  - `code`: `String`, `name`: `String`.
  - `scope`: `String` Enum: `["SYLLABUS_TEMPLATE", "SECTION"]`.
  - `type`: `String` Enum: `["QUIZ", "PRACTICAL", "PROJECT", "MIDTERM", "FINAL", "OTHER"]`.
  - `phase`: `String` Enum: `["FORMATIVE", "MIDTERM", "SUMMATIVE"]`.
  - `totalPoints`: `Double` (Tổng điểm quy định của bài thi, e.g., $10.0$).
  - `weightInCourse`: `Double` ($0 \le x \le 1$, tỷ trọng trong điểm học phần).
  - `dataGranularity`: `Enum["ITEM", "TOTAL_ONLY"]` (Mặc định `ITEM`).
  - `status`: `String` Enum:
    - `"DRAFT"`: Giảng viên đang biên soạn ma trận đề thi.
    - `"SUBMITTED"`: Đã gửi lên Trưởng bộ môn phê duyệt.
    - `"APPROVED"`: Trưởng bộ môn đã duyệt (cho phép xuất phôi in và mở cổng nhập điểm).
    - `"REJECTED"`: Trưởng bộ môn từ chối (yêu cầu sửa đổi).
    - `"SCORE_ENTERED"`: Đã có dữ liệu điểm từ Moodle/Excel nạp vào.
    - `"LOCKED"`: Đã khóa sổ kết quả hoàn toàn.
  - `isLocked`: `Boolean` (True khi `status` $\in$ `["APPROVED", "SCORE_ENTERED", "LOCKED"]`; ngăn chặn mọi hành vi thay đổi cấu trúc `items[]`).
  - `approval`: `Object` (Quản lý quy trình phê duyệt):
    - `submittedBy`: `ObjectId`, `submittedAt`: `Date`.
    - `reviewedBy`: `ObjectId`, `reviewedAt`: `Date`.
    - `reviewNote`: `String`.
    - `history`: `Array<Object>` `{ action: Enum["SUBMIT", "APPROVE", "REJECT", "UNLOCK"], actorId: ObjectId, at: Date, note: String }`.
  - `coverageSummary`: `Object` (Thống kê ma trận đề thi phục vụ màn hình duyệt nhanh):
    - `totalItemsSum`: `Double` (Tổng điểm các items).
    - `byClo`: `Array<{ cloId: ObjectId, points: Double, percentage: Double }>`.
    - `byBloom`: `Array<{ bloomLevel: Int, questionCount: Int, points: Double, percentage: Double }>`.
  - `exportedFiles`: `Array<Object>` `{ fileRef: String, templateCode: String, includeOutcomeLabels: Boolean, exportedBy: ObjectId, exportedAt: Date }`.
  - `items`: `Array<Object>` (Danh sách câu hỏi hoặc tiêu chí rubric):
    - `_id`: `ObjectId` (Khóa định danh item).
    - `code`: `String` (e.g., `"Q1"`, `"Crit1"`).
    - `orderIndex`: `Int`.
    - `title`: `String`.
    - `kind`: `Enum["QUESTION", "CRITERION"]`.
    - `maxScore`: `Double` ($> 0$).
    - `bloomLevel`: `Int` ($1 \le x \le 6$).
    - `topic`: `String`.
    - `questionId`: `ObjectId` (Nullable, trỏ về câu gốc trong `questions`).
    - `questionVersion`: `Int`.
    - `externalRef`: `String` (ID câu hỏi trên Moodle LMS để khớp cột điểm).
    - `outcomeMappings`: `Array<Object>` (minItems: 1):
      - `cloId`: `ObjectId`, `piId`: `ObjectId`, `weight`: `Double` ($0 < x \le 1$, $\sum weight = 1.0$).
    - `rubric`: `Object` `{ levels: Array<{ level: Int, description: String, score: Double }> }`.
  - `createdAt`, `updatedAt`: `Date`.
* **Validation & Business Rules:**
  - Nếu `status` $\in$ `["SUBMITTED", "APPROVED", "SCORE_ENTERED", "LOCKED"]` $\implies \text{size}(items) \ge 1$.
  - Backend phải kiểm tra: $|\text{totalPoints} - \sum items[i].maxScore| < 0.001$ trước khi cho phép `SUBMITTED`.
* **Indexes:** `{ syllabusId: 1, sectionId: 1, code: 1 }` (unique), `{ sectionId: 1, status: 1 }`, `{ "items.outcomeMappings.cloId": 1 }`, `{ "items.outcomeMappings.piId": 1 }`.

---

### NHÓM E: HỌC KỲ, LỚP HỌC PHẦN & SINH VIÊN

#### 3.16. `terms`
* **Fields:** `_id`, `code` (`String`, unique, e.g., `"2026-HK1"`), `academicYear` (`String`), `startDate`: `Date`, `endDate`: `Date`, `status` (`Enum["OPEN", "SCORE_LOCKED", "CLOSED"]`).
* **Indexes:** `{ code: 1 }` (unique).

#### 3.17. `course_sections` (Lớp học phần)
* **Fields:** `_id`, `code` (`String`, unique, e.g., `"2026-HK1-OOPR230279-01"`), `courseId` (`ObjectId`), `syllabusId` (`ObjectId`), `termId` (`ObjectId`), `lecturerIds` (`Array<ObjectId>`), `enrolledCount`: `Int`, `status` (`Enum["OPEN", "CLOSED", "LOCKED"]`), `createdAt`, `updatedAt`.
* **Indexes:** `{ code: 1 }` (unique), `{ courseId: 1, termId: 1 }`, `{ lecturerIds: 1 }`.

#### 3.18. `students`
* **Fields:**
  - `_id`: `ObjectId`.
  - `studentRef`: `String` (Unique – Mã số sinh viên chính thức hoặc mã ẩn danh).
  - `userId`: `ObjectId` (Nullable – Trỏ về `users._id` khi sinh viên được cấp quyền truy cập hệ thống).
  - `fullName`: `String` (Tùy chọn, bảo mật theo quy định).
  - `email`: `String`.
  - `cohort`: `String` (e.g., `"K2026"`).
  - `majorCode`: `String` (e.g., `"CNTT"`).
  - `programVersionId`: `ObjectId` $\rightarrow$ `program_versions._id`.
  - `status`: `Enum["ACTIVE", "SUSPENDED", "GRADUATED", "DROPPED_OUT"]`.
  - `createdAt`, `updatedAt`: `Date`.
* **Indexes:** `{ studentRef: 1 }` (unique), `{ programVersionId: 1, cohort: 1 }`, `{ userId: 1 }`.

#### 3.19. `enrollments` (Mẫu số cho tỷ lệ đạt cấp lớp)
* **Fields:** `_id`, `sectionId` (`ObjectId`), `studentId` (`ObjectId`), `status` (`Enum["ENROLLED", "WITHDRAWN"]`), `enrolledAt`: `Date`.
* **Indexes:** `{ sectionId: 1, studentId: 1 }` (unique), `{ studentId: 1 }`.

---

### NHÓM F: NHẬP ĐIỂM & ĐỐI SOÁT TỆP MOODLE

#### 3.20. `import_batches`
* **Fields:**
  - `_id`: `ObjectId`.
  - `sectionId`: `ObjectId`, `assessmentId`: `ObjectId`.
  - `fileName`: `String`, `fileChecksum`: `String` (SHA-256 chống tải trùng lặp file).
  - `fileRef`: `String` (Đường dẫn lưu trữ minh chứng gốc).
  - `sourceSystem`: `Enum["EXCEL", "CSV", "MOODLE_EXPORT"]`.
  - `columnMapping`: `Object` (Ánh xạ tên cột file sang `items._id` hoặc `items.externalRef`).
  - `emptyCellAs`: `Enum["BLANK", "MISSING"]` (Quy định người dùng chọn khi nạp file).
  - `reimportPolicy`: `Enum["REJECT_ON_CONFLICT", "OVERWRITE", "UPDATE_BLANK_ONLY"]`.
  - `status`: `Enum["UPLOADED", "VALIDATED", "HAS_ERRORS", "CONFIRMED", "ROLLED_BACK"]`.
  - `stats`: `Object` `{ totalRows: Int, validRows: Int, errorRows: Int }`.
  - `uploadedBy`: `ObjectId`, `confirmedBy`: `ObjectId`, `confirmedAt`: `Date`.
* **Indexes:** `{ sectionId: 1, assessmentId: 1, createdAt: -1 }`, `{ fileChecksum: 1 }`.

#### 3.21. `import_row_errors`
* **Fields:** `_id`, `batchId` (`ObjectId`), `rowNumber`: `Int`, `column`: `String`, `errorType` (`Enum["INVALID_STUDENT_CODE", "SCORE_OUT_OF_RANGE", "DUPLICATE_RECORD", "MISSING_FIELD"]`), `rawValue`: `String`, `message`: `String`.
* **Indexes:** `{ batchId: 1, rowNumber: 1 }`.

#### 3.22. `scores` (Collection hạt nhân – Điểm chi tiết từng Item)
* **Fields:**
  - `_id`: `ObjectId`.
  - `sectionId`: `ObjectId`, `assessmentId`: `ObjectId`, `itemId`: `ObjectId`, `studentId`: `ObjectId`.
  - `status`: `String` Enum:
    - `"SCORED"`: Có điểm hợp lệ ($0 \le rawScore \le maxScore$).
    - `"BLANK"`: Sinh viên có nộp bài nhưng bỏ câu $\implies rawScore = 0$.
    - `"ABSENT"`: Vắng thi $\implies rawScore = \text{null}$.
    - `"NOT_GRADED"`: Giảng viên chưa chấm $\implies rawScore = \text{null}$.
    - `"EXEMPT"`: Được miễn thi $\implies rawScore = \text{null}$.
    - `"MISSING"`: Lỗi dữ liệu/chưa thu thập $\implies rawScore = \text{null}$.
  - `rawScore`: `Double` (Bắt buộc null nếu status $\ne$ `SCORED` và $\ne$ `BLANK`).
  - `maxScore`: `Double` (Bản chụp điểm tối đa của item tại thời điểm chấm).
  - `rubricLevel`: `Int` (1–4, nếu item là CRITERION).
  - `importBatchId`: `ObjectId` $\rightarrow$ `import_batches._id`.
  - `sourceRow`: `Int` (Vị trí dòng trong file Excel gốc).
  - `revision`: `Int` (Optimistic Locking chống xung đột sửa điểm).
  - `updatedBy`: `ObjectId`, `updatedAt`: `Date`.
* **Indexes:** `{ sectionId: 1, assessmentId: 1, itemId: 1, studentId: 1 }` (unique), `{ studentId: 1 }`, `{ assessmentId: 1, itemId: 1 }`.

---

### NHÓM G: ĐỘNG CƠ ĐO LƯỜNG (MEASUREMENT ENGINE)

#### 3.23. `measurement_rules` (Quy tắc cấu hình thuật toán)
* **Fields:**
  - `_id`: `ObjectId`, `code`: `String`, `version`: `String`, `status`: `Enum["DRAFT", "CONFIRMED", "RETIRED"]`.
  - `confirmedBy`: `ObjectId`, `confirmedAt`: `Date`.
  - `clo`: `Object`:
    - `method`: `Enum["WEIGHTED_ITEMS", "POINTS_RATIO"]`.
    - `defaultPassThreshold`: `Double` (e.g., $0.50$ hoặc $0.72$).
    - `minCoverage`: `Double` (Tỷ lệ tối thiểu các thành phần bắt buộc có điểm).
  - `course`: `Object`:
    - `requireAllCloPass`: `Boolean` (True: sinh viên phải đạt toàn bộ CLO mới coi là đạt học phần – chống học lệch).
    - `defaultTargetPassRate`: `Double` (Mục tiêu lớp, e.g., $0.70$).
  - `pi`: `Object`:
    - `method`: `Enum["DIRECT_FROM_ITEMS", "FROM_CLOS"]`.
    - `aggregationMethod`: `Enum["WEIGHTED_AVERAGE", "MAX", "LATEST", "ACCUMULATED_EVIDENCE"]`.
    - `useIrmAsWeight`: `Boolean` (Mặc định `false`; nếu `true`, lấy hệ số từ `irmWeights`).
    - `irmWeights`: `Object` `{ I: Double, R: Double, M: Double }` (e.g., `{ I: 1, R: 2, M: 3 }`).
    - `defaultPassThreshold`: `Double`.
  - `plo`: `Object`:
    - `method`: `Enum["ALL_PIS_PASSED", "WEIGHTED_PIS", "AVERAGE_PIS"]`.
    - `defaultPassThreshold`: `Double`, `cohortTargetPassRate`: `Double`.
  - `missingDataPolicy`: `Object`:
    - `blankScoreValue`: `Int` (Luôn là $0$).
    - `excludeAbsentFromDenominator`: `Boolean`.
    - `excludeMissingFromDenominator`: `Boolean`.
  - `createdAt`, `updatedAt`: `Date`.
* **Indexes:** `{ code: 1, version: 1 }` (unique).

#### 3.24. `calculation_runs` (Lịch sử thực thi đo lường)
* **Fields:**
  - `_id`: `ObjectId`.
  - `scope`: `Enum["SECTION", "COURSE", "COHORT", "PROGRAM"]`.
  - `ruleId`: `ObjectId` $\rightarrow$ `measurement_rules._id`.
  - `programVersionId`: `ObjectId`, `syllabusIds`: `Array<ObjectId>`.
  - `isOfficial`: `Boolean` (True nếu dùng rule `CONFIRMED` và dữ liệu đã chốt sổ).
  - `dataSnapshot`: `Object` `{ scoreChecksum: String, totalScores: Int, lastScoreUpdatedAt: Date }`.
  - `lockKey`: `String` (Unique index sparse – Khóa chống chạy song song cùng phạm vi).
  - `isStale`: `Boolean` (Tự động đánh dấu true nếu điểm số trong phạm vi bị chỉnh sửa sau lần tính).
  - `staleReason`: `String`.
  - `status`: `Enum["PENDING", "PROCESSING", "SUCCESS", "FAILED"]`.
  - `triggeredBy`: `ObjectId`, `startedAt`: `Date`, `finishedAt`: `Date`.
* **Indexes:** `{ scope: 1, ruleId: 1, startedAt: -1 }`, `{ lockKey: 1 }` (unique, sparse).

#### 3.25. `student_results` (Kết quả đo lường cá nhân từng sinh viên)
* **Fields:**
  - `_id`: `ObjectId`, `runId`: `ObjectId` $\rightarrow$ `calculation_runs._id`.
  - `level`: `String` Enum: `["CLO", "COURSE", "PI", "PLO"]`.
  - `subjectId`: `ObjectId` (ID của CLO / Course / PI / PLO tương ứng).
  - `sectionId`: `ObjectId` (Nullable nếu level là PI/PLO toàn khóa).
  - `studentId`: `ObjectId` $\rightarrow$ `students._id`.
  - `termId`: `ObjectId`.
  - `status`: `Enum["COMPUTED", "INSUFFICIENT_DATA", "NOT_APPLICABLE"]`.
  - `value`: `Double` ($0 \le x \le 1$, tỷ lệ đạt chuẩn hóa; Null nếu thiếu dữ liệu).
  - `passed`: `Boolean` ($value \ge threshold$).
  - `evidenceCount`: `Int` (Số lượng minh chứng tham gia tính toán).
  - `completeness`: `Object` `{ requiredCount: Int, availableCount: Int, ratio: Double }`.
  - `inputs`: `Array<Object>` (Truy vết 1 cấp liền kề):
    - `sourceType`: `Enum["SCORE", "STUDENT_RESULT"]`.
    - `refId`: `ObjectId` (Trỏ về `scores._id` hoặc `student_results._id`).
    - `normalizedValue`: `Double`, `weight`: `Double`.
* **Indexes:** `{ runId: 1, level: 1, subjectId: 1, studentId: 1 }` (unique), `{ studentId: 1, level: 1, subjectId: 1, termId: 1 }`.

#### 3.26. `aggregate_results` (Kết quả tổng hợp cấp Lớp / Học phần / CTĐT)
* **Fields:**
  - `_id`: `ObjectId`, `runId`: `ObjectId`, `level`: `Enum["CLO", "COURSE", "PI", "PLO"]`.
  - `subjectId`: `ObjectId`.
  - `scope`: `Object` `{ type: Enum["SECTION", "COURSE", "COHORT", "PROGRAM"], refId: ObjectId }`.
  - `counts`: `Object`:
    - `enrolled`: `Int` (Tổng số sinh viên đăng ký – Mẫu số 1).
    - `evaluable`: `Int` (Số sinh viên đủ dữ liệu đánh giá – Mẫu số 2).
    - `passed`: `Int`, `failed`: `Int`, `insufficientData`: `Int`.
  - `passRate`: `Double` ($\frac{passed}{evaluable}$).
  - `passRateOfEnrolled`: `Double` ($\frac{passed}{enrolled}$).
  - `targetPassRate`: `Double`, `targetMet`: `Boolean`.
  - `dataCompleteness`: `Double` ($\frac{evaluable}{enrolled}$).
* **Indexes:** `{ runId: 1, level: 1, subjectId: 1, "scope.refId": 1 }` (unique).

---

### NHÓM H: BÁO CÁO, CẢI TIẾN LIÊN TỤC (CQI) & NHẬT KÝ

#### 3.27. `reports`
* **Fields:** `_id`, `type`: `Enum["SECTION_CLO", "COURSE_PI", "PROGRAM_PLO", "DATA_COMPLETENESS"]`, `params`: `Object`, `runId`: `ObjectId`, `ruleId`: `ObjectId`, `programVersionId`: `ObjectId`, `summary`: `Object`, `limitations`: `String`, `exports`: `Array<{ format: String, fileRef: String, generatedAt: Date }>`, `generatedBy`: `ObjectId`, `createdAt`: `Date`.
* **Indexes:** `{ type: 1, programVersionId: 1, createdAt: -1 }`.

#### 3.28. `improvement_actions` (Đóng vòng cải tiến chất lượng - Closing the Loop)
* **Fields:**
  - `_id`: `ObjectId`, `programVersionId`: `ObjectId`, `courseId`: `ObjectId` (Nullable).
  - `issue`: `Object`:
    - `level`: `Enum["CLO", "PI", "PLO"]`, `subjectId`: `ObjectId`.
    - `reportId`: `ObjectId`, `aggregateResultId`: `ObjectId`.
    - `observedPassRate`: `Double`.
    - `description`: `String`, `rootCause`: `String` (Phân tích nguyên nhân gốc rễ).
  - `actionPlan`: `String`, `expectedTarget`: `Double` (Chỉ tiêu kỳ vọng, e.g., $0.75$).
  - `ownerId`: `ObjectId` (Cán bộ chịu trách nhiệm), `targetTermId`: `ObjectId` (Học kỳ áp dụng).
  - `dueDate`: `Date`, `priority`: `Enum["LOW", "MEDIUM", "HIGH", "CRITICAL"]`.
  - `status`: `Enum["OPEN", "IN_PROGRESS", "DONE", "VERIFIED", "CANCELLED"]`.
  - `evaluation`: `Object` (Đánh giá hiệu quả ở chu kỳ đo lường kế tiếp):
    - `evaluationRunId`: `ObjectId`, `newPassRate`: `Double`, `isEffective`: `Boolean`, `evaluatedBy`: `ObjectId`, `evaluatedAt`: `Date`, `completionNote`: `String`.
  - `history`: `Array<{ status: String, changedBy: ObjectId, changedAt: Date, note: String }>`.
  - `createdAt`, `updatedAt`: `Date`.
* **Indexes:** `{ programVersionId: 1, status: 1 }`, `{ ownerId: 1 }`, `{ targetTermId: 1 }`.

#### 3.29. `audit_logs`
* **Fields:** `_id`, `at`: `Date`, `userId`: `ObjectId`, `action`: `Enum["SCORE_UPDATE", "IMPORT_CONFIRM", "ASSESSMENT_APPROVE", "ASSESSMENT_REJECT", "RUN_CALCULATION", "RULE_CONFIRM", "LOGIN"]`, `entity`: `{ collection: String, id: ObjectId }`, `before`: `Object`, `after`: `Object`, `reason`: `String`, `ip`: `String`.
* **Indexes:** `{ "entity.collection": 1, "entity.id": 1 }`, `{ userId: 1, at: -1 }`.

#### 3.30. `ml_experiments` (Tùy chọn)
* **Fields:** `_id`, `name`: `String`, `type`: `Enum["BLOOM_CLASSIFICATION", "EARLY_WARNING"]`, `datasetSnapshot`: `Object`, `model`: `Object`, `metrics`: `Object`, `status`: `Enum["TRAINING", "EVALUATED", "DEPLOYED"]`, `disclaimer`: `String`.
* **Business Rule:** Chỉ lưu kết quả hỗ trợ phân tích; nghiêm cấm tự động ghi đè kết quả vào `student_results`.

---

## 4. MA TRẬN TÍNH TOÁN VÀ CÔNG THỨC NGHIỆP VỤ (COMPUTATION ENGINE)

### 4.1. Thuật toán đo lường CLO học phần của một sinh viên $s$
Giả sử học phần có bài thi $A$, câu hỏi $k$ được gán cặp $(CLO_j, PI_m)$ với phần điểm chia là $w_{k, j, m}$ ($\sum_{(j, m)} w_{k, j, m} = 1.0$) và điểm tối đa câu là $MaxScore_k$.

Điểm tối đa của câu dành cho $CLO_j$ là:
$$MaxScore_{k, j} = MaxScore_k \times \sum_{m} w_{k, j, m}$$

Điểm sinh viên $s$ đạt được cho $CLO_j$ tại câu $k$ là:
$$Score_{s, k, j} = RawScore_{s, k} \times \sum_{m} w_{k, j, m}$$

Điểm chuẩn hóa của sinh viên $s$ đối với $CLO_j$ qua toàn bộ bài thi của môn học:
$$Value(CLO_{s, j}) = \frac{\sum_{k \in CLO_j} Score_{s, k, j}}{\sum_{k \in CLO_j} MaxScore_{k, j}}$$
Sinh viên đạt $CLO_j \iff Value(CLO_{s, j}) \ge PassThreshold(CLO_j)$.

### 4.2. Thuật toán tổng hợp PI của sinh viên $s$
Hệ thống hỗ trợ 2 chế độ cấu hình trong `measurement_rules.pi.method`:

* **Chế độ 1: `DIRECT_FROM_ITEMS` (Tính trực tiếp từ câu hỏi)**
  $$Value(PI_{s, m}) = \frac{\sum_{k \in PI_m} RawScore_{s, k} \times \sum_{j} w_{k, j, m}}{\sum_{k \in PI_m} MaxScore_k \times \sum_{j} w_{k, j, m}}$$

* **Chế độ 2: `FROM_CLOS` (Tổng hợp từ kết quả CLO theo trọng số đề cương)**
  $$Value(PI_{s, m}) = \frac{\sum_{j \in PI_m} \left( Value(CLO_{s, j}) \times W_{CLO_j \to PI_m} \times IRM\_Factor_j \right)}{\sum_{j \in PI_m} \left( W_{CLO_j \to PI_m} \times IRM\_Factor_j \right)}$$
  *(Trong đó $IRM\_Factor_j = 1.0$ nếu `useIrmAsWeight = false`)*.

---

## 5. HƯỚNG DẪN THỰC THI CHO AGENT (IMPLEMENTATION DIRECTIVES)

Khi sinh mã nguồn backend (Spring Boot) và frontend (Angular), Agent cần tuân thủ các chỉ dẫn kỹ thuật sau:

1. **Transaction Boundaries:**
   - Việc xác nhận nạp file điểm (`import_batches` $\rightarrow$ `CONFIRMED`) phải diễn ra trong một Spring `@Transactional`. Bắt buộc ghi đồng thời: Cập nhật batch status, ghi hàng loạt `scores`, và ghi `audit_logs`.
   - Khi chạy đo lường (`calculation_runs`), phải cập nhật trạng thái `PENDING` $\rightarrow$ `PROCESSING` kèm khóa phân tán thông qua `lockKey` duy nhất để tránh Race Condition khi 2 giảng viên cùng bấm tính toán một lớp.
2. **Khóa cấu trúc đề thi (Assessment Immutability):**
   - Trước khi thực hiện lệnh Insert/Update điểm vào `scores`, backend bắt buộc kiểm tra trạng thái của `assessments`: Phải có `status = "APPROVED"` hoặc `"SCORE_ENTERED"`.
   - Ngăn chặn triệt để mọi API `PUT /api/assessments/{id}/items` nếu `assessment.isLocked == true`.
3. **Mô hình DTO và Validation:**
   - Dùng Bean Validation (`@NotNull`, `@Pattern`, `@Size`) ở tầng Controller để chặn dữ liệu sai định dạng trước khi chạm tới tầng MongoDB Driver.
   - Đối với các phép so sánh số thực (`totalPoints == sum(items.maxScore)`), luôn sử dụng dung sai sai số tuyệt đối $\epsilon = 0.001$ (`Math.abs(a - b) < 1e-3`) hoặc chuyển đổi sang `BigDecimal`.
4. **Cơ chế Caching & Aggregation:**
   - Không thực hiện tính toán động trên toàn bộ `scores` mỗi khi mở Dashboard. Luôn truy vấn từ collection kết quả đã tổng hợp sẵn (`aggregate_results` và `student_results`).
   - Nếu `scores` bị sửa đổi sau ngày chạy đo lường, kích hoạt cờ `calculation_runs.isStale = true` để hiển thị cảnh báo yêu cầu người dùng tính toán lại trên giao diện Angular.