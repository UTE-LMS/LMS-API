**GIẢI THÍCH CƠ SỞ DỮ LIỆU MONGODB**

**Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra chương trình đào tạo**

*(Learning Outcomes Management and Analytics System)*

Công nghệ: Spring Boot – Angular – MongoDB · Phiên bản tài liệu: v0.7 (25 collections) · File đi kèm: `database_import.js`

# 0. Thay đổi ở v0.7 (theo ADR 0014 & `dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`)

v0.7 tinh gọn cơ sở dữ liệu từ 29 xuống **25 collection** và loại bỏ toàn bộ các bảng/cột không phục vụ đo lường chuẩn đầu ra:

| **Nhóm thay đổi** | **Nội dung (v0.7 — ADR 0014)** |
|---|---|
| Khởi tạo CTĐT & Đề cương (`UNVERIFIED -> VERIFIED`) | Một vai trò Quản lý học thuật (`PROGRAM_MANAGER` / `ADMIN`) Import tệp CSV chuẩn hóa của CTĐT và Đề cương kèm sẵn ngưỡng đạt bắt buộc (`passThreshold` $\in (0, 1]$, không để trống) trên `plos`, `pis`, `clos` và trọng số đóng góp ($W_{PI \to PLO} \in (0, 1]$ với tổng mỗi PLO $= 1.0$; $W_{CLO \to PI} \in (0, 1]$ không ép tổng $= 1.0$). Dữ liệu vừa Import lưu `UNVERIFIED` để kiểm tra trên UI (sai thì sửa CSV import lại), bấm Lưu chuyển sang `VERIFIED` |
| Xóa 4 collection dư thừa (29 $\to$ 25 collection) | **Xóa hoàn toàn** 3 collection ngân hàng câu hỏi (`question_banks`, `question_categories`, `questions`) để bảo mật đề thi và **xóa hoàn toàn** collection `measurement_rules` (cùng cột `ruleId` trên các bảng kết quả/báo cáo) vì toàn bộ ngưỡng đạt và trọng số đã gắn trực tiếp trên `plos`, `pis`, `clos`, `clo_pi_links`, `assessments.cloWeights` |
| Bài đánh giá, `cloWeights` & Tự khóa ma trận | Giảng viên lớp học phần (`LECTURER`) thiết lập ma trận câu hỏi – CLO (`items[]`), khai báo tỷ lệ đóng góp của bài kiểm tra cho từng CLO xuất hiện trong bài (`cloWeights[]: { cloId, weight }` với $\sum_p W_{p,j} = 1.0$ khi tổng hợp học phần) và **tự khóa ma trận** (`DRAFT` $\to$ `LOCKED` $\to$ `SCORE_ENTERED`; bỏ `totalPoints`, `weightInCourse`, `dataGranularity`, `approval`, `exportedFiles`) |
| Xuất CSV mẫu & Nhập điểm trực tiếp (Vắng / Trống = `0.0`) | Hệ thống sinh tệp CSV mẫu từ ma trận đề đã khóa; giảng viên nhập điểm trực tiếp theo từng cặp `(itemId, cloId)`. Sinh viên vắng thi hoặc bỏ trống câu hỏi lưu trực tiếp `rawScore = 0.0` (xóa `scores.status`, `rubricLevel`, `emptyCellAs`, `reimportPolicy`, `columnMapping`) |
| Đo lường 4 cấp độ (`ASSESSMENT_CLO -> CLO -> PI -> PLO`) | Mở rộng `RESULT_LEVELS = ['ASSESSMENT_CLO', 'CLO', 'PI', 'PLO']` (bỏ `COURSE`). Cấp 1 (`ASSESSMENT_CLO`) gắn `assessmentId != null`; Cấp 2 (`CLO`) gắn `assessmentId = null`; Cấp 3 (`PI`) tính trung bình có trọng số chuẩn hóa trên tập CLO đã học (học lại lấy lần học gần nhất `LATEST`); Cấp 4 (`PLO`) tổng hợp từ các PI. Tách mẫu số $N_{\text{section}}$ (`ENROLLED`) và $N_{\text{cohort}}$ |

# 1. Mục đích và phạm vi

Tài liệu mô tả chi tiết **25 collection** của cơ sở dữ liệu MongoDB, đồng bộ 1–1 với `doc/database_import.js` và `doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`.

**Phạm vi đã chốt:** hệ thống chỉ **tiếp nhận và đo lường chuẩn đầu ra 4 cấp độ (`ASSESSMENT_CLO`, `CLO`, `PI`, `PLO`) từ kết quả đánh giá sau khi thi**. Hệ thống **không tổ chức thi**, **không lưu nội dung câu hỏi/đề thi/đáp án**, **không chấm điểm tổng kết bài kiểm tra** và **không xét điểm qua môn học phần (`COURSE`)**.

**Chuỗi nghiệp vụ được hỗ trợ:**

```
Điểm từng CLO của từng câu hỏi → ASSESSMENT_CLO (từng bài kiểm tra) → CLO (toàn học phần) → PI → PLO → Báo cáo → Hành động cải tiến
```

# 2. Tổng quan: 25 collection chia thành 8 nhóm

Gồm **25 collection chính** (đã loại bỏ `ml_experiments`, `question_banks`, `question_categories`, `questions`, `measurement_rules`).

| **Nhóm** | **Collection** |
|---|---|
| A. Người dùng | `users` |
| B. Chương trình đào tạo | `programs`, `program_versions`, `plos`, `pis`, `courses`, `program_courses`, `course_pi_contributions` |
| C. Học phần & CLO | `syllabi`, `clos`, `clo_pi_links` |
| D. Bài đánh giá & Ma trận CLO | `assessments` |
| E. Học kỳ, lớp, sinh viên | `terms`, `course_sections`, `students`, `enrollments` |
| F. Nhập điểm CLO | `import_batches`, `import_row_errors`, `scores` |
| G. Đo lường chuẩn đầu ra (4 cấp) | `calculation_runs`, `student_results`, `aggregate_results` |
| H. Báo cáo, cải tiến, hỗ trợ | `reports`, `improvement_actions`, `audit_logs` |

## Sơ đồ quan hệ chính

```
programs ─< program_versions ─┬─< plos (passThreshold) ─< pis (weight, passThreshold)
                              ├─< program_courses >─ courses
                              └─< course_pi_contributions (I/R/M) >─ courses, pis
```

```
courses ─< syllabi (đề cương) ─┬─< clos (measurement.defaultPassThreshold) ─< clo_pi_links (weight) >─ pis
                               └─< assessments (cloWeights[{cloId, weight}], items[{code, outcomeMappings[{cloId, maxScore}]}])
```

```
terms ─< course_sections ─< enrollments (ENROLLED / WITHDRAWN) >─ students
             ├─< import_batches ─< import_row_errors
             └─< scores (sectionId × assessmentId × itemId × cloId × studentId, rawScore >= 0)
```

```
calculation_runs ─┬─< student_results   (level: ASSESSMENT_CLO | CLO | PI | PLO)
                  └─< aggregate_results (level: ASSESSMENT_CLO | CLO | PI | PLO)
                             └─< reports ─< improvement_actions
users, audit_logs: dùng chung cho toàn hệ thống
```

# 3. Chi tiết từng collection

## Nhóm A — Người dùng

### 3.1 `users` — người dùng và phân quyền
- Các trường: `username`, `email`, `passwordHash`, `fullName`, `status` (`ACTIVE` / `LOCKED`), `tokenVersion`, `roleAssignments[]` (`role` $\in$ `ADMIN | PROGRAM_MANAGER | DEPARTMENT_HEAD | QA_OFFICER | LECTURER | STUDENT | VIEWER`, `scope = { programIds[], courseIds[], sectionIds[] }`).
- Chỉ mục: `username` (unique), `email` (unique).

## Nhóm B — Chương trình đào tạo

### 3.2 `programs` & 3.3 `program_versions`
- `programs`: `code` (unique), `name`, `level`, `degree`, `extra`.
- `program_versions`: `programId`, `version` (unique cùng `programId`), `applicableCohorts[]`, `totalCredits`, `durationYears`, `status` (`DRAFT | ACTIVE | ARCHIVED`), `verification`.

### 3.4 `plos` — chuẩn đầu ra chương trình
- Các trường bắt buộc: `programVersionId`, `code` (`^PLO[1-9][0-9]*$`), `displayOrder`, **`passThreshold` $\in (0, 1]$ (ngưỡng đạt bắt buộc của PLO, không được để trống)**, `verification`.
- Các trường tùy chọn: `category`, `contentOriginal`, `contentSummary`, `capabilityLevelRaw` (1–6), `reviewNote`.
- Chỉ mục: `{ programVersionId: 1, code: 1 }` (unique).

### 3.5 `pis` — chỉ số thực hiện
- Các trường bắt buộc: `programVersionId`, `ploId`, `code` (`^PI[1-9][0-9]*\.[1-9][0-9]*$`), `displayOrder`, **`weight` $\in (0, 1]$ (trọng số đóng góp $W_{PI \to PLO}$, tổng các PI thuộc cùng PLO $= 1.0$)**, **`passThreshold` $\in (0, 1]$ (ngưỡng đạt bắt buộc của PI, không được để trống)**, `verification`.
- Đã bỏ cột `targetPassRate` (thay bằng `passThreshold` bắt buộc).
- Chỉ mục: `{ programVersionId: 1, code: 1 }` (unique), `{ ploId: 1 }`.

### 3.6 `courses`, 3.7 `program_courses`, 3.8 `course_pi_contributions`
- `courses`: `code` (unique), `name`, `credits`, `extra`.
- `program_courses`: `{ programVersionId, courseId }` (unique), `semester`, `credits`, `type` (`COMPULSORY | ELECTIVE`), `category`, `prerequisiteCourseIds[]`, `previousCourseIds[]`.
- `course_pi_contributions`: `{ programVersionId, courseId, piId }` (unique), `contribution` (`I | R | M`, không dùng làm trọng số tính điểm), `verification`.

## Nhóm C — Học phần & CLO

### 3.9 `syllabi` — phiên bản đề cương học phần
- `courseId`, `version` (unique cùng `courseId`), `effectiveFromTermId`, `effectiveToTermId`, `status` (`DRAFT | CONFIRMED | ARCHIVED`), `verification`.

### 3.10 `clos` — chuẩn đầu ra học phần
- Các trường bắt buộc: `syllabusId`, `courseId`, `code` (`^CLO[0-9]+$`), `contentOriginal`, **`measurement: { defaultPassThreshold }` với `defaultPassThreshold` $\in (0, 1]$ bắt buộc không được để trống** (đã xóa `defaultWeight`).
- Chỉ mục: `{ syllabusId: 1, code: 1 }` (unique).

### 3.11 `clo_pi_links` — liên kết CLO ↔ PI
- Các trường bắt buộc: `programVersionId`, `syllabusId`, `cloId`, `piId`, **`weight` $\in (0, 1]$ (trọng số đóng góp $W_{CLO_{c,j} \to PI_m}$, bắt buộc không để trống; không ép tổng qua các học phần $= 1.0$)**.
- Chỉ mục: `{ cloId: 1, piId: 1 }` (unique), `{ piId: 1 }`, `{ programVersionId: 1 }`.

## Nhóm D — Bài đánh giá & Ma trận Câu hỏi – CLO

### 3.12 `assessments` — bài đánh giá và ma trận câu hỏi – CLO
- Các trường chính:
  - `syllabusId`, `code`, `name`, `scope` (`SYLLABUS_TEMPLATE | SECTION`), `sectionId`, `templateId`, `type`, `phase`, `scheduledAt`.
  - `status` (`DRAFT | LOCKED | SCORE_ENTERED`), `isLocked` (`true` khi `status` $\in \{ \text{LOCKED}, \text{SCORE_ENTERED} \}$).
  - **`cloWeights[]`:** Danh sách `{ cloId, weight }` ($0 < weight \le 1.0$) biểu diễn tỷ lệ đóng góp $W_{p,j}$ của bài kiểm tra cho từng CLO xuất hiện trong bài (khi khóa bài lẻ: $\sum_p W_{p,j} \le 1.0$; khi chạy tổng hợp học phần Cấp độ 2: $\sum_{p \in P(CLO_j)} W_{p,j} = 1.0$).
  - `items[]`: Danh sách câu hỏi ẩn danh `{ _id, code, orderIndex, bloomLevel, outcomeMappings: [{ cloId, maxScore }] }` ($maxScore > 0$).
  - `coverageSummary`: `{ totalItemsSum, byClo[], byBloom[] }`.
- Đã loại bỏ hoàn toàn: `totalPoints`, `weightInCourse`, `dataGranularity`, `approval`, `exportedFiles`, và các trường nội dung câu hỏi (`title`, `kind`, `maxScore` cấp câu hỏi, `topic`, `questionId`, `questionVersion`, `externalRef`, `rubric`).

## Nhóm E — Học kỳ, lớp học phần, sinh viên

### 3.13 `terms`, 3.14 `course_sections`, 3.15 `students`, 3.16 `enrollments`
- `terms`: `code` (unique), `academicYear`, `startDate`, `endDate`, `status` (`OPEN | SCORE_LOCKED | CLOSED`).
- `course_sections`: `code` (unique), `courseId`, `syllabusId`, `termId`, `lecturerIds[]`, `enrolledCount`, `status` (`OPEN | CLOSED | LOCKED`).
- `students`: `studentRef` (unique), `userId`, `cohort`, `majorCode`, `programVersionId`, `fullName`, `email`, `status`.
- `enrollments`: `{ sectionId, studentId }` (unique), `status` (`ENROLLED | WITHDRAWN`), `enrolledAt`. Tập sinh viên có `status = ENROLLED` của lớp là mẫu số $N_{\text{section}}$.

## Nhóm F — Nhập điểm CLO

### 3.17 `import_batches` & 3.18 `import_row_errors`
- `import_batches`: `sectionId`, `assessmentId`, `fileName`, `fileChecksum`, `fileRef`, `status` (`UPLOADED | VALIDATED | HAS_ERRORS | CONFIRMED | ROLLED_BACK`), `stats`, `uploadedBy`, `confirmedBy`, `confirmedAt` (đã loại bỏ các cột dư thừa `sourceSystem`, `columnMapping`, `emptyCellAs`, `reimportPolicy`).
- `import_row_errors`: `batchId`, `rowNumber`, `column`, `errorType`, `rawValue`, `message`.

### 3.19 `scores` — điểm theo từng cặp (câu hỏi, CLO) của sinh viên
- Các trường bắt buộc: `sectionId`, `assessmentId`, `itemId`, `cloId`, `studentId`, **`rawScore` ($\ge 0.0$)**, **`maxScore` ($> 0.0$)**, thỏa mãn $0 \le \text{rawScore} \le \text{maxScore}$.
- Sinh viên vắng thi hoặc bỏ trống câu hỏi được ghi nhận trực tiếp `rawScore = 0.0`.
- Đã loại bỏ hoàn toàn: `status` (`SCORE_STATUS_VALUES`), `rubricLevel`, `revision`.
- Chỉ mục: `{ sectionId: 1, assessmentId: 1, itemId: 1, cloId: 1, studentId: 1 }` (unique).

## Nhóm G — Đo lường chuẩn đầu ra 4 cấp độ (`ASSESSMENT_CLO`, `CLO`, `PI`, `PLO`)

### 3.20 `calculation_runs`
- Các trường: `scope` (`SECTION | COHORT | PROGRAM`), `scopeRef`, `lockKey`, `programVersionId`, `syllabusIds[]`, `isOfficial`, `dataSnapshot`, `isStale`, `staleReason`, `status` (`PENDING | PROCESSING | SUCCESS | FAILED`), `errorMessage`, `triggeredBy`, `startedAt`, `finishedAt`.
- Đã loại bỏ cột `ruleId`.

### 3.21 `student_results` — kết quả chuẩn đầu ra 4 cấp độ của từng sinh viên
- `runId`, **`level` $\in$ `['ASSESSMENT_CLO', 'CLO', 'PI', 'PLO']`** (đã bỏ `'COURSE'`, thêm `'ASSESSMENT_CLO'`), `subjectId`, `sectionId`, **`assessmentId` (khác `null` khi `level = 'ASSESSMENT_CLO'`, bằng `null` ở các cấp còn lại)**, `studentId`, `termId`.
- `status` (`COMPUTED | INSUFFICIENT_DATA | NOT_APPLICABLE`), `value` $\in [0, 1]$, `passThreshold` $\in (0, 1]$, `passed` (`true | false`), `completeness`, `inputs[]`, `programVersionId`, `syllabusId` (đã loại bỏ `ruleId`, `evidenceCount`).
- Chỉ mục duy nhất: `{ runId: 1, level: 1, subjectId: 1, sectionId: 1, assessmentId: 1, studentId: 1 }`.

### 3.22 `aggregate_results` — kết quả thống kê tổng hợp 4 cấp độ
- `runId`, **`level` $\in$ `['ASSESSMENT_CLO', 'CLO', 'PI', 'PLO']`**, `subjectId`, `sectionId`, `assessmentId`, `scope` (`type` $\in$ `ASSESSMENT | SECTION | COHORT | PROGRAM`, `refId`, `cohort`, `termId`).
- `counts: { enrolled, passed, failed }` (trong đó `enrolled` là $N_{\text{section}}$ ở Cấp 1–2 và $N_{\text{cohort}}$ ở Cấp 3–4; đã bỏ `evaluable`, `insufficientData`), `mean`, `passRate`, `passThreshold`, `dataCompleteness`, `programVersionId` (đã bỏ `passRateOfEnrolled`, `targetPassRate`, `targetMet`, `ruleId`).
- Chỉ mục duy nhất: `{ runId: 1, level: 1, subjectId: 1, sectionId: 1, assessmentId: 1, 'scope.type': 1, 'scope.refId': 1, 'scope.cohort': 1, 'scope.termId': 1 }`.

## Nhóm H — Báo cáo, cải tiến, hỗ trợ

### 3.23 `reports`, 3.24 `improvement_actions`, 3.25 `audit_logs`
- `reports`: `type` (`SECTION_CLO | COURSE_PI | PROGRAM_PLO | DATA_COMPLETENESS`), `title`, `params`, `runId`, `programVersionId`, `summary`, `limitations`, `exports[]`, `generatedBy`, `createdAt` (đã bỏ `ruleId`).
- `improvement_actions`: `programVersionId`, `courseId`, `issue`, `actionPlan`, `expectedTarget`, `ownerId`, `targetTermId`, `dueDate`, `priority`, `status`, `evaluation`, `history[]`.
- `audit_logs`: `at`, `userId`, `action` (`SCORE_UPDATE | IMPORT_CONFIRM | ASSESSMENT_LOCK | ASSESSMENT_UNLOCK | RUN_CALCULATION | LOGIN`), `entity`, `before`, `after`, `reason`, `ip` (đã bỏ `RULE_CONFIRM`).
