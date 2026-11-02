# Sơ đồ ERD / class của các collection đã có `document`

> **Dự thảo do AI soạn, đối chiếu mã ngày 2026-11-02 (`main`, commit `54ef80e`).** Chỉ vẽ **9 collection có lớp `@Document`**
> trong `src/main/java/com/hcmute/lms/lmsapi/document`, đối chiếu với validator/chỉ mục trong `doc/database_import.js`.
> 21 collection còn lại của `database_import.js` (tổng 30) **chưa có `document`** nên không vẽ (mục 4). MongoDB không có khóa
> ngoại: quan hệ dưới đây là tham chiếu bằng `ObjectId`, do tầng service kiểm tra trước khi ghi.

## 1. Sơ đồ

```mermaid
erDiagram
    users {
        ObjectId _id PK
        string username UK
        string email UK
        string passwordHash
        string fullName
        string status "ACTIVE hoặc LOCKED"
        long tokenVersion
        array roleAssignments "role + scope(programIds, courseIds, sectionIds)"
        date createdAt
        date updatedAt
    }
    programs {
        ObjectId _id PK
        string code UK
        string name
        string level
        string degree
        object extra
    }
    program_versions {
        ObjectId _id PK
        ObjectId programId FK
        string version "duy nhất cùng programId"
        array applicableCohorts
        int totalCredits
        double durationYears
        string status "DRAFT, ACTIVE, ARCHIVED"
        object verification
        object extra
    }
    plos {
        ObjectId _id PK
        ObjectId programVersionId FK
        string code "duy nhất cùng programVersionId"
        int displayOrder
        string category
        string contentOriginal
        string contentSummary
        int capabilityLevelRaw
        object verification
        string reviewNote
    }
    pis {
        ObjectId _id PK
        ObjectId programVersionId FK
        ObjectId ploId FK
        string code "duy nhất cùng programVersionId"
        int displayOrder
        string nameOriginalTerm
        string contentOriginal
        string contentShort
        int capabilityLevelRaw
        double targetPassRate
        object verification
        string reviewNote
    }
    courses {
        ObjectId _id PK
        string code UK
        string name
        int credits
        object extra
    }
    course_pi_contributions {
        ObjectId _id PK
        ObjectId programVersionId FK
        ObjectId courseId FK
        ObjectId piId FK
        string contribution "I, R hoặc M"
        object verification
    }
    course_sections {
        ObjectId _id PK
        string code UK
        ObjectId courseId FK
        ObjectId syllabusId "collection syllabi chưa có document"
        ObjectId termId "collection terms chưa có document"
        array lecturerIds "ObjectId của users"
        int enrolledCount
        string status "OPEN, CLOSED, LOCKED"
    }
    audit_logs {
        ObjectId _id PK
        date at
        ObjectId userId FK
        string action "7 giá trị AuditAction"
        object entity "collection + id (đa hình)"
        object before
        object after
        string reason
        string ip
    }

    programs ||--o{ program_versions : "programId"
    program_versions ||--o{ plos : "programVersionId"
    program_versions ||--o{ pis : "programVersionId"
    plos ||--o{ pis : "ploId"
    program_versions ||--o{ course_pi_contributions : "programVersionId"
    courses ||--o{ course_pi_contributions : "courseId"
    pis ||--o{ course_pi_contributions : "piId"
    courses ||--o{ course_sections : "courseId"
    users ||--o{ audit_logs : "userId"
    users }o--o{ course_sections : "lecturerIds"
```

## 2. Đối tượng nhúng và tham chiếu không vẽ thành đường nối

| Đối tượng nhúng (class) | Nằm trong | Trường |
|---|---|---|
| `RoleAssignment` | `users.roleAssignments[]` | `role` (enum `Role`), `scope` |
| `UserScope` | `RoleAssignment.scope` | `programIds[]`, `courseIds[]`, `sectionIds[]` (ObjectId; tham chiếu `programs`, `courses`, `course_sections`). "Scope rỗng" = `scope` null hoặc cả ba mảng rỗng (`UserScope.isEmpty()`) |
| `Verification` | `program_versions`, `plos`, `pis`, `course_pi_contributions` | `status` (`UNVERIFIED`, `VERIFIED`, `NEEDS_CLARIFICATION`), `verifiedBy` (ObjectId của `users`), `verifiedAt`, `note` |
| `AuditEntity` | `audit_logs.entity` | `collection` (tên collection bất kỳ), `id` (chuỗi) — tham chiếu đa hình, không phải khóa ngoại |

Tham chiếu có trong mã nhưng không vẽ để tránh rối: `users.roleAssignments[].scope.*` → `programs`/`courses`/`course_sections`
(kiểm tra tồn tại bởi `ScopeValidationServiceImpl`); `Verification.verifiedBy` → `users`.

## 3. Chỉ mục (đối chiếu `@CompoundIndex` / `@Indexed` với `database_import.js`)

| Collection | Chỉ mục duy nhất | Chỉ mục khác |
|---|---|---|
| `users` | `username`; `email` | — |
| `programs` | `code` | — |
| `program_versions` | `(programId, version)` | — |
| `plos` | `(programVersionId, code)` | — |
| `pis` | `(programVersionId, code)` | `ploId` |
| `courses` | `code` | — |
| `course_pi_contributions` | `(programVersionId, courseId, piId)` | `piId` |
| `course_sections` | `code` | `(courseId, termId)`; `lecturerIds` |
| `audit_logs` | — | `(entity.collection, entity.id, at desc)`; `(userId, at desc)` |

Trong `database_import.js` mỗi collection có `$jsonSchema` với `validationAction: 'error'` (lỗi mã 121 được
`GlobalExceptionHandler` đổi thành 422). Hai file (`@Document` và script) là hai nơi định nghĩa độc lập: khi đổi
một bên phải đối chiếu bên kia. Script là "hợp đồng dữ liệu" (`doc/plan/README.md` §3).

## 4. Collection có trong `database_import.js` nhưng chưa có `document` (không vẽ)

`program_courses`, `syllabi`, `clos`, `clo_pi_links`, `question_banks`, `question_categories`, `questions`, `assessments`,
`terms`, `students`, `enrollments`, `import_batches`, `import_row_errors`, `scores`, `measurement_rules`, `calculation_runs`,
`student_results`, `aggregate_results`, `reports`, `improvement_actions` (collection `ml_experiments` đã loại bỏ hoàn toàn khỏi CSDL do ML ngoài phạm vi đồ án, TD-06).

Hai điều cần biết:
- `ProgramReferenceRepositoryImpl.existsPiReference` đọc thẳng ba collection **theo tên chuỗi** (`clo_pi_links`, `questions`,
  `assessments`) bằng `MongoTemplate` để chặn xóa PI còn được tham chiếu — không qua `document`.
- `courses` và `course_sections` có `document` + repository nhưng **chưa có API nào ghi** chúng; dữ liệu đến từ
  `database_import.js` (một học phần mẫu) hoặc từ test. Mã hiện chỉ dùng chúng để kiểm tra tồn tại và phân quyền theo lớp.

## 5. Khác biệt đã thấy giữa thiết kế dữ liệu và `document` (để sinh viên rà)

- `database_import.js` yêu cầu `programId`, `programVersionId`... kiểu `objectId`; trong `document` các trường này khai báo
  `String` kèm `@Field(targetType = FieldType.OBJECT_ID)` để Spring Data ghi đúng kiểu `ObjectId`. Sơ đồ ghi theo kiểu lưu trong MongoDB.
- Plan F02 §3 vẫn ghi `courses.departmentId`; trường này đã bị bỏ khỏi cả `Course` lẫn validator (commit `9a281b8`), nên
  sơ đồ không có.
- `users.createdAt/updatedAt` và `fullName` có trong `document` nhưng không nằm trong danh sách `required` của validator
  (chỉ `username, email, passwordHash, status, roleAssignments` là bắt buộc).
