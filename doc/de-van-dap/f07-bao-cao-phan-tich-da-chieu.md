# Để vấn đáp — F07: Báo cáo phân tích đa chiều 7 chiều & Xuất CSV ma trận (US-35, US-36)

> Viết cho thành viên nhóm (cả **IT** và **IBS**) nắm vững logic để bảo vệ trước Hội đồng theo Rubric Mục 6.2 (4 câu hỏi cốt lõi: *làm gì, vì sao có trong đề tài, tham số/thư viện đến từ đâu, bỏ đi thì sao*). Đối chiếu đặc tả tại `doc/plan/F07-bao-cao-truy-vet.md` §11 và quy tắc miền `.claude/rules/02-domain-invariants.md`.

## 1. Kiến trúc tổng hợp chỉ đọc từ lượt tính `SUCCESS` (`AnalyticsServiceImpl#resolveCalculationRun`)

- **Làm gì:** Mọi truy vấn phân tích đa chiều (`GET /api/v1/reports/analytics` và `/export`) bắt buộc lấy dữ liệu từ một `CalculationRun` có `status = SUCCESS` trên `aggregate_results` (và `student_results` khi đối sánh `COHORT` chưa tách sẵn ở bảng tổng hợp), tuyệt đối không đọc lại bảng `scores` thô để tính lại công thức CLO/PI/PLO.
- **Vì sao có:**
  - *Góc nhìn IBS (Đảm bảo chất lượng):* Trong kiểm định OBE (AUN-QA / ABET), con số trên báo cáo lớp, báo cáo khóa và dashboard phân tích phải đến từ cùng một lượt đo lường chuẩn (`runId`) kèm cờ chính thức (`isOfficial`) và độ tươi dữ liệu (`isStale`). Nếu dashboard tự tính lại từ điểm thô sẽ sinh ra hai con số lệch nhau khi điểm hoặc quy tắc vừa đổi.
  - *Góc nhìn IT:* Tránh quét hàng chục nghìn bản ghi `scores` trên mỗi request HTTP và giữ nguyên kiến trúc 25 collections MongoDB v0.7 mà không cần thêm bảng lưu trữ trung gian.
- **Tham số/thư viện từ đâu:** Đọc qua Spring Data MongoDB (`CalculationRunRepository`, `AggregateResultRepository`, `StudentResultRepository`). Nếu client truyền `runId` có trạng thái `RUNNING` hoặc `FAILED`, service ném `BusinessRuleViolationException` (ánh xạ HTTP `422`).
- **Bỏ đi thì sao:** Người dùng có thể vô tình xem và xuất báo cáo từ một lượt chạy đang tính dở (`RUNNING`) hoặc bị lỗi giữa chừng (`FAILED`), dẫn đến tỷ lệ đạt thiếu hụt mà không hay biết.

## 2. Bất biến 10 trường chỉ số & An toàn mẫu số bằng 0 (`MatrixMetricCell#calculate`)

- **Làm gì:** Mỗi ô giao giữa dòng và cột của ma trận 2D luôn trả đủ 10 trường: `enrolled`, `evaluated`, `passed`, `failed`, `insufficientData`, `mean`, `passRate`, `passRateOfEnrolled`, `isOfficial`, `isStale`. Khi `evaluated == 0` thì `passRate = null` và `mean = null`; khi `enrolled == 0` thì `passRateOfEnrolled = null`. Khi gộp nhiều bản ghi `AggregateResult` (ví dụ gộp các lớp trong một học kỳ ở chiều `TIME_TERM`), `mean` được tính theo trung bình gia quyền theo số sinh viên `evaluated` của từng nhóm.
- **Vì sao có:**
  - *Góc nhìn IBS:* Hiển thị `"80% đạt"` mà thiếu mẫu số thì người đọc không phân biệt được `32/40` (đủ dữ liệu) hay `32/45` (5 sinh viên chưa đủ dữ liệu). Đặc biệt, học kỳ chưa mở lớp hoặc mức Bloom không có CLO nào mà trả về `0%` sẽ làm Hội đồng hiểu nhầm là *"100% sinh viên rớt"*, trong khi bản chất là *"chưa có dữ liệu đo lường"*.
  - *Góc nhìn IT:* Xử lý triệt để phép chia cho 0 (`NaN` / `Infinity` trong IEEE 754 `double`) ngay tại DTO factory trước khi serialize JSON hoặc xuất CSV (`N/A`).
- **Tham số/thư viện từ đâu:** Quy tắc cứng số 2 trong `doc/plan/F07-bao-cao-truy-vet.md` §4 và `.claude/rules/02-domain-invariants.md` (không bao giờ trả tỷ lệ đạt mà thiếu mẫu số).
- **Bỏ đi thì sao:** Biểu đồ xu hướng qua các học kỳ (`TIME_TERM`) sẽ bị kéo tụt về `0%` ở những kỳ môn học không mở lớp, hoặc tính trung bình cộng đơn thuần `(mean1 + mean2) / 2` làm sai lệch trọng số giữa lớp 80 sinh viên và lớp 15 sinh viên.

## 3. Thiết kế 7 chiều phân tích & Bất biến mức nhận thức Bloom (`AnalyticsDimension`)

- **Làm gì:** Chuẩn hóa cả 7 góc nhìn phân tích về duy nhất một cấu trúc ma trận 2D (`rows x columns`):
  1. `TIME_TERM`: Dòng = `CLO/PI/PLO`, Cột = Học kỳ (`Term`).
  2. `COURSE_SECTIONS`: Dòng = `CLO`, Cột = Các lớp học phần song song (`CourseSection`).
  3. `COHORT`: Dòng = `PLO/PI`, Cột = Khóa tuyển sinh (`K23`, `K24`, ...).
  4. `ASSESSMENT_COMPONENT`: Dòng = `CLO`, Cột = Loại bài đánh giá (`QUIZ`, `MIDTERM`, `FINAL`, ...) đọc từ cấp `ASSESSMENT_CLO`.
  5. `CURRICULUM_CLASSIFICATION`: Gom nhóm theo `PLO_CATEGORY` (`GENERAL`/`SPECIALIZED`), `COURSE_TYPE` (`COMPULSORY`/`ELECTIVE`), hoặc `BLOOM_LEVEL` (`1..6`).
  6. `COURSE_LECTURER`: Dòng = `CLO`, Cột = Giảng viên phụ trách các lớp của học phần (`course_sections.lecturerIds`), gộp gia quyền kết quả các lớp mà từng giảng viên giảng dạy.
  7. `STUDENT`: Dòng = `CLO` (cấp môn/lớp) hoặc `PI/PLO` (cấp CTĐT), Cột = Sinh viên (`studentRef`), đọc trực tiếp từ `student_results` (`COMPUTED` so với chưa đủ dữ liệu `insufficientData = 1`).
- **Vì sao có:**
  - *Góc nhìn IBS:* Phục vụ chu trình cải tiến liên tục (CQI) ở cả cấp vĩ mô lẫn vi mô: Quản lý CTĐT & QA dùng `COURSE_LECTURER` kết hợp `STUDENT` để đánh giá khách quan sự đồng đều chất lượng giảng dạy của từng giảng viên trên cùng một học phần và đối chiếu xuống kết quả cá nhân từng sinh viên; Giảng viên dùng `COURSE_SECTIONS`, `ASSESSMENT_COMPONENT` và `STUDENT` trong lớp mình dạy để phát hiện câu hỏi/CLO hoặc sinh viên cần hỗ trợ sớm.
  - *Góc nhìn IT:* Gom cả 7 chiều về một hợp đồng `AnalyticsMatrixResponse` giúp Frontend (`LMS-UI`) chỉ cần viết một component bảng nhiệt (heatmap matrix) và một luồng xuất CSV dùng chung thay vì 7 API rời rạc. Riêng `BLOOM_LEVEL` chỉ dùng `Clo.bloomLevel` làm khóa `groupingBy`, tuyệt đối không nhân hệ số vào điểm.
- **Tham số/thư viện từ đâu:** Các enum có sẵn trong CSDL v0.7 (`ResultLevel`, `AssessmentType`, `PloCategory`, `CourseType`, `Clo.bloomLevel`).
- **Bỏ đi thì sao:** Báo cáo OBE chỉ dừng ở bảng phẳng từng lớp đơn lẻ, không trả lời được câu hỏi đối sánh ngang (giữa các lớp/khóa) và theo dõi dọc (qua các học kỳ, giữa giữa kỳ và cuối kỳ).

## 4. Phân quyền theo phạm vi Fail-closed trên 4 vai trò (`verifyScope` & `validateRunAuthorization`)

- **Làm gì:** Kiểm tra hai lớp: (1) quyền trên tham số lọc (`programVersionId`, `courseId`, `sectionIds`) và chiều phân tích; (2) quyền trên chính `runId` được yêu cầu. Cụ thể:
  - `ADMIN` và `QA` có `scope` rỗng nghiêm ngặt (strict empty scope): đọc toàn hệ thống.
  - `PROGRAM_MANAGER`: bắt buộc chỉ định `programVersionId` hoặc `courseId` thuộc phạm vi quản lý (chặn truy vấn rỗng toàn hệ thống).
  - `LECTURER`: bị chặn `403 Forbidden` ở chiều cấp CTĐT (`COHORT`, `PLO_CATEGORY`), chỉ được xem lớp/học phần mình phụ trách và không thể truyền `runId` của CTĐT/lớp ngoại lai để dò dữ liệu.
- **Vì sao có:** Đảm bảo nguyên tắc tối thiểu đặc quyền (Least Privilege) và chống lỗ hổng IDOR (Insecure Direct Object Reference) qua tham số `runId` hoặc `sectionIds`.
- **Tham số/thư viện từ đâu:** `ScopeAuthorizationService` (ADR 0003, ADR 0014) kết hợp kiểm tra vai trò từ `UserPrincipal`.
- **Bỏ đi thì sao:** Giảng viên lớp A có thể truyền `sectionIds` của giảng viên lớp B hoặc truyền `runId` của ngành khác để xem/xuất điểm lớp không thuộc phân công giảng dạy.

## 5. Xuất CSV & Excel `.xlsx` (`MatrixCsvExporter`, `MatrixExcelExporter`, `ReportExcelExporter`, `ExcelExportSupport`)

- **Làm gì:**
  - `MatrixCsvExporter`: Chuyển `AnalyticsMatrixResponse` thành mảng `byte[]` UTF-8 có 3 byte BOM (`0xEF, 0xBB, 0xBF`) ở đầu tệp, gồm cả bảng ma trận tỷ lệ đạt và bảng chi tiết 10 chỉ số thô.
  - `ReportExcelExporter` & `MatrixExcelExporter` (dùng chung `ExcelExportSupport`): Xuất bản chụp `Report` (sheet `BaoCao` cho cả 4 loại báo cáo kể cả `DATA_COMPLETENESS`) và ma trận phân tích đa chiều 2 sheet (`MaTranPhanTich` + `DuLieuChiTiet`) sang định dạng `.xlsx` khi truyền `?format=xlsx` (mặc định giữ `format=csv` để tương thích ngược; định dạng khác ngoài `csv|xlsx` trả `400`).
  - Chống Formula Injection (CWE-1236) trên cả CSV và Excel (`CellType.STRING` kèm tiền tố `'` nếu giá trị sau `trim()` bắt đầu bằng `=`, `+`, `-`, `@` hoặc bắt đầu bằng `\t`, `\r`). Các ô có mẫu số bằng `0` (`mean`, `passRate`, `passRateOfEnrolled` là `null`) được giữ trống hoàn toàn trong Excel, không bao giờ ép về `0.0`.
- **Vì sao có:**
  - Hỗ trợ cả xuất nhanh CSV UTF-8 BOM lẫn tệp `.xlsx` định dạng sẵn bảng, phần trăm (`0.00%`), số thập phân (`0.000`) và tách 2 sheet riêng biệt để QA/Giảng viên nộp minh chứng trực tiếp mà không phải tách cột thủ công.
  - Tính độ rộng cột bằng số ký tự thay vì `sheet.autoSizeColumn()` để tránh lỗi thiếu font AWT trên container Linux headless trong CI/staging.
- **Tham số/thư viện từ đâu:** CSV cài đặt thuần bằng `java.nio.charset.StandardCharsets` và `StringBuilder` của JDK 25; Excel tái sử dụng `org.apache.poi:poi-ooxml:6.0.0` (đã chốt ở ADR 0015 cho nhập/xuất mẫu, không thêm dependency mới) và `ImportTemplateResponses.XLSX_MEDIA_TYPE`.
- **Bỏ đi thì sao:** Người dùng phải tự chuyển đổi CSV hoặc định dạng lại bảng số liệu khi lập hồ sơ kiểm định, hoặc tồn tại rủi ro thực thi công thức (CWE-1236) / sập dịch vụ trên môi trường Linux không có font đồ họa nếu dùng `autoSizeColumn()`.
