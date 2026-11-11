# ADR 0013 — Điểm nhập trực tiếp theo từng CLO của từng câu; giảng viên tự khóa ma trận đề; bỏ ngân hàng câu hỏi

- **Trạng thái:** ĐÃ CHẤP NHẬN (ACCEPTED). **GVHD đã xác nhận** các điểm mâu thuẫn với thiết kế v0.5 (mục 2) theo
  báo cáo của sinh viên; **ngày ký và người ký: [ĐIỀN]**.
- **Ngày:** 2026-11-11
- **Người soạn:** Claude Code theo yêu cầu của sinh viên TinMH (IT); sinh viên rà soát và chốt: [ĐIỀN]
- **Thay thế:** ADR 0001 (duyệt ma trận đề thi, phương án A); phần "chia điểm theo `weight`" của ADR 0011 (Q15);
  Q16 (nhập câu hỏi từ Excel, US-17)
- **Nguồn:** `doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md` (ngày lập 2026-11-10)
- **Liên quan:** ADR 0010 (vai trò `DEPARTMENT_HEAD` vẫn còn, chỉ không duyệt ma trận); ADR 0011 (PI suy ra từ CLO,
  giữ nguyên); ADR 0012; `GiaiThich_CoSoDuLieu.md` v0.6; `database_import.js`; `plan/F04-bai-danh-gia.md`, `plan/F05b-nhap-diem.md`

## 1. Bối cảnh

Đặc tả nghiệp vụ mới (2026-11-10) đổi ba điểm so với thiết kế v0.5:

1. Giảng viên **tự khóa ma trận đề** rồi nhập điểm, "không cần chờ bước phê duyệt trung gian" (v0.5: Trưởng bộ môn duyệt, ADR 0001).
2. Hệ thống **không lưu nội dung câu hỏi, đề bài, đáp án**; câu hỏi chỉ có nhãn (`Câu 1`…) và điểm tối đa theo từng CLO
   (v0.5: có ngân hàng câu hỏi gồm `question_banks`, `question_categories`, `questions`, trong đó `questions.content`).
3. Giảng viên **nhập trực tiếp điểm đạt của từng CLO tại từng câu**; câu đo nhiều CLO có điểm tối đa riêng cho từng CLO
   (v0.5: điểm của câu chia cho CLO theo `weight`, tổng bằng 1, chờ Q15). Hệ thống cũng không tính hay lưu tổng điểm bài.

Rule 9 của `CLAUDE.md` yêu cầu mọi đổi thiết kế có ADR và lý do.

## 2. Quyết định

### 2.1. Bỏ duyệt ma trận; giảng viên tự khóa (thay ADR 0001)

- Luồng trạng thái của `assessments` (bản `SECTION`): `DRAFT → MATRIX_LOCKED → SCORE_ENTERED → LOCKED`.
  Bỏ `SUBMITTED`, `APPROVED`, `REJECTED`. `APPROVED` đổi thành `MATRIX_LOCKED` (ma trận đã khóa, mở cổng nhập điểm).
- Giảng viên của lớp bấm khóa (`DRAFT → MATRIX_LOCKED`); cần ≥ 1 item. Chỉ nhập điểm khi `MATRIX_LOCKED` hoặc
  `SCORE_ENTERED`. `isLocked = true` khi `status ∈ {MATRIX_LOCKED, SCORE_ENTERED, LOCKED}` và cấm sửa `items[]`.
- Bỏ khối `approval` (`submittedBy`, `reviewedBy`, `history[]`…). Hành động ghi `audit_logs`: `ASSESSMENT_LOCK`
  (thay `ASSESSMENT_APPROVE`, `ASSESSMENT_REJECT`).
- Vai trò `DEPARTMENT_HEAD` **giữ nguyên** (ADR 0010: quản lý học phần, biên soạn và phê duyệt đề cương), chỉ không còn
  duyệt ma trận bài đánh giá của lớp.
- Khung `SYLLABUS_TEMPLATE` vẫn luôn `DRAFT`, không khóa (như v0.5, mã D4 của F04).

### 2.2. Không lưu nội dung câu hỏi; bỏ ngân hàng câu hỏi

- **Bỏ 3 collection** `question_banks`, `question_categories`, `questions`. CSDL còn **26 collection chính**.
- Một item trong `assessments.items[]` chỉ gồm: `_id`, `code` (nhãn như `Câu 1`), `orderIndex`, `kind`
  (`QUESTION`/`CRITERION`), `bloomLevel`, `outcomeMappings[]`, `externalRef`, `rubric`.
  **Bỏ** `title`, `topic` (chuỗi tự do, có thể bị dùng để dán đề), `questionId`, `questionVersion`.
- Bỏ story US-13, US-14, US-17 và Q16 (không còn nhập câu hỏi). Nhập **điểm** từ Excel/CSV (F05b) vẫn giữ.

### 2.3. Điểm theo từng CLO của từng câu (đóng Q15)

- `outcomeMappings[] = { cloId, maxScore }` với `maxScore > 0`; **bỏ `weight`** và ràng buộc Σ weight = 1. Mỗi CLO chỉ
  xuất hiện một lần trong một câu (giữ). Item **không còn `maxScore` riêng** (là tổng suy ra, không lưu).
- Bỏ `assessments.totalPoints` và phép kiểm `|totalPoints − Σ maxScore|`: hệ thống không tính, không lưu tổng điểm bài.
- `scores`: **mỗi document là một ô (sinh viên, câu, CLO)**; thêm `cloId` (bắt buộc), khóa duy nhất mới
  (`sectionId`, `assessmentId`, `itemId`, `cloId`, `studentId`); `maxScore` chụp từ `outcomeMappings[].maxScore`. Các
  trạng thái `SCORED/BLANK/ABSENT/NOT_GRADED/EXEMPT/MISSING` giữ nguyên.
- Công thức cấp bài và cấp học phần theo đặc tả: `CLO = Σ điểm đạt của CLO / Σ điểm tối đa của CLO` (đặc tả §4.2, §4.3),
  tức `measurement_rules.clo.method = POINTS_RATIO`. Giá trị `WEIGHTED_ITEMS` chỉ có nghĩa khi điểm câu chia theo trọng
  số, nay không còn dùng. Việc **xác nhận quy tắc** (`CONFIRMED`) vẫn thuộc Khoa (Q04).
- `weightInCourse` của bài **giữ trong CSDL nhưng không tham gia công thức CLO** theo đặc tả (chờ Q20).
- PI vẫn suy ra từ kết quả CLO qua `clo_pi_links` (ADR 0011 giữ nguyên); `clo_pi_links.weight` vẫn chờ Q22.

## 3. Phương án đã cân nhắc

| Phương án | Nhận xét |
|---|---|
| Giữ duyệt ma trận như ADR 0001 | Không còn phù hợp: GVHD đã xác nhận bỏ |
| Giữ ngân hàng câu hỏi nhưng để `content` trống | Vẫn còn đường lưu nội dung đề, trái nguyên tắc bảo mật đề thi; 3 collection thừa vì item khai báo trực tiếp đã đủ để đo |
| Giữ `weight` và thêm cột điểm theo CLO | Hai cách chia điểm song song, khó giải thích khi vấn đáp |
| **Theo đặc tả (chọn)** | Một cách duy nhất, bám đặc tả, bỏ được hiện tượng CLO này bù điểm cho CLO kia |

## 4. Hệ quả

**Tài liệu (cập nhật cùng ADR này):** `GiaiThich_CoSoDuLieu.md` (v0.6), `database_import.js`, `ke-hoach-xay-dung.md`,
`chot-feature-do-an.md`, `cam-ket-san-pham-metric.md`, `tom-tat-du-an-de-thao-luan.md`, `plan/F04-bai-danh-gia.md`,
`plan/F05b-nhap-diem.md`, `CLAUDE.md`, `.claude/rules/02-domain-invariants.md`, ADR 0001 (đánh dấu bị thay thế).

**Backlog:** NEW-01 đổi nghĩa thành "Giảng viên tự khóa ma trận đề" (giữ mã để truy vết); bỏ US-13, US-14, US-17.
Tổng backlog 44 → 41 story (xem `ke-hoach-xay-dung.md`).

**Mã và test chưa sửa (bước tiếp theo, cần quyết định riêng):** F04 đang có mã cho quy trình duyệt (`AssessmentApprovalService`,
endpoint `/submit`, `/approve`, `/reject`, enum `AssessmentStatus`/`AuditAction`, `OutcomeMapping.weight`,
`AssessmentItemValidator`, `AssessmentCoverageCalculator`) và test tương ứng (`AssessmentIntegrationTest`,
`AssessmentApprovalServiceImplTest`, `AssessmentItemValidatorTest`, `AssessmentCoverageCalculatorTest`,
`AssessmentScopeAuthorizationTest`). Nếu nạp `database_import.js` mới trước khi sửa mã thì các test tích hợp F04 sẽ lỗi
validator (mã 121). Vì vậy **không merge thay đổi này vào `main` trước khi sửa mã F04 cùng PR hoặc PR liền sau**.
`test-plan.md` §8.5b và `sdd/` mô tả mã hiện tại nên chưa đổi; cập nhật cùng bước sửa mã.

**CSDL đã có dữ liệu:** `scores` cũ (nếu có) thiếu `cloId`; hiện chưa có điểm thật (F05b chưa làm), nên không cần migrate.

## 5. Còn mở (đặc tả chưa nói rõ, chưa tự quyết)

| # | Vấn đề | Trạng thái |
|---|---|---|
| 1 | Mở khóa ma trận khi khóa nhầm (`UNLOCK`: chỉ khi chưa có điểm, có lý do, ghi `audit_logs`) | Chưa quyết (TD-35) |
| 2 | Sinh viên thiếu dữ liệu ở một bài khi tính cấp học phần; ngưỡng `minCoverage` (đặc tả §4.4 chưa định nghĩa "ngưỡng cho phép") | Q23, chưa có |
| 3 | Sinh viên vắng có vào mẫu số hay không; báo `passRateOfEnrolled` kèm theo | Q13, chưa có |
| 4 | Có dùng trọng số của bài trong học phần (`weightInCourse`) khi gộp CLO nhiều bài không | Q20, chưa có |
| 5 | Công thức CLO → PI → PLO và `clo_pi_links.weight` | Q22, chưa có |
| 6 | Mục tiêu tỷ lệ đạt cấp lớp (`defaultTargetPassRate`), `requireAllCloPass` | Q12, chưa có |

## 6. Để vấn đáp

Vì sao nhập điểm theo từng CLO thay vì chia theo trọng số (sinh viên làm đúng ý CLO2, sai ý CLO3 trong cùng một câu thì
mỗi CLO phản ánh đúng thực chất, không bù lẫn nhau); vì sao `Σ điểm đạt / Σ điểm tối đa` ở cấp học phần tương đương trung
bình có trọng số theo điểm tối đa của từng bài; vì sao bỏ ngân hàng câu hỏi (không lưu đề thì không lộ đề, item khai báo
trực tiếp đã đủ để đo); bỏ khóa ma trận thì sửa điểm tối đa sau khi có điểm làm sai CLO của cả lớp.
