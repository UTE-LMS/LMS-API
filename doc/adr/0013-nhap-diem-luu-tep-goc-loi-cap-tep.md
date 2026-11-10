# ADR 0013 — Nhập điểm từ tệp (F05b): định dạng tệp, nơi lưu tệp gốc (`fileRef`), lỗi cấp tệp

- **Trạng thái:** NHÁP — **chờ sinh viên xác nhận** (chưa phải quyết định đã chốt; chưa hỏi GVHD/Khoa).
- **Ngày:** 2026-11-10
- **Người soạn:** Claude Code theo yêu cầu của sinh viên; sinh viên rà soát và chốt: [ĐIỀN]
- **Liên quan:** `doc/plan/F05b-nhap-diem.md` §4 (quy tắc 5, 12) và §9; `GiaiThich_CoSoDuLieu.md` §4.20–4.22;
  `doc/database_import.js` (khối `import_batches`, `import_row_errors`); TD-32 (Excel); Q13, Q19 (chưa có trả lời).

## 1. Bối cảnh

`doc/plan/F05b-nhap-diem.md` §9 ghi ba việc "cần ADR trước khi code": (1) thư viện đọc Excel và CSV, (2) nơi lưu tệp gốc
(`import_batches.fileRef`), (3) lỗi cấp tệp (tệp rỗng, sai định dạng, thiếu cột bắt buộc) lưu thế nào vì enum
`import_row_errors.errorType` chỉ có 4 giá trị theo dòng (`INVALID_STUDENT_CODE`, `SCORE_OUT_OF_RANGE`,
`DUPLICATE_RECORD`, `MISSING_FIELD`). Nhóm chưa chốt cả ba. Phiên làm F05b chọn phương án **đơn giản nhất** dưới đây để
có thể code và test; mỗi phương án đều đổi được mà không đổi hợp đồng dữ liệu (`database_import.js`).

## 2. Quyết định tạm (đang áp dụng trong mã)

### 2.1 Định dạng tệp: chỉ CSV, tái sử dụng bộ tách dòng của F05a

- Chỉ nhận CSV UTF-8 (chấp nhận BOM, phân cách `,` hoặc `;`, giá trị trong nháy kép), dùng lại `EnrollmentCsvParser.splitLine`
  của F05a nhập danh sách lớp (cùng quy ước số dòng: dòng tiêu đề là dòng 1, `rowNumber`/`sourceRow` là số dòng vật lý trong tệp).
- **Không thêm dependency** (Apache POI, …). Excel (`.xlsx`) chưa làm; giảng viên xuất sang CSV (TD-32).
- `sourceSystem` nhận `CSV` hoặc `MOODLE_EXPORT` (đều là CSV); `EXCEL` bị từ chối 422 cho tới khi có ADR Excel.

### 2.2 Nơi lưu tệp gốc: thư mục cục bộ do cấu hình, `fileRef` = tên tệp do máy chủ đặt

- Tệp gốc ghi vào thư mục `app.score-import.storage-dir` (biến môi trường `SCORE_IMPORT_STORAGE_DIR`, mặc định
  `./data/score-import`), tên `<batchId>.csv` do máy chủ sinh (không dùng tên người dùng gửi, nên không có path traversal).
  `import_batches.fileRef` lưu đúng tên tệp đó; `fileName` giữ tên người dùng gửi.
- Lý do cần **lưu thật** chứ không chỉ lưu băm: (a) `GiaiThich_CoSoDuLieu.md` §4.20 và CLAUDE.md quy tắc 8 nói hệ thống "chỉ lưu
  tệp điểm gốc và tham chiếu"; (b) bước xác nhận (US-24) **đọc lại tệp đã lưu**, kiểm tra SHA-256 khớp `fileChecksum`, rồi kiểm tra
  lại và ghi điểm. Nhờ vậy không cần collection trung gian chứa các dòng đã kiểm tra (thiết kế chỉ có 29 collection).
- Tệp không nằm trong transaction MongoDB: nếu ghi `import_batches` lỗi thì xóa tệp vừa ghi (dọn tay). Tệp mồ côi còn lại
  chỉ khi tiến trình bị tắt đúng giữa hai bước; không ảnh hưởng dữ liệu điểm.

### 2.3 Lỗi cấp tệp: trả 400, **không tạo lô**

- Tệp rỗng, không có dòng dữ liệu, thiếu cột sinh viên, cột ánh xạ không có trong tiêu đề, trùng tiêu đề, quá 5.000 dòng,
  `columnMapping` sai cú pháp/trỏ tới `itemId`/`externalRef` không có trong bài → **400 `VALIDATION_ERROR` hoặc 422**, không ghi
  `import_batches`, không ghi tệp.
- Lỗi theo dòng (sai mã sinh viên, điểm ngoài khoảng, trùng, thiếu ô) mới tạo `import_row_errors` và lô `HAS_ERRORS`.
- Dòng có số ô khác tiêu đề, ô điểm không phải số: vẫn là lỗi theo dòng, dùng loại gần nhất trong enum (`MISSING_FIELD` cho
  thiếu ô/thừa ô; `SCORE_OUT_OF_RANGE` cho "không phải số" kèm `message` nêu rõ), vì enum của validator là đóng.

## 3. Phương án đã cân nhắc

| Việc | Phương án | Ưu | Nhược | Kết luận |
|---|---|---|---|---|
| Nơi lưu tệp | **Thư mục cục bộ** (chọn) | Đơn giản, không dependency, không phình DB | Container không có volume thì mất tệp khi khởi tạo lại; không sao lưu tự động | Chọn; staging phải gắn volume |
| | Chỉ lưu SHA-256 (`fileRef = sha256:…`), không lưu tệp | Không IO, không rủi ro mất tệp | Không tải lại được tệp gốc; xác nhận phải giữ dòng đã kiểm tra ở nơi khác (collection mới) hoặc kiểm tra lại từ tệp tải lần hai | Loại: trái "lưu tệp điểm gốc" và buộc thêm collection |
| | Lưu byte trong MongoDB (GridFS hoặc trường `binary`) | Một nơi lưu, có transaction với DB (GridFS không tham gia transaction) | Thêm bucket/collection ngoài 29 collection; dữ liệu điểm sinh viên nằm lẫn CSDL | Loại |
| | Object storage (S3/MinIO) | Bền, mở rộng | Thêm hạ tầng + SDK + khóa truy cập; vượt phạm vi đồ án | Loại |
| Lỗi cấp tệp | **400, không tạo lô** (chọn) | Không lô rác, không lẫn lỗi cấp tệp với lỗi dòng, enum validator giữ nguyên | Người dùng không thấy lịch sử lần tải sai | Chọn |
| | Tạo lô `HAS_ERRORS` với 1 dòng lỗi `rowNumber = 0` | Có lịch sử mọi lần tải | `rowNumber = 0` không phải số dòng thật; không có `errorType` phù hợp (phải thêm giá trị enum ở `database_import.js`) | Loại (cần đổi validator) |
| Excel | Apache POI (Apache-2.0) | Đọc `.xlsx` trực tiếp | Dependency lớn, nhiều tầng phụ thuộc; cần ADR riêng, xử lý công thức/ô gộp/định dạng ngày-số | Hoãn (TD-32) |

## 4. Hệ quả

- Cần đặt `SCORE_IMPORT_STORAGE_DIR` (hoặc dùng mặc định) và gắn volume khi chạy container; thêm vào hướng dẫn triển khai
  khi staging được chọn. Thư mục mặc định `data/` được `.gitignore`.
- Nếu sinh viên đổi sang "tạo lô `HAS_ERRORS` cho lỗi cấp tệp" thì phải thêm giá trị `errorType` (ví dụ `FILE_ERROR`) vào
  `database_import.js` và `ImportErrorType`; mã F05b chỉ cần đổi ở một chỗ (`ImportBatchServiceImpl.createBatch`).
- Nếu đổi nơi lưu: chỉ thay hiện thực `ImportFileStorage` (một interface 3 phương thức); `fileRef` vẫn là chuỗi.
- Không ảnh hưởng quy tắc nghiệp vụ: `emptyCellAs` bắt buộc chọn, `reimportPolicy` chỉ `REJECT_ON_CONFLICT` được chấp nhận.

## 5. Câu hỏi còn mở (cho sinh viên / GVHD)

1. Nơi lưu tệp gốc trên staging/production: thư mục có volume, hay object storage? Thời hạn giữ tệp?
2. Lỗi cấp tệp: chấp nhận "400, không tạo lô" hay muốn lịch sử (cần thêm `errorType`)?
3. Q13: quy ước đánh dấu vắng/chưa chấm/miễn trong tệp (cột trạng thái? ký hiệu?) — chưa có, nên tệp hiện chỉ sinh được
   `SCORED`, `BLANK`, `MISSING`.
4. Q19: khi nào cho `OVERWRITE`/`UPDATE_BLANK_ONLY`, và mặc định nào — hiện bắt buộc người nhập chọn rõ, chỉ nhận `REJECT_ON_CONFLICT`.
5. Có cần Excel không (TD-32)? Nếu có: viết ADR Apache POI hoặc phương án khác.
