# ADR 0010 — Phân quyền quản lý danh mục học phần, biên soạn và phê duyệt đề cương theo Bộ môn và Giảng viên

- **Trạng thái:** ĐỀ XUẤT (PROPOSED), **thay thế ADR 0009**.
- **Ngày:** 2026-11-05 (cập nhật 2026-11-06)
- **Người soạn:** Antigravity theo yêu cầu của sinh viên TinMH (IT); sinh viên rà soát và chốt: [ĐIỀN]
- **Thay thế:** ADR 0009 (`0009-phan-quyen-pham-vi-de-cuong-clo.md`)
- **Liên quan:** ADR 0003 (xác thực, phân quyền F01), ADR 0004 (QA thẩm định CTĐT/PLO), ADR 0005 (`0005-phan-quyen-khoi-tao-danh-muc-ctdt.md`), `doc/plan/F03-hoc-phan-clo.md`, `doc/tech-debt.md` (TD-27..TD-29)

---

## 1. Bối cảnh & Lý do thay đổi ADR 0009

Trong thiết kế trước đây (ADR 0009), hệ thống cho phép Trưởng ngành (`PROGRAM_MANAGER` - PM) vừa tạo môn học, vừa soạn đề cương và tự khóa `CONFIRMED`. Vì đề cương (`syllabi`) không chứa mã ngành `programVersionId`, ADR 0009 phải sử dụng cơ chế "suy quyền gián tiếp qua `program_courses`".

Qua rà soát kỹ thuật và đối chiếu với hồ sơ thực tế của 2 đề cương chi tiết học phần (`APCM220307` và `ABDA433708`) tại Trường ĐH Sư phạm Kỹ thuật TP.HCM, mô hình cũ bộc lộ 3 bất cập lớn:
1. **Xung đột môn dùng chung:** Các môn đại cương/cơ sở khối ngành (Toán, Lý, Triết, Python...) được dùng chung cho nhiều ngành (CNTT, Fintech, Cơ khí...). Nếu Trưởng ngành này sửa đề cương môn dùng chung sẽ làm sai lệch chuẩn đầu ra của ngành khác.
2. **Lỗ hổng vượt quyền (TD-29):** PM có thể tự ý gán một môn của ngành khác vào CTĐT của mình để gián tiếp chiếm quyền sửa đề cương môn đó.
3. **Hiện tượng "Học phần mồ côi":** Nếu PM tạo môn học mới vào kho tổng nhưng môn đó chưa được phân công cho Bộ môn chuyên môn nào quản lý, dẫn đến không có người chịu trách nhiệm phê duyệt nội dung học thuật.
4. **Sai lệch thực tế hành chính đại học:** Trong thực tế, mã môn học thuộc thẩm quyền ban hành cấp trường của Phòng Đào tạo (`ADMIN`); nội dung đề cương do Giảng viên chuyên môn (`LECTURER`) biên soạn và Trưởng bộ môn (`DEPARTMENT_HEAD`) thẩm định, phê duyệt. Trưởng ngành (`PROGRAM_MANAGER`) chỉ phụ trách khung CTĐT và liên kết chuẩn đầu ra cấp ngành ($CLO \to PI$).

---

## 2. Quyết định Kiến trúc

Hệ thống bãi bỏ cơ chế suy quyền qua `program_courses` của ADR 0009 và thiết lập mô hình **Phân quyền phân cấp theo chiều dọc chuyên môn (Hierarchical Academic Delegation)** gồm 4 trụ cột:

### 2.1. Quản lý Danh mục học phần cấp trường (`courses` — Master Catalog)
* **Chỉ `ADMIN` (Phòng Đào tạo) có quyền:** Tạo mới (`POST`), Chỉnh sửa (`PUT`), Xóa (`DELETE`) môn học trong kho tổng `courses`.
* Thu hồi hoàn toàn quyền tạo/sửa môn học của `PROGRAM_MANAGER`.
* Môn học được coi là danh mục nền tảng (Master Data) đã được Nhà trường chốt và nạp sẵn vào hệ thống.
* Quyền đọc (`GET /courses`): Mọi người dùng đã đăng nhập (`isAuthenticated()`) đều được tra cứu công khai.

### 2.2. Phân công quản lý môn học theo Bộ môn (`scope.courseIds` & `scope.programIds`)
* **Admin phân môn và đơn vị Khoa/Ngành cho Trưởng bộ môn & Giảng viên:**
  * Khi bổ nhiệm Trưởng bộ môn (`DEPARTMENT_HEAD`), Admin gán danh mục các môn học thuộc Bộ môn quản lý vào `scope.courseIds`, đồng thời gán `scope.programIds` biểu thị Khoa/Ngành trực thuộc của Bộ môn.
  * Khi tạo tài khoản Giảng viên (`LECTURER`), Admin gán `scope.programIds` biểu thị Khoa/Ngành trực thuộc của Giảng viên (mà không cần thay đổi `$jsonSchema` của `users`).
* **Trưởng bộ môn tra cứu & phân công cho Giảng viên:** 
  * Cung cấp API `GET /api/v1/departments/lecturers` để Trưởng bộ môn lấy danh sách Giảng viên (`LECTURER` đang `ACTIVE`) thuộc cùng phạm vi Khoa/Ngành (`scope.programIds` giao nhau) hoặc đã được phân công môn học của Bộ môn, mà không cần mở quyền `/api/v1/users` của Admin.
  * Cung cấp API `POST /api/v1/departments/assign-course` và `unassign-course` để Trưởng bộ môn phân công/thu hồi quyền biên soạn đề cương cho Giảng viên (gán/gỡ `courseId` trong `scope.courseIds` của Giảng viên).
  * **Ràng buộc an toàn kép (Dual Safety Invariant):**
    1. Trưởng bộ môn chỉ được quyền phân công những môn học nằm trong `scope.courseIds` của chính mình (`403 Forbidden` nếu ngoài scope).
    2. Trưởng bộ môn chỉ được quyền nhìn thấy và phân công cho Giảng viên có `scope.programIds` thuộc phạm vi Khoa/Ngành của mình (`422 LECTURER_OUT_OF_DEPARTMENT_SCOPE` nếu cố tình gán cho giảng viên Khoa/Ngành khác).

### 2.3. Biên soạn & Phê duyệt Đề cương chi tiết (`syllabi` & `clos`)
* **Biên soạn (DRAFT):** 
  * Giảng viên (`LECTURER`) có `courseId` nằm trong `scope.courseIds` có toàn quyền tạo đề cương (`POST /courses/{id}/syllabi`), thêm/sửa/xóa CLO (`POST/PUT/DELETE /clos`) khi đề cương đang ở trạng thái `status = DRAFT`.
* **Phê duyệt (CONFIRMED):** 
  * Chỉ Trưởng bộ môn (`DEPARTMENT_HEAD`) có `courseId` nằm trong `scope.courseIds` mới có quyền thẩm định và chuyển trạng thái đề cương sang `CONFIRMED` (`PUT /syllabi/{id}/status`), đồng thời hệ thống ghi nhận thông tin thẩm định vào khối `verification`.
  * Khi đề cương chuyển sang `CONFIRMED` hoặc `ARCHIVED`, hệ thống **khóa cứng 100%**: Cấm mọi thao tác thêm/sửa/xóa CLO và liên kết CLO–PI (giải quyết TD-27). Muốn thay đổi nội dung bắt buộc phải tạo phiên bản đề cương mới (`version` mới).

### 2.4. Trách nhiệm của Trưởng ngành (`PROGRAM_MANAGER`)
* Trưởng ngành toàn quyền quản lý Chương trình đào tạo thuộc phạm vi `scope.programIds`:
  * Quản lý thông tin CTĐT, phiên bản CTĐT, chuẩn đầu ra PLO, chỉ số thực hiện PI (F02).
  * Lựa chọn môn học có sẵn từ kho trường đưa vào CTĐT của ngành mình (`program_courses`).
  * Xác định học kỳ dự kiến, tính chất bắt buộc/tự chọn, điều kiện tiên quyết, và mức đóng góp $I/R/M$ vào PI (`course_pi_contributions`).
  * Lấy đề cương đã được Bộ môn duyệt (`CONFIRMED`) về để tạo liên kết chuẩn đầu ra $CLO \to PI$ cho CTĐT của ngành mình (`clo_pi_links`).

---

## 3. Ma trận Phân quyền Tổng hợp

| Hành động | Endpoint | Vai trò cho phép | Ràng buộc Scope |
|---|---|---|---|
| **Tạo / Sửa / Xóa môn học** | `/api/v1/courses/**` (GHI) | `ADMIN` | Toàn hệ thống (Phòng Đào tạo) |
| **Tra cứu GV trong Bộ môn** | `GET /api/v1/departments/lecturers` | `ADMIN`, `DEPARTMENT_HEAD` | Cùng `scope.programIds` với TBM (hoặc đã gán môn của Bộ môn) |
| **Phân công môn cho GV** | `/api/v1/departments/assign-course` | `ADMIN`, `DEPARTMENT_HEAD` | `scope.courseIds` chứa môn & GV cùng `scope.programIds` |
| **Tạo đề cương (DRAFT)** | `POST /api/v1/courses/{id}/syllabi` | `ADMIN`, `LECTURER` | `scope.courseIds` của GV chứa `courseId` |
| **Thêm / Sửa / Xóa CLO** | `/api/v1/clos/**` (GHI) | `ADMIN`, `LECTURER` | `scope.courseIds` chứa môn của đề cương & đề cương `DRAFT` |
| **Duyệt đề cương (CONFIRMED)** | `PUT /api/v1/syllabi/{id}/status` | `ADMIN`, `DEPARTMENT_HEAD` | `scope.courseIds` của TBM chứa môn của đề cương |
| **Gán môn vào CTĐT** | `POST /program-versions/{id}/courses` | `ADMIN`, `PROGRAM_MANAGER` | `scope.programIds` của PM chứa CTĐT đích |
| **Tạo liên kết CLO–PI** | `POST .../clo-pi-links` | `ADMIN`, `PROGRAM_MANAGER` | `scope.programIds` chứa CTĐT của liên kết |
| **Thẩm định CLO–PI** | `PUT .../clo-pi-links/{id}/verification` | `QA_OFFICER` | `scope.programIds` chứa CTĐT của liên kết (ADR 0004) |
| **Đọc danh mục / đề cương / CLO** | Mọi API `GET` | `isAuthenticated()` | Toàn thể người dùng đã xác thực |

---

## 4. Hệ quả & Lợi ích

1. **Về kiến trúc & hiệu năng:** 
   * Không còn phải truy vấn DB ngược qua bảng `program_courses` để suy quyền như ADR 0009. Kiểm tra scope môn học là thao tác tra cứu tập hợp $O(1)$ (`principal.getScope().getCourseIds().contains(courseId)`), cực nhanh và sạch.
2. **Giải quyết triệt để các nợ kỹ thuật:**
   * **Triệt tiêu TD-29:** PM không thể chiếm quyền sửa đề cương bằng cách gán môn vào CTĐT của mình.
   * **Giải quyết TD-27:** Đề cương khác `DRAFT` (gồm `CONFIRMED` và `ARCHIVED`) đều bị khóa cứng không cho sửa CLO hay liên kết.
   * **Giải quyết TD-28:** Kiểm tra môn học của đề cương bắt buộc phải thuộc CTĐT trước khi tạo liên kết CLO–PI.
3. **Tính chân thực học thuật (Academic Realism):**
   * Hoàn toàn đồng nhất với quy chế đại học và các minh chứng thực tế từ Khoa Kinh tế và Khoa CNTT. Dễ dàng giải trình và thuyết phục Hội đồng phản biện.

---

## 5. Để vấn đáp bảo vệ đồ án

* **Hỏi 1: Vì sao không cho Trưởng ngành (PROGRAM_MANAGER) tự tạo môn học và viết đề cương nữa?**  
  * *Trả lời:* Trong đại học theo chuẩn kiểm định quốc tế, Trưởng ngành quản lý khung chương trình đào tạo và chuẩn đầu ra cấp ngành (PLO/PI). Môn học là tài sản học thuật dùng chung do các Bộ môn chuyên môn quản lý. Nếu để Trưởng ngành sửa đề cương sẽ làm sai lệch chuẩn đầu ra của các ngành khác cùng học môn đó. Tách bạch Trưởng bộ môn duyệt và Trưởng ngành liên kết CLO-PI đảm bảo nguyên tắc phân chia trách nhiệm (Separation of Concerns).
* **Hỏi 2: Trưởng bộ môn phân công cho Giảng viên bằng cách nào trên hệ thống và làm sao để Trưởng bộ môn không thấy/phân công nhầm giảng viên khoa khác?**  
  * *Trả lời:* Thông qua cơ chế phân quyền ủy quyền (Delegated Administration) kết hợp giao tập hợp `scope.programIds`. Admin gán danh mục môn của Bộ môn (`scope.courseIds`) và ngành trực thuộc (`scope.programIds`) cho Trưởng bộ môn, đồng thời gán `scope.programIds` cho Giảng viên. Khi Trưởng bộ môn gọi `GET /api/v1/departments/lecturers` hoặc `POST /api/v1/departments/assign-course`, hệ thống đối chiếu giao (`intersect`) tập `programIds` giữa Trưởng bộ môn và Giảng viên mà không cần mở quyền quản trị `/api/v1/users` hay sửa đổi schema `database_import.js`.
