# Rule 02 — Bất biến nghiệp vụ và kiến trúc

Căn cứ: `doc/GiaiThich_CoSoDuLieu.md` (14 nguyên tắc §2, §4, §8), `doc/BS.md` §4, `doc/Misa.md` §3F.
Rubric Nguyên tắc 2: nghiệp vụ chỉ có "hợp lý / không hợp lý so với mục tiêu và sản phẩm đã thống nhất".
Các bất biến dưới đây **chính là** mục tiêu đã thống nhất; mã vi phạm chúng là không hợp lý (TC2.1, TC2.2).

## Kiến trúc

- Angular chỉ gọi API Spring Boot, không bao giờ truy cập MongoDB trực tiếp.
- Công thức đo lường chính thức chỉ chạy ở backend. UI hiển thị kết quả API trả về, không tự tính lại.
- Backend kiểm tra quyền và dữ liệu trước khi xử lý. Quyền theo `users.roleAssignments` (vai trò + phạm vi);
  ẩn nút ở UI không thay cho kiểm tra ở backend.
- MongoDB không có khóa ngoại: tầng service phải kiểm tra tham chiếu (cloId tồn tại, câu hỏi và danh mục
  cùng ngân hàng, cặp CLO–PI nằm trong `clo_pi_links` của chính CLO đó…) trước khi ghi.
- Xác nhận nhập điểm cần transaction nhiều document → MongoDB chạy replica set (cả dev và CI). Dev: image
  `docker/mongo/` (README).
- **Bài đánh giá (`assessments`) có quy trình duyệt ma trận** (CSDL v0.5, NEW-01, chờ GVHD Q11): `DRAFT → SUBMITTED →
  APPROVED | REJECTED → SCORE_ENTERED → LOCKED`. Chỉ nhập điểm khi `APPROVED`/`SCORE_ENTERED`; `isLocked = true` khi
  `status ∈ {APPROVED, SCORE_ENTERED, LOCKED}` và cấm sửa `items[]`; gửi duyệt cần ≥ 1 item và
  `|totalPoints − Σ items.maxScore| < 0,001`; duyệt/từ chối ghi `approval.history[]` và `audit_logs`. Chỉ duyệt
  *ma trận ánh xạ*, không duyệt nội dung câu hỏi. `exportedFiles` (xuất phôi in) không có tính năng.
- Phân quyền theo 7 vai trò của CSDL v0.5: `ADMIN`, `PROGRAM_MANAGER`, `DEPARTMENT_HEAD`, `QA_OFFICER`, `LECTURER`,
  `STUDENT` (chỉ đọc kết quả của chính mình), `VIEWER`; `scope = {programIds, courseIds, sectionIds}`.
- Tệp minh chứng/tệp điểm gốc: lưu thông tin mô tả và tham chiếu trong MongoDB, nơi lưu tệp thiết kế riêng.

## Quy tắc đo lường (không tự đặt số)

- **Không hardcode** công thức, trọng số, ngưỡng đạt, mẫu số. Đọc từ `measurement_rules`. Chỉ quy tắc
  `CONFIRMED` mới cho kết luận chính thức (`calculation_runs.isOfficial`). Bản `DRAFT` có ngưỡng để trống.
- Các số trong tài liệu (40/60, ngưỡng 60%, 72%, tỷ trọng CLO 20/40/40, 45 ghi danh/40 đủ dữ liệu) **chỉ là
  ví dụ minh họa**. Dùng trong test thì đặt tên fixture rõ là "ví dụ minh họa", không đưa vào seed hay
  cấu hình mặc định.
- Liên kết CLO–PI dùng cho tính chính thức phải ở trạng thái `VERIFIED`.
- Một câu hỏi/tiêu chí có thể đo nhiều cặp (CLO, PI); `weight` của các cặp trong một câu có tổng bằng 1;
  không gán nguyên điểm một câu cho mọi chuẩn. Cách chia này đang chờ Khoa xác nhận (Q15): giữ ở dạng
  cấu hình, không rải logic cứng.
- Tính PLO từ PI theo quy tắc cấu hình, **không** lấy trung bình mức năng lực của PI để suy ra mức PLO.

## Phân biệt khái niệm (sai là lỗi nghiệp vụ)

| Không đồng nhất | Hệ quả khi code |
|---|---|
| Mức Bloom ≠ điểm số | Lưu riêng `bloomLevel`, không có trường quy đổi sang điểm |
| I/R/M ≠ trọng số | `course_pi_contributions` không được dùng làm trọng số; cấm map I=1, R=2, M=3. CSDL v0.5 có `pi.useIrmAsWeight`/`irmWeights`: engine coi `useIrmAsWeight` luôn là `false`, không đặt `irmWeights` mẫu; chỉ xem lại khi Khoa xác nhận (Q18) |
| `capabilityLevelRaw` ≠ Bloom | Lưu nguyên giá trị nguồn, không diễn giải (Q03) |
| Mức rubric (1–4) ≠ Bloom | Hai trường riêng |
| Điểm học phần ≠ mức đạt CLO | CLO tính từ điểm câu hỏi/tiêu chí gắn CLO, không từ điểm tổng kết |
| CLO cá nhân ≠ tỷ lệ đạt lớp ≠ mục tiêu cấp lớp | Ba đại lượng riêng trong `student_results` / `aggregate_results` |
| Tỷ lệ đạt luôn có mẫu số | Luôn trả `counts` (ghi danh, đủ dữ liệu, đạt, chưa đủ dữ liệu) và `passRateOfEnrolled` |

## Dữ liệu và định danh

- Thiếu dữ liệu **không mặc định 0**. `scores.status`: `SCORED`, `BLANK` (đã dự thi bỏ trống, = 0 có ghi nhận),
  `ABSENT`, `NOT_GRADED`, `EXEMPT`, `MISSING` (các trạng thái này `rawScore = null`). `rawScore ≤ maxScore`.
  Khi nhập, ô trống là `BLANK` hay `MISSING` do người nhập chọn cho từng lô; hệ thống không tự đoán.
- CLO nhận diện bằng `_id` (mã `CLO1` chỉ duy nhất trong một đề cương). PLO/PI nhận diện bằng **mã**, không
  bằng thứ tự dòng (`displayOrder` là trường riêng; tài liệu xếp PLO1, 6, 7, 4, 5, 2, 3).
- Mọi dữ liệu và kết quả gắn phiên bản: `programVersionId`, `syllabusId`, `ruleId`. Báo cáo cũ đọc lại
  được khi quy tắc đổi. Bài đánh giá cấp lớp (`SECTION`) giữ bản sao ánh xạ tại thời điểm thi; bản đã có
  điểm bị khóa `items`.
- Truy vết được từ kết quả → `inputs[]` → `scores` → `import_batches` + số dòng trong file gốc.
- Sửa điểm, xác nhận nhập, chạy tính, xác nhận quy tắc, đăng nhập đều ghi `audit_logs` (trước/sau, lý do).
- Chống xung đột: `scores.revision` (khóa lạc quan), `calculation_runs.lockKey`, `isStale` khi điểm đổi sau
  lần tính.
- `contentOriginal` của PLO/PI/CLO để trống tới khi đối chiếu bản gốc; `verification.status` mặc định
  `UNVERIFIED`/`NEEDS_CLARIFICATION`. Agent không tự điền nguyên văn từ trí nhớ.
- ML **ngoài phạm vi hiện tại** (người dùng đã chốt). Không tạo mã, dependency hay dữ liệu ML. Collection
  `ml_experiments` là tùy chọn trong thiết kế và `database_import.js` vẫn có khai báo quy tắc kiểm tra cho nó
  (không dùng tới); nếu sau này đưa ML vào thì kết quả ML tách riêng, không ghi vào `student_results`.
- Khi `GiaiThich_CoSoDuLieu.md` lệch với `Misa.md`/`BS.md`, theo `GiaiThich_CoSoDuLieu.md`.

## Riêng tư

- Dùng `studentRef` (mã thay thế) cho tới khi Khoa cho phép dùng mã thật (Q09). Chỉ thu thập thông tin cần
  thiết. Không lưu bài làm của sinh viên.
- Không đưa dữ liệu điểm/sinh viên thật vào repo, test, ảnh chụp, commit. Seed và test chỉ dùng dữ liệu giả
  có nhãn.

## Khi gặp điều chưa xác nhận

Các câu hỏi Q01–Q16 (`GiaiThich_CoSoDuLieu.md` §8) còn mở. Với mỗi mục liên quan tới tác vụ:
làm ở dạng cấu hình có trạng thái xác nhận, nêu giả định trong câu trả lời, và nhắc người dùng cập nhật bảng
Q. Không chọn một đáp án rồi viết như thể đã được Khoa duyệt.
