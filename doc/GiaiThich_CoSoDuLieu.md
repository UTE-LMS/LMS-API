**GIẢI THÍCH CƠ SỞ DỮ LIỆU MONGODB**

**Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra chương trình đào tạo**

*(Learning Outcomes Management and Analytics System)*

Công nghệ: Spring Boot – Angular – MongoDB · Phiên bản tài liệu: v0.4 (dự thảo) · File đi kèm: `database_import.js`

# 1. Mục đích và phạm vi

Tài liệu mô tả chi tiết các collection của cơ sở dữ liệu MongoDB, để giảng viên hướng dẫn xem xét và cho ý kiến trước khi triển khai.

**Phạm vi đã chốt:** hệ thống chỉ **tiếp nhận và đo lường các kết quả có sẵn sau khi thi** (điểm theo câu hỏi/tiêu chí, rubric). Hệ thống **không tổ chức thi**: không tự sinh đề, không có quy trình duyệt đề. Có **ngân hàng câu hỏi dạng danh mục** để lưu các câu hỏi của đề đã thi cùng ánh xạ CLO–PI, mức Bloom, nhằm khai báo một lần và dùng lại qua nhiều đề, nhiều học kỳ. Bài đánh giá trong cơ sở dữ liệu *khai báo cấu trúc của bài đã thi* (câu nào được dùng, điểm tối đa, đo cặp CLO–PI nào) để quy điểm lên chuẩn đầu ra.

**Về "minh chứng":** các tài liệu, dữ liệu nhóm thu thập trong quá trình khảo sát và xây dựng hệ thống (bản mô tả CTĐT, đề cương, quy trình đo lường, bảng điểm…) là hồ sơ của dự án, **không phải dữ liệu hệ thống quản lý khi vận hành**, nên không có collection quản lý danh mục này. Nguồn của từng PLO, PI, CLO chỉ được ghi ngắn gọn (tên tài liệu, trang) trong khối `source`. Hệ thống cũng không lưu bài làm của sinh viên; chỉ lưu điểm và truy vết về file điểm gốc.

**Chuỗi nghiệp vụ được hỗ trợ:**

```
Điểm từng câu hỏi/tiêu chí → CLO và PI → PLO → Báo cáo → Hành động cải tiến
```

**Nguồn thiết kế:** hai tài liệu `BS.md`, `Misa.md` (kế hoạch dự án và hướng dẫn đọc CTĐT), nội dung trao đổi kỹ thuật bổ sung và file góp ý (`fb.md`) về ngân hàng câu hỏi và khái niệm minh chứng. Những điểm chưa được Khoa CNTT xác nhận đều được thiết kế dạng **cấu hình có trạng thái xác nhận** thay vì cố định trong mã (xem mục 8).

# 2. Nguyên tắc thiết kế

| **#** | **Nguyên tắc** | **Cách thể hiện** |
|---|---|---|
| 1 | Mọi dữ liệu và kết quả gắn với **phiên bản** CTĐT, đề cương, quy tắc đo lường | `programVersionId`, `syllabusId`, `ruleId` có trong kết quả; báo cáo cũ vẫn đọc được khi quy tắc đổi |
| 2 | Mã CLO chỉ có nghĩa trong một học phần (CLO1 của OOP khác CLO1 của Cơ sở dữ liệu) | CLO nhận diện bằng `_id`; `code` chỉ duy nhất trong một đề cương |
| 3 | PLO/PI nhận diện bằng **mã**, không dùng thứ tự dòng (tài liệu xếp PLO1, 6, 7, 4, 5, 2, 3) | `code` và `displayOrder` là hai trường riêng |
| 4 | Lưu **nội dung nguyên văn, nguồn, trang, người xác nhận**; tách "tài liệu nói gì" khỏi "nhóm diễn giải" | Khối `source`, `verification`, `reviewNote` |
| 5 | Mức Bloom ≠ điểm số; ký hiệu I/R/M ≠ trọng số; mức năng lực của PLO không suy ra bằng trung bình các PI | Lưu riêng từng loại, **không có trường quy đổi** giữa chúng |
| 6 | Công thức, trọng số, ngưỡng đạt phải được người có thẩm quyền xác nhận | `measurement_rules` có trạng thái `DRAFT/CONFIRMED`; chỉ quy tắc đã xác nhận mới cho kết luận chính thức |
| 7 | Dữ liệu thiếu **không mặc định bằng 0**; riêng câu bỏ trống của sinh viên đã dự thi tính 0 có ghi nhận | `scores.status`: `SCORED`, `BLANK`, `ABSENT`, `NOT_GRADED`, `EXEMPT`, `MISSING` |
| 8 | Điểm theo **từng câu hỏi/tiêu chí**; mỗi câu gắn CLO, PI và mức Bloom; một câu có thể đo nhiều cặp (CLO, PI) | `outcomeMappings[]` gồm các cặp `{cloId, piId, weight}` trong `questions` và `assessments.items[]`; `bloomLevel`, `maxScore` |
| 9 | Tỷ lệ đạt phải rõ mẫu số; báo cáo kèm số sinh viên chưa đủ dữ liệu | `aggregate_results.counts` (ghi danh, đủ dữ liệu, đạt, chưa đủ dữ liệu) |
| 10 | Truy vết từ kết quả về dữ liệu gốc | `student_results.inputs[]` → `scores` → `import_batches` + số dòng trong file gốc |
| 11 | Angular không truy cập MongoDB; backend kiểm tra quyền, dữ liệu và thực hiện công thức đo lường | Phân quyền theo `users.roleAssignments`; công thức đọc từ `measurement_rules` |
| 12 | PI gắn cho câu hỏi chỉ được chọn trong ánh xạ CLO–PI đã duyệt của chính CLO đó; không cho gắn tùy ý | Dịch vụ kiểm tra cặp (CLO, PI) có trong `clo_pi_links`; chạy tính chính thức yêu cầu liên kết đã `VERIFIED` |
| 13 | Chỉ thu thập thông tin cần thiết, ưu tiên mã sinh viên thay thế; không lưu bài làm của sinh viên | `students.studentRef` |
| 14 | Kết quả học máy (nếu có) chỉ hỗ trợ, không thay kết quả chính thức | `ml_experiments` tách riêng, không ghi vào `student_results` |

# 3. Tổng quan: 30 collection chia thành 8 nhóm

Gồm **29 collection chính** và **1 collection tùy chọn** (`ml_experiments`).

| **Nhóm** | **Collection** |
|---|---|
| A. Người dùng | `users` |
| B. Chương trình đào tạo | `programs`, `program_versions`, `plos`, `pis`, `courses`, `program_courses`, `course_pi_contributions` |
| C. Học phần & CLO | `syllabi`, `clos`, `clo_pi_links` |
| D. Ngân hàng câu hỏi & bài đánh giá | `question_banks`, `question_categories`, `questions`, `assessments` |
| E. Học kỳ, lớp, sinh viên | `terms`, `course_sections`, `students`, `enrollments` |
| F. Nhập điểm | `import_batches`, `import_row_errors`, `scores` |
| G. Đo lường | `measurement_rules`, `calculation_runs`, `student_results`, `aggregate_results` |
| H. Báo cáo, cải tiến, hỗ trợ | `reports`, `improvement_actions`, `audit_logs`, `ml_experiments` (tùy chọn) |

## Sơ đồ quan hệ chính

```
programs ─< program_versions ─┬─< plos ─< pis
                              ├─< program_courses >─ courses
                              └─< course_pi_contributions (I/R/M) >─ courses, pis
```

```
courses ─< syllabi (đề cương) ─┬─< clos ─< clo_pi_links >─ pis
                               ├─< question_banks ─┬─< question_categories (cây 3 cấp: chương → chủ đề → nhóm nhỏ)
                               │                   └─< questions (thuộc 1 danh mục; mỗi câu đo các cặp CLO–PI)
                               └─< assessments
                                     ├─ scope = SYLLABUS_TEMPLATE : khung theo đề cương
                                     └─ scope = SECTION           : bản của từng lớp, chứa items[]
                                            (mỗi item là bản sao của một câu hỏi hoặc một tiêu chí rubric,
                                             kèm cặp CLO–PI và điểm tối đa)
```

```
terms ─< course_sections ─< enrollments >─ students
             ├─< import_batches ─< import_row_errors
             └─< scores  (sinh viên × câu hỏi/tiêu chí)
```

```
measurement_rules ─< calculation_runs ─┬─< student_results   (CLO / COURSE / PI / PLO của từng sinh viên)
                                       └─< aggregate_results (cấp lớp / khóa / CTĐT)
                                                  └─< reports ─< improvement_actions
users, audit_logs: dùng chung cho toàn hệ thống
```

**Quy ước chung**

- Khóa chính `_id` kiểu `ObjectId`. Mã nghiệp vụ (`code`) là trường riêng, có chỉ mục duy nhất trong phạm vi phù hợp.
- Mỗi collection có `createdAt`, `updatedAt` (và `createdBy`, `updatedBy` khi triển khai).
- Các collection danh mục (`programs`, `program_versions`, `courses`, `syllabi`, `clos`, `assessments`) có vùng `extra: {}` để mở rộng trường linh hoạt mà không phải đổi cấu trúc. Vùng này không được dùng làm đầu vào của công thức đo lường nếu chưa đưa vào cấu trúc chính thức.
- MongoDB không có khóa ngoại; các ràng buộc liên kết do tầng dịch vụ Spring Boot kiểm tra trước khi ghi.
- Khối dùng lại ở nhiều nơi:
   - `verification`: `{status: UNVERIFIED | VERIFIED | NEEDS_CLARIFICATION, verifiedBy, verifiedAt, note}` — trạng thái xác nhận nghiệp vụ.

# 4. Chi tiết từng collection

## Nhóm A — Người dùng

### 4.1 users — người dùng và phân quyền

**Mục đích:** đăng nhập và phân quyền theo vai trò, phạm vi phụ trách.

| **Trường** | **Ý nghĩa** |
|---|---|
| `username`, `email`, `passwordHash`, `fullName` | Thông tin tài khoản (mật khẩu chỉ lưu băm) |
| `status` | `ACTIVE` / `LOCKED` |
| `roleAssignments[]` | Mỗi phần tử: `role` (`ADMIN`, `PROGRAM_MANAGER`, `QA_OFFICER`, `LECTURER`, `VIEWER`) và `scope` (danh sách CTĐT/học phần/lớp được phụ trách) |

**Chỉ mục:** `username`, `email` duy nhất. Quyền chi tiết theo hành vi (nhập điểm, xác nhận nhập, xác nhận quy tắc, chạy tính toán…) được định nghĩa trong mã backend, ánh xạ từ `role`; ma trận quyền dự kiến nằm trong tài liệu thiết kế. **Dữ liệu khởi tạo:** chưa tạo tài khoản nào.

## Nhóm B — Chương trình đào tạo

### 4.2 programs — chương trình đào tạo

| **Trường** | **Ý nghĩa** |
|---|---|
| `code`, `name` | Mã nội bộ và tên ngành (Công nghệ thông tin) |
| `level`, `degree` | Trình độ, văn bằng (chờ đọc từ phần thông tin chung của bản CTĐT) |

**Chỉ mục:** `code` duy nhất. **Khởi tạo:** 1 dòng (`CNTT`).

### 4.3 program_versions — phiên bản CTĐT

**Mục đích:** một CTĐT có thể có nhiều phiên bản áp dụng cho các khóa khác nhau; PLO/PI/ma trận đều gắn với phiên bản để báo cáo cũ không bị sai khi CTĐT cập nhật.

| **Trường** | **Ý nghĩa** |
|---|---|
| `programId` | Tham chiếu `programs` |
| `version` | Tên phiên bản (khởi tạo: `BM7A-2.7-Updated-2026`) |
| `applicableCohorts[]` | Các khóa tuyển sinh áp dụng (chưa biết — câu hỏi Q01) |
| `status` | `DRAFT` / `ACTIVE` / `ARCHIVED` |
| `verification` | Trạng thái xác nhận |

**Chỉ mục:** (`programId`, `version`) duy nhất.

### 4.4 plos — chuẩn đầu ra chương trình (7 PLO)

| **Trường** | **Ý nghĩa** |
|---|---|
| `programVersionId` | Thuộc phiên bản CTĐT nào |
| `code` | `PLO1`…`PLO7` (định dạng được kiểm tra) |
| `displayOrder` | Thứ tự trình bày trong tài liệu (PLO1, 6, 7, 4, 5, 2, 3) — tách khỏi mã |
| `contentOriginal` | Nguyên văn trong CTĐT (để trống cho đến khi đối chiếu bản gốc) |
| `contentSummary` | Diễn giải ngắn |
| `capabilityLevelRaw` | Số ghi trong cột "Trình độ năng lực" của tài liệu; lưu đúng như nguồn, **không** tính lại từ PI |
| `verification`, `reviewNote` | Trạng thái xác nhận, nhận xét cần làm rõ |

**Chỉ mục:** (`programVersionId`, `code`) duy nhất. **Khởi tạo:** 7 dòng; mức năng lực PLO1=3, PLO2=4, PLO3=3, PLO4=3, PLO5=4, PLO6=6, PLO7=6.

### 4.5 pis — chỉ số thực hiện (17 PI)

| **Trường** | **Ý nghĩa** |
|---|---|
| `ploId` | PLO mà PI thuộc về (một PLO có nhiều PI) |
| `code` | `PI1.1`…`PI7.2` (định dạng được kiểm tra) |
| `contentOriginal`, `contentShort` | Nguyên văn (chờ đối chiếu) và nội dung rút gọn |
| `capabilityLevelRaw` | Số ghi trong tài liệu (ví dụ PI6.1=4, PI6.2=6, PI6.3=5) |
| `targetPassRate` | Mục tiêu tỷ lệ đạt do đơn vị quy định (chưa có) |
| `verification`, `reviewNote` | Trạng thái, ghi chú |

**Chỉ mục:** (`programVersionId`, `code`) duy nhất; `ploId`. **Khởi tạo:** 17 dòng. PI2.2, PI7.1, PI7.2 được ghi chú cần làm rõ thang mức năng lực (động từ và mức ghi trong tài liệu chưa tương ứng — Q03).

### 4.6 courses — học phần

Học phần là *môn học có mã và nội dung xác định*, khác với *lớp học phần* (một lần tổ chức giảng dạy trong học kỳ).

| **Trường** | **Ý nghĩa** |
|---|---|
| `code`, `name`, `credits` | Mã (ví dụ `OOPR230279`), tên, số tín chỉ |

**Chỉ mục:** `code` duy nhất. **Khởi tạo:** học phần `OOPR230279 — Lập trình hướng đối tượng` (các học phần thử nghiệm khác sẽ nhập khi Khoa xác nhận danh sách).

### 4.7 program_courses — vị trí học phần trong CTĐT

| **Trường** | **Ý nghĩa** |
|---|---|
| `programVersionId`, `courseId` | Học phần thuộc phiên bản CTĐT nào |
| `semester`, `credits`, `type` | Học kỳ dự kiến, tín chỉ, bắt buộc/tự chọn |
| `category` | Nhóm: cơ sở / cơ sở ngành / chuyên ngành / đồ án / khóa luận |
| `prerequisiteCourseIds[]` | Học phần tiên quyết |

**Chỉ mục:** (`programVersionId`, `courseId`) duy nhất. **Khởi tạo:** chưa có (cần danh mục học phần từ bản CTĐT).

### 4.8 course_pi_contributions — ma trận học phần đóng góp vào PI (Phụ lục II)

**Mục đích:** lưu mức **I** (Introduced – giới thiệu), **R** (Reinforced – củng cố), **M** (Mastered – thành thạo) mà mỗi học phần được bố trí cho từng PI. Đây là *thiết kế đóng góp dự kiến*, không phải kết quả của sinh viên; không dùng làm trọng số tính điểm.

| **Trường** | **Ý nghĩa** |
|---|---|
| `programVersionId`, `courseId`, `piId` | Bộ ba xác định một ô trong ma trận |
| `contribution` | `I`, `R` hoặc `M` |
| `verification` | Trạng thái xác nhận |

Ô trống trong ma trận nghĩa là *không có bản ghi*, không kết luận học phần "không liên quan" về nội dung.

**Chỉ mục:** (`programVersionId`, `courseId`, `piId`) duy nhất; `piId`. **Khởi tạo:** 4 dòng của OOPR230279 (trang 46): PI1.2=R, PI6.1=R, PI6.2=I, PI7.1=R.

## Nhóm C — Học phần & CLO

### 4.9 syllabi — phiên bản đề cương học phần

| **Trường** | **Ý nghĩa** |
|---|---|
| `courseId`, `version` | Học phần và phiên bản đề cương |
| `effectiveFromTermId`, `effectiveToTermId` | Học kỳ bắt đầu/kết thúc áp dụng |
| `status` | `DRAFT` / `CONFIRMED` / `ARCHIVED` |

**Chỉ mục:** (`courseId`, `version`) duy nhất. **Khởi tạo:** chưa có (chờ đề cương chính thức).

### 4.10 clos — chuẩn đầu ra học phần

| **Trường** | **Ý nghĩa** |
|---|---|
| `syllabusId`, `courseId` | CLO thuộc đề cương nào |
| `code` | `CLO1`, `CLO2`… (duy nhất **trong một đề cương**) |
| `contentOriginal` | Nội dung nguyên văn trong đề cương |
| `bloomLevel` (1–6), `bloomNote` | Mức tư duy theo Bloom — phân loại nhiệm vụ, không phải điểm số |
| `verification` | `Trạng thái xác nhận` |
| `measurement` | Cấu hình đo riêng của CLO: `weightInCourse` (tỷ trọng trong học phần), `passThreshold` (ngưỡng đạt riêng); để trống cho đến khi được xác nhận |

**Chỉ mục:** (`syllabusId`, `code`) duy nhất.

### 4.11 clo_pi_links — liên kết CLO ↔ PI (nhiều–nhiều)

Một CLO có thể đóng góp nhiều PI; một PI được nhiều CLO của nhiều học phần cùng đo.

| **Trường** | **Ý nghĩa** |
|---|---|
| `cloId`, `piId`, `syllabusId`, `programVersionId` | Hai đầu liên kết và phiên bản áp dụng |
| `weight` | Trọng số (để trống cho đến khi quy tắc được xác nhận) |

Vai trò: hiển thị ma trận CLO–PI và **kiểm tra nhất quán** với PI gán trực tiếp cho câu hỏi. **Chỉ mục:** (`cloId`, `piId`) duy nhất. **Khởi tạo:** chưa có.

## Nhóm D — Ngân hàng câu hỏi & bài đánh giá

**Vai trò của nhóm này.** Ngân hàng câu hỏi là *danh mục các câu hỏi của đề đã thi* (nhập từ Moodle, Excel hoặc nhập tay). Mỗi câu được khai báo một lần: thuộc danh mục nội dung nào, mức Bloom nào, đo cặp CLO–PI nào. Khi lập bài đánh giá cho một lớp, câu hỏi được sao chép vào bài kèm ánh xạ tại thời điểm thi. Ngân hàng **không** sinh đề tự động và **không** có quy trình duyệt đề.

**Cách sắp xếp danh mục.** Có hai loại thông tin, cần phân biệt:

| **Loại** | **Lưu ở đâu** | **Vì sao** |
|---|---|---|
| **Danh mục nội dung** (cây): Chương/Phần → Chủ đề → Nhóm nhỏ | `question_categories` | Mỗi câu hỏi chỉ thuộc **một** vị trí nội dung, nên phù hợp với cây phân cấp; xem được theo chương, theo chủ đề |
| **Thuộc tính phân loại**: CLO, PI, mức Bloom, loại câu hỏi, độ khó, trạng thái | Trường của `questions` | Một câu có thể đo **nhiều** CLO/PI nên không thể đặt vào cây; lọc bằng chỉ mục |

Các cách duyệt ngân hàng: theo chương/chủ đề (đi theo cây), theo CLO, theo PI, theo mức Bloom, theo loại câu hỏi, theo trạng thái (đều có chỉ mục). Thống kê độ phủ (số câu theo CLO × mức Bloom) tính được bằng truy vấn tổng hợp trên `questions`.

### 4.12 question_banks — ngân hàng câu hỏi

Một học phần có thể có một hoặc nhiều ngân hàng (ví dụ ngân hàng trắc nghiệm, ngân hàng tự luận). Không bắt buộc mỗi CLO một ngân hàng riêng: một ngân hàng chứa câu hỏi của nhiều CLO.

| **Trường** | **Ý nghĩa** |
|---|---|
| `code`, `name`, `description` | Mã (duy nhất), tên, mô tả |
| `courseId`, `syllabusId` | Học phần và **phiên bản đề cương** mà ngân hàng áp dụng — bắt buộc, vì CLO chỉ có nghĩa trong một đề cương |
| `sourceSystem`, `externalBankId` | Nguồn: `INTERNAL` / `MOODLE` / `EXCEL_IMPORT`; mã ngân hàng ở hệ thống ngoài (nếu có) |
| `status` | `ACTIVE` / `ARCHIVED` |

**Chỉ mục:** `code` duy nhất; (`courseId`, `syllabusId`). **Khởi tạo:** rỗng.

### 4.13 question_categories — danh mục nội dung (cây tối đa 3 cấp)

Mỗi ngân hàng có một cây danh mục riêng: cấp 1 là Chương/Phần, cấp 2 là Chủ đề, cấp 3 là Nhóm nhỏ. Giới hạn 3 cấp để cây dễ dùng và dễ báo cáo. Tên chương, chủ đề lấy từ đề cương của từng học phần; ví dụ dưới đây chỉ minh họa cấu trúc:

```
Ngân hàng: Lập trình hướng đối tượng
├── C1    Chương 1                (cấp 1)
│   ├── C1.1   Chủ đề 1.1         (cấp 2)
│   │   └── C1.1.1  Nhóm nhỏ     (cấp 3)
│   └── C1.2   Chủ đề 1.2         (cấp 2)
└── C2    Chương 2                (cấp 1)
```

| **Trường** | **Ý nghĩa** |
|---|---|
| `bankId`, `code`, `name` | Ngân hàng, mã (duy nhất trong ngân hàng), tên danh mục |
| `parentId` | Danh mục cha (`null` ở cấp 1) |
| `ancestorIds[]` | Danh sách tổ tiên từ gốc đến cha — lấy toàn bộ câu hỏi dưới một chương bằng một truy vấn |
| `level` | 1, 2 hoặc 3 |
| `order`, `status` | Thứ tự hiển thị; `ACTIVE` / `ARCHIVED` |

**Quy tắc kiểm tra:** cấp 1 không có cha; cấp 2–3 phải có cha và số tổ tiên bằng cấp − 1; không vượt cấp 3. **Chỉ mục:** (`bankId`, `code`) duy nhất; (`bankId`, `parentId`, `order`); `ancestorIds`.

### 4.14 questions — câu hỏi

| **Trường** | **Ý nghĩa** |
|---|---|
| `bankId`, `code`, `version` | Ngân hàng, mã câu (duy nhất trong ngân hàng, ví dụ Q001), phiên bản của câu |
| `questionType` | `MCQ` (trắc nghiệm) / `SHORT_ANSWER` / `ESSAY` (tự luận) / `PRACTICAL` (thực hành) / `PROJECT_TASK` (nhiệm vụ đồ án) |
| `content` | Nội dung câu hỏi (có thể để trống nếu đề còn bảo mật) |
| `categoryId` | Danh mục nội dung chứa câu hỏi |
| `bloomLevel` | Mức Bloom của nhiệm vụ (1–6, bắt buộc). Lưu riêng, **không** mặc định bằng trình độ năng lực của PI |
| `difficulty`, `defaultMaxScore` | Độ khó (tùy chọn); điểm tối đa gợi ý |
| `outcomeMappings[]` | Các cặp `{cloId, piId, weight}`: câu hỏi đo CLO nào và PI nào. `weight` là phần điểm của câu được chia cho cặp đó; tổng các cặp của một câu bằng 1. Một câu có thể đo nhiều cặp |
| `source` | `{system, externalQuestionId}` — nguồn nhập (Moodle…) và mã câu bên ngoài, để đối chiếu và chống nhập trùng |
| `status` | `DRAFT` (mới nhập, chưa xác định ánh xạ) / `ACTIVE` / `RETIRED` |
| `verification` | Trạng thái xác nhận ánh xạ |

**Quy tắc:**

- Mỗi câu hỏi (và mỗi tiêu chí rubric trong bài đánh giá) đều có thể ánh xạ với CLO và PI, và **một câu có thể ánh xạ với nhiều cặp CLO–PI**: nhiều CLO khác nhau, một CLO đo nhiều PI, hoặc một PI đến từ nhiều CLO. Mỗi cặp chỉ được khai báo một lần trong cùng một câu (MongoDB kiểm tra) để không tính trùng điểm.
- Câu `ACTIVE` phải có ít nhất một cặp và tổng `weight` bằng 1 (MongoDB kiểm tra); câu `DRAFT` được để trống ánh xạ.
- PI của một cặp phải thuộc ánh xạ CLO–PI đã có trong `clo_pi_links` của chính CLO đó (dịch vụ Spring Boot kiểm tra, chặn ghi nếu sai). Giảng viên không được gắn câu hỏi với PI tùy ý. Khi chạy tính ở chế độ chính thức, liên kết CLO–PI còn phải ở trạng thái đã duyệt.
- Khi nội dung hoặc ánh xạ của một câu thay đổi, tăng `version`. Các bài đã thi giữ bản sao ánh xạ của phiên bản cũ nên kết quả cũ không bị đổi.

**Ví dụ một câu ánh xạ nhiều cặp** (số liệu và các cặp chỉ để minh họa cách tính, không phải ánh xạ chính thức). Câu thực hành tối đa 10 điểm, khai báo 3 cặp, sinh viên đạt 7 điểm:

| **Cặp (CLO, PI)** | `weight` | **Phần điểm tối đa của câu dành cho cặp** | **Sinh viên đạt** |
|---|---|---|---|
| (CLO2, PI6.1) | 0,3 | 3,0 | 2,1 |
| (CLO3, PI6.2) | 0,5 | 5,0 | 3,5 |
| (CLO3, PI7.1) | 0,2 | 2,0 | 1,4 |

Tổng hợp theo từng chuẩn: **CLO2** nhận 3,0 điểm tối đa (đạt 2,1); **CLO3** nhận 7,0 (đạt 4,9, cộng từ hai cặp); **PI6.1** 3,0 (2,1); **PI6.2** 5,0 (3,5); **PI7.1** 2,0 (1,4). Cách chia này tránh gán toàn bộ điểm của một câu cho mọi chuẩn mà câu đó liên quan; có thể đổi sang cách khác nếu Khoa quy định (Q15).

**Chỉ mục:** (`bankId`, `code`) duy nhất; `categoryId`; (`bankId`, `status`, `bloomLevel`); `outcomeMappings.cloId`; `outcomeMappings.piId`; (`bankId`, `source.system`, `source.externalQuestionId`) duy nhất khi có mã ngoài. **Khởi tạo:** rỗng — chỉ nhập khi có đề đã thi và ánh xạ CLO–PI đã duyệt.

### 4.15 assessments — bài đánh giá và cấu trúc câu hỏi/tiêu chí

**Mục đích:** khai báo cấu trúc của bài đã thi để quy điểm lên chuẩn đầu ra. Có **hai tầng**:

- `SYLLABUS_TEMPLATE`: khung theo đề cương (loại bài, trọng số dự kiến). Đề cương dùng nhiều năm nên khung ít thay đổi.
- `SECTION`: bản thực tế của từng lớp trong học kỳ (câu hỏi/tiêu chí đã thi, sao chép từ ngân hàng câu hỏi hoặc khai báo trực tiếp với tiêu chí rubric). Điểm luôn trỏ vào bản này, nên đổi đề ở kỳ sau không làm sai kết quả kỳ trước. Bản đã có điểm bị khóa không cho sửa `items`.

| **Trường** | **Ý nghĩa** |
|---|---|
| `syllabusId`, `code`, `name` | Đề cương, mã và tên bài |
| `scope`, `sectionId`, `templateId` | Tầng (khung/lớp); bản `SECTION` bắt buộc có `sectionId` và trỏ về khung |
| `type`, `phase`, `scheduledAt` | Loại (quiz, thực hành, đồ án, giữa kỳ, cuối kỳ…), giai đoạn (định kỳ/giữa kỳ/cuối kỳ), thời điểm |
| `weightInCourse` | Trọng số của bài trong điểm tổng kết |
| `dataGranularity` | `ITEM` (mặc định — điểm theo từng câu hỏi/tiêu chí) hoặc `TOTAL_ONLY` (chỉ điểm tổng, dự phòng; khi đó không đo được CLO/PI) |
| `items[]` | Danh sách câu hỏi hoặc tiêu chí rubric (bên dưới) |

Mỗi phần tử `items[]` (nhúng trong bài vì chỉ tồn tại cùng bài, số lượng giới hạn):

| **Trường** | **Ý nghĩa** |
|---|---|
| `_id`, `code`, `title`, `kind` | Định danh; `QUESTION` (câu hỏi) hoặc `CRITERION` (tiêu chí rubric) |
| `maxScore` | Điểm tối đa — chính là tỷ trọng điểm riêng của câu hỏi |
| `bloomLevel`, `topic` | Mức Bloom của nhiệm vụ; chương/chủ đề (chuỗi, để xem điểm thành phần theo chương) |
| `outcomeMappings[]` | Các cặp `{cloId, piId, weight}` — câu hỏi/tiêu chí đo cặp CLO–PI nào; `weight` là phần điểm chia cho cặp, tổng các cặp của một câu bằng 1 (MongoDB kiểm tra). Là **bản sao tại thời điểm thi** nên không đổi khi ngân hàng câu hỏi sửa sau này |
| `questionId`, `questionVersion` | Câu hỏi gốc trong ngân hàng và phiên bản tại thời điểm thi; để trống với tiêu chí rubric hoặc câu ngoài ngân hàng |
| `rubric.levels[]` | Các mức rubric (1–4) có mô tả và điểm; *mức rubric khác mức Bloom* |
| `externalRef` | Định danh câu hỏi trên hệ thống LMS/Moodle để đối chiếu khi nhập |

**Chỉ mục:** (`syllabusId`, `sectionId`, `code`) duy nhất; `sectionId`; `items.outcomeMappings.cloId`; `items.outcomeMappings.piId`; `items.questionId`. **Khởi tạo:** chưa có.

## Nhóm E — Học kỳ, lớp học phần, sinh viên

### 4.16 terms — học kỳ

| **Trường** | **Ý nghĩa** |
|---|---|
| `code`, `academicYear`, `startDate`, `endDate` | Ví dụ `2026-HK1` |
| `status` | `OPEN` / `SCORE_LOCKED` (khóa nhập/sửa điểm) / `CLOSED` |

Tránh dùng chuỗi tự do rải rác (sai chính tả làm gãy thống kê) và cho phép khóa sổ điểm. **Chỉ mục:** `code` duy nhất.

### 4.17 course_sections — lớp học phần

| **Trường** | **Ý nghĩa** |
|---|---|
| `code` | Mã lớp |
| `courseId`, `syllabusId`, `termId` | Học phần, đề cương áp dụng, học kỳ |
| `lecturerIds[]` | Giảng viên phụ trách (dùng để phân quyền theo phạm vi) |
| `enrolledCount`, `status` | Sĩ số; `OPEN` / `CLOSED` / `LOCKED` |

Không gắn phiên bản CTĐT ở cấp lớp, vì một lớp có thể có sinh viên của nhiều khóa; phiên bản CTĐT lấy từ `students.programVersionId`. **Chỉ mục:** `code` duy nhất; (`courseId`, `termId`); `lecturerIds`.

### 4.18 students — sinh viên

| **Trường** | **Ý nghĩa** |
|---|---|
| `studentRef` | **Mã thay thế** (không dùng mã thật nếu chưa được phép) — duy nhất |
| `cohort`, `programVersionId` | Khóa, phiên bản CTĐT áp dụng cho sinh viên |
| `fullName` | Tùy chọn, chỉ lưu khi được phép |

Quyết định dùng mã thật hay mã thay thế phụ thuộc Khoa (Q09). **Chỉ mục:** `studentRef` duy nhất; `cohort`.

### 4.19 enrollments — ghi danh vào lớp học phần

`sectionId`, `studentId`, `status` (`ENROLLED`/`WITHDRAWN`). Đây là **mẫu số** của tỷ lệ đạt: lớp 45 sinh viên mà chỉ 40 người đủ dữ liệu thì phải báo cáo thêm 5 người chưa đủ dữ liệu. **Chỉ mục:** (`sectionId`, `studentId`) duy nhất.

## Nhóm F — Nhập điểm

### 4.20 import_batches — mỗi lần nhập file điểm

| **Trường** | **Ý nghĩa** |
|---|---|
| `sectionId`, `assessmentId` | Nhập cho lớp và bài đánh giá nào |
| `fileName`, `fileChecksum`, `fileRef` | File gốc; mã băm để phát hiện nhập trùng |
| `sourceSystem` | `EXCEL` / `CSV` / `MOODLE_EXPORT` |
| `columnMapping` | Ánh xạ cột trong file → câu hỏi/tiêu chí (lưu để dùng lại) |
| `emptyCellAs` | Ô trống là `BLANK` (bỏ trống, tính 0) hay `MISSING` (thiếu dữ liệu) — người nhập chọn rõ cho từng lô, hệ thống không tự đoán |
| `reimportPolicy` | Nhập lại: thêm mới / cập nhật / từ chối (chờ quyết định) |
| `status` | `UPLOADED` → `VALIDATED` → `HAS_ERRORS` / `CONFIRMED` / `ROLLED_BACK` |
| `stats`, `uploadedBy`, `confirmedBy`, `confirmedAt` | Thống kê dòng hợp lệ/lỗi; người nhập, người xác nhận |

**Chỉ mục:** (`sectionId`, `assessmentId`, `createdAt`); `fileChecksum`.

### 4.21 import_row_errors — lỗi từng dòng khi nhập

`batchId`, `rowNumber`, `column`, `errorType` (`INVALID_STUDENT_CODE`, `SCORE_OUT_OF_RANGE`, `DUPLICATE_RECORD`, `MISSING_FIELD`), `rawValue`, `message`. Tách riêng khỏi lô nhập vì số lỗi không giới hạn trước. **Chỉ mục:** (`batchId`, `rowNumber`).

### 4.22 scores — điểm theo từng câu hỏi/tiêu chí (collection lớn nhất)

Mỗi document là điểm của **một sinh viên ở một câu hỏi/tiêu chí**.

| **Trường** | **Ý nghĩa** |
|---|---|
| `sectionId`, `assessmentId`, `itemId`, `studentId` | Khóa nghiệp vụ (duy nhất) |
| `status` | Xem bảng dưới |
| `rawScore`, `maxScore` | Điểm đạt; điểm tối đa chụp tại thời điểm nhập |
| `rubricLevel` | Mức rubric sinh viên đạt (1–4), nếu câu hỏi/tiêu chí có rubric |
| `importBatchId`, `sourceRow` | Truy vết về lô nhập và dòng trong file gốc |
| `revision` | Số lần sửa (khóa lạc quan: sửa đồng thời sẽ báo xung đột) |

| `status` | **Ý nghĩa** | `rawScore` |
|---|---|---|
| `SCORED` | Đã chấm | có điểm |
| `BLANK` | Sinh viên có dự thi nhưng bỏ trống câu này | bằng 0 (có ghi nhận) |
| `ABSENT` | Vắng cả bài | `null` |
| `NOT_GRADED` | Chưa chấm | `null` |
| `EXEMPT` | Được miễn | `null` |
| `MISSING` | Thiếu dữ liệu nhập | `null` |

**Quy tắc kiểm tra tự động:** `rawScore ≤ maxScore`; `BLANK` phải bằng 0; các trạng thái thiếu dữ liệu bắt buộc `rawScore = null` (không được ngầm bằng 0); không trùng (`sectionId`, `assessmentId`, `itemId`, `studentId`). Mọi lần sửa điểm ghi vào `audit_logs`.

## Nhóm G — Đo lường

### 4.23 measurement_rules — quy tắc đo lường có phiên bản

**Mục đích:** lưu công thức, trọng số, ngưỡng dưới dạng **dữ liệu cấu hình** để backend đọc và thực hiện. Nhờ vậy khi Khoa xác nhận hoặc thay đổi quy tắc, không phải sửa mã và các báo cáo cũ vẫn truy được quy tắc đã dùng.

| **Trường** | **Ý nghĩa** |
|---|---|
| `code`, `version`, `status` | Mã (QT-PĐBCL-ĐLCĐR), phiên bản; `DRAFT` / `CONFIRMED` / `RETIRED` |
| `confirmedBy`, `confirmedAt` | Người xác nhận quy tắc |
| `clo` | Cách tính CLO: `method`, `passThreshold` (ngưỡng mặc định), `minCoverage` |
| `course` | `requireAllCloPass`: có yêu cầu đạt *từng* CLO mới đạt học phần hay không (chống học lệch — Q12) |
| `missingDataPolicy` | Cách xử lý từng trạng thái điểm (`BLANK` tính 0; `ABSENT`, `NOT_GRADED`, `MISSING` không tính 0…) |
| `pi` | `method = DIRECT_FROM_ITEMS`: PI tính trực tiếp từ các câu hỏi được gán PI đó |
| `plo`, `cohort` | Cách gộp lên PLO; mẫu số và mục tiêu tỷ lệ đạt cấp lớp |

Chỉ quy tắc `CONFIRMED` mới được dùng để kết luận chính thức. **Khởi tạo:** một khung bản nháp `0.1-DRAFT`, **mọi ngưỡng/trọng số để trống** chờ Khoa xác nhận; không có con số nào được tự đặt.

### 4.24 calculation_runs — mỗi lần chạy tính toán

| **Trường** | **Ý nghĩa** |
|---|---|
| `scope` | Phạm vi tính: lớp / học phần / khóa / CTĐT |
| `ruleId`, `programVersionId`, `syllabusIds` | Quy tắc và phiên bản đã dùng |
| `isOfficial` | `true` chỉ khi quy tắc đã `CONFIRMED` và dữ liệu đã chốt |
| `dataSnapshot` | Dấu vết dữ liệu đầu vào (số điểm, thời điểm sửa mới nhất, các lô nhập, mã băm cấu hình ánh xạ/trọng số) để chứng minh và tái lập kết quả |
| `lockKey` | Khóa chống chạy trùng: hai lượt tính cùng phạm vi, cùng quy tắc không chạy đồng thời |
| `isStale`, `staleReason` | Đánh dấu kết quả đã cũ khi điểm trong phạm vi bị nhập/sửa sau lần tính |
| `status`, `triggeredBy`, `startedAt`, `finishedAt` | Trạng thái và người/thời điểm chạy |

### 4.25 student_results — kết quả từng sinh viên

| **Trường** | **Ý nghĩa** |
|---|---|
| `runId`, `level`, `subjectId` | Lượt tính; cấp (`CLO` / `COURSE` / `PI` / `PLO`); đối tượng (CLO/đề cương/PI/PLO) |
| `sectionId`, `studentId`, `termId` | Lớp (nếu có), sinh viên, học kỳ (để xem tiến trình qua các học kỳ) |
| `status` | `COMPUTED` / `INSUFFICIENT_DATA` / `NOT_APPLICABLE` |
| `value`, `passed` | Kết quả (tỷ lệ) và đạt/không đạt; `null` nếu chưa đủ dữ liệu |
| `completeness` | Độ đầy đủ: số mục cần so với số mục có |
| `inputs[]` | **Truy vết:** các `scores` (hoặc kết quả cấp dưới) đã tham gia, kèm giá trị chuẩn hóa và trọng số |

**Chỉ mục:** (`runId`, `level`, `subjectId`, `sectionId`, `studentId`) duy nhất; (`studentId`, `level`, `subjectId`, `termId`).

### 4.26 aggregate_results — kết quả cấp lớp / khóa / CTĐT

| **Trường** | **Ý nghĩa** |
|---|---|
| `level`, `subjectId`, `scope` | Cấp CLO/COURSE/PI/PLO; đối tượng; phạm vi (lớp, học phần, khóa, học kỳ) |
| `counts` | `enrolled` (ghi danh), `evaluable` (đủ dữ liệu), `passed`, `failed`, `insufficientData` |
| `passRate` | Đạt / đủ dữ liệu |
| `passRateOfEnrolled` | Đạt / ghi danh — luôn kèm theo để tỷ lệ cao không che phần còn thiếu |
| `targetPassRate`, `targetMet` | Mục tiêu do đơn vị quy định và đã đạt hay chưa |
| `dataCompleteness` | Độ đầy đủ dữ liệu |

## Nhóm H — Báo cáo, cải tiến, hỗ trợ

### 4.27 reports — báo cáo đã sinh

`type` (`SECTION_CLO`, `COURSE_PI`, `PROGRAM_PLO`, `DATA_COMPLETENESS`), `params`, `runId`, `ruleId`, `programVersionId` (gắn phiên bản để đọc lại báo cáo cũ), `summary`, `limitations` (ghi rõ phạm vi và giới hạn — ví dụ dữ liệu ba học phần chưa đủ để kết luận toàn chương trình), `exports[]` (bản xuất bảng tính), `generatedBy`, `generatedAt`.

### 4.28 improvement_actions — vấn đề và hành động cải tiến

| **Trường** | **Ý nghĩa** |
|---|---|
| `issue` | Vấn đề gắn với một CLO/PI/PLO và báo cáo/kết quả liên quan |
| `action`, `ownerId`, `dueDate`, `priority` | Hành động, người phụ trách, hạn, mức ưu tiên |
| `status` | `OPEN` → `IN_PROGRESS` → `DONE` → `VERIFIED` (hoặc `CANCELLED`) |
| `completionNote`, `history[]` | Ghi chú kết quả hoàn thành; lịch sử đổi trạng thái |

### 4.29 audit_logs — nhật ký thao tác quan trọng

Ghi lại: sửa điểm (`SCORE_UPDATE`, kèm giá trị trước/sau và lý do), xác nhận nhập (`IMPORT_CONFIRM`), chạy tính (`RUN_CALCULATION`), xác nhận quy tắc (`RULE_CONFIRM`), đăng nhập. Trường: `at`, `userId`, `action`, `entity {collection, id}`, `before`, `after`, `reason`, `ip`.

### 4.30 ml_experiments (tùy chọn) — thử nghiệm học máy

`name`, `datasetSnapshot`, `features`, `model`, `metrics`, `status`, `disclaimer`. Kết quả chỉ mang tính hỗ trợ, không ghi vào `student_results`. Có thể bỏ nếu đồ án không thực hiện phần ML.

# 5. Luồng dữ liệu và ví dụ truy vết

Ví dụ minh họa (số liệu **chỉ để giải thích**, chưa phải quy tắc chính thức):

1. Chuẩn bị câu hỏi và bài đã thi: câu hỏi A (có trong `questions`, đo cặp CLO3–PI6.2) và tiêu chí rubric B (khai báo trực tiếp, cũng đo CLO3–PI6.2). Lập bài đánh giá (`assessments`, bản `SECTION`) bằng cách sao chép câu A kèm ánh xạ, thêm tiêu chí B; câu A tối đa 10 điểm, tiêu chí B tối đa 20 điểm.
2. Giảng viên nhập file điểm → `import_batches` (+ `import_row_errors` nếu có lỗi) → xác nhận → ghi `scores`: sinh viên X đạt 6/10 ở câu A, 16/20 ở tiêu chí B.
3. Chạy tính (`calculation_runs`) theo `measurement_rules`:
   - **CLO3** của sinh viên X = 60%×0,4 + 80%×0,6 = 72% → ghi `student_results` (level `CLO`), `inputs` chứa hai điểm gốc.
   - **PI6.2** của sinh viên X tính trực tiếp từ các câu hỏi gán PI6.2 → ghi `student_results` (level `PI`).
   - PLO6 tổng hợp từ các kết quả PI thuộc PLO6.
4. Tổng hợp lớp → `aggregate_results` (ví dụ 45 ghi danh, 40 đủ dữ liệu, 32 đạt, 5 chưa đủ dữ liệu).
5. Sinh báo cáo (`reports`) có ghi giới hạn phạm vi; vấn đề phát hiện → `improvement_actions`.
6. Truy vết ngược: từ một kết quả PLO → `inputs` → `scores` → `import_batches` + dòng trong file điểm gốc.

**Bốn trường hợp cần phân biệt:** điểm học phần ≠ mức đạt CLO; kết quả CLO cá nhân ≠ tỷ lệ đạt của lớp; ngưỡng đạt cá nhân ≠ mục tiêu cấp lớp; tỷ lệ đạt luôn đi kèm mẫu số và số sinh viên chưa đủ dữ liệu.

# 6. Kiểm tra tính hợp lệ của dữ liệu (đã thử nghiệm)

Mỗi collection có quy tắc kiểm tra cấu trúc (`$jsonSchema`) do MongoDB thực thi. Các quy tắc chính đã được chạy thử trên MongoDB 8.2.6 bằng `database_import.js`:

| **Quy tắc** | **Kết quả thử** |
|---|---|
| Điểm lớn hơn điểm tối đa bị từ chối | Đạt |
| `BLANK` phải bằng 0; trạng thái thiếu dữ liệu (ví dụ `ABSENT`) không được kèm điểm 0 | Đạt |
| Trạng thái điểm ngoài danh sách bị từ chối | Đạt |
| Trùng (lớp, bài, câu hỏi, sinh viên) bị từ chối | Đạt |
| Mã PLO/PI sai định dạng bị từ chối | Đạt |
| Ký hiệu ma trận ngoài I/R/M bị từ chối | Đạt |
| Bài đánh giá cấp lớp thiếu `sectionId` bị từ chối; mức Bloom ngoài 1–6 bị từ chối | Đạt |
| Hai lượt tính cùng phạm vi/quy tắc không chạy đồng thời | Đạt |
| Danh mục câu hỏi: cấp 1 không có cha; cấp 2–3 phải có cha và đủ tổ tiên; cấp 4 bị từ chối; trùng mã trong ngân hàng bị từ chối | Đạt |
| Câu hỏi `ACTIVE`: phải có ít nhất một cặp CLO–PI và tổng phần điểm bằng 1 (0,5 + 0,5 được; 0,5 + 0,3 hoặc rỗng bị từ chối); câu `DRAFT` được để trống ánh xạ | Đạt |
| Câu hỏi thiếu Bloom, Bloom ngoài 1–6, loại câu hỏi lạ, thiếu `piId` trong cặp bị từ chối | Đạt |
| Một câu ánh xạ nhiều cặp được chấp nhận (3 cặp qua 2 CLO; một CLO đo 2 PI; một PI từ 2 CLO; 5 cặp); khai báo trùng một cặp trong cùng câu bị từ chối (cả ở ngân hàng câu hỏi và ở bài đánh giá) | Đạt |
| Nhập trùng một câu từ Moodle (cùng ngân hàng, cùng mã ngoài) bị từ chối; nhiều câu nội bộ không có mã ngoài vẫn nhập được | Đạt |
| Câu hỏi/tiêu chí trong bài đánh giá: thiếu cặp CLO–PI, cặp rỗng, hoặc tổng phần điểm khác 1 bị từ chối | Đạt |
| Chạy lại script không tạo dữ liệu trùng | Đạt |

Những ràng buộc liên kết (ví dụ `cloId` phải tồn tại, câu hỏi thuộc đúng bài đánh giá, cặp CLO–PI của câu hỏi phải có trong `clo_pi_links`, câu hỏi và danh mục phải cùng một ngân hàng) do tầng dịch vụ Spring Boot kiểm tra, vì MongoDB không có khóa ngoại. Thao tác xác nhận nhập điểm cần transaction nhiều document, nên MongoDB cần chạy ở dạng replica set.

# 7. Dữ liệu khởi tạo trong database_import.js

| **Collection** | **Số dòng** | **Nguồn** | **Lưu ý** |
|---|---|---|---|
| `programs`, `program_versions` | 1 + 1 | Tên file bản CTĐT | Phiên bản áp dụng chờ xác nhận (Q01) |
| `plos` | 7 | `Misa.md` | Mức năng lực đúng theo tài liệu |
| `pis` | 17 | `Misa.md` | Mức năng lực đúng theo tài liệu |
| `courses` | 1 | `Misa.md` | OOPR230279 |
| `course_pi_contributions` | 4 | `Misa.md`, trang 46 | Chỉ học phần OOP |
| `measurement_rules` | 1 | Khung | Bản nháp, ngưỡng/trọng số để trống |
| `question_banks`, `question_categories`, `questions` | 0 | — | Tạo rỗng; nhập khi có đề đã thi và ánh xạ CLO–PI đã duyệt |

**Lưu ý quan trọng:** nội dung PLO/PI trong dữ liệu khởi tạo là **diễn giải ngắn** lấy từ tài liệu hướng dẫn, chưa phải nguyên văn bản CTĐT. Vì vậy `contentOriginal` để trống và `verification.status = NEEDS_CLARIFICATION` cho đến khi đối chiếu bản gốc. Chưa có học phần, CLO, điểm hay sinh viên thật nào được đưa vào.

# 8. Các điểm cần xác nhận (ảnh hưởng đến cơ sở dữ liệu)

Danh sách này được theo dõi bằng tài liệu của dự án, không lưu trong cơ sở dữ liệu.

| **Mã** | **Nội dung** | **Tình trạng** | **Ảnh hưởng** |
|---|---|---|---|
| Q01 | Bản CTĐT áp dụng cho khóa nào | Chưa hỏi | `program_versions.applicableCohorts` |
| Q02 | Khi nào có đề cương chính thức | Chưa hỏi | `syllabi`, `clos` (hiện chỉ có CLO minh họa) |
| Q03 | Thang "trình độ năng lực" PLO/PI | Chưa hỏi | Hiện lưu nguyên giá trị, chưa diễn giải |
| Q04 | Công thức, ngưỡng, mẫu số, xử lý thiếu dữ liệu | Chưa hỏi | `measurement_rules` (đang để trống) |
| Q05 | Điểm theo câu hỏi hay điểm tổng | **Đã trả lời, chờ xác nhận:** theo từng câu hỏi | `dataGranularity = ITEM` |
| Q06, Q07 | Phiên bản công nghệ; nơi lưu mã, tệp, môi trường | Chưa hỏi | Môi trường triển khai; MongoDB replica set |
| Q08 | Ai rà soát nghiệp vụ, kỹ thuật | Chưa hỏi | Quy trình xác nhận (`verification`) |
| Q09 | Dùng mã sinh viên thật hay mã thay thế | Chưa hỏi | `students`; có thể tách `student_identities` |
| Q10 | PI gán trực tiếp hay suy ra qua CLO | **Đã trả lời, chờ xác nhận:** gán trực tiếp cho từng câu hỏi, nhưng chỉ chọn trong các PI đã liên kết với CLO của câu đó (ánh xạ CLO–PI đã duyệt) | `outcomeMappings`; kiểm tra với `clo_pi_links`; `pi.method = DIRECT_FROM_ITEMS` |
| Q11 | Phạm vi: có tổ chức thi không | **Phạm vi đồ án:** không tổ chức thi; ngân hàng câu hỏi chỉ là danh mục câu hỏi của đề đã thi. Chờ GVHD xác nhận | Không có sinh đề, không có duyệt đề |
| Q12 | Quy tắc "phải đạt từng CLO"; tỷ trọng, ngưỡng từng CLO | Chưa hỏi | `course.requireAllCloPass`, `clos.measurement` |
| Q13 | Phân biệt bỏ trống / vắng thi / chưa chấm / thiếu; vắng thi có vào mẫu số không | Chưa hỏi | `scores.status`, `emptyCellAs`, `aggregate_results.counts` |
| Q14 | Khi nào Khoa cung cấp ánh xạ CLO–PI đã duyệt của các học phần thử nghiệm | Chưa hỏi | `clo_pi_links` — cần có trước khi gán ánh xạ cho câu hỏi |
| Q15 | Khi một câu đo nhiều cặp CLO–PI, điểm của câu được chia thế nào (chia theo phần, tổng bằng 1; hay mỗi cặp tính trọn điểm) | Chưa hỏi | `outcomeMappings.weight`; thiết kế hiện chọn cách chia, tổng bằng 1 |
| Q16 | Cấu trúc chương/chủ đề của ngân hàng câu hỏi từng học phần; nguồn nhập câu hỏi (Moodle hay Excel) | Chưa hỏi | `question_categories`, `import_batches` |

Các số liệu ví dụ trong tài liệu (trọng số 40/60, ngưỡng 60%, tỷ trọng CLO 20/40/40, ngưỡng 72%/50%…) **chưa phải quy định chính thức** và không được đưa vào cơ sở dữ liệu như quy tắc.

# 9. Hướng dẫn nạp cơ sở dữ liệu

1. Cài MongoDB (khuyến nghị chạy dạng replica set) và `mongosh`.
2. Chạy:

```
mongosh "mongodb://localhost:27017" --file database_import.js
```

3. Tùy chọn (biến môi trường): `LOMS_DB` đổi tên cơ sở dữ liệu (mặc định `learning_outcomes`); `LOMS_SAMPLE=1` nạp thêm 4 CLO minh họa của OOP.
4. Script chạy lại nhiều lần được và **không xóa dữ liệu**: collection đã có chỉ được cập nhật quy tắc kiểm tra, dữ liệu khởi tạo chỉ chèn khi chưa tồn tại.
