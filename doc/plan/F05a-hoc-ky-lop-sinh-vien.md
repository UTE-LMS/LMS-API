# F05a — Học kỳ, lớp học phần, sinh viên, ghi danh

Story: **US-18** học kỳ + khóa sổ điểm (C), **US-19** lớp học phần + giảng viên phụ trách (C), **US-20** nhập danh sách lớp →
sinh viên + ghi danh (C). Giai đoạn P3. Chủ trì đề xuất: IT (IBS cho quy ước dữ liệu). Package: `academic`.
Phụ thuộc: `00`, `F01` (giảng viên là `users`), `F03` (`courses`, `syllabi`).

## 1. Mục tiêu

Biết **ai học lớp nào, học kỳ nào, đề cương nào**. Số ghi danh chính là **mẫu số** của tỷ lệ đạt cấp lớp, nên dữ liệu ghi danh phải
đúng và truy vết được. Học kỳ có thể bị **khóa sổ điểm** để chặn nhập/sửa điểm.

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` nguyên tắc 9 và 13, §4.16–4.19, Q09.
- `database_import.js`: khối `terms`, `course_sections`, `students`, `enrollments`.

## 3. Collection và trường chính

| Collection | Trường chính |
|---|---|
| `terms` | `code` (duy nhất, vd `2026-HK1`), `academicYear`, `startDate`, `endDate`, `status` (`OPEN`/`SCORE_LOCKED`/`CLOSED`) |
| `course_sections` | `code` (duy nhất), `courseId`, `syllabusId`, `termId`, `lecturerIds[]`, `enrolledCount`, `status` (`OPEN`/`CLOSED`/`LOCKED`) |
| `students` | `studentRef` (duy nhất, **mã thay thế**), `userId` (null được; nối `users` cho NEW-03), `cohort`, `majorCode`, `programVersionId`, `status` (`ACTIVE`/`SUSPENDED`/`GRADUATED`/`DROPPED_OUT`), `fullName`, `email` (tùy chọn, chỉ khi Khoa cho phép) |
| `enrollments` | `sectionId`, `studentId`, `status` (`ENROLLED`/`WITHDRAWN`), `enrolledAt`. Duy nhất `(sectionId, studentId)` |

## 4. Quy tắc nghiệp vụ

1. **Mẫu số = ghi danh.** `counts.enrolled` của F06 là số `enrollments` có `status = ENROLLED` của lớp. `WITHDRAWN` **không** thuộc mẫu số
   nhưng giữ bản ghi (không xóa). Cách tính `enrolledCount` trong `course_sections`: dẫn xuất từ `enrollments`, cập nhật cùng lúc, hoặc tính khi đọc **[ĐỀ XUẤT]** — chọn một, ghi lại.
2. **Khóa sổ điểm:** `terms.status = SCORE_LOCKED` hoặc `CLOSED` ⇒ không nhập điểm mới, không sửa điểm (F05b kiểm tra). `course_sections.status = LOCKED` có tác dụng tương tự cho lớp đó.
3. `course_sections.syllabusId` phải là đề cương của đúng `courseId`; **[ĐỀ XUẤT]** chỉ cho đề cương `CONFIRMED` khi mở lớp. Một lớp gắn đúng một đề cương (bài đánh giá và CLO của lớp bám theo).
4. `lecturerIds` chỉ chứa `users` có vai trò `LECTURER`; phạm vi giảng viên ở F01 phải tương thích (lớp mình).
5. **Riêng tư:** dùng `studentRef` (mã thay thế) cho tới khi Khoa cho phép mã thật (Q09). `fullName`, `email` chỉ lưu khi được phép. Không đưa dữ liệu sinh viên thật vào seed/test/ảnh chụp/commit. Không lưu bài làm của sinh viên.
6. `studentRef` duy nhất toàn hệ thống; nhập lại danh sách có sinh viên đã tồn tại thì **dùng lại bản ghi**, không tạo trùng.
7. Một sinh viên chỉ ghi danh một lần cho một lớp (`(sectionId, studentId)` duy nhất).

## 5. Stories và acceptance criteria (nháp)

| ID | AC | Trạng thái |
|---|---|---|
| US-18/AC-1 | Tạo học kỳ với `code` mới, `startDate ≤ endDate` → thành công; trùng `code` → 409; `startDate > endDate` → 400/422 | **ĐÃ HIỆN THỰC trên nhánh F03 (Issue #28 - làm trước cho F03); PR chưa mở/chưa merge** |
| US-18/AC-2 | Đổi `status` sang `SCORE_LOCKED` → ghi nhận trạng thái và log; chặn nhập/sửa điểm (test cùng F05b) | **MỘT PHẦN (chưa hoàn thành):** đã có trạng thái `SCORE_LOCKED` và việc mở lại cần lý do (xem AC-3), có log ứng dụng, chưa ghi `audit_logs`; vế **từ chối nhập/sửa điểm CHỜ F05b** (chưa có mã nhập điểm) |
| US-18/AC-3 | Mở lại học kỳ `SCORE_LOCKED → OPEN` chỉ ADMIN/PROGRAM_MANAGER và có lý do giải trình | **ĐÃ HIỆN THỰC trên nhánh F03 (Issue #28 - làm trước cho F03); PR chưa mở/chưa merge** |
| US-19/AC-1 | Tạo lớp với `courseId`, `syllabusId` (cùng học phần), `termId`, ≥ 1 giảng viên → thành công | **ĐÃ HIỆN THỰC** (xem §11) |
| US-19/AC-2 | `syllabusId` không thuộc `courseId`, hoặc `termId`/`lecturerIds` không tồn tại → 422 | **ĐÃ HIỆN THỰC** (xem §11) |
| US-19/AC-3 | Trùng `code` lớp → 409 | **ĐÃ HIỆN THỰC** (xem §11) |
| US-19/AC-4 | Giảng viên chỉ thấy lớp mình phụ trách (xem F01) | **ĐÃ HIỆN THỰC** (xem §11) |
| US-20/AC-1 | Tải tệp CSV/Excel danh sách lớp: mỗi dòng có `studentRef`; sinh viên mới được tạo, sinh viên cũ được dùng lại; tất cả được ghi danh `ENROLLED` | **ĐÃ HIỆN THỰC cho tệp CSV** (xem §11; Excel chờ ADR cùng F05b) |
| US-20/AC-2 | Dòng thiếu `studentRef`, trùng trong tệp, hoặc đã ghi danh lớp đó → báo lỗi theo dòng, không ghi nửa chừng (một thao tác thống nhất) | **ĐÃ HIỆN THỰC cho tệp CSV** (xem §11; Excel chờ ADR cùng F05b) |
| US-20/AC-3 | Tệp rỗng, sai cột, sai định dạng → 400 với thông báo rõ | **ĐÃ HIỆN THỰC cho tệp CSV** (xem §11; Excel chờ ADR cùng F05b) |
| US-20/AC-4 | Sau khi nhập, số `ENROLLED` của lớp đúng bằng số dòng hợp lệ; rút khỏi lớp chuyển `WITHDRAWN`, mẫu số giảm tương ứng | **ĐÃ HIỆN THỰC cho tệp CSV** (xem §11; Excel chờ ADR cùng F05b) |

## 6. API đề xuất **[ĐỀ XUẤT]**

| Phương thức | Đường dẫn | Quyền | Trạng thái |
|---|---|---|---|
| GET/POST/PUT/DELETE | `/api/v1/terms`, `/api/v1/terms/{id}`; `PUT /api/v1/terms/{id}/status` | ADMIN, PROGRAM_MANAGER; đọc: authenticated | **ĐÃ HIỆN THỰC** |
| GET | `/api/v1/sections?termId=&courseId=` | authenticated; chỉ trả lớp người gọi đọc/quản lý được | **ĐÃ HIỆN THỰC** |
| GET | `/api/v1/sections/{id}` | đọc được lớp (giảng viên lớp mình, ADMIN, scope) hoặc quản lý được lớp | **ĐÃ HIỆN THỰC** |
| POST | `/api/v1/sections` | ADMIN; PROGRAM_MANAGER có CTĐT chứa học phần trong `scope.programIds` | **ĐÃ HIỆN THỰC** |
| PUT | `/api/v1/sections/{id}` (chỉ `lecturerIds`), `PUT /api/v1/sections/{id}/status` | như POST | **ĐÃ HIỆN THỰC** |
| POST | `/api/v1/sections/{id}/enrollments/import` (multipart `file`, CSV) | LECTURER lớp mình, ADMIN (`canAccessSection` WRITE) | **ĐÃ HIỆN THỰC** |
| GET | `/api/v1/sections/{id}/enrollments?status=` | đọc được lớp | **ĐÃ HIỆN THỰC** |
| PUT | `/api/v1/enrollments/{id}/status` (rút/ghi danh lại) | LECTURER lớp mình, ADMIN | **ĐÃ HIỆN THỰC** |

## 7. Kiểm tra tham chiếu ở service

`courseId`, `syllabusId` (cùng `courseId`), `termId`, `lecturerIds` (người dùng vai trò LECTURER) tồn tại; `studentId`, `sectionId` của
ghi danh tồn tại; sinh viên và lớp cùng CTĐT khi có `programVersionId` **[ĐỀ XUẤT]** (cảnh báo, không chặn vì sinh viên có thể học môn ngoài khóa).

## 8. Test bắt buộc

Mẫu số: lớp 45 ghi danh gồm 3 người `WITHDRAWN` thì `ENROLLED = 42` (số minh họa); tệp có dòng trùng/thiếu; nhập lại cùng tệp (idempotent,
không nhân đôi sinh viên); học kỳ khóa → chặn nhập điểm; giảng viên ngoài lớp; `studentRef` trùng giữa hai tệp; tệp lớn (hiệu năng cơ sở).

## 9. Không làm / chưa quyết

- Không làm: đồng bộ trực tiếp với hệ thống đào tạo/Moodle; lưu bài làm; lưu dữ liệu cá nhân ngoài những gì cần.
- Chờ: Q09 (mã thật hay thay thế), Q17 (bộ môn), chọn **ba học phần thử nghiệm** (chưa chọn; hệ thống phải chạy với học phần bất kỳ).
- Thư viện đọc Excel/CSV dùng chung với F05b: quyết bằng một ADR (xem F05b).

## 10. Để vấn đáp

Vì sao ghi danh là mẫu số (báo cáo phải nói rõ lớp có bao nhiêu người, bao nhiêu đủ dữ liệu); vì sao dùng `studentRef` (bảo vệ dữ
liệu cá nhân); vì sao khóa sổ theo học kỳ (điểm đã công bố không được đổi âm thầm); bỏ ghi danh thì không thể báo "5 người chưa đủ dữ liệu".

## 11. Hiện thực lớp học phần, sinh viên, ghi danh (2026-11-08)

Phần còn lại của F05a (US-19, US-20) đã có mã trên nhánh `feat/f05a-sections-enrollments`. Các quyết định dưới đây là **lựa chọn của người
hiện thực, chờ hai sinh viên và GVHD xác nhận**; mục nào đổi thì sửa mã kèm test tương ứng.

| # | Quyết định | Lý do / hệ quả |
|---|---|---|
| D1 | Mở/sửa/đổi trạng thái lớp: ADMIN, hoặc PROGRAM_MANAGER mà học phần thuộc một phiên bản CTĐT của chương trình nằm trong `scope.programIds` (`canManageCourseSections`). Học phần chưa thuộc CTĐT nào thì chỉ ADMIN | Plan ghi "ADMIN, PROGRAM_MANAGER" nhưng ADR 0005 §3.2 cấm PM ghi ngoài phạm vi CTĐT; không kiểm scope là lỗ hổng đã gặp ở TD-29 |
| D2 | Chỉ mở lớp trên đề cương `CONFIRMED` (plan §4 quy tắc 3, trước đây là [ĐỀ XUẤT]) và trong học kỳ `OPEN` | Lớp bám theo CLO/bài đánh giá của đề cương; học kỳ đã khóa sổ điểm không nên có lớp mới. Vi phạm → 422 |
| D3 | `courseId`, `syllabusId`, `termId` của lớp **bất biến** sau khi mở; `PUT` chỉ đổi `lecturerIds` | Bài đánh giá, ghi danh và điểm của lớp bám theo ba tham chiếu này |
| D4 | `lecturerIds` phải là người dùng `ACTIVE` có vai trò `LECTURER`; ID trùng được khử trùng lặp | Quyền nhập điểm của giảng viên suy ra từ `lecturerIds` (ADR 0003) |
| D5 | `enrolledCount` là giá trị dẫn xuất: **tính lại** bằng `count(ENROLLED)` sau mỗi lần nhập/rút/ghi danh lại, trong cùng transaction | Chọn một trong ba phương án ở §4 quy tắc 1; tính lại thay vì cộng dồn để không lệch |
| D6 | Ghi danh (mẫu số) **đóng băng** khi lớp không còn `OPEN` hoặc học kỳ không còn `OPEN`: nhập CSV và rút/ghi danh lại trả 422 | Điểm đã công bố không được đổi mẫu số âm thầm (§10). Mở lại lớp `LOCKED` cần lý do giải trình (cùng quy tắc US-18/AC-3) |
| D7 | Nhập CSV là một thao tác thống nhất (`@Transactional`): có dòng lỗi thì **không ghi gì** và trả 422 kèm `fieldErrors[]` theo dòng (`line N`) | US-20/AC-2 |
| D8 | Nhập lại cùng tệp bị từ chối 422 ("đã ghi danh") chứ không bỏ qua êm; sinh viên đã `WITHDRAWN` xuất hiện lại trong tệp cũng bị 422 và được chỉ dẫn dùng `PUT /enrollments/{id}/status` | Không nhân đôi sinh viên/ghi danh; việc ghi danh lại là quyết định có chủ đích, không phải hệ quả của nhập tệp |
| D9 | Tệp CSV UTF-8 (chấp nhận BOM), phân cách `,` hoặc `;`, tối đa 5000 dòng dữ liệu; chỉ đọc cột `studentRef` (bắt buộc), `cohort`, `majorCode`. Cột khác (họ tên, email...) **bị bỏ qua và được báo lại** trong `ignoredColumns` | Không thêm thư viện; không lưu dữ liệu cá nhân khi Khoa chưa cho phép. Sinh viên đã có thì dùng lại, **không ghi đè** `cohort`/`majorCode` |
| D10 | Excel (`.xlsx`) **chưa làm**: cần chọn thư viện bằng một ADR dùng chung với F05b | CLAUDE.md quy tắc 3 (không thêm dependency khi chưa có lý do) |
| D11 | `GET /sections` không phân trang | Số lớp theo học kỳ nhỏ; ghi ở `doc/tech-debt.md` TD-30 |
| D12 | Đổi trạng thái học kỳ/lớp/ghi danh mới ghi log ứng dụng, **chưa ghi `audit_logs`** | `audit_logs.action` là enum đóng trong `database_import.js`; thêm hành động mới là đổi thiết kế CSDL, cần ADR (TD-31) |

**Chưa làm của F05a:** US-18/AC-2 vế "từ chối nhập/sửa điểm" (chờ F05b); nhập Excel (D10); ghi `audit_logs` (D12); `programVersionId`,
`userId`, `fullName`, `email` của `students` (chưa có tính năng dùng đến, NEW-03 và Q09).
