# ADR 0004 — QA_OFFICER xác nhận nội dung CTĐT, PLO, PI

- **Trạng thái:** ĐỀ XUẤT, **chờ Khoa/GVHD xác nhận (Q08)**. Chưa phải quyết định chính thức.
- **Ngày:** 2026-10-28
- **Người soạn:** Claude Code theo yêu cầu của sinh viên TinMH; sinh viên rà soát và ghi tên người chốt: [ĐIỀN]

## Bối cảnh

`plan/F02-ctdt-plo-pi.md` ban đầu cho `PROGRAM_MANAGER` vừa soạn vừa đổi `verification.status` của PLO/PI, nên
người soạn tự xác nhận nội dung của mình. Câu hỏi Q08 ("ai rà soát nghiệp vụ, kỹ thuật") chưa được hỏi Khoa. Rule 9 của
`CLAUDE.md` yêu cầu đổi thiết kế có ADR.

## Phương án

| # | Phương án | Ưu | Nhược |
|---|---|---|---|
| A | `PROGRAM_MANAGER` soạn và tự xác nhận (hiện trạng) | Ít việc nhất | Không có người kiểm độc lập; `VERIFIED` ít giá trị |
| B | `QA_OFFICER` xác nhận; `PROGRAM_MANAGER` chỉ soạn | Có người kiểm độc lập; không thêm vai trò; không đổi enum, CSDL, cam kết 6 vai trò | Cần Khoa chấp nhận QA là bên xác nhận nội dung CTĐT |
| C | Thêm vai trò `DEAN` (Trưởng khoa) xác nhận | Khớp tổ chức nếu Khoa muốn Trưởng khoa ký trong hệ thống | Thêm vai trò thứ 8: đổi enum, validator `database_import.js`, cam kết Bước 3, ma trận test; có thể thành vai trò chỉ để xem |
| D | `DEPARTMENT_HEAD` xác nhận | Không thêm vai trò | Sai tầng: vai trò này là Trưởng bộ môn (học phần, lớp), PLO là cấp chương trình |

## Quyết định (đề xuất tạm)

Chọn **B**. Phạm vi: `program_versions`, `plos`, `pis` (các đối tượng có `verification` thuộc F02). CLO và liên kết
CLO–PI thuộc F03 chưa đổi.

- Chỉ `QA_OFFICER` đổi `verification.status`; `PROGRAM_MANAGER` và `ADMIN` bị 403.
- Giữ `PROGRAM_MANAGER` là người ghi nội dung CTĐT, PLO, PI.
- Nếu Khoa yêu cầu Trưởng khoa ký trong hệ thống, xem xét C trong ADR sau; thêm giá trị vào enum về sau không phá dữ
  liệu cũ.

## Hệ quả

- Đã sửa `F02` (quy tắc 9, US-08/AC-2, AC-3, bảng API, mục 9) và `chot-feature-do-an.md` §6 và dòng US-08.
- Chưa có mã F02 nên chưa sửa mã. Khi làm US-08: kiểm tra quyền ở backend, test 200 cho `QA_OFFICER`, 403 cho
  `PROGRAM_MANAGER` và `ADMIN`, 401 khi chưa đăng nhập; ghi `verifiedBy`, `verifiedAt`.
- F01 chỉ có `canAccessSection`; F02 phải bổ sung kiểm tra quyền trên CTĐT theo `scope.programIds` (ví dụ
  `canAccessProgram`, fail-closed). QA_OFFICER cần cả `programIds` (xác nhận CTĐT) lẫn `courseIds`/`sectionIds` (đọc
  lớp) cho tới khi có ánh xạ CTĐT → lớp (hạn chế đã biết của ADR 0003); cách xử lý chờ sinh viên chọn.
- Vẫn chưa có ràng buộc "người xác nhận ≠ người sửa gần nhất" và "sửa nội dung thì về `UNVERIFIED`"; cân nhắc khi làm
  US-08 (ghi vào AC trước khi code).
- Cập nhật `GiaiThich_CoSoDuLieu.md` §8 (Q08) và `tom-tat-du-an-de-thao-luan.md` khi Khoa trả lời.
