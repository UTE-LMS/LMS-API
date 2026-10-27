# Tóm tắt dự án LOMS để hai sinh viên thảo luận

> **Tài liệu làm việc nội bộ, không phải hồ sơ nộp.** Tổng hợp từ `ke-hoach-xay-dung.md` (v0.3),
> `GiaiThich_CoSoDuLieu.md` (v0.5), `cam-ket-san-pham-metric.md` (dự thảo, chưa ký), `quy-trinh/phan-cong.md`
> (dự thảo) và `CLAUDE.md`. Mọi mục **[ĐỀ XUẤT]** / **[CHỜ XÁC NHẬN]** giữ nguyên ý nghĩa như trong kế hoạch:
> chưa được Khoa hoặc GVHD chốt, không dùng làm số liệu thật.
>
> Ngày tổng hợp: 2026-10-21 (kế hoạch ghi hiện tại là **tuần 5** của kỳ 95 ngày làm việc, khoảng 19 tuần).
> Phần "Câu hỏi để thảo luận" ở cuối là chỗ hai bạn cần quyết.

---

## 1. Dự án là gì, giải quyết bài toán nào

**LOMS** (Learning Outcomes Management and Analytics System): hệ thống quản lý, đo lường và phân tích chuẩn
đầu ra chương trình đào tạo (CTĐT).

**Bài toán.** Sau khi thi, điểm thường nằm trong bảng tính hoặc xuất từ LMS. Muốn biết sinh viên đạt chuẩn đầu ra
(CLO/PI/PLO) đến đâu thì phải quy điểm từng câu hỏi lên các chuẩn đó, và việc này thường làm thủ công, khó kiểm
chứng, khó truy vết.

**Hệ thống làm gì.** Nhận điểm đã có sau khi thi (theo từng câu hỏi hoặc tiêu chí) rồi đo mức đạt theo chuỗi:

```
Điểm câu hỏi/tiêu chí → CLO → PI → PLO → Báo cáo → Hành động cải tiến
```

Ba đặc điểm bắt buộc của kết quả:

1. **Có phiên bản**: gắn với phiên bản CTĐT, đề cương, quy tắc đo; báo cáo cũ đọc lại được khi quy tắc đổi.
2. **Truy vết được**: từ một kết quả đi ngược về điểm, lô nhập, dòng trong file gốc.
3. **Nói rõ mẫu số**: luôn kèm số ghi danh, số đủ dữ liệu, số chưa đủ dữ liệu (ví dụ lớp 45 ghi danh mà chỉ 40
   người đủ dữ liệu thì phải báo 5 người còn lại).

### 1.1 Trong phạm vi

- CTĐT Công nghệ thông tin (1 chương trình), **7 PLO, 17 PI**.
- **Ba học phần thử nghiệm**: **chưa chọn**. Hệ thống phải chạy với bất kỳ học phần nào; trong lúc chờ thì phát
  triển và kiểm thử bằng dữ liệu giả có nhãn "DỮ LIỆU THỬ NGHIỆM".
- 8 nhóm chức năng F01–F08 (mục 3).

### 1.2 Ngoài phạm vi (đã chốt, không làm)

| Không làm | Ghi chú |
|---|---|
| Tổ chức thi, sinh đề, duyệt **nội dung** đề thi, xuất phôi in đề | Ngân hàng câu hỏi chỉ là **danh mục câu hỏi của đề đã thi**. Từ CSDL v0.5 có *duyệt ma trận đề thi* (ánh xạ CLO–PI) bởi Trưởng bộ môn: **[CHỜ GVHD, Q11]**, xem NEW-01 |
| Lưu bài làm của sinh viên | Chỉ lưu điểm và tham chiếu về file điểm gốc |
| Machine Learning | Không viết mã, collection hay dependency ML (`ml_experiments` không dùng) |
| Quản lý "minh chứng" như danh mục dữ liệu | Chỉ lưu tệp điểm gốc và tham chiếu; nguồn của PLO/PI/CLO chỉ ghi ngắn trong khối `source` |
| Kết luận mức đạt của toàn CTĐT | Báo cáo ghi rõ "chưa đủ dữ liệu để kết luận toàn chương trình" khi áp dụng |
| Thay thế quy trình đo lường chính thức của Khoa | Hệ thống là công cụ hỗ trợ, quy tắc do Khoa xác nhận |

### 1.3 Công nghệ đã chốt

Java, **Spring Boot 4.1.0** (Java 25 theo `CLAUDE.md`), **MongoDB (phải là replica set** vì xác nhận nhập điểm cần
transaction nhiều document), **Angular 21 + TypeScript**. Kiến trúc: một ứng dụng backend chia module (modular
monolith); Angular **chỉ gọi API**, không truy cập MongoDB; công thức đo lường **chỉ chạy ở backend**.

---

## 2. Đầu ra của dự án (deliverables)

### 2.1 Sản phẩm phần mềm

| Chức năng | Nội dung chính | Story |
|---|---|---|
| **F01** Người dùng & phân quyền | Đăng nhập; 6 vai trò dùng trong cam kết (ADMIN, PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER, LECTURER, VIEWER; CSDL còn `STUDENT`); phạm vi theo CTĐT/học phần/lớp; backend kiểm tra quyền mọi API; audit log | US-01…04 |
| **F02** CTĐT, PLO, PI | Chương trình và phiên bản; 7 PLO, 17 PI nhận diện bằng **mã** (không bằng thứ tự dòng); ma trận học phần đóng góp PI (I/R/M); trạng thái xác nhận | US-05…08 |
| **F03** Học phần, CLO | Học phần, đề cương theo phiên bản, CLO thuộc một đề cương, liên kết CLO–PI (nhiều–nhiều, có xác nhận) | US-09…12 |
| **F04** Ngân hàng câu hỏi & bài đánh giá | Cây danh mục 3 cấp; câu hỏi ánh xạ nhiều cặp (CLO, PI, weight); bài đánh giá 2 tầng (khung theo đề cương, bản theo lớp); rubric mức 1–4; **duyệt ma trận đề thi** (DRAFT → SUBMITTED → APPROVED/REJECTED, khóa `items`, `coverageSummary`) | US-13…17, NEW-01 |
| (hỗ trợ F05) Học kỳ, lớp, sinh viên | Học kỳ + khóa sổ điểm; lớp học phần + giảng viên phụ trách; nhập danh sách lớp → sinh viên và ghi danh | US-18…20 |
| **F05** Nhập điểm | Tải CSV/Excel, ánh xạ cột, kiểm tra và báo lỗi theo dòng/cột, chọn cách hiểu ô trống, xác nhận trong một transaction hoặc rollback, sửa điểm có lý do | US-21…25 |
| **F06** Đo lường | Quy tắc cấu hình có phiên bản; tính CLO → PI → PLO; tổng hợp lớp/khóa/CTĐT có mẫu số; chống chạy trùng | US-26…30 |
| **F07** Báo cáo & truy vết | Báo cáo CLO/PI/PLO/độ đầy đủ dữ liệu; truy vết ≤ 3 thao tác; xuất Excel/PDF; dashboard | US-31…36 |
| **F08** Theo dõi cải tiến | Vấn đề và hành động cải tiến gắn CLO/PI/PLO (`rootCause`, `expectedTarget`); trạng thái OPEN → IN_PROGRESS → DONE → VERIFIED; đánh giá hiệu quả ở chu kỳ sau | US-37…38, NEW-02 |
| Vận hành | `/actuator/health`, log tập trung, cảnh báo, xử lý lỗi thống nhất, UI có trạng thái rỗng/lỗi/hết thời gian | US-39…40 |

Backlog: **44 story = 33 Must + 9 Should + 2 Could** (40 story gốc + 4 story mới từ CSDL v0.5: NEW-01 Must, NEW-02 và NEW-03 Should, NEW-04 Could). Đề xuất chốt phạm vi cam kết: `chot-feature-do-an.md`.

- Should: US-04, 08, 13, 25, 30, 35, 36, NEW-02, NEW-03.
- Could: US-17 (nhập câu hỏi từ Excel/Moodle), NEW-04 (phương pháp mở rộng của engine).

### 2.2 Hạ tầng và chất lượng

- **CI/CD ≥ 6 chặng**: build → lint/phân tích tĩnh → test + cổng coverage → quét bảo mật/phụ thuộc (gitleaks,
  OWASP Dependency-Check / npm audit, Trivy) → đóng gói Docker + gắn phiên bản → tự triển khai staging.
  Sau đó health check; rollback bằng image phiên bản trước. Không `continue-on-error`, không `|| true`.
- **Kiểm thử ≥ 4 tầng**: unit (JUnit 5, Mockito; Vitest ở UI), integration (Spring Boot Test + Testcontainers
  MongoDB replica set), API (MockMvc/RestAssured), E2E (Playwright), phi chức năng (k6/JMeter, quét bảo mật).
- **Môi trường**: staging công khai (tốt nhất có production với người dùng thật); nơi đặt staging chưa chốt.

### 2.3 Hồ sơ và minh chứng (theo Rubric)

| Hồ sơ | Vị trí / trạng thái |
|---|---|
| Bản cam kết sản phẩm + metric (Bước 3) | `doc/cam-ket-san-pham-metric.md`: **khung đã có, chưa ký**; là **cổng K1** |
| Bảng phân công (mức khung) | `doc/quy-trinh/phan-cong.md`: khung đã có, chưa điền |
| Coding convention, nợ kỹ thuật, checklist rubric | `doc/coding-convention.md`, `doc/tech-debt.md`, `doc/rubric-checklist.md` (đã có file) |
| SRS/AC, SDD, ≥ 4 loại sơ đồ khớp mã, ADR | `doc/srs/`, `doc/sdd/`, `doc/adr/`: **chưa có** |
| Báo cáo tiến độ ≥ 1 lần/tuần | `doc/bao-cao-tien-do/`: **chưa có** |
| AI Usage Log | `logsAI/ai-usage-log.md` (mỗi repo một bản, gộp khi nộp) |
| Kế hoạch test, test case, coverage, defect | `doc/test-plan.md` (chưa có), CI artifacts, issue tracker |
| Khảo sát (≥ 3 bên liên quan hoặc ≥ 30 phiếu), so sánh ≥ 3 giải pháp | chưa có |
| Báo cáo thực nghiệm người dùng (≥ 10 người thật, SUS ≥ 80, hoàn thành ≥ 90%, ≥ 1 vòng cải tiến đo lại) | chưa có |
| Luận văn, báo cáo trùng lặp (≤ 20%), slide, bộ câu hỏi vấn đáp | chưa có |

---

## 3. Mô hình dữ liệu (MongoDB, 30 collection: 29 chính + 1 tùy chọn không dùng)

Thiết kế CSDL **v0.5** (cập nhật 2026-10-23 theo `PLO_DB_UPDATE.md`) trong `GiaiThich_CoSoDuLieu.md` là **nguồn ưu tiên** khi các tài liệu lệch nhau. Tóm tắt thay đổi so với v0.4: `GiaiThich_CoSoDuLieu.md` §0.

| Nhóm | Collection | Vai trò |
|---|---|---|
| A. Người dùng | `users` | Tài khoản (mật khẩu chỉ lưu băm), `roleAssignments[]` = vai trò (7 giá trị, gồm `DEPARTMENT_HEAD`, `STUDENT`) + phạm vi `{programIds, courseIds, sectionIds}` |
| B. CTĐT | `programs`, `program_versions`, `plos`, `pis`, `courses`, `program_courses`, `course_pi_contributions` | CTĐT + phiên bản, 7 PLO, 17 PI, học phần, vị trí học phần trong CTĐT, ma trận I/R/M |
| C. Học phần & CLO | `syllabi`, `clos`, `clo_pi_links` | Đề cương theo phiên bản, CLO (duy nhất trong một đề cương), liên kết CLO–PI |
| D. Câu hỏi & bài đánh giá | `question_banks`, `question_categories`, `questions`, `assessments` | Danh mục câu hỏi đã thi; bài đánh giá khung (`SYLLABUS_TEMPLATE`) và bản lớp (`SECTION`, chứa `items[]`); bản lớp có trạng thái duyệt `DRAFT/SUBMITTED/APPROVED/REJECTED/SCORE_ENTERED/LOCKED` và `isLocked` (khóa `items` từ `APPROVED`) |
| E. Học kỳ, lớp, sinh viên | `terms`, `course_sections`, `students`, `enrollments` | Học kỳ (OPEN/SCORE_LOCKED/CLOSED), lớp, sinh viên (`studentRef`), ghi danh = **mẫu số** |
| F. Nhập điểm | `import_batches`, `import_row_errors`, `scores` | Lô nhập, lỗi từng dòng, điểm theo (sinh viên × câu hỏi/tiêu chí) |
| G. Đo lường | `measurement_rules`, `calculation_runs`, `student_results`, `aggregate_results` | Quy tắc có phiên bản, lượt tính, kết quả cá nhân (CLO/COURSE/PI/PLO), kết quả tổng hợp |
| H. Báo cáo, cải tiến | `reports`, `improvement_actions`, `audit_logs` | Báo cáo kèm `limitations`, hành động cải tiến, nhật ký thao tác |

Sơ đồ quan hệ chính:

```
programs ─< program_versions ─┬─< plos ─< pis
                              ├─< program_courses >─ courses
                              └─< course_pi_contributions (I/R/M)

courses ─< syllabi ─┬─< clos ─< clo_pi_links >─ pis
                    ├─< question_banks ─┬─< question_categories (cây 3 cấp)
                    │                   └─< questions (mỗi câu đo nhiều cặp CLO–PI)
                    └─< assessments (SYLLABUS_TEMPLATE | SECTION có items[])

terms ─< course_sections ─┬─< import_batches ─< import_row_errors
                          └─< scores        ; course_sections ─< enrollments >─ students

measurement_rules ─< calculation_runs ─┬─< student_results
                                       └─< aggregate_results ─< reports ─< improvement_actions
```

**Module backend** (package theo chức năng, mỗi module `controller → service → repository` + `dto`, `document`):
`auth`, `program`, `course`, `assessment`, `academic`, `scoreimport`, `measurement`, `report`, `improvement`,
`audit`.

MongoDB không có khóa ngoại, nên **tầng service phải tự kiểm tra tham chiếu** (cloId tồn tại, câu hỏi và danh mục
cùng ngân hàng, cặp CLO–PI nằm trong `clo_pi_links` của chính CLO đó…) trước khi ghi. Script `database_import.js`
dựng collection kèm `$jsonSchema` và seed tối thiểu (7 PLO, 17 PI, 1 học phần OOP, 4 dòng ma trận I/R/M, một khung
quy tắc `DRAFT` để trống ngưỡng). Image Docker `lms-mongo` (`docker/mongo/`) chạy replica set và nạp script này (xem
README).

**Chuỗi nghiệp vụ v0.5 có thêm bước duyệt:** … → lập bài đánh giá bản lớp (`DRAFT`) → gửi duyệt (`SUBMITTED`) →
Trưởng bộ môn duyệt (`APPROVED`, khóa `items`) hoặc từ chối (`REJECTED`) → mới được nhập điểm (`SCORE_ENTERED`). Ba chỗ
lệch với phạm vi/nguyên tắc cũ đang chờ GVHD/Khoa: duyệt ma trận đề thi và `exportedFiles` (Q11), `departmentId` chưa
có collection bộ môn (Q17), `irmWeights` quy đổi I/R/M (Q18; engine luôn coi `useIrmAsWeight = false`).

---

## 4. Các luật nghiệp vụ bắt buộc (sai là lỗi nghiệp vụ)

### 4.1 Cách đo

- **Không hardcode** công thức, trọng số, ngưỡng, mẫu số. Đọc từ `measurement_rules`; chỉ quy tắc `CONFIRMED`
  mới cho kết luận chính thức (`calculation_runs.isOfficial`).
- Các con số trong tài liệu (40/60, ngưỡng 60%, 72%, tỷ trọng CLO 20/40/40, 45 ghi danh/40 đủ dữ liệu) **chỉ là
  ví dụ minh họa**; không đưa vào seed hay cấu hình mặc định.
- Một câu hỏi/tiêu chí có thể đo **nhiều cặp (CLO, PI)**; `weight` các cặp trong một câu có tổng bằng 1. Ví dụ:
  câu 10 điểm, ba cặp weight 0,3/0,5/0,2 → phần điểm tối đa 3/5/2. Cách chia này còn chờ Khoa (Q15).
- PI gán trực tiếp cho câu hỏi nhưng **chỉ chọn trong các PI đã liên kết với CLO của câu đó** (`clo_pi_links`);
  chạy tính chính thức yêu cầu liên kết ở trạng thái `VERIFIED`.
- Tính PLO từ PI theo quy tắc cấu hình, **không** lấy trung bình mức năng lực của PI để suy ra mức PLO.

### 4.2 Các khái niệm không được đồng nhất

| Không đồng nhất | Hệ quả khi code |
|---|---|
| Mức Bloom ≠ điểm số | Lưu riêng `bloomLevel`, không có trường quy đổi sang điểm |
| I/R/M ≠ trọng số | Không map I=1, R=2, M=3 |
| `capabilityLevelRaw` ≠ Bloom | Lưu nguyên giá trị nguồn, không diễn giải (Q03) |
| Mức rubric (1–4) ≠ Bloom | Hai trường riêng |
| Điểm học phần ≠ mức đạt CLO | CLO tính từ điểm câu hỏi/tiêu chí gắn CLO, không từ điểm tổng kết |
| CLO cá nhân ≠ tỷ lệ đạt lớp ≠ mục tiêu cấp lớp | Ba đại lượng riêng |
| Tỷ lệ đạt luôn có mẫu số | Luôn trả `counts` (ghi danh, đủ dữ liệu, đạt, chưa đạt, chưa đủ dữ liệu) và `passRateOfEnrolled` |

### 4.3 Dữ liệu điểm

`scores.status` gồm 6 trạng thái:

| Trạng thái | Ý nghĩa | `rawScore` |
|---|---|---|
| `SCORED` | Đã chấm | có điểm |
| `BLANK` | Có dự thi, bỏ trống câu này | 0 (có ghi nhận) |
| `ABSENT` | Vắng cả bài | `null` |
| `NOT_GRADED` | Chưa chấm | `null` |
| `EXEMPT` | Được miễn | `null` |
| `MISSING` | Thiếu dữ liệu nhập | `null` |

- **Thiếu dữ liệu không mặc định 0.** Ô trống khi nhập là `BLANK` hay `MISSING` do người nhập chọn cho từng lô;
  hệ thống không tự đoán.
- `rawScore ≤ maxScore`; không trùng `(sectionId, assessmentId, itemId, studentId)`.
- CLO nhận diện bằng `_id` (mã `CLO1` chỉ duy nhất trong một đề cương); PLO/PI nhận diện bằng **mã**, `displayOrder`
  là trường riêng (tài liệu xếp PLO1, 6, 7, 4, 5, 2, 3).
- `contentOriginal` của PLO/PI/CLO để trống tới khi đối chiếu bản gốc; `verification.status` mặc định
  `UNVERIFIED`/`NEEDS_CLARIFICATION`.
- Sửa điểm, xác nhận nhập, chạy tính, xác nhận quy tắc, đăng nhập đều ghi `audit_logs` (trước/sau, lý do).
- Chống xung đột: `scores.revision` (khóa lạc quan), `calculation_runs.lockKey`, `isStale` khi điểm đổi sau lần tính.
- Riêng tư: dùng `studentRef` (mã thay thế) cho tới khi Khoa cho phép mã thật (Q09); test/seed chỉ dùng dữ liệu giả.

### 4.4 Luồng minh họa end-to-end

1. Khai báo câu hỏi/tiêu chí và ánh xạ CLO–PI → lập bài đánh giá bản `SECTION` (sao chép ánh xạ tại thời điểm thi)
   → gửi Trưởng bộ môn duyệt ma trận (`APPROVED` thì khóa `items`) **[CHỜ GVHD, Q11]**.
2. Giảng viên nhập file điểm → `import_batches` (+ `import_row_errors`) → xác nhận → ghi `scores`.
3. Chạy tính theo `measurement_rules` → `student_results` (CLO, PI, PLO) với `inputs[]` ghi đủ đầu vào.
4. Tổng hợp lớp → `aggregate_results` (kèm `counts`).
5. Sinh `reports` (có `limitations`) → phát hiện vấn đề → `improvement_actions`.
6. Truy vết ngược: PLO → `inputs` → `scores` → `import_batches` + dòng trong file gốc.

---

## 5. Backlog chi tiết theo epic

Ưu tiên: **M** = Must, **S** = Should, **C** = Could. "Chủ trì" là **[ĐỀ XUẤT]** theo `Misa.md` §4; hai bạn hỗ
trợ chéo tự do. Mỗi story phải có AC dạng Given/When/Then **trước khi code**.

| Epic | Story (ưu tiên, chủ trì) | Trọng tâm AC |
|---|---|---|
| **E01** Người dùng & quyền | US-01 đăng nhập/đăng xuất (M, IT) · US-02 tạo người dùng, vai trò, phạm vi (M, IT+IBS) · US-03 kiểm tra quyền theo phạm vi ở backend (M, IT) · US-04 nhật ký thao tác (S, IT) | Sai mật khẩu bị từ chối; tài khoản `LOCKED` không vào được; giảng viên chỉ thấy/nhập điểm lớp mình, trái phạm vi → 403 |
| **E02** CTĐT/PLO/PI | US-05 chương trình + phiên bản (M, IBS+IT) · US-06 7 PLO/17 PI theo mã (M, IBS) · US-07 ma trận I/R/M (M, IBS) · US-08 quy trình xác nhận nội dung (S, IBS) | `(programId, version)` duy nhất; mã sai định dạng bị từ chối; ô trống = không có bản ghi |
| **E03** Học phần/CLO | US-09 học phần (M) · US-10 đề cương theo phiên bản (M) · US-11 CLO (M) · US-12 liên kết CLO–PI + xác nhận (M), chủ trì IBS | `code` CLO duy nhất trong đề cương; Bloom 1–6; chỉ liên kết `VERIFIED` được dùng tính chính thức |
| **E04** Câu hỏi/bài đánh giá | US-13 ngân hàng + cây 3 cấp (S) · US-14 câu hỏi nhiều cặp (M) · US-15 bài đánh giá khung/lớp (M) · US-16 rubric 1–4 (M) · US-17 nhập câu hỏi Excel/Moodle (C, IT) · **NEW-01 duyệt ma trận đề thi (M, IBS+IT, chờ GVHD)** | Cấp 1 không cha, không vượt cấp 3; tổng weight = 1 với câu ACTIVE; PI phải thuộc `clo_pi_links` của CLO; bản có điểm bị khóa `items` |
| **E05** Học kỳ/lớp/SV | US-18 học kỳ + khóa sổ (M) · US-19 lớp học phần (M) · US-20 nhập danh sách lớp (M), chủ trì IT | Học kỳ khóa thì không nhập/sửa điểm; ghi danh là mẫu số (Q09) |
| **E06** Nhập điểm | US-21 tải tệp + ánh xạ cột (M) · US-22 kiểm tra, báo lỗi dòng/cột (M) · US-23 chọn BLANK/MISSING (M, IBS+IT) · US-24 xác nhận một transaction / rollback (M) · US-25 sửa điểm có lý do (S) | Checksum phát hiện nhập trùng; lỗi giữa chừng không để điểm nửa vời; audit trước/sau + `revision` |
| **E07** Đo lường | US-26 quy tắc có phiên bản (M) · US-27 tính CLO từng SV (M) · US-28 tính PI/PLO (M) · US-29 tổng hợp có mẫu số (M) · US-30 chống chạy trùng + `isStale` (S) | Kết quả khớp tính tay độc lập; thiếu dữ liệu ≠ 0; `counts` đủ 5 số; không suy PLO bằng trung bình PI |
| **E08** Báo cáo/truy vết | US-31 báo cáo CLO theo lớp (M) · US-32 báo cáo PI/PLO (M) · US-33 báo cáo độ đầy đủ (M) · US-34 truy vết (M) · US-35 xuất Excel/PDF (S) · US-36 dashboard (S) | Có phạm vi, quy tắc, phiên bản, `limitations`; truy vết ≤ 3 thao tác **[ĐỀ XUẤT]** |
| **E09** Cải tiến | US-37 tạo vấn đề/hành động (M) · US-38 theo dõi trạng thái (M) · NEW-02 đánh giá hiệu quả ở chu kỳ sau (S) | Gắn CLO/PI/PLO, người phụ trách, hạn; lưu `history[]` |
| **E10** Hệ thống | US-39 health check, log, cảnh báo (M) · US-40 xử lý ngoại lệ thống nhất (M), chủ trì IT | `/actuator/health`; mã lỗi nhất quán; UI đủ trạng thái |

---

## 6. Lộ trình và cổng kiểm soát

| Giai đoạn | Tuần | Mục tiêu | Đầu ra chính |
|---|---|---|---|
| **P0** Nền tảng | T5–T6 | Repo, môi trường, CI sườn, quy ước | MongoDB replica set; sửa `.gitignore` + gỡ track `application.properties`; quét secret; coding convention; bảo vệ nhánh `main`; README; CI build + test; ADR đầu tiên; nạp `database_import.js` |
| **P1** Khảo sát | T5–T8 (song song) | Hiểu nhu cầu thật | Phỏng vấn ≥ 3 bên liên quan; so sánh ≥ 3 giải pháp; chân dung người dùng; trả lời Q01–Q19 |
| **P2** Danh mục lõi | T6–T9 | E01, E02, E03 | Đăng nhập + phân quyền; CTĐT/PLO/PI/ma trận; học phần/đề cương/CLO; **staging đầu tiên (mục tiêu T7)** |
| **K1** | mục tiêu hết T7, hạn T10 | Chốt bản cam kết có chữ ký | `cam-ket-san-pham-metric.md` |
| **P3** Đánh giá & nhập điểm | T8–T11 | E04, E05, E06 | Ngân hàng câu hỏi, bài đánh giá, lớp, nhập tệp, báo lỗi, xác nhận/rollback |
| **P4** Đo lường | T10–T13 | E07 | Rule engine cấu hình; CLO → PI → PLO; tổng hợp có mẫu số; `inputs[]` |
| **P5** Báo cáo & cải tiến | T12–T15 | E08, E09 | Báo cáo, drill-down, xuất tệp, dashboard, theo dõi cải tiến |
| **P6** Củng cố chất lượng | T14–T16 | Phi chức năng | E2E, hiệu năng, quét phụ thuộc, sửa lỗi Critical, rà sơ đồ khớp mã |
| **P7** Thực nghiệm người dùng | T15–T17 | TC2.7 | ≥ 10 người dùng thật, SUS, ≥ 1 vòng cải tiến đo lại |
| **P8** Luận văn & bảo vệ | T16–T19 | TC3–TC5 | Luận văn, kiểm tra trùng lặp, slide, diễn tập demo, bộ câu hỏi vấn đáp |

**Đóng băng tính năng: cuối T15.** Nguyên tắc: lát cắt dọc xuyên suốt (mỗi giai đoạn có UI + API + test + tài liệu
chạy được trên staging), không làm hết backend rồi mới làm UI.

| Cổng | Khi nào | Điều kiện | Nếu không đạt |
|---|---|---|---|
| **K1** | Mục tiêu T7, hạn T10 | Bản cam kết Bước 3 có chữ ký; nên đã chọn ba học phần thử nghiệm | **Dừng đề tài (G1), không có ngoại lệ** |
| **K2** | Cuối T9 | CTĐT/PLO/PI nhập thật; staging chạy; CI xanh; ≥ 3 lần triển khai tự động | Giảm Should của P3 |
| **K3** | Cuối T10 | Khoa đã xác nhận (hoặc từ chối) quy tắc đo; có điểm thật hoặc quyết định dùng dữ liệu thử nghiệm | Chạy chế độ không chính thức, ghi rõ giới hạn |
| **K4** | Cuối T13 | Tính CLO→PI→PLO đúng với bộ kiểm chứng | Cắt dashboard/xuất PDF |
| **K5** | Cuối T15 | Đóng băng; coverage lõi ≥ 70%; 0 defect Critical/Blocker | Hoãn thực nghiệm, chỉ sửa lỗi |
| **K6** | Cuối T17 | Thực nghiệm xong, có số liệu thật | Bắt buộc có (G7) |

---

## 7. Mục tiêu chất lượng (nhắm Mức 5 của Rubric)

| Chỉ số | Mục tiêu |
|---|---|
| Lint / Blocker-Critical / trùng lặp mã | 0 / 0 / ≤ 3% |
| Tuần có commit / thay đổi qua PR có review / secret lộ | ≥ 90% / ≥ 90% / 0 |
| Số tầng kiểm thử / tự động hóa | ≥ 4 / ≥ 70% |
| Coverage mô-đun lõi (đo lường, nhập điểm, kiểm tra dữ liệu, phân quyền) | ≥ 70% |
| Test pass ở lần chạy CI cuối / defect Critical còn tồn | 100% / 0 |
| Chặng pipeline / build xanh / số lần deploy / commit → staging | ≥ 6 / ≥ 90% / ≥ 10 / ≤ 15 phút |
| Thực nghiệm: người dùng thật / hoàn thành tác vụ / SUS / vòng cải tiến | ≥ 10 / ≥ 90% / ≥ 80 / ≥ 1 |
| Báo cáo tiến độ | ≥ 1 lần/tuần |

**KPI nghiệp vụ [ĐỀ XUẤT]** trong bản cam kết (cần ≥ 5; con số do hai bạn và GVHD đặt):

| # | KPI | Ngưỡng đề xuất |
|---|---|---|
| K1 | Độ khớp CLO/PI/PLO của hệ thống với tính độc lập trên bộ kiểm chứng | 100% ca khớp trong dung sai [ĐIỀN] |
| K2 | Lỗi cài sẵn trong tệp thử được phát hiện đúng dòng/cột | 100% |
| K3 | Thời gian từ nhập tệp điểm đến có báo cáo CLO của một lớp | giảm ≥ [ĐIỀN]% so với cơ sở (**cần đo cơ sở**) |
| K4 | Số thao tác truy vết từ kết quả PLO về dòng điểm gốc | ≤ 3 |
| K5 | Tỷ lệ hoàn thành tác vụ chính của người dùng thật | ≥ 90% |
| K6 | Điểm SUS | ≥ 80 |
| K7 | Tỷ lệ câu hỏi/tiêu chí có ánh xạ CLO–PI đã xác nhận (học phần thử nghiệm) | ≥ [ĐIỀN]% |

**Yêu cầu phi chức năng [ĐỀ XUẤT]** (cần ≥ 5, chỉnh theo máy chủ thật):

| # | Yêu cầu | Ngưỡng |
|---|---|---|
| N1 | Nhập và kiểm tra tệp ~5.000 dòng | ≤ 10 giây |
| N2 | Tính CLO cho một lớp ~60 sinh viên | ≤ 5 giây |
| N3 | API đọc p95 với 50 người dùng đồng thời | ≤ 500 ms |
| N4 | Bảo mật: mật khẩu băm bcrypt/argon2; 100% endpoint kiểm tra quyền; 0 lỗ hổng mức cao trong quét phụ thuộc | đạt |
| N5 | Khả dụng staging trong giai đoạn thực nghiệm | ≥ 99% |
| N6 | Lỗi giữa chừng khi xác nhận nhập không để điểm nửa vời | 0 bản ghi nửa vời |

**Definition of Done cho một story:** (1) có AC viết xong trước khi code và người kia đã đọc; (2) qua lint, phân
tích tĩnh, có test ca thuận/âm/biên gắn AC; (3) PR được người kia review, CI xanh; (4) tài liệu/sơ đồ ảnh hưởng đã
cập nhật, có ADR nếu đổi thiết kế; (5) chạy được trên staging, không có secret hay dữ liệu thật; (6) AI Usage Log
đã ghi và người viết giải thích được từng đoạn.

**Bộ kiểm chứng phép tính độc lập.** Một bạn tính tay (hoặc bảng tính riêng) vài ca: lớp nhỏ, có sinh viên
vắng/chưa chấm/được miễn, câu đo nhiều cặp CLO–PI, ca biên sát ngưỡng. Bạn kia lập trình; hai kết quả phải khớp.

---

## 8. Cách hai sinh viên phối hợp

- Không phân công cứng nhắc; IBS/IT chỉ là bên **chủ trì**, hai người hỗ trợ chéo tự do.
- Vẫn phải giữ ba thứ nhẹ để đáp ứng Rubric (hội đồng hỏi riêng từng người):
  1. Bản khung `doc/quy-trinh/phan-cong.md` (một trang).
  2. Dấu vết đóng góp thật: mỗi người commit bằng danh tính Git của mình; PR do người này mở thì người kia review;
     AI Usage Log ghi đúng sinh viên.
  3. **Cả hai giải thích được mô-đun lõi** (đo lường, nhập điểm, phân quyền, truy vết). Mỗi tuần, người *không*
     viết một mô-đun giải thích lại cho người viết nghe (4 câu của Mục 6.2: làm gì, vì sao có trong đề tài, tham
     số/thư viện từ đâu, bỏ đi thì sao).
- Review chéo: IBS soát nghiệp vụ và AC, IT soát kỹ thuật. Không tự approve PR của mình.
- Phân vai theo `Misa.md` §4: **IBS** = nghiệp vụ, chuẩn hóa dữ liệu, thiết kế báo cáo, kiểm chứng kết quả;
  **IT** = thiết kế kỹ thuật, lập trình, tích hợp, kiểm thử kỹ thuật, triển khai.

Nhịp tuần: đầu tuần chọn story và viết/duyệt AC → trong tuần nhánh theo story → PR → review chéo → CI xanh → merge →
tự triển khai staging → cuối tuần báo cáo tiến độ, cập nhật `rubric-checklist.md`, ghi AI log, rà nợ kỹ thuật,
diễn tập vấn đáp. GVHD duyệt báo cáo 2 tuần/lần.

---

## 9. Câu hỏi còn mở với Khoa (Q01–Q19)

Nguồn: `GiaiThich_CoSoDuLieu.md` §8. Không chọn một đáp án rồi viết như thể Khoa đã duyệt.

| Mã | Nội dung | Tình trạng | Ảnh hưởng |
|---|---|---|---|
| Q01 | Bản CTĐT áp dụng cho khóa nào | Chưa hỏi | `program_versions.applicableCohorts` |
| Q02 | Khi nào có đề cương chính thức | Chưa hỏi | `syllabi`, `clos` |
| Q03 | Thang "trình độ năng lực" của PLO/PI | Chưa hỏi | Đang lưu nguyên giá trị, chưa diễn giải |
| Q04 | Công thức, ngưỡng, mẫu số, xử lý thiếu dữ liệu | Chưa hỏi | `measurement_rules` đang để trống |
| Q05 | Điểm theo câu hỏi hay điểm tổng | **Đã trả lời, chờ xác nhận:** theo từng câu hỏi | `dataGranularity = ITEM` |
| Q06, Q07 | Phiên bản công nghệ; nơi lưu mã, tệp, môi trường | Chưa hỏi | Môi trường triển khai, replica set |
| Q08 | Ai rà soát nghiệp vụ, kỹ thuật | Chưa hỏi | Quy trình `verification` |
| Q09 | Dùng mã sinh viên thật hay mã thay thế | Chưa hỏi | `students` |
| Q10 | PI gán trực tiếp hay suy ra qua CLO | **Đã trả lời, chờ xác nhận:** gán trực tiếp cho từng câu hỏi, trong các PI đã liên kết với CLO của câu đó | `outcomeMappings`, `pi.method = DIRECT_FROM_ITEMS` |
| Q11 | Có tổ chức thi không; có duyệt ma trận đề thi không | **Phạm vi đồ án: không tổ chức thi, không duyệt nội dung đề.** Duyệt *ma trận ánh xạ* bởi Trưởng bộ môn (v0.5, NEW-01): chờ GVHD xác nhận | `assessments.status/isLocked/approval`, `exportedFiles`, vai trò `DEPARTMENT_HEAD` |
| Q12 | Quy tắc "phải đạt từng CLO"; tỷ trọng, ngưỡng từng CLO | Chưa hỏi | `course.requireAllCloPass`, `clos.measurement` |
| Q13 | Phân biệt bỏ trống / vắng / chưa chấm / thiếu; vắng thi có vào mẫu số không | Chưa hỏi | `scores.status`, `emptyCellAs`, `counts` |
| Q14 | Khi nào Khoa cung cấp ánh xạ CLO–PI đã duyệt của các học phần thử nghiệm | Chưa hỏi | `clo_pi_links` |
| Q15 | Khi một câu đo nhiều cặp CLO–PI, điểm chia thế nào | Chưa hỏi (thiết kế hiện chọn chia theo phần, tổng bằng 1) | `outcomeMappings.weight` |
| Q16 | Cấu trúc chương/chủ đề ngân hàng câu hỏi; nguồn nhập (Moodle hay Excel) | Chưa hỏi | `question_categories`, `import_batches` |
| Q17 | Bộ môn: có cần collection `departments` không, hay `courses.departmentId` chỉ là mã tham chiếu | Chưa hỏi (v0.5 thêm trường, chưa có collection) | `courses.departmentId`; phạm vi duyệt của `DEPARTMENT_HEAD` |
| Q18 | Có cho quy đổi I/R/M thành hệ số khi tổng hợp PI từ CLO không, và giá trị | Chưa hỏi. Mặc định **không** (`useIrmAsWeight = false`) | `pi.useIrmAsWeight`, `pi.irmWeights` |
| Q19 | Phương pháp mặc định: `pi.aggregationMethod`, `plo.method`, `reimportPolicy` | Chưa hỏi | `measurement_rules`, `import_batches` |

Đến nay (theo tài liệu) **chỉ 3 câu có câu trả lời tạm** (Q05, Q10, Q11) và đều chờ xác nhận chính thức. Q17–Q19 mới
từ CSDL v0.5.

---

## 10. Rủi ro chính

| # | Rủi ro | Mức | Phương án |
|---|---|---|---|
| R1 | Khoa chậm/không cung cấp đề cương, quy tắc đo, điểm theo câu hỏi; **ba học phần chưa chọn** | **Cao** | Rule engine cấu hình; dev bằng dữ liệu giả có nhãn; chọn học phần trước K1, quyết xong trước K3; nếu K3 vẫn thiếu thì chạy chế độ không chính thức, ghi rõ giới hạn. **Không bịa dữ liệu hay công thức** |
| R2 | Điểm chỉ có tổng, không có theo câu hỏi (Q05) | Cao | `TOTAL_ONLY` có trong thiết kế nhưng **không đo được CLO/PI**; bàn với GVHD đổi phạm vi bằng ADR trước K1 |
| R7 | Một người làm hết phần lõi, người kia không giải thích được | Cao | Review chéo bắt buộc; diễn tập vấn đáp hằng tuần |
| R9 | Lộ dữ liệu sinh viên hoặc secret | Cao | `studentRef`, dữ liệu giả, gitleaks trong CI, `.gitignore` đúng |
| R10 | **Chậm tiến độ**: T5 mà code mới là scaffold, P0–P2 nén còn 5 tuần | Cao | Thứ tự: nền tảng → K1 → danh mục lõi; ưu tiên Must; cắt Should rồi Could, **không cắt kiểm thử** |
| R3 | Không đủ ≥ 10 người dùng thật hoặc ≥ 3 bên liên quan | Trung bình | Lên danh sách, hẹn ngay từ T5; báo sớm GVHD |
| R4 | Staging không hỗ trợ replica set | Trung bình | Kiểm tra ở P0; nếu không có thì ADR đổi cách đảm bảo nhất quán |
| R5 | Phiên bản mới (Java 25, Spring Boot 4.1, Angular 21), công cụ chưa theo kịp | Trung bình | Dùng tài liệu đúng phiên bản; thử Sonar/JaCoCo sớm; cân nhắc Java LTS 21 sau khi hỏi Trung tâm CNPM |
| R6 | Phạm vi phình to (dashboard, PDF, đồng bộ Moodle) | Trung bình | Chỉ Must/Should; mọi đổi phạm vi ghi ADR và xin phép |
| R8 | Lịch sử Git thiếu các tuần đầu: nếu T1–T4 không có commit thì tối đa 15/19 ≈ 79% (Mức 4, không đạt Mức 5 cho chỉ số này) | Trung bình | Commit đều từ T5; **không sửa ngày commit, không tạo commit giả** |

---

## 11. Hiện trạng và việc cần làm ngay

**Hiện trạng** (theo kế hoạch, đo ngày 2026-10-20, và `git log` hiện tại của `LMS-API`):

- `LMS-API`: scaffold Spring Boot; `pom.xml` đã chuyển sang MongoDB. `git log` gần nhất có các commit: chuyển
  persistence sang MongoDB, thêm workflow CI build + test, thêm README/tài liệu thiết kế/log AI, thêm rule cho
  agent. Nhánh hiện tại: `chore/project-setup` (chưa merge vào `main`).
- `LMS-UI`: scaffold Angular 21, chưa có màn hình (theo kế hoạch).
- Chưa có (theo `CLAUDE.md` và kế hoạch): bản cam kết đã ký, test thực ngoài `contextLoads`, cấu hình MongoDB thật,
  báo cáo tiến độ, khảo sát, phân công đã điền.
- Còn treo: `websocket` và `mail` trong `pom.xml` chưa có lý do dùng (dependency phải giải thích được);
  MongoDB cục bộ đang standalone; `.gitignore` ghi sai đường dẫn `application.properties` và file đó đã bị track.

> Lưu ý: tôi chưa kiểm tra lại từng mục "hiện trạng" của `CLAUDE.md` sau các commit mới nhất, nên mục nào đã xử lý
> rồi thì hai bạn cập nhật lại.

**Việc cần làm ngay (T5–T6)** theo mục 11 của kế hoạch:

1. Mở PR cho tài liệu hiện có để người kia review.
2. Mỗi người cấu hình `git user.name/email` riêng.
3. Bật MongoDB replica set (dev).
4. Sửa `.gitignore`, gỡ track `application.properties`, chuyển cấu hình sang biến môi trường.
5. Quyết giữ/bỏ `websocket`, `mail`; SSR của UI; Java 25 hay LTS.
6. CI sườn, quét gitleaks, bảo vệ nhánh `main` (≥ 1 review).
7. Coding convention và README.
8. Chốt `phan-cong.md`.
9. Gửi lại danh mục MC01–MC09 và Q01–Q19 cho đầu mối Khoa, kèm hạn; đề xuất chọn ba học phần.
10. Lên danh sách ≥ 3 bên liên quan phỏng vấn và ≥ 10 người dùng thực nghiệm.
11. Đối chiếu CTĐT gốc để trích nguyên văn 7 PLO/17 PI.
12. Viết SRS/AC cho tập Must, nháp bản cam kết K1.
13. Bắt đầu code US-01…03 để có staging đầu tiên vào T7.

---

## 12. Câu hỏi để thảo luận giữa hai bạn

**Phạm vi và cam kết (quan trọng nhất, hạn K1)**

1. Cam kết bao nhiêu story? Kế hoạch tự cảnh báo 32 Must trong khoảng 10 tuần code là nặng cho hai người. Có nên
   chỉ cam kết Must + vài Should, và để US-35/36 (xuất file, dashboard) làm mục tiêu mở rộng không?
2. F08 (theo dõi cải tiến, US-37/38) hiện xếp Must. Có giữ trong cam kết không, hay hạ xuống?
3. Chọn **ba học phần thử nghiệm** nào, và có điểm theo từng câu hỏi hay chỉ có điểm tổng (R2)? Nếu chỉ có điểm
   tổng thì không đo được CLO/PI nên phải đổi phạm vi bằng ADR trước K1.
4. Nếu đến K3 Khoa vẫn chưa xác nhận quy tắc đo, hai bạn và GVHD có chấp nhận chạy chế độ không chính thức không?
   (Bản cam kết có ô cần GVHD đồng ý.)

**Kỹ thuật**

5. Xác thực: JWT hay session? (cần ADR)
6. Đọc tệp điểm: Apache POI (Excel) + thư viện CSV nào? Lưu tệp gốc ở đâu?
7. Migrate/seed CSDL: dùng `database_import.js` hay Mongock/Spring?
8. Java 25 hay LTS 21? Giữ hay bỏ `websocket`, `mail`? SSR của Angular giữ hay bỏ? Staging đặt ở đâu?

**Phối hợp và tiến độ**

9. Ai chủ trì epic nào (điền cột "Chốt" trong `phan-cong.md`)?
10. Ai là IBS, ai là IT, và `git user.name` của mỗi người (hiện máy này đang cấu hình `TinMH`)?
11. Chọn ngày diễn tập vấn đáp hằng tuần và người viết báo cáo tiến độ luân phiên.

**Khảo sát và thực nghiệm**

12. Ai là ≥ 3 bên liên quan để phỏng vấn (nên có giảng viên, cán bộ phụ trách CTĐT/đảm bảo chất lượng)? Ai là
    ≥ 10 người dùng thật cho thực nghiệm (không chỉ bạn cùng lớp)?
13. Quy trình hiện tại của Khoa đo CLO/PI mất bao lâu? Cần đo cơ sở này để KPI K3 có nghĩa.

**Khác biệt nhỏ giữa các tài liệu cần thống nhất**

14. Kế hoạch dùng "K1–K6" cho các **cổng**, bản cam kết dùng "K1–K7" cho các **KPI**. Nên đổi tên một bên (ví dụ
    KPI thành "KPI-1…KPI-7") để khỏi nhầm.
15. Kế hoạch ghi 6 KPI đề xuất, bản cam kết ghi 7 (thêm K7: tỷ lệ ánh xạ CLO–PI đã xác nhận); rubric chỉ cần ≥ 5.
    Chọn bộ KPI nào cho bản ký?
