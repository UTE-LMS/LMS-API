# F06 — Quy tắc và engine đo lường (CLO → PI → PLO)

Story: **US-26** quy tắc đo lường có phiên bản (C), **US-27** tính CLO từng sinh viên (C), **US-28** tính PI và PLO (C),
**US-29** tổng hợp lớp/khóa/CTĐT kèm mẫu số (C), **US-30** chống chạy trùng + `isStale` (MR; ghi `lockKey` đi kèm US-27 là C),
**NEW-04** phương pháp mở rộng (MR). Giai đoạn P4. Chủ trì đề xuất: IBS (đặc tả, kiểm chứng) + IT (lập trình).
Package: `measurement`. Phụ thuộc: `00`, `F02`–`F05b` (cần CTĐT, CLO, liên kết, bài đã duyệt, điểm).
**Đây là mô-đun lõi** (coverage ≥ 70%, cả hai sinh viên giải thích được từng công thức). **Có công thức chưa được đặc tả: xem mục 9 trước khi code cấp PLO, COURSE, gộp PI nhiều học phần.**

## 1. Mục tiêu

Từ điểm từng câu hỏi/tiêu chí tính mức đạt **CLO → PI → PLO** của từng sinh viên, rồi tổng hợp cấp lớp/khóa/CTĐT có mẫu số.
Công thức, trọng số, ngưỡng **đọc từ `measurement_rules`**, không hardcode. Mọi kết quả gắn phiên bản và truy vết một cấp liền kề.

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` nguyên tắc 1, 5, 6, 7, 9, 10, §4.23–4.26, §5 (luồng và 4 trường hợp cần phân biệt), Q04, Q10, Q12, Q13, Q15, Q18, Q19.
- `PLO_DB_UPDATE.md` §4 (công thức tham chiếu) và §5 mục 1, 4.
- `database_import.js`: khối `measurement_rules` (cả điều kiện `useIrmAsWeight`), `calculation_runs`, `student_results`, `aggregate_results`; seed `QT-PDBCL-DLCDR` bản `0.1-DRAFT`.

## 3. Collection và trường chính

| Collection | Trường chính |
|---|---|
| `measurement_rules` | `code`, `version`, `status` (`DRAFT`/`CONFIRMED`/`RETIRED`), `confirmedBy/At`; khối `clo` (`method`: `WEIGHTED_ITEMS`/`POINTS_RATIO`, `defaultPassThreshold`, `minCoverage`); `course` (`requireAllCloPass`, `defaultTargetPassRate`); `pi` (`method`: `DIRECT_FROM_ITEMS`/`FROM_CLOS`, `aggregationMethod`: `WEIGHTED_AVERAGE`/`MAX`/`LATEST`/`ACCUMULATED_EVIDENCE`, `useIrmAsWeight`, `irmWeights`, `defaultPassThreshold`); `plo` (`method`: `ALL_PIS_PASSED`/`WEIGHTED_PIS`/`AVERAGE_PIS`, `defaultPassThreshold`, `cohortTargetPassRate`); `missingDataPolicy` (`blankScoreValue` = 0, `excludeAbsentFromDenominator`, `excludeMissingFromDenominator`). Duy nhất `(code, version)` |
| `calculation_runs` | `scope` (`SECTION`/`COURSE`/`COHORT`/`PROGRAM`), `scopeRef` (`sectionIds`, `courseIds`, `cohorts`, `termId`), `ruleId`, `programVersionId`, `syllabusIds[]`, `isOfficial`, `dataSnapshot` (`scoreChecksum`, `totalScores`, `lastScoreUpdatedAt`), `lockKey`, `isStale`, `staleReason`, `status` (`PENDING`/`PROCESSING`/`SUCCESS`/`FAILED`), `errorMessage`, `triggeredBy`, `startedAt`, `finishedAt` |
| `student_results` | `runId`, `level` (`CLO`/`COURSE`/`PI`/`PLO`), `subjectId`, `sectionId`, `studentId`, `termId`, `status` (`COMPUTED`/`INSUFFICIENT_DATA`/`NOT_APPLICABLE`), `value` (0–1 hoặc null), `passed`, `evidenceCount`, `completeness` (`requiredCount`, `availableCount`, `ratio`), `inputs[]` (`sourceType`: `SCORE`/`STUDENT_RESULT`, `refId`, `normalizedValue`, `weight`), `ruleId`, `programVersionId`, `syllabusId`. Duy nhất `(runId, level, subjectId, sectionId, studentId)` |
| `aggregate_results` | `runId`, `level`, `subjectId`, `scope` (`type`, `refId`, `cohort`, `termId`), `counts` (`enrolled`, `evaluable`, `passed`, `failed`, `insufficientData`), `passRate`, `passRateOfEnrolled`, `targetPassRate`, `targetMet`, `dataCompleteness`, `ruleId`, `programVersionId` |

## 4. Quy tắc nghiệp vụ

### 4.1 Chung
1. **Không hardcode** công thức, trọng số, ngưỡng, mẫu số. Mọi số đến từ `measurement_rules` (hoặc `clos.measurement`, `pis.targetPassRate`). Bản `DRAFT` có ngưỡng `null`.
2. **Chỉ quy tắc `CONFIRMED` mới cho kết luận chính thức.** `isOfficial = true` khi: quy tắc `CONFIRMED` **và** mọi liên kết CLO–PI dùng là `VERIFIED` **và** dữ liệu đã chốt sổ (học kỳ `SCORE_LOCKED`, **[ĐỀ XUẤT]**). Ngược lại chạy ở chế độ không chính thức, mọi kết quả/báo cáo ghi rõ.
3. Chạy với quy tắc `DRAFT` chưa có ngưỡng: vẫn tính được `value`, nhưng `passed = null` và `isOfficial = false`. Không được tự đặt ngưỡng.
4. **Thiếu dữ liệu ≠ 0:** `BLANK` tính 0 (có ghi nhận, vào cả tử và mẫu); `ABSENT`/`NOT_GRADED`/`EXEMPT`/`MISSING` (`rawScore = null`) xử lý theo `missingDataPolicy` và `clo.minCoverage`: loại khỏi mẫu số hay ghi `INSUFFICIENT_DATA`. Policy còn `null` ⇒ **không đoán**, trả `INSUFFICIENT_DATA` và báo rõ **[CHỜ Q13]**.
5. **Tách bạch:** điểm học phần ≠ mức đạt CLO (CLO tính từ điểm câu hỏi/tiêu chí gắn CLO, không từ điểm tổng kết); CLO cá nhân ≠ tỷ lệ đạt lớp ≠ mục tiêu cấp lớp (ba đại lượng riêng); Bloom không tham gia công thức; **I/R/M không là trọng số**.
6. **Không suy mức PLO bằng trung bình mức năng lực (`capabilityLevelRaw`) của PI.**
7. Mỗi lần chạy **tạo `runId` mới** và giữ kết quả cũ; báo cáo cũ đọc lại được khi quy tắc đổi.
8. Engine là **hàm thuần**, không phụ thuộc HTTP/Mongo: nhận điểm + ánh xạ + quy tắc, trả kết quả. Phần đọc/ghi Mongo nằm ở service bao ngoài. Dễ test bằng giá trị tính tay.

### 4.2 Công thức triển khai (nguồn: `PLO_DB_UPDATE.md` §4.1–4.2, **đã chọn làm công thức của đồ án**)
Công thức nằm trong mã dưới dạng *phương pháp có tên*, được chọn bằng `measurement_rules` (`clo.method`, `pi.method`); **ngưỡng, mẫu số, chính sách thiếu dữ liệu vẫn đọc từ quy tắc**, không đặt cứng. Kết luận *chính thức* vẫn cần quy tắc `CONFIRMED` (4.1 mục 2); việc chọn công thức này không thay bước Khoa xác nhận (Q04).

Ký hiệu: sinh viên *s*, câu hỏi/tiêu chí *k* (điểm tối đa `MaxScore_k`, điểm thô `Raw_{s,k}`), cặp `(CLO_j, PI_m)` của *k* với phần điểm chia `w_{k,j,m}`, Σ_{(j,m)} `w_{k,j,m}` = 1.

**CLO** (§4.1; ánh xạ vào `clo.method = POINTS_RATIO`):
```
MaxScore_{k,j}  = MaxScore_k × Σ_m w_{k,j,m}
Score_{s,k,j}   = Raw_{s,k}  × Σ_m w_{k,j,m}
Value(CLO_{s,j}) = Σ_{k∈CLO_j} Score_{s,k,j}  /  Σ_{k∈CLO_j} MaxScore_{k,j}
đạt  ⇔  Value(CLO_{s,j}) ≥ PassThreshold(CLO_j)
```
**PI, chế độ `DIRECT_FROM_ITEMS`** (§4.2):
```
Value(PI_{s,m}) = Σ_{k∈PI_m} Raw_{s,k} × Σ_j w_{k,j,m}  /  Σ_{k∈PI_m} MaxScore_k × Σ_j w_{k,j,m}
```
**PI, chế độ `FROM_CLOS`** (§4.2, NEW-04):
```
Value(PI_{s,m}) = Σ_{j∈PI_m} Value(CLO_{s,j}) × W_{CLO_j→PI_m} × IRM_Factor_j
                  / Σ_{j∈PI_m} W_{CLO_j→PI_m} × IRM_Factor_j
```
`W_{CLO_j→PI_m}` là `clo_pi_links.weight`; `IRM_Factor_j = 1` vì engine luôn coi `useIrmAsWeight = false` (Q18).

**Cách áp dụng:**
- Chỉ các item có điểm tham gia tổng. `BLANK` (`Raw = 0`) vào cả tử và mẫu. Item `ABSENT`/`NOT_GRADED`/`EXEMPT`/`MISSING` bị loại khỏi tử và mẫu *hoặc* khiến kết quả `INSUFFICIENT_DATA`, theo `missingDataPolicy` và `clo.minCoverage` (4.1 mục 4). Policy `null` ⇒ không đoán, `INSUFFICIENT_DATA`.
- Mẫu số bằng 0 (không còn item nào có điểm) ⇒ `INSUFFICIENT_DATA`, không chia cho 0.
- `value` ∈ [0, 1]; so sánh với ngưỡng bằng `≥` (đạt khi bằng ngưỡng). So sánh số thực dùng dung sai (ví dụ 1e-9), tiền xử lý `BigDecimal` nếu cần.
- Mọi item tham gia phải ghi vào `inputs[]` (`refId` = `scores._id`, `weight` = phần `Σ w` đã dùng, `normalizedValue` = `Raw/MaxScore`) để truy vết (4.6).

**Phần tài liệu `PLO_DB_UPDATE.md` KHÔNG có công thức** (không tự bịa; xem 4.4): `clo.method = WEIGHTED_ITEMS`, cấp PLO (`ALL_PIS_PASSED`/`WEIGHTED_PIS`/`AVERAGE_PIS`), `pi.aggregationMethod` ngoài `WEIGHTED_AVERAGE`, cấp COURSE ngoài `requireAllCloPass`. Giá trị enum chưa có công thức ⇒ API từ chối khi chạy (422, "phương pháp chưa được đặc tả"), không âm thầm dùng công thức khác.

**Ví dụ minh họa để viết test (tính tay, KHÔNG phải số của Khoa):**
Bài có hai câu. Câu A: tối đa 10, `Raw = 8`, ba cặp `(CLO1,PI1)=0,3`, `(CLO2,PI1)=0,5`, `(CLO2,PI2)=0,2`. Tiêu chí B: tối đa 20, `Raw = 10`, cặp `(CLO2,PI2)=1`.
- CLO1: Score = 8×0,3 = 2,4; Max = 10×0,3 = 3 ⇒ **Value = 0,8**.
- CLO2: câu A có Σ_m w = 0,5+0,2 = 0,7 ⇒ Score 5,6, Max 7; B ⇒ Score 10, Max 20 ⇒ Value = 15,6/27 ≈ **0,57778**.
- PI1 (DIRECT): chỉ câu A, Σ_j w = 0,3+0,5 = 0,8 ⇒ 8×0,8 / 10×0,8 = 6,4/8 = **0,8**.
- PI2 (DIRECT): A có Σ_j w = 0,2 ⇒ 1,6 và 2; B có Σ_j w = 1 ⇒ 10 và 20 ⇒ 11,6/22 ≈ **0,52727**.
- PI1 (FROM_CLOS), với `W` minh họa CLO1→PI1 = 0,4 và CLO2→PI1 = 0,6: (0,8×0,4 + 0,57778×0,6) / (0,4+0,6) ≈ **0,66667**.
Đặt tên fixture "ví dụ minh họa"; thêm ca tương tự với `ABSENT`/`BLANK` do IBS tính độc lập.

**Về ví dụ 72% trong `GiaiThich_CoSoDuLieu.md` §5** (`60%×0,4 + 80%×0,6`): đó là minh họa khái niệm, **không phải công thức triển khai**. Với cùng dữ liệu, công thức §4.1 cho 22/30 ≈ 73,3%. Test lấy kỳ vọng theo §4.1, không theo 72%. IBS nên sửa ví dụ trong `GiaiThich` §5 cho khớp công thức (việc tài liệu, không chặn code).

### 4.3 Thứ tự ưu tiên ngưỡng **[ĐỀ XUẤT, chờ Q12]**
Ngưỡng đạt CLO: `clos.measurement.defaultPassThreshold` nếu có, ngược lại `measurement_rules.clo.defaultPassThreshold`; cả hai `null` ⇒ `passed = null`. Tương tự cho PI (`pi.defaultPassThreshold`) và PLO.

### 4.4 PLO và phương pháp chưa có công thức **[CHỜ GVHD/KHOA, xem mục 9: CT-01, 03, 04, 05, 09]**
`PLO_DB_UPDATE.md` không nêu công thức cấp PLO, cấp COURSE (ngoài `requireAllCloPass`), `WEIGHTED_ITEMS`, hay các `aggregationMethod` ngoài `WEIGHTED_AVERAGE`. Chưa có câu trả lời thì **không cài**: API trả 422. Đừng suy "hiển nhiên" (ví dụ lấy trung bình `value` PI cho PLO) rồi coi là đã được duyệt. Mục 9 liệt kê từng thiếu sót, phương án gợi ý và ảnh hưởng.

### 4.5 Tổng hợp cấp lớp (`aggregate_results`)
- `counts.enrolled` = số `enrollments ENROLLED` (F05a). `evaluable` = số sinh viên có `status = COMPUTED`. `insufficientData` = các sinh viên còn lại, **kể cả người không có bản ghi điểm nào**: `evaluable + insufficientData = enrolled`.
- `passed`, `failed` trong số `evaluable`. `passRate = passed / evaluable`; `passRateOfEnrolled = passed / enrolled`; `dataCompleteness = evaluable / enrolled`.
- `targetPassRate`: `course.defaultTargetPassRate` (cấp lớp), `pis.targetPassRate` (PI), `plo.cohortTargetPassRate` (khóa); `targetMet` chỉ tính khi mục tiêu có giá trị.
- Mẫu số 0 ⇒ tỷ lệ `null`, không chia cho 0. **Luôn trả đủ `counts`.**

### 4.6 Truy vết một cấp liền kề (`inputs[]`)
Cấp CLO → `scores`; cấp PI → `scores` (DIRECT) hoặc `student_results` cấp CLO (FROM_CLOS); cấp PLO → `student_results` cấp PI. Mỗi `inputs[]` có `sourceType`, `refId`, `normalizedValue`, `weight`. Không nhúng chuỗi đầy đủ (giới hạn 16 MB của BSON).

### 4.7 Chạy, khóa, trạng thái cũ
- Khóa chống chạy trùng: `lockKey` = chuỗi từ (scope, phạm vi, `ruleId`); chỉ mục duy nhất áp dụng khi `status ∈ PENDING, PROCESSING`. Trùng ⇒ 409. Chuyển `PENDING → PROCESSING → SUCCESS | FAILED`; lỗi ⇒ `FAILED` + `errorMessage` và **nhả khóa**.
- Ghi `dataSnapshot` (`scoreChecksum`, `totalScores`, `lastScoreUpdatedAt`) để biết lần tính dùng dữ liệu nào.
- Khi điểm trong phạm vi bị sửa/nhập thêm sau lần tính ⇒ `isStale = true` + `staleReason` (US-30, MR).
- Ghi `audit_logs`: `RUN_CALCULATION` khi chạy, `RULE_CONFIRM` khi xác nhận quy tắc. Ghi kết quả của một lần chạy (student_results + aggregate_results) trong cùng một transaction hoặc đánh dấu lần chạy `FAILED` nếu không trọn vẹn.

## 5. Stories và acceptance criteria (nháp)

| ID | AC |
|---|---|
| US-26/AC-1 | Tạo `measurement_rules` `DRAFT` với ngưỡng `null` → hợp lệ; trùng `(code, version)` → 409 |
| US-26/AC-2 | Xác nhận quy tắc (`CONFIRMED`) ghi `confirmedBy/At` và `audit_logs` `RULE_CONFIRM`; chỉ vai trò được phép **[CHỜ KHOA, Q08]** |
| US-26/AC-3 | Quy tắc `CONFIRMED` không sửa nội dung; muốn đổi ⇒ tạo `version` mới (`RETIRED` bản cũ) |
| US-26/AC-4 | Bật `useIrmAsWeight = true` thiếu `irmWeights` I, R, M > 0 ⇒ bị từ chối; engine **bỏ qua** cờ này và coi là `false` (Q18) |
| US-27/AC-1 | Kết quả CLO (công thức §4.1) của từng sinh viên khớp **tính tay độc lập** trên bộ kiểm chứng, gồm ví dụ minh họa ở 4.2 (CLO1 = 0,8; CLO2 ≈ 0,57778) (lớp nhỏ, có vắng/chưa chấm/miễn, câu đo nhiều cặp, ca sát ngưỡng) |
| US-27/AC-2 | `BLANK` vào tử và mẫu; `MISSING`/`ABSENT`/`NOT_GRADED`/`EXEMPT` không bị coi là 0; kết quả theo `missingDataPolicy`, thiếu thì `INSUFFICIENT_DATA` |
| US-27/AC-3 | Một câu đo 3 cặp weight 0,3/0,5/0,2: phần điểm tối đa chia 3/5/2 (ví dụ minh họa với câu 10 điểm) và CLO chỉ nhận phần của mình |
| US-27/AC-4 | `inputs[]` của mỗi `student_results` CLO chỉ ra đúng các `scores._id` đã dùng |
| US-27/AC-5 | Quy tắc `DRAFT` ⇒ `isOfficial = false`, `passed = null` nếu ngưỡng `null`; chạy đồng thời cùng phạm vi + quy tắc ⇒ 409 |
| US-28/AC-1 | PI `DIRECT_FROM_ITEMS` (PI1 = 0,8; PI2 ≈ 0,52727 ở ví dụ 4.2) và PLO theo `plo.method` đã chọn khớp tính tay; phương pháp chưa có công thức ⇒ 422; PLO **không** suy từ trung bình `capabilityLevelRaw` |
| US-28/AC-2 | `inputs[]` mỗi cấp trỏ đúng một cấp liền kề (PI → scores; PLO → student_results PI) |
| US-29/AC-1 | Lớp 45 ghi danh, 40 đủ dữ liệu (ví dụ minh họa) ⇒ `counts` = {enrolled 45, evaluable 40, insufficientData 5, passed/failed đúng}; báo cả 5 người chưa đủ |
| US-29/AC-2 | `passRate = passed/evaluable`, `passRateOfEnrolled = passed/enrolled`, `dataCompleteness = evaluable/enrolled`; mẫu số 0 ⇒ `null`, không lỗi chia 0 |
| US-29/AC-3 | Mục tiêu `null` ⇒ `targetMet = null`, không mặc định `false`/`true` |
| US-30/AC-1 (MR) | Sửa/nhập điểm sau lần tính ⇒ `calculation_runs.isStale = true` với `staleReason`; kết quả cũ vẫn đọc được |
| NEW-04 (MR) | Mỗi phương pháp mở rộng (`FROM_CLOS`, `MAX`, `LATEST`, `ACCUMULATED_EVIDENCE`, `AVERAGE_PIS`) có ca tính tay độc lập; `WEIGHTED_PIS` chỉ làm sau khi CSDL có trọng số |

## 6. API đề xuất **[ĐỀ XUẤT]**

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| GET/POST/PUT | `/api/measurement-rules`, `/api/measurement-rules/{id}` | PROGRAM_MANAGER (đặc tả); xác nhận: `POST .../confirm` **[CHỜ Q08]** |
| POST | `/api/calculation-runs` `{ scope, scopeRef, ruleId }` | PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER; LECTURER (lớp mình) |
| GET | `/api/calculation-runs/{id}`; `GET .../student-results`, `.../aggregate-results` | theo phạm vi |

## 7. Kiểm tra tham chiếu ở service

Phạm vi chạy có điểm (bài `SCORE_ENTERED`/`LOCKED`) và ánh xạ; mọi cặp trong `outcomeMappings` còn nằm trong `clo_pi_links` (hoặc dùng snapshot trong bài); `ruleId` tồn tại;
nếu `isOfficial`: quy tắc `CONFIRMED`, liên kết `VERIFIED`, học kỳ đã chốt.

## 8. Test bắt buộc

Giá trị kỳ vọng **tính tay độc lập** (ghi cách tính), không lấy kết quả của chính mã. Ca: lớp nhỏ có sinh viên `ABSENT`/`NOT_GRADED`/`EXEMPT`/`MISSING`/`BLANK`; câu đo nhiều cặp; ca sát ngưỡng
(`value = ngưỡng` ⇒ đạt, `ngưỡng − ε` ⇒ không); sinh viên không có điểm nào; PI không có câu hỏi nào gán; CLO không có câu nào (`NOT_APPLICABLE`); mẫu số 0; quy tắc `DRAFT` chạy chính thức bị
từ chối; tổng `weight` ≠ 1 trong dữ liệu (engine từ chối/ghi cảnh báo); chạy trùng đồng thời; chạy lỗi giữa chừng nhả khóa; sửa điểm sau tính ⇒ `isStale`; hiệu năng ~60 sinh viên ≤ 5 giây (N2).
Đặt tên fixture "ví dụ minh họa"; **không** đưa 40/60, 72%, 20/40/40 vào seed/cấu hình mặc định.

## 9. Công thức còn thiếu — câu hỏi cho GVHD/Khoa

`PLO_DB_UPDATE.md` §4 chỉ có công thức **CLO** (§4.1) và **PI** (§4.2, hai chế độ). Các phần dưới đây có giá trị enum hoặc trường trong CSDL
nhưng **tài liệu không nêu công thức**. Agent/LLM **không được tự bịa**: đến khi có câu trả lời, engine từ chối (422 "phương pháp chưa được đặc tả")
hoặc trả `INSUFFICIENT_DATA`/`passed = null`. Mã Q20–Q23 đã thêm vào sổ câu hỏi ở `GiaiThich_CoSoDuLieu.md` §8.

| Mã | Còn thiếu gì | Vì sao thiếu (đối chiếu lược đồ) | Phương án gợi ý để chọn | Chặn | Q |
|---|---|---|---|---|---|
| CT-01 | Công thức CLO khi `clo.method = WEIGHTED_ITEMS` và **nguồn trọng số của từng câu hỏi/tiêu chí** | `items[]` chỉ có `maxScore`, không có trường trọng số item | (a) bỏ giá trị này, chỉ giữ `POINTS_RATIO`; (b) dùng `assessments.weightInCourse` kết hợp tỷ lệ điểm; (c) thêm `items[].weight` vào CSDL | Chỉ khi chọn giá trị này | Q20 |
| CT-02 | Vai trò của `assessments.weightInCourse` và `clos.measurement.defaultWeight` | Hai trường có trong CSDL nhưng không xuất hiện trong công thức §4.1–4.2 | Dùng cho điểm học phần/tỷ trọng CLO trong học phần, hoặc bỏ khỏi cam kết | Cấp COURSE (CT-03) | Q20 |
| CT-03 | Kết quả cấp **COURSE**: `value` và `passed` khi `course.requireAllCloPass = false` | Chỉ biết: `true` ⇒ đạt khi mọi CLO đạt; không nêu `value` hay trường hợp `false` | (a) không tính `value`, chỉ `passed` theo mọi CLO đạt; (b) `value = Σ Value(CLO)×defaultWeight` (cần CT-02) | Báo cáo cấp học phần | Q20 |
| CT-04 | Cấp **PLO**: `value` và `passed` của `ALL_PIS_PASSED`, `AVERAGE_PIS`, `WEIGHTED_PIS` | Không có công thức PLO. `WEIGHTED_PIS` còn thiếu trường trọng số PI trong PLO | `ALL_PIS_PASSED`: đạt khi mọi PI đạt (value = tỷ lệ PI đạt hay null?); `AVERAGE_PIS`: TB `value` PI (PI không dữ liệu thì loại hay chặn?); `WEIGHTED_PIS`: cần trường trọng số mới | **US-28 phần PLO** | Q21 |
| CT-05 | Gộp PI của một sinh viên qua **nhiều học phần/lớp/học kỳ**: `aggregationMethod` = `WEIGHTED_AVERAGE`, `MAX`, `LATEST`, `ACCUMULATED_EVIDENCE` | §4.2 chỉ viết tổng trên các câu `k ∈ PI_m`, không nói gộp qua nhiều học phần; không nêu trọng số của `WEIGHTED_AVERAGE` (theo điểm tối đa? tín chỉ?), `LATEST` (theo `termId`?), `ACCUMULATED_EVIDENCE` (cộng dồn điểm/điểm tối đa?) | Mặc định một PI chỉ tính trong phạm vi một học phần (một lớp); gộp nhiều học phần là NEW-04 sau khi có công thức | PI/PLO toàn khóa | Q22 |
| CT-06 | `FROM_CLOS`: nguồn và **chuẩn hóa** `W_{CLO→PI}` | `clo_pi_links.weight` có thể `null`; chưa nói tổng `W` của các CLO cùng PI có phải bằng 1, và CLO từ học phần khác nhau xử lý thế nào | Công thức §4.2 đã chuẩn hóa bằng mẫu số Σ W nên không cần tổng = 1; cần xác nhận cách điền `W` khi `null` (không chạy) | NEW-04 | Q22 |
| CT-07 | Thiếu dữ liệu **trong công thức**: định nghĩa `clo.minCoverage`, khi nào `INSUFFICIENT_DATA`, `excludeAbsentFromDenominator`/`excludeMissingFromDenominator` áp ở mức câu hỏi hay mức sinh viên | `completeness.requiredCount/availableCount/ratio` có, nhưng đo theo số câu hay theo điểm tối đa chưa nêu; vắng cả bài thì có vào `evaluable` không | Ratio theo **điểm tối đa** các câu bắt buộc (hay theo số câu); `minCoverage` so với ratio đó; `ABSENT` cả bài ⇒ `INSUFFICIENT_DATA` và vào `insufficientData` | **US-27, US-29** (kết luận chính thức) | Q13, Q23 |
| CT-08 | **Ngưỡng:** ưu tiên giữa `clos.measurement.defaultPassThreshold` và `measurement_rules.clo.defaultPassThreshold`; ngưỡng PI/PLO; `≥` hay `>` | Nhiều chỗ chứa ngưỡng, chưa nói chỗ nào thắng | CLO riêng nếu có, ngược lại ngưỡng của quy tắc; so sánh `≥` (theo §4.1) | US-27 (`passed`) | Q12, Q23 |
| CT-09 | Tổng hợp **COHORT/PROGRAM**: gộp sinh viên từ nhiều lớp thế nào (đếm sinh viên một lần hay theo từng lớp), `passRate` khóa tính trên sinh viên hay trên bản ghi lớp | `aggregate_results.scope` có cohort/program nhưng §4 chỉ nêu cấp lớp | Chỉ làm cấp lớp (SECTION) và học phần (COURSE) trước; COHORT/PROGRAM sau khi có công thức | US-29 phần khóa/CTĐT, US-32 | Q22, Q23 |
| CT-10 | **Làm tròn và độ chính xác:** số chữ số lưu, dung sai khi so sánh ngưỡng, `double` hay `BigDecimal` | Tài liệu chỉ nêu dung sai 0,001 cho `totalPoints` | Đề xuất: lưu nguyên độ chính xác, so ngưỡng với dung sai 1e-9, làm tròn chỉ khi hiển thị. Do nhóm quyết (kỹ thuật), không bắt buộc hỏi GVHD | Test kỳ vọng | Q23 |
| CT-11 | Tiêu chí **rubric**: `rawScore` của `CRITERION` lấy từ `rubric.levels[].score` hay nhập trực tiếp; `rubricLevel` có tham gia công thức không | §4 coi mọi item như nhau; mức rubric chỉ lưu | Mặc định: dùng `rawScore` đã nhập; `rubricLevel` chỉ để hiển thị/đối chiếu | F05b khi nhập tiêu chí | Q20 |

**Cách làm trước khi có trả lời:** chạy được CLO (§4.1), PI `DIRECT_FROM_ITEMS` và `FROM_CLOS` (§4.2) theo mục 4.2; cấp COURSE chỉ ghi `passed` theo `requireAllCloPass = true`; cấp PLO và các phương pháp ở CT-01, 04, 05, 09 trả 422.
Ghi câu trả lời của GVHD/Khoa vào bảng này (cột Quyết định, ngày, người trả lời), rồi cập nhật mục 4 và tạo ADR nếu CSDL đổi.

## 10. Không làm / chưa quyết

- Không làm: ML, quy đổi I/R/M thành hệ số, kết luận mức đạt toàn chương trình khi chỉ có dữ liệu ba học phần thử nghiệm, suy PLO từ trung bình mức năng lực, dùng điểm tổng kết học phần làm đầu vào CLO.
- **Công thức còn thiếu (mục 9, Q20–Q23) cần hỏi GVHD/Khoa trước khi cài.**
- **Chờ Khoa:** Q04 (xác nhận công thức, ngưỡng, mẫu số để có kết luận chính thức; công thức CLO/PI đã chọn theo `PLO_DB_UPDATE.md` §4), Q12 (đạt từng CLO, tỷ trọng/ngưỡng CLO), Q13 (xử lý vắng/chưa chấm/thiếu trong mẫu số), Q15 (chia điểm câu đo nhiều cặp), Q18 (I/R/M), Q19 (phương pháp mặc định). Nếu đến K3 vẫn chưa có: chạy chế độ không chính thức, ghi rõ giới hạn (cần GVHD đồng ý).
- Cần bổ sung CSDL nếu chọn `WEIGHTED_PIS` (thiếu trọng số PI trong PLO), `WEIGHTED_ITEMS` (thiếu trọng số item): ghi ADR và cập nhật `GiaiThich_CoSoDuLieu.md` + `database_import.js` cùng PR.

## 11. Để vấn đáp

Vì sao công thức đọc từ `measurement_rules` (Khoa chưa chốt; đổi quy tắc không phải sửa mã); vì sao `BLANK` khác `MISSING` trong công thức; vì sao tỷ lệ đạt luôn kèm
mẫu số (45 ghi danh, 40 đủ dữ liệu thì 5 người còn lại không được biến mất); vì sao truy vết chỉ một cấp liền kề (tránh vượt 16 MB, mỗi cấp tự chứng minh bằng cấp dưới);
vì sao `lockKey` (hai giảng viên cùng bấm tính một lớp); bỏ cờ `isOfficial` thì kết quả nháp có thể bị đọc như kết luận chính thức.
