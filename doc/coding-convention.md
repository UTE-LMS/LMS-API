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
- Package: `com.hcmute.lms.lmsapi.<module>.{controller,service,repository,dto,document,mapper}`; module theo chức
  năng: `auth`, `program`, `course`, `assessment`, `academic`, `scoreimport`, `measurement`, `report`, `improvement`, `audit`.
- Tên: lớp `PascalCase`; phương thức/biến `camelCase`; hằng `UPPER_SNAKE_CASE`; bean/collection theo `snake_case`
  đúng như tài liệu CSDL (`course_sections`, `student_results`). Enum đặt tên như trong CSDL (`SCORED`, `BLANK`…).
- Controller chỉ nhận/trả DTO, không chứa nghiệp vụ, không trả `document`. Nghiệp vụ nằm ở `service`;
  phép tính đo lường là service thuần, không phụ thuộc HTTP.
- Kiểm tra đầu vào bằng Bean Validation; lỗi xử lý tập trung bằng `@RestControllerAdvice`, mã lỗi nhất quán.
- Tham chiếu giữa collection kiểm tra ở service trước khi ghi (MongoDB không có khóa ngoại).
- Cấu hình qua `application.properties` + biến môi trường; chỉ commit `application.properties.example`.
- Kết nối MongoDB: property **`spring.mongodb.uri`** (Spring Boot 4).
- Log không chứa dữ liệu nhạy cảm; không log mật khẩu, token, mã sinh viên thật.

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
