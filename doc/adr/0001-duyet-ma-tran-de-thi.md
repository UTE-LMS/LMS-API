# ADR 0001 — Duyệt ma trận đề thi trước khi nhập điểm

- **Trạng thái:** ĐỀ XUẤT, **chờ GVHD xác nhận (Q11)**. Chưa phải quyết định chính thức.
- **Ngày:** 2026-10-27
- **Người soạn:** Claude Code theo yêu cầu của sinh viên; sinh viên rà soát và ghi tên người chốt: [ĐIỀN]

## Bối cảnh

Phạm vi đã chốt (`GiaiThich_CoSoDuLieu.md` §1, `CLAUDE.md` quy tắc 1) là *tiếp nhận và đo lường kết quả có sẵn sau
khi thi*; không tổ chức thi, không sinh đề, không duyệt đề. Thiết kế CSDL v0.5 (cập nhật theo `PLO_DB_UPDATE.md`) thêm
cho `assessments`: `status` (`DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `SCORE_ENTERED`, `LOCKED`), `isLocked`,
`approval` (người gửi/duyệt, lịch sử), `coverageSummary` (độ phủ theo CLO và Bloom), `exportedFiles` (xuất phôi in), và
vai trò `DEPARTMENT_HEAD`. Điều này đi ngoài mệnh đề "không duyệt đề". Rule 9 của `CLAUDE.md` yêu cầu mọi đổi phạm vi
có ADR và xin phép.

## Phương án

| # | Phương án | Ưu | Nhược |
|---|---|---|---|
| A | Giữ nguyên v0.5: Trưởng bộ môn duyệt *ma trận ánh xạ* CLO–PI; chỉ nhập điểm khi `APPROVED`/`SCORE_ENTERED` | Ánh xạ CLO–PI được rà soát trước khi điểm gắn vào; khóa `items` giúp kết quả tái lập được; khớp tài liệu CSDL | Thêm một story (NEW-01), một vai trò, một màn hình; cần GVHD đồng ý đổi phạm vi |
| B | Giảng viên tự xác nhận ma trận (`DRAFT → APPROVED`), bỏ bước Trưởng bộ môn; vẫn khóa `items` | Giữ được khóa cấu trúc và thứ tự "ma trận trước, điểm sau", ít việc hơn, không cần vai trò mới | Không có người rà soát độc lập; `DEPARTMENT_HEAD`, `approval` trong CSDL thừa một phần |
| C | Bỏ hẳn quy trình trạng thái; `items` chỉ bị khóa khi đã có điểm (như v0.4) | Phạm vi nhỏ nhất, đúng phạm vi cũ | Không có chốt ánh xạ trước khi nhập điểm; trái hướng của `PLO_DB_UPDATE.md` |

## Quyết định

**[ĐIỀN sau khi GVHD trả lời.]** Đề xuất tạm thời của người soạn: làm theo A trong cam kết (`chot-feature-do-an.md`,
NEW-01), nhưng **không viết mã NEW-01 cho tới khi GVHD trả lời**; nếu GVHD không đồng ý A thì chuyển sang B (ít thay đổi
nhất so với CSDL hiện có).

## Điều giữ nguyên dù chọn phương án nào

- Chỉ duyệt *ma trận ánh xạ chuẩn đầu ra*, không duyệt nội dung câu hỏi, không sinh đề.
- `assessments.exportedFiles` (xuất phôi in đề) không có tính năng.
- Khi `isLocked`, API cấm sửa `items[]`; `totalPoints` phải khớp tổng `items.maxScore` (dung sai 0,001).

## Hệ quả

- Nếu chọn A hoặc B: thêm AC cho NEW-01 trước khi code; test cho chuyển trạng thái hợp lệ/không hợp lệ, vai trò không
  được duyệt (403), nhập điểm khi chưa `APPROVED` bị từ chối; ghi `audit_logs` (`ASSESSMENT_APPROVE`/`ASSESSMENT_REJECT`).
- Nếu chọn C: sửa `database_import.js` và `GiaiThich_CoSoDuLieu.md` bỏ các trường duyệt; cập nhật kế hoạch và cam kết.
- Cập nhật `ke-hoach-xay-dung.md` (R11), `cam-ket-san-pham-metric.md` (NEW-01), `CLAUDE.md` và rule 02 theo quyết định.
