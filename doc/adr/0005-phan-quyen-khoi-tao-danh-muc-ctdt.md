# ADR 0005 — ADMIN khởi tạo danh mục CTĐT; PROGRAM_MANAGER quản lý phiên bản theo scope

- **Trạng thái:** ĐỀ XUẤT, **chờ Khoa/GVHD xác nhận**. Chưa phải quyết định chính thức.
- **Ngày:** 2026-11-03
- **Người soạn:** Antigravity theo yêu cầu của sinh viên TinMH (IT); sinh viên rà soát và ghi tên người chốt: [ĐIỀN]

---

## 1. Bối cảnh

Trong đặc tả ban đầu của phân hệ F02 (`plan/F02-ctdt-plo-pi.md` §6), quyền ghi (`POST`, `PUT`) trên `/api/v1/programs` được gán cho vai trò `PROGRAM_MANAGER`. Tuy nhiên, khi triển khai và rà soát cơ chế kiểm soát truy cập theo phạm vi (**Scope Authorization** — F01 / ADR 0003), nhóm phát triển nhận diện được 3 mâu thuẫn kiến trúc và nghiệp vụ nghiêm trọng:

1. **Nghịch lý "Con gà và quả trứng" với `ScopeValidationService` (F01):**  
   Tại [`ScopeValidationServiceImpl.java`](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/service/impl/ScopeValidationServiceImpl.java#L31-L43), khi `ADMIN` tạo tài khoản và gán vai trò `PROGRAM_MANAGER` kèm `scope.programIds`, hệ thống bắt buộc các `programId` đó **phải tồn tại sẵn** trong collection `programs` (nếu không sẽ báo lỗi HTTP 422 `SCOPE_NOT_FOUND`). Nếu `PROGRAM_MANAGER` lại là người tạo ra `Program`, hệ thống không thể thiết lập tài khoản ban đầu nếu chưa có dữ liệu mồi.
2. **Quy chế Quản trị Dữ liệu Danh mục Cấp Trường (Master Data Governance):**  
   Ngành đào tạo (`Program`) là danh mục pháp lý dài hạn do Bộ GD&ĐT cấp phép (ví dụ: Mã ngành 7480201) và Hội đồng trường ban hành quyết định mở ngành. Trưởng một chương trình cụ thể (`PROGRAM_MANAGER`) không có thẩm quyền tự ý tạo thêm các ngành đào tạo mới trong hệ thống của trường đại học.
3. **Nguy cơ Tự leo thang đặc quyền (Self-Privilege Escalation):**  
   Nếu cho phép `PROGRAM_MANAGER` tạo `Program` rồi hệ thống tự động gán ID vào `scope.programIds` của họ, người dùng có thể tự do mở rộng phạm vi dữ liệu của mình mà không qua phê duyệt quản trị định danh, vi phạm nguyên tắc phân định trách nhiệm (**Separation of Duties**) và chuẩn an toàn thông tin OWASP.

---

## 2. Các phương án xem xét

| # | Phương án | Ưu điểm | Nhược điểm |
|---|---|---|---|
| **A** | Giữ nguyên hiện trạng: `PROGRAM_MANAGER` tạo `Program` không kiểm tra scope | Không sửa mã hiện tại | Nghịch lý khi thiết lập tài khoản; rủi ro rác danh mục ngành; không giải thích được trước Hội đồng. |
| **B** | `PROGRAM_MANAGER` tạo `Program` và tự động gán vào `scope.programIds` của mình (Creator-as-Owner) | Tự phục vụ (Self-service), không phụ thuộc Admin | Vi phạm quy chế quản trị đại học (Master Data); vi phạm chuẩn an toàn chống tự leo thang đặc quyền; bế tắc khi có nhiều đồng chủ nhiệm cùng quản lý một ngành. |
| **C** | **Chuyển quyền tạo/sửa `Program` cho `ADMIN`; `PROGRAM_MANAGER` quản lý `ProgramVersion`, `PLO`, `PI` theo đúng `scope.programIds`** | **Chuẩn tắc mô hình quản trị ERP đại học; giải quyết dứt điểm nghịch lý F01; bảo vệ Master Data; kích hoạt kiểm tra Scope toàn diện cho mọi thao tác của PM.** | Cần cập nhật `@PreAuthorize` của `ProgramController` sang `ADMIN` và bổ sung kiểm tra scope cho `PROGRAM_MANAGER` ở các tài nguyên con. |

---

## 3. Quyết định (Đề xuất)

Chọn phương án **C**.

1. **Khởi tạo danh mục Ngành (`programs`):**
   - Chỉ có tài khoản mang vai trò `ADMIN` (đại diện Phòng Đào tạo / Quản trị hệ thống) mới có quyền gọi `POST /api/v1/programs` và `PUT /api/v1/programs/{id}`.
   - `PROGRAM_MANAGER` và các vai trò khác khi gọi tạo/sửa `Program` sẽ bị từ chối bằng **HTTP 403 Forbidden**.
   - Hành vi xem (`GET /api/v1/programs`, `GET /api/v1/programs/{id}`) vẫn mở cho mọi vai trò đã đăng nhập.

2. **Quản trị chuyên môn học thuật (`program_versions`, `plos`, `pis`, `course_pi_contributions`):**
   - Vai trò `PROGRAM_MANAGER` toàn quyền soạn thảo, chỉnh sửa, chuyển trạng thái và thiết lập ma trận I/R/M trong phạm vi các ngành được phân công.
   - **Bắt buộc kiểm tra Scope:** Mọi thao tác ghi của `PROGRAM_MANAGER` đều phải thỏa mãn `scope.programIds` chứa đúng `programId` của chương trình tương ứng (thông qua `ScopeAuthorizationService.canAccessProgram` và `canAccessProgramVersion`, cơ chế fail-closed). Nếu thao tác ngoài scope $\to$ bị từ chối bằng **HTTP 403 Forbidden**.

3. **Thẩm định độc lập (`verification`):**
   - Giữ nguyên quyết định theo **ADR 0004 & ADR 0014**: Chỉ cán bộ `QA` đúng phạm vi `scope.programIds` mới được phép đổi trạng thái thẩm định; `PROGRAM_MANAGER` và `ADMIN` đều bị **HTTP 403 Forbidden**.

---

## 4. Hệ quả

1. **Về Mã Nguồn & Kiểm Thử:**
   - Cập nhật [ProgramController.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/controller/ProgramController.java): `@PreAuthorize("hasRole('ADMIN')")` cho `createProgram` và `updateProgram`.
   - Bổ sung phương thức `canAccessProgramVersion` vào [ScopeAuthorizationService.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/service/ScopeAuthorizationService.java) và [ScopeAuthorizationServiceImpl.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/service/impl/ScopeAuthorizationServiceImpl.java).
   - Tích hợp SpEL kiểm tra `@scopeAuthorizationService.canAccessProgram(...)` và `canAccessProgramVersion(...)` trên [ProgramVersionController.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/controller/ProgramVersionController.java), [PloController.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/controller/PloController.java), [PiController.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/controller/PiController.java), [CoursePiContributionController.java](file:///D:/Code/Project/LMS-API/src/main/java/com/hcmute/lms/lmsapi/controller/CoursePiContributionController.java).
   - Cập nhật bộ test tích hợp [ProgramIntegrationTest.java](file:///D:/Code/Project/LMS-API/src/test/java/com/hcmute/lms/lmsapi/controller/ProgramIntegrationTest.java): Kiểm tra `ADMIN` tạo `Program` thành công (201), `PROGRAM_MANAGER` gọi bị 403; `PROGRAM_MANAGER` tạo version đúng scope thành công, ngoài scope bị 403.
2. **Về Tài Liệu:**
   - Đồng bộ hóa các tài liệu: `doc/plan/F02-ctdt-plo-pi.md`, `doc/sdd/02-component.md`, `doc/sdd/04-sequence.md`, `doc/srs/README.md`, `doc/test-plan.md`, `doc/de-van-dap/f02-ctdt-plo-pi.md`.
