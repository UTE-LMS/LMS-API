# F05b — Nhập điểm từ tệp

Story: **US-21** tải tệp + ánh xạ cột (C), **US-22** kiểm tra, báo lỗi theo dòng/cột (C), **US-23** chọn BLANK/MISSING + chính
sách nhập lại (C; chỉ `REJECT_ON_CONFLICT` cam kết), **US-24** xác nhận trong một transaction hoặc rollback (C), **US-25** sửa
điểm đơn lẻ có lý do (MR). Giai đoạn P3. Chủ trì đề xuất: IT (IBS cho cách hiểu ô trống). Package: `scoreimport`.
Phụ thuộc: `00` (transaction, audit), `F01`, `F04` (bài `APPROVED`), `F05a` (lớp, sinh viên, ghi danh).
**Đây là mô-đun lõi** (coverage ≥ 70%, cả hai sinh viên phải giải thích được).

## 1. Mục tiêu

Tiếp nhận điểm **có sẵn sau khi thi** từ tệp CSV/Excel (xuất từ Moodle hoặc bảng tính), kiểm tra, báo lỗi chính xác theo dòng/cột,
cho người dùng xác nhận, rồi ghi `scores` **trong một transaction** hoặc không ghi gì. Mọi điểm truy vết được về lô nhập và dòng
tệp gốc.

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` nguyên tắc 7, 10, §4.20–4.22, §6, Q05, Q13, Q19.
- `database_import.js`: khối `import_batches`, `import_row_errors`, `scores` (validator `oneOf` theo `status`).
- `PLO_DB_UPDATE.md` §5 mục 1–2 (ranh giới transaction; chỉ nhập điểm khi bài `APPROVED`/`SCORE_ENTERED`).

## 3. Collection và trường chính

| Collection | Trường chính |
|---|---|
| `import_batches` | `sectionId`, `assessmentId`, `fileName`, `fileChecksum` (SHA-256), `fileRef` (chuỗi, nơi lưu tệp gốc), `sourceSystem` (`EXCEL`/`CSV`/`MOODLE_EXPORT`), `columnMapping`, `emptyCellAs` (`BLANK`/`MISSING`), `reimportPolicy` (`REJECT_ON_CONFLICT`/`OVERWRITE`/`UPDATE_BLANK_ONLY`), `status` (`UPLOADED`/`VALIDATED`/`HAS_ERRORS`/`CONFIRMED`/`ROLLED_BACK`), `stats` (`totalRows`, `validRows`, `errorRows`), `uploadedBy`, `confirmedBy`, `confirmedAt` |
| `import_row_errors` | `batchId`, `rowNumber`, `column`, `errorType` (`INVALID_STUDENT_CODE`, `SCORE_OUT_OF_RANGE`, `DUPLICATE_RECORD`, `MISSING_FIELD`), `rawValue`, `message` |
| `scores` | `sectionId`, `assessmentId`, `itemId`, `studentId`, `status`, `rawScore`, `maxScore` (snapshot), `rubricLevel` (1–4, cho CRITERION), `importBatchId`, `sourceRow`, `revision`, `updatedBy`, `updatedAt`. Duy nhất `(sectionId, assessmentId, itemId, studentId)` |

`scores.status ∈ SCORED, BLANK, ABSENT, NOT_GRADED, EXEMPT, MISSING`. Validator: `SCORED`/`BLANK` bắt buộc `rawScore` + `maxScore`; bốn trạng
thái còn lại `rawScore = null`; `BLANK ⇒ rawScore = 0`; `rawScore ≤ maxScore`.

## 4. Quy tắc nghiệp vụ

1. **Thiếu dữ liệu ≠ 0.** Ô trống khi nhập là `BLANK` (đã dự thi, bỏ trống = 0 có ghi nhận) hoặc `MISSING` (thiếu dữ liệu, `null`) **do người nhập
   chọn cho từng lô** (`emptyCellAs`); hệ thống không tự đoán. `ABSENT`, `NOT_GRADED`, `EXEMPT` đến từ cột trạng thái trong tệp hoặc quy ước ánh xạ **[CHỜ Q13]**; không suy từ ô trống.
2. **Điều kiện nhập:** bài đánh giá phải `APPROVED` hoặc `SCORE_ENTERED`; học kỳ/lớp chưa khóa (F05a); `dataGranularity = ITEM`. Vi phạm → từ chối cả lô.
3. **Người dùng ngoài phạm vi lớp bị từ chối** (403); giảng viên chỉ nhập cho lớp mình.
4. **Ánh xạ cột:** cột tệp ↔ `items._id` hoặc `items.externalRef`; lưu `columnMapping` trong lô để dùng lại. Cột sinh viên ↔ `studentRef` (chỉ sinh viên **đang ghi danh** lớp đó).
5. **Kiểm tra từng dòng** (4 loại lỗi trong enum): mã sinh viên không có/không ghi danh (`INVALID_STUDENT_CODE`), điểm âm hoặc > `maxScore`
   (`SCORE_OUT_OF_RANGE`), trùng `(sectionId, assessmentId, itemId, studentId)` trong tệp hoặc với dữ liệu đã có (`DUPLICATE_RECORD`), thiếu trường bắt buộc
   (`MISSING_FIELD`). Lỗi cấp tệp (rỗng, sai định dạng, thiếu cột bắt buộc) chưa nằm trong enum: trả 400 và không tạo lô, hoặc tạo lô `HAS_ERRORS` với 1 dòng lỗi `rowNumber = 0` **[ĐỀ XUẤT, chọn một và ghi lại]**.
6. **Checksum SHA-256** của tệp: cùng `(sectionId, assessmentId, fileChecksum)` đã `CONFIRMED` ⇒ cảnh báo nhập trùng (từ chối hoặc hỏi lại).
7. **Chính sách nhập lại** khi `(item, student)` đã có điểm: cam kết `REJECT_ON_CONFLICT` (từ chối cả lô hoặc dòng đó, ghi `DUPLICATE_RECORD`). `OVERWRITE`, `UPDATE_BLANK_ONLY` là MR **[CHỜ Q19]**.
8. **Xác nhận (US-24) là một transaction** nhiều document: cập nhật `import_batches.status = CONFIRMED` (+ `confirmedBy/At`), ghi hàng loạt `scores`, chuyển
   `assessments.status → SCORE_ENTERED` nếu đây là lô đầu, ghi `audit_logs` (`IMPORT_CONFIRM`). Lỗi giữa chừng ⇒ **không để lại điểm nửa vời**. Cần MongoDB replica set.
9. Lô có lỗi (`HAS_ERRORS`) **không xác nhận được**. **[ĐỀ XUẤT]** hoặc sửa tệp rồi tải lại (lô mới), hoặc xác nhận phần hợp lệ khi người dùng chọn rõ — mặc định: không cho xác nhận.
10. `scores.maxScore` = `items.maxScore` **tại thời điểm nhập** (snapshot). `sourceRow` = số dòng trong tệp gốc. `revision` bắt đầu từ 1.
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

## 6. API đề xuất **[ĐỀ XUẤT]**

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| POST | `/api/sections/{sectionId}/assessments/{assessmentId}/import-batches` (tệp + cấu hình) | LECTURER (lớp mình) |
| GET | `/api/import-batches/{id}`; `GET .../errors?page=` | LECTURER (lớp mình), QA_OFFICER |
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
- Chờ: Q05 (điểm theo câu hỏi — đã trả lời tạm, chờ xác nhận), Q13 (cách phân biệt vắng/chưa chấm/thiếu; vắng có vào mẫu số không), Q16 (nguồn nhập), Q19 (chính sách nhập lại).

## 10. Để vấn đáp

Vì sao xác nhận trong transaction (điểm nửa vời làm sai CLO mà không ai biết); vì sao `BLANK` ≠ `MISSING` (bỏ trống = 0 có ghi nhận, thiếu dữ liệu
thì không được coi là 0); vì sao lưu `sourceRow` và `fileChecksum` (truy vết và chống nhập trùng); vì sao chỉ nhập khi bài `APPROVED` (điểm gắn vào cấu trúc đã khóa);
bỏ `revision` thì hai giảng viên sửa cùng một điểm sẽ ghi đè nhau mà không biết.
