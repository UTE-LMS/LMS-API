# F04 — Bài đánh giá, rubric, duyệt ma trận đề thi, ngân hàng câu hỏi

Story: **US-15** bài đánh giá khung và bản lớp (C), **US-16** tiêu chí rubric + mức 1–4 (C), **NEW-01** duyệt ma trận đề
thi (C, **[CHỜ GVHD, Q11]**), **US-13** ngân hàng + cây danh mục 3 cấp (MR), **US-14** câu hỏi nhiều cặp trong ngân hàng
(đề xuất MR; kế hoạch v0.3 xếp M), **US-17** nhập câu hỏi từ Excel/Moodle (K). Giai đoạn P3. Chủ trì đề xuất: IBS + IT.
Package: `assessment`. Phụ thuộc: `00`, `F01`, `F02`, `F03` (cần `clos`, `clo_pi_links`); `F05a` cho bản lớp (`course_sections`).

## 1. Mục tiêu

Khai báo **cấu trúc bài đã thi**: có những câu hỏi/tiêu chí nào, điểm tối đa, mức Bloom, đo cặp (CLO, PI) nào với phần điểm bao
nhiêu. Đây là "bản đồ" để quy điểm lên chuẩn đầu ra. Bản lớp phải được **duyệt ma trận** rồi mới mở cổng nhập điểm, và bị
**khóa `items[]`** sau đó. Không sinh đề, không duyệt *nội dung* câu hỏi.

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` §0, §1 (phạm vi), nguyên tắc 8 và 12, §4.12–4.15, §6, Q11.
- `database_import.js`: khối `question_banks`, `question_categories`, `questions`, `assessments` (có 4 điều kiện `$expr`).
- `PLO_DB_UPDATE.md` §3.15 và §5 (chỉ dẫn khóa cấu trúc đề).
- `adr/0001-duyet-ma-tran-de-thi.md` (phương án A/B/C cho NEW-01).

## 3. Collection `assessments` (lõi của feature)

| Trường | Ghi chú |
|---|---|
| `syllabusId`, `code`, `name` | `code` duy nhất trong `(syllabusId, sectionId)` |
| `scope` | `SYLLABUS_TEMPLATE` (khung theo đề cương) hoặc `SECTION` (bản lớp, **bắt buộc có `sectionId`**) |
| `sectionId`, `templateId` | `templateId` trỏ khung mẫu (nếu bản lớp sao chép từ khung) |
| `type` | `QUIZ`, `PRACTICAL`, `PROJECT`, `MIDTERM`, `FINAL`, `OTHER` |
| `phase` | `FORMATIVE`, `MIDTERM`, `SUMMATIVE` |
| `totalPoints`, `weightInCourse` (0–1) | `totalPoints` phải khớp Σ `items.maxScore` (dung sai 0,001) |
| `dataGranularity` | `ITEM` (mặc định) hoặc `TOTAL_ONLY` — `TOTAL_ONLY` **không đo được CLO/PI** |
| `status` | `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `SCORE_ENTERED`, `LOCKED` |
| `isLocked` | `true` khi `status ∈ {APPROVED, SCORE_ENTERED, LOCKED}` (validator bắt buộc khớp) |
| `approval` | `{ submittedBy, submittedAt, reviewedBy, reviewedAt, reviewNote, history[] }`; `history[].action ∈ SUBMIT, APPROVE, REJECT, UNLOCK` |
| `coverageSummary` | `{ totalItemsSum, byClo[{cloId, points, percentage}], byBloom[{bloomLevel, questionCount, points, percentage}] }` |
| `exportedFiles` | **không có tính năng** (xuất phôi in là khâu tổ chức thi) |
| `items[]` | `_id`, `code`, `orderIndex`, `title`, `kind` (`QUESTION`/`CRITERION`), `maxScore` (> 0), `bloomLevel`, `topic`, `questionId`/`questionVersion` (null được), `externalRef` (chuỗi, mã câu trên Moodle để khớp cột điểm), `outcomeMappings[]` (≥ 1), `rubric` |
| `outcomeMappings[]` | `{ cloId, piId, weight }`, `weight` ∈ (0, 1], **Σ weight = 1** trong một item, không trùng cặp |

Chỉ mục: duy nhất `(syllabusId, sectionId, code)`; `(sectionId, status)`; `items.outcomeMappings.cloId`/`piId`; `items.questionId`.
Validator ở MongoDB đã ép: bản `SECTION` có `sectionId`; `isLocked` khớp `status`; từ `SUBMITTED` trở đi có ≥ 1 item; Σ weight = 1 và
không trùng cặp. **Service vẫn phải kiểm tra trước** để trả lỗi rõ ràng (không để lộ lỗi 121 của MongoDB).

## 4. Quy tắc nghiệp vụ

1. **Bản lớp là SNAPSHOT:** `items[].outcomeMappings` sao chép tại thời điểm thi; đổi `clo_pi_links` sau đó không làm đổi bài đã thi.
2. Mỗi cặp (`cloId`, `piId`) trong `outcomeMappings` phải **có trong `clo_pi_links` của chính CLO đó**, và CLO thuộc `syllabusId` của bài. Chạy tính chính thức (F06) yêu cầu liên kết `VERIFIED`.
3. `Σ weight` của một item = 1 (sai số 0,0001). Không gán nguyên điểm một câu cho mọi chuẩn. Cách chia chờ Khoa (Q15): giữ ở dạng dữ liệu, không rải logic cứng.
4. `rubric.levels[].level` ∈ 1–4 là **mức rubric**, tách khỏi `bloomLevel` (1–6). Không quy đổi lẫn nhau.
5. **Luồng trạng thái (NEW-01) [ĐỀ XUẤT, chờ Q11]:**
   `DRAFT → SUBMITTED` (giảng viên gửi; cần ≥ 1 item và `|totalPoints − Σ maxScore| < 0,001`);
   `SUBMITTED → APPROVED | REJECTED` (Trưởng bộ môn; `REJECTED` kèm `reviewNote`); `REJECTED → DRAFT` (sửa lại);
   `APPROVED → SCORE_ENTERED` (tự động khi lô điểm đầu tiên được xác nhận, F05b); `SCORE_ENTERED → LOCKED` (khóa sổ);
   `UNLOCK` (mở khóa) chỉ khi chưa có điểm và có lý do **[ĐỀ XUẤT]**. Mỗi chuyển trạng thái ghi `approval.history[]` và `audit_logs`
   (`ASSESSMENT_APPROVE`/`ASSESSMENT_REJECT`). Chuyển trạng thái sai thứ tự → 422.
6. **Khóa cấu trúc:** khi `isLocked = true` mọi API đổi `items[]` (thêm/sửa/xóa/đổi `maxScore`/`outcomeMappings`) bị từ chối (409/422).
   Phải đổi `isLocked` và `status` **cùng một lần ghi** (validator bắt buộc khớp).
7. **Chỉ nhập điểm khi `APPROVED` hoặc `SCORE_ENTERED`** (F05b kiểm tra; F04 cung cấp trạng thái).
8. `coverageSummary` do service tính từ `items` (độ phủ theo CLO và Bloom), lưu lại để màn hình duyệt nhanh đọc; không để người dùng nhập tay.
9. Chỉ `DEPARTMENT_HEAD` (đúng phạm vi) duyệt/từ chối; người gửi duyệt **không tự duyệt** bài của mình.
10. Nếu GVHD không đồng ý bước Trưởng bộ môn (phương án B của ADR 0001): giảng viên tự xác nhận `DRAFT → APPROVED`, giữ khóa `items`.
11. `dataGranularity = TOTAL_ONLY`: cho lưu nhưng báo rõ không đo được CLO/PI; nếu dữ liệu thật chỉ có điểm tổng phải đổi phạm vi bằng ADR trước K1 (R2).

## 5. Stories và acceptance criteria (nháp)

| ID | AC |
|---|---|
| US-15/AC-1 | Giảng viên tạo bài `SECTION` cho lớp mình, sao chép từ khung (`templateId`) hoặc tự khai báo `items` → thành công, `status = DRAFT`, `isLocked = false` |
| US-15/AC-2 | `outcomeMappings` có Σ `weight` ≠ 1 → 422; có cặp trùng → 422; cặp không nằm trong `clo_pi_links` của CLO → 422 |
| US-15/AC-3 | `scope = SECTION` thiếu `sectionId` → 400 |
| US-15/AC-4 | Giảng viên sửa bài của lớp không thuộc phạm vi → 403 |
| US-15/AC-5 | Khi `isLocked = true`, thêm/sửa/xóa item → bị từ chối; bài vẫn đọc được |
| US-16/AC-1 | Item `kind = CRITERION` có `rubric.levels` với `level` 1–4, mỗi mức có `score` ≤ `maxScore`; `level` ngoài 1–4 → 400 |
| US-16/AC-2 | `bloomLevel` và mức rubric là hai trường độc lập (không API nào đổi cái này từ cái kia) |
| NEW-01/AC-1 | Giảng viên gửi duyệt bài hợp lệ → `SUBMITTED`, ghi `approval.submittedBy/At`, `history` có `SUBMIT` |
| NEW-01/AC-2 | Gửi duyệt khi 0 item, hoặc `totalPoints` lệch Σ `maxScore` quá 0,001 → 422 |
| NEW-01/AC-3 | `DEPARTMENT_HEAD` duyệt → `APPROVED`, `isLocked = true`, ghi `reviewedBy/At`, `history`, và `audit_logs` (`ASSESSMENT_APPROVE`) |
| NEW-01/AC-4 | Từ chối kèm `reviewNote` → `REJECTED`, `isLocked = false`; thiếu `reviewNote` → 400 |
| NEW-01/AC-5 | Vai trò khác `DEPARTMENT_HEAD` (kể cả người gửi) gọi duyệt → 403 |
| NEW-01/AC-6 | Màn hình duyệt đọc `coverageSummary`: `byClo` và `byBloom` khớp với tính tay từ `items` (ví dụ kiểm chứng độc lập) |
| NEW-01/AC-7 | Chuyển trạng thái sai thứ tự (ví dụ `DRAFT → APPROVED`, `LOCKED → DRAFT`) → 422 |
| US-13/AC-1 (MR) | Cây danh mục: cấp 1 không có cha; cấp 2–3 có `ancestorIds` đúng; cấp 4 → 422; trùng `(bankId, code)` → 409 |
| US-14/AC-1 (MR) | Câu hỏi `ACTIVE` phải có ≥ 1 cặp, Σ `weight` = 1; câu và danh mục cùng ngân hàng; PI thuộc `clo_pi_links` của CLO |

## 6. API đề xuất **[ĐỀ XUẤT]**

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| GET/POST/PUT | `/api/syllabi/{id}/assessment-templates` | PROGRAM_MANAGER, LECTURER |
| GET/POST/PUT | `/api/sections/{sectionId}/assessments`, `/api/assessments/{id}` | LECTURER (lớp mình) |
| PUT | `/api/assessments/{id}/items` | LECTURER; **từ chối nếu `isLocked`** |
| POST | `/api/assessments/{id}/submit` | LECTURER |
| POST | `/api/assessments/{id}/approve`, `/reject` | DEPARTMENT_HEAD |
| GET | `/api/assessments/{id}/coverage` | các vai trò được xem |
| (MR) | `/api/question-banks`, `/api/question-banks/{id}/categories`, `/api/questions` | PROGRAM_MANAGER, LECTURER |

## 7. Kiểm tra tham chiếu ở service

`syllabusId`/`sectionId`/`templateId` tồn tại và khớp nhau (lớp thuộc đúng đề cương); mọi `cloId` thuộc `syllabusId`; mọi cặp
nằm trong `clo_pi_links`; `questionId` (nếu có) thuộc ngân hàng của cùng đề cương; người gửi duyệt ≠ người duyệt.

## 8. Test bắt buộc

Σ `weight` = 0,9999 / 1 / 1,01 / 0,5+0,5 / một cặp = 1; cặp trùng; cặp CLO–PI lạ; `maxScore` = 0 và âm; `totalPoints` lệch 0,0009 và 0,0011;
mọi chuyển trạng thái hợp lệ và không hợp lệ; sửa `items` khi `isLocked`; người không đúng vai trò duyệt; người gửi tự duyệt;
`REJECTED` không có ghi chú; sao chép từ khung rồi đổi liên kết CLO–PI (snapshot không đổi theo); đồng thời hai người cùng duyệt một bài (chỉ một thắng).

## 9. Không làm / chưa quyết

- Không làm: sinh đề, duyệt nội dung câu hỏi, xuất phôi in (`exportedFiles`), nhập câu hỏi từ Excel/Moodle (US-17).
- **Chờ GVHD (Q11):** có giữ NEW-01 và vai trò `DEPARTMENT_HEAD` không → quyết bằng ADR 0001 **trước khi code NEW-01**.
- Chờ Khoa: Q15 (cách chia điểm khi một câu đo nhiều cặp), Q16 (cấu trúc ngân hàng), Q17 (phạm vi duyệt của Trưởng bộ môn theo bộ môn).
- US-13/14 xuống MR: item khai báo trực tiếp trong `assessments.items[]` đã đủ để đo; ngân hàng chỉ là tiện ích tái sử dụng.

## 10. Để vấn đáp

Vì sao lưu snapshot ánh xạ trong từng bài (đổi liên kết về sau không làm sai kết quả cũ); vì sao Σ `weight` = 1 (điểm một câu chỉ
được chia, không được nhân đôi); vì sao khóa `items` sau duyệt (điểm đã nhập gắn vào đúng cấu trúc đó); vì sao duyệt *ma trận* khác
duyệt *đề* (chỉ kiểm ánh xạ chuẩn đầu ra, không đụng nội dung câu hỏi); bỏ `isLocked` thì sửa `maxScore` sau khi có điểm làm sai CLO.
