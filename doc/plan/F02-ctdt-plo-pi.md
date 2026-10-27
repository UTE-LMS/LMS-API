# F02 — CTĐT, PLO, PI và ma trận I/R/M

Story: **US-05** chương trình + phiên bản (C), **US-06** 7 PLO / 17 PI theo mã (C), **US-07** ma trận học phần đóng góp PI
(C), **US-08** quy trình xác nhận nội dung (MR). Giai đoạn P2. Chủ trì đề xuất: IBS (IT hỗ trợ). Package: `program`.
Phụ thuộc: `00-nen-tang-chung.md`, `F01`.

## 1. Mục tiêu

Quản lý khung chuẩn đầu ra cấp chương trình: chương trình, phiên bản CTĐT, 7 PLO, 17 PI, và ma trận học phần đóng góp PI
(I/R/M). Đây là dữ liệu tham chiếu của mọi kết quả đo lường sau này, nên **luôn gắn phiên bản** và **không sửa đè** bản đã duyệt.

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` §2 (nguyên tắc 1, 3, 4, 5), §4.2–4.8, §7 (dữ liệu khởi tạo).
- `database_import.js`: khối `programs`, `program_versions`, `plos`, `pis`, `courses`, `program_courses`,
  `course_pi_contributions`, và phần seed (7 PLO, 17 PI, 4 dòng I/R/M).
- `Misa.md` (hướng dẫn đọc CTĐT): chỉ tra cứu; **không chép nguyên văn PLO/PI từ trí nhớ**.

## 3. Collection và trường chính

| Collection | Trường chính (validator thực tế trong script) |
|---|---|
| `programs` | `code` (duy nhất), `name`, `level`, `degree` |
| `program_versions` | `programId`, `version`, `totalCredits`, `durationYears`, `applicableCohorts[]`, `status` (`DRAFT`/`ACTIVE`/`ARCHIVED`), `verification`, `extra`. Duy nhất `(programId, version)` |
| `plos` | `programVersionId`, `code` (`^PLO[1-9][0-9]*$`), `displayOrder`, `category` (`GENERAL`/`SPECIALIZED`/`UNSPECIFIED`), `contentOriginal`, `contentSummary`, `capabilityLevelRaw` (1–6), `verification`, `reviewNote`. Duy nhất `(programVersionId, code)` |
| `pis` | `programVersionId`, `ploId`, `code` (`^PI[1-9][0-9]*\.[1-9][0-9]*$`), `displayOrder`, `nameOriginalTerm`, `contentOriginal`, `contentShort`, `capabilityLevelRaw` (1–6), `targetPassRate` (0–1), `verification`. Duy nhất `(programVersionId, code)` |
| `courses` | `code` (duy nhất), `name`, `credits`, `departmentId` (chưa có collection bộ môn, Q17) — thuộc F03 |
| `program_courses` | `programVersionId`, `courseId`, `semester`, `credits`, `type` (`COMPULSORY`/`ELECTIVE`), `category`, `prerequisiteCourseIds[]`. Duy nhất `(programVersionId, courseId)` — thuộc F03 |
| `course_pi_contributions` | `programVersionId`, `courseId`, `piId`, `contribution` (`I`/`R`/`M`), `verification`. Duy nhất `(programVersionId, courseId, piId)` |

`verification = { status: UNVERIFIED | VERIFIED | NEEDS_CLARIFICATION, verifiedBy, verifiedAt, note }`.

## 4. Quy tắc nghiệp vụ

1. **PLO/PI nhận diện bằng mã**, không bằng thứ tự dòng. `displayOrder` là trường riêng (tài liệu xếp PLO1, 6, 7, 4, 5, 2, 3).
2. `PI.ploId` và `PI.programVersionId` phải khớp: PI của PLO nào thì cùng `programVersionId` với PLO đó. Mã PI nên
   bắt đầu bằng số PLO tương ứng (`PI6.2` thuộc `PLO6`) — **[ĐỀ XUẤT]** kiểm tra mềm, báo cảnh báo thay vì chặn nếu tài liệu có ngoại lệ.
3. **Không sửa đè bản đã duyệt:** sửa PLO/PI của một phiên bản đã `ACTIVE` phải sinh phiên bản mới (`program_versions`)
   hoặc ít nhất ghi `audit`; **[ĐỀ XUẤT]** chặn sửa nội dung khi phiên bản `ACTIVE` và đã có kết quả đo dùng nó.
4. `contentOriginal` để `null` cho tới khi sinh viên đối chiếu bản CTĐT gốc; `verification.status` mặc định
   `NEEDS_CLARIFICATION`/`UNVERIFIED`. **Agent không tự điền nguyên văn.**
5. `capabilityLevelRaw` lưu nguyên giá trị nguồn (1–6), **không diễn giải, không đổi sang Bloom** (Q03).
6. **I/R/M ≠ trọng số.** `course_pi_contributions` chỉ là thiết kế sư phạm; cấm ánh xạ I=1, R=2, M=3. Ô trống của ma trận = **không có bản ghi**.
7. `targetPassRate` là mục tiêu cấp lớp/khóa của PI, để `null` cho tới khi Khoa xác nhận; không đặt mặc định.
8. `program_versions.applicableCohorts` để trống tới khi trả lời Q01.

## 5. Stories và acceptance criteria (nháp)

| ID | AC |
|---|---|
| US-05/AC-1 | Tạo `program_versions` với `(programId, version)` mới → thành công, `status = DRAFT` |
| US-05/AC-2 | Trùng `(programId, version)` → 409 |
| US-05/AC-3 | `programId` không tồn tại → 422 |
| US-05/AC-4 | Chuyển trạng thái chỉ theo chiều `DRAFT → ACTIVE → ARCHIVED`; chuyển ngược → 422 |
| US-06/AC-1 | Tạo PLO với mã đúng mẫu `^PLO[1-9][0-9]*$` → thành công; mã `PLO0`, `plo1`, `PLO01` → 400 |
| US-06/AC-2 | Tạo PI với `ploId` không thuộc cùng `programVersionId` → 422 |
| US-06/AC-3 | `capabilityLevelRaw` ngoài 1–6 → 400 |
| US-06/AC-4 | Danh sách PLO sắp theo `displayOrder`, **không** theo `code` hay thứ tự chèn |
| US-06/AC-5 | Tạo PLO/PI không có `contentOriginal` vẫn hợp lệ; trạng thái xác nhận mặc định không phải `VERIFIED` |
| US-07/AC-1 | Thêm dòng ma trận `(programVersionId, courseId, piId, contribution ∈ {I,R,M})` → thành công |
| US-07/AC-2 | Trùng `(programVersionId, courseId, piId)` → 409; `contribution` khác `I/R/M` → 400 |
| US-07/AC-3 | `courseId`/`piId` không thuộc `programVersionId` đó → 422 |
| US-07/AC-4 | Không API nào trả "trọng số" suy từ I/R/M (kiểm tra bằng test hợp đồng DTO) |
| US-08/AC-1 (MR) | Đổi `verification.status` ghi `verifiedBy`, `verifiedAt`; `VERIFIED` cần có người xác nhận |

## 6. API đề xuất **[ĐỀ XUẤT]**

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| GET/POST/PUT | `/api/programs`, `/api/programs/{id}` | đọc: mọi vai trò; ghi: PROGRAM_MANAGER |
| GET/POST/PUT | `/api/program-versions`, `/api/program-versions/{id}` | như trên |
| GET/POST/PUT | `/api/program-versions/{pvId}/plos`, `.../plos/{id}` | như trên |
| GET/POST/PUT | `/api/program-versions/{pvId}/pis`, `.../pis/{id}` | như trên |
| GET/POST/DELETE | `/api/program-versions/{pvId}/pi-contributions` | như trên |
| PUT | `.../plos/{id}/verification`, `.../pis/{id}/verification` (US-08) | PROGRAM_MANAGER |

## 7. Kiểm tra tham chiếu ở service (không có khóa ngoại)

`programId` tồn tại; `ploId` cùng `programVersionId` với PI; `courseId` và `piId` của ma trận thuộc đúng `programVersionId`;
xóa PLO/PI chỉ khi chưa có `clo_pi_links`, `course_pi_contributions` hoặc `outcomeMappings` tham chiếu (hoặc chuyển trạng thái thay vì xóa).

## 8. Test bắt buộc

Mã sai định dạng (`PLO0`, `PLO01`, `PI1.0`, `PI01.1`); trùng khóa; PI khác phiên bản với PLO; `capabilityLevelRaw` = 0 và 7;
thứ tự hiển thị khác thứ tự mã; ma trận trùng; chuyển trạng thái phiên bản sai chiều; xóa PLO còn PI tham chiếu.

## 9. Không làm / chưa quyết

- Không điền nguyên văn PLO/PI; không suy mức PLO từ PI; không quy đổi I/R/M.
- Chờ: Q01 (khóa áp dụng), Q03 (thang năng lực), Q08 (ai xác nhận nội dung). Việc 11 trong kế hoạch: đối chiếu CTĐT gốc để trích nguyên văn (người làm, không phải LLM).
- Seed mặc định của script (7 PLO, 17 PI, 4 dòng I/R/M) là dữ liệu khởi tạo; API không được phụ thuộc vào việc seed đã có.

## 10. Để vấn đáp

Vì sao nhận diện PLO/PI bằng mã (thứ tự dòng trong tài liệu không ổn định); vì sao có `displayOrder` riêng; vì sao I/R/M không
phải trọng số (là vai trò sư phạm Introduce/Reinforce/Master, không phải mức đóng góp số học); vì sao phiên bản hóa (báo cáo
cũ phải đọc lại được khi CTĐT đổi); bỏ `verification` thì nguyên văn chưa đối chiếu có thể bị coi là chính thức.
