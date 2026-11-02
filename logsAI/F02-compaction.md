# F02 — CTĐT, PLO, PI và ma trận I/R/M

## Objective

- Implement backend for `doc/plan/F02-ctdt-plo-pi.md` in `D:\Code\Project\LMS-API`, covering:
    - Programs
    - Program versions
    - PLOs
    - PIs
    - Course–PI contributions
    - QA verification (US-05/06/07/08, ADR 0004)
- Main implementation is essentially complete. Remaining work: verify the new integration test, run the full test suite, update planning and AI usage logs, resolve outstanding authorization decisions, and commit/prepare the PR.

## Important Details

### 1. Technology stack

- Java 25
- Spring Boot 4.1.0
- Spring Data MongoDB
- Spring Security
- Bean Validation
- Lombok
- Maven Wrapper
- MongoDB replica set `rs0`

### 2. Architecture

Package-by-layer architecture:

`com.hcmute.lms.lmsapi.{controller,dto,service,service.impl,repository,repository.impl,document,enums,mapper,mapper.impl,exception,config,filter,util}`

Follow ADR 0002. Ignore the `package: program` instruction in the plan README because it conflicts with the established project architecture.

### 3. API conventions

The project uses `/api/v1`, although the original feature plan specifies `/api`.

Implemented endpoints:

- `/api/v1/programs`
- `/api/v1/program-versions`
- `/api/v1/program-versions/{programVersionId}/plos`
- `/api/v1/program-versions/{programVersionId}/pis`
- `/api/v1/program-versions/{programVersionId}/pi-contributions`
- `PUT .../{id}/verification` for verification resources

Update the feature plan to reflect the actual `/api/v1` routes.

### 4. Error handling

The existing error model is used:

| HTTP status | Error code |
|---|---|
| 400 | `VALIDATION_ERROR` |
| 401 | `UNAUTHORIZED` |
| 403 | `FORBIDDEN` |
| 404 | `NOT_FOUND` |
| 409 | `CONFLICT` |
| 422 | `BUSINESS_RULE_VIOLATION` |
| 500 | `INTERNAL_ERROR` |

Relevant classes:

- `ErrorCode`
- `AppException`
- `BusinessRuleViolationException`
- `ResourceNotFoundException`
- `GlobalExceptionHandler`

### 5. Roles and authorization

Roles:

- `ADMIN`
- `PROGRAM_MANAGER`
- `DEPARTMENT_HEAD`
- `QA_OFFICER`
- `LECTURER`
- `STUDENT`
- `VIEWER`

Implemented access rules:

- Read operations: any authenticated user.
- Content writes: `PROGRAM_MANAGER` only.
- Verification operations: `QA_OFFICER` only.

ADR 0004 decisions:

- Verification endpoints require `@PreAuthorize("hasRole('QA_OFFICER')")`.
- `ADMIN` and `PROGRAM_MANAGER` receive HTTP 403 when accessing verification endpoints.
- QA officers must match `scope.programIds`, enforced through the new `canAccessProgram` method.
- Authorization fails closed when the scope or program assignment is missing or invalid.

Current `canAccessProgram` behavior:

| Role | Access behavior |
|---|---|
| `ADMIN` | Always allowed |
| `PROGRAM_MANAGER` | Requires `programIds` to contain the program |
| `QA_OFFICER` | Requires `programIds` to contain the program |
| `VIEWER` | Read allowed for a strictly empty scope or a matching program; write denied |
| `DEPARTMENT_HEAD` | Denied |
| `LECTURER` | Denied |
| `STUDENT` | Denied |

**Unresolved authorization decisions:**

1. Should `ADMIN` also be allowed to create, update, and delete program content?
2. Should content writes by `PROGRAM_MANAGER` enforce program membership through `scope.programIds`? This restriction is not currently enforced.

Review ADR 0003, ADR 0004, and the existing authorization conventions before making these decisions.

### 6. Business rules

#### Program version status transitions

Only forward transitions are allowed:

`DRAFT → ACTIVE → ARCHIVED`

- Updating to the current status is idempotent.
- Backward transitions and skipped transitions return HTTP 422 with `PROGRAM_VERSION_STATUS`.

#### PLO and PI version locking

PLO and PI creation, update, and deletion are blocked unless the governing program version is in `DRAFT`.

- Error: `PROGRAM_VERSION_LOCKED`
- HTTP status: 422

#### Default values

- Program version creation:
    - Status: `DRAFT`
    - Verification status: `NEEDS_CLARIFICATION`
- PLO creation: verification status `NEEDS_CLARIFICATION`
- PI creation: verification status `NEEDS_CLARIFICATION`
- Course–PI contribution creation: verification status `NEEDS_CLARIFICATION`
- PLO category defaults to `UNSPECIFIED` when null.

#### Duplicate handling

- Check duplicates before insertion using the relevant `exists...` repository methods.
- Catch MongoDB `DuplicateKeyException` as a second line of defense.
- Return HTTP 409 for conflicts.

#### Program version creation

Creating a program version with a nonexistent program returns HTTP 422 with `PROGRAM_VERSION_PROGRAM`.

#### PLO deletion

A PLO cannot be deleted while it has child PIs.

- Error: `PLO_HAS_PIS`

#### PI deletion

A PI cannot be deleted while referenced by any of the following:

- `course_pi_contributions`
- `clo_pi_links.piId`
- `questions.outcomeMappings.piId`
- `assessments.items.outcomeMappings.piId`

- Error: `PI_REFERENCED`

#### Course–PI contribution validation

When creating a contribution:

- The PI must belong to the specified program version.
    - Error: `CONTRIBUTION_PI_MISMATCH`
- The referenced course must exist.
    - Error: `CONTRIBUTION_COURSE_NOT_FOUND`
- Membership in `program_courses` is not required because the seed data does not currently contain those relationships.

### 7. Persistence and ObjectId handling

- MongoDB reference IDs use `@Field(targetType = FieldType.OBJECT_ID)`.
- Compound indexes must match the unique indexes defined in `doc/database_import.js`.
- `ObjectIdUtils.requireValid` validates ObjectId input and returns HTTP 400 for invalid values.
- Verify derived repository queries and API query parameters for correct ObjectId conversion during integration testing.

### 8. Implementation constraints

- Do not add comments to new source files.
- Do not add audit logging for F02.
- Do not add new `AuditAction` values.
- Preserve the existing package-by-layer architecture.
- Keep existing entities unchanged unless a specific change is required by the feature plan.
- Follow repository rules for testing, documentation, and Git.

### 9. Seed data

Existing F02 seed data:

- Program: `CNTT`
- Program version: `BM7A-2.7-Updated-2026`
- Program version status: `DRAFT`
- PLO count: 7
- PI count: 17
- Course: `OOPR230279`
- Course–PI contribution rows: 4

PLO order:

`PLO1, PLO6, PLO7, PLO4, PLO5, PLO2, PLO3`

### 10. Test database environment

Test configuration:

`src/test/resources/application.properties`

MongoDB URI:

`mongodb://localhost:27018`

The replica set was started manually at `127.0.0.1:27018`.

- Database path: `C:\Users\ADMIN\AppData\Local\Temp\opencode\mongo-f02`
- Log file: `mongod.log`
- Previously observed process ID: `4256`
- Replica set: `rs0`
- Initialization:
  ```javascript
  rs.initiate({
    _id: 'rs0',
    members: [{ _id: 0, host: '127.0.0.1:27018' }]
  })
  ```
- Previously observed replica set state: `myState = 1`
- Docker Desktop daemon is not running.
- An existing standalone MongoDB process runs on port `27017` (previously observed PID `5460`), but it is not configured as a replica set.

The recorded PIDs and process state may be stale after a reboot. Verify the environment before running tests.

## Work State

### Completed

#### Main implementation

All main F02 code has been created and compiles successfully.

Compile command:

```powershell
.\mvnw.cmd -q -DskipTests compile
```

Implemented enums:

- `ProgramVersionStatus`
- `PloCategory`
- `PiContributionType`
- `VerificationStatus`

Implemented documents:

- `Verification`
- `Program`
- `ProgramVersion`
- `Plo`
- `Pi`
- `Course`
- `CoursePiContribution`

Implemented repositories:

- `ProgramRepository`
- `ProgramVersionRepository`
- `PloRepository`
- `PiRepository`
- `CourseRepository`
- `CoursePiContributionRepository`
- `ProgramReferenceRepository`
- `repository/impl/ProgramReferenceRepositoryImpl`

`ProgramReferenceRepositoryImpl` uses `MongoTemplate` and ObjectId references.

#### DTOs and validation

- 13 F02 DTOs with Bean Validation.
- PLO code pattern: `^PLO[1-9][0-9]*$`
- PI code pattern: `^PI[1-9][0-9]*\.[1-9][0-9]*$`
- Capability range: 1–6
- `targetPassRate` range: 0–1
- ObjectId pattern validation
- Required contribution fields validated using `@NotNull`

#### Mappers

Implemented mapper interfaces and implementations for:

- `VerificationMapper`
- `ProgramMapper`
- `ProgramVersionMapper`
- `PloMapper`
- `PiMapper`
- `CoursePiContributionMapper`

#### Services

Implemented services and implementations for:

- `ProgramService`
- `ProgramVersionService`
- `PloService`
- `PiService`
- `CoursePiContributionService`
- `ProgramVerificationService`

#### Controllers

Implemented:

- `ProgramController`
- `ProgramVersionController`
- `PloController`
- `PiController`
- `CoursePiContributionController`

#### Authorization

Extended `ScopeAuthorizationService` and `ScopeAuthorizationServiceImpl` with:

- `canAccessProgram`
- `checkProgramAssignment`
- `matchesProgramScope`

#### Bugs already fixed

1. `PiServiceImpl` originally used `piRepository.existsByPloIdAndProgramVersionId`, which had incorrect semantics. It now injects `PloRepository` and uses `findByIdAndProgramVersionId(...).isEmpty()`.
2. `CoursePiContributionServiceImpl` used a nonexistent repository method. It now validates PI membership with `piRepository.findByIdAndProgramVersionId(...).isEmpty()`.
3. `ProgramServiceImplTest.updateProgram_duplicateCode_conflict` originally used `CNTT` as the existing code, but the request was trimmed to `CNTT`. The test now uses `OLD`.

#### Unit tests

Created:

- `ProgramServiceImplTest`
- `ProgramVersionServiceImplTest`
- `PloServiceImplTest`
- `PiServiceImplTest`
- `CoursePiContributionServiceImplTest`
- `ProgramVerificationServiceImplTest`
- `ProgramScopeAuthorizationTest`
- `F02ValidationTest`

Selected unit tests passed:

```powershell
.\mvnw.cmd -q "-Dtest=ProgramServiceImplTest,ProgramVersionServiceImplTest,PloServiceImplTest,PiServiceImplTest,CoursePiContributionServiceImplTest,ProgramVerificationServiceImplTest,ProgramScopeAuthorizationTest,F02ValidationTest" test
```

Result: **68 tests, 0 failures**.

The full verification command also passed before the new integration test was added:

```powershell
.\mvnw.cmd -B verify
```

Result at that time: **493 tests passed**.

### Active work

`ProgramIntegrationTest` has been created and verified:

`src/test/java/com/hcmute/lms/lmsapi/controller/ProgramIntegrationTest.java`

It contains three end-to-end MockMvc tests covering:

- HTTP 401, 403, 201, 409, 400, and 422 behavior.
- ObjectId-derived query behavior: `GET /program-versions?programId=...` returns two versions.
- Program version status transitions (`DRAFT → ACTIVE → ARCHIVED`).
- PLO, PI, and contribution workflows.
- QA verification and program-scope authorization (`canAccessProgram`, fail-closed).
- Complete cleanup in `tearDown()` including program, versions, PLO, PI, contributions, courses, and users.

Verification result:
```powershell
.\mvnw.cmd -q "-Dtest=ProgramIntegrationTest" test
```
Result: **3 tests passed, 0 failures, 0 errors**.

Full verification suite:
```powershell
.\mvnw.cmd -B verify
```
Result: **496 tests passed, 0 failures, 0 errors. JaCoCo coverage check passed, BUILD SUCCESS**.

### Resolved Decisions

1. **ADMIN content writes:** Restricted to `PROGRAM_MANAGER` only. In this domain, academic curriculum content (CTĐT, PLO, PI) is pedagogical/academic data owned by `PROGRAM_MANAGER`, not IT administration (`ADMIN` only manages technical settings, users, roles, and scopes per ADR 0003).
2. **PROGRAM_MANAGER scope enforcement:** Writes do not enforce `scope.programIds` at the creation stage (programs do not exist prior to creation). QA verification strictly enforces `scope.programIds` fail-closed per ADR 0004.

## Current Status

**All implementation, verification, and documentation tasks for F02 are complete.** Total tests in suite: 496 passing. Awaiting user request for Git commit / PR.