# ADR 0009 — Phân quyền theo phạm vi cho ghi đề cương, CLO và liên kết CLO–PI

- **Trạng thái:** ĐỀ XUẤT, **chờ GVHD/Khoa xác nhận**. Chưa phải quyết định chính thức.
- **Ngày:** 2026-11-04
- **Người soạn:** Claude Code theo yêu cầu của sinh viên TinMH (IT); sinh viên rà soát và ghi tên người chốt: [ĐIỀN]
- **Liên quan:** ADR 0003 (xác thực, phân quyền F01), ADR 0004 (QA xác nhận), ADR 0005 (`0005-phan-quyen-khoi-tao-danh-muc-ctdt.md`, §3.2), `doc/plan/F03-hoc-phan-clo.md`

---

## 1. Bối cảnh

Nhánh F03 đã hiện thực US-10/11/12 (`syllabi`, `clos`, `clo_pi_links`). Khi rà soát, các endpoint **ghi** của đề cương, CLO và
liên kết CLO–PI chỉ kiểm vai trò `hasAnyRole('ADMIN','PROGRAM_MANAGER')`, **không** kiểm `scope.programIds` của
`PROGRAM_MANAGER` (PM). Điều này trái tinh thần ADR 0005 §3.2: PM chỉ ghi trong phạm vi `scope.programIds`, cơ chế fail-closed.
Hệ quả hiện tại: mọi PM sửa được đề cương/CLO của học phần thuộc ngành khác.

Khó khăn: `syllabi` và `clos` chỉ có `courseId`, **không có** `programVersionId` (CSDL v0.5), nên không thể gọi thẳng
`canAccessProgramVersion`. Riêng `clo_pi_links` có `programVersionId`, nên suy phạm vi trực tiếp được.

## 2. Các phương án xem xét

| # | Phương án | Ưu điểm | Nhược điểm |
|---|---|---|---|
| **A** | Suy phạm vi qua `program_courses`: PM ghi được khi học phần thuộc ít nhất một phiên bản CTĐT của ngành trong `scope.programIds` | Không đổi schema; bám đúng ADR 0005 §3.2; dùng lại dữ liệu đã có | Thêm truy vấn mỗi lần kiểm tra; học phần chưa vào CTĐT nào thì PM không ghi được |
| **B** | Giữ nguyên: coi đề cương là danh mục cấp học phần, mọi PM sửa mọi học phần | Không sửa mã | PM bất kỳ sửa đề cương/CLO của học phần ngoài ngành; vi phạm ADR 0005 §3.2; không giải thích được trước hội đồng |
| **C** | Thêm `programVersionId` vào `syllabi` rồi kiểm bằng `canAccessProgramVersion` | Kiểm tra trực tiếp, rẻ | Đổi schema CSDL v0.5; một đề cương gắn một phiên bản CTĐT không khớp thực tế học phần dùng chung nhiều CTĐT; đổi thiết kế phải xin phép (`CLAUDE.md` quy tắc 9) |

## 3. Quyết định (Đề xuất)

Chọn phương án **A** (người dùng đã chốt hướng này).

1. **Thêm vào `ScopeAuthorizationService` 4 phương thức**, đều fail-closed, nhận `UserPrincipal` và `AccessType`:
   - `canAccessCourse(principal, courseId, accessType)`
   - `canAccessSyllabus(principal, syllabusId, accessType)` — tra `syllabi.courseId` rồi gọi `canAccessCourse`
   - `canAccessClo(principal, cloId, accessType)` — tra `clos.courseId` rồi gọi `canAccessCourse`
   - `canAccessCloPiLink(principal, linkId, accessType)` — tra `clo_pi_links.programVersionId` rồi gọi `canAccessProgramVersion`
2. **Quy tắc `canAccessCourse`:**
   - `ADMIN` → luôn `true`.
   - `PROGRAM_MANAGER` → `true` khi tồn tại **ít nhất một** bản ghi `program_courses` có `courseId` đó mà
     `program_versions.programId` ∈ `scope.programIds` của PM (phép **hợp**: học phần thuộc nhiều CTĐT thì một CTĐT trong scope là đủ).
   - Học phần **chưa thuộc `program_courses` nào** → PM bị từ chối; chỉ `ADMIN` ghi được. Quy trình kèm theo: `ADMIN` tạo học phần
     và gán vào CTĐT trước khi PM soạn đề cương.
   - Tài nguyên không tồn tại → `false` (→ HTTP 403, nhất quán với `canAccessProgramVersion`).
   - Vai trò khác không có quyền ghi.
3. **Áp `@PreAuthorize`:**

   | Endpoint ghi | Điều kiện scope (ngoài vai trò `ADMIN`/`PROGRAM_MANAGER`) |
   |---|---|
   | `POST /courses/{courseId}/syllabi` | `canAccessCourse` |
   | `PUT /syllabi/{id}/status` | `canAccessSyllabus` |
   | `POST /syllabi/{syllabusId}/clos` | `canAccessSyllabus` |
   | `PUT`, `DELETE /clos/{id}` | `canAccessClo` |
   | `POST /syllabi/{syllabusId}/clo-pi-links` | `canAccessProgramVersion` (đã có) **thêm** `canAccessSyllabus` |
   | `DELETE /clo-pi-links/{id}` | `canAccessCloPiLink` |

   Các `GET` giữ `isAuthenticated()`. `PUT /clo-pi-links/{id}/verification` giữ nguyên: chỉ `QA_OFFICER` đúng scope (ADR 0004).
4. Ngoài phạm vi ADR này (không đổi): quyền ghi `courses` và `terms`.

## 4. Hệ quả

1. **Mã nguồn và kiểm thử:** thêm 4 phương thức vào `ScopeAuthorizationService` và `…Impl`; sửa `@PreAuthorize` ở `SyllabusController`,
   `CloController`, `CloPiLinkController`; thêm test cho từng phương thức và ca HTTP (PM ngoài scope → 403, trong scope → 2xx,
   học phần chưa thuộc CTĐT nào → PM 403, `ADMIN` 2xx). Test: `CourseScopeAuthorizationTest` (unit) và `CloIntegrationTest` (US-10/AC-4, US-11/AC-5, US-12/AC-6); xem `doc/test-plan.md` mục 8.5. Quyền GHI chỉ tính phân công `PROGRAM_MANAGER` (và `ADMIN`): vai trò khác của cùng người dùng, ví dụ `QA_OFFICER` ở ngành khác, không cộng dồn thành quyền ghi.
2. **Quy trình nghiệp vụ:** `ADMIN` phải gán học phần vào CTĐT (`program_courses`) trước khi PM soạn đề cương. Nếu gỡ học phần khỏi
   `program_courses` cuối cùng trong scope của PM, PM mất quyền ghi đề cương học phần đó (dữ liệu đề cương vẫn giữ nguyên).
3. **Chi phí:** mỗi lần kiểm tra của PM thêm truy vấn `program_courses` (và `program_versions`). Chưa đo. `program_courses` đã có chỉ mục `course_idx` trên `courseId` (`ProgramCourse`), nên truy vấn `findByCourseId` không quét toàn bộ.
4. **Rủi ro đã biết (chưa xử lý, ghi ở `doc/tech-debt.md` TD-27…TD-29):** quyền suy từ `program_courses` nên PM tự gán học phần của ngành khác vào CTĐT của mình (`POST /program-versions/{id}/courses` chỉ kiểm CTĐT đích) sẽ có quyền ghi đề cương/CLO học phần đó; cần GVHD/Khoa quyết định chặn hay chấp nhận. Ngoài ra đề cương `ARCHIVED` vẫn cho sửa CLO và `createLink` chưa kiểm học phần thuộc `programVersionId`.
5. **Tài liệu:** đồng bộ `doc/plan/F03-hoc-phan-clo.md`, `doc/de-van-dap/f03-hoc-phan-clo.md`, `doc/sdd/02-component.md`,
   `doc/sdd/04-sequence.md`, `doc/test-plan.md`, `doc/srs/README.md`.

## 5. Để vấn đáp

**Hỏi 1. Vì sao PM không được sửa đề cương của học phần thuộc ngành khác, trong khi `courses` là danh mục dùng chung?**
Đề cương và CLO quyết định chuẩn đầu ra mà ngành cam kết (OBE). ADR 0005 §3.2 giao PM quyền ghi trong ngành mình. Danh mục `courses`
là dùng chung, nhưng nội dung đề cương gắn với CTĐT sử dụng học phần đó; cho PM sửa ngoài ngành là phương án B, đã loại.

**Hỏi 2. Đề cương không có `programVersionId`, vậy "đề cương thuộc ngành nào" được suy ra thế nào?**
Qua `program_courses`: học phần có mặt trong phiên bản CTĐT nào, phiên bản đó thuộc `programId` nào. Nếu một trong các `programId`
đó nằm trong `scope.programIds` của PM thì PM được ghi. Làm vậy không đổi schema (phương án C bị loại vì phải xin phép đổi thiết kế CSDL v0.5).

**Hỏi 3. Học phần dùng chung cho hai ngành thì sao? PM ngành này có sửa được đề cương làm ảnh hưởng ngành kia không?**
Có. Quy tắc là phép hợp: một CTĐT trong scope là đủ. Đây là đánh đổi có chủ đích; rủi ro được giảm nhờ đề cương `CONFIRMED` bị
khóa (sửa = tạo phiên bản mới) và liên kết CLO–PI phải được `QA_OFFICER` đúng scope xác nhận. Nếu hội đồng muốn chặt hơn (cần đồng
thuận mọi ngành), phải xin thay quy tắc; hiện chưa làm.

**Hỏi 4. Học phần mới tạo, chưa gán vào CTĐT, PM có soạn đề cương được không?**
Không (fail-closed): chỉ `ADMIN`. Vì không có căn cứ để biết học phần thuộc ngành nào. Bỏ quy tắc này thì PM nào cũng ghi được vào học
phần "mồ côi", quay lại lỗ hổng của phương án B.

**Hỏi 5. Vì sao tài nguyên không tồn tại lại trả 403 mà không phải 404?**
Nhất quán với `canAccessProgramVersion` (ADR 0005): kiểm tra quyền chạy trước, không tiết lộ tài nguyên có tồn tại hay không.
