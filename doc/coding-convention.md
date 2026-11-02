# Coding convention (dự thảo)

> **DỰ THẢO — hai sinh viên xác nhận.** Rubric TC2.4 yêu cầu convention được công bố và tuân thủ. Bản này là đề
> xuất mặc định; sửa trực tiếp nếu hai bạn chọn khác. Áp dụng cho cả `LMS-API` và `LMS-UI`. Tình trạng cài đặt và
> kết quả đo của các công cụ kiểm tra tự động (Checkstyle/Spotless, SonarQube/SpotBugs, gitleaks, ESLint,
> Prettier…) **không ghi ở đây** mà ghi ở [`doc/quality/README.md`](quality/README.md); tài liệu này chỉ nêu quy
> ước phải tuân theo. Nguồn quy ước: `CLAUDE.md` và `.claude/rules/03-*` của từng repo (UI:
> `LMS-UI/.claude/rules/03-angular-architecture-and-quality.md`).

## 1. Chung

- Mã định danh, tên file, commit message viết **tiếng Anh**; tài liệu và giao diện người dùng viết tiếng Việt.
- Không hardcode giá trị cấu hình, ngưỡng, trọng số (xem `.claude/rules/02-domain-invariants.md`). Không secret trong mã.
- Hàm ngắn, một việc; không mã chết, không `System.out.println`/`console.log` còn sót; comment chỉ giải thích "vì sao".
- Không magic number, không chuỗi hardcode: dùng enum, hằng hoặc cấu hình.
- Không tắt rule để cho qua: không `@SuppressWarnings` (Java), không `// eslint-disable`, `@ts-ignore` (UI). Nếu bắt
  buộc phải có ngoại lệ thì ghi lý do ngay tại chỗ **và** thêm một dòng vào `doc/tech-debt.md`.
- Không commit secret, chuỗi kết nối thật hay dữ liệu sinh viên thật; cấu hình thật đặt qua biến môi trường, chỉ
  commit file `*.example`. Chạy công cụ quét secret trước khi commit (xem mục 6).
- Thêm dependency chỉ khi có lý do gắn yêu cầu, giấy phép tương thích, phiên bản cố định; xóa dependency không dùng.
  Không tự nâng/hạ phiên bản chính của JDK, Spring Boot, Angular, MongoDB khi chưa hỏi.
- Mọi thay đổi vào `main` qua Pull Request có review chéo (người mở PR không tự approve). Cách bật bảo vệ nhánh:
  [`doc/quy-trinh/bao-ve-nhanh-main.md`](quy-trinh/bao-ve-nhanh-main.md).

## 2. Java / Spring Boot (LMS-API)

- Phong cách: **Google Java Style** (đề xuất kiểm bằng Spotless hoặc Checkstyle; chưa cài).
- **Kiến trúc phân tầng 3 lớp (3-Tier) & Package-by-Layer ở cấp gốc:**
  - `com.hcmute.lms.lmsapi.controller`: Tầng Presentation tiếp nhận HTTP, validate DTO, gọi Service; tuyệt đối không chứa logic nghiệp vụ, không gọi trực tiếp Repository, không trả đối tượng `document` ra ngoài.
  - `com.hcmute.lms.lmsapi.dto`: Các lớp Data Transfer Object (`ErrorResponse`, `FieldErrorDetail`, Request/Response DTOs); tách riêng khỏi exception và document theo SRP.
  - `com.hcmute.lms.lmsapi.service` & `com.hcmute.lms.lmsapi.service.impl`: Tầng Business Logic quản lý nghiệp vụ đo lường, biên giới giao dịch `@Transactional`; service là service thuần, không phụ thuộc Servlet.
  - `com.hcmute.lms.lmsapi.repository` & `com.hcmute.lms.lmsapi.repository.impl`: Tầng Data Access quản lý truy vấn MongoDB qua Spring Data MongoRepository và các truy vấn tùy biến nâng cao qua MongoTemplate (`*RepositoryCustom` / `*RepositoryCustomImpl`).
  - `com.hcmute.lms.lmsapi.document`: Domain Models ánh xạ tài liệu MongoDB và đối tượng nhúng (`UserScope`, `RoleAssignment`).
  - `com.hcmute.lms.lmsapi.enums`: Enum nghiệp vụ dùng chung giữa các tầng (`Role`, `UserStatus`, `AuditAction`, `AccessType`, `PloCategory`, `ProgramVersionStatus`, `VerificationStatus`, `PiContributionType`) theo đúng `$jsonSchema`; không đặt enum trong `dto` hay `document` (tránh phụ thuộc ngược tầng). `ErrorCode` là ngoại lệ, đặt ở `exception` vì gắn chặt với `AppException`.
  - `com.hcmute.lms.lmsapi.mapper` & `com.hcmute.lms.lmsapi.mapper.impl`: Chuyển đổi giữa DTO và Document, đảm nhiệm làm sạch/che giấu dữ liệu nhạy cảm (sanitization) theo SRP và DIP.
  - `com.hcmute.lms.lmsapi.exception`: Ngoại lệ nghiệp vụ (`AppException`, `BusinessRuleViolationException`, `ResourceNotFoundException`), `ErrorCode` enum và `@RestControllerAdvice` tập trung (`GlobalExceptionHandler`).
  - `com.hcmute.lms.lmsapi.config`: Cấu hình Spring Beans (`MongoConfig`, `SecurityConfig`, `WebConfig`).
  - `com.hcmute.lms.lmsapi.filter`: Bộ lọc HTTP (`CorrelationIdFilter` quản lý `X-Correlation-Id` và MDC).
  - `com.hcmute.lms.lmsapi.util`: Hàm tiện ích tĩnh không giữ trạng thái (`ObjectIdUtils`, `PageableValidationUtils`, `StringNormalizationUtils`); không chứa logic nghiệp vụ.
  - `com.hcmute.lms.lmsapi.validator`: Bean Validation tùy biến và kiểm tra chính sách dùng lại (`PasswordPolicyValidator`, `@Utf8ByteLength`).
- **Tuân thủ 5 nguyên lý SOLID:**
  - *SRP*: Phân định trách nhiệm rành mạch: Mapper sanitize dữ liệu, Service tính toán nghiệp vụ, Custom Repository truy vấn dữ liệu phức tạp.
  - *OCP & DIP*: Các tầng phụ thuộc vào Interface (đặt tại package gốc); class hiện thực đặt tại `.impl` tương ứng và được Spring IoC tiêm qua constructor.
  - *ISP*: Interface nhỏ gọn, chuyên biệt theo vai trò.
  - *LSP*: Các class trong `.impl` hoàn toàn có thể thay thế cho Interface mà không làm thay đổi hành vi kỳ vọng của hệ thống.
- **Quy tắc phụ thuộc một chiều:** `controller` → `service` (interface) → `repository` (interface) → `document`. Nghiêm cấm nhảy cóc tầng.
- Tên: lớp `PascalCase`; phương thức/biến `camelCase`; hằng `UPPER_SNAKE_CASE`; bean/collection theo `snake_case` đúng như tài liệu CSDL (`course_sections`, `student_results`). Enum đặt tên như trong CSDL (`SCORED`, `BLANK`…).
- Kiểm tra đầu vào bằng Bean Validation; lỗi xử lý tập trung bằng `@RestControllerAdvice`, mã lỗi nhất quán.
- Tham chiếu giữa collection kiểm tra ở service trước khi ghi (MongoDB không có khóa ngoại).
- Cấu hình qua `application.properties` + biến môi trường; chỉ commit `application.properties.example`.
- Kết nối MongoDB: property **`spring.mongodb.uri`** (Spring Boot 4).
- Log không chứa dữ liệu nhạy cảm; không log mật khẩu, token, mã sinh viên thật. Có `X-Correlation-Id` và MDC cho mọi request.
- Controller chỉ nhận và trả DTO (không trả `document`), không chứa logic nghiệp vụ. Kiểm tra quyền theo vai trò đặt ở
  tầng web bằng `@PreAuthorize`/cấu hình bảo mật; kiểm tra phạm vi dữ liệu đặt ở service.
- Hạn chế đã biết giữa quy ước ở trên và mã hiện có, chờ hai sinh viên quyết định sửa mã hay sửa quy ước (không tự
  đổi tại đây): (1) quy ước nói service "không phụ thuộc Servlet" nhưng `AuthService.login(…, HttpServletRequest)` và
  `AuditServiceImpl` (đọc `RequestContextHolder` để lấy IP) có phụ thuộc Servlet; (2) có một `@SuppressWarnings("unchecked")`
  trong `AuditLogMapperImpl` mà chưa ghi lý do/nợ kỹ thuật theo mục 1; (3) `dto` import `document` (`UserProfileResponse.from(User)`,
  `UserPrincipal.from(User)`) và `exception` import `dto` (`ErrorResponse`), ngược với ý "tách DTO khỏi exception/document".

## 3. TypeScript / Angular (LMS-UI)

Phong cách: **Angular Style Guide** chính thức (`angular.dev/style-guide`, tra đúng phiên bản Angular 21) + **ESLint**
(`angular-eslint`, cấu hình ở `LMS-UI/eslint.config.js`) + **Prettier**. Quy tắc kiến trúc và chất lượng chi tiết nằm ở
`LMS-UI/.claude/rules/03-angular-architecture-and-quality.md`; hai bộ rulebook sinh mã (`angular_component_rulebook.md`,
`angular_model_service_rulebook.md` trong cùng thư mục `.claude/rules/`) là tài liệu hướng dẫn cho agent sinh
component/model/service và phải được đối chiếu với convention này, không thay thế nó.

- **Định dạng:** `.prettierrc` (printWidth 100, nháy đơn, parser `angular` cho HTML) và `.editorconfig` (2 space,
  UTF-8) đã có; định dạng bằng Prettier trước khi báo xong. `npm run lint` phải sạch lỗi (mục tiêu 0 lỗi lint).
- **Tên:** theo kiểu mà Angular CLI sinh ra trong repo, không trộn kiểu cũ/mới; selector tiền tố `app`. Tên thư mục,
  file, class, interface viết tiếng Anh.
- **Cấu trúc:** `core/` (dùng toàn app: api client, auth, interceptor, guard, models, services…), `shared/`
  (thành phần dùng lại, không phụ thuộc nghiệp vụ), `features/` (mỗi nhóm chức năng một thư mục, lazy load). Phụ thuộc
  một chiều `features → shared, core`; feature không import chéo feature khác.
- **Angular hiện đại:** standalone component, `inject()`, signals (`signal`, `computed`, `input()`, `output()`),
  control flow `@if/@for/@switch` (`@for` có `track`), `ChangeDetectionStrategy.OnPush` cho component mới, Reactive
  Forms. Không logic nặng trong template; logic gọi API nằm trong service.
- **TypeScript:** `strict` và `strictTemplates`; **cấm `any`** (dùng `unknown` + thu hẹp kiểu), cấm `@ts-ignore`,
  `eslint-disable` để cho qua. Model là `interface`/`type` khớp DTO backend, không suy diễn tên trường.
- **Gọi API và bảo mật:** một lớp HTTP tập trung (`HttpClient` + interceptor); URL API đặt ở cấu hình theo môi trường,
  không có secret; không đặt token trong URL, không log token; không `innerHTML` với dữ liệu người dùng, không
  `bypassSecurityTrust*` nếu chưa có lý do ghi rõ. UI không tự tính công thức đo lường.
- **Dependency:** ghim đúng major tương thích Angular 21 (không dùng `@latest`, không `--force`/`--legacy-peer-deps`),
  nêu lý do và giấy phép, ghi nhật ký AI; thư viện lớn ghi ADR. Không nới budget build để cho qua.
- Cần hai sinh viên chốt: bản dự thảo cũ của file này dẫn kiểu CLI 21 (`app.ts` → `class App`), còn thư mục
  `src/app` của LMS-UI trên nhánh `feat/f01-ui-screens` (đọc ngày 2026-11-02) có `app.component.ts`. Chọn một kiểu,
  rồi ghi vào đây và vào rule 03 của UI.

## 4. Test

- Tên test nêu hành vi và gắn AC: `US-xx/AC-y: <hành vi>` (API: `@DisplayName`; UI: tên `it(...)`). Bảng AC ↔ test:
  [`doc/test-plan.md`](test-plan.md).
- Bắt buộc có ca âm, ca biên. Không skip/`@Disabled`/`xit` để cho pipeline xanh.
- Giá trị kỳ vọng tính tay độc lập, không lấy kết quả của chính mã làm kỳ vọng; dữ liệu giả mang nhãn "DỮ LIỆU THỬ NGHIỆM".

## 5. Git

- Nhánh: `feat/…`, `fix/…`, `docs/…`, `test/…`, `chore/…`. Không làm trực tiếp trên `main`.
- Commit theo Conventional Commits, tiếng Anh, thể mệnh lệnh: `feat(scoreimport): validate score range`.
- Commit nhỏ, một ý. Không `--no-verify`, không force-push lên nhánh chung.
- Mô tả PR nêu: mục tiêu, user story/AC liên quan, cách kiểm chứng, phần có AI hỗ trợ, ảnh hưởng tài liệu/sơ đồ.
  Mỗi sinh viên commit bằng danh tính Git của riêng mình.

## 6. Điều kiện kiểm tra bằng công cụ

Các ngưỡng dưới đây là **mục tiêu** lấy từ `.claude/rules/03-*` (không phải kết quả đã đạt). Công cụ đang dùng, cách
chạy và kết quả đo thật (kèm ngày, commit) ghi ở [`doc/quality/README.md`](quality/README.md); chưa đo thì ghi
"chưa đo" ở đó, không ước lượng.

| Điều kiện | Mục tiêu | Công cụ đề xuất theo rule | Nơi ghi kết quả |
|---|---|---|---|
| Lint / style | 0 lỗi | API: Checkstyle hoặc Spotless; UI: ESLint, Prettier | `doc/quality/README.md` |
| Phân tích tĩnh | 0 Blocker/Critical | SonarQube/SonarCloud hoặc SpotBugs | `doc/quality/README.md` |
| Trùng lặp mã | ≤ 3% | SonarQube/SonarCloud hoặc công cụ tương đương | `doc/quality/README.md` |
| Secret | 0 secret | gitleaks hoặc trufflehog | `doc/quality/README.md` |
| Coverage mô-đun lõi | ≥ 70% dòng lệnh | JaCoCo (API), `@vitest/coverage-v8` (UI) | CI artifact + `doc/quality/README.md` |
