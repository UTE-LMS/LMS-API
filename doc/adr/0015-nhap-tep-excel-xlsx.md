# ADR 0015 — Nhập tệp Excel `.xlsx` cho mọi chức năng nhập (đóng TD-32)

- **Trạng thái:** ĐÃ CHỐT theo lựa chọn của sinh viên TinMH trong phiên làm việc ngày 2026-11-18 (phạm vi, thư viện,
  cách xử lý UI, tệp mẫu). **Chưa hỏi GVHD/Khoa**; sinh viên rà soát lại: [ĐIỀN]
- **Ngày:** 2026-11-18
- **Người soạn:** Claude Code theo yêu cầu của sinh viên.
- **Liên quan:** ADR 0013 (đã ghi "Excel hoãn, cần ADR riêng"); TD-32; `doc/ke-hoach-xay-dung.md` (US-20: CSV/Excel).

## 1. Bối cảnh

Hệ thống có 9 chỗ nhập tệp, tất cả chỉ nhận `.csv`: người dùng, khoa, học phần, CTĐT (PLO/PI), học phần trong CTĐT,
đề cương (CLO), lớp học phần, ghi danh (theo lớp và hàng loạt) và điểm. Giảng viên làm việc trong Excel nên phải xuất
sang CSV, dễ lỗi mã hóa tiếng Việt và dấu phân cách (`,` hay `;`). ADR 0013 đã hoãn Excel vì cần chọn thư viện và nêu
cách xử lý công thức, ô gộp, định dạng số/ngày.

## 2. Quyết định

1. **Nhận `.xlsx` ở cả 9 chỗ nhập**; `.csv` giữ nguyên hành vi. `.xls` (Excel cũ) bị từ chối với hướng dẫn "lưu thành
   .xlsx hoặc .csv".
2. **Đổi `.xlsx` sang CSV rồi dùng lại bộ đọc CSV hiện có** (`XlsxCsvConverter`, `ImportFiles`). Không viết bộ đọc
   Excel riêng cho từng chức năng, nên hai định dạng luôn có cùng quy tắc kiểm tra cột, số dòng và thông báo lỗi.
3. **Thư viện: Apache POI `poi-ooxml` 6.0.0** (Apache-2.0; thêm 12 jar phụ thuộc gồm xmlbeans, commons-compress,
   commons-io, log4j-api).
4. **Tệp mẫu tải về có thêm `.xlsx`**: các endpoint mẫu nhận `?format=xlsx` (mặc định `csv`); mẫu `.xlsx` được tạo
   từ chính mẫu CSV nên hai bản không thể lệch nhau.
5. **Nhập điểm:** tệp gốc `.xlsx` được lưu nguyên trạng (`<batchId>.xlsx`), `fileChecksum` tính trên tệp gốc, lần
   kiểm tra/xác nhận đọc lại tệp đã lưu rồi đổi sang CSV. `sourceSystem` tự là `EXCEL` khi tệp là `.xlsx`; khai báo
   `EXCEL` với tệp `.csv` bị từ chối 422. Thêm `POST .../import-batches/preview` trả tên cột để giao diện tự ánh xạ
   (trình duyệt không đọc được `.xlsx`).

## 3. Quy tắc đọc `.xlsx`

| Chủ đề | Quy tắc | Lý do |
|---|---|---|
| Trang tính | Chỉ trang tính đầu tiên; dòng 1 là tiêu đề | Giống tệp CSV một bảng |
| Số dòng | Dòng thứ n của Excel là dòng thứ n của CSV, dòng trống vẫn giữ chỗ | Báo lỗi "dòng 7" khớp số dòng người dùng thấy |
| Ô số | Lấy giá trị hiển thị, dấu thập phân luôn là dấu chấm | `2110001` không thành `2110001.0`; không phụ thuộc locale máy chủ |
| Ô công thức | Lấy giá trị đã tính lưu sẵn trong tệp; **không thực thi công thức** | Công thức có thể tham chiếu tệp ngoài; không cần thực thi để đọc dữ liệu |
| Ô gộp | Chỉ ô trên cùng bên trái có giá trị | Hành vi mặc định của Excel |
| Xuống dòng trong ô | Đổi thành khoảng trắng | Các bộ đọc CSV xử lý theo từng dòng |
| Ô phần trăm / ngày | Lấy đúng như hiển thị (`50%`, ngày theo định dạng ô) | Không tự suy diễn; nếu sai dạng, bộ kiểm tra theo chức năng sẽ báo lỗi dòng |
| Giới hạn | Tối đa 20.000 dòng và 200 cột có dữ liệu; kích thước tuân giới hạn upload của Spring | Chặn tệp lớn bất thường; POI có sẵn kiểm tra zip-bomb |
| Tệp hỏng/đặt mật khẩu | 400 "Không đọc được tệp .xlsx..." | Không lộ chi tiết nội bộ |

## 4. Phương án đã cân nhắc

| Phương án | Ưu | Nhược | Kết luận |
|---|---|---|---|
| Apache POI | Chuẩn phổ biến, dễ giải thích, vừa đọc vừa ghi (cần cho tệp mẫu `.xlsx`), có giới hạn chống zip-bomb | Nặng, nhiều phụ thuộc | **Chọn** |
| FastExcel Reader 0.20.2 | Nhẹ, ít phụ thuộc | Chỉ đọc nên không tạo được tệp mẫu `.xlsx`; ít người biết | Loại |
| Bộ đọc Excel riêng cho từng chức năng | Kiểm soát từng chỗ | 9 bản sao logic, dễ lệch hành vi với CSV | Loại |
| Đọc `.xlsx` trong trình duyệt (thư viện npm) | Tự ánh xạ cột tức thì | Thêm dependency thứ hai, tăng bundle (đã vượt ngân sách 2 MB) | Loại; dùng endpoint `preview` |

## 5. Hệ quả

- `pom.xml` thêm `poi-ooxml`; cần theo dõi cảnh báo bảo mật của POI và các phụ thuộc của nó.
- `ImportFileStorage.save` nhận thêm `extension` (`.csv` hoặc `.xlsx`); `fileRef` có thể là `<24 hex>.xlsx`.
- Tệp `.xlsx` lớn tốn bộ nhớ hơn CSV vì POI nạp cả gói vào bộ nhớ; giới hạn dòng/cột và giới hạn upload là hàng rào.
  Nếu sau này cần tệp rất lớn, đổi sang đọc dạng luồng (SAX) mà không đổi giao diện `XlsxCsvConverter`.
- Chỉ đọc giá trị: định dạng, màu, nhiều trang tính, hình, macro đều bị bỏ qua.

## 6. Câu hỏi còn mở

1. GVHD/Khoa có chấp nhận thêm thư viện POI cho đề tài không?
2. Cần đọc trang tính khác trang đầu (chọn theo tên) không? Hiện chưa làm.
