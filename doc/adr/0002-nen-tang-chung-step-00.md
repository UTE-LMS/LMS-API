# ADR 0002 — Nền tảng dùng chung Bước 00: Kiến trúc 3 tầng (Package-by-Layer & SOLID), Actuator, Replica Set và Hợp đồng lỗi thống nhất

- **Trạng thái:** ĐÃ CHẤP NHẬN (ACCEPTED)
- **Ngày:** 2026-10-27
- **Người quyết định:** TinMH (Sinh viên IT), sinh viên IBS, Giảng viên hướng dẫn
- **Phạm vi:** `LMS-API` (toàn bộ cấu trúc mã nguồn) và `LMS-UI` (hợp đồng API liên repo `LMS-UI/doc/API-CONTRACT.md`)

---

## 1. Bối cảnh (Context)

Bước 00 khởi tạo nền tảng dùng chung (Shared Foundation) cho toàn bộ hệ thống đo lường chuẩn đầu ra đào tạo (`LMS-API` và `LMS-UI`), bao gồm hai User Story trọng tâm:
1. **US-39:** Giám sát sức khỏe dịch vụ (Health Check), thông tin phiên bản (Info) và cơ chế đánh dấu truy vết phân tán (`correlationId` / `X-Correlation-Id`).
2. **US-40:** Xử lý ngoại lệ tập trung với định dạng phản hồi chuẩn mực thống nhất `{ timestamp, status, code, message, path, fieldErrors }` cùng bảng mã lỗi `ErrorCode` enum nhất quán.

Đồng thời, hệ thống đo lường chuẩn đầu ra đòi hỏi các thao tác nghiệp vụ phức tạp liên quan đến nhiều collection (ví dụ: xác nhận nạp bảng điểm đồng thời ghi nhật ký kiểm toán `audit_logs`). Trong MongoDB, các thao tác nhiều document (multi-document transactions) bắt buộc phải thực thi trên cụm **Replica Set**.

Trước khi triển khai các tính năng nghiệp vụ từ F01 đến F08, hệ thống cần đưa ra các quyết định kiến trúc then chốt về:
- Cấu trúc tổ chức package mã nguồn theo mô hình 3 tầng (3-tier architecture) kết hợp phân tách interface/implementation để thỏa mãn trọn vẹn 5 nguyên lý SOLID.
- Phương án triển khai giám sát và cấu hình bảo mật.
- Phương án hạ tầng kiểm thử và môi trường CSDL MongoDB Replica Set.
- Hợp đồng giao tiếp kỹ thuật giữa Backend và Frontend nhằm loại trừ triệt để sai lệch dữ liệu.

---

## 2. Các Quyết Định Kiến Trúc (Decisions)

### QĐ-1: Chuẩn hóa mô hình kiến trúc 3 tầng phân theo lớp ở cấp gốc (Package-by-Layer at Root) & Tuân thủ triệt để SOLID
Toàn bộ mã nguồn `LMS-API` được tổ chức theo cấu trúc Package-by-Layer ngay tại cấp gốc package `com.hcmute.lms.lmsapi`, tách bạch rõ ràng giữa trừu tượng (Interface) và hiện thực (.impl) để đáp ứng trọn vẹn 5 nguyên lý thiết kế hướng đối tượng SOLID:

```
com.hcmute.lms.lmsapi
├── controller/             # Tầng Presentation: Tiếp nhận HTTP, validate DTO đầu vào, ủy quyền cho Service
├── dto/                    # Data Transfer Objects: ErrorResponse, FieldErrorDetail, Request/Response DTOs
├── service/                # Tầng Business Logic (Trừu tượng): Các Interface định nghĩa hợp đồng nghiệp vụ
│   └── impl/               # Hiện thực nghiệp vụ: Triển khai logic tính toán chuẩn đầu ra, transaction
├── repository/             # Tầng Data Access (Trừu tượng): Interface kế thừa MongoRepository & Custom
│   └── impl/               # Hiện thực truy vấn phức tạp: Triển khai MongoTemplate cho truy vấn nâng cao
├── document/               # Domain Models: Các thực thể MongoDB Document và Enum dữ liệu
├── mapper/                 # Trừu tượng chuyển đổi dữ liệu & làm sạch dữ liệu nhạy cảm (Sanitization)
│   └── impl/               # Hiện thực chuyển đổi DTO <-> Document, che giấu mật khẩu/token
├── exception/              # Ngoại lệ nghiệp vụ (AppException, ErrorCode) & GlobalExceptionHandler
├── config/                 # Cấu hình Spring Beans (MongoConfig, SecurityConfig, WebConfig)
└── filter/                 # Bộ lọc HTTP (CorrelationIdFilter gắn X-Correlation-Id và MDC)
```

#### Luồng phụ thuộc một chiều chuẩn 3 tầng (Unidirectional Dependency Flow)
- `controller` → `service` (Interface) → `repository` (Interface) → `document`.
- **Nguyên tắc phân tầng nghiêm ngặt:**
  - Controller chỉ nhận/trả DTO, ủy quyền xử lý cho Service Interface. Controller **tuyệt đối không** gọi trực tiếp Repository, không chứa logic nghiệp vụ tính điểm/đo lường, và không bao giờ trả đối tượng Document ra ngoài API.
  - Service chỉ phụ thuộc vào Repository Interface. Mọi tính toán đo lường là service thuần túy (pure service), không phụ thuộc vào Servlet Request/Response.
  - Mapper đảm nhiệm việc chuyển đổi giữa DTO và Document, tách biệt hoàn toàn khỏi Service.

#### Biện giải chi tiết theo 5 nguyên lý SOLID (SOLID Principles Alignment)
1. **Single Responsibility Principle (SRP - Đơn trách nhiệm):**
   - Mỗi tầng và mỗi lớp chỉ có một lý do duy nhất để thay đổi.
   - `AuditLogMapper` / `AuditLogMapperImpl` chịu trách nhiệm duy nhất là ánh xạ đối tượng và lọc bỏ dữ liệu nhạy cảm (sanitizing password, token, hash). Nhờ đó, `AuditServiceImpl` không bị trộn lẫn trách nhiệm làm sạch chuỗi mà chỉ tập trung vào nghiệp vụ kiểm tra điều kiện audit và lưu trữ.
   - Tầng DTO (`dto`) tách rời khỏi Exception (`exception`) giúp phân định rõ giữa cấu trúc dữ liệu truyền tải và cơ chế xử lý ngoại lệ.
   - Truy vấn phức tạp được tách ra `repository.impl` thay vì viết dồn vào Service.
2. **Open-Closed Principle (OCP - Đóng mở):**
   - Các tầng giao tiếp qua Interface (`AuditService`, `AuditLogRepository`, `AuditLogRepositoryCustom`, `AuditLogMapper`).
   - Khi cần mở rộng chiến lược ghi log (ví dụ ghi thêm vào Kafka, Elasticsearch hoặc định dạng audit mới), hệ thống có thể tạo class hiện thực mới hoặc mở rộng Custom Repository mà không cần sửa đổi mã nguồn của các lớp Controller hay Service hiện hữu.
3. **Liskov Substitution Principle (LSP - Thay thế Liskov):**
   - Mọi lớp trong package `.impl` (`AuditServiceImpl`, `AuditLogMapperImpl`, `AuditLogRepositoryCustomImpl`) tuân thủ nghiêm ngặt hợp đồng do Interface quy định; có thể thay thế hoàn toàn cho Interface đó mà không làm thay đổi tính đúng đắn của chương trình hay vi phạm kỳ vọng của các lớp gọi nó.
4. **Interface Segregation Principle (ISP - Phân tách giao diện):**
   - Các Interface được thiết kế tinh gọn, tập trung vào vai trò cụ thể thay vì gom thành một giao diện "Fat Interface". Ví dụ: `AuditService` chỉ chứa phương thức `record()`; `AuditLogRepositoryCustom` chỉ định nghĩa phương thức truy vấn mở rộng đặc thù.
5. **Dependency Inversion Principle (DIP - Đảo ngược phụ thuộc):**
   - Các module cấp cao (Controller, Service) **không** phụ thuộc trực tiếp vào các module cấp thấp (Service Impl, Repository Impl, Mapper Impl). Cả hai đều phụ thuộc vào trừu tượng (Interface đặt ở root package layer). Spring Framework IoC Container tự động tiêm (inject) các bean hiện thực vào constructor khi khởi động ứng dụng.

---

### QĐ-2: Tích hợp `spring-boot-starter-actuator` dưới Spring Boot 4.1 Parent
- Bổ sung `spring-boot-starter-actuator` vào `pom.xml`, không khai báo phiên bản tường minh (được Spring Boot Starter Parent 4.1.0 quản lý tự động).
- Giới hạn phơi bày an toàn: Chỉ mở endpoint `health` và `info` qua giao diện Web (`management.endpoints.web.exposure.include=health,info`).
- Cấu hình bảo mật Fail-Secure tại `SecurityConfig`:
  - Cho phép truy cập công khai không cần xác thực (`permitAll`) đối với `/actuator/health`, `/actuator/health/**` và `/actuator/info`.
  - Toàn bộ các endpoint quản trị nhạy cảm khác (`/actuator/beans`, `/actuator/env`, `/actuator/mappings`...) cùng các endpoint nghiệp vụ đều bắt buộc xác thực (`authenticated`), trả về HTTP `401 Unauthorized` nếu chưa đăng nhập.

---

### QĐ-3: Khởi chạy MongoDB 8.0 Replica Set `rs0` qua Docker và GitHub Actions CI Service (Không dùng Testcontainers)
- **Lý do chọn Replica Set:** MongoDB yêu cầu Replica Set để kích hoạt `MongoTransactionManager` hỗ trợ giao dịch nhiều document (multi-document transactions) cho Spring Data MongoDB.
- **Lý do KHÔNG sử dụng Testcontainers:**
  1. Tránh bổ sung thêm các dependency nặng (`testcontainers-bom`, `mongodb-testcontainers`) vào `pom.xml`, giữ mã nguồn tinh gọn và giải trình rõ ràng trong hội đồng bảo vệ.
  2. Testcontainers đòi hỏi quyền truy cập trực tiếp vào Docker daemon socket (`/var/run/docker.sock`), thường xuyên phát sinh lỗi cấp quyền hoặc không tương thích trên các môi trường máy trạm Windows của sinh viên và CI runner.
  3. Đảm bảo tính nhất quán tuyệt đối giữa môi trường phát triển cục bộ và môi trường CI: Cả hai đều dùng chung Docker image `mongo:8.0` chạy Replica Set `rs0`, được nạp trước cấu trúc 30 collection và `$jsonSchema` validator từ `doc/database_import.js`.

---

### QĐ-4: Xử lý ngoại lệ tập trung và Hợp đồng giao tiếp liên repo (Cross-Repo API Contract)
- Xây dựng `@RestControllerAdvice` duy nhất (`GlobalExceptionHandler`) bắt toàn bộ ngoại lệ phát sinh trong hệ thống.
- Chuẩn hóa định dạng phản hồi `ErrorResponse` gồm: `timestamp` (ISO-8601 UTC), `status`, `code` (`ErrorCode` enum), `message` (tiếng Việt an toàn), `path`, `fieldErrors`.
- Phân biệt minh bạch các mã HTTP: `400` (Validation), `401` (Chưa xác thực), `403` (Cấm quyền), `404` (Không tìm thấy), `409` (Xung đột khóa/revision), `422` (Vi phạm quy tắc nghiệp vụ hoặc `$jsonSchema` validator mã 121), `500` (Lỗi nội bộ máy chủ).
- Bảo mật an toàn (Information Exposure Prevention): Tuyệt đối không để lộ stack trace hoặc tên lớp nội bộ trong response HTTP 500; toàn bộ chi tiết lỗi được ghi log an toàn tại server kèm `X-Correlation-Id`.
- Bộ lọc `CorrelationIdFilter` quản lý `X-Correlation-Id` và SLF4J MDC, luôn dọn dẹp bằng khối `finally` để tránh rò rỉ bộ nhớ (ThreadLocal leak).
- Thiết lập tài liệu hợp đồng chính thức `D:\TLCN\LMS-UI\doc\API-CONTRACT.md` làm cầu nối ràng buộc giữa hai nhóm phát triển Frontend và Backend.

---

## 3. Các Phương Án Đã Cân Nhắc (Alternatives Considered)

| Tiêu chí | Phương án A (Được chọn) | Phương án B | Phương án C |
|---|---|---|---|
| **Kiến trúc package** | **Package-by-Layer ở cấp gốc kết hợp `.impl` (Tuân thủ trọn vẹn SOLID & 3-Tier)** | Package-by-Feature phẳng (flat theo chức năng audit, common...) | Kiến trúc Hexagonal / Onion đầy đủ (Port & Adapter) |
| *Ưu điểm* | Trách nhiệm rõ ràng (SRP), lỏng khớp qua interface (DIP/OCP), chuẩn mực công nghiệp và đào tạo, cực kỳ dễ bảo trì và phân công. | Ít file, code nhanh ở giai đoạn mẫu ban đầu. | Cách ly hoàn toàn domain khỏi mọi framework. |
| *Nhược điểm* | Cần tuân thủ phân tách interface và class hiện thực. | Vi phạm SRP, dễ trộn lẫn tầng dữ liệu và nghiệp vụ; Controller dễ nhảy cóc gọi Repository. | Quá cồng kềnh, sinh quá nhiều tầng trung gian, phức tạp hóa không cần thiết cho đồ án tốt nghiệp 2 người. |
| **Hạ tầng kiểm thử DB** | **Docker Replica Set cục bộ + CI Service** | Testcontainers tích hợp trong mã Java | Chạy H2 hoặc In-Memory MongoDB (Fongo) |
| *Ưu điểm* | Sử dụng chính xác MongoDB 8.0 thật; nạp validator `$jsonSchema` thật; kiểm thử đúng 100% môi trường production. | Tự động dựng container khi chạy test mà không cần lệnh Docker ngoài. | Chạy rất nhanh, không cần cài Docker. |
| *Nhược điểm* | Cần khởi động container Docker trước khi chạy test integration cục bộ. | Phụ thuộc Docker daemon socket; chậm thời gian build; thêm thư viện ngoài. | Không hỗ trợ `$jsonSchema` MongoDB 8.0; không có transaction Replica Set thật. |

---

## 4. Hệ Quả & Ràng Buộc (Consequences)

### Tích cực
- Hệ thống đạt độ chuẩn mực cao nhất về kiến trúc phần mềm, cấu trúc mã nguồn rành mạch, dễ giải trình trước hội đồng bảo vệ cả chuyên ngành Công nghệ thông tin (IT) lẫn Kinh doanh số (IBS).
- Các giao dịch cập nhật điểm và ghi nhật ký kiểm toán `audit_logs` được bảo đảm tính nguyên tử (Atomicity), loại bỏ hoàn toàn nguy cơ dữ liệu mồ côi khi có sự cố.
- Frontend và Backend có bản đặc tả hợp đồng API rõ ràng (`API-CONTRACT.md`), giải quyết dứt điểm nợ kỹ thuật TD-12.
- Hạ tầng MongoDB Replica Set giải quyết dứt điểm nợ kỹ thuật TD-04.
- Đạt điểm tối đa theo Rubric đánh giá đồ án tốt nghiệp về kiến trúc phần mềm, chất lượng mã nguồn (TC2.4) và kiểm thử tự động (TC2.5).

### Ràng buộc
- Mọi thành viên phát triển bắt buộc phải tuân thủ phân tầng: Tuyệt đối không được tiêm trực tiếp `Repository` vào `Controller`, không được trả đối tượng `Document` ra ngoài API.
- Các module nghiệp vụ tiếp theo (F01–F08) đều phải duy trì cấu trúc Interface tại root layer và class hiện thực tại `.impl`.
- Mọi API mới phát sinh đều phải được cập nhật vào `D:\TLCN\LMS-UI\doc\API-CONTRACT.md` trước khi tiến hành code.
