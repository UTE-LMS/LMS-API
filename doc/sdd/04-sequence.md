# Sơ đồ tuần tự (sequence) của các luồng đã có mã

> **Dự thảo do AI soạn, đối chiếu mã ngày 2026-11-02 (`main`, commit `54ef80e`).** Mỗi sơ đồ dưới đây được viết sau khi đọc
> đúng các lớp nêu ở mục "Căn cứ"; tên lớp, phương thức, mã lỗi là tên thật trong mã. Sinh viên đọc lại từng bước với mã
> (hội đồng sẽ đối chiếu ngẫu nhiên). Luồng chưa có mã nằm ở mục 9, không vẽ.

Quy ước lỗi (từ `ErrorCode`, `GlobalExceptionHandler`): 400 `VALIDATION_ERROR`, 401 `UNAUTHORIZED`, 403 `FORBIDDEN`,
404 `NOT_FOUND`, 409 `CONFLICT`, 422 `BUSINESS_RULE_VIOLATION`, 500 `INTERNAL_ERROR`; thân lỗi là `ErrorResponse`.

## 1. Đăng nhập, cấp JWT và ghi audit `LOGIN` (US-01)

Căn cứ: `AuthController.login`, `AuthServiceImpl.login`, `AuditServiceImpl.record`, `AuditLogMapperImpl`, `JwtConfig`.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant F as CorrelationIdFilter
    participant S as Spring Security<br/>(SecurityFilterChain)
    participant AC as AuthController
    participant SVC as AuthServiceImpl
    participant UR as UserRepository
    participant PE as PasswordEncoder (BCrypt)
    participant JE as JwtEncoder (HS256)
    participant AU as AuditServiceImpl
    participant AM as AuditLogMapperImpl
    participant AR as AuditLogRepository
    participant DB as MongoDB

    C->>F: POST /api/v1/auth/login {username, password}
    F->>S: gắn X-Correlation-Id vào MDC và header phản hồi
    S->>AC: permitAll, BearerTokenResolver trả null nên bỏ qua token đính kèm
    AC->>AC: @Valid LoginRequest (sai/thiếu trường: 400)
    AC->>SVC: login(request, httpRequest)
    SVC->>UR: findByUsername(trim + lowercase)
    UR->>DB: query users theo username
    alt username không tồn tại
        SVC->>PE: matches(password, DUMMY_HASH)
        SVC-->>C: BadCredentialsException, 401 thông báo chung
    else tồn tại
        SVC->>PE: matches(password, passwordHash)
        alt sai mật khẩu hoặc status khác ACTIVE
            SVC-->>C: BadCredentialsException, 401 cùng thông báo chung
        else hợp lệ
            SVC->>JE: encode(claims: iss, sub=userId, iat, exp, tokenVersion)
            JE-->>SVC: accessToken
            SVC->>AU: record(LOGIN, entity users/userId, userId, ip)
            AU->>AM: toDocument(...) làm sạch before/after
            AU->>AR: save(AuditLog)
            AR->>DB: insert audit_logs
            SVC-->>AC: LoginResponse(accessToken, Bearer, expiresIn, user)
            AC-->>C: 200 OK
        end
    end
```

Điểm cần nhớ khi vấn đáp:
- Khi username không tồn tại vẫn gọi `matches` với hash giả để thời gian xử lý gần như nhau (chống dò tài khoản).
- Kiểm tra mật khẩu **trước** kiểm tra trạng thái; ba trường hợp lỗi cùng trả 401 với cùng thông điệp.
- `AuthServiceImpl.login` **không** có `@Transactional`; `AuditServiceImpl.record` có `@Transactional(REQUIRED)` nên bước ghi audit tự mở một
  transaction riêng (xem mục 5).
- Token chứa `sub` và `tokenVersion`, **không** chứa vai trò (vai trò nạp từ CSDL ở mỗi request, mục 2).

## 2. Mỗi request có token: kiểm tra JWT, nạp quyền, kiểm tra vai trò (US-01/AC-5, US-03)

Căn cứ: `SecurityConfig` (`bearerTokenResolver`, `jwtAuthenticationConverter`, `authorizeHttpRequests`),
`AuthSessionServiceImpl.validateUserSession`, `UserPrincipal.getAuthorities`, `@PreAuthorize` trong controller.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant F as CorrelationIdFilter
    participant B as BearerTokenAuthenticationFilter
    participant D as JwtDecoder (Nimbus, HS256)
    participant J as jwtAuthenticationConverter
    participant SS as AuthSessionServiceImpl
    participant UR as UserRepository
    participant Z as AuthorizationFilter + @PreAuthorize
    participant CT as Controller
    participant SV as Service

    C->>F: request + Authorization: Bearer token
    F->>B: tiếp tục chuỗi lọc
    B->>D: giải mã, kiểm tra chữ ký và hạn
    alt token sai chữ ký hoặc hết hạn
        D-->>C: 401 (AuthenticationEntryPoint, ErrorResponse UNAUTHORIZED)
    end
    D->>J: Jwt hợp lệ
    J->>SS: validateUserSession(jwt)
    SS->>UR: findById(sub)
    alt không có user, status khác ACTIVE, hoặc tokenVersion trong token khác trong CSDL
        SS-->>C: InvalidBearerTokenException, 401
    else hợp lệ
        SS-->>J: UserPrincipal (authorities ROLE_x cho mỗi roleAssignment)
    end
    J->>Z: Authentication(UserPrincipal)
    Z->>Z: /api/v1/users/** cần ADMIN, còn lại cần đăng nhập, rồi @PreAuthorize hasRole(...)
    alt không đủ vai trò
        Z-->>C: 403 (ErrorResponse FORBIDDEN)
    else đủ vai trò
        Z->>CT: gọi phương thức controller
        CT->>SV: gọi service bằng DTO
        SV-->>C: kết quả (qua DTO)
    end
```

Điểm cần nhớ:
- Mỗi request đọc lại `users` từ CSDL (không tin vai trò trong token) nên đổi vai trò/khóa tài khoản có hiệu lực ngay, và
  `logout`/khóa tăng `tokenVersion` làm mọi token cũ bị 401.
- Kiểm tra **phạm vi dữ liệu** (scope) không nằm ở bước này mà ở service khi cần (mục 4 là ví dụ duy nhất hiện có).
- F03 (ADR 0010 thay thế ADR 0009): Admin quản lý Master Catalog `courses`; Trưởng bộ môn (`DEPARTMENT_HEAD`) nhận scope `courseIds` từ Admin và phân công cho Giảng viên (`LECTURER`) trong bộ môn biên soạn đề cương/CLO ở trạng thái `DRAFT`; Trưởng bộ môn thẩm định và duyệt `CONFIRMED`. Trưởng ngành (`PROGRAM_MANAGER`) quản lý đưa môn vào CTĐT (`program_courses`) và tạo liên kết $CLO \to PI$ (`clo_pi_links`) theo `scope.programIds`. Các kiểm tra scope đều thực hiện fail-closed qua `ScopeAuthorizationService`.

## 3. Tạo phiên bản CTĐT, PLO, PI (US-05, US-06)

Căn cứ: `ProgramVersionController`, `ProgramVersionServiceImpl.createProgramVersion`, `PloController`, `PloServiceImpl.createPlo`,
`PiServiceImpl.createPi`. (Tạo `program` ở `ProgramServiceImpl.createProgram` do `ADMIN` thực hiện theo ADR 0005: kiểm tra mã trùng rồi lưu.)

```mermaid
sequenceDiagram
    autonumber
    actor PM as PROGRAM_MANAGER
    participant CT as Controller<br/>(ProgramVersion / Plo / Pi)
    participant PV as ProgramVersionServiceImpl
    participant PL as PloServiceImpl / PiServiceImpl
    participant RP as ProgramRepository
    participant RV as ProgramVersionRepository
    participant RL as PloRepository
    participant RI as PiRepository
    participant MP as Mapper
    participant DB as MongoDB

    Note over PM,CT: Đã qua mục 2: JWT hợp lệ và @PreAuthorize hasRole('PROGRAM_MANAGER') kèm kiểm tra phạm vi chương trình (@scopeAuthorizationService.canAccessProgram / canAccessProgramVersion per ADR 0005)

    rect rgb(235, 245, 255)
    Note over PM,DB: A. POST /api/v1/program-versions
    PM->>CT: {programId, version, ...}
    CT->>CT: @Valid ProgramVersionRequest (400 nếu sai)
    CT->>PV: createProgramVersion(request)
    PV->>RP: existsById(programId)
    alt chương trình không tồn tại
        PV-->>PM: 422 BUSINESS_RULE_VIOLATION
    end
    PV->>RV: existsByProgramIdAndVersion
    alt đã tồn tại
        PV-->>PM: 409 CONFLICT
    end
    PV->>RV: save(status DRAFT, verification NEEDS_CLARIFICATION)
    RV->>DB: insert program_versions (chỉ mục duy nhất programId+version)
    PV->>MP: toResponse
    PV-->>PM: 201 Created
    end

    rect rgb(240, 255, 240)
    Note over PM,DB: B. POST /api/v1/program-versions/{pvId}/plos (PI tương tự, thêm bước kiểm PLO)
    PM->>CT: {code, displayOrder, ...}
    CT->>CT: @Pattern pvId 24 hex, @Valid PloRequest (code theo mẫu PLO...)
    CT->>PL: createPlo(pvId, request)
    PL->>RV: findById(pvId)
    alt không có phiên bản
        PL-->>PM: 404 NOT_FOUND
    else status khác DRAFT
        PL-->>PM: 422 PROGRAM_VERSION_LOCKED
    end
    Note over PL,RL: Với PI: kiểm ploId thuộc cùng pvId (PiServiceImpl.requirePloInVersion), sai thì 422 PI_PLO_MISMATCH
    PL->>RL: existsByProgramVersionIdAndCode
    alt trùng mã
        PL-->>PM: 409 CONFLICT
    end
    PL->>RL: save(verification NEEDS_CLARIFICATION, category mặc định UNSPECIFIED)
    RL->>DB: insert plos (chỉ mục duy nhất programVersionId+code)
    PL->>MP: toResponse
    PL-->>PM: 201 Created
    end
```

Điểm cần nhớ:
- Cả ba thao tác **không** có `@Transactional` (mỗi lần chỉ ghi một document) và **không** ghi `audit_logs`
  (`AuditAction` hiện chỉ có `SCORE_UPDATE`, `IMPORT_CONFIRM`, `ASSESSMENT_APPROVE`, `ASSESSMENT_REJECT`, `RUN_CALCULATION`,
  `RULE_CONFIRM`, `LOGIN`; chưa có hành động cho CTĐT/PLO/PI). Đã kiểm bằng `grep auditService` trong `src/main`: chỉ `AuthServiceImpl` gọi.
- Trùng khóa vừa được kiểm trước khi ghi (`exists…`), vừa được chặn bởi chỉ mục duy nhất; nếu hai yêu cầu chạy đồng thời,
  `DuplicateKeyException` được bắt và đổi thành 409.
- Mọi thay đổi PLO/PI chỉ được khi phiên bản ở `DRAFT`; `ProgramVersion` đã `ACTIVE` bị khóa nội dung.
- Controller không chạm repository; service trả DTO qua mapper.

## 4. Xác nhận nội dung bởi QA_OFFICER có kiểm tra phạm vi (US-08, ADR 0004)

Căn cứ: `PloController.verifyPlo`, `ProgramVerificationServiceImpl.verifyPlo`, `ScopeAuthorizationServiceImpl.canAccessProgram`.
Phiên bản CTĐT và PI đi theo cùng khuôn (`verifyProgramVersion`, `verifyPi`).

```mermaid
sequenceDiagram
    autonumber
    actor QA as QA_OFFICER
    participant CT as PloController
    participant VS as ProgramVerificationServiceImpl
    participant RV as ProgramVersionRepository
    participant SA as ScopeAuthorizationServiceImpl
    participant RL as PloRepository
    participant MP as PloMapper

    Note over QA,CT: Đã qua mục 2. @PreAuthorize hasRole('QA_OFFICER') chặn ADMIN và PROGRAM_MANAGER ngay ở controller (403)
    QA->>CT: PUT /program-versions/{pvId}/plos/{id}/verification {status, note}
    CT->>VS: verifyPlo(pvId, id, request, principal)
    VS->>RV: findById(pvId)
    alt không có phiên bản
        VS-->>QA: 404 NOT_FOUND
    end
    VS->>SA: canAccessProgram(principal, version.programId, WRITE)
    Note over SA: Duyệt từng roleAssignment, hợp (UNION):<br/>ADMIN: true<br/>PROGRAM_MANAGER, QA_OFFICER: programIds chứa programId<br/>VIEWER: chỉ READ<br/>vai trò khác: false (fail-closed)
    alt không có assignment nào cấp quyền
        VS-->>QA: AccessDeniedException, 403
    end
    VS->>RL: findByIdAndProgramVersionId(id, pvId)
    alt không thấy
        VS-->>QA: 404 NOT_FOUND
    end
    VS->>VS: Verification(status từ request, verifiedBy=principal.id, verifiedAt=now, note)
    VS->>RL: save(plo)
    VS->>MP: toResponse
    VS-->>QA: 200 OK
```

Điểm cần nhớ:
- Hai lớp chặn: vai trò ở controller (`QA_OFFICER`), phạm vi chương trình ở service (`scope.programIds`). `ADMIN` được
  `canAccessProgram` cho qua nhưng bị controller chặn trước, đúng ADR 0004 (tách người soạn khỏi người xác nhận).
  Đề xuất này **chờ Q08**; chưa phải quyết định đã duyệt.
- Đây là nơi duy nhất trong mã production gọi `canAccessProgram`; `canAccessSection` (quyền trên lớp học phần) chưa có
  lớp production nào gọi, chỉ có test dùng qua controller thử nghiệm.

## 5. Ghi audit trong transaction (US-39/AC-2)

Căn cứ: `MongoConfig` (`MongoTransactionManager`, `@EnableTransactionManagement`), `AuditServiceImpl.record`
(`@Transactional(propagation = REQUIRED)`), `TransactionRollbackIntegrationTest`.

> **Phạm vi trung thực:** chưa có nghiệp vụ production nào bọc `@Transactional` rồi gọi `AuditService`. Sơ đồ A là cơ chế
> được kiểm bằng `TransactionalBusinessTestService`, một `@TestComponent` chỉ có trong `src/test`, ghi collection `terms`.
> Sơ đồ B là điều xảy ra thật hôm nay ở đăng nhập.

**A. Cơ chế (kiểm bởi test, nghiệp vụ gọi là lớp thử nghiệm)**

```mermaid
sequenceDiagram
    autonumber
    participant T as Nghiệp vụ có @Transactional<br/>(TransactionalBusinessTestService, chỉ trong src/test)
    participant TM as MongoTransactionManager
    participant MT as MongoTemplate
    participant AU as AuditServiceImpl<br/>@Transactional(REQUIRED)
    participant AM as AuditLogMapperImpl
    participant AR as AuditLogRepository
    participant DB as MongoDB replica set rs0

    T->>TM: bắt đầu transaction (ClientSession)
    T->>MT: insert(document vào terms)
    MT->>DB: ghi trong session (chưa commit)
    T->>AU: record(RULE_CONFIRM, entity, before, after, reason)
    Note over AU: REQUIRED nên tham gia transaction đang chạy
    AU->>AM: toDocument(...) che các khóa nhạy cảm thành [PROTECTED]
    AU->>AR: save(AuditLog)
    AR->>DB: ghi audit_logs trong cùng session
    alt bước sau ném ngoại lệ
        T->>TM: rollback
        TM->>DB: hủy cả terms lẫn audit_logs (test khẳng định cả hai đều không còn)
    else không lỗi
        T->>TM: commit
        TM->>DB: lưu cả terms lẫn audit_logs
    end
```

**B. Thực tế hôm nay (đăng nhập)**

```mermaid
sequenceDiagram
    autonumber
    participant SVC as AuthServiceImpl.login<br/>(không @Transactional)
    participant AU as AuditServiceImpl.record<br/>@Transactional(REQUIRED)
    participant TM as MongoTransactionManager
    participant AR as AuditLogRepository
    participant DB as MongoDB rs0

    SVC->>AU: record(LOGIN, users/userId, userId, ip)
    Note over AU,TM: Không có transaction ngoài nên REQUIRED mở transaction mới
    AU->>TM: bắt đầu transaction
    AU->>AR: save(AuditLog)
    AR->>DB: insert audit_logs
    AU->>TM: commit khi record() kết thúc
```

Điểm cần nhớ: replica set là điều kiện để transaction hoạt động (MongoDB standalone sẽ lỗi); vì vậy dev dùng image `docker/mongo`
và CI khởi động `rs0`. `AuditServiceImpl` kiểm `SCORE_UPDATE` bắt buộc `reason`, nên khi F05b có mã, sửa điểm phải truyền lý do.

## 6. Nhập danh sách lớp từ CSV (US-20)

Căn cứ: `EnrollmentController#importEnrollments`, `EnrollmentServiceImpl#importEnrollments`, `EnrollmentCsvParser#parse`,
`GlobalExceptionHandler#handleImportValidation`, `EnrollmentImportIntegrationTest`. Thao tác là **một transaction**: có dòng lỗi thì
không ghi gì (nhánh `alt`), nên mẫu số `enrolledCount` không bao giờ lệch nửa chừng.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (giảng viên lớp)
    participant CT as EnrollmentController
    participant SA as ScopeAuthorizationServiceImpl
    participant S as EnrollmentServiceImpl<br/>@Transactional
    participant P as EnrollmentCsvParser
    participant DB as MongoDB replica set rs0

    C->>CT: POST /sections/{id}/enrollments/import (multipart file)
    CT->>SA: canAccessSection(principal, id, WRITE)
    alt không phải giảng viên của lớp / ADMIN
        SA-->>CT: false
        CT-->>C: 403
    end
    CT->>S: importEnrollments(id, InputStream, actor)
    S->>DB: đọc lớp; đọc học kỳ của lớp
    alt lớp hoặc học kỳ không OPEN
        S-->>C: 422 (mẫu số đã đóng băng)
    end
    S->>P: parse(InputStream)
    alt tệp rỗng / thiếu cột studentRef / quá 5000 dòng
        P-->>C: 400 VALIDATION_ERROR
    end
    S->>DB: đọc students theo studentRef, enrollments hiện có của lớp
    S->>S: validateRows: thiếu, sai mẫu, quá dài, trùng trong tệp, đã ghi danh, đã rút
    alt có dòng lỗi
        S-->>C: 422 + fieldErrors[line N] (chưa ghi gì)
    else tất cả hợp lệ
        S->>DB: saveAll(students mới)
        S->>DB: saveAll(enrollments ENROLLED)
        S->>DB: count(ENROLLED) rồi ghi course_sections.enrolledCount
        S-->>C: 200 {totalRows, newStudents, reusedStudents, sectionEnrolledCount, ignoredColumns}
    end
```

## 7. Duyệt ma trận bài đánh giá bản lớp (NEW-01, ADR 0001)

Căn cứ: `AssessmentController#submit/approve/reject`, `AssessmentApprovalServiceImpl`, `ScopeAuthorizationServiceImpl#canReviewAssessment`,
`AuditServiceImpl#record`, `AssessmentIntegrationTest`. Gửi duyệt, duyệt và từ chối mỗi thao tác là một transaction; `status` và `isLocked`
được ghi trong cùng một lần `save`, `revision` (`@Version`) và WriteConflict của MongoDB bảo đảm hai người cùng duyệt chỉ một người thắng.

```mermaid
sequenceDiagram
    autonumber
    participant GV as Giảng viên của lớp
    participant TBM as Trưởng bộ môn (đúng scope.courseIds)
    participant C as AssessmentController
    participant SA as ScopeAuthorizationServiceImpl
    participant S as AssessmentApprovalServiceImpl<br/>@Transactional
    participant AU as AuditServiceImpl
    participant DB as MongoDB replica set rs0

    GV->>C: POST /assessments/{id}/submit
    C->>SA: canAccessAssessment(WRITE) (lớp của giảng viên)
    C->>S: submit(id, actor)
    S->>DB: đọc bài; phải là SECTION và DRAFT
    alt chưa có phần tử / totalPoints thiếu hoặc lệch Σ maxScore từ 0,001
        S-->>GV: 422
    end
    S->>DB: save(SUBMITTED, approval.submittedBy/At, history SUBMIT)

    TBM->>C: POST /assessments/{id}/approve
    C->>SA: canReviewAssessment (DEPARTMENT_HEAD, courseId của đề cương trong scope)
    alt không đúng vai trò / phạm vi (kể cả ADMIN)
        SA-->>TBM: 403
    end
    C->>S: approve(id, actor)
    S->>DB: đọc bài; phải đang SUBMITTED, nếu không 422
    alt actor là người đã gửi duyệt
        S-->>TBM: 403
    end
    S->>DB: save(APPROVED, isLocked = true, reviewedBy/At, history APPROVE) kiểm revision
    S->>AU: record(ASSESSMENT_APPROVE, before, after)
    AU->>DB: ghi audit_logs trong cùng transaction
    alt người duyệt khác ghi trước (revision đổi / WriteConflict 112)
        DB-->>TBM: 409
    else thành công
        S-->>TBM: 200 (items[] bị khóa)
    end
```

Từ chối (`/reject`) giống nhánh duyệt nhưng bắt buộc `reviewNote`, đặt `REJECTED`, `isLocked = false`, ghi `ASSESSMENT_REJECT`. Giảng viên sửa
bài `REJECTED` thì bài tự về `DRAFT` (`AssessmentServiceImpl#reopenIfRejected`) rồi mới gửi duyệt lại được.

## 8. Bảng đối chiếu nhanh sơ đồ ↔ mã

| Sơ đồ | Lớp/phương thức cần mở để đối chiếu |
|---|---|
| 1 | `AuthController#login`, `AuthServiceImpl#login`, `AuthServiceImpl.DUMMY_HASH`, `AuditServiceImpl#record` |
| 2 | `SecurityConfig#securityFilterChain`, `#bearerTokenResolver`, `#jwtAuthenticationConverter`; `AuthSessionServiceImpl#validateUserSession`; `UserPrincipal#getAuthorities` |
| 3 | `ProgramVersionServiceImpl#createProgramVersion`; `PloServiceImpl#createPlo`, `#requireDraftableVersion`; `PiServiceImpl#createPi`, `#requirePloInVersion` |
| 4 | `ProgramVerificationServiceImpl#verifyPlo`, `#requireProgramAccess`; `ScopeAuthorizationServiceImpl#canAccessProgram`, `#checkProgramAssignment` |
| 5 | `MongoConfig#transactionManager`; `AuditServiceImpl#record`; `TransactionRollbackIntegrationTest` |
| 6 | `EnrollmentController#importEnrollments`; `EnrollmentServiceImpl#importEnrollments`, `#validateRows`, `#recountEnrolled`; `EnrollmentCsvParser#parse`; `GlobalExceptionHandler#handleImportValidation` |
| 7 | `AssessmentController#submit/approve/reject`; `AssessmentApprovalServiceImpl#submit`, `#approve`, `#reject`, `#requireNotSubmitter`; `ScopeAuthorizationServiceImpl#canReviewAssessment`; `AssessmentServiceImpl#requireEditable`, `#reopenIfRejected` |

## 9. Thiết kế mục tiêu (chưa có mã)

Các luồng **nhập điểm từ tệp** (F05b: tải tệp, ánh xạ cột, kiểm tra, xác nhận trong transaction có ghi `IMPORT_CONFIRM`/`SCORE_UPDATE`)
và **chạy đo lường** (F06: CLO → PI → PLO theo `measurement_rules`, ghi `RUN_CALCULATION`) **chưa có mã** trong `src/main`
nên không có sơ đồ ở đây. Khi có mã, thêm sơ đồ vào file này ngay trong PR đó (rule 05: sơ đồ khớp mã, cập nhật cùng thay đổi).
Mô tả dự kiến nằm ở [`doc/plan/F05b-nhap-diem.md`](../plan/F05b-nhap-diem.md) và [`doc/plan/F06-do-luong.md`](../plan/F06-do-luong.md)
(bản nháp chưa ký, không phải thiết kế đã được chấp nhận).
