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

| ID | AC |
|---|---|
| US-18/AC-1 | Tạo học kỳ với `code` mới, `startDate ≤ endDate` → thành công; trùng `code` → 409; `startDate > endDate` → 400/422 |
| US-18/AC-2 | Đổi `status` sang `SCORE_LOCKED` → mọi API nhập/sửa điểm của lớp trong học kỳ trả lỗi (test cùng F05b); ghi `audit_logs` **[ĐỀ XUẤT]** |
| US-18/AC-3 | Mở lại học kỳ `SCORE_LOCKED → OPEN` chỉ ADMIN/PROGRAM_MANAGER và có lý do **[ĐỀ XUẤT]** |
| US-19/AC-1 | Tạo lớp với `courseId`, `syllabusId` (cùng học phần), `termId`, ≥ 1 giảng viên → thành công |
| US-19/AC-2 | `syllabusId` không thuộc `courseId`, hoặc `termId`/`lecturerIds` không tồn tại → 422 |
| US-19/AC-3 | Trùng `code` lớp → 409 |
| US-19/AC-4 | Giảng viên chỉ thấy lớp mình phụ trách (xem F01) |
| US-20/AC-1 | Tải tệp CSV/Excel danh sách lớp: mỗi dòng có `studentRef`; sinh viên mới được tạo, sinh viên cũ được dùng lại; tất cả được ghi danh `ENROLLED` |
| US-20/AC-2 | Dòng thiếu `studentRef`, trùng trong tệp, hoặc đã ghi danh lớp đó → báo lỗi theo dòng, không ghi nửa chừng (một thao tác thống nhất) |
| US-20/AC-3 | Tệp rỗng, sai cột, sai định dạng → 400 với thông báo rõ |
| US-20/AC-4 | Sau khi nhập, số `ENROLLED` của lớp đúng bằng số dòng hợp lệ; rút khỏi lớp chuyển `WITHDRAWN`, mẫu số giảm tương ứng |

## 6. API đề xuất **[ĐỀ XUẤT]**

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| GET/POST/PUT | `/api/terms`, `/api/terms/{id}`; `PUT /api/terms/{id}/status` | ADMIN, PROGRAM_MANAGER |
| GET/POST/PUT | `/api/sections`, `/api/sections/{id}` | ADMIN, PROGRAM_MANAGER; đọc: giảng viên lớp mình |
| POST | `/api/sections/{id}/enrollments/import` (tệp) | LECTURER (lớp mình), ADMIN |
| GET | `/api/sections/{id}/enrollments` | theo phạm vi |
| PUT | `/api/enrollments/{id}/status` (rút/ghi danh lại) | LECTURER (lớp mình), ADMIN |

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
