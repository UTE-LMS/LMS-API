# F04 — Bài đánh giá và Ma trận Câu hỏi – CLO (Giảng viên tự khóa ma trận)

Story: **US-15** bài đánh giá khung và bản lớp (C), **NEW-01** giảng viên tự khóa/mở khóa ma trận bài đánh giá kèm độ phủ `coverageSummary` (C, **cập nhật theo [ADR 0013](../adr/0013-import-ctdt-de-cuong-va-nhap-diem-clo-truc-tiep.md) thay thế ADR 0001**). Các story **US-13, US-14, US-16, US-17** đã được **loại bỏ hoàn toàn (K)** theo ADR 0013 để bảo mật đề thi và nhập điểm trực tiếp từng CLO của từng câu hỏi. Giai đoạn P3. Chủ trì đề xuất: IBS + IT.
Package: `assessment`. Phụ thuộc: `00`, `F01`, `F02`, `F03` (cần `clos`, `clo_pi_links`); `F05a` cho bản lớp (`course_sections`).

## 1. Mục tiêu

Khai báo **ma trận câu hỏi – CLO của bài đánh giá**: bài đánh giá có những câu hỏi nào (`Câu 1`, `Câu 2`...), mỗi câu hỏi đánh giá những CLO nào và **điểm tối đa (`maxScore`) dành cho từng CLO tại câu hỏi đó là bao nhiêu** (do giảng viên nhập trực tiếp, không dùng trọng số `weight`). Bản lớp (`scope = SECTION`) do **Giảng viên phụ trách lớp tự khóa ma trận** (`DRAFT → LOCKED`) trước khi mở cổng nhập điểm CLO, và bị **khóa cứng `items[]`** sau đó. Hệ thống tuyệt đối không lưu nội dung câu hỏi/đáp án/ngân hàng đề và không đo điểm tổng bài kiểm tra (`totalPoints`).

## 2. Đọc trước

- `dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md` §3.2, §3.3, §6.2.
- `GiaiThich_CoSoDuLieu.md` (v0.6) §0, §1, nguyên tắc 8, 12, 13, §4.12.
- `database_import.js` (v0.6): khối `OUTCOME_MAPPING`, `assessments`.
- `adr/0013-import-ctdt-de-cuong-va-nhap-diem-clo-truc-tiep.md`.

## 3. Collection `assessments` (lõi của feature — CSDL v0.6)

| Trường | Ghi chú |
|---|---|
| `syllabusId`, `code`, `name` | `code` duy nhất trong `(syllabusId, sectionId)` |
| `scope` | `SYLLABUS_TEMPLATE` (khung theo đề cương) hoặc `SECTION` (bản lớp, **bắt buộc có `sectionId`**) |
| `sectionId`, `templateId` | `templateId` trỏ khung mẫu (nếu bản lớp sao chép từ khung) |
| `type` | `QUIZ`, `PRACTICAL`, `PROJECT`, `MIDTERM`, `FINAL`, `OTHER` |
| `phase` | `FORMATIVE`, `MIDTERM`, `SUMMATIVE` |
| `scheduledAt` | Thời điểm kiểm tra (tùy chọn) |
| `status` | `DRAFT` (đang thiết lập ma trận) → `LOCKED` (giảng viên đã khóa ma trận, mở cổng nhập điểm) → `SCORE_ENTERED` (đã có điểm nhập vào) |
| `isLocked` | `true` khi `status ∈ {LOCKED, SCORE_ENTERED}` (validator bắt buộc khớp) |
| `coverageSummary` | `{ totalItemsSum, byClo[{cloId, points, percentage}], byBloom[{bloomLevel, questionCount, points, percentage}] }` (trong đó `points` là tổng `outcomeMappings[].maxScore`) |
| `items[]` | `_id`, `code` (ví dụ `"Câu 1"`), `orderIndex`, `bloomLevel` (1–6, tùy chọn), `outcomeMappings[]` (≥ 1) |
| `outcomeMappings[]` | `{ cloId, maxScore }`, `maxScore > 0` (do giảng viên nhập trực tiếp điểm tối đa của từng CLO trong câu hỏi đó; không trùng `cloId` trong cùng 1 câu hỏi) |

> **Các trường đã loại bỏ hoàn toàn theo ADR 0013:** `totalPoints`, `weightInCourse`, `dataGranularity`, `approval`, `exportedFiles`; trong `items[]` đã bỏ `title`, `kind`, `maxScore` (cấp item), `topic`, `questionId`, `questionVersion`, `externalRef`, `rubric`; trong `outcomeMappings[]` đã thay `weight` bằng `maxScore`.

Chỉ mục: duy nhất `(syllabusId, sectionId, code)`; `(sectionId, status)`; `items.outcomeMappings.cloId`.

## 4. Quy tắc nghiệp vụ (cập nhật theo ADR 0013)

1. **Bản lớp là SNAPSHOT:** `items[].outcomeMappings` sao chép tại thời điểm thiết lập; đổi `clos` sau đó không làm đổi bài đã thi.
2. Mỗi `cloId` trong `outcomeMappings` phải **thuộc `syllabusId` của bài**. Chạy tính chính thức (F06) yêu cầu `clo_pi_links` của các CLO đó đã `VERIFIED`.
3. **Điểm tối đa trực tiếp cho từng CLO trong mỗi câu hỏi:** Mỗi phần tử trong `outcomeMappings` khai báo `{ cloId, maxScore }` với `maxScore > 0`. Nếu một câu hỏi kiểm tra nhiều CLO, từng CLO trong câu hỏi đó có `maxScore` riêng biệt; mỗi `cloId` chỉ xuất hiện tối đa 1 lần trong cùng một câu hỏi (`noDuplicateClos`).
4. **Hệ thống không đo kết quả bài kiểm tra:** Không có trường `totalPoints` và không kiểm tra tổng điểm bài thi; hệ thống chỉ tổng hợp điểm theo từng CLO để đo lường chuẩn đầu ra.
5. **Luồng trạng thái Giảng viên tự khóa ma trận (NEW-01 — ADR 0013):**
   - `DRAFT → LOCKED`: Giảng viên phụ trách lớp (`LECTURER` trong `course_sections.lecturerIds`) gọi `POST /api/v1/assessments/{id}/lock`. Điều kiện: `scope = SECTION`, `status = DRAFT`, `size(items) >= 1`. Đặt `status = LOCKED`, `isLocked = true`, ghi `audit_logs` (`ASSESSMENT_LOCK`).
   - `LOCKED → DRAFT` (Mở khóa — `unlock`): Giảng viên phụ trách lớp gọi `POST /api/v1/assessments/{id}/unlock`. Chỉ cho phép khi `status = LOCKED` và **chưa có bản ghi điểm nào trong `scores` thuộc `assessmentId` đó** (nếu đã có điểm hoặc `status = SCORE_ENTERED` $\to$ từ chối `422`). Đặt `status = DRAFT`, `isLocked = false`, ghi `audit_logs` (`ASSESSMENT_UNLOCK`).
   - `LOCKED → SCORE_ENTERED`: Tự động chuyển khi lô điểm đầu tiên được xác nhận ở F05b.
   - Xóa bài đánh giá (`DELETE /api/v1/assessments/{id}`): Chỉ cho phép khi `status = DRAFT` và chưa có điểm.
6. **Khóa cấu trúc:** khi `isLocked = true` mọi API sửa bài hoặc đổi `items[]` bị từ chối (`422`). Phải đổi `isLocked` và `status` **cùng một lần ghi**.
7. **Chỉ nhập điểm khi `LOCKED` hoặc `SCORE_ENTERED`** (F05b kiểm tra).
8. `coverageSummary` do service tính tự động từ `items`:
   - Điểm của câu hỏi $k$ trên ma trận chuẩn đầu ra là $\sum_{m \in \text{outcomeMappings}_k} maxScore_m$.
   - `totalItemsSum` = tổng `maxScore` của mọi `outcomeMappings` trong toàn bộ `items[]`.
   - `byClo`: tổng `maxScore` và tỷ lệ % của từng `cloId` trên `totalItemsSum`.
   - `byBloom`: số câu hỏi, tổng `maxScore` các CLO trong câu hỏi có `bloomLevel` đó, và tỷ lệ % trên `totalItemsSum`.

## 5. Stories và acceptance criteria (cập nhật theo ADR 0013)

| ID | AC |
|---|---|
| US-15/AC-1 | Giảng viên tạo bài `SECTION` cho lớp mình, sao chép từ khung (`templateId`) hoặc tự khai báo `items` → thành công, `status = DRAFT`, `isLocked = false` |
| US-15/AC-2 | `outcomeMappings` có `maxScore <= 0` → 400; có `cloId` trùng trong cùng một câu hỏi → 422; `cloId` không thuộc `syllabusId` của bài → 422 |
| US-15/AC-3 | `scope = SECTION` thiếu `sectionId` → 400 |
| US-15/AC-4 | Giảng viên tạo/sửa/xóa/khóa bài của lớp không thuộc `lecturerIds` → 403 |
| US-15/AC-5 | Khi `isLocked = true` (`LOCKED` / `SCORE_ENTERED`), sửa thông tin bài, xóa bài hoặc `PUT /items` → bị từ chối (422); bài vẫn đọc được |
| NEW-01/AC-1 | Giảng viên phụ trách lớp gọi `POST /api/v1/assessments/{id}/lock` khi bài `SECTION` đang `DRAFT` và có ≥ 1 câu hỏi hợp lệ → `status = LOCKED`, `isLocked = true`, ghi `audit_logs` (`ASSESSMENT_LOCK`) |
| NEW-01/AC-2 | Gọi `lock` khi `items[]` rỗng, hoặc bài là `SYLLABUS_TEMPLATE`, hoặc `status != DRAFT` → 422 |
| NEW-01/AC-3 | Giảng viên phụ trách lớp gọi `POST /api/v1/assessments/{id}/unlock` khi bài đang `LOCKED` và chưa có điểm trong `scores` → `status = DRAFT`, `isLocked = false`, ghi `audit_logs` (`ASSESSMENT_UNLOCK`). Nếu đã có điểm hoặc `status = SCORE_ENTERED` → 422 |
| NEW-01/AC-4 | Người dùng không phải Giảng viên phụ trách lớp (`lecturerIds`) gọi `lock` / `unlock` → 403 |
| NEW-01/AC-5 | API `GET /api/v1/assessments/{id}/coverage` và `coverageSummary` tính đúng `totalItemsSum`, `byClo`, `byBloom` từ `outcomeMappings[].maxScore` khớp tính tay độc lập |

## 6. API chuẩn (tiền tố `/api/v1` — ADR 0013)

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| GET/POST | `/api/v1/syllabi/{id}/assessment-templates` | đọc: `canAccessSyllabus` READ; tạo: `PROGRAM_MANAGER`, `LECTURER` hoặc `DEPARTMENT_HEAD` có học phần trong scope |
| GET/POST | `/api/v1/sections/{sectionId}/assessments` | đọc: `canAccessSection` READ; tạo: `LECTURER` của lớp |
| GET/PUT/DELETE | `/api/v1/assessments/{id}` | đọc theo lớp/đề cương; sửa/xóa (chỉ khi `DRAFT`): như tạo |
| PUT | `/api/v1/assessments/{id}/items` | `LECTURER` của lớp (hoặc người tạo khung); **từ chối 422 nếu `isLocked = true`** |
| POST | `/api/v1/assessments/{id}/lock` | `LECTURER` của lớp (`DRAFT -> LOCKED`, ghi `ASSESSMENT_LOCK`) |
| POST | `/api/v1/assessments/{id}/unlock` | `LECTURER` của lớp (`LOCKED -> DRAFT` khi chưa có điểm, ghi `ASSESSMENT_UNLOCK`) |
| GET | `/api/v1/assessments/{id}/coverage` | như đọc; trả `byClo` kèm mã CLO và `byBloom` |

## 7. Kiểm tra tham chiếu ở service

`syllabusId`/`sectionId`/`templateId` tồn tại và khớp nhau (lớp thuộc đúng đề cương); mọi `cloId` thuộc `syllabusId`;
`questionId` (nếu có) thuộc ngân hàng của cùng đề cương; người gửi duyệt ≠ người duyệt.

## 8. Test bắt buộc

Σ `weight` = 0,9999 / 1 / 1,01 / 0,5+0,5 / một CLO = 1; CLO trùng; CLO không thuộc đề cương; `maxScore` = 0 và âm; `totalPoints` lệch 0,0009 và 0,0011;
mọi chuyển trạng thái hợp lệ và không hợp lệ; sửa `items` khi `isLocked`; người không đúng vai trò duyệt; người gửi tự duyệt;
`REJECTED` không có ghi chú; sao chép từ khung rồi sửa CLO (snapshot không đổi theo); đồng thời hai người cùng duyệt một bài (chỉ một thắng).

## 9. Không làm / chưa quyết

- Không làm: sinh đề, duyệt nội dung câu hỏi, xuất phôi in (`exportedFiles`), nhập câu hỏi từ Moodle. Nhập câu hỏi từ Excel (US-17, Q16).
- **Q11:** GVHD đã xác nhận giữ NEW-01 và vai trò `DEPARTMENT_HEAD` (ADR 0001 phương án A, theo báo cáo của sinh viên; ngày ký: [ĐIỀN]). Mã đã hiện thực, xem §11.
- Chờ Khoa: Q15 (cách chia điểm khi một câu đo nhiều CLO). *(Riêng Q16 đã chốt nhập Excel; Q17 đã chốt bỏ collection `departments`, kiểm soát qua `scope.courseIds`).*
- US-13/14 xuống MR: item khai báo trực tiếp trong `assessments.items[]` đã đủ để đo; ngân hàng chỉ là tiện ích tái sử dụng.

## 10. Để vấn đáp

Vì sao lưu snapshot ánh xạ trong từng bài (đổi liên kết về sau không làm sai kết quả cũ); vì sao Σ `weight` = 1 (điểm một câu chỉ
được chia, không được nhân đôi); vì sao khóa `items` sau duyệt (điểm đã nhập gắn vào đúng cấu trúc đó); vì sao duyệt *ma trận* khác
duyệt *đề* (chỉ kiểm ánh xạ chuẩn đầu ra, không đụng nội dung câu hỏi); bỏ `isLocked` thì sửa `maxScore` sau khi có điểm làm sai CLO.

## 11. Hiện thực (2026-11-08)

US-15, US-16 và NEW-01 đã có mã trên nhánh `feat/f04-assessments` (US-13, US-14 là mục tiêu mở rộng, US-17 loại khỏi phạm vi: **chưa làm**).
Các quyết định dưới đây là lựa chọn của người hiện thực, **chờ hai sinh viên xác nhận**.

**API thực tế** (tiền tố `/api/v1`):

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| GET/POST | `/syllabi/{id}/assessment-templates` | đọc: `canAccessSyllabus` READ; tạo: LECTURER hoặc DEPARTMENT_HEAD có học phần trong `scope.courseIds` |
| GET/POST | `/sections/{id}/assessments` | đọc: `canAccessSection` READ; tạo: LECTURER của lớp |
| GET/PUT | `/assessments/{id}`, `PUT /assessments/{id}/items` | đọc theo lớp/đề cương; sửa: như tạo |
| GET | `/assessments/{id}/coverage` | như đọc; trả `byClo` kèm mã CLO và `byBloom` |
| POST | `/assessments/{id}/submit` | LECTURER của lớp |
| POST | `/assessments/{id}/approve`, `/reject` | DEPARTMENT_HEAD có học phần của đề cương trong `scope.courseIds` (`canReviewAssessment`) |

| # | Quyết định | Lý do / hệ quả |
|---|---|---|
| D1 | Khung theo đề cương do LECTURER hoặc DEPARTMENT_HEAD có học phần trong scope soạn; **PROGRAM_MANAGER và ADMIN chỉ đọc** | Plan §6 ghi "PROGRAM_MANAGER, LECTURER" nhưng ADR 0010 đã chuyển quyền soạn nội dung học phần về bộ môn; khung là nội dung của đề cương |
| D2 | Bản lớp chỉ LECTURER của lớp tạo/sửa/gửi duyệt (ADMIN, PM, TBM không) | ADMIN không soạn nội dung (ADR 0010); TBM là người duyệt, không tự soạn bài của lớp |
| D3 | Duyệt/từ chối: chỉ DEPARTMENT_HEAD đúng phạm vi, **ADMIN không có quyền**; người gửi duyệt không được duyệt/từ chối lượt của chính mình (403) kể cả khi cùng giữ vai trò DEPARTMENT_HEAD | Plan §4 quy tắc 9, NEW-01/AC-5 |
| D4 | Khung (`SYLLABUS_TEMPLATE`) không đi qua quy trình duyệt: luôn `DRAFT`, `isLocked = false`, sửa được; gửi duyệt khung → 422 | Quy trình duyệt chỉ có ý nghĩa với bài đã/sẽ thi của một lớp |
| D5 | Bản lớp chỉ sửa được ở `DRAFT` hoặc `REJECTED`; sửa bài `REJECTED` tự đưa về `DRAFT`; gửi duyệt chỉ từ `DRAFT` (bài bị từ chối phải sửa rồi mới gửi lại) | Plan §4 quy tắc 5 (`REJECTED → DRAFT`); `SUBMITTED` đang chờ duyệt nên cũng khóa sửa |
| D6 | **Chưa có** API mở khóa (`UNLOCK`), xóa bài đánh giá; các chuyển `SCORE_ENTERED`, `LOCKED` thuộc F05b trở đi | Plan §4 ghi UNLOCK là [ĐỀ XUẤT], không nằm trong AC |
| D7 | Thêm trường `revision` (`@Version`) vào `assessments` để hai người cùng duyệt chỉ một người thắng | Thiết kế CSDL §4.15 chưa có trường này (validator MongoDB không cấm trường thừa); cần cập nhật `GiaiThich_CoSoDuLieu.md` khi hai sinh viên xác nhận |
| D8 | `coverageSummary.percentage` là phần trăm 0–100 so với Σ `maxScore`; phần tử không khai báo Bloom không vào `byBloom`; độ phủ tính lại ở mọi lần ghi `items` và khi đọc `/coverage` | NEW-01/AC-6 |
| D9 | Σ `weight` kiểm bằng `|Σ − 1| ≤ 0,0001` tính bằng `double`, đúng phép kiểm của validator MongoDB. Hệ quả: tổng 0,9999 bị từ chối (sai số số thực làm `|0,9999 − 1|` lớn hơn 0,0001 một chút), 0,99995 được chấp nhận | Tránh trường hợp service chấp nhận mà MongoDB từ chối (lỗi 121) |
| D10 | Rubric: mức trong một tiêu chí không trùng, điểm mỗi mức ≤ `maxScore`; mức ngoài 1–4 → 400. Không bắt buộc `CRITERION` phải có rubric, không cấm `QUESTION` có rubric | Plan chưa nói rõ; giữ quy tắc tối thiểu |
| D11 | `TOTAL_ONLY` lưu được, phản hồi có `measurableByOutcome = false` | Plan §4 quy tắc 11 |
| D12 | US-15/AC-3 (bản lớp thiếu `sectionId` → 400) **không phát sinh** ở API vì `sectionId` lấy từ đường dẫn; validator MongoDB vẫn ép ở tầng CSDL | Thiết kế API |
| D13 | `DEPARTMENT_HEAD` duyệt dựa trên `scope.courseIds` chứa học phần của đề cương của bài (Q17 đã chốt không có collection `departments`) | ADR 0010 |
