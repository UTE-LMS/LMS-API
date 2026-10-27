# Coding convention (dự thảo)

> **DỰ THẢO — hai sinh viên xác nhận.** Rubric TC2.4 yêu cầu convention được công bố và tuân thủ. Bản này là đề
> xuất mặc định; sửa trực tiếp nếu hai bạn chọn khác. Áp dụng cho cả `LMS-API` và `LMS-UI`. Công cụ kiểm tra tự
> động (Checkstyle/Spotless, Sonar) **chưa được cài bên API**; UI đã có ESLint và Prettier.

## 1. Chung

- Mã định danh, tên file, commit message viết **tiếng Anh**; tài liệu và giao diện người dùng viết tiếng Việt.
- Không hardcode giá trị cấu hình, ngưỡng, trọng số (xem `.claude/rules/02-domain-invariants.md`). Không secret trong mã.
- Hàm ngắn, một việc; không mã chết, không `System.out.println`/`console.log` còn sót; comment chỉ giải thích "vì sao".
- Mọi thay đổi vào `main` qua Pull Request có review chéo.

## 2. Java / Spring Boot (LMS-API)

- Phong cách: **Google Java Style** (đề xuất kiểm bằng Spotless hoặc Checkstyle; chưa cài).
- **Kiến trúc phân tầng 3 lớp (3-Tier) & Package-by-Layer ở cấp gốc:**
  - `com.hcmute.lms.lmsapi.controller`: Tầng Presentation tiếp nhận HTTP, validate DTO, gọi Service; tuyệt đối không chứa logic nghiệp vụ, không gọi trực tiếp Repository, không trả đối tượng `document` ra ngoài.
  - `com.hcmute.lms.lmsapi.dto`: Các lớp Data Transfer Object (`ErrorResponse`, `FieldErrorDetail`, Request/Response DTOs); tách riêng khỏi exception và document theo SRP.
  - `com.hcmute.lms.lmsapi.service` & `com.hcmute.lms.lmsapi.service.impl`: Tầng Business Logic quản lý nghiệp vụ đo lường, biên giới giao dịch `@Transactional`; service là service thuần, không phụ thuộc Servlet.
  - `com.hcmute.lms.lmsapi.repository` & `com.hcmute.lms.lmsapi.repository.impl`: Tầng Data Access quản lý truy vấn MongoDB qua Spring Data MongoRepository và các truy vấn tùy biến nâng cao qua MongoTemplate (`*RepositoryCustom` / `*RepositoryCustomImpl`).
  - `com.hcmute.lms.lmsapi.document`: Domain Models ánh xạ tài liệu MongoDB và Enum dữ liệu theo đúng `$jsonSchema`.
  - `com.hcmute.lms.lmsapi.mapper` & `com.hcmute.lms.lmsapi.mapper.impl`: Chuyển đổi giữa DTO và Document, đảm nhiệm làm sạch/che giấu dữ liệu nhạy cảm (sanitization) theo SRP và DIP.
  - `com.hcmute.lms.lmsapi.exception`: Ngoại lệ nghiệp vụ (`AppException`, `BusinessRuleViolationException`, `ResourceNotFoundException`), `ErrorCode` enum và `@RestControllerAdvice` tập trung (`GlobalExceptionHandler`).
  - `com.hcmute.lms.lmsapi.config`: Cấu hình Spring Beans (`MongoConfig`, `SecurityConfig`, `WebConfig`).
  - `com.hcmute.lms.lmsapi.filter`: Bộ lọc HTTP (`CorrelationIdFilter` quản lý `X-Correlation-Id` và MDC).
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

## 3. TypeScript / Angular (LMS-UI)

- `.prettierrc` (printWidth 100, nháy đơn) và `.editorconfig` (2 space) đã có; ESLint (`angular-eslint`) đã cấu hình,
  `npm run lint` phải sạch lỗi. Chi tiết: `LMS-UI/.claude/rules/03-angular-architecture-and-quality.md`.
- Tên file/class theo kiểu CLI 21 sinh ra (`app.ts` → `class App`); selector tiền tố `app`.
- Cấm `any`, `@ts-ignore`, `eslint-disable` để cho qua.

## 4. Test

- Tên test nêu hành vi và gắn AC: `US-xx/AC-y: <hành vi>`.
- Bắt buộc có ca âm, ca biên. Không skip/`@Disabled`/`xit` để cho pipeline xanh.

## 5. Git

- Nhánh: `feat/…`, `fix/…`, `docs/…`, `test/…`, `chore/…`. Không làm trực tiếp trên `main`.
- Commit theo Conventional Commits, tiếng Anh, thể mệnh lệnh: `feat(scoreimport): validate score range`.
- Commit nhỏ, một ý. Không `--no-verify`, không force-push lên nhánh chung.
