# F03 — Học phần, đề cương, CLO và liên kết CLO–PI

Story: **US-09** học phần + vị trí trong CTĐT (C), **US-10** đề cương theo phiên bản (C), **US-11** CLO thuộc một đề cương (C),
**US-12** liên kết CLO–PI và xác nhận (C). Giai đoạn P2. Chủ trì đề xuất: IBS (IT hỗ trợ). Package: `course`.
Phụ thuộc: `00`, `F01`, `F02` (cần `program_versions`, `pis`).

## 1. Mục tiêu

Khai báo học phần, đề cương theo phiên bản, các CLO của từng đề cương, và **liên kết CLO–PI chính thức** — khung mà mọi câu hỏi
sau này phải bám vào (câu hỏi chỉ được đo cặp (CLO, PI) có trong liên kết này).

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` §2 (nguyên tắc 2, 12), §4.6–4.7, §4.9–4.11, §7.
- `database_import.js`: khối `courses`, `program_courses`, `syllabi`, `clos`, `clo_pi_links`; seed học phần OOP (`OOPR230279`).

## 3. Collection và trường chính

| Collection | Trường chính |
|---|---|
| `courses` | `code` (duy nhất), `name`, `credits` (int), `departmentId` (chỉ là ObjectId chờ, Q17), `extra` |
| `program_courses` | `programVersionId`, `courseId`, `semester`, `credits`, `type` (`COMPULSORY`/`ELECTIVE`), `category` (`FOUNDATION`, `MAJOR_FOUNDATION`, `SPECIALIZED`, `CAPSTONE_PROJECT`, `THESIS`), `prerequisiteCourseIds[]` |
| `syllabi` | `courseId`, `version`, `effectiveFromTermId`, `effectiveToTermId` (null được), `status` (`DRAFT`/`CONFIRMED`/`ARCHIVED`), `verification`. Duy nhất `(courseId, version)` |
| `clos` | `syllabusId`, `courseId`, `code` (`^CLO[0-9]+$`), `contentOriginal`, `bloomLevel` (1–6), `bloomNote`, `measurement` (`{ defaultWeight, defaultPassThreshold }`, null cho tới khi Khoa xác nhận), `verification`. Duy nhất `(syllabusId, code)` |
| `clo_pi_links` | `cloId`, `piId`, `syllabusId`, `programVersionId`, `weight` (0 < x ≤ 1, null cho tới khi quy tắc xác nhận), `verification`. Duy nhất `(cloId, piId)` |

## 4. Quy tắc nghiệp vụ

1. **Mã CLO chỉ có nghĩa trong một đề cương** (`CLO1` của OOP khác `CLO1` của Cơ sở dữ liệu). Nhận diện CLO bằng `_id`; `code` chỉ duy nhất trong một `syllabusId`.
2. `clos.courseId` phải bằng `syllabi.courseId` của `syllabusId` đó.
3. `bloomLevel` ∈ 1–6 và **không phải điểm số** — không có trường quy đổi.
4. `clos.measurement.defaultWeight` và `defaultPassThreshold` **để `null`** tới khi Khoa xác nhận (Q12). Nếu có giá trị, nằm trong [0, 1].
   Không đặt mặc định (không dùng 20/40/40, 60%…).
5. **Liên kết CLO–PI:** `clo_pi_links.syllabusId` và `programVersionId` phải khớp CLO và PI. Chỉ liên kết `verification.status = VERIFIED`
   mới được dùng cho tính chính thức (F06). `weight` dùng cho phương pháp `FROM_CLOS` (NEW-04), được phép `null`.
6. Không xóa/sửa CLO, liên kết đã được `outcomeMappings` hoặc `student_results` tham chiếu; thay vào đó đổi trạng thái hoặc sinh đề cương mới.
7. Đề cương `CONFIRMED` không sửa nội dung CLO; sửa = tạo `syllabi.version` mới (nguyên tắc phiên bản hóa).
8. Học phần dùng thử: **ba học phần thử nghiệm chưa chọn**. Hệ thống phải chạy với bất kỳ học phần nào; seed OOP chỉ là ví dụ.
9. `contentOriginal` của CLO để trống tới khi đối chiếu đề cương gốc (Q02); không điền từ trí nhớ.

## 5. Stories và acceptance criteria (nháp)

| ID | AC |
|---|---|
| US-09/AC-1 | Tạo `courses` với `code` mới → thành công; trùng `code` → 409 |
| US-09/AC-2 | Gán học phần vào một `programVersionId` (`program_courses`); trùng `(programVersionId, courseId)` → 409 |
| US-09/AC-3 | `prerequisiteCourseIds` chứa học phần không tồn tại hoặc chính nó → 422 |
| US-10/AC-1 | Tạo `syllabi` mới với `(courseId, version)` chưa có, `status = DRAFT`; trùng → 409 |
| US-10/AC-2 | `effectiveToTermId` không trước `effectiveFromTermId` (nếu có thông tin học kỳ) → 422 |
| US-10/AC-3 | Đề cương `CONFIRMED` không cho sửa CLO của nó (422); phải tạo phiên bản mới |
| US-11/AC-1 | Tạo CLO với `code` đúng mẫu, `bloomLevel` 1–6 → thành công |
| US-11/AC-2 | Hai CLO cùng `code` trong **cùng** đề cương → 409; cùng `code` ở **hai** đề cương khác nhau → hợp lệ |
| US-11/AC-3 | `bloomLevel` = 0 hoặc 7 → 400 |
| US-11/AC-4 | `measurement` rỗng/`null` vẫn hợp lệ; giá trị ngoài [0,1] → 400 |
| US-12/AC-1 | Tạo liên kết `(cloId, piId)` khi `piId` thuộc `programVersionId` của liên kết → thành công |
| US-12/AC-2 | Trùng `(cloId, piId)` → 409; PI khác `programVersionId` → 422 |
| US-12/AC-3 | Đổi `verification.status` sang `VERIFIED` ghi người xác nhận và thời điểm |
| US-12/AC-4 | API ma trận trả CLO × PI kèm trạng thái xác nhận từng ô |
| US-12/AC-5 | `weight` ngoài (0, 1] → 400; `null` hợp lệ |

## 6. API đề xuất **[ĐỀ XUẤT]**

| Phương thức | Đường dẫn |
|---|---|
| GET/POST/PUT | `/api/courses`, `/api/courses/{id}` |
| GET/POST/PUT/DELETE | `/api/program-versions/{pvId}/courses` (`program_courses`) |
| GET/POST/PUT | `/api/courses/{courseId}/syllabi`, `/api/syllabi/{id}` |
| GET/POST/PUT | `/api/syllabi/{syllabusId}/clos`, `/api/clos/{id}` |
| GET/POST/PUT/DELETE | `/api/syllabi/{syllabusId}/clo-pi-links`; `GET .../clo-pi-matrix` |
| PUT | `/api/clo-pi-links/{id}/verification` |

Quyền ghi: PROGRAM_MANAGER; đọc: mọi vai trò theo phạm vi (xem `F01`).

## 7. Kiểm tra tham chiếu ở service

`courseId`, `syllabusId`, `programVersionId`, `piId` tồn tại; `clos.courseId` = `syllabi.courseId`; liên kết khớp
`syllabusId`/`programVersionId` của CLO và PI; `effective*TermId` trỏ `terms` (F05a) khi có giá trị.

## 8. Test bắt buộc

Mã CLO trùng trong/ngoài đề cương; `bloomLevel` biên (0,1,6,7); liên kết trùng; PI sai phiên bản; sửa CLO của đề cương `CONFIRMED`;
`weight` biên (0, 1, 1,01, null); xóa CLO đang được dùng; ma trận CLO × PI rỗng.

## 9. Không làm / chưa quyết

- Không điền nguyên văn CLO, không tự suy ánh xạ CLO–PI từ nội dung (Q14: Khoa cung cấp ánh xạ đã duyệt).
- Chờ: Q02 (khi nào có đề cương chính thức), Q12 (tỷ trọng/ngưỡng từng CLO), Q14, Q17 (`departmentId`).
- Nếu có phương án nhập hàng loạt đề cương từ tệp: ngoài phạm vi, cần ADR.

## 10. Để vấn đáp

Vì sao CLO nhận diện bằng `_id` chứ không bằng `code`; vì sao có `clo_pi_links` riêng (khung đã duyệt để chặn gán PI tùy ý cho
câu hỏi); vì sao đề cương phiên bản hóa (đổi CLO không làm sai báo cáo cũ); bỏ kiểm tra "cặp CLO–PI nằm trong liên kết" thì
một câu hỏi có thể đo PI không liên quan CLO của nó.
