# Chốt danh sách feature của đồ án LOMS

> **Trạng thái: ĐỀ XUẤT CHỐT (cập nhật 2026-11-10 theo ADR 0013), chưa ký.** File này là bản quyết định phạm vi tính năng, soạn từ
> `tom-tat-du-an-de-thao-luan.md` (2026-10-21), `dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md` và thiết kế CSDL v0.6 (`GiaiThich_CoSoDuLieu.md`, cập nhật theo
> ADR 0013). Nó trả lời các câu hỏi phạm vi ở mục 12 của bản tóm tắt. Nó **không thay** bản cam kết
> `cam-ket-san-pham-metric.md` (cổng K1, cần chữ ký GVHD): sau khi hai bạn đồng ý, chép cột "Quyết định" ở mục 3
> sang mục 4 của bản cam kết.
>
> Ngày soạn: 2026-10-23 (cập nhật 2026-11-10 theo ADR 0013).

Quy ước: **C** = cam kết (phải chạy được khi bảo vệ), **MR** = mục tiêu mở rộng (làm nếu còn thời gian, không
tính vào cam kết), **K** = không làm.

---

## 1. Tóm tắt quyết định

| Hạng mục | Số lượng | Ghi chú |
|---|---|---|
| Story cam kết (C) | **31** | Đã bỏ US-16 (rubric) sang K theo ADR 0013; giữ NEW-01 (giảng viên tự khóa ma trận) |
| Mục tiêu mở rộng (MR) | 9 | Đã chuyển US-13, US-14 (ngân hàng câu hỏi) sang K theo ADR 0013 |
| Không làm (K) | 11 | Mục 4 (gồm US-13, US-14, US-16, US-17) |
| Collection dùng | **25** | CSDL v0.7 gồm 25 collection (đã bỏ `ml_experiments`, 3 collection ngân hàng câu hỏi và collection `measurement_rules` theo ADR 0014) |

---

## 2. Luồng nghiệp vụ cam kết (tiêu chí nghiệm thu — cập nhật theo ADR 0014)

```
Import CSV CTĐT & Đề cương kèm sẵn Ngưỡng đạt bắt buộc và Trọng số (PROGRAM_MANAGER / ADMIN — UNVERIFIED -> VERIFIED)
  → Khởi tạo tự động CTĐT, PLO/PI (kèm passThreshold và weight PI->PLO), học phần, đề cương, CLO (kèm defaultPassThreshold), liên kết CLO–PI (kèm weight CLO->PI)
  → Giảng viên lớp học phần tạo bài đánh giá bản lớp (items[] kèm outcomeMappings: [{cloId, maxScore}], cloWeights: [{cloId, weight}])
  → Giảng viên tự khóa ma trận (DRAFT → LOCKED) → xuất tệp CSV mẫu từ ma trận đề đã khóa
  → Giảng viên nhập điểm trực tiếp từng CLO của từng câu hỏi qua CSV mẫu (Vắng thi / Bỏ trống = 0.0) → xem trước trên UI → Lưu vào scores
  → Chạy đo lường 4 cấp độ (ASSESSMENT_CLO -> CLO toàn học phần -> PI chuẩn hóa mẫu số -> PLO)
  → Tổng hợp bài đánh giá / lớp học phần (N_section) và cấp khóa / CTĐT (N_cohort)
  → Báo cáo (có limitations) → truy vết ngược về dòng tệp gốc
  → Ghi nhận vấn đề và hành động cải tiến
```

Demo nghiệm thu đi đúng luồng này trên môi trường đã triển khai, dùng dữ liệu thử nghiệm có nhãn
"DỮ LIỆU THỬ NGHIỆM" cho tới khi Khoa cấp dữ liệu thật.

---

## 3. Danh sách story đã chốt

Cột "Đề xuất" phân biệt story gốc (giữ nguyên ưu tiên) với thay đổi so với kế hoạch gốc (có lý do ở cột cuối).

### E01 Người dùng và quyền (F01)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-01 | Đăng nhập / đăng xuất | **C** | |
| US-02 | Tạo người dùng, gán vai trò và phạm vi | **C** | 4 vai trò chuẩn hóa hệ thống (ADR 0014): ADMIN, PROGRAM_MANAGER, LECTURER, QA |
| US-03 | Backend kiểm tra quyền theo phạm vi mọi API | **C** | Giảng viên chỉ thấy và nhập điểm lớp mình; trái phạm vi trả 403 |
| US-04 | Màn hình xem nhật ký thao tác | MR | **Việc ghi `audit_logs` vẫn thuộc cam kết**, nằm trong AC của US-24, US-26, US-27, NEW-01 (sửa điểm, xác nhận nhập, chạy tính, xác nhận quy tắc, duyệt đề, đăng nhập) |

### E02 CTĐT, PLO, PI (F02)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-05 | Chương trình và phiên bản CTĐT (hỗ trợ Import CTĐT — Phương án R1, ADR 0013) | **C** | `(programId, version)` duy nhất; có `totalCredits`, `durationYears`; `PROGRAM_MANAGER` nạp từ file CTĐT |
| US-06 | 7 PLO, 17 PI theo mã | **C** | Nhận diện bằng mã; `category`, `displayOrder`; không tự điền nguyên văn |
| US-07 | Ma trận học phần đóng góp PI (I/R/M) | **C** | I/R/M chỉ là thiết kế sư phạm, không là trọng số |
| US-08 | Quy trình xác nhận nội dung | MR | Trạng thái `verification` vẫn có trong dữ liệu; chỉ làm màn hình luồng xác nhận nếu còn thời gian. Người thẩm định CTĐT, PLO, PI: `QA` |

### E03 Học phần, CLO (F03)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-09 | Học phần và vị trí trong CTĐT | **C** | `type` là `COMPULSORY`/`ELECTIVE` |
| US-10 | Đề cương theo phiên bản (hỗ trợ Import Đề cương — Phương án R1, ADR 0013) | **C** | `PROGRAM_MANAGER` nạp từ file Đề cương học phần |
| US-11 | CLO thuộc một đề cương | **C** | `code` duy nhất trong một đề cương; Bloom 1–6 |
| US-12 | Liên kết CLO–PI và xác nhận | **C** | Chỉ liên kết `VERIFIED` được dùng để tính chính thức |

### E04 Bài đánh giá & Ma trận Câu hỏi – CLO (F04)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-13 | Ngân hàng câu hỏi và cây danh mục 3 cấp | **K** | **Đã loại bỏ hoàn toàn theo ADR 0013** để bảo mật đề thi (xóa 3 collection `question_banks`, `question_categories`, `questions`) |
| US-14 | Câu hỏi trong ngân hàng | **K** | **Đã loại bỏ hoàn toàn theo ADR 0013** (không lưu nội dung câu hỏi/ngân hàng đề) |
| US-15 | Bài đánh giá khung (theo đề cương) và bản lớp | **C** | `items[]` chỉ lưu mã câu hỏi (`Câu 1`, `Câu 2`...) và `outcomeMappings: [{cloId, maxScore}]` ($maxScore > 0$ do giảng viên khai báo trực tiếp cho từng CLO của câu hỏi, không dùng `weight`) |
| US-16 | Tiêu chí rubric, mức rubric 1–4 | **K** | **Đã loại bỏ theo ADR 0013**: hệ thống chỉ nhận điểm số trực tiếp của từng CLO trong từng câu hỏi |
| US-17 | Nhập câu hỏi từ Excel/Moodle | **K** | Đã loại bỏ để bảo mật đề thi |
| **NEW-01** | **Giảng viên tự khóa ma trận bài đánh giá:** giảng viên phụ trách lớp thiết lập ma trận câu hỏi – CLO và tự khóa ma trận (`DRAFT → LOCKED`), mở cổng nhập điểm CLO, kèm bảng độ phủ theo CLO và theo Bloom (`coverageSummary`) | **C** | **Cập nhật theo ADR 0013 (thay thế ADR 0001):** Bỏ bước Trưởng bộ môn duyệt ma trận; giảng viên tự khóa (`LOCKED`) và có thể mở khóa (`UNLOCK`) nếu chưa có điểm trong `scores` |

### E05 Học kỳ, lớp, sinh viên

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-18 | Học kỳ và khóa sổ điểm | **C** | Học kỳ `SCORE_LOCKED` thì không nhập/sửa điểm |
| US-19 | Lớp học phần, giảng viên phụ trách | **C** | |
| US-20 | Nhập danh sách lớp → sinh viên, ghi danh | **C** | Ghi danh là mẫu số; dùng `studentRef` (mã thay thế) |

### E06 Nhập điểm CLO (F05b)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-21 | Nhập điểm trực tiếp từng CLO của từng câu hỏi (trên lưới hoặc tải tệp CSV/Excel) | **C** | Khớp từng cặp `(itemId, cloId)` trong ma trận đã khóa (`LOCKED`/`SCORE_ENTERED`) |
| US-22 | Kiểm tra, báo lỗi theo dòng/cột | **C** | 4 loại lỗi: mã sinh viên sai, điểm vượt `maxScore` của CLO tại câu hỏi đó, trùng bản ghi, thiếu trường |
| US-23 | Chọn BLANK/MISSING cho ô trống; chính sách nhập lại | **C** | Chính sách nhập lại cam kết: `REJECT_ON_CONFLICT`. `OVERWRITE`, `UPDATE_BLANK_ONLY` là MR |
| US-24 | Xác nhận nhập trong một transaction hoặc rollback | **C** | Cần MongoDB replica set. Chỉ nhập được khi bài ở `LOCKED`/`SCORE_ENTERED` |
| US-25 | Sửa điểm đơn lẻ có lý do | MR | Nếu làm: dùng `scores.revision`, ghi audit trước/sau |

### E07 Đo lường chuẩn đầu ra (F06)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-26 | Quy tắc đo lường có phiên bản | **C** | Cấu hình `measurement_rules`; chỉ `CONFIRMED` mới cho kết luận chính thức (đã bỏ khối `course`) |
| US-27 | Tính CLO của từng sinh viên (cả từng bài đánh giá và tích lũy toàn học phần) | **C** | **Phương án B1 (`POINTS_RATIO`, ADR 0013):** tỷ lệ tổng điểm thực tế đạt được trên tổng điểm tối đa của CLO; kèm khóa chống chạy trùng bằng `lockKey` |
| US-28 | Tính PI và PLO | **C** | PI: `FROM_CLOS` (suy ra từ kết quả CLO toàn học phần theo `clo_pi_links.weight`, ADR 0011). PLO: cài đặt phương pháp cấu hình trong `measurement_rules.plo` |
| US-29 | Tổng hợp cấp bài đánh giá / lớp / học phần / khóa / CTĐT kèm mẫu số | **C** | Luôn trả đủ `counts` (ghi danh, đủ dữ liệu, đạt, chưa đạt, chưa đủ dữ liệu) và `passRateOfEnrolled` |
| US-30 | Đánh dấu kết quả cũ (`isStale`) | MR | |

### E08 Báo cáo và truy vết (F07)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-31 | Báo cáo CLO theo lớp | **C** | Có phạm vi, quy tắc, phiên bản, `limitations` |
| US-32 | Báo cáo PI theo học phần/khóa, PLO theo CTĐT | **C** | Ghi rõ "chưa đủ dữ liệu để kết luận toàn chương trình" khi áp dụng |
| US-33 | Báo cáo độ đầy đủ dữ liệu | **C** | |
| US-34 | Truy vết kết quả → điểm → lô nhập → dòng tệp gốc | **C** | ≤ 3 thao tác |
| US-35 | Xuất báo cáo | MR | Nếu làm: chỉ Excel. PDF: K |
| US-36 | Dashboard tổng quan | MR | Đọc từ `aggregate_results`, không tính lại từ `scores` |

### E09 Cải tiến (F08)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-37 | Tạo vấn đề và hành động cải tiến | **C** | Gắn CLO/PI/PLO, `rootCause`, `actionPlan`, `expectedTarget`, người phụ trách, hạn, mức ưu tiên (kể cả `CRITICAL`) |
| US-38 | Theo dõi trạng thái (OPEN → IN_PROGRESS → DONE → VERIFIED) | **C** | Lưu `history[]` |
| NEW-02 | Đánh giá hiệu quả cải tiến ở chu kỳ đo kế tiếp (`evaluation`: `newPassRate`, `isEffective`) | MR | Story mới từ v0.5; nối với một lượt tính sau đó |

### E10 Hệ thống

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-39 | Health check, log tập trung, cảnh báo | **C** | `/actuator/health` |
| US-40 | Xử lý ngoại lệ thống nhất, UI có trạng thái rỗng/lỗi/hết thời gian | **C** | |

### Story mới khác do CSDL v0.5

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| NEW-03 | Vai trò STUDENT: sinh viên đăng nhập xem `student_results` của chính mình | MR | Cần nối `students.userId`; chỉ đọc |
| NEW-04 | Phương pháp mở rộng của engine: `pi.method = FROM_CLOS`, `aggregationMethod` còn lại (`MAX`, `LATEST`, `ACCUMULATED_EVIDENCE`), các phương pháp PLO còn lại | MR | CSDL đã hỗ trợ giá trị enum; chỉ cài đặt phần ngoài cam kết nếu còn thời gian |

---

## 4. Không làm (K)

| Hạng mục | Lý do |
|---|---|
| Tổ chức thi, sinh đề; lưu nội dung đề thi / ngân hàng câu hỏi (US-13, US-14, US-17) | Bảo mật đề thi tuyệt đối (ADR 0013): đã xóa hoàn toàn `question_banks`, `question_categories`, `questions` khỏi CSDL |
| Xuất phôi in đề thi (`assessments.exportedFiles`) & Quy trình Trưởng bộ môn duyệt ma trận (`approval`) | Đã xóa khỏi CSDL theo ADR 0013; giảng viên lớp học phần tự khóa ma trận (`DRAFT → LOCKED`) |
| Đo kết quả bài kiểm tra tổng (`totalPoints`, `TOTAL_ONLY`) và xét điểm qua môn học phần (`level = COURSE`, `measurement_rules.course`) | Hệ thống chỉ đo lường chuẩn đầu ra (`CLO`, `PI`, `PLO`), không phải hệ thống quản lý điểm học vụ (ADR 0013) |
| Tiêu chí rubric (US-16) và chia điểm CLO theo trọng số (`weight`) | Giảng viên nhập trực tiếp điểm tối đa (`maxScore`) và điểm đạt được (`rawScore`) cho từng CLO của từng câu hỏi (ADR 0013) |
| Lưu bài làm của sinh viên | Chỉ lưu điểm CLO từng câu hỏi và tham chiếu tệp điểm gốc |
| Machine Learning (`ml_experiments`) | Ngoài phạm vi đồ án; đã loại bỏ collection `ml_experiments` khỏi `database_import.js` (TD-06) |
| Quy đổi I/R/M thành hệ số (`pi.useIrmAsWeight`, `irmWeights`) | Trái bất biến "I/R/M ≠ trọng số"; đã loại bỏ khỏi CSDL (ADR 0012) |
| Collection bộ môn (`departments`) | Đã chốt loại bỏ (Q17): không tạo collection này |

---

## 5. Thứ tự cắt khi chậm tiến độ

Chỉ cắt trong cột MR trước; story C chỉ hạ xuống khi có ADR và GVHD đồng ý (bản cam kết mục 10).

| Cổng | Nếu chậm | Cắt |
|---|---|---|
| K2 (cuối T9) | P2 chưa xong | Bỏ US-08, US-13, US-14 (đã là MR); không làm thêm MR nào của P3 |
| K3 (cuối T10) | Khoa chưa xác nhận quy tắc | Chạy chế độ không chính thức, ghi rõ giới hạn; không cắt story |
| K4 (cuối T13) | Engine chưa khớp bộ kiểm chứng | Bỏ US-36, US-35, US-30, NEW-02 |
| K5 (cuối T15) | Chưa đủ coverage lõi ≥ 70% | Dừng thêm tính năng, chỉ sửa lỗi; không cắt kiểm thử |

Thứ tự ưu tiên làm: nền tảng và staging (T5–T7) → US-01–03 → CTĐT/CLO (E02, E03) → NEW-01 và E04 → E05 → E06 → E07
→ E08 → E09. Mỗi giai đoạn là lát cắt dọc (UI + API + test + tài liệu chạy trên staging).

---

## 6. Phân quyền theo vai trò (Chuẩn hóa 4 vai trò — ADR 0014)

Vai trò là bên *chủ trì* thao tác; backend kiểm tra theo `roleAssignments[].scope`. 4 vai trò chuẩn hóa hệ thống:

| Thao tác | ADMIN | PROGRAM_MANAGER | LECTURER | QA |
|---|---|---|---|---|
| Quản lý người dùng | ✔ | | | |
| Import CTĐT & Đề cương học phần (Phương án R1, ADR 0013 / ADR 0014); quản lý PLO/PI, ma trận I/R/M, đề cương, CLO, liên kết CLO–PI | ✔ | ✔ | | |
| Thẩm định chất lượng phiên bản CTĐT, PLO, PI, Đề cương (`verification`, US-08; ADR 0004 & ADR 0014) | | | | ✔ |
| Lập bài đánh giá lớp, thiết lập ma trận câu hỏi – CLO và **tự khóa ma trận** (`DRAFT → LOCKED`, ADR 0013 / ADR 0014) | | | ✔ (lớp mình) | |
| Nhập điểm trực tiếp từng CLO của từng câu hỏi, xác nhận lô (vắng/trống = 0.0) | | | ✔ (lớp mình) | |
| Chạy đo lường 4 cấp (`ASSESSMENT_CLO -> CLO -> PI -> PLO`), xem báo cáo | ✔ | ✔ | ✔ (lớp mình) | ✔ (toàn hệ thống) |
| Tạo / theo dõi hành động cải tiến | ✔ | ✔ | | ✔ |

---

## 7. Quy tắc nghiệp vụ ràng buộc mọi feature

Tóm tắt từ `GiaiThich_CoSoDuLieu.md` mục 2 và ADR 0013; vi phạm là lỗi nghiệp vụ.

1. Không hardcode công thức: toàn bộ ngưỡng đạt và trọng số gắn trực tiếp trên `plos`, `pis`, `clos`, `clo_pi_links`, `assessments.cloWeights` (đã xóa collection `measurement_rules`).
2. Sinh viên vắng thi hoặc bỏ trống câu hỏi lưu trực tiếp `rawScore = 0.0`. Mẫu số lớp học phần $N_{\text{section}}$ là toàn bộ sinh viên có ghi danh `status = ENROLLED`. Đo lường 4 cấp độ: `ASSESSMENT_CLO -> CLO -> PI -> PLO` (bỏ `COURSE`).
3. Bloom ≠ điểm; I/R/M ≠ trọng số; `capabilityLevelRaw` lưu nguyên, không diễn giải. Hệ thống chỉ đo chuẩn đầu ra (`CLO`, `PI`, `PLO`), không tính điểm bài kiểm tra hay điểm học phần (`COURSE`).
4. Tỷ lệ đạt luôn kèm mẫu số: ghi danh, đủ dữ liệu, đạt, chưa đạt, chưa đủ dữ liệu.
5. Một câu hỏi có thể đánh giá 1 hoặc nhiều CLO; mỗi CLO trong câu hỏi có `maxScore` riêng và được giảng viên nhập `rawScore` trực tiếp riêng (không chia điểm theo trọng số `weight`). Mức đạt CLO tính theo Phương án B1 (`POINTS_RATIO`) cho cả từng bài đánh giá và toàn học phần.
6. Mọi kết quả gắn `programVersionId`, `syllabusId`, `ruleId`; truy vết được về lô nhập và dòng tệp gốc.
7. Không bịa nguyên văn PLO/PI/CLO, ánh xạ CLO–PI, công thức hay ngưỡng: để trống, đánh dấu chờ xác nhận.
8. Angular chỉ gọi API; công thức đo lường chỉ chạy ở backend.

---

## 8. Việc còn treo trước khi ký (phụ thuộc người khác)

| # | Việc | Ai trả lời | Tình trạng (2026-11-06) | Ảnh hưởng tới feature |
|---|---|---|---|---|
| 1 | Chấp nhận NEW-01 (Trưởng bộ môn duyệt ma trận đề thi) và không làm xuất phôi in | GVHD (Q11) | Nhóm SV chốt giữ NEW-01 (ADR 0001); **GVHD đã ký xác nhận** (ngày: [ĐIỀN]) | NEW-01, vai trò DEPARTMENT_HEAD |
| 2 | Chọn học phần thử nghiệm; xác nhận điểm có theo từng câu hỏi (Q05) | Khoa, GVHD | **Đã chọn 2 học phần** (`APCM220307`, `ABDA433708`); điểm theo câu hỏi `ITEM` (Q05) | E06–E08 |
| 3 | Công thức, ngưỡng, mẫu số, xử lý thiếu dữ liệu (Q04, Q12, Q13) | Khoa | **Chưa có:** Khoa chưa cung cấp | US-26–29; nếu không kịp K3 thì chạy chế độ không chính thức |
| 4 | Phương pháp mặc định PI/PLO và chính sách nhập lại (Q19) | Khoa | **Chưa có:** Khoa chưa cung cấp | US-23, US-28 |
| 5 | Ánh xạ CLO–PI đã duyệt của các học phần thử nghiệm (Q14) | Khoa | **Đã có:** Lấy theo 2 đề cương chính thức đã duyệt (Q14) | US-12, US-15; KPI K7 |
| 6 | Ai được xác nhận quy tắc đo và CTĐT (Q08) | Khoa | **Đã chốt nội bộ:** `QA_OFFICER` xác nhận CTĐT/PLO/PI (ADR 0004); người xác nhận quy tắc đo chờ Khoa | Phân quyền mục 6 |
| 7 | Dùng mã sinh viên thật hay mã thay thế (Q09) | Khoa | **Đã chốt:** Dùng mã thật trên DB thật/staging; dữ liệu mẫu/test giữ mã giả (R9) | US-20 |
| 8 | Chấp nhận chạy chế độ không chính thức nếu K3 trượt | GVHD | Chờ GVHD xác nhận | Bản cam kết mục 3 |
| 9 | ADR cập nhật thiết kế: ADR 0001 (NEW-01), ADR 0010 (bộ môn/GV), ADR 0011 (PI suy ra từ CLO), ADR 0012 (bỏ quy đổi I/R/M) | Hai sinh viên | **Đã ban hành ADR 0001, 0010, 0011, 0012** | Giữ mã và tài liệu nhất quán |
| 10 | **Công thức còn thiếu** (cấp PLO; cấp học phần khi `requireAllCloPass = false`; `WEIGHTED_ITEMS`; gộp PI nhiều học phần; thiếu dữ liệu/ngưỡng trong công thức): 11 mục CT-01…CT-11 ở `plan/F06-do-luong.md` §9 | GVHD, Khoa (Q20–Q23) | **Chưa có:** PLO không thể báo cáo chính thức nếu chưa có | US-27, US-28, US-29, US-32 |

---

## 9. Chữ ký chốt

| Vai trò | Họ tên | Chữ ký | Ngày |
|---|---|---|---|
| Sinh viên 1 (IT) | Tín | | |
| Sinh viên 2 (IT) | Minh | | |
| Giảng viên hướng dẫn | [ĐIỀN] | | |
