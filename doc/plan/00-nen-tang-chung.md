# 00 — Nền tảng dùng chung

Story: **US-39** (health check, log, cảnh báo), **US-40** (xử lý ngoại lệ thống nhất). Ưu tiên C. Giai đoạn P0, làm
**trước mọi feature khác**. Chủ trì đề xuất: IT. Kiến trúc: Chuẩn 3 tầng (3-tier) theo cấu trúc Package-by-Layer ở cấp gốc `com.hcmute.lms.lmsapi.{controller, dto, service, service.impl, repository, repository.impl, document, enums, mapper, mapper.impl, exception, config, filter}` tuân thủ 5 nguyên lý SOLID.

## 1. Mục tiêu

Đặt sẵn những phần mà mọi feature dùng lại: cấu hình Mongo + transaction trên Replica Set `rs0`, định dạng lỗi thống nhất (US-40), dịch vụ ghi
`audit_logs` có sanitize dữ liệu nhạy cảm qua `mapper` độc lập, khung kiểm thử tích hợp với MongoDB replica set thật, health check và correlationId (US-39), cùng tài liệu hợp đồng liên repo `API-CONTRACT.md`. Làm xong phần này thì các feature sau chỉ cần
viết nghiệp vụ tuân thủ đúng mô hình 3 tầng.

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` §2 (14 nguyên tắc), §4.29 (`audit_logs`), §6 (kiểm tra hợp lệ), §9 (nạp CSDL).
- `database_import.js`: khối `audit_logs`.
- `README.md` gốc: chạy MongoDB bằng Docker.
- `.claude/rules/03-code-quality-and-git.md` (cấu trúc mã), `04-testing-and-cicd.md` (kiểm thử, CI).

## 3. Hạng mục

### 3.1 Cấu hình MongoDB và transaction
- Kết nối qua `spring.mongodb.uri` (đã có trong `application.properties.example`, đọc từ biến `MONGODB_URI`).
- Transaction nhiều document (xác nhận nhập điểm, duyệt bài đánh giá…) cần bean quản lý transaction của Spring Data MongoDB
  và replica set. **Tra tài liệu chính thức đúng phiên bản trong Spring Boot 4.1** để biết có cần khai báo bean thủ công
  hay không; đừng viết theo trí nhớ Spring Boot 3.
- Phạm vi: chỉ cấu hình, không thêm thư viện.

### 3.2 Xử lý lỗi thống nhất (US-40)
- Một `@RestControllerAdvice` duy nhất trả cùng một cấu trúc lỗi. **[ĐỀ XUẤT]** `{ timestamp, status, code, message,
  path, fieldErrors[]? }`; `code` là hằng enum (ví dụ `VALIDATION_ERROR`, `NOT_FOUND`, `FORBIDDEN`, `CONFLICT`,
  `BUSINESS_RULE_VIOLATION`), không phải chuỗi tự do.
- Phân biệt: 400 (validation), 401, 403, 404, 409 (xung đột `revision`, trùng khóa duy nhất), 422 (vi phạm quy tắc nghiệp vụ như
  tổng `weight` ≠ 1, cặp CLO–PI không có trong `clo_pi_links`).
- Không lộ stack trace hay thông tin nội bộ ra ngoài; log chi tiết ở phía server.

### 3.3 Dịch vụ ghi nhật ký (`audit`) và Kiến trúc SOLID
- Interface `AuditService` đặt tại `com.hcmute.lms.lmsapi.service`, triển khai `AuditServiceImpl` đặt tại `com.hcmute.lms.lmsapi.service.impl`.
- `action` thuộc enum của validator: `SCORE_UPDATE`, `IMPORT_CONFIRM`, `ASSESSMENT_APPROVE`, `ASSESSMENT_REJECT`, `RUN_CALCULATION`, `RULE_CONFIRM`, `LOGIN` đặt tại `com.hcmute.lms.lmsapi.enums`.
- **Nguyên lý Single Responsibility (SRP):** Tách logic làm sạch dữ liệu nhạy cảm (sanitization cho password, hash, token, secret) và khởi tạo tài liệu ra interface riêng `AuditLogMapper` (`com.hcmute.lms.lmsapi.mapper`) và lớp hiện thực `AuditLogMapperImpl` (`com.hcmute.lms.lmsapi.mapper.impl`), không trộn lẫn vào `AuditServiceImpl`.
- **Nguyên lý Open-Closed & Dependency Inversion (OCP & DIP):** Tầng Data Access tách `AuditLogRepository` và `AuditLogRepositoryCustom` (`com.hcmute.lms.lmsapi.repository`) kèm `AuditLogRepositoryCustomImpl` (`com.hcmute.lms.lmsapi.repository.impl`) sử dụng `MongoTemplate` cho truy vấn động mở rộng. `AuditServiceImpl` chỉ phụ thuộc vào `AuditLogRepository` và `AuditLogMapper` abstractions.
- Khi được gọi trong một thao tác có transaction thì bản ghi audit **nằm cùng transaction** (lỗi giữa chừng không để lại audit mồ côi).
- `reason` bắt buộc với `SCORE_UPDATE`. Không ghi mật khẩu, băm mật khẩu hay dữ liệu cá nhân thừa vào `before`/`after`.

### 3.4 Health check, log, cảnh báo (US-39)
- `/actuator/health` cần `spring-boot-starter-actuator`: **chưa có trong `pom.xml`**, thêm kèm lý do (US-39) và giữ phiên bản do
  Spring Boot quản lý. Chỉ mở endpoint `health` (và `info`), không mở tất cả endpoint actuator.
- Log có cấu trúc, không ghi dữ liệu nhạy cảm; thêm `correlationId` nếu không phức tạp hóa.
- Cảnh báo khi lỗi: phụ thuộc môi trường staging **[CHỜ XÁC NHẬN Q06, Q07]**; ở P0 chỉ cần health check hoạt động.

### 3.5 Khung kiểm thử
- Unit: JUnit 5 + Mockito (đã có qua starter test).
- Integration: cần MongoDB **replica set** thật. Cách đề xuất: Testcontainers với image `mongo:8.0` chạy replica set
  (cần thêm dependency Testcontainers: **ghi ADR + lý do**), hoặc dùng service MongoDB trong CI. Chọn và ghi ADR.
- Dữ liệu test: tạo bằng code/fixture, đặt tên "DỮ LIỆU THỬ NGHIỆM"; không dùng dữ liệu thật.
- Quy ước tên test: `@DisplayName("US-xx/AC-y: …")`.

### 3.6 Quy ước chung cho tầng truy cập dữ liệu
- Repository chỉ truy cập một collection; kiểm tra tham chiếu (cloId tồn tại, cặp CLO–PI hợp lệ…) nằm ở **service**.
- Bắt lỗi `DuplicateKeyException` từ chỉ mục duy nhất và đổi thành lỗi 409 thống nhất.
- Bắt lỗi validator của MongoDB (`$jsonSchema`, mã 121) và đổi thành 422/400 thay vì để lộ lỗi 500.

## 4. Acceptance criteria (nháp)

| ID | Given / When / Then |
|---|---|
| US-40/AC-1 | Khi gọi API với body sai kiểu/thiếu trường bắt buộc → 400, `code = VALIDATION_ERROR`, `fieldErrors` nêu từng trường |
| US-40/AC-2 | Khi vi phạm quy tắc nghiệp vụ (ví dụ tổng `weight` ≠ 1) → 422, `code = BUSINESS_RULE_VIOLATION`, thông báo nêu quy tắc |
| US-40/AC-3 | Khi ghi trùng khóa duy nhất (ví dụ `plos(programVersionId, code)`) → 409, `code = CONFLICT` |
| US-40/AC-4 | Khi có lỗi không lường trước → 500, thân lỗi không chứa stack trace; log phía server có chi tiết |
| US-39/AC-1 | `GET /actuator/health` trả `UP` khi Mongo kết nối được và `DOWN` khi không |
| US-39/AC-2 | Thao tác có transaction: nếu bước sau lỗi thì cả `scores`/trạng thái lẫn bản ghi `audit_logs` đều không được ghi (test tích hợp trên replica set) |

## 5. Test bắt buộc

- Mỗi mã lỗi ở `@RestControllerAdvice` có ít nhất một test API (MockMvc).
- `AuditService`: ghi đúng `entity`, không lộ trường nhạy cảm, rollback cùng transaction.
- Ca biên: body rỗng, `ObjectId` sai định dạng ở path (400, không phải 500), trường thừa.

## 6. Không làm / chưa quyết

- Không làm: cơ chế log tập trung riêng (ELK…) khi chưa có hạ tầng; không thêm công cụ giám sát ngoài yêu cầu **[CHỜ Q06, Q07]**.
- Đã quyết (ghi trong ADR 0002): Chạy MongoDB 8.0 replica set `rs0` qua Docker cục bộ và GitHub Actions CI service thay vì Testcontainers; chuẩn hóa mô hình 3 tầng (Package-by-Layer ở cấp gốc kết hợp `.impl`) tuân thủ trọn vẹn 5 nguyên lý SOLID.

## 7. Để vấn đáp (gợi ý cho người review & bảo vệ đồ án)

Hội đồng có thể hỏi:
1. **Vì sao chọn mô hình 3 tầng (Package-by-Layer) và tách `.impl`?**
   - Phân định rành mạch trách nhiệm (SRP): Controller chỉ tiếp nhận HTTP và validate DTO; Service chỉ chứa logic nghiệp vụ; Mapper lọc sạch dữ liệu nhạy cảm; Repository chỉ truy cập CSDL.
   - Tuân thủ Dependency Inversion (DIP) và Open-Closed (OCP): Controller và Service chỉ phụ thuộc vào Interface trừu tượng ở package gốc, các lớp hiện thực đặt tại `.impl` được Spring IoC tiêm tự động.
2. **Vì sao xử lý lỗi tập trung (`@RestControllerAdvice`) và ban hành `API-CONTRACT.md`?**
   - Đảm bảo US-40: Mọi lỗi từ chối đều trả về đúng cấu trúc `{ timestamp, status, code, message, path, fieldErrors }` với mã `ErrorCode` enum nhất quán. Giúp Frontend xử lý tập trung qua HTTP Interceptor, không bị crash ứng dụng.
3. **Vì sao cần MongoDB Replica Set `rs0` và chạy qua Docker/CI service thay vì Testcontainers?**
   - Spring Data MongoDB yêu cầu Replica Set để kích hoạt `MongoTransactionManager` thực thi multi-document transactions (đảm bảo tính nguyên tử khi nhập điểm và ghi audit log).
   - Chọn Docker/CI service giúp môi trường dev và CI đồng nhất 100%, không phát sinh lỗi quyền truy cập Docker socket trên máy sinh viên, không làm nặng file `pom.xml`.
4. **Nếu bỏ `@RestControllerAdvice` hoặc Replica Set thì hệ thống bị gì?**
   - Bỏ Exception Handler: Mọi lỗi ngoài ý muốn sẽ trả về HTTP 500 kèm toàn bộ stack trace nội bộ ra client, gây lỗ hổng bảo mật nghiêm trọng (Information Exposure).
   - Bỏ Replica Set: Các thao tác nghiệp vụ có `@Transactional` sẽ ném ngoại lệ khi ghi nhiều document, dẫn đến nguy cơ dữ liệu mồ côi (audit log ghi nhưng dữ liệu điểm lỗi, hoặc ngược lại).
