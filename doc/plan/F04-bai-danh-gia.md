# F04 — Bài đánh giá, rubric, ma trận đề (giảng viên tự khóa)

> **Cập nhật 2026-11-11 (ADR 0013, GVHD đã xác nhận):** bỏ duyệt ma trận của Trưởng bộ môn (giảng viên tự khóa); bỏ ngân hàng
> câu hỏi và nội dung câu hỏi; điểm nhập trực tiếp theo từng CLO của từng câu (`outcomeMappings = {cloId, maxScore}`).
> Các mục 1–10 dưới đây đã viết lại theo quyết định này. **Mục 11 mô tả mã đã hiện thực theo thiết kế cũ (ADR 0001) nên đã
> lỗi thời**, sẽ viết lại khi sửa mã.

Story: **US-15** bài đánh giá khung và bản lớp (C), **US-16** tiêu chí rubric + mức 1–4 (C), **NEW-01** giảng viên tự khóa
ma trận đề (C; đổi nghĩa từ "duyệt ma trận", giữ mã để truy vết). **Bỏ:** US-13 (ngân hàng + cây danh mục), US-14 (câu hỏi
trong ngân hàng), US-17 (nhập câu hỏi từ Excel/Moodle). Giai đoạn P3. Chủ trì đề xuất: IBS + IT.
Package: `assessment`. Phụ thuộc: `00`, `F01`, `F02`, `F03` (cần `clos`, `clo_pi_links`); `F05a` cho bản lớp (`course_sections`).

## 1. Mục tiêu

Khai báo **ma trận đề của bài đã thi**: có những câu hỏi/tiêu chí nào (chỉ nhãn `Câu 1`, `Câu 2`…), mỗi câu đo CLO nào và
**điểm tối đa riêng của từng CLO trong câu** (ADR 0013; PI suy ra từ kết quả CLO qua `clo_pi_links`, ADR 0011). Đây là
"bản đồ" để quy điểm lên chuẩn đầu ra. Giảng viên của lớp **tự khóa** ma trận rồi mới nhập điểm; sau khi khóa, `items[]` bị
khóa. **Không lưu nội dung câu hỏi, đề bài, đáp án**; không sinh đề; không duyệt đề.

## 2. Đọc trước

- `dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md` §1, §3.2, §3.3.
- `GiaiThich_CoSoDuLieu.md` §0 (v0.6), §1 (phạm vi), nguyên tắc 8, 12, 15, §4.15, §8 (Q11, Q15, Q16).
- `database_import.js`: khối `assessments` (3 điều kiện `$expr`) và `scores`.
- `adr/0013-…` (thay ADR 0001).

## 3. Collection `assessments` (lõi của feature)

| Trường | Ghi chú |
|---|---|
| `syllabusId`, `code`, `name` | `code` duy nhất trong `(syllabusId, sectionId)` |
| `scope` | `SYLLABUS_TEMPLATE` (khung theo đề cương) hoặc `SECTION` (bản lớp, **bắt buộc có `sectionId`**) |
| `sectionId`, `templateId` | `templateId` trỏ khung mẫu (nếu bản lớp sao chép từ khung) |
| `type` | `QUIZ`, `PRACTICAL`, `PROJECT`, `MIDTERM`, `FINAL`, `OTHER` |
| `phase` | `FORMATIVE`, `MIDTERM`, `SUMMATIVE` |
| `weightInCourse` (0–1) | Không tham gia công thức CLO theo đặc tả (chờ Q20). **Không còn `totalPoints`** |
| `dataGranularity` | `ITEM` (mặc định) hoặc `TOTAL_ONLY` — `TOTAL_ONLY` **không đo được CLO/PI** |
| `status` | `DRAFT`, `MATRIX_LOCKED`, `SCORE_ENTERED`, `LOCKED` |
| `isLocked` | `true` khi `status ∈ {MATRIX_LOCKED, SCORE_ENTERED, LOCKED}` (validator bắt buộc khớp) |
| `coverageSummary` | `{ totalItemsSum, byClo[{cloId, points, percentage}], byBloom[{bloomLevel, questionCount, points, percentage}] }` |
| `exportedFiles` | **không có tính năng** (xuất phôi in là khâu tổ chức thi) |
| `items[]` | `_id`, `code` (nhãn), `orderIndex`, `kind` (`QUESTION`/`CRITERION`), `bloomLevel`, `externalRef`, `outcomeMappings[]` (≥ 1), `rubric`. **Không** có `title`, `topic`, `questionId`, `questionVersion`, `maxScore` |
| `outcomeMappings[]` | `{ cloId, maxScore }`, `maxScore` > 0, không trùng CLO trong một item (ADR 0013). Điểm tối đa của cả câu = tổng, không lưu |

Chỉ mục: duy nhất `(syllabusId, sectionId, code)`; `(sectionId, status)`; `items.outcomeMappings.cloId`.
Validator ở MongoDB đã ép: bản `SECTION` có `sectionId`; `isLocked` khớp `status`; từ `MATRIX_LOCKED` trở đi có ≥ 1 item;
không trùng CLO trong một item; `maxScore` > 0. **Service vẫn phải kiểm tra trước** để trả lỗi rõ ràng (không để lộ lỗi 121 của MongoDB).

## 4. Quy tắc nghiệp vụ

1. **Bản lớp là SNAPSHOT:** `items[].outcomeMappings` sao chép tại thời điểm thi; đổi `clos` sau đó không làm đổi bài đã thi.
2. Mỗi `cloId` trong `outcomeMappings` phải **thuộc `syllabusId` của bài**. Chạy tính chính thức (F06) yêu cầu `clo_pi_links` của các CLO đó đã `VERIFIED`.
3. **Điểm tối đa theo từng CLO do giảng viên nhập trực tiếp**, không chia theo `weight` (đóng Q15). Câu đo nhiều CLO có điểm tối
   đa riêng cho từng CLO; điểm tối đa của CLO trong bài = Σ `maxScore` của CLO đó qua các câu (đặc tả §4.1).
4. `rubric.levels[].level` ∈ 1–4 là **mức rubric**, tách khỏi `bloomLevel` (1–6). Không quy đổi lẫn nhau.
5. **Luồng trạng thái (NEW-01, ADR 0013):** `DRAFT → MATRIX_LOCKED` (giảng viên của lớp khóa; cần ≥ 1 item);
   `MATRIX_LOCKED → SCORE_ENTERED` (tự động khi lô điểm đầu tiên được xác nhận, F05b); `SCORE_ENTERED → LOCKED` (khóa sổ).
   Mỗi lần khóa ghi `audit_logs` (`ASSESSMENT_LOCK`). Chuyển trạng thái sai thứ tự → 422. **Mở khóa (`UNLOCK`) chưa quyết** (ADR 0013 mục 5, TD-35).
6. **Khóa cấu trúc:** khi `isLocked = true` mọi API đổi `items[]` (thêm/sửa/xóa/đổi `outcomeMappings`) bị từ chối (409/422).
   Phải đổi `isLocked` và `status` **cùng một lần ghi** (validator bắt buộc khớp).
7. **Chỉ nhập điểm khi `MATRIX_LOCKED` hoặc `SCORE_ENTERED`** (F05b kiểm tra; F04 cung cấp trạng thái).
8. `coverageSummary` do service tính từ `items` (điểm tối đa theo CLO và Bloom), để giảng viên kiểm tra trước khi khóa và để
   cảnh báo CLO của đề cương chưa được gắn câu nào ("chưa được đánh giá", đặc tả §4.4); không để người dùng nhập tay.
9. Chỉ **giảng viên của lớp** tạo/sửa/khóa bản lớp. `DEPARTMENT_HEAD` không còn duyệt.
10. **Không lưu nội dung câu hỏi:** API và document không có trường nội dung/đề bài/đáp án; `code` chỉ là nhãn.
11. `dataGranularity = TOTAL_ONLY`: cho lưu nhưng báo rõ không đo được CLO/PI; nếu dữ liệu thật chỉ có điểm tổng phải đổi phạm vi bằng ADR trước K1 (R2).

## 5. Stories và acceptance criteria (nháp)

| ID | AC |
|---|---|
| US-15/AC-1 | Giảng viên tạo bài `SECTION` cho lớp mình, sao chép từ khung (`templateId`) hoặc tự khai báo `items` → thành công, `status = DRAFT`, `isLocked = false` |
| US-15/AC-2 | `outcomeMappings[].maxScore` ≤ 0 → 422; item không có CLO → 422; có CLO trùng trong một item → 422; CLO không thuộc `syllabusId` của bài → 422 |
| US-15/AC-3 | `scope = SECTION` thiếu `sectionId` → 400 |
| US-15/AC-4 | Giảng viên sửa bài của lớp không thuộc phạm vi → 403 |
| US-15/AC-5 | Khi `isLocked = true`, thêm/sửa/xóa item → bị từ chối; bài vẫn đọc được |
| US-15/AC-6 | Item chỉ có nhãn `code` và `outcomeMappings`; document lưu xuống không có trường nào lưu nội dung câu hỏi |
| US-16/AC-1 | Item `kind = CRITERION` có `rubric.levels` với `level` 1–4; `level` ngoài 1–4 → 400 |
| US-16/AC-2 | `bloomLevel` và mức rubric là hai trường độc lập (không API nào đổi cái này từ cái kia) |
| NEW-01/AC-1 | Giảng viên của lớp khóa bài hợp lệ → `MATRIX_LOCKED`, `isLocked = true`, ghi `audit_logs` (`ASSESSMENT_LOCK`) |
| NEW-01/AC-2 | Khóa khi 0 item → 422 |
| NEW-01/AC-3 | Vai trò khác giảng viên của lớp (kể cả `DEPARTMENT_HEAD`, `ADMIN`) gọi khóa → 403 |
| NEW-01/AC-4 | Chuyển trạng thái sai thứ tự (khóa lại bài đã khóa; `LOCKED → DRAFT`) → 422 |
| NEW-01/AC-5 | `coverageSummary.byClo` khớp tính tay từ `items` (ví dụ minh họa: đặc tả §3.2, CLO1 5,0; CLO2 4,5; CLO3 0,5); CLO không có điểm tối đa nào → báo "chưa được đánh giá" |

## 6. API đề xuất **[ĐỀ XUẤT]**

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| GET/POST/PUT | `/api/syllabi/{id}/assessment-templates` | LECTURER, DEPARTMENT_HEAD có học phần trong `scope.courseIds` (ADR 0010) |
| GET/POST/PUT | `/api/sections/{sectionId}/assessments`, `/api/assessments/{id}` | LECTURER (lớp mình) |
| PUT | `/api/assessments/{id}/items` | LECTURER; **từ chối nếu `isLocked`** |
| POST | `/api/assessments/{id}/lock` | LECTURER (lớp mình) |
| GET | `/api/assessments/{id}/coverage` | các vai trò được xem |

## 7. Kiểm tra tham chiếu ở service

`syllabusId`/`sectionId`/`templateId` tồn tại và khớp nhau (lớp thuộc đúng đề cương); mọi `cloId` thuộc `syllabusId`;
người khóa là giảng viên của lớp (`section.lecturerIds`).

## 8. Test bắt buộc

`maxScore` = 0 và âm; item không có CLO; CLO trùng; CLO không thuộc đề cương; mọi chuyển trạng thái hợp lệ và không hợp lệ;
sửa `items` khi `isLocked`; người không phải giảng viên của lớp khóa; khóa bài 0 item; sao chép từ khung rồi sửa CLO
(snapshot không đổi theo); `coverageSummary` khớp ví dụ tính tay; hai người cùng khóa một bài (chỉ một thắng).

## 9. Không làm / chưa quyết

- Không làm: sinh đề, duyệt nội dung câu hỏi, **lưu nội dung câu hỏi/đề/đáp án**, ngân hàng câu hỏi (US-13, US-14, US-17), xuất phôi in (`exportedFiles`).
- Chưa quyết: `UNLOCK` khi khóa nhầm (TD-35); trọng số bài trong học phần (Q20).

## 10. Để vấn đáp

Vì sao lưu snapshot ánh xạ trong từng bài (đổi liên kết về sau không làm sai kết quả cũ); vì sao điểm tối đa và điểm đạt nhập
theo từng CLO của từng câu (một câu đo nhiều CLO thì sinh viên làm đúng ý CLO2, sai ý CLO3 vẫn phản ánh đúng, không bù lẫn
nhau); vì sao khóa `items` khi đã khóa ma trận (điểm đã nhập gắn vào đúng cấu trúc đó, sửa điểm tối đa sau khi có điểm làm sai
CLO của cả lớp); vì sao không lưu nội dung câu hỏi (loại bỏ nguy cơ lộ đề); ai chịu trách nhiệm đúng/sai ánh xạ CLO khi không
còn bước duyệt (giảng viên, có `audit_logs` ghi người khóa).

## 11. Hiện thực (2026-11-08) — **LỖI THỜI sau ADR 0013**

> Mục này mô tả mã F04 đã viết theo thiết kế cũ (ADR 0001: gửi duyệt/duyệt/từ chối, `weight`, `totalPoints`, ngân hàng câu hỏi). Giữ lại làm lịch sử cho tới khi sửa mã theo ADR 0013; **không dùng làm đặc tả**.

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
