# F08 — Theo dõi hành động cải tiến (đóng vòng)

Story: **US-37** tạo vấn đề và hành động cải tiến (C), **US-38** theo dõi trạng thái OPEN → IN_PROGRESS → DONE → VERIFIED (C),
**NEW-02** đánh giá hiệu quả ở chu kỳ đo kế tiếp (MR). Giai đoạn P5. Chủ trì đề xuất: IBS (nội dung) + IT. Package: `improvement`.
Phụ thuộc: `00`, `F01`, `F06`, `F07` (gắn với kết quả/báo cáo).

## 1. Mục tiêu

Đóng vòng "đo lường → phát hiện vấn đề → hành động → đo lại": mỗi chỗ chưa đạt có thể trở thành một hành động cải tiến có người phụ trách, hạn, chỉ tiêu kỳ vọng, lịch sử trạng thái, và
(MR) được đánh giá hiệu quả bằng một lượt tính sau đó.

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` nguyên tắc 1 và 10, §4.28, §5 bước 5.
- `database_import.js`: khối `improvement_actions`.

## 3. Collection `improvement_actions`

| Trường | Ghi chú |
|---|---|
| `programVersionId` | bắt buộc; `courseId` (null được) |
| `issue` | `{ level (CLO/PI/PLO), subjectId, reportId, aggregateResultId, observedPassRate (0–1), description, rootCause }`; bắt buộc `level`, `subjectId` |
| `actionPlan`, `expectedTarget` (0–1) | kế hoạch và chỉ tiêu kỳ vọng |
| `ownerId` (bắt buộc), `targetTermId`, `dueDate`, `priority` (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`) | |
| `status` | `OPEN`, `IN_PROGRESS`, `DONE`, `VERIFIED`, `CANCELLED` |
| `evaluation` | (MR) `{ evaluationRunId, newPassRate, isEffective, evaluatedBy, evaluatedAt, completionNote }` |
| `history[]` | `{ status, changedBy, changedAt, note }` |

Chỉ mục: `(programVersionId, status)`, `ownerId`, `targetTermId`.

## 4. Quy tắc nghiệp vụ

1. Hành động **gắn với một kết quả cụ thể** (CLO/PI/PLO + `subjectId`), ưu tiên có `aggregateResultId`/`reportId` để truy ngược; `observedPassRate` lấy từ kết quả đó, **không nhập tay tùy ý** khi đã có `aggregateResultId`.
2. `ownerId` là người dùng tồn tại và có quyền trên phạm vi **[ĐỀ XUẤT]**; `targetTermId` tồn tại.
3. **Chuyển trạng thái:** `OPEN → IN_PROGRESS → DONE → VERIFIED`; `CANCELLED` từ `OPEN`/`IN_PROGRESS` kèm lý do. Không nhảy cóc, không quay lui **[ĐỀ XUẤT]**. Mỗi lần chuyển thêm một phần tử `history[]`
   (`status`, `changedBy`, `changedAt`, `note`); không sửa/xóa phần tử cũ.
4. `VERIFIED` do người **khác** người phụ trách xác nhận **[ĐỀ XUẤT]** và kèm ghi chú hoàn thành.
5. `expectedTarget` và `observedPassRate` ∈ [0, 1]; giá trị mục tiêu là *chỉ tiêu do người dùng đặt*, không do hệ thống suy.
6. **Đánh giá hiệu quả (NEW-02, MR):** `evaluationRunId` phải là một `calculation_runs` `SUCCESS` có `startedAt` **sau** thời điểm hành động `DONE`, cùng loại đối tượng (level/subject). `newPassRate` lấy từ
   `aggregate_results` của lượt đó; `isEffective` so với `expectedTarget`. **Không kết luận hiệu quả từ lượt tính không chính thức** hoặc `isStale` mà không ghi rõ giới hạn.
7. Dữ liệu cải tiến **không được sinh bởi ML**; mọi con số hệ thống chỉ trích từ kết quả đã tính.
8. Không có dữ liệu sinh viên thật trong mô tả; dùng `studentRef` nếu cần nhắc tới cá nhân (nên tránh).

## 5. Stories và acceptance criteria (nháp)

| ID | AC |
|---|---|
| US-37/AC-1 | Tạo hành động từ một `aggregate_results` chưa đạt mục tiêu: `issue.level`, `subjectId`, `aggregateResultId`, `observedPassRate` được điền từ kết quả; người dùng nhập `description`, `rootCause`, `actionPlan`, `expectedTarget`, `ownerId`, `dueDate`, `priority` |
| US-37/AC-2 | Thiếu `issue.level`/`subjectId`/`ownerId`/`programVersionId` ⇒ 400; `level` ngoài CLO/PI/PLO ⇒ 400; `expectedTarget` ngoài [0,1] ⇒ 400 |
| US-37/AC-3 | `aggregateResultId`/`reportId`/`ownerId`/`targetTermId` không tồn tại ⇒ 422; `subjectId` không khớp với kết quả đã chọn ⇒ 422 |
| US-37/AC-4 | Vai trò không được phép (ví dụ `VIEWER`, `LECTURER` ngoài phạm vi) ⇒ 403 |
| US-38/AC-1 | Chuyển `OPEN → IN_PROGRESS → DONE → VERIFIED` hợp lệ; mỗi lần thêm `history[]` đúng người/thời điểm/ghi chú |
| US-38/AC-2 | Chuyển sai thứ tự (ví dụ `OPEN → DONE`, `VERIFIED → OPEN`) ⇒ 422 |
| US-38/AC-3 | `CANCELLED` cần lý do; hành động đã `VERIFIED` không hủy được |
| US-38/AC-4 | Danh sách lọc theo `programVersionId`, `status`, `ownerId`, `targetTermId` |
| NEW-02/AC-1 (MR) | Gắn `evaluationRunId` hợp lệ ⇒ điền `newPassRate`, `isEffective`, `evaluatedBy/At`; lượt tính trước thời điểm `DONE` hoặc khác đối tượng ⇒ 422 |
| NEW-02/AC-2 (MR) | Lượt tính không chính thức ⇒ lưu được nhưng `completionNote`/cờ ghi rõ "chưa chính thức" |

## 6. API đề xuất **[ĐỀ XUẤT]**

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| POST | `/api/improvement-actions` | PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER |
| GET | `/api/improvement-actions`, `/api/improvement-actions/{id}` (lọc theo trạng thái, chủ trì, học kỳ) | theo phạm vi |
| PUT | `/api/improvement-actions/{id}` (sửa nội dung khi chưa `VERIFIED`) | như trên |
| POST | `/api/improvement-actions/{id}/transitions` `{ toStatus, note }` | chủ trì / vai trò được phép |
| POST | `/api/improvement-actions/{id}/evaluation` (MR) | PROGRAM_MANAGER, QA_OFFICER |

## 7. Kiểm tra tham chiếu ở service

`programVersionId`, `courseId`, `subjectId` (đúng collection theo `level`), `reportId`, `aggregateResultId`, `ownerId`, `targetTermId`, `evaluationRunId` tồn tại; `aggregateResultId` thuộc cùng `programVersionId`.

## 8. Test bắt buộc

Mọi chuyển trạng thái hợp lệ và không hợp lệ; `history[]` chỉ thêm, không sửa; `expectedTarget` biên (0, 1, 1,01, âm); thiếu trường bắt buộc; tham chiếu mồ côi;
người ngoài phạm vi; `VERIFIED` bởi chính người phụ trách (nếu chốt quy tắc 4); hai người chuyển trạng thái đồng thời (một thắng); đánh giá với lượt tính cũ hơn/không cùng đối tượng.

## 9. Không làm / chưa quyết

- Không làm: ML gợi ý hành động, tự động tạo hành động không có người duyệt, gửi email nhắc việc (`spring-boot-starter-mail` chưa có lý do dùng; nếu cần thì ADR riêng).
- Chờ: GVHD/IBS xác nhận F08 nằm trong cam kết (kế hoạch xếp M; `chot-feature-do-an.md` đề xuất C cho US-37, 38 và MR cho NEW-02); danh mục nguyên nhân gốc (`rootCause`) có chuẩn hóa không; ai được xác nhận `VERIFIED`.

## 10. Để vấn đáp

Vì sao có F08 (đo mà không hành động thì chưa "đóng vòng" CQI); vì sao `history[]` chỉ thêm (lịch sử là minh chứng); vì sao đánh giá hiệu quả cần một lượt tính **sau** hành động (so sánh cùng đại lượng ở hai chu kỳ);
bỏ liên kết tới `aggregateResultId` thì không còn biết hành động nhắm vào con số nào.
