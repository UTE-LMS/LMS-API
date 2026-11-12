# ADR 0014 — Import CSV CTĐT & Đề cương (`UNVERIFIED -> VERIFIED`), Giảng viên tự khóa ma trận, Nhập điểm CLO trực tiếp (Vắng = 0đ) và Đo lường 4 cấp độ (`ASSESSMENT_CLO -> CLO -> PI -> PLO`)

- **Trạng thái:** ĐÃ CHẤP NHẬN (ACCEPTED) theo **quyết định của sinh viên (`minhat5`)** ngày 2026-11-11.
- **Ngày:** 2026-11-11
- **Người soạn:** Antigravity theo yêu cầu của sinh viên Minh (`minhat5`); sinh viên rà soát và chốt: [ĐIỀN]
- **Thay thế / Cập nhật:** Thay thế ADR 0001 (`0001-duyet-ma-tran-de-thi.md`); cập nhật ADR 0010 (`0010-phan-quyen-de-cuong-theo-bo-mon-va-giang-vien.md`), ADR 0011 (`0011-pi-suy-ra-tu-clo.md`), ADR 0013 (`0013-nhap-diem-luu-tep-goc-loi-cap-tep.md`)
- **Liên quan:** `doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`, `doc/ke-hoach-sua-doi-theo-nghiep-vu-moi.md`, `doc/database_import.js` (25 collections), `doc/GiaiThich_CoSoDuLieu.md` (v0.7), `.claude/rules/02-domain-invariants.md`

---

## 1. Bối cảnh

Sau khi rà soát luồng nghiệp vụ thực tế và tinh gọn hệ thống để phục vụ chuyên biệt cho bài toán đo lường Chuẩn đầu ra (OBE):
1. **Khởi tạo dữ liệu học thuật qua CSV (`UNVERIFIED -> VERIFIED`):** Một vai trò quản lý học thuật (`PROGRAM_MANAGER` / `ADMIN`) import tệp CSV chuẩn hóa của Chương trình đào tạo (CTĐT) và Đề cương chi tiết học phần kèm sẵn ngưỡng đạt bắt buộc (`PassThreshold` $\in (0, 1]$) của từng `PLO`, `PI`, `CLO` và trọng số đóng góp ($W_{PI \to PLO} \in (0, 1]$, $W_{CLO \to PI} \in (0, 1]$).
2. **Bảo mật đề thi tuyệt đối & Loại bỏ bảng quy tắc rời rạc:** Hệ thống tuyệt đối không lưu trữ nội dung câu hỏi/đề bài/đáp án hay ngân hàng câu hỏi (bỏ `question_banks`, `question_categories`, `questions`) và bỏ bảng `measurement_rules` rời rạc vì toàn bộ ngưỡng đạt và trọng số đã gắn bắt buộc trên `plos`, `pis`, `clos`, `clo_pi_links`, `assessments.cloWeights` (từ 29 xuống **25 collections**).
3. **Giảng viên tự khóa ma trận đề & Khai báo tỷ lệ đóng góp bài kiểm tra ($W_{p,j}$):** Giảng viên phụ trách lớp học phần tự thiết lập ma trận câu hỏi – CLO, khai báo tỷ lệ đóng góp $W_{p,j} \in (0, 1]$ của từng bài kiểm tra cho từng CLO xuất hiện trong bài (`cloWeights`), và **tự khóa ma trận** (`DRAFT -> LOCKED -> SCORE_ENTERED`).
4. **Xuất CSV mẫu từ ma trận đã khóa & Nhập điểm CLO trực tiếp (Vắng thi / Bỏ trống = `0.0`):** Mỗi câu hỏi ánh xạ tới 1 hoặc nhiều CLO với điểm tối đa `maxScore` riêng cho từng CLO. Hệ thống sinh tệp CSV mẫu từ ma trận đã khóa; sinh viên vắng thi hoặc bỏ trống câu hỏi được tính trực tiếp bằng `0.0` điểm (bỏ `scores.status` và `missingDataPolicy`).
5. **Đo lường và lưu trữ kết quả xuyên suốt 4 cấp độ (`ASSESSMENT_CLO -> CLO -> PI -> PLO`):** Loại bỏ cấp `COURSE`, lưu kết quả ở cả 4 cấp độ với công thức trung bình có trọng số chuẩn hóa ở tầng `CLO -> PI` (không ép tổng $\sum W_{CLO \to PI} = 1.0$ để hỗ trợ môn tự chọn; học lại lấy lần học gần nhất `LATEST`).

---

## 2. Quyết định

### 2.1. Import CSV CTĐT & Đề cương kèm Trọng số và Ngưỡng đạt bắt buộc (`UNVERIFIED -> VERIFIED`)
- **`plos`:** bắt buộc có `passThreshold` $\in (0, 1]$.
- **`pis`:** bắt buộc có `weight` $\in (0, 1]$ ($W_{PI \to PLO}$, tổng các PI trong cùng 1 PLO $= 1.0$) và `passThreshold` $\in (0, 1]$.
- **`clos`:** trong `measurement`, bỏ `defaultWeight`, bắt buộc có `defaultPassThreshold` $\in (0, 1]$.
- **`clo_pi_links`:** bắt buộc có `weight` $\in (0, 1]$ ($W_{CLO_{c,j} \to PI_m}$, không ép tổng qua mọi học phần bằng $1.0$).
- Dữ liệu vừa Import lưu ở trạng thái `UNVERIFIED` để hiển thị trên giao diện kiểm tra (nếu sai thì sửa tệp CSV rồi Import đè lại), bấm Lưu mới chuyển sang `VERIFIED`.

### 2.2. Rút gọn Schema từ 29 xuống 25 Collections
- Xóa 4 collections: `question_banks`, `question_categories`, `questions`, `measurement_rules`.
- Xóa trường khóa ngoại `ruleId` khỏi `calculation_runs`, `student_results`, `aggregate_results`, `reports`.
- Rút gọn `assessments`: xóa `totalPoints`, `weightInCourse`, `dataGranularity`, `approval`, `exportedFiles`; trên `items[]` chỉ giữ `_id`, `code`, `orderIndex`, `bloomLevel`, `outcomeMappings: [{ cloId, maxScore }]`; bổ sung `cloWeights: [{ cloId, weight }]`.

### 2.3. Giảng viên tự khóa ma trận đề (`DRAFT -> LOCKED -> SCORE_ENTERED`)
- Thay `/submit`, `/approve`, `/reject` bằng `POST /api/v1/assessments/{id}/lock`, `POST /api/v1/assessments/{id}/unlock` và `DELETE /api/v1/assessments/{id}`.
- Khi khóa từng bài kiểm tra đơn lẻ trong học kỳ: kiểm tra $0 < W_{p,j} \le 1.0$ cho các CLO có trong bài và tổng tích lũy $\le 1.0$; khi chạy tổng hợp CLO toàn học phần (Cấp độ 2) bắt buộc $\sum_{p \in P(CLO_j)} W_{p,j} = 1.0$.

### 2.4. Xuất CSV mẫu & Nhập điểm trực tiếp (Vắng thi / Bỏ trống = `0.0`)
- Trong `scores`: xóa `status`, `rubricLevel`; bắt buộc `cloId`, `rawScore >= 0`, `maxScore > 0` với khóa duy nhất `{ sectionId: 1, assessmentId: 1, itemId: 1, cloId: 1, studentId: 1 }`.
- Khi Import CSV điểm: hiển thị bảng điểm lên giao diện kiểm tra trước (nếu sai sửa CSV rồi Import lại), khi bấm Lưu ghi trực tiếp vào `scores`.

### 2.5. Đo lường và lưu trữ 4 cấp độ (`RESULT_LEVELS = ['ASSESSMENT_CLO', 'CLO', 'PI', 'PLO']`)
- **Cấp độ 1 (`level = 'ASSESSMENT_CLO'`, `assessmentId != null`):** $CLO_{s,p,j} = \frac{Score_{s,p,j}}{MaxScore_{p,j}}$, mẫu số lớp $N_{\text{section}}$ (`ENROLLED`).
- **Cấp độ 2 (`level = 'CLO'`, `assessmentId = null`):** $Value(CLO_{s,j}) = \sum_{p \in P(CLO_j)} W_{p,j} \times CLO_{s,p,j}$ ($\sum_p W_{p,j} = 1.0$), mẫu số lớp $N_{\text{section}}$.
- **Cấp độ 3 (`level = 'PI'`, `assessmentId = null`):** Lấy kết quả CLO của lần học gần nhất (`LATEST`) nếu sinh viên học lại học phần $c$, tính theo trung bình có trọng số chuẩn hóa trên tập $\text{Evaluated}(s, PI_m)$:
  $$Value(PI_{s,m}) = \frac{\sum_{(c, j) \in \text{Evaluated}(s, PI_m)} W_{CLO_{c,j} \to PI_m} \times Value(CLO_{s,c,j})}{\sum_{(c, j) \in \text{Evaluated}(s, PI_m)} W_{CLO_{c,j} \to PI_m}}$$
  mẫu số cấp khóa/CTĐT $N_{\text{cohort}}$.
- **Cấp độ 4 (`level = 'PLO'`, `assessmentId = null`):** $Value(PLO_{s,q}) = \frac{\sum_{m \in \text{Evaluated}(s, PLO_q)} W_{PI_m \to PLO_q} \times Value(PI_{s,m})}{\sum_{m \in \text{Evaluated}(s, PLO_q)} W_{PI_m \to PLO_q}}$, mẫu số cấp khóa/CTĐT $N_{\text{cohort}}$.
