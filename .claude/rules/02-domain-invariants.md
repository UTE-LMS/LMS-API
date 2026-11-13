# Rule 02 — Bất biến nghiệp vụ và kiến trúc

Căn cứ: `doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`, `doc/GiaiThich_CoSoDuLieu.md` (v0.7 — 25 collections), `doc/database_import.js`.
Rubric Nguyên tắc 2: nghiệp vụ chỉ có "hợp lý / không hợp lý so với mục tiêu và sản phẩm đã thống nhất".
Các bất biến dưới đây **chính là** mục tiêu đã thống nhất; mã vi phạm chúng là không hợp lý (TC2.1, TC2.2).

## Kiến trúc

- Angular chỉ gọi API Spring Boot, không bao giờ truy cập MongoDB trực tiếp.
- Công thức đo lường chính thức chỉ chạy ở backend. UI hiển thị kết quả API trả về, không tự tính lại.
- Backend kiểm tra quyền và dữ liệu trước khi xử lý. Quyền theo `users.roleAssignments` (vai trò + phạm vi);
  ẩn nút ở UI không thay cho kiểm tra ở backend.
- MongoDB không có khóa ngoại: tầng service phải kiểm tra tham chiếu (`cloId` tồn tại và thuộc cùng `syllabusId`, cặp `CLO–PI` nằm trong `clo_pi_links`…) trước khi ghi.
- Xác nhận nhập điểm cần transaction nhiều document → MongoDB chạy replica set (cả dev và CI). Dev: image
  `docker/mongo/` (README).
- **Bài đánh giá (`assessments`) do Giảng viên lớp học phần tự khóa ma trận** (CSDL v0.7, ADR 0014):
  `DRAFT → LOCKED → SCORE_ENTERED`. Giảng viên phụ trách lớp khai báo danh sách câu hỏi (`items[]`), mỗi câu hỏi ánh xạ tới 1 hoặc
  nhiều CLO kèm điểm tối đa trực tiếp `outcomeMappings[]: { cloId, maxScore }` ($maxScore > 0$), khai báo tỷ lệ đóng góp của bài kiểm tra cho từng CLO xuất hiện trong bài `cloWeights[]: { cloId, weight }` ($W_{p,j} \in (0, 1]$), và **tự khóa ma trận** (`isLocked = true` khi `status ∈ {LOCKED, SCORE_ENTERED}`, cấm sửa `items[]`). Chỉ nhập điểm khi ma trận đã khóa (`LOCKED` hoặc `SCORE_ENTERED`); có thể mở khóa (`UNLOCK`) về `DRAFT` khi và chỉ khi chưa có điểm nào trong `scores`. Khóa/mở khóa ghi `audit_logs` (`ASSESSMENT_LOCK`, `ASSESSMENT_UNLOCK`). Hệ thống tuyệt đối không lưu nội dung câu hỏi/đáp án/ngân hàng câu hỏi và không đo điểm tổng bài kiểm tra (`totalPoints`, `weightInCourse`) hay điểm học phần (`COURSE`).
- **Khởi tạo CTĐT & Đề cương bằng Import CSV (`UNVERIFIED -> VERIFIED`):** Vai trò Quản lý học thuật (`PROGRAM_MANAGER` / `ADMIN`) nạp (Import) tệp CSV CTĐT và Đề cương chi tiết học phần kèm sẵn ngưỡng đạt bắt buộc (`PassThreshold` $\in (0, 1]$, không để trống) của từng `PLO`, `PI`, `CLO` và trọng số đóng góp ($W_{PI \to PLO} \in (0, 1]$ với tổng mỗi PLO $= 1.0$; $W_{CLO \to PI} \in (0, 1]$ không ép tổng $= 1.0$). Dữ liệu vừa Import lưu ở trạng thái `UNVERIFIED` để hiển thị lên giao diện kiểm tra (sai thì sửa CSV import đè lại), bấm Lưu mới chuyển sang `VERIFIED`.
- Phân quyền theo 4 vai trò chuẩn hóa (ADR 0014 & OBE Schema v0.7): `ADMIN` (quản trị toàn hệ thống), `PROGRAM_MANAGER` (quản lý CTĐT, đề cương & bài mẫu), `LECTURER` (giảng viên phụ trách lớp học phần, tự khóa ma trận đề, nhập điểm), `QA` (Cán bộ Đảm bảo chất lượng & Khảo thí, xem báo cáo đo lường chất lượng, thẩm định CTĐT); `scope = {programIds, courseIds, sectionIds}`. *(Loại bỏ `DEPARTMENT_HEAD`; `STUDENT` là thực thể dữ liệu học vụ trong `students`/`enrollments` để tính mẫu số, không đăng nhập)*.
- Tệp minh chứng/tệp điểm gốc: lưu thông tin mô tả và tham chiếu trong MongoDB, nơi lưu tệp thiết kế riêng.

## Quy tắc đo lường 4 cấp độ (`ASSESSMENT_CLO` → `CLO` → `PI` → `PLO`)

- **Không hardcode** trọng số hay ngưỡng đạt trong mã nguồn Java. Đọc trực tiếp `passThreshold` và `weight` từ `plos`, `pis`, `clos`, `clo_pi_links`, `assessments.cloWeights` (đã loại bỏ collection `measurement_rules`). Chỉ dữ liệu đã `VERIFIED` mới được dùng cho tính toán chuẩn đầu ra.
- **Điểm CLO của từng câu hỏi do Giảng viên nhập trực tiếp qua tệp CSV mẫu sinh từ ma trận đề đã khóa:**
  - Một câu hỏi có thể đánh giá 1 hoặc nhiều CLO; từng cặp `(itemId, cloId)` có `maxScore` riêng trong `outcomeMappings` và được tách thành cột riêng trong tệp CSV mẫu.
  - Khóa duy nhất của `scores`: `{ sectionId, assessmentId, itemId, cloId, studentId }`.
  - **Sinh viên vắng thi hoặc bỏ trống câu hỏi:** ghi nhận trực tiếp `rawScore = 0.0` (đã bỏ `scores.status` và `missingDataPolicy`).
- **Chuỗi công thức 4 cấp độ (`RESULT_LEVELS = ['ASSESSMENT_CLO', 'CLO', 'PI', 'PLO']`):**
  1. **Cấp độ 1 (`level = ASSESSMENT_CLO`, `assessmentId != null`):** $CLO_{s,p,j} = \frac{Score_{s,p,j}}{MaxScore_{p,j}} \in [0, 1]$, so với $\text{PassThreshold}(CLO_j)$. Mẫu số thống kê lớp là $N_{\text{section}}$ (sĩ số sinh viên `ENROLLED`).
  2. **Cấp độ 2 (`level = CLO`, `assessmentId = null`):** $Value(CLO_{s,j}) = \sum_{p \in P(CLO_j)} W_{p,j} \times CLO_{s,p,j}$ (bắt buộc $\sum_{p \in P(CLO_j)} W_{p,j} = 1.0$ khi tổng hợp học phần), so với $\text{PassThreshold}(CLO_j)$. Mẫu số thống kê lớp là $N_{\text{section}}$.
  3. **Cấp độ 3 (`level = PI`):** Tổng hợp từ các CLO của một hoặc nhiều học phần mà sinh viên $s$ đã học ($\text{Evaluated}(s, PI_m)$; nếu học lại học phần $c$ thì lấy kết quả của **lần học gần nhất `LATEST`**) theo công thức trung bình có trọng số chuẩn hóa:
     $$Value(PI_{s,m}) = \frac{\sum_{(c, j) \in \text{Evaluated}(s, PI_m)} W_{CLO_{c,j} \to PI_m} \times Value(CLO_{s,c,j})}{\sum_{(c, j) \in \text{Evaluated}(s, PI_m)} W_{CLO_{c,j} \to PI_m}}$$
     so với $\text{PassThreshold}(PI_m)$. Mẫu số thống kê cấp khóa/CTĐT là $N_{\text{cohort}}$.
  4. **Cấp độ 4 (`level = PLO`):** Tổng hợp có trọng số từ các PI thuộc PLO ($\sum_{m \in PI(PLO_q)} W_{PI_m \to PLO_q} = 1.0$), so với $\text{PassThreshold}(PLO_q)$. Mẫu số thống kê cấp khóa/CTĐT là $N_{\text{cohort}}$.

## Phân biệt khái niệm (sai là lỗi nghiệp vụ)

| Không đồng nhất | Hệ quả khi code |
|---|---|
| Mức Bloom ≠ điểm số | Lưu riêng `bloomLevel`, không có trường quy đổi sang điểm |
| I/R/M ≠ trọng số | `course_pi_contributions` không được dùng làm trọng số; cấm map I=1, R=2, M=3 (ADR 0012) |
| `capabilityLevelRaw` ≠ Bloom | Lưu nguyên giá trị nguồn, không diễn giải |
| Hệ thống chỉ đo chuẩn đầu ra (4 cấp độ) | Không tính điểm bài kiểm tra tổng (`totalPoints`, `weightInCourse`), không xét điểm qua môn học phần (`level = COURSE` đã xóa) |
| `ASSESSMENT_CLO` ≠ `CLO` toàn học phần | Phân biệt rõ qua `level` (`ASSESSMENT_CLO` vs `CLO`) và `assessmentId` (`!= null` vs `null`) |
| Mẫu số cấp lớp ($N_{\text{section}}$) ≠ Mẫu số cấp khóa ($N_{\text{cohort}}$) | Cấp 1 & 2 chia cho $N_{\text{section}}$ (`ENROLLED`); Cấp 3 & 4 chia cho $N_{\text{cohort}}$ |

## Dữ liệu và định danh

- CLO nhận diện bằng `_id` (mã `CLO1` chỉ duy nhất trong một đề cương). PLO/PI nhận diện bằng **mã**, không
  bằng thứ tự dòng (`displayOrder` là trường riêng).
- Mọi dữ liệu và kết quả gắn phiên bản: `programVersionId`, `syllabusId`. Bài đánh giá cấp lớp (`SECTION`) khi đã khóa (`LOCKED` / `SCORE_ENTERED`) bị cấm sửa `items`.
- Truy vết được từ kết quả → `inputs[]` → `scores` → `import_batches` + số dòng trong file gốc.
- Sửa điểm, xác nhận nhập, khóa/mở khóa ma trận, chạy tính, đăng nhập đều ghi `audit_logs` (trước/sau, lý do).
- ML, Ngân hàng câu hỏi và bảng `measurement_rules` rời rạc **đã loại bỏ**. Hệ thống gồm **25 collection chính** (`doc/database_import.js`).

## Riêng tư

- Không đưa dữ liệu điểm/sinh viên thật vào repo, test, ảnh chụp, commit. Seed và test chỉ dùng dữ liệu giả có nhãn.
