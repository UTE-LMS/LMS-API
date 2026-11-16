# BẢN CAM KẾT SẢN PHẨM CUỐI CÙNG VÀ BỘ METRIC ĐÁNH GIÁ

## 1. Thông tin chung
- **Tên đề tài:** Xây dựng Hệ thống Website Quản lý học tập tích hợp đo lường chuẩn đầu ra (Learning Outcomes Management and Analytics System — HCMUTE OBE LMS)
- **Giảng viên hướng dẫn:** Nguyễn Minh Đạo
- **Nhóm sinh viên thực hiện:**
  1. Nguyễn Nhật Minh - MSSV: 23110265 - Đóng góp: 100%
  2. Mai Hồng Tín - MSSV: 23110342 - Đóng góp: 100%
- **Công nghệ cốt lõi:** Backend Java 25 / Spring Boot 4.1.2 + MongoDB Replica Set (`rs0`); Frontend Angular + TypeScript.

## 2. Danh sách chức năng cốt lõi cam kết hoàn thành
- [x] **Chức năng 1 — Xác thực & Phân quyền phân cấp theo Khoa:** Đăng nhập/đăng xuất JWT (`tokenVersion` thu hồi token tức thì khi khóa/logout), phòng chống timing attack, quản lý người dùng với mảng `roles` (`ADMIN`, `PROGRAM_MANAGER`, `LECTURER`, `QA`), gán 1 `PROGRAM_MANAGER` thuộc 1 Khoa (`faculties`), kiểm tra quyền ở mức dịch vụ (`ScopeAuthorizationService`) và guard bảo vệ tài khoản `ADMIN ACTIVE` cuối cùng.
- [x] **Chức năng 2 — Quản lý Danh mục Công khai, CTĐT, Học phần & Bất biến dữ liệu** Trang chủ tra cứu công khai (`permitAll` các API `GET` học thuật) hỗ trợ lọc đa chiều; quản lý Khoa, Ngành (`programs`), Phiên bản CTĐT (`program_versions` kèm `totalCredits`, `durationYears`, `applicableCohort`), PLO/PI, Học phần (`courses`), Khung học phần theo học kỳ (`program_courses` kèm tiên quyết/học trước), Đề cương (`syllabi`), CLO và liên kết `CLO–PI`; cơ chế **Immutable Guard** chặn sửa định danh `Course`/`Program`/`Faculty` khi đã gắn phiên bản `ACTIVE`/`ARCHIVED`; workflow khóa phiên bản CTĐT (`DRAFT → ACTIVE → ARCHIVED`).
- [x] **Chức năng 3 — Import dữ liệu hàng loạt qua CSV:** Cung cấp file mẫu và import CSV chuẩn UTF-8 cho: Danh mục học phần (`/courses/import`), Phiên bản CTĐT kèm PLO/PI & trình độ năng lực (`/program-versions/import`), Khung học phần theo học kỳ/tiên quyết (`/program-versions/{id}/courses/import`), Đề cương & CLO–PI (`/courses/{id}/syllabi/import`), Danh sách sinh viên ghi danh (`/sections/{id}/enrollments/import`) và Nhập điểm theo từng câu hỏi–CLO (`/import-batches`).
- [x] **Chức năng 4 — Bài đánh giá, Khóa ma trận & Nhập điểm an toàn giao dịch:** Quản lý Học kỳ (`OPEN → SCORE_LOCKED → CLOSED`), Lớp học phần (`course_sections` với `lecturerIds`), Bài đánh giá theo mẫu đề cương hoặc theo lớp (`items[]` ánh xạ `outcomeMappings`, tính tự động `coverageSummary`), khóa/mở khóa ma trận đề (`DRAFT ↔ LOCKED`), kiểm tra lỗi từng dòng/cột CSV (`import_row_errors`), quy ước vắng thi/bỏ trống = `0.0`, xác nhận nhập điểm vào `scores` bằng MongoDB Transaction đảm bảo không lưu nửa vời, chặn giảng viên sửa/nhập điểm khi phiên bản CTĐT đã `ARCHIVED`.
- [x] **Chức năng 5 — Engine Đo lường OBE 4 cấp độ, Thống kê đa chiều & Truy vết kiểm toán:** Tính toán tự động mức đạt 4 cấp `ASSESSMENT_CLO → CLO → PI → PLO` (tỷ lệ đóng góp $W_{p,j}$, chuẩn hóa mẫu số trên tập CLO đã học, chống chạy trùng bằng `lockKey`, đánh dấu `isStale` khi điểm thay đổi), Ma trận phân tích đa chiều 7 chiều (`AnalyticsMatrixResponse`), sinh báo cáo `reports` (kèm `limitations` và mẫu số $N_{\text{section}}$/$N_{\text{cohort}}$), xuất CSV và truy vết ngược từ kết quả PLO/PI/CLO về từng dòng file điểm gốc qua `audit_logs` và `TraceController`.

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
