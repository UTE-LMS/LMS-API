# Kế hoạch kiểm thử và bảng truy vết AC ↔ test (dự thảo)

> **DỰ THẢO do AI soạn, chờ hai sinh viên đọc, sửa và viết lại bằng lời của mình.** Đối chiếu mã ngày **2026-11-02**,
> nhánh `main` tại commit `54ef80e` (repo `LMS-API`). Tài liệu này **không chứa số coverage, số test pass/fail hay lịch sử
> CI do AI tự đo**: những chỗ cần số được ghi "chưa đo" và chỉ rõ cách đo. Mọi tuyên bố về mã dưới đây được kiểm chứng
> bằng cách đọc `src/test/java`, `src/main/java`, `pom.xml`, `.github/workflows/ci.yml`; **chưa chạy** `./mvnw verify` trong
> phiên soạn tài liệu này. Acceptance criteria (AC) lấy từ `doc/plan/` vốn cũng là **bản nháp chưa ký**.

Liên quan: [`doc/plan/README.md`](plan/README.md) (§7 Definition of Done), [`.claude/rules/04-testing-and-cicd.md`](../.claude/rules/04-testing-and-cicd.md),
[`doc/srs/README.md`](srs/README.md) (mục lục US → AC), [`doc/srs/nfr-de-xuat.md`](srs/nfr-de-xuat.md) (NFR đề xuất),
[`doc/coding-convention.md`](coding-convention.md) §4.

## 1. Mục tiêu và phạm vi

- Mục tiêu: chứng minh các chức năng **đã có mã** đáp ứng AC; liệt kê rõ AC **chưa có test** (khoảng trống) để sinh viên
  xử lý; có sẵn khung kiểm thử cho các feature chưa có mã.
- Trong phạm vi hiện có mã: nền tảng chung (lỗi, audit, transaction, health, correlationId), **F01** (xác thực, người dùng,
  phân quyền), **F02** (chương trình, phiên bản, PLO, PI, ma trận I/R/M, xác nhận nội dung).
- Chưa có mã (ghi nhận, **không bịa test**): F03 (học phần, CLO), F04 (bài đánh giá), F05a (học kỳ, lớp, sinh viên), F05b
  (nhập điểm), F06 (đo lường), F07 (báo cáo), F08 (cải tiến) và UI. Xem mục 8.5.
- Ngoài phạm vi kiểm thử: nội dung đề thi, bài làm của sinh viên, ML (đều ngoài phạm vi đề tài, `CLAUDE.md` quy tắc 1).

## 2. Chỉ tiêu cần đạt (lấy từ rule 04; là **mục tiêu**, chưa phải kết quả)

| Chỉ số | Mục tiêu (Mức 5) | Số đo hiện có |
|---|---|---|
| Số tầng kiểm thử | ≥ 4 trong 5 tầng (mục 3) | Có 3 tầng ở mức đầu (unit, integration, API); E2E và phi chức năng **chưa có** |
| Coverage dòng lệnh mô-đun lõi | ≥ 70%, có báo cáo | **Chưa đo trong tài liệu này.** JaCoCo đã cấu hình (mục 4) nhưng báo cáo chưa được công bố |
| Tỷ lệ test tự động hóa | ≥ 70% | Chưa có danh sách test thủ công để so, nên chưa tính được |
| Test pass ở lần chạy CI cuối | 100% | Xem mục 6.3 (chỉ là kết luận tổng của workflow) |
| Defect Critical/Blocker còn tồn | 0 | Chưa có danh sách defect (mục 7) |
| Chặng pipeline / build xanh / deploy / commit→staging | ≥ 6 / ≥ 90% / ≥ 10 lần / ≤ 15 phút | Chưa đo; pipeline mới có chặng build + test (mục 4) |

## 3. Chiến lược 5 tầng

| Tầng | Mục đích | Công cụ | Hiện trạng trong repo `LMS-API` (đã đọc mã) | Kế hoạch |
|---|---|---|---|---|
| 1. Unit | Kiểm một lớp/hàm, mock phụ thuộc: service, mapper, validator, util, filter, handler lỗi | JUnit 5, Mockito (qua các starter `*-test` trong `pom.xml`) | **Có.** Các lớp dùng `MockitoExtension` (ví dụ `UserServiceImplTest`, `PloServiceImplTest`, `AuthSessionServiceImplTest`, `ScopeAuthorizationServiceImplTest`); lớp không cần Spring (`UserScopeTest`, `PasswordPolicyValidatorTest`, `GlobalExceptionHandlerTest`, `CorrelationIdFilterTest`) | Giữ; thêm test cho logic đo lường thuần khi F06 có mã, kỳ vọng tính tay độc lập |
| 2. Integration | Kiểm nhiều thành phần với MongoDB thật chạy **replica set** (cần transaction) | `@SpringBootTest` + MongoDB 8.0 `rs0` chạy bằng Docker (`docker/mongo`) hoặc service container trong CI (ADR 0002 chọn cách này thay Testcontainers) | **Có.** `TransactionRollbackIntegrationTest` (rollback/commit nghiệp vụ + `audit_logs`), `UserRepositoryCustomConcurrencyChallengerTest` ($inc `tokenVersion` đồng thời) | Thêm khi F05b có mã: xác nhận nhập điểm trong transaction, chống ghi đè đồng thời (`revision`) |
| 3. API | Kiểm hợp đồng HTTP: mã trạng thái, JSON lỗi, phân quyền, validation | `MockMvc` dựng từ `WebApplicationContext` + `springSecurity()` (Spring Security Test), chạy trên MongoDB thật | **Có.** `AuthIntegrationTest`, `UserIntegrationTest`, `ProgramIntegrationTest`, `ScopeAuthorization*Test`, `ActuatorHealthTest`… (15 lớp dùng `@SpringBootTest`) | Giữ; bổ sung các khoảng trống ở mục 8.6 |
| 4. E2E / UI | Kiểm luồng người dùng qua giao diện thật | UI: Vitest (unit/component); E2E: Playwright (rule 04) | **Chưa có** ở repo API. Trạng thái của repo `LMS-UI` do repo đó ghi | **Kế hoạch:** Playwright cho các luồng đăng nhập, quản lý CTĐT/PLO/PI, nhập điểm; chạy trên staging khi có môi trường |
| 5. Phi chức năng | Hiệu năng, bảo mật cơ bản, độ tin cậy theo NFR định lượng | Hiệu năng: k6 hoặc JMeter; bảo mật: OWASP Dependency-Check, Trivy, gitleaks, kiểm tra OWASP Top 10 thủ công/ZAP | **Chưa có** test hiệu năng. Có một số test bảo mật dạng chức năng (chống timing attack, thu hồi token, từ chối 401/403 — xem mục 8) | **Kế hoạch**, gắn với [`doc/srs/nfr-de-xuat.md`](srs/nfr-de-xuat.md); số liệu chờ sinh viên và GVHD chốt |

Ranh giới tầng 2 và 3 trong repo hiện tại: lớp `*IntegrationTest` và `*EmpiricalChallengeTest` đều vừa dùng Spring context vừa
dùng MongoDB thật nên gộp được gọi là "integration/API"; sinh viên có thể gán lại tên tầng khi viết báo cáo.

## 4. Công cụ (đối chiếu `pom.xml` và `.github/workflows/ci.yml`)

| Công cụ | Trạng thái | Bằng chứng |
|---|---|---|
| JUnit 5 + Mockito + Spring Boot Test | **Đã có** | các starter `spring-boot-starter-*-test` trong `pom.xml` |
| Spring Security Test (`springSecurity()` cho MockMvc) | **Đã có** | `spring-boot-starter-security-test` trong `pom.xml`; 11 lớp test gọi `SecurityMockMvcConfigurers.springSecurity()` |
| Maven Surefire (chạy test) | **Đã có**; truyền `app.security.jwt.*` và `INITIAL_ADMIN_*` rỗng cho test | `pom.xml` |
| JaCoCo 0.8.15 (đo coverage) | **Đã cấu hình**: `prepare-agent`, `report` (pha `test`), `check` (pha `verify`) với quy tắc ≥ 70% dòng lệnh cho gói `service/impl`, `controller`, `filter`. **Lưu ý:** `haltOnFailure=false` nên vi phạm ngưỡng **không làm hỏng build** | `pom.xml` |
| Báo cáo coverage công bố | **Chưa có**: workflow không tải artifact/báo cáo JaCoCo lên | `.github/workflows/ci.yml` |
| MongoDB 8.0 replica set `rs0` cho test | **Đã có**: CI khởi động container `mongo:8.0` và nạp `doc/database_import.js`; dev dùng image `docker/mongo` | `ci.yml`, `docker/mongo/Dockerfile` |
| GitHub Actions | **Đã có** workflow `CI`, job `build-test` (checkout, Java 25, khởi động MongoDB, `./mvnw -B verify`); chạy khi `push` và `pull_request` | `ci.yml` |
| Linter/phân tích tĩnh (Checkstyle/Spotless, Sonar, SpotBugs) | **Kế hoạch** (TD-07 trong `doc/tech-debt.md`); kết quả ghi ở `doc/quality/README.md` | — |
| Quét secret, quét phụ thuộc, quét image (gitleaks, OWASP Dependency-Check, Trivy) | **Kế hoạch** (TD-09) | — |
| Testcontainers | **Không dự định** (ADR 0002 chọn MongoDB qua Docker/CI service) | `doc/adr/0002-…` |
| Playwright (E2E), k6/JMeter (hiệu năng) | **Kế hoạch** | — |

Cách chạy cục bộ (theo `README.md`): bật MongoDB replica set bằng image `docker/mongo`, rồi `./mvnw verify`. Lưu ý
`src/test/resources/application.properties` mặc định trỏ cổng **27018** (ghi đè được bằng biến `MONGODB_URI`), khác cổng
27017 mặc định của ứng dụng; CI đặt `MONGODB_URI` rõ ràng. Báo cáo JaCoCo sinh ra ở `target/site/jacoco/` (đường dẫn mặc
định của plugin; **chưa kiểm chứng trong phiên này vì chưa chạy build**).

## 5. Dữ liệu test

- Dữ liệu giả, **không dùng dữ liệu thật** của sinh viên/điểm/giảng viên (rule 02, 04). Fixture tạo bằng mã trong
  `@BeforeEach`, dọn trong `@AfterEach`; test tích hợp dùng hậu tố ngẫu nhiên để tránh đụng nhau (ví dụ `SUFFIX` trong
  `ProgramIntegrationTest`). `TransactionRollbackIntegrationTest` gắn nhãn `"DỮ LIỆU THỬ NGHIỆM"` vào dữ liệu.
- Test tích hợp ghi vào **cùng CSDL `learning_outcomes`** mà dev dùng (không có CSDL riêng cho test). Rủi ro lẫn dữ liệu;
  `src/test/resources/application.properties` ghi chú đã cô lập `INITIAL_ADMIN_*` để seeder không tạo admin thật.
- Số dùng trong tài liệu nghiệp vụ (40/60, 60%, 72%, 20/40/40, 45 ghi danh/40 đủ dữ liệu) **chỉ là ví dụ minh họa**; khi viết
  test đo lường đặt tên fixture "ví dụ minh họa", giá trị kỳ vọng tính tay độc lập và ghi cách tính.
- Dữ liệu khởi tạo của `database_import.js` (7 PLO, 17 PI, quy tắc đo lường nháp) là dữ liệu nạp mặc định; test API không
  được phụ thuộc vào việc nó đã có (plan F02 §9).
- Ca âm và ca biên bắt buộc cho miền này (rule 04): điểm > điểm tối đa; `BLANK` ≠ `MISSING`/`ABSENT`; trùng
  `(sectionId, assessmentId, itemId, studentId)`; tổng `weight` ≠ 1; cặp CLO–PI ngoài `clo_pi_links`; tệp trống/sai
  cột/trùng checksum; quy tắc `DRAFT` không chạy chính thức; lớp 45 ghi danh/40 đủ dữ liệu; người dùng ngoài phạm vi lớp
  bị từ chối; sửa điểm đồng thời báo xung đột. **Tất cả chưa có mã để kiểm** (F04–F06) — liệt kê để không bị quên.

## 6. Tiêu chí vào / ra

### 6.1 Tiêu chí vào (bắt đầu kiểm thử một story)
1. AC của story đã viết và người còn lại đã đọc (plan README §7 mục 1).
2. Mã build được (`./mvnw -B verify` không lỗi biên dịch); MongoDB replica set sẵn sàng.
3. Dữ liệu test và biến môi trường cần thiết có sẵn (mục 5).

### 6.2 Tiêu chí ra (đóng một story / một PR)
1. Mỗi AC có ít nhất một test gắn mã AC; có ca thuận, ca âm, ca biên.
2. `./mvnw verify` xanh; báo đúng output (số test pass/fail/skip). Không `@Disabled`, không nới assertion.
3. Báo cáo coverage mô-đun lõi có thể xem được và ≥ 70% **khi đã đo** (chưa đo thì ghi "chưa đo").
4. 0 defect Critical/Blocker mở liên quan story.
5. Bảng truy vết (mục 8) cập nhật cùng PR.

### 6.3 Trạng thái lần chạy gần nhất (đọc từ GitHub, không phải do AI chạy)
`gh run list -R UTE-LMS/LMS-API` ngày 2026-11-02 cho thấy workflow `CI` trên `main` (sự kiện `push`, run
`41394140776`, tạo lúc `2026-11-02T05:27:32Z`) có `conclusion = success`. Đây chỉ là kết luận tổng của workflow; **không** kèm
số test hay coverage. Cũng thấy ít nhất một lần `failure` trước đó trên nhánh `feat/f02-ctdt-plo-pi` (2026-10-29). Sinh viên
nên đọc lại log run cụ thể trước khi trích dẫn bất kỳ số liệu nào.

Thống kê tĩnh (đếm bằng `grep`, **không phải kết quả chạy**): 44 lớp test, khoảng 406 phương thức `@Test`/`@ParameterizedTest`
(mỗi `@ParameterizedTest` nở thành nhiều ca khi chạy); 15 lớp dùng `@SpringBootTest`, 18 lớp dùng `MockitoExtension`.
Không thấy `@Disabled` trong `src/test`.

## 7. Quản lý lỗi (defect)

- **Mỗi lỗi là một issue GitHub** (`UTE-LMS/LMS-API` hoặc `UTE-LMS/LMS-UI` tùy nơi lỗi), nhãn mức độ:
  - **Critical**: sai nghiệp vụ lõi (tính sai kết quả, nhập điểm sai, phân quyền hở, mất/lộ dữ liệu) hoặc hệ thống không dùng được.
  - **Major**: chức năng cam kết không chạy đúng AC nhưng có cách tránh, hoặc lỗi bảo mật mức trung bình.
  - **Minor**: lỗi giao diện/thông báo/định dạng, không ảnh hưởng kết quả nghiệp vụ.
- Nội dung issue: bước tái hiện, kết quả thực tế, kết quả mong đợi, môi trường (commit, nhánh), AC liên quan, trạng thái
  (Mở → Đang sửa → Đã sửa chờ kiểm lại → Đóng).
- **Mỗi lỗi sửa kèm một regression test** gắn mã AC (hoặc mã issue) và commit tham chiếu issue (`Fixes #n` hoặc `Refs #n`).
  Không đóng issue Critical khi chưa có test chứng minh.
- Danh sách defect khi nộp hồ sơ lấy từ issue tracker (Rubric mục 8 #8). **Hiện chưa có danh sách defect nào được
  tổng hợp trong tài liệu này.** Lỗi/ảo giác của chính AI ghi riêng ở `logsAI/ai-usage-log.md`.

## 8. Bảng truy vết AC ↔ test

### 8.1 Cách đọc

- Cột "Test" ghi `Lớp#phương thức` trong `src/test/java/com/hcmute/lms/lmsapi/…`. Nhiều phương thức nằm trong lớp `@Nested`
  của lớp ngoài; tên lớp ngoài là đủ để tìm.
- Trạng thái: **Có** = có test kiểm đúng ý AC; **Một phần** = có test nhưng thiếu một vế của AC (ghi rõ vế nào);
  **Mã thử nghiệm** = test chạy trên controller/service dành riêng cho test, chưa có endpoint thật; **Khoảng trống** = không
  thấy test nào.
- Việc "test kiểm đúng ý AC" là đánh giá của người soạn sau khi đọc tên và nội dung test, **không phải** kết quả chạy.
- **Quy ước gắn mã AC** (rule 04: `@DisplayName("US-xx/AC-y: …")`) mới chỉ áp dụng ở `GlobalExceptionHandlerTest` và
  `TransactionRollbackIntegrationTest`. Các test còn lại **chưa gắn mã AC trong tên**; bảng dưới do người soạn ghép tay
  nên có thể sai. Việc gắn lại `@DisplayName` thuộc về sinh viên (bảng này không sửa mã).

### 8.2 Nền tảng chung (`doc/plan/00-nen-tang-chung.md`)

| AC | Test | Trạng thái | Ghi chú |
|---|---|---|---|
| US-40/AC-1 (400, `VALIDATION_ERROR`, `fieldErrors`) | `GlobalExceptionHandlerTest#shouldHandleMethodArgumentNotValid`, `#shouldHandleMalformedJsonBody`, `#shouldHandleInvalidObjectIdPathVariable`; `ErrorCodeAndResponseChallengerTest#testErrorResponseSerializationWithPopulatedFieldErrors`; `UserIntegrationTest#pageNegative_Returns400` | Có | Cả mức handler (unit) và HTTP thật |
| US-40/AC-2 (422, `BUSINESS_RULE_VIOLATION`) | `GlobalExceptionHandlerTest#shouldHandleBusinessRuleViolationException`, `#shouldHandleMongoWriteExceptionCode121`; `UserIntegrationTest#createUser_NonExistentScopeId_Returns422` | Có | Ví dụ "tổng `weight` ≠ 1" chưa có mã (F04) |
| US-40/AC-3 (409, `CONFLICT`) | `GlobalExceptionHandlerTest#shouldHandleDuplicateKeyException`, `#shouldHandleOptimisticLockingFailure`; `UserIntegrationTest#createUser_DuplicateUsername_Returns409`; `ProgramIntegrationTest#ploPiAndContributionFlow` (PLO trùng mã) | Có | Ví dụ AC dùng `plos(programVersionId, code)`: đúng ví dụ này được kiểm ở `ProgramIntegrationTest` |
| US-40/AC-4 (500, không lộ stack trace) | `GlobalExceptionHandlerTest#shouldHandleNullPointerExceptionSecurely`, `#shouldHandleRuntimeExceptionSecurely`, `#shouldFallbackDataIntegrityUnknownTo500` | Một phần | Thiếu: khẳng định "log phía server có chi tiết"; chưa có test 500 đi qua MockMvc |
| US-39/AC-1 (`/actuator/health`: `UP` khi Mongo nối được, `DOWN` khi không) | `ActuatorHealthTest#healthEndpointShouldBePermittedWithoutAuthentication` | Một phần | Assertion `anyOf(UP, DOWN)` nên **chưa chứng minh** cả hai vế; không có test ngắt Mongo để thấy `DOWN` |
| US-39/AC-2 (rollback cùng `audit_logs`) | `TransactionRollbackIntegrationTest#testTransactionalRollbackWhenExceptionThrown`, `#testTransactionalCommitWhenSuccessful` | Mã thử nghiệm | Dùng `@TestComponent TransactionalBusinessTestService` và collection `terms`; chưa có nghiệp vụ thật dùng `@Transactional` + audit |
| _(không có AC)_ correlationId / MDC, mục 3.4 của plan 00 | `CorrelationIdFilterTest` (4 ca) | Có test, thiếu AC | Nên thêm AC `US-39/AC-3` cho `X-Correlation-Id` |
| _(không có AC)_ che dữ liệu nhạy cảm trong audit, plan 00 §3.3 | `AuditLogMapperUnitTest`, `AuditServiceUnitTest` (che `password`, `token`…, bắt buộc `reason` với `SCORE_UPDATE`) | Có test, thiếu AC | Nên thêm AC cho `AuditService` |

### 8.3 F01 — Người dùng và phân quyền (`doc/plan/F01-nguoi-dung-phan-quyen.md`)

| AC | Test | Trạng thái | Ghi chú |
|---|---|---|---|
| US-01/AC-1 (đăng nhập → JWT, audit `LOGIN`) | `AuthIntegrationTest#login_Success`; `TimingAttackDefenseTest#login_Success_GeneratesJwtAndRecordsAudit` | Có | |
| US-01/AC-2 (sai mật khẩu/không tồn tại → 401 chung; hash giả) | `AuthIntegrationTest#login_NonExistentUser_Returns401`, `#login_WrongPassword_Returns401`; `TimingAttackDefenseTest#login_NonExistentUser_MustCallPasswordEncoderWithDummyHash`; `AuthTimingAndRevocationEmpiricalChallengeTest#challengeTimingAttack_ResponseParityAcrossAllFailureModes` | Có | `challengeTimingAttack_NonExistentUser_ExecutesDummyHash` kiểm độ trễ thực (> 15 ms): phụ thuộc thời gian, có nguy cơ không ổn định (mục 9) |
| US-01/AC-3 (tài khoản `LOCKED` → 401 chung) | `AuthIntegrationTest#login_LockedUser_Returns401WithGenericMessage`; `TimingAttackDefenseTest#login_LockedUser_ThrowsBadCredentialsWithGenericMessage`; `AuthTimingAndRevocationEmpiricalChallengeTest#challengeLockedAccount_CannotLogin` | Có | |
| US-01/AC-4 (`passwordHash` không lộ ở phản hồi, log) | `UserIntegrationTest#createUser_Success`; `UserSecurityAndIntegrityEmpiricalChallengeTest` (cập nhật người dùng); `AuditLogMapperUnitTest`, `AuditServiceUnitTest` (che trong audit) | Một phần | Có kiểm tạo/sửa người dùng; **chưa thấy** kiểm `LoginResponse`, `GET /auth/me`, `GET /users`, hay dòng log |
| US-01/AC-5 (logout/khóa → tăng `tokenVersion`, token cũ 401 vĩnh viễn) | `AuthIntegrationTest#logout_Success_IncrementsTokenVersion_AndInvalidatesToken`; `UserIntegrationTest#lockAccount_InvalidatesTokenImmediately_AndPermanently`; `AuthTimingAndRevocationEmpiricalChallengeTest#challengeLogout_*`, `#challengeLockedAccount_LockAndUnlock_TokensIssuedBeforeLockPermanentlyRevoked`; `UserRepositoryCustomConcurrencyChallengerTest`; `AuthSessionServiceImplTest#validateUserSession_TokenVersionMismatch_ThrowsException` | Có | `challengeLogout_ConcurrentLogoutStress` và test 100 luồng đồng thời: cần theo dõi độ ổn định (mục 9) |
| US-01/AC-6 (`BearerTokenResolver` bỏ qua endpoint permitAll) | `BearerTokenResolverAndSecurityChallengerTest` (nhóm 1–3); `AuthIntegrationTest#login_WithExpiredOrInvalidBearerTokenInHeader_ShouldSucceed`, `#permitAllEndpoints_AccessibleWithoutToken`, `#protectedActuatorEndpoint_RequiresAuthentication`; `ActuatorHealthTest#beansEndpointMustRequireAuthentication` | Có | |
| US-02/AC-1 (ADMIN tạo user, ≥ 1 `roleAssignment`, BCrypt, 8–72 byte, chuẩn hóa) | `UserIntegrationTest#createUser_Success`; `UserServiceImplTest#shouldCreateUserSuccessfully`; `PasswordPolicyValidatorTest`, `PasswordPolicyValidatorEmpiricalChallengeTest`; `StringNormalizationUtilsTest`; `AuthIntegrationTest#login_PasswordShorterThan8Bytes_Returns400`, `#login_PasswordExceeding72Bytes_Returns400`, `#login_VietnameseAccentedPassword_Success` | Một phần | Thấy ràng buộc `@NotEmpty` cho `roleAssignments` trong `CreateUserRequest` nhưng **chưa thấy test** tạo user với danh sách vai trò rỗng → 400 |
| US-02/AC-2 (trùng username/email → 409) | `UserIntegrationTest#createUser_DuplicateUsername_Returns409`, `#createUser_DuplicateEmail_Returns409`; `UserServiceImplTest#shouldRejectDuplicateUsername`, `#shouldRejectDuplicateEmail` | Có | |
| US-02/AC-3 (scope tham chiếu không tồn tại → 422) | `UserIntegrationTest#createUser_NonExistentScopeId_Returns422`; `UserSecurityAndIntegrityEmpiricalChallengeTest` (nhóm 2: program/course/section, tạo và sửa); `ScopeValidationServiceImplTest#shouldThrowWhen*` | Có | |
| US-02/AC-4 (không phải ADMIN → 403) | `UserIntegrationTest#nonAdmin_GetUsers_Returns403`, `#nonAdmin_CreateUser_Returns403`, `#nonAdmin_UpdateUser_Returns403`, `#nonAdmin_UpdateStatus_Returns403`, `#unauthenticated_Returns401` | Có | Thiếu `GET /users/{id}` ở nhóm này |
| US-02/AC-5 (chặn tự khóa/tự hạ quyền/ADMIN cuối → 422) | `UserIntegrationTest#adminSelfLock_Returns422`, `#adminSelfDemotion_Returns422`; `AdminGuardsAndPaginationEmpiricalChallengeTest` (nhóm 1); `AdminGuardsEmpiricalChallengeTest` (Guard 1, 2, 3A, 3B); `UserServiceImplTest#shouldRejectLockingLastActiveAdmin`, `#shouldRejectDemotingLastActiveAdmin` | Có | Test tuần tự theo thiết kế; rủi ro đồng thời được chấp nhận (ADR 0003, TD-13) |
| US-02/AC-6 (phân trang, 400 khi sai biên) | `UserIntegrationTest#pageNegative_Returns400`, `#sizeZero_Returns400`, `#sizeExceeding100_Returns400`, `#validPagination_Returns200WithEnvelope`; `AdminGuardsAndPaginationEmpiricalChallengeTest` (nhóm 2); `PageableValidationUtilsTest` | Có | |
| US-03/AC-1 (LECTURER chỉ thao tác lớp phụ trách, lớp khác 403) | `ScopeAuthorizationMatrixIntegrationTest#lecturer_ScopedSection_Success`, `#lecturer_Unassigned_Forbidden`; `ScopeAuthorizationServiceImplTest#lecturer_*` | Mã thử nghiệm | Chạy qua `ScopeAuthorizationTestController` (`/api/v1/test/sections/**`, chỉ có trong `src/test`). `canAccessSection` **chưa được endpoint thật nào gọi** (xác minh bằng `grep` trong `src/main`) |
| US-03/AC-2 (mọi endpoint trừ permitAll → 401 khi chưa xác thực) | `AuthIntegrationTest#me_ForgedToken_Returns401`; `ScopeAuthorizationMatrixIntegrationTest#noToken_Returns401`; `UserIntegrationTest#unauthenticated_Returns401`; `ProgramIntegrationTest#programAndVersionFlow` (đoạn `GET /programs` không token); `ActuatorHealthTest#arbitraryApiEndpointMustRequireAuthentication`; `BearerTokenResolverAndSecurityChallengerTest` (nhóm 4) | Một phần | Kiểm theo mẫu từng nhóm endpoint; **không có** test duyệt toàn bộ endpoint để chứng minh "mọi endpoint" |
| US-03/AC-3 (ma trận vai trò × phạm vi × READ/WRITE) | `ScopeAuthorizationMatrixIntegrationTest` (nhóm 2); `ScopeAuthorizationServiceImplTest` (25 ca); `ScopeAuthorizationEmpiricalChallengeTest` | Mã thử nghiệm | Như trên. AC viết "test tham số hóa"; thực tế là các phương thức riêng cho từng vai trò |
| US-03/AC-4 (LECTURER `lecturerIds`; VIEWER/PM chỉ `programIds` → 403) | `ScopeAuthorizationMatrixIntegrationTest#lecturer_IndependentLecturerIdsRule_Success`, `#viewer_ProgramOnlyScope_Forbidden`; `ScopeAuthorizationServiceImplTest#lecturer_IndependentLecturerIdsRule_Granted`, `#viewer_ProgramOnlyScope_IsNotEmptyScope_Denied`, `#managementRoles_Read_ProgramOnlyScope_Denied`; `UserScopeTest`, `UserScopeEmpiricalChallengeTest` | Mã thử nghiệm | |
| US-03/AC-5 (đổi vai trò/scope trong DB có hiệu lực ngay với token cũ) | `ScopeAuthorizationMatrixIntegrationTest#dynamicPermissionReload_ImmediateEffectWithSameToken`; `ScopeDynamicReloadAndEdgeCasesEmpiricalChallengeTest#*` | Có | Kiểm qua endpoint thử nghiệm `/api/v1/test/sections/**`, nên cùng hạn chế "mã thử nghiệm" như AC-1 |
| US-04/AC-1..5 (API xem nhật ký: phân trang, lọc, ADMIN-only, không lộ secret, 400) | `AuditLogApiIntegrationTest` (15 ca, mã AC trong `@DisplayName`); `AuditLogQueryServiceImplTest` (4 ca); `AuditLogRepositoryCustomUnitTest#testSearchLogs*`; `AuditLogMapperUnitTest#testToResponseSanitizesOnRead` | Có | Chạy được trên MongoDB đơn lẻ (không dùng transaction). Chưa kiểm thử trên `rs0` Docker vì Docker đang tắt lúc làm |
| "ghi audit" ngoài `LOGIN` | — | Khoảng trống | Mã chỉ gọi `AuditService.record` ở đăng nhập; các hành động còn lại thuộc F04/F05b/F06 |
| NEW-03 (sinh viên xem kết quả, MR) | `ScopeAuthorizationMatrixIntegrationTest#student_ReadAndWrite_Forbidden` (chỉ kiểm bị từ chối) | Khoảng trống | Chưa có AC và chưa có mã |

### 8.4 F02 — CTĐT, PLO, PI, ma trận I/R/M (`doc/plan/F02-ctdt-plo-pi.md`)

Lưu ý chung: `ProgramIntegrationTest` chỉ có **3 phương thức lớn** (mỗi phương thức gom nhiều bước) nên một kết luận "pass" không cho biết bước nào hỏng; nên tách theo AC khi gắn mã (mục 9).

| AC | Test | Trạng thái | Ghi chú |
|---|---|---|---|
| US-05/AC-1 (tạo phiên bản mới → `DRAFT`) | `ProgramVersionServiceImplTest#create_success_defaultsDraft` | Một phần | Chỉ unit; `ProgramIntegrationTest` không kiểm `POST /program-versions` thành công |
| US-05/AC-2 (trùng `(programId, version)` → 409) | `ProgramVersionServiceImplTest#create_duplicateVersion_conflict` | Một phần | Chỉ unit, chưa có kiểm qua HTTP |
| US-05/AC-3 (`programId` không tồn tại → 422) | `ProgramIntegrationTest#programAndVersionFlow`; `ProgramVersionServiceImplTest#create_programMissing_businessRule` | Có | |
| US-05/AC-4 (chỉ `DRAFT → ACTIVE → ARCHIVED`, ngược → 422) | `ProgramIntegrationTest#programAndVersionFlow`; `ProgramVersionServiceImplTest#update_draftToActive_success`, `#update_activeToDraft_businessRule`, `#update_skipActive_businessRule` | Một phần | Chưa thấy test `ACTIVE → ARCHIVED` thành công và `ARCHIVED → …` bị chặn |
| (ngoài AC) `programs`: tạo/trùng mã/quyền (`ADMIN` ghi per ADR 0005, PM bị 403, mọi người đọc) | `ProgramIntegrationTest#programAndVersionFlow`; `ProgramServiceImplTest#*` | Có test, thiếu AC | Plan F02 cập nhật per ADR 0005: `ADMIN` tạo 201, PM bị 403 |
| US-06/AC-1 (mã PLO `^PLO[1-9][0-9]*$`; `PLO0`, `plo1`, `PLO01` → 400) | `F02ValidationTest#ploCode_valid`, `#ploCode_invalid` (`PLO0`, `PLO01`, `plo1`, `PLO`, `PLO1A`); `ProgramIntegrationTest#ploPiAndContributionFlow` (`PLO0` → 400, `PLO1` → 201) | Có | |
| US-06/AC-2 (PI có `ploId` ngoài phiên bản → 422) | `PiServiceImplTest#create_ploNotInVersion_businessRule`; `ProgramIntegrationTest#ploPiAndContributionFlow` | Có | |
| US-06/AC-3 (`capabilityLevelRaw` ngoài 1–6 → 400) | `F02ValidationTest#capabilityLevel_valid`, `#capabilityLevel_invalid` | Một phần | Chỉ kiểm ràng buộc trên DTO bằng `Validator`; chưa có kiểm HTTP 400 |
| US-06/AC-4 (danh sách PLO sắp theo `displayOrder`) | — | Khoảng trống | `ProgramIntegrationTest` chỉ khẳng định `hasSize(1)` của PI; không test nào khẳng định thứ tự |
| US-06/AC-5 (không `contentOriginal` vẫn hợp lệ; mặc định không `VERIFIED`) | `PloServiceImplTest#create_success_defaults`; `ProgramIntegrationTest#ploPiAndContributionFlow` (`verification.status = NEEDS_CLARIFICATION`) | Có | Mới kiểm PLO; PI chỉ thấy trong `PiServiceImplTest#create_success` |
| US-07/AC-1 (thêm dòng ma trận I/R/M) | `CoursePiContributionServiceImplTest#create_success`; `ProgramIntegrationTest#ploPiAndContributionFlow` | Có | |
| US-07/AC-2 (trùng → 409; `contribution` khác I/R/M → 400) | `CoursePiContributionServiceImplTest#create_duplicate_conflict`; `ProgramIntegrationTest#ploPiAndContributionFlow`; `F02ValidationTest#contribution_required` | Một phần | Có 409; mới kiểm `contribution` **thiếu**, chưa kiểm giá trị lạ (ví dụ `"X"`) → 400 |
| US-07/AC-3 (`courseId`/`piId` không thuộc `programVersionId` → 422) | `CoursePiContributionServiceImplTest#create_piMissing_businessRule`, `#create_courseMissing_businessRule` | Một phần | Mã chỉ kiểm **PI** thuộc phiên bản; **học phần** chỉ kiểm tồn tại trong `courses`, chưa kiểm thuộc phiên bản (collection `program_courses` chưa có `document`, thuộc F03). Đây là khoảng cách giữa AC và mã |
| US-07/AC-4 (không API nào trả "trọng số" suy từ I/R/M) | — | Khoảng trống | `CoursePiContributionResponse` không có trường trọng số (đã đọc mã) nhưng **không có test hợp đồng DTO** như AC yêu cầu |
| US-08/AC-1 (MR: ghi `verifiedBy`, `verifiedAt`) | `ProgramIntegrationTest#verificationRequiresQaOfficerInProgramScope`; `ProgramVerificationServiceImplTest#verifyPlo_success` | Một phần | Có kiểm `verifiedBy`; chưa thấy kiểm `verifiedAt` và vế "`VERIFIED` cần có người xác nhận" |
| US-08/AC-2 (MR: `QA_OFFICER` xác nhận phiên bản/PLO/PI) | `ProgramIntegrationTest#verificationRequiresQaOfficerInProgramScope`; `ProgramVerificationServiceImplTest#verifyVersion_success`, `#verifyPlo_success`, `#verifyPi_success`, `#verifyVersion_outOfScope_accessDenied`; `ProgramScopeAuthorizationTest#*` | Có | Có cả ca QA ngoài phạm vi → 403 |
| US-08/AC-3 (MR: `PROGRAM_MANAGER`, `ADMIN` → 403; chưa đăng nhập → 401) | `ProgramIntegrationTest#verificationRequiresQaOfficerInProgramScope` | Một phần | Có 403 cho PM, ADMIN, QA ngoài phạm vi trên endpoint của **phiên bản**; **chưa có** ca 401 cho `/verification`, và PLO/PI chỉ kiểm bằng QA hợp lệ |
| (ngoài AC) xóa PLO/PI, sửa khi phiên bản `ACTIVE` | `PloServiceImplTest#update_activeVersion_businessRule`, `#delete_hasPis_businessRule`, `#delete_success`; `PiServiceImplTest#update_activeVersion_businessRule`, `#delete_hasContribution_businessRule`, `#delete_hasQuestionReference_businessRule`, `#delete_success`, `#delete_otherVersion_notFound`; `ProgramIntegrationTest#ploPiAndContributionFlow` (xóa PLO còn PI → 422) | Có test, thiếu AC | Quy tắc "không sửa đè bản đã duyệt" (plan F02 §4 mục 3) và "xóa khi chưa tham chiếu" (§7) nên có AC |
| (ngoài AC) quyền đọc/ghi theo vai trò (`ADMIN` ghi `programs`; `PROGRAM_MANAGER` ghi `program-versions` trong scope) | `ProgramIntegrationTest#programAndVersionFlow` (LECTURER ghi 403; PM ghi `programs` 403; ADMIN ghi `programs` 201; PM ghi `program-versions` ngoài scope 403) | Có | Đã kiểm chứng per ADR 0005 |
| (ngoài AC) `canAccessProgram` fail-closed | `ProgramScopeAuthorizationTest#*` | Có test, thiếu AC | Chuyển giao từ F01 sang F02 (plan F01 §9) nên có AC |

### 8.5 F03–F08: chưa có mã

Các story dưới đây có AC dự thảo trong `doc/plan/` nhưng **chưa có mã nguồn nghiệp vụ** (không có `controller`/`service`/`document`
tương ứng trong `src/main`, trừ `Course` và `CourseSection` chỉ đọc phục vụ F01/F02). Vì vậy **không có test** để truy vết;
bảng dưới chỉ để không bỏ sót.

| Feature | Story | Số dòng AC dự thảo | Trạng thái kiểm thử |
|---|---|---|---|
| F03 Học phần, đề cương, CLO | US-09…12 | 15 | Chưa có mã, chưa có test |
| F04 Bài đánh giá, rubric, duyệt ma trận | US-13…16, NEW-01 | 16 | Chưa có mã, chưa có test (US-17 loại khỏi phạm vi) |
| F05a Học kỳ, lớp, sinh viên | US-18…20 | 11 | Chưa có mã, chưa có test |
| F05b Nhập điểm | US-21…25 | 16 | Chưa có mã, chưa có test |
| F06 Đo lường CLO → PI → PLO | US-26…30 | 15 | Chưa có mã, chưa có test (NEW-04 chưa có dòng AC) |
| F07 Báo cáo, truy vết | US-31…36 | 11 | Chưa có mã, chưa có test |
| F08 Cải tiến | US-37, 38, NEW-02 | 10 | Chưa có mã, chưa có test |

**Ca kiểm thử phân quyền scope F03 (ADR 0009, ĐỀ XUẤT chờ xác nhận; đã có test trên nhánh F03, chờ review/merge):**

| AC dự thảo | Ca dự kiến | Trạng thái |
|---|---|---|
| US-10/AC-4 | `POST /courses/{courseId}/syllabi`, `PUT /syllabi/{id}/status`: PM ngoài scope → 403; PM trong scope → 2xx; học phần chưa thuộc `program_courses` nào → PM 403, ADMIN 2xx | Đã có test (`CloIntegrationTest`) |
| US-11/AC-5 | `POST /syllabi/{syllabusId}/clos`, `PUT`/`DELETE /clos/{id}`: như trên | Đã có test (`CloIntegrationTest`) |
| US-12/AC-6 | `POST /syllabi/{syllabusId}/clo-pi-links` (cần cả `canAccessProgramVersion` và `canAccessSyllabus`), `DELETE /clo-pi-links/{id}`: như trên; `PUT …/verification` vẫn chỉ `QA_OFFICER` đúng scope | Đã có test (`CloIntegrationTest`) |
| (ngoài AC) | Unit test 4 phương thức mới của `ScopeAuthorizationService`: học phần thuộc hai CTĐT chỉ một trong scope → true (phép hợp); tài nguyên không tồn tại → false; vai trò khác → false | Đã có test (`CourseScopeAuthorizationTest`) |

Test đặc biệt quan trọng cần có khi các feature này có mã (nhắc lại từ rule 04, mục 5): điểm > tối đa; `BLANK` ≠ `MISSING`;
trùng bản ghi điểm; tổng `weight` ≠ 1; cặp CLO–PI ngoài `clo_pi_links`; quy tắc `DRAFT` không chạy chính thức; lớp
45/40; sửa điểm đồng thời; người dùng ngoài phạm vi lớp.

### 8.6 Khoảng trống phát hiện được (cần sinh viên xử lý)

| # | Khoảng trống | Loại |
|---|---|---|
| G-01 | US-06/AC-4: không test thứ tự `displayOrder` | AC không có test |
| G-02 | US-07/AC-4: không test hợp đồng DTO "không có trọng số suy từ I/R/M" | AC không có test |
| G-03 | US-05/AC-1, AC-2: chỉ có test unit, chưa có test HTTP; US-05/AC-4: thiếu ca `ACTIVE → ARCHIVED` và `ARCHIVED → …` | Test một phần |
| G-04 | US-06/AC-3: chỉ kiểm DTO, chưa kiểm phản hồi HTTP 400 | Test một phần |
| G-05 | US-07/AC-2: chưa kiểm `contribution` giá trị lạ → 400; US-07/AC-3: mã chưa kiểm học phần thuộc phiên bản (chờ `program_courses`) | Test một phần / lệch AC–mã |
| G-06 | US-08/AC-1, AC-3: thiếu `verifiedAt`, ca 401 cho `/verification`, ca 403 cho PLO/PI | Test một phần |
| G-07 | US-39/AC-1: không có test `DOWN`; assertion `anyOf(UP, DOWN)` quá lỏng | Test một phần |
| G-08 | US-39/AC-2 chỉ được kiểm bằng service thử nghiệm; chưa có nghiệp vụ thật + `@Transactional` + audit | Test trên mã thử nghiệm |
| G-09 | US-40/AC-4: chưa kiểm log phía server; chưa có test 500 qua MockMvc | Test một phần |
| G-10 | US-01/AC-4: chưa kiểm `passwordHash` không lộ ở `LoginResponse`, `/auth/me`, `GET /users`, log | Test một phần |
| G-11 | US-02/AC-1: chưa kiểm `roleAssignments` rỗng → 400 | Test một phần |
| G-12 | US-03: `canAccessSection` chỉ được kiểm qua controller thử nghiệm; AC-2 "mọi endpoint" không có test duyệt toàn bộ | Test trên mã thử nghiệm / một phần |
| G-13 | NEW-03 (hoãn) chưa có mã; chỉ `LOGIN` được ghi audit — các `AuditAction` còn lại chưa có nơi gọi. US-04 (đọc nhật ký) đã có mã và test | Chưa có mã (phần còn lại) |
| G-14 | Quy ước gắn mã AC trong `@DisplayName` mới áp dụng cho 2 lớp test; phần lớn test chưa gắn AC | Quy trình |
| G-15 | Không có tầng E2E, không có test hiệu năng, không có bảng test thủ công/UAT | Thiếu tầng |
| G-16 | Chưa có báo cáo coverage công bố; JaCoCo `check` với `haltOnFailure=false` không chặn build | Công cụ |
| G-17 | Các tài nguyên F02 thiếu test ghi bị 403 cho vai trò không phải `PROGRAM_MANAGER` ngoài `programs` | Test một phần |

## 9. Rủi ro về chất lượng bộ test hiện có (người soạn quan sát, cần sinh viên xác nhận)

- **Test phụ thuộc thời gian thực / đồng thời:** `AuthTimingAndRevocationEmpiricalChallengeTest#challengeTimingAttack_NonExistentUser_ExecutesDummyHash`
  (ngưỡng độ trễ 15 ms), `#challengeLogout_ConcurrentLogoutStress` (10 yêu cầu đồng thời), `UserRepositoryCustomConcurrencyChallengerTest`
  (100 luồng). Rule 04 yêu cầu test không phụ thuộc thời gian thực; cần theo dõi tần suất đỏ ngẫu nhiên trên CI.
- **Test lớn gom nhiều bước:** `ProgramIntegrationTest` (3 phương thức). Khó biết AC nào hỏng; nên tách.
- **Dùng chung CSDL `learning_outcomes`:** chạy test trên máy có dữ liệu dev thật có thể lẫn dữ liệu (mục 5).
- **Tên lớp `*EmpiricalChallengeTest`/`*ChallengerTest`** không nói rõ tầng/AC; nên đổi theo quy ước khi gắn mã AC.
- **JaCoCo không chặn build** (`haltOnFailure=false`): cần sinh viên quyết định có đặt cổng chặn thật hay không; không hạ ngưỡng
  để cho qua (rule 04).

## 10. Kiểm thử thủ công và nghiệm thu người dùng

Chưa có kịch bản kiểm thử thủ công hay UAT. **Kế hoạch:** soạn kịch bản theo AC cho các luồng trên giao diện khi UI có màn
hình tương ứng (đăng nhập; quản lý CTĐT/PLO/PI; nhập điểm; xem báo cáo), ghi kết quả có ngày, người thực hiện, phiên bản.
Thực nghiệm người dùng (SUS, tỷ lệ hoàn thành) thuộc `doc/thuc-nghiem-nguoi-dung/`, do người thật thực hiện; AI không tạo
kết quả (rule 05).

## 11. Việc cần sinh viên quyết định

1. Chốt/bác bỏ các khoảng trống G-01…G-17 và giao người xử lý (cột "Phụ trách" chưa điền).
2. Gắn mã AC vào `@DisplayName` của test hiện có (nên một PR riêng, chỉ đổi tên hiển thị, không đổi logic).
3. Chọn cách công bố báo cáo coverage (artifact của CI hoặc `doc/quality/`) và có để JaCoCo chặn build hay không.
4. Chốt các NFR trong [`doc/srs/nfr-de-xuat.md`](srs/nfr-de-xuat.md) để làm căn cứ test phi chức năng.
5. Quyết định cách xử lý các test nhạy thời gian ở mục 9.
