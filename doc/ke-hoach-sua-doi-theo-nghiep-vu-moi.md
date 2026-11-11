# Kế hoạch Sửa đổi Codebase và Cơ sở dữ liệu theo Nghiệp vụ mới

> **Cơ sở tham chiếu:**
> - Đặc tả nghiệp vụ và toán học gửi GVHD: [`doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`](dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md)
> - Schema MongoDB & Thiết kế CSDL: [`doc/database_import.js`](database_import.js) & [`doc/GiaiThich_CoSoDuLieu.md`](GiaiThich_CoSoDuLieu.md)

---

## 1. Tóm tắt các thay đổi cốt lõi trong Database và Nghiệp vụ

1. **Loại bỏ Ngân hàng câu hỏi, Nội dung đề thi & Bảng `measurement_rules` rời rạc (29 $\to$ 25 collections):**
   - Đã xoá 3 collections câu hỏi: `question_banks`, `question_categories`, `questions`.
   - Xoá collection `measurement_rules` (và bỏ trường `ruleId`, `missingDataPolicy` trong `calculation_runs`), vì toàn bộ ngưỡng đạt (`passThreshold`) và trọng số đóng góp đã được gắn bắt buộc trực tiếp trên `plos`, `pis`, `clos`, `clo_pi_links`, `assessments.cloWeights`.
   - Trong `assessments.items[]`: Xoá `title`, `kind`, `maxScore` (cấp câu hỏi), `topic`, `questionId`, `questionVersion`, `externalRef`, `rubric`. Chỉ giữ định danh câu hỏi ẩn danh (`id`, `code`: `"Câu 1"`, `"Câu 2"`, `orderIndex`, `bloomLevel`, `outcomeMappings`).
2. **Import CSV CTĐT & Đề cương kèm sẵn Trọng số ($\sum = 100\%$) và Ngưỡng đạt bắt buộc (`PassThreshold`) theo quy trình `UNVERIFIED` $\to$ `VERIFIED`:**
   - Một vai trò quản lý học thuật (`PROGRAM_MANAGER` / `ADMIN`) import tệp CSV chuẩn hóa của CTĐT và Đề cương để khởi tạo:
     - **`plos`:** bắt buộc có **`passThreshold`** ($0 < passThreshold \le 1$, không được để trống).
     - **`pis`:** bắt buộc có **`weight`** ($0 < weight \le 1$, trọng số đóng góp $W_{PI \to PLO}$, thỏa mãn $\sum_{m \in PI(PLO_q)} W_{PI_m \to PLO_q} = 1.0$) và **`passThreshold`** ($0 < passThreshold \le 1$, không được để trống).
     - **`clos`:** trong `measurement`, xoá `defaultWeight`, bắt buộc có **`defaultPassThreshold`** ($0 < defaultPassThreshold \le 1$, không được để trống).
     - **`clo_pi_links`:** bắt buộc có **`weight`** ($0 < weight \le 1$, trọng số đóng góp $W_{CLO_{c,j} \to PI_m} \in (0, 1]$; không ép buộc tổng qua mọi học phần phải bằng $1.0$ do có các học phần tự chọn, khi tính PI hệ thống tự động chia chuẩn hóa cho mẫu số $\sum_{\text{Evaluated}} W$).
   - Khi vừa Import, dữ liệu lưu ở trạng thái **`UNVERIFIED`** để hiển thị lên giao diện kiểm tra; **nếu sai thì người dùng sửa trên tệp CSV rồi Import lại** (ghi đè bản `UNVERIFIED`), nếu đúng thì bấm **Lưu / Xác nhận** trên giao diện để chuyển sang **`VERIFIED`**.
3. **Tỷ lệ % đóng góp của từng bài kiểm tra cho từng CLO ($\sum_{p} W_{p,j} = 100\%$) & Giảng viên tự khóa ma trận đề:**
   - Trên mỗi bài kiểm tra `assessments`: Xoá `totalPoints`, `weightInCourse`, `dataGranularity`, `approval`, `exportedFiles`; bổ sung **`cloWeights: [{ cloId, weight }]`** ($0 < weight \le 1$, biểu diễn $W_{p,j}$ cho các CLO có trong bài; nếu bài không đánh giá $CLO_j$ thì $W_{p,j} = 0$).
   - **Quy tắc kiểm tra theo thời điểm:**
     - Khi khóa từng bài kiểm tra đơn lẻ (`POST /lock`): kiểm tra mọi CLO xuất hiện trong `items` đều có $0 < W_{p,j} \le 1.0$ và tổng tích lũy qua các bài đã khóa trong cùng `sectionId` $\le 1.0$.
     - Khi chạy tính tổng hợp CLO toàn học phần (Cấp độ 2): bắt buộc tổng $\sum_{p \in P(CLO_j)} W_{p,j} = 1.0$ ($100\%$).
   - Vòng đời `assessments.status` rút gọn còn: `DRAFT` $\to$ `LOCKED` $\to$ `SCORE_ENTERED`. Thay `/submit`, `/approve`, `/reject` bằng `POST /api/v1/assessments/{id}/lock`, `POST /api/v1/assessments/{id}/unlock` và `DELETE /api/v1/assessments/{id}`.
4. **Xuất tệp CSV mẫu từ Ma trận đề đã khóa & Import Điểm CLO trực tiếp theo từng câu hỏi (Vắng thi / Bỏ trống = `0` điểm):**
   - Trong `assessments.items[].outcomeMappings[]`: Thay `weight` bằng **`maxScore` ($> 0$)** cho từng `(itemId, cloId)`.
   - Hệ thống tự động sinh tệp CSV mẫu từ ma trận đề đã khóa (`LOCKED`) cho danh sách sinh viên đang theo học (`ENROLLED`).
   - Trong `scores`:
     - Bổ sung trường bắt buộc **`cloId`**, xoá `rubricLevel` và xoá `status` (`SCORED`, `ABSENT`, `EXEMPT`, `MISSING`). Unique index mới: `{ sectionId: 1, assessmentId: 1, itemId: 1, cloId: 1, studentId: 1 }`.
     - Giảng viên nhập điểm thực đạt trực tiếp cho từng CLO của từng câu hỏi ($0 \le Score_{s,p,k,j} \le MaxScore_{p,k,j}$).
     - **Nếu sinh viên vắng thi hoặc bỏ trống câu hỏi:** Điểm lưu trực tiếp **`rawScore = 0.0`**.
     - Khi chọn tệp CSV để Import điểm, dữ liệu hiển thị lên giao diện để giảng viên kiểm tra trước (nếu sai thì sửa tệp CSV rồi Import lại), khi bấm **Lưu** là ghi chính thức vào bảng `scores` (không dùng `UNVERIFIED` trên bảng `scores`).
5. **Đo lường và Lưu trữ Kết quả Chuẩn đầu ra xuyên suốt 4 Cấp độ (`ASSESSMENT_CLO` $\to$ `CLO` $\to$ `PI` $\to$ `PLO`):**
   - Cập nhật `RESULT_LEVELS` (bỏ `'COURSE'`, thêm `'ASSESSMENT_CLO'`): **`['ASSESSMENT_CLO', 'CLO', 'PI', 'PLO']`**.
   - Bổ sung trường **`assessmentId` (`objectId | null`)** vào `student_results` và `aggregate_results` (khác `null` khi `level = 'ASSESSMENT_CLO'`, bằng `null` ở các cấp `'CLO'`, `'PI'`, `'PLO'`).
   - Cấp độ 1 & 2 chia cho mẫu số **$N_{\text{section}}$** (sĩ số sinh viên `ENROLLED` của lớp học phần); Cấp độ 3 & 4 chia cho mẫu số **$N_{\text{cohort}}$** (sĩ số sinh viên thuộc khóa/CTĐT trong đợt tính).
   - Ở Cấp độ 3 (`CLO -> PI`):
     - Nếu sinh viên học lại / học cải thiện học phần $c$, lấy kết quả $Value(CLO_{s,c,j})$ của **lần học gần nhất (`LATEST`)**.
     - Tính theo công thức trung bình có trọng số **chuẩn hóa trên tập các CLO sinh viên đã có kết quả** ($\text{Evaluated}(s, PI_m)$):
       $$Value(PI_{s,m}) = \frac{\sum_{(c, j) \in \text{Evaluated}(s, PI_m)} \Big( W_{CLO_{c,j} \to PI_m} \times Value(CLO_{s,c,j}) \Big)}{\sum_{(c, j) \in \text{Evaluated}(s, PI_m)} W_{CLO_{c,j} \to PI_m}}$$

---

## 2. Quy trình Git chống Conflict trước khi sửa Code & Database

Do nhánh `origin/main` vừa merge PR #83 (`feat/f05b-score-import`, gồm 29 file Java của F05b và file `doc/adr/0013-nhap-diem-luu-tep-goc-loi-cap-tep.md`), để **đảm bảo 100% không xảy ra Merge Conflict**:

1. **Bước 1:** Cất tạm các thay đổi chưa commit trên nhánh local (`git stash push -u -m "wip-new-spec"`).
2. **Bước 2:** Kéo `origin/main` mới nhất về (`git checkout main && git pull origin main`) và tạo **một nhánh làm việc duy nhất** từ `origin/main` mới nhất (ví dụ: `git checkout -b feat/obe-4-levels-refactor`).
3. **Bước 3:** Đặt số hiệu ADR mới là **`doc/adr/0014-import-ctdt-de-cuong-va-do-luong-4-cap-clo-pi-plo.md`** để không đụng tên với file `0013-nhap-diem-luu-tep-goc-loi-cap-tep.md` đã có trên `origin/main`.
4. **Bước 4:** Thực hiện tuần tự từ Phase 0 đến Phase 4 trên cùng nhánh mới này.

---

## 3. Lộ trình thực thi trên Database & Codebase (`src/main` & `src/test`)

```
Phase 0: Cập nhật doc/database_import.js (25 collections) & Đồng bộ tài liệu kiến trúc
   │
   ▼
Phase 1: Refactor Code hiện có của F02, F03 & F04 (Xoá 13 file thừa + Cập nhật Domain/Service/Controller/Test)
   │
   ▼
Phase 2: Bổ sung API Import CSV CTĐT & Đề cương (PassThreshold bắt buộc, Trọng số tổng = 100%, UNVERIFIED -> VERIFIED)
   │
   ▼
Phase 3: Refactor F05b (từ PR #83) — Xuất CSV Mẫu từ Ma trận đề đã khóa & Import Điểm CLO (Vắng/Trống = 0.0)
   │
   ▼
Phase 4: Triển khai F06 & F07 — Engine Đo lường 4 Cấp độ (ASSESSMENT_CLO -> CLO -> PI -> PLO) & Báo cáo
```

---

### Phase 0: Cập nhật `doc/database_import.js` (25 Collections) & Đồng bộ Tài liệu

1. **Cập nhật [`doc/database_import.js`](database_import.js):**
   - Xoá collection `measurement_rules` (tổng số collection còn **25 collections**).
   - Trong `plos`: thêm trường bắt buộc `passThreshold: { bsonType: 'double', minimum: 0, exclusiveMinimum: true, maximum: 1 }`.
   - Trong `pis`: thêm 2 trường bắt buộc `weight: { bsonType: 'double', minimum: 0, exclusiveMinimum: true, maximum: 1 }` và `passThreshold: { bsonType: 'double', minimum: 0, exclusiveMinimum: true, maximum: 1 }`.
   - Trong `clos`: tại `measurement`, xoá `defaultWeight`, bắt buộc `defaultPassThreshold: { bsonType: 'double', minimum: 0, exclusiveMinimum: true, maximum: 1 }`.
   - Trong `clo_pi_links`: đặt `weight` là bắt buộc (`double`, $(0, 1]$).
   - Trong `assessments`:
     - Xoá `totalPoints`, `weightInCourse`, `dataGranularity`, `approval`, `exportedFiles`.
     - Trong `items[]`: xoá `title`, `kind`, `maxScore` (cấp câu hỏi), `topic`, `questionId`, `questionVersion`, `externalRef`, `rubric`; trong `outcomeMappings[]` đổi `weight` thành `maxScore` ($> 0$).
     - Thêm mảng `cloWeights: [{ cloId: ObjectId, weight: double (0, 1] }]`.
   - Trong `scores`:
     - Xoá hằng `SCORE_STATUS_VALUES`, xoá trường `status`, xoá `rubricLevel`, xoá điều kiện `oneOf` cho `rawScore: null`.
     - Bắt buộc `cloId: ObjectId` và `rawScore: { bsonType: 'double', minimum: 0 }`.
     - Unique index: `{ sectionId: 1, assessmentId: 1, itemId: 1, cloId: 1, studentId: 1 }`.
   - Trong `calculation_runs`: xoá `ruleId` và `missingDataPolicy`.
   - Trong `student_results` và `aggregate_results`:
     - Đổi `RESULT_LEVELS = ['ASSESSMENT_CLO', 'CLO', 'PI', 'PLO']`.
     - Thêm trường `assessmentId: { bsonType: ['objectId', 'null'] }`.
     - Cập nhật Unique Index của `student_results`: `{ runId: 1, level: 1, subjectId: 1, sectionId: 1, assessmentId: 1, studentId: 1 }`.
     - Cập nhật Unique Index của `aggregate_results`: `{ runId: 1, level: 1, subjectId: 1, sectionId: 1, assessmentId: 1 }`.
2. **Nạp lại schema vào MongoDB Docker:**
   ```powershell
   docker exec -i mongodb mongosh -u admin -p admin123 --authenticationDatabase admin < doc/database_import.js
   ```
3. **Đồng bộ các tài liệu liên quan:** `doc/GiaiThich_CoSoDuLieu.md`, `doc/adr/0014-...`, `CLAUDE.md`, `.claude/rules/02-domain-invariants.md`, `doc/plan/F02-ctdt-plo-pi.md`, `F03-hoc-phan-clo.md`, `F04-bai-danh-gia.md`, `F05b-nhap-diem.md`, `F06-do-luong.md`, `F07-bao-cao-truy-vet.md`, `doc/sdd/*`, `doc/test-plan.md`.

---

### Phase 1: Refactor Code hiện có của F02, F03 & F04 (`src/main/java/com/hcmute/lms/lmsapi/...`)

#### 1.1. Danh sách 13 file Java của F04 cần XOÁ BỎ hoàn toàn

| Phân lớp | Đường dẫn file cần xoá | Lý do xoá |
|---|---|---|
| **Document** | `src/main/java/com/hcmute/lms/lmsapi/document/AssessmentApproval.java` | Bỏ luồng duyệt đề `approval` |
| **Document** | `src/main/java/com/hcmute/lms/lmsapi/document/ApprovalHistoryEntry.java` | Bỏ lịch sử duyệt đề `approval.history` |
| **Document** | `src/main/java/com/hcmute/lms/lmsapi/document/AssessmentRubric.java` | Bỏ `rubric` trên câu hỏi |
| **Document** | `src/main/java/com/hcmute/lms/lmsapi/document/RubricLevel.java` | Bỏ `rubric.levels` |
| **DTO** | `src/main/java/com/hcmute/lms/lmsapi/dto/AssessmentRejectRequest.java` | Bỏ API `/reject` |
| **DTO** | `src/main/java/com/hcmute/lms/lmsapi/dto/RubricDto.java` | Bỏ `rubric` |
| **DTO** | `src/main/java/com/hcmute/lms/lmsapi/dto/RubricLevelDto.java` | Bỏ `rubric.levels` |
| **Enum** | `src/main/java/com/hcmute/lms/lmsapi/enums/ApprovalAction.java` | Bỏ `SUBMIT / APPROVE / REJECT / REOPEN` |
| **Enum** | `src/main/java/com/hcmute/lms/lmsapi/enums/DataGranularity.java` | Bỏ `dataGranularity` (`ITEM` / `ASSESSMENT_TOTAL`) |
| **Enum** | `src/main/java/com/hcmute/lms/lmsapi/enums/ItemKind.java` | Bỏ phân loại hình thức câu hỏi (`MCQ`, `ESSAY`...) |
| **Service** | `src/main/java/com/hcmute/lms/lmsapi/service/AssessmentApprovalService.java` | Thay bằng `lock` / `unlock` trực tiếp trong `AssessmentService` |
| **Service Impl** | `src/main/java/com/hcmute/lms/lmsapi/service/impl/AssessmentApprovalServiceImpl.java` | Thay bằng `lock` / `unlock` trực tiếp trong `AssessmentServiceImpl` |
| **Unit Test** | `src/test/java/com/hcmute/lms/lmsapi/service/impl/AssessmentApprovalServiceImplTest.java` | Chuyển thành test `lock` / `unlock` |

#### 1.2. Danh sách các file Java cần SỬA ĐỔI / BỔ SUNG trong Phase 1

1. **Nhóm F02 (PLO & PI — Bổ sung `passThreshold` bắt buộc và `weight` đóng góp PI $\to$ PLO):**
   - `src/main/java/com/hcmute/lms/lmsapi/document/Plo.java`, `dto/PloRequest.java`, `dto/PloResponse.java`, `mapper/impl/PloMapperImpl.java`, `service/impl/PloServiceImpl.java`:
     - Bổ sung `Double passThreshold` (`@NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")`).
   - `src/main/java/com/hcmute/lms/lmsapi/document/Pi.java`, `dto/PiRequest.java`, `dto/PiResponse.java`, `mapper/impl/PiMapperImpl.java`, `service/impl/PiServiceImpl.java`:
     - Bổ sung `Double weight` (`@NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")`, trọng số đóng góp $W_{PI \to PLO}$) và `Double passThreshold` (`@NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")`).

2. **Nhóm F03 (CLO & Liên kết CLO – PI):**
   - `src/main/java/com/hcmute/lms/lmsapi/document/CloMeasurement.java`, `dto/CloMeasurementDto.java`, `mapper/impl/CloMapperImpl.java`, `service/impl/CloServiceImpl.java`:
     - Xoá trường `Double defaultWeight`.
     - Giữ trường `Double defaultPassThreshold` và đặt `@NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")` (không được để trống).
   - `src/main/java/com/hcmute/lms/lmsapi/document/CloPiLink.java`, `dto/CloPiLinkRequest.java`, `dto/CloPiLinkCellDto.java`, `dto/CloPiLinkResponse.java`, `service/impl/CloPiLinkServiceImpl.java`:
     - Đặt `Double weight` là bắt buộc (`@NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")`), lưu trọng số đóng góp $W_{CLO_{c,j} \to PI_m}$.

3. **Nhóm F04 (Enums):**
   - `src/main/java/com/hcmute/lms/lmsapi/enums/AssessmentStatus.java`: Xoá `SUBMITTED`, `APPROVED`, `REJECTED`. Giữ đúng 3 giá trị: `DRAFT`, `LOCKED`, `SCORE_ENTERED`.
   - `src/main/java/com/hcmute/lms/lmsapi/enums/AuditAction.java`: Xoá `ASSESSMENT_APPROVE`, `ASSESSMENT_REJECT`. Thêm `ASSESSMENT_LOCK`, `ASSESSMENT_UNLOCK`.

4. **Nhóm F04 (Documents & DTOs):**
   - `src/main/java/com/hcmute/lms/lmsapi/document/OutcomeMapping.java` & `dto/OutcomeMappingDto.java`:
     - Đổi `Double weight` thành `Double maxScore` (`@NotNull @DecimalMin(value = "0.0", inclusive = false)`).
   - **Tạo mới** `src/main/java/com/hcmute/lms/lmsapi/document/AssessmentCloWeight.java` & `src/main/java/com/hcmute/lms/lmsapi/dto/AssessmentCloWeightDto.java`:
     - Gồm `{ String cloId, Double weight }` (`0 < weight <= 1.0`) biểu diễn **tỷ lệ % đóng góp $W_{p,j}$ của bài kiểm tra $A_p$ cho `cloId`** trong toàn học phần.
   - `src/main/java/com/hcmute/lms/lmsapi/document/AssessmentItem.java` & `dto/AssessmentItemDto.java`:
     - Xoá `title`, `kind`, `maxScore` (cấp item), `topic`, `externalRef`, `rubric`. Giữ: `id`, `code` (`@NotBlank`), `orderIndex` (`@Min(1)`), `bloomLevel`, `outcomeMappings` (`@NotEmpty @Valid`).
   - `src/main/java/com/hcmute/lms/lmsapi/document/Assessment.java`:
     - Xoá `totalPoints`, `weightInCourse`, `dataGranularity`, `approval`. Giữ `isLocked` và bổ sung `List<AssessmentCloWeight> cloWeights`.
   - `src/main/java/com/hcmute/lms/lmsapi/dto/AssessmentRequest.java`, `AssessmentUpdateRequest.java`, `AssessmentResponse.java`, `mapper/impl/AssessmentMapperImpl.java`:
     - Xoá `totalPoints`, `weightInCourse`, `dataGranularity`, `measurableByOutcome`, `approval`; bổ sung `List<AssessmentCloWeightDto> cloWeights`.

5. **Nhóm F04 (Validators, Calculators, Services, Controllers):**
   - `src/main/java/com/hcmute/lms/lmsapi/service/impl/AssessmentItemValidator.java`:
     - Xoá hàm `sumsToOne` (trên câu hỏi) và toàn bộ kiểm tra `rubric`.
     - Kiểm tra mỗi `item` có `outcomeMappings` khác rỗng, không trùng `cloId` trong cùng 1 câu hỏi, mọi `mapping.maxScore > 0`, và mọi `cloId` thuộc cùng `syllabusId` của bài đánh giá.
     - Kiểm tra `cloWeights`: không trùng `cloId`, mỗi `weight` nằm trong $(0, 1]$, tập `cloId` trong `cloWeights` khớp với tập các `cloId` xuất hiện trong `items[].outcomeMappings[]` của bài kiểm tra đó, và tổng tỷ lệ đóng góp `weight` tích lũy của mỗi `cloId` qua các bài kiểm tra đã khóa trong cùng `sectionId` không vượt quá `1.0` ($100\%$).
   - `src/main/java/com/hcmute/lms/lmsapi/service/impl/AssessmentCoverageCalculator.java`:
     - Tính tổng điểm tối đa theo từng `cloId` và theo `bloomLevel` từ `mapping.getMaxScore()`.
   - `src/main/java/com/hcmute/lms/lmsapi/service/AssessmentService.java` & `service/impl/AssessmentServiceImpl.java`:
     - Bỏ logic `reopenIfRejected`, bỏ gán `dataGranularity`, `totalPoints`, `weightInCourse`, `approval`.
     - Khi tạo/cập nhật: Nếu `status != DRAFT` hoặc `Boolean.TRUE.equals(assessment.getIsLocked())` $\to$ ném `ASSESSMENT_LOCKED` (409).
     - Bổ sung phương thức `AssessmentResponse lock(String id, UserPrincipal principal)`:
       - Kiểm tra quyền giảng viên phụ trách `sectionId` (hoặc `ADMIN`).
       - Kiểm tra `status == DRAFT`, `items` không rỗng (`ASSESSMENT_ITEMS_REQUIRED`), `cloWeights` hợp lệ qua `AssessmentItemValidator`.
       - Cập nhật `status = LOCKED`, `isLocked = true`, ghi `AuditLog` (`ASSESSMENT_LOCK`).
     - Bổ sung phương thức `AssessmentResponse unlock(String id, UserPrincipal principal)`:
       - Kiểm tra quyền giảng viên phụ trách `sectionId` (hoặc `ADMIN`).
       - Kiểm tra `status == LOCKED` (nếu `status == SCORE_ENTERED` hoặc đã có bản ghi trong `scores` của `assessmentId` $\to$ chặn 409 `ASSESSMENT_HAS_SCORES`).
       - Cập nhật `status = DRAFT`, `isLocked = false`, ghi `AuditLog` (`ASSESSMENT_UNLOCK`).
     - Bổ sung phương thức `void delete(String id, UserPrincipal principal)`:
       - Chỉ cho phép xoá khi `status == DRAFT` và chưa có `scores`.
   - `src/main/java/com/hcmute/lms/lmsapi/controller/AssessmentController.java`:
     - Xoá dependency `AssessmentApprovalService` và 3 endpoints `/submit`, `/approve`, `/reject`.
     - Thêm `POST /api/v1/assessments/{id}/lock`, `POST /api/v1/assessments/{id}/unlock`, `DELETE /api/v1/assessments/{id}`.
   - `src/main/java/com/hcmute/lms/lmsapi/service/ScopeAuthorizationService.java` & `service/impl/ScopeAuthorizationServiceImpl.java`:
     - Xoá phương thức `canReviewAssessment`.

6. **Nhóm Seeder & Tests (Cập nhật để pass 100%):**
   - `src/main/java/com/hcmute/lms/lmsapi/config/DemoDataSeeder.java` & `DemoDataSeederTest.java`: Bỏ `defaultWeight` khi seed CLO, bổ sung `weight` và `passThreshold` bắt buộc cho PLO/PI/CLO.
   - `src/test/java/com/hcmute/lms/lmsapi/dto/CloValidationTest.java`, `controller/CloIntegrationTest.java`, `controller/PloIntegrationTest.java`, `controller/PiIntegrationTest.java`: Cập nhật fixture có `passThreshold` và `weight`.
   - `src/test/java/com/hcmute/lms/lmsapi/service/impl/AssessmentItemValidatorTest.java`, `AssessmentCoverageCalculatorTest.java`, `controller/AssessmentIntegrationTest.java`: Cập nhật test cho `OutcomeMappingDto(cloId, maxScore)`, `cloWeights`, và luồng `DRAFT -> lock -> LOCKED -> unlock -> DRAFT`.

---

### Phase 2: Triển khai Import CSV CTĐT & Đề cương (`UNVERIFIED` $\to$ `VERIFIED` — Mở rộng F02 & F03)

1. **Import CSV Chương trình đào tạo (`POST /api/v1/program-versions/{pvId}/import-csv` & `POST .../verify`):**
   - **Files tạo mới trong `com.hcmute.lms.lmsapi`:**
     - `dto/ProgramCsvImportResponse.java`
     - `service/ProgramImportService.java` & `service/impl/ProgramImportServiceImpl.java`
   - **Xử lý:**
     - Đọc tệp CSV CTĐT: kiểm tra bắt buộc (không để trống) `passThreshold` của từng PLO/PI và `weight` ($W_{PI \to PLO}$) sao cho tổng các PI trong mỗi PLO = `1.0` ($100\%$).
     - Lưu vào CSDL với `verification.status = UNVERIFIED` (nếu đang có dữ liệu `UNVERIFIED` của lần import trước thì ghi đè khi người dùng sửa CSV rồi Import lại) và trả về dữ liệu để hiển thị lên giao diện kiểm tra.
     - Khi người dùng bấm **Lưu / Xác nhận (`POST .../verify`)**: Chuyển `verification.status = VERIFIED`, ghi nhận `verifiedBy`, `verifiedAt` và `AuditLog`.
2. **Import CSV Đề cương chi tiết học phần (`POST /api/v1/courses/{courseId}/syllabi/import-csv` & `POST .../verify`):**
   - **Files tạo mới trong `com.hcmute.lms.lmsapi`:**
     - `dto/SyllabusCsvImportResponse.java`
     - `service/SyllabusImportService.java` & `service/impl/SyllabusImportServiceImpl.java`
   - **Xử lý:**
     - Đọc tệp CSV Đề cương: trích xuất `syllabi`, danh sách `clos` (bắt buộc có `measurement.defaultPassThreshold` $\in (0, 1]$), và ma trận liên kết `clo_pi_links` (bắt buộc có `weight` $W_{CLO_{c,j} \to PI_m} \in (0, 1]$, không ép tổng qua các học phần bằng `1.0`).
     - Lưu ở trạng thái `UNVERIFIED` (nếu sai thì sửa CSV rồi Import đè lại); khi bấm Lưu xác nhận trên giao diện mới chuyển sang `VERIFIED`.

---

### Phase 3: Refactor F05b (từ PR #83) — Xuất tệp CSV Mẫu từ Ma trận đề đã khóa & Import Điểm CLO (Vắng/Trống = `0.0`)

Trên nền các file F05b vừa được PR #83 đưa vào `origin/main`, thực hiện điều chỉnh để khớp hoàn toàn với nghiệp vụ mới:

1. **Xoá các Enum không còn sử dụng của F05b cũ:**
   - Xoá `src/main/java/com/hcmute/lms/lmsapi/enums/ScoreStatus.java` và `src/main/java/com/hcmute/lms/lmsapi/enums/EmptyCellAs.java`.
2. **Cập nhật Document & Repository (`Score.java`, `ScoreRepository.java`):**
   - Trong `src/main/java/com/hcmute/lms/lmsapi/document/Score.java`:
     - Bổ sung trường bắt buộc `String cloId`.
     - Xoá `ScoreStatus status` và `String rubricLevel`.
     - Đặt `Double rawScore` luôn $\ge 0.0$ (không `null`; sinh viên vắng thi hoặc ô trống trong CSV đều nhận `rawScore = 0.0`).
     - Cập nhật `@CompoundIndex` thành `{ 'sectionId': 1, 'assessmentId': 1, 'itemId': 1, 'cloId': 1, 'studentId': 1 }`.
3. **Cập nhật Parser, Validator & Service (`ScoreCsvParser.java`, `ScoreRowValidator.java`, `ImportBatchServiceImpl.java`):**
   - **Xuất CSV mẫu (`GET /api/v1/assessments/{id}/scores/template-csv`):**
     - Kiểm tra bài đánh giá ở trạng thái `LOCKED` hoặc `SCORE_ENTERED`.
     - Sinh tiêu đề cột từ `assessment.items[].outcomeMappings[]` theo định dạng `MaSV,HoTen,<item.code> - <clo.code> (Max: <mapping.maxScore>)` kèm UTF-8 BOM (`\uFEFF`) và điền sẵn danh sách sinh viên có `status == ENROLLED` trong `enrollments`.
   - **Đọc CSV để xem trước & Lưu điểm (`ScoreCsvParser`, `ScoreRowValidator`):**
     - Đối sánh tiêu đề cột với cặp `(itemId, cloId, maxScore)` của ma trận đề đã khóa.
     - Nếu ô điểm trong CSV để trống $\to$ tự động quy về `0.0`. Kiểm tra $0.0 \le \text{rawScore} \le \text{mapping.maxScore}$.
     - Trả dữ liệu bảng điểm về giao diện để giảng viên kiểm tra trước (nếu sai giảng viên sửa tệp CSV rồi Import lại); khi giảng viên bấm **Lưu**, ghi vào collection `scores`, cập nhật `assessment.status = SCORE_ENTERED` và ghi `AuditLog`.

---

### Phase 4: Triển khai F06 & F07 — Engine Đo lường Chuẩn đầu ra Xuyên suốt 4 Cấp độ (`ASSESSMENT_CLO` $\to$ `CLO` $\to$ `PI` $\to$ `PLO`)

1. **Documents, Enums & Repositories (`com.hcmute.lms.lmsapi`):**
   - `enums/ResultLevel.java`: gồm 4 giá trị `ASSESSMENT_CLO`, `CLO`, `PI`, `PLO` (không có `COURSE`).
   - `document/CalculationRun.java` (không còn `ruleId` và `missingDataPolicy`) & `repository/CalculationRunRepository.java`.
   - `document/StudentResult.java` (có `String assessmentId` — khác `null` khi `level == ASSESSMENT_CLO`, bằng `null` khi `level` thuộc `CLO | PI | PLO`) & `repository/StudentResultRepository.java`.
   - `document/AggregateResult.java` (có `String assessmentId` nullable) & `repository/AggregateResultRepository.java`.
2. **Pure Calculation Engine (`service/calculation/`):**
   - `CloCalculator.java`:
     - **Cấp độ 1 (`level = ASSESSMENT_CLO`, `assessmentId = A_p.id`) — Mức đạt $CLO_j$ trên từng bài kiểm tra đơn lẻ $A_p$:**
       $$CLO_{s,p,j} = \frac{\sum_{k \in K_p(CLO_j)} Score_{s,p,k,j}}{\sum_{k \in K_p(CLO_j)} MaxScore_{p,k,j}} \in [0, 1], \qquad \text{Passed}(CLO_{s,p,j}) = \big( CLO_{s,p,j} \ge \text{PassThreshold}(CLO_j) \big)$$
     - **Cấp độ 2 (`level = CLO`, `assessmentId = null`) — Mức đạt $CLO_j$ trên toàn học phần (kiểm tra $\sum_{p \in P(CLO_j)} W_{p,j} = 1.0$):**
       $$Value(CLO_{s,j}) = \sum_{p \in P(CLO_j)} \big( W_{p,j} \times CLO_{s,p,j} \big) \in [0, 1], \qquad \text{Passed}(CLO_{s,j}) = \big( Value(CLO_{s,j}) \ge \text{PassThreshold}(CLO_j) \big)$$
   - `PiCalculator.java`:
     - **Cấp độ 3 (`level = PI`, `assessmentId = null`) — Tổng hợp $PI_m$ từ các $CLO_{c,j}$ thuộc một hoặc nhiều học phần:**
       - Nếu sinh viên $s$ học học phần $c$ nhiều lần, chọn kết quả $Value(CLO_{s,c,j})$ từ **lần học hợp lệ gần nhất (`LATEST`)**.
       - Tính trung bình có trọng số chuẩn hóa trên tập $\text{Evaluated}(s, PI_m)$ (lưu kèm `completeness.ratio` = $\sum_{(c, j) \in \text{Evaluated}(s, PI_m)} W_{CLO_{c,j} \to PI_m}$):
         $$Value(PI_{s,m}) = \frac{\sum_{(c, j) \in \text{Evaluated}(s, PI_m)} \Big( W_{CLO_{c,j} \to PI_m} \times Value(CLO_{s,c,j}) \Big)}{\sum_{(c, j) \in \text{Evaluated}(s, PI_m)} W_{CLO_{c,j} \to PI_m}} \in [0, 1]$$
       - So sánh trực tiếp với $\text{PassThreshold}(PI_m)$: $\text{Passed}(PI_{s,m}) = \big( Value(PI_{s,m}) \ge \text{PassThreshold}(PI_m) \big)$.
   - `PloCalculator.java`:
     - **Cấp độ 4 (`level = PLO`, `assessmentId = null`) — Tổng hợp $PLO_q$ từ các $PI_m$ trực thuộc:**
       $$Value(PLO_{s,q}) = \frac{\sum_{m \in \text{Evaluated}(s, PLO_q)} \big( W_{PI_m \to PLO_q} \times Value(PI_{s,m}) \big)}{\sum_{m \in \text{Evaluated}(s, PLO_q)} W_{PI_m \to PLO_q}} \in [0, 1]$$
       $$\text{Passed}(PLO_{s,q}) = \big( Value(PLO_{s,q}) \ge \text{PassThreshold}(PLO_q) \big)$$
   - `AggregateStatsCalculator.java`:
     - Ở Cấp độ 1 (`ASSESSMENT_CLO`) và Cấp độ 2 (`CLO`): chia cho mẫu số **$N_{\text{section}}$** (tổng số sinh viên `ENROLLED` của lớp học phần).
     - Ở Cấp độ 3 (`PI`) và Cấp độ 4 (`PLO`): chia cho mẫu số **$N_{\text{cohort}}$** (tổng số sinh viên thuộc khóa/CTĐT có dữ liệu trong đợt đánh giá).

---

## 4. Tiêu chí nghiệm thu (Definition of Done) cho đợt sửa đổi

- [x] Bản đặc tả gửi GVHD ([`doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`](dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md)) và Kế hoạch sửa đổi ([`doc/ke-hoach-sua-doi-theo-nghiep-vu-moi.md`](ke-hoach-sua-doi-theo-nghiep-vu-moi.md)) đã cập nhật đầy đủ:
  - Bỏ hoàn toàn cách tính điểm học vụ cũ (`weightInCourse`, `defaultWeight`, cấp `COURSE`, bảng `measurement_rules`, `ScoreStatus`).
  - `PassThreshold` của từng `CLO`, `PI`, `PLO` và trọng số đóng góp ($W_{CLO \to PI}$, $W_{PI \to PLO}$) bắt buộc không được để trống khi Import CSV (`UNVERIFIED` $\to$ `VERIFIED`).
  - Lưu trữ đầy đủ 4 cấp độ kết quả: `ASSESSMENT_CLO` (kèm `assessmentId`), `CLO`, `PI`, `PLO`.
  - Công thức tính `PI` từ `CLO` có chuẩn hóa mẫu số theo tập CLO đã học ($\text{Evaluated}$), lấy kết quả lần học gần nhất (`LATEST`) khi học lại, và tách biệt mẫu số $N_{\text{section}}$ với $N_{\text{cohort}}$.
  - Quy trình Git đồng bộ với `origin/main` (sau PR #83) để chống conflict 100%.
- [ ] Thực thi **Phase 0 & Phase 1**: Đồng bộ `database_import.js` (25 collections), xoá 13 file thừa, cập nhật F02/F03/F04, chạy `./mvnw test` xanh 100%.
- [ ] Thực thi **Phase 2, 3, 4**: Hoàn thiện Import CSV CTĐT & Đề cương (`UNVERIFIED` $\to$ `VERIFIED`), Refactor F05b (Xuất CSV mẫu & Import điểm CLO vắng = 0đ), và Engine F06/F07 (Đo lường 4 cấp độ `ASSESSMENT_CLO -> CLO -> PI -> PLO`).
