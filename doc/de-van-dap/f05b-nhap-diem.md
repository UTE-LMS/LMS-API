# Để vấn đáp — F05b: Nhập điểm từ tệp CSV (US-21…24)

> Viết cho người **không** viết đoạn mã này (rule 01 §4). Mỗi mục trả lời 4 câu của hội đồng (Rubric Mục 6.2): làm gì, vì sao có trong đề tài,
> tham số/thư viện đến từ đâu, bỏ đi thì sao. Quyết định tạm nằm ở `doc/plan/F05b-nhap-diem.md` §11 và `doc/adr/0013-…` (NHÁP, chờ sinh viên xác nhận).
> Các con số trong ví dụ (Q1 tối đa 4, Q2 tối đa 6, lớp 45/40) là **ví dụ minh họa**, không phải cấu hình hay quy định của Khoa.

## 1. `ImportBatchServiceImpl#confirmBatch` — xác nhận trong một transaction (US-24)

- **Làm gì:** biến một lô đã kiểm tra (`VALIDATED`) thành điểm thật. Trong **một** transaction: đọc lại tệp đã lưu và kiểm tra lại mọi dòng, ghi lô `CONFIRMED`,
  chèn toàn bộ `scores`, chuyển bài `APPROVED → SCORE_ENTERED` nếu là lô đầu, ghi `audit_logs` `IMPORT_CONFIRM`. Bước nào lỗi thì **không còn gì được ghi**.
- **Vì sao có:** điểm ghi nửa vời (ví dụ 37 trong 60 sinh viên) làm sai CLO/PI mà không ai biết; người dùng cũng không biết phải nhập lại phần nào. Thiết kế CSDL
  (§6) và rule 02 yêu cầu transaction nhiều document, nên MongoDB phải chạy **replica set** (Docker `lms-mongo`, CI service).
- **Tham số/thư viện từ đâu:** `@Transactional` + `MongoTransactionManager` (bean ở `MongoConfig`, Spring Data MongoDB). Không thư viện mới. Thứ tự các bước có chủ đích:
  ghi lô trước (có khóa lạc quan `@Version`) để hai người xác nhận cùng lúc thì người sau thất bại **trước khi** ghi 10.000 điểm; chỉ mục duy nhất
  `scores(sectionId, assessmentId, itemId, studentId)` là lưới an toàn cuối cho trùng bản ghi.
- **Bỏ đi thì sao:** đã thử: tạm bỏ `@Transactional` thì cả 3 test `ImportConfirmRollbackIntegrationTest` đỏ (còn lại điểm, lô vẫn có trạng thái cũ, hoặc audit mồ côi).
  Bỏ bước "kiểm tra lại" thì ghi danh/điểm đổi giữa hai bước sẽ lọt vào dữ liệu.

## 2. `ScoreRowValidator` — kiểm tra từng dòng/cột (US-22, US-23)

- **Làm gì:** với mỗi dòng tệp: kiểm mã sinh viên (trống, trùng trong tệp, không đang `ENROLLED`), kiểm từng ô điểm (số hợp lệ, `0 ≤ điểm ≤ maxScore`), kiểm (sinh viên, câu)
  đã có điểm chưa (`REJECT_ON_CONFLICT`). Trả về mọi lỗi kèm `rowNumber` (số dòng vật lý, tiêu đề là 1), `column`, loại lỗi, giá trị gốc; và các điểm sẽ ghi cho những dòng đúng.
- **Vì sao có:** KPI K2 của bản cam kết: phát hiện 100% lỗi cài sẵn đúng dòng/cột và không báo nhầm dòng đúng. Giảng viên sửa tệp theo số dòng này.
- **Tham số/thư viện từ đâu:** `maxScore` lấy từ `assessments.items[].maxScore` (không hardcode); so sánh bằng `BigDecimal` để `4,01 > 4` chính xác. Loại lỗi là enum đóng của
  `database_import.js`. Lớp này **thuần** (không truy cập CSDL) nên test bằng ví dụ tính tay (`ScoreRowValidatorTest`).
- **Bỏ đi thì sao:** không có báo lỗi theo dòng, hoặc cả lô thất bại bằng một lỗi validator MongoDB chung (mã 121) không chỉ ra dòng/cột nào sai (validator vẫn là lưới an toàn cuối: chặn `rawScore > maxScore`, `BLANK ≠ 0`).
  Dòng ngắn hơn tiêu đề bị báo `MISSING_FIELD` chứ không âm thầm hiểu là ô trống (không đoán).

## 3. `BLANK` ≠ `MISSING` ≠ `ABSENT` (US-23/AC-1)

- **Làm gì:** ô điểm có chữ số → `SCORED` (kể cả `0`). Ô **tồn tại nhưng trống** → theo `emptyCellAs` do **người nhập chọn** cho cả lô: `BLANK` (đã dự thi, bỏ trống, `rawScore = 0` có ghi nhận)
  hoặc `MISSING` (thiếu dữ liệu, `rawScore = null`). Không chọn thì 400.
- **Vì sao có:** nguyên tắc "thiếu dữ liệu ≠ 0" (rule 02). Coi ô trống là 0 làm hạ CLO của người vắng; coi bỏ trống là thiếu thì bỏ sót người làm bài bỏ câu.
  Hệ thống không biết đó là trường hợp nào nên không được đoán. `ABSENT`/`NOT_GRADED`/`EXEMPT` chưa nhập được qua tệp vì Q13 (quy ước đánh dấu, và vắng có vào mẫu số không) chưa có trả lời.
- **Tham số/thư viện từ đâu:** enum `EmptyCellAs` khớp validator `import_batches.emptyCellAs`; trạng thái điểm khớp `SCORE_STATUS_VALUES`. Validator MongoDB ép `BLANK ⇒ 0` và `MISSING ⇒ null`.
- **Bỏ đi thì sao:** mặc định 0 hoặc mặc định null đều là quyết định nghiệp vụ ngầm; bản cố định giá trị mặc định sẽ sai với một nửa các lớp.

## 4. Tệp gốc, checksum và `sourceRow` — truy vết và chống nhập trùng (US-21)

- **Làm gì:** tính SHA-256 của tệp; nếu đã có lô `CONFIRMED` cùng (lớp, bài, checksum) → 409. Tệp được lưu ở thư mục cấu hình dưới tên `<batchId>.csv` (`fileRef`); mỗi điểm lưu `sourceRow`
  (dòng trong tệp) và `importBatchId`. Bước xác nhận đọc lại đúng tệp đó và kiểm tra checksum còn khớp.
- **Vì sao có:** nhập hai lần cùng một tệp sẽ nhân đôi hoặc xung đột điểm; truy vết kết quả → điểm → lô + dòng tệp gốc là yêu cầu của rule 02. Lưu tệp (chứ không chỉ băm) vì CSDL §4.20 nói hệ thống
  lưu tệp điểm gốc và tham chiếu, và để xác nhận không cần collection trung gian.
- **Tham số/thư viện từ đâu:** `java.security.MessageDigest` (JDK), `HexFormat`; thư mục từ `app.score-import.storage-dir`. Phương án đã cân nhắc nằm ở ADR 0013 (chưa chốt).
- **Bỏ đi thì sao:** không phát hiện nhập trùng, không truy ngược được một điểm về dòng tệp; tệp lưu trong container không gắn volume sẽ mất khi tạo lại (TD-38).

## 5. Phân quyền và điều kiện nhập

- **Làm gì:** ghi (tải, kiểm tra, xác nhận, hủy) chỉ cho `LECTURER` của lớp (`hasRole('LECTURER')` **và** `canAccessSection/canAccessImportBatch`); ADMIN không ghi điểm (đúng plan §6, giống F04).
  Chỉ nhập khi bài `APPROVED`/`SCORE_ENTERED`, `dataGranularity = ITEM`, lớp không `LOCKED`, học kỳ `OPEN` — kiểm lại ở cả ba bước.
- **Vì sao có:** điểm chỉ gắn vào cấu trúc bài đã được Trưởng bộ môn duyệt và khóa (NEW-01); học kỳ đã khóa sổ điểm không được đổi âm thầm (US-18/AC-2).
- **Bỏ đi thì sao:** giảng viên lớp khác ghi đè điểm; điểm vào bài chưa duyệt, rồi bài được sửa ma trận làm lệch điểm cũ.

## Câu hội đồng có thể hỏi thêm

1. *Hai giảng viên bấm xác nhận cùng lúc?* Khóa lạc quan `@Version` trên lô + chỉ mục duy nhất của `scores`; test `ImportBatchConfirmIntegrationTest#us24_concurrentConfirmOnlyOneWins`.
2. *Vì sao không cho xác nhận phần hợp lệ của lô có lỗi?* Chọn mặc định an toàn (plan §4 quy tắc 9); xác nhận một phần làm điểm lớp không đầy đủ mà người dùng khó nhận ra.
3. *Vì sao lỗi cấp tệp không tạo lô?* Enum `errorType` của CSDL không có loại lỗi cấp tệp; thêm giá trị enum là đổi hợp đồng dữ liệu (ADR 0013, câu hỏi mở).
4. *Chưa làm gì?* US-25 (sửa điểm), `OVERWRITE`/`UPDATE_BLANK_ONLY` (Q19), `ABSENT`/`NOT_GRADED`/`EXEMPT` qua tệp (Q13), Excel (TD-32), gỡ điểm lô đã xác nhận (TD-36).
