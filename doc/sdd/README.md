# SDD — Thiết kế hệ thống (mục lục sơ đồ)

> **Dự thảo do AI soạn, chờ hai sinh viên đọc và viết lại.** Sơ đồ viết bằng Mermaid để diff được trong Git. Đối chiếu mã
> ngày **2026-11-02** (`main`, commit `54ef80e`). Rule 05: sơ đồ phải khớp 100% mã; hội đồng đối chiếu ngẫu nhiên. **Khi đổi mã
> ảnh hưởng thiết kế, sửa sơ đồ trong cùng PR.**

## Mục lục

| # | File | Loại sơ đồ | Phạm vi (đã có mã) |
|---|---|---|---|
| 1 | [`01-context.md`](01-context.md) | Context | Người dùng, LMS-UI, LMS-API, MongoDB, GitHub Actions |
| 2 | [`02-component.md`](02-component.md) | Component / package | 15 package thật của `com.hcmute.lms.lmsapi`, 7 controller và bảng endpoint, ma trận phụ thuộc tự tính từ `import` |
| 3 | [`03-erd-collection.md`](03-erd-collection.md) | ERD / class của collection | 9 collection có `@Document` (đối chiếu `doc/database_import.js`) |
| 4 | [`04-sequence.md`](04-sequence.md) | Sequence | Đăng nhập + JWT; kiểm tra JWT/quyền mỗi request; tạo phiên bản CTĐT/PLO/PI; xác nhận nội dung có kiểm tra phạm vi; ghi audit trong transaction |
| 5 | [`05-deployment.md`](05-deployment.md) | Deployment | Máy dev (Docker MongoDB) và CI; staging/production **chưa chọn nền tảng** |

Đủ 5 loại sơ đồ mà rule 05 liệt kê (context, component, ERD/class, sequence, deployment) cho **phần đã có mã**.

## Còn thiếu (không có trong bộ sơ đồ này vì chưa có mã)

- Sequence **nhập điểm** (F05b) và **chạy đo lường** (F06); ERD/class của 21 collection chưa có `document`
  (học phần/CLO, bài đánh giá, học kỳ/lớp/sinh viên, điểm, quy tắc đo lường, kết quả, báo cáo, cải tiến).
  Mô tả dự kiến chỉ có ở `doc/plan/` (bản nháp). Bổ sung đúng PR đưa mã tương ứng vào.
- Sơ đồ triển khai staging/production: chờ quyết định nền tảng (Q06/Q07) rồi ghi ADR.
- Sơ đồ của repo `LMS-UI` (cấu trúc màn hình, luồng UI): thuộc repo đó.
- Mô hình quy trình nghiệp vụ (BPMN hoặc tương đương) và từ điển dữ liệu đầy đủ mà rule 05 nhắc: từ điển dữ liệu hiện nằm ở
  `doc/GiaiThich_CoSoDuLieu.md`; chưa có tài liệu riêng cho quy trình nghiệp vụ.

## Cách kiểm tra sơ đồ còn khớp mã

1. Component: chạy lệnh ở mục 3 của `02-component.md` và so với bảng phụ thuộc; đếm lớp trong các package.
2. Endpoint: `grep -rnE "@(Get|Post|Put|Delete|Patch|Request)Mapping|@PreAuthorize" src/main/java/com/hcmute/lms/lmsapi/controller`.
3. Collection: so các lớp trong `document/` với `COLLECTIONS` trong `doc/database_import.js` (tên collection, trường, chỉ mục).
4. Sequence: mở các phương thức nêu ở bảng cuối mục 8 của `04-sequence.md`, đọc từng bước.
5. Cú pháp Mermaid: dán từng khối vào trình xem Mermaid (GitHub hiển thị trực tiếp). Người soạn đã chạy bộ phân tích cú pháp
   `mermaid@11.4.1` (chế độ `parse`, không dựng ảnh) trên cả 9 khối và không có lỗi cú pháp; **chưa** kiểm tra giao diện hiển thị.

Liên quan: [`doc/srs/README.md`](../srs/README.md), [`doc/test-plan.md`](../test-plan.md), `doc/adr/`.
