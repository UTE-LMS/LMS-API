# BẢN CAM KẾT SẢN PHẨM CUỐI CÙNG VÀ BỘ METRIC ĐÁNH GIÁ

## 1. Thông tin chung
- **Tên đề tài:** Xây dựng Hệ thống Website Quản lý học tập tích hợp đo lường chuẩn đầu ra (Learning Outcomes Management and Analytics System — HCMUTE OBE LMS)
- **Giảng viên hướng dẫn:** Nguyễn Minh Đạo
- **Nhóm sinh viên thực hiện:**
  1. Nguyễn Nhật Minh - MSSV: 23110265 - Đóng góp: 100%
  2. Mai Hồng Tín - MSSV: 23110342 - Đóng góp: 100%
- **Công nghệ cốt lõi:** Backend Java 25 / Spring Boot 4.1.2 + MongoDB Replica Set (`rs0`); Frontend Angular + TypeScript.

## 2. Danh sách chức năng cốt lõi cam kết hoàn thành

- [x] **Chức năng 1 — Xác thực, Bảo mật JWT & Phân quyền phân cấp theo Khoa:**
  - **Đăng nhập / Đăng xuất / Hồ sơ cá nhân:** Xác thực JWT HS256 (`POST /api/v1/auth/login`, `/logout`, `GET /api/v1/auth/me`), phòng chống timing attack khi sai username, thu hồi token tức thì bằng `tokenVersion` khi người dùng đăng xuất hoặc bị khóa (`LOCKED`).
  - **Quản lý người dùng (`ADMIN`):** CRUD người dùng (`GET/POST/PUT /api/v1/users`, `PUT /api/v1/users/{id}/status`), mảng vai trò `roles` gồm 4 vai trò chuẩn (`ADMIN`, `PROGRAM_MANAGER`, `LECTURER`, `QA`), kiểm tra chính sách mật khẩu 8–72 bytes UTF-8 + mã hóa BCrypt, **ADMIN Guards** chặn tự khóa/tự gỡ quyền hoặc khóa/hạ quyền tài khoản `ADMIN ACTIVE` cuối cùng.
  - **Quản lý Khoa & Phân công Program Manager (`ADMIN`):** CRUD Khoa (`GET/POST/PUT /api/v1/faculties`), gán/hủy gán/điều chuyển `PROGRAM_MANAGER` thuộc Khoa (`POST /assign-pm`, `DELETE /unassign-pm`, `POST /transfer-pm`, tự động thu hồi JWT cũ khi điều chuyển).
  - **Kiểm soát phạm vi (`ScopeAuthorizationService`):** Kết hợp đa vai trò theo phép hợp (`UNION`), Fail-Closed khi thiếu scope; `PROGRAM_MANAGER` chỉ thao tác tài nguyên thuộc `facultyId` của mình, `LECTURER` chỉ thao tác lớp học phần được phân công (`lecturerIds`), `QA` chỉ đọc toàn trường.

- [x] **Chức năng 2 — Quản lý Danh mục Công khai, CTĐT, Học phần, Đề cương & Bất biến dữ liệu:**
  - **Tra cứu công khai (`permitAll`):** Mở các endpoint `GET` học thuật (Khoa, Ngành, Phiên bản CTĐT, PLO, PI, Học phần trong CTĐT, Danh mục học phần, Đề cương, CLO, Ma trận CLO–PI) hỗ trợ tìm kiếm và lọc đa chiều.
  - **Ngành & Phiên bản CTĐT:** Quản lý Ngành (`programs`) và Phiên bản CTĐT (`program_versions` kèm `totalCredits`, `durationYears`, `applicableCohort`); tạo mới mặc định `ACTIVE` & `VERIFIED`, chuyển trạng thái `ACTIVE → ARCHIVED` (`/lock`, `/status`).
  - **PLO, PI & Khung học phần CTĐT:** CRUD `PLO` (`category`, `capabilityLevelRaw`, `passThreshold`), `PI` (`weight` tổng bằng `1.0` trong mỗi PLO, `passThreshold`, `capabilityLevelRaw`), và gán học phần vào CTĐT (`program_courses` kèm `semester`, `credits`, `type`, `category`, `prerequisiteCourseIds`, `previousCourseIds`, chặn xung đột tự tham chiếu và giao giữa tiên quyết/học trước). Chặn sửa/xóa PLO/PI khi phiên bản đã `ARCHIVED`.
  - **Học phần, Đề cương, CLO & Liên kết CLO–PI:** CRUD `Course` (`isActive`, gắn `facultyId`), `Syllabus` (`CONFIRMED → ARCHIVED`, kiểm tra `effectiveToTerm >= effectiveFromTerm`), `CLO` (`bloomLevel` 1–6, `defaultPassThreshold`), `CloPiLink` (`weight` trong `(0, 1]`, kiểm tra học phần thuộc `program_courses`). Chặn sửa/xóa CLO khi đề cương đã `ARCHIVED`.
  - **Immutable Guard:** Chặn đổi tên Khoa, thông tin gốc CTĐT (`code`, `name`, `level`, `degree`), hoặc tên/tín chỉ Học phần khi đã nằm trong phiên bản CTĐT `ACTIVE`/`ARCHIVED`; chặn xóa/khóa khi còn đang được sử dụng.

- [x] **Chức năng 3 — Import dữ liệu hàng loạt qua CSV (kèm file mẫu UTF-8 BOM):**
  - **Import Khoa (`ADMIN`):** `GET /api/v1/faculties/import-template`, `POST /api/v1/faculties/import` (`code,name,description,isActive`), kiểm tra trùng mã/tên best-effort theo từng dòng.
  - **Import Người dùng (`ADMIN`):** `GET /api/v1/users/import-template`, `POST /api/v1/users/import` (`username,email,password,fullName,roles,facultyCode,status`), hỗ trợ nhiều role ngăn cách `;`/`|`, ánh xạ `facultyCode` và băm BCrypt.
  - **Import Học phần (`ADMIN` / `PROGRAM_MANAGER`):** `GET /api/v1/courses/import-template`, `POST /api/v1/courses/import` (`code,name,credits,facultyCode`); `ADMIN` gán theo từng dòng hoặc `facultyId` mặc định, `PROGRAM_MANAGER` tự động gán về khoa của mình.
  - **Import Phiên bản CTĐT & Khung học phần:** `GET/POST /api/v1/program-versions/import` (nạp PLO, PI, ánh xạ môn học, `totalCredits`, `durationYears`, `applicableCohort`, kiểm tra tổng `piWeight = 1.0`) và `GET/POST /api/v1/program-versions/{id}/courses/import` (upsert học kỳ, tín chỉ, loại môn, học trước, tiên quyết).
  - **Import Đề cương & CLO–PI:** `GET/POST /api/v1/courses/{courseId}/syllabi/import` (nạp CLO, mức Bloom, ngưỡng đạt, ánh xạ `piCode` + `linkWeight` theo `programVersionId`, tự động ở trạng thái `CONFIRMED` & `VERIFIED`).
  - **Import Lớp học phần & Ghi danh:** `GET/POST /api/v1/sections/import` (`code,courseCode,syllabusVersion,termCode,lecturerUsernames`, kiểm tra quyền theo khoa, đề cương `CONFIRMED`, học kỳ `OPEN`, giảng viên `ACTIVE` có role `LECTURER`); `GET/POST /api/v1/sections/{sectionId}/enrollments/import` (1 lớp) và `GET/POST /api/v1/enrollments/import` (nhiều lớp: `sectionCode,studentCode,cohort`), tự động tạo `Student` theo `studentCode` nếu chưa có và cập nhật `enrolledCount`.

- [x] **Chức năng 4 — Vận hành Học kỳ, Lớp học phần, Bài đánh giá & Nhập điểm an toàn giao dịch:**
  - **Học kỳ & Lớp học phần:** CRUD Học kỳ (`OPEN → SCORE_LOCKED → CLOSED`, mở lại cần `reason`, chặn xóa khi đã có đề cương/lớp); mở Lớp học phần (`course_sections` trên đề cương `CONFIRMED` và học kỳ `OPEN`), cập nhật giảng viên, đổi trạng thái (`OPEN`, `CLOSED`, `LOCKED`), quản lý rút/ghi danh lại (`ENROLLED ↔ WITHDRAWN`) chỉ khi lớp và học kỳ cùng `OPEN`.
  - **Bài đánh giá & Ma trận câu hỏi – CLO:** Tạo khung bài đánh giá mẫu trên đề cương (`SYLLABUS_TEMPLATE`) hoặc bài kiểm tra của lớp (`SECTION`, hỗ trợ sao chép từ `templateId`), khai báo câu hỏi `items[]` ánh xạ `outcomeMappings` (`cloId`, `maxScore`) và `cloWeights`, tự động tính `coverageSummary` (phân bố điểm theo CLO & Bloom), khóa/mở khóa ma trận đề (`DRAFT ↔ LOCKED`, kiểm tra tổng trọng số CLO toàn lớp không vượt `1.0`).
  - **Import điểm qua Batch Transaction:** Tải mẫu CSV bảng điểm của bài đánh giá (`GET /api/v1/assessments/{id}/score-template`), quy trình 3 bước `Upload → Validate → Confirm` (`/import-batches`), kiểm tra hợp lệ từng dòng/cột (mã sinh viên `studentCode`, điểm `0 <= rawScore <= maxScore`, quy ước vắng thi/bỏ trống = `0.0`, lưu lỗi vào `import_row_errors`), xác nhận ghi vào `scores` bằng MongoDB Transaction (rollback toàn bộ nếu lỗi), chuyển bài đánh giá sang `SCORE_ENTERED`, hỗ trợ `rollback` batch nháp và chặn nhập điểm khi lớp `LOCKED`, học kỳ không `OPEN`, đề cương/CTĐT đã `ARCHIVED`.

- [x] **Chức năng 5 — Engine Đo lường OBE 4 cấp độ, Thống kê đa chiều, Báo cáo & Kiểm toán:**
  - **Engine tính toán 4 cấp độ (`POST /api/v1/calculations`):** Tính tự động từ `scores` → `ASSESSMENT_CLO` (tổng điểm đạt / tổng `maxScore`) → `CLO` (tổng có trọng số `W_{p,j} = 1.0`) → `PI` (trung bình có trọng số chuẩn hóa theo các CLO đã học) → `PLO` (tổng có trọng số `W_{PI→PLO} = 1.0`, so ngưỡng đạt `plo.passThreshold`); tổng hợp `aggregate_results` (`passRate`, trung bình, độ lệch chuẩn, min/max, `completeness`), chống chạy trùng bằng `lockKey` và đánh dấu `isStale` khi điểm thay đổi.
  - **Thống kê ma trận đa chiều (`GET /api/v1/reports/analytics` & `/export`):** Truy vấn cross-tab 2 chiều theo **7 chiều phân tích** (`TIME_TERM`, `COURSE_SECTIONS`, `COHORT`, `ASSESSMENT_COMPONENT`, `CURRICULUM_CLASSIFICATION`, `COURSE_LECTURER`, `STUDENT`) kèm xuất CSV UTF-8 BOM.
  - **Báo cáo tĩnh & Truy vết kiểm toán:** Sinh báo cáo snapshot (`POST /api/v1/reports` với 4 loại: `SECTION_CLO`, `COURSE_PI`, `PROGRAM_PLO`, `DATA_COMPLETENESS`) và xuất CSV; truy vết ngược kết quả `PLO`/`PI`/`CLO` về từng dòng điểm gốc (`TraceController`); tự động lưu `createdAt`/`updatedAt` (`@EnableMongoAuditing`) trên toàn bộ 15 collection nghiệp vụ và ghi `audit_logs` (`CREATE`, `UPDATE`, `DELETE`, `STATUS_CHANGE`, `IMPORT`, `LOGIN`, `ASSESSMENT_LOCK`, `ASSESSMENT_UNLOCK`, `IMPORT_CONFIRM`, `SCORE_UPDATE`, `RUN_CALCULATION`) cho mọi thao tác hệ thống.

## 3. Danh mục tối thiểu 5 KPI nghiệp vụ định lượng
1. **KPI 1 (Độ chính xác tính toán OBE 4 cấp):** 100% ca kiểm chứng tính `ASSESSMENT_CLO → CLO → PI → PLO` trên hệ thống khớp với kết quả tính độc lập (bảng tính) trong sai số cho phép ($\le 10^{-6}$).
2. **KPI 2 (Phát hiện lỗi tệp điểm đầu vào):** Phát hiện chính xác **100%** các lỗi cài sẵn trong tệp CSV nhập điểm (sai mã sinh viên, điểm âm/vượt `maxScore`, trùng bản ghi, thiếu trường bắt buộc) kèm vị trí dòng/cột trong `import_row_errors`.
3. **KPI 3 (Rút ngắn thời gian tổng hợp báo cáo CLO/PI/PLO):** Giảm thời gian từ lúc có bảng điểm câu hỏi của một lớp (~60 sinh viên) đến lúc xuất được báo cáo CLO/PI hoàn chỉnh từ **~30–45 phút (làm thủ công trên Excel)** xuống **dưới 2 phút** trên hệ thống (thời gian chạy engine $\le 5$ giây).
4. **KPI 4 (Hiệu quả truy vết minh chứng):** Số thao tác (click/API call) để truy vết ngược từ một kết quả `PLO`/`PI`/`CLO` bất kỳ về tới dòng điểm gốc và lô nhập (`import_batches`) đạt **$\le 3$ bước**.
5. **KPI 5 (Toàn vẹn dữ liệu & Bảo vệ bất biến):** **0%** bản ghi điểm nửa vời khi xảy ra lỗi giữa chừng lúc xác nhận nhập điểm (MongoDB Transaction rollback); **100%** thao tác sửa tên/tín chỉ học phần hoặc chương trình đã công bố (`ACTIVE`/`ARCHIVED`) và thao tác nhập điểm trên CTĐT đã khóa (`ARCHIVED`) bị chặn đúng quy tắc.
6. **KPI 6 (Khả năng sử dụng thực nghiệm UAT):** Tỷ lệ hoàn thành tác vụ chính của người dùng thử nghiệm đạt **$\ge 90\%$** và điểm đánh giá khả dụng **SUS $\ge 80$ điểm**.

## 4. Bảng metric kỹ thuật điều chỉnh
| Tiêu chí kỹ thuật | Chuẩn mặc định của Rubric | Chuẩn cam kết điều chỉnh | Ghi chú lý do điều chỉnh |
|---|---|---|---|
| **Độ phủ kiểm thử (TC2.5)** | $\ge 70\%$ dòng lệnh toàn repo | **$\ge 70\%$** tầng nghiệp vụ lõi (`service`, `calculation`, `security`, `import`) | Tập trung kiểm thử sâu logic tính toán OBE, RBAC và Transaction bằng Integration Test với MongoDB Replica Set thật; không chạy theo % ở các DTO/Config boilerplate. |
| **Chất lượng mã nguồn tĩnh (TC2.4)** | 0 lỗi Lint / 0 Critical | **0 lỗi** `Spotless`, `SpotBugs`, `PMD-CPD` (duplication = 0 block vượt ngưỡng) | Thực thi tự động và chặn merge qua Maven plugins trong CI (`spotless:check spotbugs:check pmd:cpd-check`). |
| **Triển khai CI/CD (TC2.6)** | 6 chặng pipeline | **6 chặng pipeline** (`Build` → `Lint & Static Analysis` → `Test & JaCoCo` → `Dependency & Secret Scan` → `Docker/Trivy Package` → `Deploy & Smoke Staging`) | Cấu hình chuẩn trong `.github/workflows/ci.yml`, có cơ chế rollback tự động khi smoke test lỗi. |
| **Hiệu năng API & Xử lý lô (TC2.1)** | Phản hồi trang < 2.0s | API đọc p95 **$\le 500\text{ ms}$** (50 CCU); Import & kiểm tra **5.000 dòng điểm $\le 10\text{ giây}$** | Đánh chỉ mục Compound Index trên MongoDB cho `scores`, `student_results`, `aggregate_results` và xử lý batch `saveAll`. |
| **Truy vết & Bảo mật (TC2.1 / TC2.3)** | Kiểm tra quyền & nhật ký | **100%** endpoint ghi/đọc nhạy cảm bảo vệ bằng JWT + RBAC; mật khẩu BCrypt (72-byte UTF-8 guard); ghi `audit_logs` cho mọi thao tác nhạy cảm | Đảm bảo không lộ `passwordHash`/`tokenVersion`, chống timing attack và ghi nhận đầy đủ `logsAI/`. |

## 5. Xác nhận và Cam kết
- Đại diện sinh viên ký tên: .......................... (Ngày: ..../..../202...)
- Giảng viên hướng dẫn xác nhận: ...................... (Ngày: ..../..../202...)
- Kết luận rà soát của Bộ môn: ĐẠT ☐ / KHÔNG ĐẠT ☐
