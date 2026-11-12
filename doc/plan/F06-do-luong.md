# F06 — Engine Đo lường Chuẩn đầu ra 4 Cấp độ (`ASSESSMENT_CLO → CLO → PI → PLO`)

Story: **US-27** tính `ASSESSMENT_CLO` (Cấp 1) và `CLO` toàn học phần (Cấp 2) của từng sinh viên (C), **US-28** tính `PI` (Cấp 3) và `PLO` (Cấp 4) (C), **US-29** tổng hợp cấp bài đánh giá / lớp (`N_section`) và cấp khóa / CTĐT (`N_cohort`) (C), **US-30** chống chạy trùng `lockKey` + `isStale` (MR). Giai đoạn P4. Chủ trì đề xuất: IBS (đặc tả, kiểm chứng) + IT (lập trình).
Phụ thuộc: `F02`–`F05b` (cần CTĐT, CLO, liên kết `clo_pi_links`, bài đánh giá đã khóa + `cloWeights`, điểm `scores`).
**Đây là mô-đun lõi** (coverage ≥ 70%, cả hai sinh viên giải thích được từng công thức).

## 1. Mục tiêu

Từ điểm từng cặp `(Câu hỏi, CLO)` trong bảng `scores`, tính toán và lưu trữ mức đạt chuẩn đầu ra xuyên suốt **4 cấp độ (`RESULT_LEVELS = ['ASSESSMENT_CLO', 'CLO', 'PI', 'PLO']`)** của từng sinh viên, rồi tổng hợp cấp bài đánh giá / lớp học phần ($N_{\text{section}}$) và cấp khóa / CTĐT ($N_{\text{cohort}}$).
- **Không hardcode** trọng số hay ngưỡng đạt trong mã Java: đọc trực tiếp từ `plos.passThreshold`, `pis.weight`, `pis.passThreshold`, `clos.measurement.defaultPassThreshold`, `clo_pi_links.weight`, `assessments.cloWeights` (đã loại bỏ hoàn toàn collection `measurement_rules` theo ADR 0014).

## 2. Đọc trước

- [`doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`](../dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md) §4–§5 (công thức toán học 4 cấp độ và ví dụ tính tay).
- [`doc/GiaiThich_CoSoDuLieu.md`](../GiaiThich_CoSoDuLieu.md) v0.7 (25 collections) và [`doc/database_import.js`](../database_import.js).
- [`doc/adr/0014-import-ctdt-de-cuong-va-do-luong-4-cap-clo-pi-plo.md`](../adr/0014-import-ctdt-de-cuong-va-do-luong-4-cap-clo-pi-plo.md).

## 3. Collection và trường chính

| Collection | Trường chính |
|---|---|
| `calculation_runs` | `scope` (`SECTION`/`COHORT`/`PROGRAM`), `scopeRef` (`sectionIds`, `courseIds`, `cohorts`, `termId`), `programVersionId`, `syllabusIds[]`, `isOfficial`, `dataSnapshot` (`scoreChecksum`, `totalScores`, `lastScoreUpdatedAt`), `lockKey`, `isStale`, `staleReason`, `status` (`PENDING`/`PROCESSING`/`SUCCESS`/`FAILED`), `errorMessage`, `triggeredBy`, `startedAt`, `finishedAt` *(đã bỏ `ruleId`)* |
| `student_results` | `runId`, **`level` (`ASSESSMENT_CLO` / `CLO` / `PI` / `PLO` — đã bỏ `COURSE`)**, `subjectId`, `sectionId`, **`assessmentId`** (`!= null` khi `level = ASSESSMENT_CLO`; `= null` khi `level` thuộc `CLO | PI | PLO`), `studentId`, `termId`, `status` (`COMPUTED`/`INSUFFICIENT_DATA`/`NOT_APPLICABLE`), `value` (0–1), `passThreshold` (0–1), `passed`, `completeness` (`requiredCount`, `availableCount`, `ratio`), `inputs[]` (`sourceType`: `SCORE`/`STUDENT_RESULT`, `refId`, `normalizedValue`, `weight`), `programVersionId`, `syllabusId`. Duy nhất `(runId, level, subjectId, sectionId, assessmentId, studentId)` |
| `aggregate_results` | `runId`, **`level` (`ASSESSMENT_CLO` / `CLO` / `PI` / `PLO`)**, `subjectId`, `sectionId`, `assessmentId`, `scope` (`type`: `ASSESSMENT`/`SECTION`/`COHORT`/`PROGRAM`, `refId`, `cohort`, `termId`), `counts` (`enrolled`, `passed`, `failed`), `mean`, `passRate`, `passThreshold`, `dataCompleteness`, `programVersionId` |

## 4. Công thức triển khai 4 cấp độ (xem `dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md` §4)

### 4.1 Cấp độ 1 (`level = ASSESSMENT_CLO`, `assessmentId = A_p.id`) — CLO trên từng bài kiểm tra
$$CLO_{s,p,j} = \frac{\sum_{k \in K_p(CLO_j)} Score_{s,p,k,j}}{\sum_{k \in K_p(CLO_j)} MaxScore_{p,k,j}} \in [0, 1], \qquad \text{Passed}(CLO_{s,p,j}) = \big( CLO_{s,p,j} \ge \text{PassThreshold}(CLO_j) \big)$$
- Thống kê lớp học phần ($N_{\text{section}}$ sinh viên có `enrollments.status == ENROLLED`):
  $$\overline{CLO}_{p,j} = \frac{1}{N_{\text{section}}} \sum_{s=1}^{N_{\text{section}}} CLO_{s,p,j}, \qquad \text{PassRate}(CLO_{p,j}) = \frac{\text{passed}}{N_{\text{section}}}$$

### 4.2 Cấp độ 2 (`level = CLO`, `assessmentId = null`) — CLO toàn học phần
- Kiểm tra tổng tỷ lệ đóng góp của các bài kiểm tra trong học phần cho $CLO_j$ bằng $1.0$: $\sum_{p \in P(CLO_j)} W_{p,j} = 1.0$.
$$Value(CLO_{s,j}) = \sum_{p \in P(CLO_j)} \big( W_{p,j} \times CLO_{s,p,j} \big) \in [0, 1], \qquad \text{Passed}(CLO_{s,j}) = \big( Value(CLO_{s,j}) \ge \text{PassThreshold}(CLO_j) \big)$$
- Thống kê lớp học phần: chia cho mẫu số $N_{\text{section}}$ (`ENROLLED`).

### 4.3 Cấp độ 3 (`level = PI`, `assessmentId = null`) — Tổng hợp PI từ CLO của một hoặc nhiều học phần
- Chỉ sử dụng các liên kết `clo_pi_links` đã `VERIFIED` ($W_{CLO_{c,j} \to PI_m} \in (0, 1]$, không ép tổng thiết kế bằng $1.0$).
- Nếu sinh viên $s$ học lại / học cải thiện học phần $c$ nhiều lần, chọn kết quả $Value(CLO_{s,c,j})$ từ **lần học hợp lệ gần nhất (`LATEST`)**.
- Tính trung bình có trọng số chuẩn hóa trên tập các CLO mà sinh viên $s$ đã có kết quả $\text{Evaluated}(s, PI_m)$:
$$Value(PI_{s,m}) = \frac{\sum_{(c, j) \in \text{Evaluated}(s, PI_m)} \Big( W_{CLO_{c,j} \to PI_m} \times Value(CLO_{s,c,j}) \Big)}{\sum_{(c, j) \in \text{Evaluated}(s, PI_m)} W_{CLO_{c,j} \to PI_m}} \in [0, 1]$$
$$\text{Passed}(PI_{s,m}) = \big( Value(PI_{s,m}) \ge \text{PassThreshold}(PI_m) \big)$$
- Thống kê cấp khóa / CTĐT: chia cho mẫu số $N_{\text{cohort}}$.

### 4.4 Cấp độ 4 (`level = PLO`, `assessmentId = null`) — Tổng hợp PLO từ các PI
- Kiểm tra $\sum_{m \in PI(PLO_q)} W_{PI_m \to PLO_q} = 1.0$ (từ `pis.weight` đã `VERIFIED`).
$$Value(PLO_{s,q}) = \frac{\sum_{m \in \text{Evaluated}(s, PLO_q)} \big( W_{PI_m \to PLO_q} \times Value(PI_{s,m}) \big)}{\sum_{m \in \text{Evaluated}(s, PLO_q)} W_{PI_m \to PLO_q}} \in [0, 1]$$
$$\text{Passed}(PLO_{s,q}) = \big( Value(PLO_{s,q}) \ge \text{PassThreshold}(PLO_q) \big)$$
- Thống kê cấp khóa / CTĐT: chia cho mẫu số $N_{\text{cohort}}$.
