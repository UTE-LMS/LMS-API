# SRS — Đặc tả yêu cầu (mục lục US → AC)

> **Dự thảo do AI soạn, chờ hai sinh viên đọc và hoàn thiện.** File này là **mục lục**: trỏ tới nơi đang giữ user story và
> acceptance criteria (AC) trong `doc/plan/`, **không chép lại AC** để tránh hai bản lệch nhau. AC trong `doc/plan/` cũng là
> **bản nháp chưa ký** (`doc/plan/README.md`). Trạng thái triển khai bên dưới được đối chiếu với mã ngày **2026-11-02**
> (`main`, commit `54ef80e`) bằng cách đọc controller, service, `SecurityConfig` và test; **không phải** kết quả nghiệm thu.

Liên quan: [`doc/plan/README.md`](../plan/README.md) (ưu tiên C/MR/K, nguồn sự thật), [`doc/sdd/README.md`](../sdd/README.md)
(thiết kế), [`doc/test-plan.md`](../test-plan.md) (AC ↔ test), [`nfr-de-xuat.md`](nfr-de-xuat.md) (NFR đề xuất),
[`doc/GiaiThich_CoSoDuLieu.md`](../GiaiThich_CoSoDuLieu.md) (từ điển dữ liệu), [`doc/chot-feature-do-an.md`](../chot-feature-do-an.md) (phạm vi).

## 1. Ký hiệu

- Ưu tiên: **C** cam kết, **MR** mục tiêu mở rộng, **K** không làm (đề xuất chưa ký, `doc/plan/README.md` §2).
- Trạng thái triển khai (đọc từ mã): **Có API** = có endpoint/cơ chế chạy được; **Một phần**; **Chưa có mã**. Không dùng để
  nói "đã đạt AC": mức đạt AC xem `doc/test-plan.md` §8.

## 2. Mục lục theo feature (có mã)

### Nền tảng chung — [`doc/plan/00-nen-tang-chung.md`](../plan/00-nen-tang-chung.md)

| Story | Nội dung | Ưu tiên | AC trong plan | Trạng thái triển khai (từ mã) |
|---|---|---|---|---|
| US-39 | Health check, log, cảnh báo | C | US-39/AC-1, AC-2 | **Một phần.** Có `GET /actuator/health`, `/actuator/info`, `X-Correlation-Id` + MDC, cấu hình `MongoTransactionManager`. **Chưa có** cảnh báo khi lỗi (chờ Q06/Q07) và nghiệp vụ thật dùng transaction + audit |
| US-40 | Xử lý ngoại lệ thống nhất | C | US-40/AC-1…AC-4 | **Có.** `GlobalExceptionHandler`, 7 `ErrorCode`, `ErrorResponse` |

### F01 — Người dùng và phân quyền — [`doc/plan/F01-nguoi-dung-phan-quyen.md`](../plan/F01-nguoi-dung-phan-quyen.md)

| Story | Nội dung | Ưu tiên | AC trong plan | Trạng thái triển khai (từ mã) |
|---|---|---|---|---|
| US-01 | Đăng nhập/đăng xuất | C | US-01/AC-1…AC-6 | **Có API:** `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` |
| US-02 | Tạo/quản lý người dùng + vai trò + phạm vi | C | US-02/AC-1…AC-6 | **Có API:** `/users` (tạo, danh sách phân trang, xem, sửa, đổi trạng thái). Không có xóa người dùng |
| US-03 | Backend kiểm tra quyền theo phạm vi | C | US-03/AC-1…AC-5 | **Một phần.** Cơ chế có (`SecurityConfig`, `@PreAuthorize`, `ScopeAuthorizationService`). `canAccessProgram` được dùng ở API xác nhận F02; `canAccessSection` **chưa có endpoint thật gọi** |
| US-04 | Màn hình nhật ký thao tác | MR (ghi audit vẫn C) | _(chưa có dòng AC)_ | **Chưa có mã đọc nhật ký.** Chỉ `LOGIN` được ghi vào `audit_logs` |
| NEW-03 | Vai trò `STUDENT` xem kết quả của mình | MR | _(chưa có dòng AC)_ | **Chưa có mã.** Vai trò `STUDENT` có trong enum và bị từ chối ở ma trận quyền lớp |

### F02 — CTĐT, PLO, PI, ma trận I/R/M — [`doc/plan/F02-ctdt-plo-pi.md`](../plan/F02-ctdt-plo-pi.md)

| Story | Nội dung | Ưu tiên | AC trong plan | Trạng thái triển khai (từ mã) |
|---|---|---|---|---|
| US-05 | Chương trình + phiên bản | C | US-05/AC-1…AC-4 | **Có API:** `/programs` (GET, POST/PUT: `ADMIN` per ADR 0005), `/program-versions` (GET, POST/PUT: `PROGRAM_MANAGER` trong scope per ADR 0005) |
| US-06 | 7 PLO / 17 PI theo mã | C | US-06/AC-1…AC-5 | **Có API:** `/program-versions/{pvId}/plos` và `/pis` (GET, POST, PUT, DELETE). Dữ liệu 7 PLO/17 PI đến từ seed của `database_import.js`, không phải từ API |
| US-07 | Ma trận học phần đóng góp PI | C | US-07/AC-1…AC-4 | **Có API:** `/program-versions/{pvId}/pi-contributions` (GET, POST, DELETE; không có PUT). Kiểm "học phần thuộc phiên bản" chưa làm (chờ `program_courses`, F03) |
| US-08 | Xác nhận nội dung | MR | US-08/AC-1…AC-3 | **Có API:** `PUT …/verification` cho phiên bản, PLO, PI (chỉ `QA_OFFICER`, đề xuất chờ Q08) |

## 3. Mục lục theo feature (chưa có mã)

Mỗi file dưới đây giữ story và AC dự thảo của feature; **chưa có controller/service/document nghiệp vụ tương ứng** trong
`src/main` (tình trạng ngày 2026-11-02). `Course` và `CourseSection` chỉ là `document` phục vụ kiểm tra tồn tại/phân quyền.

| Feature | File | Story | Ưu tiên (xem plan §2) | Dòng AC có mã trong plan | Trạng thái |
|---|---|---|---|---|---|
| F03 Học phần, đề cương, CLO | [`F03-hoc-phan-clo.md`](../plan/F03-hoc-phan-clo.md) | US-09…12 | C | 15 (US-09…12); bổ sung 3 AC phân quyền theo Bộ môn và Giảng viên (US-10/AC-4, US-11/AC-5, US-12/AC-6 theo [ADR 0010](../adr/0010-phan-quyen-de-cuong-theo-bo-mon-va-giang-vien.md) thay thế ADR 0009) | Đã hoàn thành US-09..12 cơ bản; tài liệu thiết kế ADR 0010 đã chốt, chuẩn bị bổ sung test và mã phân quyền |
| F04 Bài đánh giá, rubric, ma trận đề (giảng viên tự khóa) | [`F04-bai-danh-gia.md`](../plan/F04-bai-danh-gia.md) | US-15, 16, NEW-01 (US-13, 14, 17: bỏ, ADR 0013) | US-15, 16: C; NEW-01: C (đổi nghĩa, GVHD đã xác nhận) | 16 (US-13…16, NEW-01) | Có mã cho US-15, US-16, NEW-01; US-13, 14 chưa làm |
| F05a Học kỳ, lớp, sinh viên | [`F05a-hoc-ky-lop-sinh-vien.md`](../plan/F05a-hoc-ky-lop-sinh-vien.md) | US-18…20 | C | 11 | Có mã (US-18 một phần: thiếu vế chặn nhập điểm chờ F05b; Excel chờ ADR) |
| F05b Nhập điểm | [`F05b-nhap-diem.md`](../plan/F05b-nhap-diem.md) | US-21…25 | US-21…24: C; US-25: MR | 16 | Chưa có mã |
| F06 Đo lường | [`F06-do-luong.md`](../plan/F06-do-luong.md) | US-26…30, NEW-04 | US-26…29: C; US-30, NEW-04: MR | 15 (US-26…30) | Chưa có mã |
| F07 Báo cáo, truy vết | [`F07-bao-cao-truy-vet.md`](../plan/F07-bao-cao-truy-vet.md) | US-31…36 | US-31…34: C; US-35, 36: MR | 11 | Chưa có mã |
| F08 Cải tiến | [`F08-cai-tien.md`](../plan/F08-cai-tien.md) | US-37, 38, NEW-02 | US-37, 38: C; NEW-02: MR | 10 | Chưa có mã |

## 4. Mức đầy đủ của AC so với yêu cầu "100% user story có AC" (rule 05)

Đối chiếu danh sách story ở tiêu đề mỗi file `doc/plan/` với các dòng AC dạng `US-xx/AC-y`:

| Story chưa có dòng AC | Ghi chú |
|---|---|
| US-04, NEW-03 (F01) | Cả hai là MR; cần viết AC nếu giữ trong phạm vi |
| NEW-04 (F06) | MR; cần viết AC nếu giữ trong phạm vi |
| US-17 (F04) | Đề xuất K (không làm) nên không cần AC; xác nhận khi chốt phạm vi |

Các story còn lại có AC dự thảo, nhưng **AC chưa được hai sinh viên và GVHD duyệt**; "100% US có AC" chỉ đạt khi AC được chốt.

## 5. Phần còn lại của SRS (chưa soạn; không tạo file rỗng)

Chỉ những mục có nội dung thật mới có file. Còn thiếu, cần sinh viên và người dùng nghiệp vụ cung cấp:
- Mô tả bài toán, bên liên quan, chân dung người dùng, phạm vi làm/không làm (TC1) — cần phỏng vấn người thật.
- Mô hình quy trình nghiệp vụ và quy tắc nghiệp vụ (business rules) tập trung. Hiện các bất biến nghiệp vụ nằm ở
  `doc/plan/README.md` §5 và `.claude/rules/02-domain-invariants.md`.
- Yêu cầu phi chức năng đã **chốt**: hiện chỉ có bản **đề xuất** ở [`nfr-de-xuat.md`](nfr-de-xuat.md).
- Câu hỏi mở Q01–Q23 và trạng thái xác nhận: `doc/GiaiThich_CoSoDuLieu.md` §8.
