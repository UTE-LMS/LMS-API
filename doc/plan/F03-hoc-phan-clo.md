# F03 — Học phần, đề cương, CLO và liên kết CLO–PI

Story: **US-09** học phần + vị trí trong CTĐT (C), **US-10** đề cương theo phiên bản (C), **US-11** CLO thuộc một đề cương (C),
**US-12** liên kết CLO–PI và xác nhận (C). Giai đoạn P2. Chủ trì đề xuất: IBS (IT hỗ trợ). Package: `course`.
Phụ thuộc: `00`, `F01`, `F02` (cần `program_versions`, `pis`), `F05a` (`terms`).

## 1. Mục tiêu

Khai báo học phần, đề cương theo phiên bản, các CLO của từng đề cương, và **liên kết CLO–PI chính thức** — khung mà mọi câu hỏi
sau này phải bám vào (câu hỏi chỉ được đo cặp (CLO, PI) có trong liên kết này).

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` §2 (nguyên tắc 2, 12), §4.6–4.7, §4.9–4.11, §7.
- `database_import.js`: khối `courses`, `program_courses`, `syllabi`, `clos`, `clo_pi_links`; seed học phần OOP (`OOPR230279`).

## 3. Collection và trường chính

| Collection | Trường chính |
|---|---|
| `courses` | `code` (duy nhất), `name`, `credits` (int), `extra`. *(Q17 đã chốt: không tạo collection `departments`, bỏ `departmentId`)* |
| `program_courses` | `programVersionId`, `courseId`, `semester`, `credits`, `type` (`COMPULSORY`/`ELECTIVE`), `category` (`FOUNDATION`, `MAJOR_FOUNDATION`, `SPECIALIZED`, `CAPSTONE_PROJECT`, `THESIS`), `prerequisiteCourseIds[]`, `previousCourseIds[]` |
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

| ID | AC | Trạng thái |
|---|---|---|
| US-09/AC-1 | Tạo `courses` với `code` mới → thành công; trùng `code` → 409 | **ĐÃ HOÀN THÀNH (PR #67)** |
| US-09/AC-2 | Gán học phần vào một `programVersionId` (`program_courses`); trùng `(programVersionId, courseId)` → 409 | **ĐÃ HOÀN THÀNH (PR #67)** |
| US-09/AC-3 | `prerequisiteCourseIds` / `previousCourseIds` chứa học phần không tồn tại hoặc chính nó → 422 | **ĐÃ HOÀN THÀNH (PR #67, #68)** |
| US-10/AC-1 | Tạo `syllabi` mới với `(courseId, version)` chưa có, `status = DRAFT`; trùng → 409 | **ĐÃ HOÀN THÀNH (Issue #21)** |
| US-10/AC-2 | `effectiveToTermId` không trước `effectiveFromTermId` (nếu có thông tin học kỳ) → 422 | **ĐÃ HOÀN THÀNH (Issue #21)** *(Đã làm trước module `terms` của F05a)* |
| US-10/AC-3 | Đề cương `CONFIRMED` không cho sửa CLO của nó (422); phải tạo phiên bản mới | **ĐÃ HOÀN THÀNH (Issue #21)** |
| US-11/AC-1 | Tạo CLO với `code` đúng mẫu, `bloomLevel` 1–6 → thành công | **ĐÃ HOÀN THÀNH (Issue #22)** |
| US-11/AC-2 | Hai CLO cùng `code` trong **cùng** đề cương → 409; cùng `code` ở **hai** đề cương khác nhau → hợp lệ | **ĐÃ HOÀN THÀNH (Issue #22)** |
| US-11/AC-3 | `bloomLevel` = 0 hoặc 7 → 400 | **ĐÃ HOÀN THÀNH (Issue #22)** |
| US-11/AC-4 | `measurement` rỗng/`null` vẫn hợp lệ; giá trị ngoài [0,1] → 400 | **ĐÃ HOÀN THÀNH (Issue #22)** |
| US-12/AC-1 | Tạo liên kết `(cloId, piId)` khi `piId` thuộc `programVersionId` của liên kết → thành công | **ĐÃ HOÀN THÀNH (Issue #23)** |
| US-12/AC-2 | Trùng `(cloId, piId)` → 409; PI khác `programVersionId` → 422 | **ĐÃ HOÀN THÀNH (Issue #23)** |
| US-12/AC-3 | Đổi `verification.status` sang `VERIFIED` ghi người xác nhận và thời điểm | **ĐÃ HOÀN THÀNH (Issue #23)** |
| US-12/AC-4 | API ma trận trả CLO × PI kèm trạng thái xác nhận từng ô | **ĐÃ HOÀN THÀNH (Issue #23)** |
| US-12/AC-5 | `weight` ngoài (0, 1] → 400; `null` hợp lệ | **ĐÃ HOÀN THÀNH (Issue #23)** |
| US-10/AC-4 *(bổ sung theo ADR 0009)* | Ghi đề cương (`POST /courses/{courseId}/syllabi`, `PUT /syllabi/{id}/status`): `PROGRAM_MANAGER` ngoài scope → 403; trong scope → 2xx; học phần chưa thuộc `program_courses` nào → PM 403, `ADMIN` 2xx | **ĐÃ HIỆN THỰC trên nhánh F03 (chờ review/merge)** |
| US-11/AC-5 *(bổ sung theo ADR 0009)* | Ghi CLO (`POST /syllabi/{syllabusId}/clos`, `PUT`/`DELETE /clos/{id}`): PM ngoài scope → 403; trong scope → 2xx; học phần chưa thuộc `program_courses` nào → PM 403, `ADMIN` 2xx | **ĐÃ HIỆN THỰC trên nhánh F03 (chờ review/merge)** |
| US-12/AC-6 *(bổ sung theo ADR 0009)* | Ghi liên kết (`POST /syllabi/{syllabusId}/clo-pi-links`, `DELETE /clo-pi-links/{id}`): PM ngoài scope → 403; trong scope → 2xx; học phần chưa thuộc `program_courses` nào → PM 403, `ADMIN` 2xx | **ĐÃ HIỆN THỰC trên nhánh F03 (chờ review/merge)** |

Ghi chú: AC-4/AC-5/AC-6 ở trên chưa có mã và chưa có test; đang chờ ADR 0009 được xác nhận (xem mục 6). Quy tắc nghiệp vụ **6** (không
xóa/sửa CLO, liên kết đã được `outcomeMappings` hoặc `student_results` tham chiếu) và ca test "xóa CLO đang được dùng" (mục 8) **CHỜ F06**:
các collection `outcomeMappings` (trong `questions`/`assessments`) và `student_results` chưa có nghiệp vụ, nên chưa thể kiểm tham chiếu.
Quy tắc **7** (đề cương `CONFIRMED` không sửa CLO) đã có qua US-10/AC-3.

## 6. API đề xuất **[ĐÃ HIỆN THỰC]**

| Phương thức | Đường dẫn |
|---|---|
| GET/POST/PUT | `/api/v1/courses`, `/api/v1/courses/{id}` |
| GET/POST/PUT/DELETE | `/api/v1/program-versions/{pvId}/courses` (`program_courses`) |
| GET/POST/PUT | `/api/v1/courses/{courseId}/syllabi`, `/api/v1/syllabi/{id}`, `/api/v1/syllabi/{id}/status` |
| GET/POST/PUT/DELETE | `/api/v1/syllabi/{syllabusId}/clos`, `/api/v1/clos/{id}` |
| GET/POST/DELETE | `/api/v1/syllabi/{syllabusId}/clo-pi-links`, `/api/v1/clo-pi-links/{id}`; `GET .../clo-pi-matrix` |
| PUT | `/api/v1/clo-pi-links/{id}/verification` |

Quyền (hiện trạng mã): ghi `ADMIN`, `PROGRAM_MANAGER` (mới kiểm vai trò); thẩm định: `QA_OFFICER` (theo phạm vi scope); đọc: mọi vai trò đã xác thực.

**Đề xuất theo ADR 0009 (chờ GVHD/Khoa xác nhận; đã hiện thực trên nhánh F03, chờ review/merge):** các endpoint ghi của `syllabi`, `clos`, `clo_pi_links` thêm kiểm `scope.programIds`
của `PROGRAM_MANAGER` (fail-closed) bằng cách suy phạm vi qua `program_courses`:

| Endpoint ghi | Vai trò | Kiểm scope (PM) |
|---|---|---|
| `POST /courses/{courseId}/syllabi` | ADMIN, PM | `canAccessCourse` |
| `PUT /syllabi/{id}/status` | ADMIN, PM | `canAccessSyllabus` |
| `POST /syllabi/{syllabusId}/clos` | ADMIN, PM | `canAccessSyllabus` |
| `PUT`, `DELETE /clos/{id}` | ADMIN, PM | `canAccessClo` |
| `POST /syllabi/{syllabusId}/clo-pi-links` | ADMIN, PM | `canAccessProgramVersion` và `canAccessSyllabus` |
| `DELETE /clo-pi-links/{id}` | ADMIN, PM | `canAccessCloPiLink` (qua `programVersionId`) |
| `PUT /clo-pi-links/{id}/verification` | QA_OFFICER đúng scope (ADR 0004), không đổi | — |

`ADMIN` luôn qua. PM qua khi học phần có trong `program_courses` của ít nhất một phiên bản thuộc ngành trong scope của PM; học phần
chưa thuộc CTĐT nào thì chỉ `ADMIN` ghi được (nên `ADMIN` gán học phần vào CTĐT trước khi PM soạn đề cương). Các `GET` giữ `isAuthenticated()`.

## 7. Kiểm tra tham chiếu ở service

`courseId`, `syllabusId`, `programVersionId`, `piId` tồn tại; `clos.courseId` = `syllabi.courseId`; liên kết khớp
`syllabusId`/`programVersionId` của CLO và PI; `effective*TermId` trỏ `terms` (F05a) khi có giá trị; kiểm tra `effectiveToTermId` không được trước `effectiveFromTermId`.

## 8. Test bắt buộc

Mã CLO trùng trong/ngoài đề cương; `bloomLevel` biên (0,1,6,7); liên kết trùng; PI sai phiên bản; sửa CLO của đề cương `CONFIRMED`;
`weight` biên (0, 1, 1,01, null); xóa CLO đang được dùng *(chờ F06)*; ma trận CLO × PI rỗng; phân quyền scope theo ADR 0009 (PM ngoài scope → 403,
trong scope → 2xx, học phần chưa thuộc CTĐT nào → PM 403 và `ADMIN` 2xx, học phần thuộc hai CTĐT chỉ một trong scope → 2xx, tài nguyên không tồn tại → 403).

## 9. Không làm / chưa quyết (Các nội dung CHƯA ĐƯỢC XÁC NHẬN)

1. **Q02 (Đề cương chính thức):** Chưa có tệp đề cương gốc được duyệt từ Khoa $\to$ Không seed nội dung câu chữ CLO từ trí nhớ, `contentOriginal` để trống hoặc nhập trực tiếp từ người dùng.
2. **Q12 (Tỷ trọng và ngưỡng đạt CLO):** Chưa có tỷ trọng (`defaultWeight`), ngưỡng đạt (`defaultPassThreshold`) và quy tắc `requireAllCloPass` $\to$ `measurement` mặc định để `null`, không tự đặt số liệu giả (như 20/40/40 hay 60%).
3. **Q14 (Ánh xạ CLO-PI từ Khoa):** Chưa có bảng ma trận CLO–PI được Khoa ký duyệt $\to$ Không tự suy diễn hay tự seed dữ liệu ánh xạ CLO–PI vào hệ thống.
4. **Q22 (Công thức trọng số weight CLO-PI):** Chưa có công thức tính trọng số $\to$ Cho phép `weight` để `null`, không bắt buộc tính toán số học.
5. **F05a - US-19 & US-20:** Lớp học phần (`course_sections`) và nhập danh sách sinh viên/ghi danh (`students`, `enrollments`) chưa làm trong phạm vi này; US-20 còn chờ Q09 (quy ước mã sinh viên).

## 10. Để vấn đáp

Vì sao CLO nhận diện bằng `_id` chứ không bằng `code`; vì sao có `clo_pi_links` riêng (khung đã duyệt để chặn gán PI tùy ý cho
câu hỏi); vì sao đề cương phiên bản hóa (đổi CLO không làm sai báo cáo cũ); bỏ kiểm tra "cặp CLO–PI nằm trong liên kết" thì
một câu hỏi có thể đo PI không liên quan CLO của nó; vì sao phải triển khai trước quản lý `terms` của F05a để kiểm tra tính hợp lệ về thời gian của đề cương. Vì sao PM ghi đề cương/CLO phải bị giới hạn theo scope và phạm vi được suy qua `program_courses` (ADR 0009). Xem chi tiết tại `doc/de-van-dap/f03-hoc-phan-clo.md`.
