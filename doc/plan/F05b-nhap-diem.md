# F05b — Nhập điểm trực tiếp từng CLO của từng câu hỏi

Story: **US-21** nhập điểm / tải tệp + ánh xạ cột `(itemId, cloId)` (C), **US-22** kiểm tra, báo lỗi theo dòng/cột (C), **US-23** chọn BLANK/MISSING + chính
sách nhập lại (C; chỉ `REJECT_ON_CONFLICT` cam kết), **US-24** xác nhận trong một transaction hoặc rollback (C), **US-25** sửa
điểm đơn lẻ có lý do (MR). Giai đoạn P3. Chủ trì đề xuất: IT (IBS cho cách hiểu ô trống). Package: `scoreimport`.
Phụ thuộc: `00` (transaction, audit), `F01`, `F04` (bài `LOCKED` / `SCORE_ENTERED`), `F05a` (lớp, sinh viên, ghi danh).
**Trạng thái hiện thực (2026-11-10): US-21..24 đã merge qua PR #83 (theo schema cũ) và sẽ được refactor theo ADR 0014 (nhập điểm theo cặp `(itemId, cloId)`, vắng thi/bỏ trống = 0.0).** Mã nằm ở các package theo tầng của repo (`controller`, `service`, `service.impl`, `document`, ...).
**Đây là mô-đun lõi** (coverage ≥ 70%, cả hai sinh viên phải giải thích được).

## 1. Mục tiêu

Tiếp nhận điểm **của từng CLO trong từng câu hỏi** do giảng viên nhập trực tiếp hoặc tải lên từ tệp CSV/Excel (sau khi ma trận bài đánh giá đã được giảng viên khóa `LOCKED`), kiểm tra đối chiếu với `maxScore` của từng cặp `(itemId, cloId)`, báo lỗi chính xác theo dòng/cột,
cho người dùng xác nhận, rồi ghi `scores` **trong một transaction** hoặc không ghi gì. Mọi điểm truy vết được về lô nhập và dòng
tệp gốc.

## 2. Đọc trước

- `dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md` §3.3, §3.4, §6.2.
- `GiaiThich_CoSoDuLieu.md` (v0.6) nguyên tắc 7, 8, 10, §4.20–4.22, §6, Q05, Q13, Q15, Q19.
- `database_import.js` (v0.6): khối `import_batches`, `import_row_errors`, `scores` (khóa duy nhất `{sectionId, assessmentId, itemId, cloId, studentId}`).
- `adr/0013-import-ctdt-de-cuong-va-nhap-diem-clo-truc-tiep.md`.

## 3. Collection và trường chính

| Collection | Trường chính |
|---|---|
| `import_batches` | `sectionId`, `assessmentId`, `fileName`, `fileChecksum` (SHA-256), `fileRef` (chuỗi, nơi lưu tệp gốc), `sourceSystem` (`EXCEL`/`CSV`/`MOODLE_EXPORT`), `columnMapping`, `emptyCellAs` (`BLANK`/`MISSING`), `reimportPolicy` (`REJECT_ON_CONFLICT`/`OVERWRITE`/`UPDATE_BLANK_ONLY`), `status` (`UPLOADED`/`VALIDATED`/`HAS_ERRORS`/`CONFIRMED`/`ROLLED_BACK`), `stats` (`totalRows`, `validRows`, `errorRows`), `uploadedBy`, `confirmedBy`, `confirmedAt` |
| `import_row_errors` | `batchId`, `rowNumber`, `column`, `errorType` (`INVALID_STUDENT_CODE`, `SCORE_OUT_OF_RANGE`, `DUPLICATE_RECORD`, `MISSING_FIELD`), `rawValue`, `message` |
| `scores` | `sectionId`, `assessmentId`, `itemId`, **`cloId`**, `studentId`, `status`, `rawScore`, `maxScore` (snapshot từ `outcomeMappings[].maxScore`), `importBatchId`, `sourceRow`, `revision`, `updatedBy`, `updatedAt`. Duy nhất `(sectionId, assessmentId, itemId, cloId, studentId)` (đã bỏ `rubricLevel` theo ADR 0013) |

`scores.status ∈ SCORED, BLANK, ABSENT, NOT_GRADED, EXEMPT, MISSING`. Validator: `SCORED`/`BLANK` bắt buộc `rawScore` + `maxScore`; bốn trạng
thái còn lại `rawScore = null`; `BLANK ⇒ rawScore = 0`; `rawScore ≤ maxScore`.

## 4. Quy tắc nghiệp vụ (cập nhật theo ADR 0013)

1. **Thiếu dữ liệu ≠ 0.** Ô trống khi nhập là `BLANK` (đã dự thi, bỏ trống = 0 có ghi nhận) hoặc `MISSING` (thiếu dữ liệu, `null`) **do người nhập
   chọn cho từng lô** (`emptyCellAs`); hệ thống không tự đoán.
2. **Điều kiện nhập:** bài đánh giá phải ở trạng thái `LOCKED` hoặc `SCORE_ENTERED` (`isLocked = true`); học kỳ/lớp chưa khóa (F05a). Vi phạm → từ chối cả lô (422).
3. **Người dùng ngoài phạm vi lớp bị từ chối** (403); giảng viên chỉ nhập cho lớp mình.
4. **Điểm nhập trực tiếp theo từng cặp `(itemId, cloId)`:** Mỗi cột điểm trong tệp (hoặc trên lưới nhập điểm) ánh xạ tới đúng một cặp `(itemId, cloId)` đã khai báo trong `assessment.items[].outcomeMappings`. Nếu một câu hỏi có 2 CLO (ví dụ `Câu 2 - CLO1 (max 2.0)` và `Câu 2 - CLO2 (max 3.0)`), giảng viên nhập điểm riêng cho từng cột; hệ thống **không** tự chia điểm bằng trọng số.
5. **Kiểm tra từng dòng** (4 loại lỗi trong enum): mã sinh viên không có/không ghi danh (`INVALID_STUDENT_CODE`), điểm âm hoặc > `maxScore` của cặp `(itemId, cloId)` đó
   (`SCORE_OUT_OF_RANGE`), trùng `(sectionId, assessmentId, itemId, cloId, studentId)` trong tệp hoặc với dữ liệu đã có (`DUPLICATE_RECORD`), thiếu trường bắt buộc
   (`MISSING_FIELD`).
6. **Checksum SHA-256** của tệp: cùng `(sectionId, assessmentId, fileChecksum)` đã `CONFIRMED` ⇒ cảnh báo/từ chối nhập trùng.
7. **Chính sách nhập lại** khi `(itemId, cloId, studentId)` đã có điểm: cam kết `REJECT_ON_CONFLICT`.
8. **Xác nhận (US-24) là một transaction** nhiều document: cập nhật `import_batches.status = CONFIRMED` (+ `confirmedBy/At`), ghi hàng loạt `scores`, chuyển
   `assessments.status → SCORE_ENTERED` nếu đang ở `LOCKED`, ghi `audit_logs` (`IMPORT_CONFIRM`). Lỗi giữa chừng ⇒ **không để lại điểm nửa vời**. Cần MongoDB replica set.
9. Lô có lỗi (`HAS_ERRORS`) **không xác nhận được**.
10. `scores.maxScore` = `outcomeMappings[].maxScore` của cặp `(itemId, cloId)` **tại thời điểm nhập** (snapshot). `sourceRow` = số dòng trong tệp gốc. `revision` bắt đầu từ 1.
11. **Sửa điểm đơn lẻ (US-25, MR):** dùng khóa lạc quan `revision` (lệch ⇒ 409), **bắt buộc có lý do**, ghi `audit_logs` (`SCORE_UPDATE`) có `before`/`after`, từ chối khi học kỳ/lớp đã khóa. Sau khi sửa, đánh dấu các `calculation_runs` liên quan `isStale = true` (F06).
12. Lưu tệp gốc: `fileRef` chỉ là tham chiếu (đường dẫn/khóa); **nơi lưu là quyết định riêng** (ADR). Không lưu bài làm của sinh viên.

## 5. Stories và acceptance criteria (nháp)

| ID | AC |
|---|---|
| US-21/AC-1 | Tải tệp CSV/Excel hợp lệ cho một lớp + bài `APPROVED` → tạo lô `UPLOADED` với `fileChecksum`, `fileName`, `uploadedBy` |
| US-21/AC-2 | Gửi `columnMapping` (cột → `itemId`/`externalRef`); lưu lại và dùng lại cho lô sau cùng bài |
| US-21/AC-3 | Tải cùng tệp đã `CONFIRMED` (cùng checksum, lớp, bài) → cảnh báo/từ chối nhập trùng |
| US-21/AC-4 | Bài đánh giá chưa `APPROVED`/`SCORE_ENTERED`, hoặc học kỳ khóa → từ chối (422), không tạo lô |
| US-22/AC-1 | Tệp có dòng điểm vượt `maxScore` → `import_row_errors` ghi đúng `rowNumber`, `column`, `SCORE_OUT_OF_RANGE`, `rawValue` |
| US-22/AC-2 | Mã sinh viên lạ hoặc không ghi danh → `INVALID_STUDENT_CODE`; dòng/cột thiếu → `MISSING_FIELD`; trùng → `DUPLICATE_RECORD` |
| US-22/AC-3 | Tệp thử có lỗi cài sẵn: hệ thống phát hiện **100%** đúng dòng/cột (KPI K2 trong bản cam kết), và không báo nhầm dòng đúng |
| US-22/AC-4 | Tệp rỗng, sai cột bắt buộc, sai định dạng → từ chối với thông báo rõ (không 500) |
| US-23/AC-1 | `emptyCellAs = BLANK` → ô trống thành `BLANK`, `rawScore = 0`; `= MISSING` → `MISSING`, `rawScore = null`. Không có lựa chọn ⇒ 400 (không đoán) |
| US-23/AC-2 | `reimportPolicy = REJECT_ON_CONFLICT` và đã có điểm ⇒ lô/dòng bị từ chối, điểm cũ giữ nguyên |
| US-24/AC-1 | Xác nhận lô `VALIDATED` → `CONFIRMED`: toàn bộ `scores`, trạng thái lô, trạng thái bài (nếu lô đầu) và `audit_logs` được ghi cùng nhau |
| US-24/AC-2 | Lỗi giữa chừng (ví dụ ghi điểm thứ k thất bại) → rollback: không có `scores`, lô không `CONFIRMED`, không có `audit_logs` (test trên replica set) |
| US-24/AC-3 | Lô `HAS_ERRORS` không xác nhận được; lô `CONFIRMED` không xác nhận lại |
| US-24/AC-4 | Hủy lô: `ROLLED_BACK`, các `scores` của lô (nếu đã xác nhận) bị gỡ **chỉ khi** chưa có `calculation_runs` chính thức dùng chúng **[ĐỀ XUẤT]** |
| US-25/AC-1 (MR) | Sửa một điểm có lý do + `revision` đúng → cập nhật, `revision + 1`, `audit_logs` có `before`/`after`/`reason` |
| US-25/AC-2 (MR) | `revision` lệch (người khác sửa trước) → 409; thiếu lý do → 400; học kỳ khóa → từ chối |
| Chung | Người dùng ngoài phạm vi lớp → 403; mỗi `scores` truy được `importBatchId` + `sourceRow` |

## 6. API đề xuất **[ĐỀ XUẤT]** (API thực tế: xem §11.2)

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| POST | `/api/sections/{sectionId}/assessments/{assessmentId}/import-batches` (tệp + cấu hình) | LECTURER (lớp mình) |
| GET | `/api/import-batches/{id}`; `GET .../errors?page=` | LECTURER (lớp mình), QA |
| POST | `/api/import-batches/{id}/validate` (nếu tách bước) | LECTURER |
| POST | `/api/import-batches/{id}/confirm`, `/rollback` | LECTURER |
| PUT | `/api/scores/{id}` (MR, kèm `revision`, `reason`) | LECTURER (lớp mình) |

## 7. Kiểm tra tham chiếu ở service

`sectionId`/`assessmentId` tồn tại và cùng nhau (bài thuộc đúng lớp); cột ánh xạ trỏ tới `itemId` có trong bài; `studentId` có `enrollment ENROLLED`
trong lớp; người nhập có phạm vi trên lớp; trạng thái bài/học kỳ/lớp cho phép.

## 8. Test bắt buộc (ca âm và ca biên, tên `US-xx/AC-y`)

Điểm = `maxScore` (hợp lệ), `maxScore + 0,01` (lỗi), âm, `NaN`/chữ; `BLANK` ≠ `MISSING` ≠ `ABSENT` (không ngầm bằng 0); trùng bản ghi trong tệp và với dữ liệu có sẵn;
tệp rỗng, tệp chỉ có tiêu đề, thiếu cột bắt buộc, dòng thừa cột; nhập trùng checksum; mã sinh viên không ghi danh; lô có 1 lỗi trên 1000 dòng; rollback khi lỗi giữa chừng;
hai người xác nhận cùng một lô đồng thời (chỉ một thành công); sửa điểm đồng thời báo xung đột (`revision`); học kỳ `SCORE_LOCKED`; bài `DRAFT`/`SUBMITTED`/`REJECTED`;
người ngoài lớp; lớp 45 ghi danh chỉ 40 sinh viên có điểm (nhập vẫn hợp lệ, 5 người không có `scores`, dữ liệu minh họa). Hiệu năng: ~5.000 dòng trong ≤ 10 giây (NFR đề xuất N1).

## 9. Không làm / chưa quyết

- Không làm: đồng bộ trực tiếp với Moodle (chỉ nhập tệp xuất ra), nhập `TOTAL_ONLY`, lưu bài làm, tự đoán ô trống.
- **Cần ADR trước khi code:** thư viện đọc Excel (ví dụ Apache POI) và CSV; nơi lưu tệp gốc (`fileRef`); lỗi cấp tệp lưu thế nào (4.5).
  Mỗi dependency thêm: lý do + giấy phép + phương án không dùng.
  **Đã viết [ADR 0013](../adr/0013-nhap-diem-luu-tep-goc-loi-cap-tep.md) ở trạng thái NHÁP, chờ sinh viên xác nhận:** chỉ CSV (không thêm dependency),
  tệp gốc lưu trong thư mục cấu hình, lỗi cấp tệp trả 400 và không tạo lô. Excel chưa làm (TD-32).
- Chờ: Q05 (điểm theo câu hỏi — đã trả lời tạm, chờ xác nhận), Q13 (cách phân biệt vắng/chưa chấm/thiếu; vắng có vào mẫu số không), Q16 (nguồn nhập), Q19 (chính sách nhập lại).

## 10. Để vấn đáp

Vì sao xác nhận trong transaction (điểm nửa vời làm sai CLO mà không ai biết); vì sao `BLANK` ≠ `MISSING` (bỏ trống = 0 có ghi nhận, thiếu dữ liệu
thì không được coi là 0); vì sao lưu `sourceRow` và `fileChecksum` (truy vết và chống nhập trùng); vì sao chỉ nhập khi bài `APPROVED` (điểm gắn vào cấu trúc đã khóa);
bỏ `revision` thì hai giảng viên sửa cùng một điểm sẽ ghi đè nhau mà không biết.

## 11. Hiện thực nhập điểm từ tệp (2026-11-10)

US-21, 22, 23, 24 đã có mã trên nhánh `feat/f05b-score-import`. Các quyết định dưới đây là **lựa chọn của người hiện thực, chờ hai sinh viên và
GVHD xác nhận**; mục nào đổi thì sửa mã kèm test tương ứng. Ba quyết định lớn (định dạng, nơi lưu tệp, lỗi cấp tệp) nằm ở
[ADR 0013](../adr/0013-nhap-diem-luu-tep-goc-loi-cap-tep.md) (NHÁP).

### 11.1 Quyết định

| # | Quyết định | Lý do / hệ quả |
|---|---|---|
| D1 | Chỉ CSV (UTF-8, BOM, `,` hoặc `;`, nháy kép, ≤ 5.000 dòng dữ liệu), dùng lại `EnrollmentCsvParser` của F05a. `sourceSystem` nhận `CSV` (mặc định) và `MOODLE_EXPORT`; `EXCEL` → 422 | Không thêm dependency (CLAUDE.md quy tắc 3); Excel chờ ADR (TD-32) |
| D2 | Luồng 3 bước, mỗi bước một API: tải (`UPLOADED`) → kiểm tra (`VALIDATED`/`HAS_ERRORS`) → xác nhận (`CONFIRMED`). Kiểm tra chạy lại được khi lô chưa xác nhận (thay lỗi cũ) | Khớp AC-1 của US-21 ("tạo lô UPLOADED") và tách US-22 khỏi US-21 |
| D3 | Lỗi cấp tệp (rỗng, chỉ tiêu đề, thiếu cột sinh viên/cột đã ánh xạ, tiêu đề trùng, quá 5.000 dòng, `columnMapping` sai) → 400/422, **không tạo lô, không lưu tệp** | ADR 0013; enum `errorType` của validator là đóng, không có loại lỗi cấp tệp |
| D4 | Tệp gốc lưu ở `app.score-import.storage-dir` (mặc định `./data/score-import`), tên `<batchId>.csv`; `fileRef` = tên đó. Bước xác nhận **đọc lại tệp đã lưu**, kiểm tra SHA-256, kiểm tra lại mọi dòng với dữ liệu hiện tại rồi mới ghi | Không cần collection trung gian; điểm ghi đúng cái đã kiểm tra, nhưng vẫn bắt được thay đổi ghi danh/điểm giữa hai bước |
| D5 | Ánh xạ cột: `columnMapping` (JSON trong phần form) = `{studentColumn?, items:[{column, item}]}`; `item` là `_id` hoặc `externalRef` của câu hỏi/tiêu chí, được xác định về `_id` khi lưu. Cột sinh viên mặc định `studentRef`. Không gửi `columnMapping` → dùng ánh xạ của lô gần nhất của cùng bài; chưa có lô nào → 400 | US-21/AC-2. Lưu dạng danh sách vì tên cột có thể chứa dấu chấm (khóa map của MongoDB không cho) |
| D6 | `emptyCellAs` và `reimportPolicy` **bắt buộc**, không có mặc định. `reimportPolicy` ≠ `REJECT_ON_CONFLICT` → 422 | US-23/AC-1; Q19 chưa có mặc định nên không tự chọn |
| D7 | Ô điểm có chữ số = `SCORED` (kể cả `0`); ô tồn tại nhưng trống = theo `emptyCellAs` (`BLANK` → `rawScore` 0, `MISSING` → `null`); **dòng ngắn hơn tiêu đề (ô không tồn tại) là lỗi `MISSING_FIELD`**, không tự hiểu là trống | Thiếu dữ liệu ≠ 0; không đoán |
| D8 | `ABSENT`, `NOT_GRADED`, `EXEMPT` **chưa nhập được qua tệp**: không có cột trạng thái/ký hiệu vì Q13 chưa trả lời. Enum `ScoreStatus` đã đủ 6 giá trị | Không tự quyết quy ước vắng/chưa chấm/miễn; ô chữ không phải số bị báo lỗi chứ không đoán |
| D9 | Loại lỗi khi enum đóng: ô điểm không phải số/âm/> `maxScore` → `SCORE_OUT_OF_RANGE` (kèm `message`); dòng thừa ô hoặc ngắn hơn tiêu đề, mã sinh viên trống → `MISSING_FIELD`; trùng mã sinh viên trong tệp hoặc (sinh viên, câu) đã có điểm → `DUPLICATE_RECORD`; mã lạ/không `ENROLLED` → `INVALID_STUDENT_CODE` | Cần sinh viên xác nhận; muốn tách loại thì đổi enum ở `database_import.js` cùng lúc |
| D10 | Điểm hợp lệ: số thập phân dạng `[+-]?\d{1,9}([.,]\d{1,6})?` (dấu phẩy là dấu thập phân), `0 ≤ điểm ≤ maxScore` so bằng `BigDecimal` (4,01 > 4). Không nhận ký hiệu khoa học, `NaN`, `Infinity` | Kiểm tra chính xác biên `maxScore + 0,01` |
| D11 | Điều kiện nhập (kiểm ở cả tải, kiểm tra, xác nhận): bài `APPROVED`/`SCORE_ENTERED`, `dataGranularity = ITEM`, lớp không `LOCKED`, học kỳ `OPEN` (`SCORE_LOCKED`/`CLOSED` → 422). Lớp `CLOSED` vẫn nhập được (`CLOSED` chỉ đóng băng ghi danh) | Quy tắc 2; hoàn thành vế "từ chối nhập điểm" của US-18/AC-2 |
| D12 | Trùng checksum chỉ tính với lô `CONFIRMED` cùng (lớp, bài) → 409. Lô chưa xác nhận/đã hủy không chặn tải lại | US-21/AC-3 ("đã CONFIRMED") |
| D13 | Lô `HAS_ERRORS` không xác nhận được; **không** xác nhận phần hợp lệ. Muốn nhập tiếp thì sửa tệp rồi tải lô mới | Quy tắc 9, chọn mặc định an toàn |
| D14 | Xác nhận = một `@Transactional`: ghi lô `CONFIRMED` (khóa lạc quan `revision`) → `insert` các `scores` (`revision = 1`, `maxScore` snapshot, `sourceRow`, `importBatchId`) → bài `APPROVED` → `SCORE_ENTERED` (chỉ lô đầu; `isLocked` giữ `true`) → `audit_logs` `IMPORT_CONFIRM`. Hai người xác nhận đồng thời: một thành công, người kia 409/422 | US-24; đã kiểm chứng rollback trên replica set (chèn lỗi giữa chừng) |
| D15 | Hủy lô (`/rollback`) chỉ cho `UPLOADED`/`VALIDATED`/`HAS_ERRORS`. Lô `CONFIRMED` → 422 (**chưa làm** gỡ điểm đã xác nhận, TD-36) | Gỡ điểm cần quyết định `audit_logs.action` (enum đóng, TD-31) và kiểm tra `calculation_runs` chính thức (F06 chưa có) |
| D16 | Phân quyền: ghi (tải, kiểm tra, xác nhận, hủy) = `hasRole('LECTURER')` **và** `canAccessSection(WRITE)`/`canAccessImportBatch(WRITE)`; ADMIN **không** được ghi (đúng plan §6, giống F04). Đọc = quyền đọc lớp (LECTURER của lớp, ADMIN, PROGRAM_MANAGER trong scope, QA) | Plan §6 "LECTURER (lớp mình)"; id lô lạ → 403 (fail-closed, như TD-22) |
| D17 | `audit_logs` `IMPORT_CONFIRM`: `entity = {import_batches, <id>}`, `before.status = VALIDATED`, `after = {status, scoreCount, assessmentId, assessmentStatus}`, không `reason`; `userId` là `ObjectId` của người xác nhận | `entity.id` lưu thành `entity._id` (TD-34) |

### 11.2 API thực tế (tiền tố `/api/v1`, JSON; lỗi theo `ErrorResponse` của US-40)

| Phương thức | Đường dẫn | Quyền | Ghi chú |
|---|---|---|---|
| POST | `/sections/{sectionId}/assessments/{assessmentId}/import-batches` | LECTURER của lớp | `multipart/form-data`: `file` (CSV), `emptyCellAs` (`BLANK`\|`MISSING`, bắt buộc), `reimportPolicy` (bắt buộc, chỉ `REJECT_ON_CONFLICT`), `sourceSystem` (`CSV`\|`MOODLE_EXPORT`, tùy chọn), `columnMapping` (chuỗi JSON, tùy chọn). 201 `ImportBatchResponse` (`UPLOADED`) |
| GET | `/sections/{sectionId}/assessments/{assessmentId}/import-batches` | đọc lớp | `ImportBatchResponse[]`, mới nhất trước |
| GET | `/import-batches/{id}` | đọc lớp của lô | `ImportBatchResponse` |
| GET | `/import-batches/{id}/errors?page=0&size=20` | đọc lớp của lô | `PageResponse<ImportRowErrorResponse>` sắp theo `rowNumber` (size 1..100) |
| POST | `/import-batches/{id}/validate` | LECTURER của lớp | 200 `ImportBatchResponse` (`VALIDATED` hoặc `HAS_ERRORS` + `stats`) |
| POST | `/import-batches/{id}/confirm` | LECTURER của lớp | 200 `ImportBatchResponse` (`CONFIRMED`); 422 nếu không phải `VALIDATED` hoặc dữ liệu đã đổi; 409 nếu xung đột đồng thời |
| POST | `/import-batches/{id}/rollback` | LECTURER của lớp | 200 `ImportBatchResponse` (`ROLLED_BACK`); 422 nếu đã `CONFIRMED`/đã hủy |

`ImportBatchResponse`: `id, sectionId, assessmentId, fileName, fileChecksum, fileRef, sourceSystem, columnMapping{studentColumn, items[{column, item(=_id)}]},
emptyCellAs, reimportPolicy, status, stats{totalRows, validRows, errorRows, scoreCount}|null, uploadedBy, confirmedBy, confirmedAt, createdAt`.
`ImportRowErrorResponse`: `rowNumber, column|null, errorType, rawValue|null, message`. Mã lỗi: 400 (thiếu tham số, tệp/ánh xạ sai), 401, 403, 404
(bài không thuộc lớp), 409 (tệp đã CONFIRMED, xung đột đồng thời), 422 (điều kiện nhập, trạng thái lô, `reimportPolicy`/EXCEL chưa hỗ trợ).
Giới hạn kích thước tệp là mặc định của Spring (`spring.servlet.multipart.max-file-size` = 1 MB); chưa chỉnh.

### 11.3 AC ↔ test

Viết tắt lớp test (`src/test/java/com/hcmute/lms/lmsapi/…`): `UIT` = `controller/ImportBatchUploadIntegrationTest`, `VIT` = `controller/ImportBatchValidateIntegrationTest`,
`CIT` = `controller/ImportBatchConfirmIntegrationTest`, `RIT` = `integration/ImportConfirmRollbackIntegrationTest`, `PIT` = `integration/ImportBatchPerformanceIntegrationTest`,
`SRV` = `service/impl/ScoreRowValidatorTest`, `SVC` = `service/impl/ImportBatchServiceImplTest`, `CSV` = `util/ScoreCsvParserTest`, `LFS` = `service/impl/LocalImportFileStorageTest`,
`CHK` = `util/ChecksumUtilsTest`, `SCA` = `service/ImportBatchScopeAuthorizationTest`.

| AC | Trạng thái | Test |
|---|---|---|
| US-21/AC-1 | Có mã, có test | `UIT#us21_ac1_uploadCreatesUploadedBatch`; `LFS#saveThenLoadRoundTrips`; `CHK#matchesPublishedSha256Vectors`; `SVC#fileNameIsSanitised`, `#createBatchDeletesStoredFileWhenSavingTheBatchFails` |
| US-21/AC-2 | Có | `UIT#us21_ac2_columnMappingIsStoredAndReused`, `#us21_ac2_customColumnNames`, `#us21_ambiguousExternalRef` |
| US-21/AC-3 | Có | `UIT#us21_ac3_sameConfirmedFileIsRejected`, `#us21_ac3_sameFileAllowedWhenPreviousWasNotConfirmed`, `#us21_ac3_checksumIsScopedToSectionAndAssessment` |
| US-21/AC-4 (và US-18/AC-2 vế nhập điểm) | Có | `UIT#us21_ac4_assessmentNotOpenForScores` (DRAFT/SUBMITTED/REJECTED/LOCKED), `#us21_ac4_scoreEnteredAllowedTotalOnlyRejected`, `#us21_ac4_termLockedBlocksImport`, `#us21_ac4_sectionLockedBlocksImport`, `#us21_assessmentMustBelongToSection`; `VIT#us22_validateRespectsStateAndLocks`; `CIT#us24_confirmRechecksTermLock` |
| US-22/AC-1 | Có | `SRV#scoreJustAboveMaxIsReported`, `#invalidNumbersAreRejected`, `#scoresAtBoundaryAreValid`; `VIT#us22_errorsAreReportedAtTheExactRowAndColumn` |
| US-22/AC-2 | Có | `SRV#studentCodeErrors`, `#shortAndLongRowsAreMissingField`, `#allErrorsOfARowAreReported`; `VIT#us22_errorsAreReportedAtTheExactRowAndColumn`, `#us22_errorsArePaged`, `#us22_validFileAndRevalidateIsIdempotent`, `#us22_revalidateSeesNewEnrollment` |
| US-22/AC-3 (100% đúng dòng/cột, không báo nhầm) | Có (tệp thử 8 dòng, 8 lỗi cài sẵn; biên 1 lỗi/1.000 dòng) — **chưa phải KPI K2 đo trên tệp thật** | `VIT#us22_errorsAreReportedAtTheExactRowAndColumn`; `SRV#oneErrorInAThousandRows` |
| US-22/AC-4 | Có | `UIT#us22_ac4_fileLevelErrorsCreateNoBatch`; `CSV#emptyOrHeaderOnlyFilesAreRejected`, `#duplicateHeadersAreRejected`, `#rowLimitBoundary`; `SVC#validateDetectsTamperedStoredFile` |
| US-23/AC-1 | Có | `UIT#us23_ac1_emptyCellPolicyIsMandatory`; `SRV#emptyCellFollowsChosenPolicy`; `VIT#us23_ac1_blank`, `#us23_ac1_missing`, `#us23_ac1_explicitZeroIsScoredNotBlank` |
| US-23/AC-2 | Có | `UIT#us23_ac2_onlyRejectOnConflictAndNoExcel`; `SRV#existingScoreIsRejectedByPolicy`; `VIT#us23_ac2_conflictIsRejectedAndOldScoresStay`; `CIT#us24_secondBatchWithSameKeysIsRejectedAfterFirstConfirm` |
| US-24/AC-1 | Có | `CIT#us24_ac1_confirmWritesEverythingTogether`, `#us24_ac1_onlyFirstBatchChangesAssessmentStatus`, `#studentsAbsentFromFileHaveNoScores`, `#us24_confirmRechecksEnrollment`; `PIT` (5.000 dòng) |
| US-24/AC-2 (rollback trên replica set) | Có | `RIT#us24_ac2_failureWhileWritingScoresRollsEverythingBack`, `#us24_ac2_failureAtTheLastStepRollsEverythingBack`, `#us24_ac2_batchCanBeConfirmedAfterARolledBackAttempt`; `CIT#us24_concurrentConfirmOnlyOneWins` |
| US-24/AC-3 | Có | `CIT#us24_ac3_onlyValidatedBatchCanBeConfirmed` |
| US-24/AC-4 (hủy lô) | **Một phần**: hủy lô chưa xác nhận có; gỡ điểm của lô đã xác nhận **chưa làm** (TD-36) | `CIT#us24_ac4_rollbackOnlyUnconfirmedBatches` |
| US-25/AC-1, AC-2 | **Chưa làm** (mở rộng) | — |
| Chung (403 ngoài lớp; truy vết `importBatchId` + `sourceRow`) | Có | `UIT#authorizationOnUpload`; `VIT#authorizationOnBatchEndpoints`; `CIT#authorizationOnConfirmAndRollback`; `SCA`; `CIT#us24_ac1_confirmWritesEverythingTogether` |

### 11.4 Chưa làm và câu hỏi còn mở

- **US-25** (sửa điểm đơn lẻ, `SCORE_UPDATE`, khóa lạc quan `revision`): chưa làm.
- `OVERWRITE`, `UPDATE_BLANK_ONLY`: chờ Q19 (bị từ chối 422). Chưa có mặc định `reimportPolicy`.
- Vắng/chưa chấm/miễn (`ABSENT`, `NOT_GRADED`, `EXEMPT`) và việc "vắng có vào mẫu số không": chờ Q13.
- Excel: chờ ADR (TD-32). Gỡ điểm lô đã xác nhận: TD-36. Đồng bộ Moodle, `TOTAL_ONLY`: không làm.
- Q16 (nguồn nhập): hiện nhận CSV từ bất kỳ nguồn nào; `MOODLE_EXPORT` chỉ là nhãn, không có phân tích cấu trúc riêng của tệp Moodle.
- Nơi lưu tệp trên staging/production và thời hạn giữ tệp: xem câu hỏi mở trong ADR 0013.
