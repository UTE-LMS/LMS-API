# Kế hoạch xây dựng sản phẩm

Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra chương trình đào tạo (LOMS).
Phiên bản kế hoạch: **v0.3** (dự thảo, 2026-10-27, **đang ở tuần 5–6**; v0.2 ngày 2026-10-20). Người soạn: Claude Code theo
yêu cầu của sinh viên; **cần hai sinh viên và giảng viên hướng dẫn (GVHD) rà soát, chỉnh và chốt**.

**Thay đổi v0.2 → v0.3:** đồng bộ với thiết kế CSDL **v0.5** (`GiaiThich_CoSoDuLieu.md` §0, cập nhật theo
`PLO_DB_UPDATE.md`): thêm vai trò `DEPARTMENT_HEAD`, `STUDENT`; thêm quy trình Trưởng bộ môn duyệt ma trận đề thi (story
mới NEW-01); thêm 3 story mới NEW-02…04; sửa phạm vi "duyệt đề" thành "duyệt *nội dung* đề"; cập nhật module, số
collection (30, dùng 29), rủi ro R11, câu hỏi Q17–Q19, hiện trạng (đã có image MongoDB Docker). Danh sách tính năng
đề xuất chốt nằm ở `chot-feature-do-an.md`.

**Bản chia theo từng feature (để giao cho LLM hỗ trợ code): `doc/plan/README.md`.** File này là bản tổng.

Nguồn: `Rubric_Do_An_Mon_Hoc_CNPM_V2-1.docx.md`, `BS.md`, `Misa.md`, `GiaiThich_CoSoDuLieu.md` (v0.5),
`database_import.js`. Rule cho agent: `CLAUDE.md`, `.claude/rules/`.

**Đã chốt (người dùng xác nhận)**
- Hiện tại là **tuần 5** của kỳ 95 ngày làm việc (≈ 19 tuần).
- **ML nằm ngoài phạm vi.**
- **Ba học phần thử nghiệm để sau** (chưa chọn).
- Hai tài liệu lệch nhau thì **theo `GiaiThich_CoSoDuLieu.md`** (thiết kế CSDL).
- Hai sinh viên **không phân công cứng nhắc**, hỗ trợ chéo tự do.
- Thiết kế CSDL **v0.5** (theo `PLO_DB_UPDATE.md`) là bản hiện hành. Riêng ba điểm lệch phạm vi/nguyên tắc của v0.5 (duyệt
  ma trận đề thi, `departmentId`, `irmWeights`) **vẫn chờ GVHD/Khoa xác nhận** (Q11, Q17, Q18).

**Quy ước đọc**
- **[ĐỀ XUẤT]**: ý của người soạn, chưa được Khoa/GVHD xác nhận; không đưa vào hồ sơ như số liệu thật.
- **[CHỜ XÁC NHẬN]**: phụ thuộc câu hỏi Q01–Q23 (`GiaiThich_CoSoDuLieu.md` §8; Q17–Q19 mới từ v0.5; Q20–Q23 là công thức còn thiếu, xem `plan/F06-do-luong.md` §9) hoặc người có thẩm quyền.
- Mốc thời gian tính theo tuần của kỳ (T1…T19). Ngày cụ thể điền khi chốt lịch.

---

## 1. Mục tiêu và phạm vi

**Mục tiêu.** Hệ thống tiếp nhận kết quả có sẵn sau khi thi (điểm theo từng câu hỏi/tiêu chí) và đo lường mức
đạt theo chuỗi *câu hỏi/tiêu chí → CLO → PI → PLO → báo cáo → hành động cải tiến*, có phiên bản, truy vết
được về dữ liệu gốc, và luôn báo rõ phạm vi, mẫu số, số sinh viên chưa đủ dữ liệu.

**Trong phạm vi**
- CTĐT CNTT (1 chương trình), 7 PLO, 17 PI.
- Học phần thử nghiệm: **chưa chọn** (xem R1 và việc K1). Hệ thống phải hoạt động với bất kỳ học phần nào; phát
  triển và kiểm thử bằng dữ liệu giả có nhãn cho tới khi có học phần và dữ liệu thật.
- 8 nhóm chức năng F01–F08 (mục 3).

**Ngoài phạm vi (không làm)**: tổ chức thi, sinh đề, duyệt *nội dung* đề thi; xuất phôi in đề thi
(`assessments.exportedFiles`, trường có trong CSDL nhưng không có tính năng); lưu bài làm của sinh viên; kết luận mức
đạt của toàn chương trình; thay thế quy trình đo lường chính thức của Khoa; **thử nghiệm ML**; quản lý "minh chứng"
như một danh mục dữ liệu (chỉ lưu tệp điểm gốc và tham chiếu, theo thiết kế CSDL); quy đổi I/R/M thành hệ số.

**Điều chỉnh phạm vi theo CSDL v0.5 [CHỜ GVHD, Q11]:** *duyệt ma trận đề thi* (cấu trúc câu hỏi/tiêu chí và ánh xạ
CLO–PI của một bài đánh giá lớp) do Trưởng bộ môn duyệt trước khi mở cổng nhập điểm. Đây là duyệt ánh xạ chuẩn đầu
ra, không phải duyệt nội dung câu hỏi. Nếu GVHD không đồng ý thì giữ cơ chế khóa `items` nhưng bỏ bước Trưởng bộ
môn (giảng viên tự xác nhận). Thay đổi phạm vi này phải ghi ADR: bản nháp ở `adr/0001-duyet-ma-tran-de-thi.md`.

**Công nghệ đã chốt**: Java, Spring Boot 4.1.0, MongoDB (replica set), Angular 21 + TypeScript. Kiến trúc: một
ứng dụng backend phân module (modular monolith), Angular chỉ gọi API.

**Hiện trạng đầu T5** (đo từ repo ngày 2026-10-20)
- `LMS-API`: scaffold Spring Boot; `pom.xml` đã chuyển sang MongoDB; bộ tài liệu thiết kế và rule trong `doc/`
  (chưa commit). `LMS-UI`: scaffold Angular 21 chưa có màn hình.
- Lịch sử Git mỗi repo chỉ có 1 commit (2026-08-07, trước kỳ). Chưa có CI, test thực, README, convention.
- Chưa có bản cam kết Bước 3, báo cáo tiến độ, khảo sát, phân công trong repo.
- Nghĩa là **tiến độ code đang chậm so với kế hoạch lý tưởng**; kế hoạch dưới đây nén P0–P2 vào T5–T9.

**Cập nhật 2026-10-27:** đã có README, tài liệu quy ước, khung CI, image MongoDB replica set (`docker/mongo/`, nạp
`database_import.js` v0.5 tự động) nên hai việc P0 "bật MongoDB replica set" và "nạp `database_import.js`" đã xong ở
mức dev (xem mục 11). CSDL đã lên v0.5. Vẫn chưa có API nghiệp vụ, test thực, bản cam kết ký.

---

## 2. Kiến trúc đích

```
Angular (LMS-UI) ──HTTPS/JSON──> Spring Boot API ──> MongoDB (replica set)
                                      │
                                      ├─ Kho tệp điểm gốc (phương án do Trung tâm CNPM hướng dẫn)
                                      └─ Nhật ký thao tác (audit_logs) + log ứng dụng
```

**Module backend** (package theo chức năng; mỗi module có `controller → service → repository`, `dto`, `document`):

| Module | Collection | Chức năng |
|---|---|---|
| `auth` | `users` | F01 |
| `program` | `programs`, `program_versions`, `plos`, `pis`, `program_courses`, `course_pi_contributions` | F02 |
| `course` | `courses`, `syllabi`, `clos`, `clo_pi_links` | F03 |
| `assessment` | `question_banks`, `question_categories`, `questions`, `assessments` | F04, gồm quy trình duyệt ma trận đề thi (NEW-01) |
| `academic` | `terms`, `course_sections`, `students`, `enrollments` | hỗ trợ F05 |
| `scoreimport` | `import_batches`, `import_row_errors`, `scores` | F05 |
| `measurement` | `measurement_rules`, `calculation_runs`, `student_results`, `aggregate_results` | F06 |
| `report` | `reports` | F07 |
| `improvement` | `improvement_actions` | F08 |
| `audit` | `audit_logs` | xuyên suốt |

CSDL v0.5 có 30 collection; dùng **29 collection chính** (không dùng `ml_experiments`). Một số trường v0.5 có trong
CSDL nhưng chưa có tính năng: `assessments.exportedFiles`, `courses.departmentId` (chưa có collection bộ môn, Q17),
`pi.useIrmAsWeight`/`irmWeights` (engine luôn coi là `false`, Q18).

**Quyết định kiến trúc cần ghi ADR** (`doc/adr/`): cơ chế xác thực (JWT hay session); cách đọc tệp điểm
(Apache POI cho Excel, thư viện CSV); cách lưu tệp gốc; cách thực hiện transaction; chiến lược migrate/seed
CSDL (script `database_import.js` hay Mongock/Spring); công cụ báo cáo/xuất tệp; SSR của UI giữ hay bỏ.
Mỗi ADR nêu phương án đã cân nhắc (Rubric TC2.1 Mức 5).

---

## 3. Backlog theo epic và user story

Ưu tiên: **M** = Must (cam kết), **S** = Should, **C** = Could (chỉ làm khi còn thời gian). Cột "Chủ trì" là
**[ĐỀ XUẤT]** theo `Misa.md` §4 (IBS: nghiệp vụ, dữ liệu, kiểm chứng, báo cáo; IT: thiết kế kỹ thuật, lập trình,
kiểm thử kỹ thuật, triển khai). Đây chỉ là bên *chủ trì*, **hai bạn hỗ trợ chéo tự do**. Mỗi story phải có
acceptance criteria (AC) dạng Given/When/Then trước khi code (Rubric: 100% story có AC); phần dưới chỉ nêu trọng
tâm của AC.

### E01 — Người dùng và phân quyền (F01)
| ID | Story | Ưu tiên | Chủ trì | Trọng tâm AC |
|---|---|---|---|---|
| US-01 | Đăng nhập/đăng xuất | M | IT | Sai mật khẩu bị từ chối; mật khẩu chỉ lưu băm; tài khoản `LOCKED` không vào được |
| US-02 | Quản trị tạo người dùng, gán vai trò và phạm vi | M | IT, IBS (ma trận quyền) | 6 vai trò dùng trong cam kết: ADMIN, PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER, LECTURER, VIEWER (CSDL còn `STUDENT`, xem NEW-03); phạm vi `{programIds, courseIds, sectionIds}` |
| US-03 | Mọi API kiểm tra quyền theo phạm vi ở backend | M | IT | Giảng viên chỉ thấy/nhập điểm lớp mình phụ trách; truy cập trái phạm vi trả 403 |
| US-04 | Ghi nhật ký đăng nhập và thao tác quan trọng | S | IT | `audit_logs` có người, thời điểm, hành động, trước/sau, lý do |

### E02 — CTĐT, PLO, PI (F02)
| ID | Story | Ưu tiên | Chủ trì | Trọng tâm AC |
|---|---|---|---|---|
| US-05 | Quản lý chương trình và phiên bản CTĐT | M | IBS, IT | `(programId, version)` duy nhất; trạng thái DRAFT/ACTIVE/ARCHIVED; `totalCredits`, `durationYears` |
| US-06 | Quản lý 7 PLO và 17 PI theo **mã**, kèm nguồn, trang, trạng thái xác nhận | M | IBS | Mã sai định dạng (`^PLO[1-9][0-9]*$`, `^PI[1-9][0-9]*\.[1-9][0-9]*$`) bị từ chối; `displayOrder` tách khỏi mã; `category` GENERAL/SPECIALIZED/UNSPECIFIED; `capabilityLevelRaw` 1–6; nội dung nguyên văn chỉ điền khi đã đối chiếu |
| US-07 | Quản lý ma trận học phần đóng góp PI (I/R/M) | M | IBS | Ô trống = không có bản ghi; I/R/M không được dùng làm trọng số |
| US-08 | Quy trình xác nhận nội dung (UNVERIFIED → VERIFIED / NEEDS_CLARIFICATION) | S | IBS | Ghi người xác nhận và thời điểm |

### E03 — Học phần, CLO, liên kết CLO–PI (F03)
| ID | Story | Ưu tiên | Chủ trì | Trọng tâm AC |
|---|---|---|---|---|
| US-09 | Quản lý học phần và vị trí trong CTĐT | M | IBS, IT | Mã học phần duy nhất; tiên quyết; `type` là `COMPULSORY`/`ELECTIVE` |
| US-10 | Quản lý đề cương theo phiên bản | M | IBS | Học kỳ áp dụng; DRAFT/CONFIRMED/ARCHIVED |
| US-11 | Quản lý CLO thuộc một đề cương | M | IBS | `code` duy nhất **trong đề cương**; Bloom 1–6 |
| US-12 | Khai báo và xác nhận liên kết CLO–PI (nhiều–nhiều) | M | IBS | Hiển thị ma trận; chỉ liên kết đã VERIFIED được dùng cho tính chính thức; `weight` của liên kết (0 < x ≤ 1) để trống tới khi quy tắc xác nhận |

### E04 — Ngân hàng câu hỏi và bài đánh giá (F04)
| ID | Story | Ưu tiên | Chủ trì | Trọng tâm AC |
|---|---|---|---|---|
| US-13 | Quản lý ngân hàng và cây danh mục nội dung 3 cấp | S | IBS, IT | Cấp 1 không có cha; không vượt cấp 3; trùng mã bị từ chối |
| US-14 | Khai báo câu hỏi kèm ánh xạ nhiều cặp (CLO, PI, weight) | M | IBS | Tổng weight = 1 với câu ACTIVE; PI phải thuộc `clo_pi_links` của chính CLO; không trùng cặp |
| US-15 | Lập bài đánh giá theo đề cương (khung) và theo lớp (bản SECTION có `items[]`) | M | IBS, IT | Copy ánh xạ tại thời điểm thi; `totalPoints` khớp tổng `items.maxScore` (dung sai 0,001); bản `isLocked` (APPROVED trở đi) bị khóa `items` |
| US-16 | Khai báo tiêu chí rubric và mức rubric 1–4 | M | IBS | Mức rubric tách khỏi Bloom |
| US-17 | Nhập câu hỏi từ Excel/Moodle, chống nhập trùng | C | IT | Trùng `(bank, system, externalQuestionId)` bị từ chối **[CHỜ XÁC NHẬN Q16]** |
| NEW-01 | **Duyệt ma trận đề thi:** giảng viên gửi duyệt (`SUBMITTED`), Trưởng bộ môn duyệt/từ chối (`APPROVED`/`REJECTED`), khóa `items`, ghi `approval.history[]` và `audit_logs`; bảng độ phủ theo CLO và Bloom (`coverageSummary`) | M | IBS (quy trình), IT | Chỉ nhập điểm khi `APPROVED`/`SCORE_ENTERED`; gửi duyệt cần ≥ 1 item và `totalPoints` khớp; vai trò khác `DEPARTMENT_HEAD` không duyệt được; `PUT items` bị từ chối khi `isLocked` **[CHỜ GVHD, Q11]** |

### E05 — Học kỳ, lớp học phần, sinh viên
| ID | Story | Ưu tiên | Chủ trì | Trọng tâm AC |
|---|---|---|---|---|
| US-18 | Quản lý học kỳ và khóa sổ điểm | M | IT | OPEN/SCORE_LOCKED/CLOSED; khóa thì không nhập/sửa điểm |
| US-19 | Quản lý lớp học phần, giảng viên phụ trách | M | IBS, IT | Gắn đề cương và học kỳ |
| US-20 | Nhập danh sách lớp (CSV/Excel) → sinh viên và ghi danh | M | IT | Dùng `studentRef`; ghi danh là mẫu số **[CHỜ XÁC NHẬN Q09]** |

### E06 — Nhập điểm (F05)
| ID | Story | Ưu tiên | Chủ trì | Trọng tâm AC |
|---|---|---|---|---|
| US-21 | Tải tệp điểm và ánh xạ cột → câu hỏi/tiêu chí (lưu để dùng lại) | M | IT | Tính checksum, phát hiện nhập trùng |
| US-22 | Kiểm tra tệp, báo lỗi theo dòng và cột | M | IT | Mã sinh viên không hợp lệ, điểm vượt thang, trùng bản ghi, thiếu trường; lưu `import_row_errors` |
| US-23 | Chọn cách hiểu ô trống (BLANK hay MISSING) và chính sách nhập lại cho từng lô | M | IBS, IT | Hệ thống không tự đoán **[CHỜ XÁC NHẬN Q13]**. Chính sách nhập lại: `REJECT_ON_CONFLICT` / `OVERWRITE` / `UPDATE_BLANK_ONLY` **[CHỜ XÁC NHẬN Q19]** |
| US-24 | Xác nhận nhập (một transaction) hoặc rollback lô | M | IT | Lỗi giữa chừng không để lại điểm nửa vời; chỉ nhập khi bài đánh giá `APPROVED`/`SCORE_ENTERED`; ghi `audit_logs` cùng transaction |
| US-25 | Sửa điểm đơn lẻ có lý do | S | IT | Ghi audit trước/sau; khóa lạc quan `revision`; học kỳ đã khóa thì từ chối |

### E07 — Đo lường (F06)
| ID | Story | Ưu tiên | Chủ trì | Trọng tâm AC |
|---|---|---|---|---|
| US-26 | Quản lý quy tắc đo lường có phiên bản (DRAFT/CONFIRMED/RETIRED) | M | IBS (đặc tả), IT | Ngưỡng/trọng số để trống tới khi xác nhận; chỉ CONFIRMED cho kết quả chính thức; quy tắc tách theo cấp `clo`/`course`/`pi`/`plo`/`missingDataPolicy` **[CHỜ XÁC NHẬN Q04, Q12]** |
| US-27 | Tính CLO của từng sinh viên theo lớp | M | IT, IBS (kiểm chứng) | Kết quả tính tay độc lập khớp; trạng thái COMPUTED/INSUFFICIENT_DATA; thiếu dữ liệu ≠ 0; `Value(CLO)` = Σ điểm phần của CLO / Σ điểm tối đa phần của CLO (CSDL §4.1, xem `PLO_DB_UPDATE.md` §4.1) |
| US-28 | Tính PI và PLO từ kết quả cấp dưới | M | IT, IBS | Không suy mức PLO bằng trung bình mức PI; `inputs[]` ghi đủ đầu vào (một cấp liền kề, `sourceType` SCORE/STUDENT_RESULT). PI theo `DIRECT_FROM_ITEMS` (Q10); `FROM_CLOS` và các `aggregationMethod`/PLO method còn lại: xem NEW-04 **[CHỜ XÁC NHẬN Q19]** |
| US-29 | Tổng hợp cấp lớp/khóa/CTĐT kèm mẫu số | M | IT | `counts` đủ 5 số; luôn có `passRateOfEnrolled` |
| US-30 | Chống chạy trùng và đánh dấu kết quả cũ | S | IT | `lockKey` (chỉ mục duy nhất khi `PENDING`/`PROCESSING`); `isStale` khi điểm đổi sau lần tính |

### E08 — Báo cáo và truy vết (F07)
| ID | Story | Ưu tiên | Chủ trì | Trọng tâm AC |
|---|---|---|---|---|
| US-31 | Báo cáo CLO theo lớp học phần | M | IBS (nội dung), IT | Có phạm vi, quy tắc, phiên bản dữ liệu, `limitations` |
| US-32 | Báo cáo PI theo học phần/khóa và PLO theo CTĐT | M | IBS, IT | Ghi rõ "chưa đủ dữ liệu để kết luận toàn CTĐT" khi áp dụng |
| US-33 | Báo cáo độ đầy đủ dữ liệu | M | IBS | Liệt kê sinh viên/câu hỏi thiếu và lý do |
| US-34 | Truy vết từ kết quả → điểm → lô nhập → dòng tệp gốc | M | IT | Tối đa 3 thao tác từ kết quả đến dòng gốc **[ĐỀ XUẤT]** |
| US-35 | Xuất báo cáo ra Excel/PDF | S | IT | Bản xuất ghi trong `reports.exports` |
| US-36 | Dashboard tổng quan | S | IBS (chỉ số, cách đọc), IT | Chỉ hiển thị số API trả về, kèm mẫu số |

### E09 — Theo dõi cải tiến (F08)
| ID | Story | Ưu tiên | Chủ trì | Trọng tâm AC |
|---|---|---|---|---|
| US-37 | Tạo vấn đề và hành động cải tiến từ một kết quả/báo cáo | M | IBS, IT | Gắn CLO/PI/PLO; `rootCause`, `actionPlan`, `expectedTarget`; người phụ trách, hạn, học kỳ áp dụng, ưu tiên (LOW…CRITICAL) |
| US-38 | Theo dõi trạng thái OPEN → IN_PROGRESS → DONE → VERIFIED | M | IT | Lưu `history[]`; ghi chú hoàn thành |
| NEW-02 | Đánh giá hiệu quả hành động cải tiến ở chu kỳ đo kế tiếp (`evaluation`: `evaluationRunId`, `newPassRate`, `isEffective`) | S | IBS, IT | Nối với một lượt tính sau thời điểm hành động; không tự suy `isEffective` khi lượt tính chưa chính thức |

### E10 — Hệ thống và vận hành
| ID | Story | Ưu tiên | Chủ trì | Trọng tâm AC |
|---|---|---|---|---|
| US-39 | Health check, log tập trung, cảnh báo | M | IT | `/actuator/health`; cảnh báo khi lỗi |
| US-40 | Xử lý ngoại lệ thống nhất, trạng thái rỗng/lỗi/hết thời gian ở UI | M | IT | Mã lỗi nhất quán; UI hiển thị đủ trạng thái |

### Story mới do CSDL v0.5 (ngoài E01–E10)
| ID | Story | Ưu tiên | Chủ trì | Trọng tâm AC |
|---|---|---|---|---|
| NEW-03 | Vai trò `STUDENT`: sinh viên đăng nhập xem `student_results` của chính mình | S | IT | Nối `students.userId` ↔ `users`; chỉ đọc; không thấy kết quả của người khác (403) |
| NEW-04 | Mở rộng engine: `pi.method = FROM_CLOS`, `aggregationMethod` (`MAX`, `LATEST`, `ACCUMULATED_EVIDENCE`), phương pháp PLO còn lại (`ALL_PIS_PASSED`/`WEIGHTED_PIS`/`AVERAGE_PIS`) | C | IT, IBS (kiểm chứng) | CSDL đã hỗ trợ giá trị enum; mỗi phương pháp phải có ca tính tay độc lập; `useIrmAsWeight` luôn `false` **[CHỜ KHOA, Q18, Q19]** |

**Tổng: 44 story = 33 Must + 9 Should + 2 Could** (40 story gốc: 32 M + 7 S + 1 C; cộng NEW-01 là M, NEW-02 và
NEW-03 là S, NEW-04 là C; đã bỏ story ML). Tập Must (và phần Should hai bạn chọn) là ứng viên cho **bản cam kết Bước
3**. Rubric TC2.2 chấm theo đúng bản cam kết đó nên đừng cam kết quá sức: tuần 5 mà code mới là scaffold, 33 Must
trong ~10 tuần code là nặng cho hai người. **[ĐỀ XUẤT]** cân nhắc đưa vào cam kết chỉ Must + vài Should, và nói rõ
US-35/36 là mục tiêu mở rộng. Phương án cụ thể (32 story cam kết, US-13/14 hạ xuống mục tiêu mở rộng vì câu hỏi khai
báo thẳng trong `assessments.items[]` đã đủ để đo) nằm ở `chot-feature-do-an.md`, chưa ký.

---

## 4. Lộ trình theo giai đoạn (tính từ T5)

Nguyên tắc: **lát cắt dọc xuyên suốt** (mỗi giai đoạn có UI + API + test + tài liệu chạy được trên staging), không
làm hết backend rồi mới làm UI. CI/CD và staging dựng sớm để có ≥ 10 lần triển khai tự động trải đều (từ T7 đến T19
còn ~12 tuần, nên mục tiêu này vẫn đạt được nếu mỗi lần merge `main` tự triển khai).

| Giai đoạn | Tuần | Mục tiêu | Đầu ra chính (có minh chứng) |
|---|---|---|---|
| **P0 Nền tảng** | **T5–T6** | Repo, môi trường, CI sườn, quy ước | Commit toàn bộ tài liệu hiện có; MongoDB replica set chạy được; sửa `.gitignore` + gỡ track `application.properties`; quét secret; coding convention; bảo vệ nhánh `main`; README; CI chạy build + test; ADR đầu tiên (xác thực, migrate CSDL); nạp `database_import.js` |
| **P1 Khảo sát** | T5–T8 (song song) | Hiểu nhu cầu thật | Phỏng vấn ≥ 3 bên liên quan; so sánh ≥ 3 giải pháp; chân dung người dùng; trả lời Q01–Q23; danh mục MC01–MC09 với trạng thái |
| **P2 Danh mục lõi** | **T6–T9** | E01, E02, E03 | Đăng nhập + phân quyền; CTĐT/PLO/PI/ma trận I/R/M; học phần/đề cương/CLO/liên kết CLO–PI; UI quản lý; **staging đầu tiên (mục tiêu T7)** |
| **K1: cam kết Bước 3** | **mục tiêu hết T7, hạn cuối T10** | Chốt sản phẩm và metric có chữ ký | `doc/cam-ket-san-pham-metric.md` (mục 8) |
| **P3 Đánh giá và nhập điểm** | T8–T11 | E04 (kể cả NEW-01), E05, E06 | Ngân hàng câu hỏi, bài đánh giá, ánh xạ, duyệt ma trận đề thi; học kỳ/lớp/ghi danh; nhập tệp, báo lỗi, xác nhận/rollback; sửa điểm có audit |
| **P4 Đo lường** | T10–T13 | E07 | Rule engine cấu hình; tính CLO → PI → PLO; tổng hợp có mẫu số; truy vết `inputs[]`; so sánh với kết quả đối chứng nếu có |
| **P5 Báo cáo và cải tiến** | T12–T15 | E08, E09 | Báo cáo CLO/PI/PLO/độ đầy đủ; drill-down; xuất tệp; dashboard; theo dõi cải tiến |
| **P6 Củng cố chất lượng** | T14–T16 | Phi chức năng, bảo mật, hiệu năng, coverage | E2E, hiệu năng theo NFR; quét phụ thuộc; sửa lỗi Critical; rà sơ đồ khớp mã |
| **P7 Thực nghiệm người dùng** | T15–T17 | TC2.7 | ≥ 10 người dùng thật; kịch bản tác vụ; SUS; ≥ 1 vòng cải tiến + đo lại trước–sau |
| **P8 Luận văn và bảo vệ** | T16–T19 | TC3, TC4, TC5 | Luận văn; kiểm tra trùng lặp; slide; diễn tập demo; bộ câu hỏi vấn đáp |

**Đóng băng tính năng: cuối T15.** Sau đó chỉ sửa lỗi và hoàn thiện tài liệu.

### Cổng kiểm soát (gate)

| Cổng | Khi nào | Điều kiện | Nếu không đạt |
|---|---|---|---|
| **K1** | Mục tiêu T7, hạn T10 | Có bản cam kết sản phẩm và metric có chữ ký. Nên đã **chọn ba học phần thử nghiệm** để phạm vi thử nghiệm nằm trong cam kết | Dừng đề tài (G1). Không có ngoại lệ |
| **K2** | Cuối T9 | CTĐT/PLO/PI nhập thật; staging chạy; CI xanh; ≥ 3 lần triển khai tự động | Giảm Should của P3 |
| **K3** | Cuối T10 | Khoa đã xác nhận (hoặc từ chối) quy tắc đo; có dữ liệu điểm thật hoặc quyết định dùng dữ liệu thử nghiệm | Kích hoạt phương án dự phòng ở R1 |
| **K4** | Cuối T13 | Tính CLO→PI→PLO chạy đúng với bộ kiểm chứng; đủ để làm báo cáo | Cắt bớt dashboard/xuất PDF |
| **K5** | Cuối T15 | Đóng băng tính năng; coverage lõi ≥ 70%; 0 defect Critical/Blocker | Hoãn thực nghiệm; chỉ sửa lỗi |
| **K6** | Cuối T17 | Thực nghiệm xong, có số liệu thật | Bắt buộc có (G7) |

---

## 5. Cách phối hợp hai sinh viên

Không phân công cứng nhắc; IBS/IT chỉ là bên *chủ trì* (cột "Chủ trì" ở mục 3). Hai bạn hỗ trợ chéo tự do, nhưng
để vẫn đáp ứng Rubric (Bước 2: phân công theo thành viên; hội đồng hỏi riêng từng người) cần giữ **ba thứ nhẹ**:

1. **Bản khung phân công** `doc/quy-trinh/phan-cong.md`: mỗi epic ai *chủ trì*, ai *hỗ trợ*. Một trang là đủ.
2. **Dấu vết đóng góp thật**: mỗi người commit bằng danh tính Git của mình; PR do người này mở thì người kia review;
   AI Usage Log ghi đúng sinh viên của từng phiên.
3. **Cả hai giải thích được mô-đun lõi** (đo lường, nhập điểm, phân quyền, truy vết): mỗi tuần, người *không* viết
   một mô-đun tự giải thích lại cho người viết nghe (diễn tập vấn đáp 4 câu của Mục 6.2).

---

## 6. Chiến lược chất lượng và kiểm thử

**Definition of Done cho một story**
1. Có AC viết xong trước khi code, đã được người còn lại đọc.
2. Mã qua lint, phân tích tĩnh; có test (ca thuận, ca âm, ca biên) gắn AC.
3. PR có review của sinh viên còn lại; CI xanh.
4. Tài liệu/sơ đồ bị ảnh hưởng đã cập nhật; ADR nếu có đổi thiết kế.
5. Chạy được trên staging; không có secret hay dữ liệu thật.
6. Mục AI Usage Log cho phiên đó đã ghi; người viết giải thích được từng đoạn.

**Tầng kiểm thử (mục tiêu ≥ 4)**

| Tầng | Công cụ | Phạm vi trọng tâm |
|---|---|---|
| Unit | JUnit 5, Mockito; Vitest (UI) | Rule engine, tính CLO/PI/PLO, kiểm tra điểm, kiểm tra ánh xạ |
| Integration | Spring Boot Test + Testcontainers MongoDB (replica set) | Transaction nhập điểm, chỉ mục duy nhất, `$jsonSchema` |
| API | MockMvc/RestAssured | Phân quyền theo phạm vi, mã lỗi, validation |
| E2E/UI | Playwright | Luồng chính: nhập điểm → tính CLO → xem báo cáo → truy vết |
| Phi chức năng | k6 hoặc JMeter; quét bảo mật | Theo NFR (mục 8) |

**Bộ kiểm chứng phép tính độc lập.** Một bạn tính tay (hoặc bằng bảng tính riêng) một số ca: 1 lớp nhỏ, có sinh viên
vắng/chưa chấm/được miễn, câu đo nhiều cặp CLO–PI, ca biên sát ngưỡng. Bạn kia lập trình phép tính; hai kết quả phải
khớp. Số trong ví dụ của tài liệu (60%×0,4 + 80%×0,6 = 72%) chỉ là **ví dụ minh họa**, không phải quy tắc.

**Mục tiêu (Mức 5)**: lint 0; Blocker/Critical 0; trùng lặp ≤ 3%; coverage lõi ≥ 70%; tự động hóa ≥ 70%;
100% test pass ở lần chạy cuối; 0 defect Critical/Blocker khi bảo vệ.

## 7. CI/CD và vận hành

Pipeline ≥ 6 chặng: **build → lint/phân tích tĩnh → test + cổng coverage → quét bảo mật/phụ thuộc (gitleaks,
OWASP Dependency-Check/npm audit, Trivy) → đóng gói Docker + gắn phiên bản → triển khai tự động staging**, sau
đó health check. Rollback bằng image phiên bản trước. Secret qua cơ chế của nền tảng. Môi trường do Trung tâm CNPM
hướng dẫn **[CHỜ XÁC NHẬN Q06, Q07]**.

Mốc: pipeline chạy xanh cuối T6; staging đầu tiên T7; từ T7 mỗi lần merge `main` tự triển khai; cảnh báo và log tập
trung xong trước T14. Mục tiêu: build xanh ≥ 90%, commit → staging ≤ 15 phút.

---

## 8. Việc phải làm trước khi chốt bản cam kết Bước 3 (K1)

`doc/cam-ket-san-pham-metric.md` cần các phần sau (tôi sẽ soạn khung khi hai bạn sẵn sàng):

1. Mục tiêu, người dùng, phạm vi làm/không làm (có ghi ML ngoài phạm vi).
2. **Tập story cam kết** (Must + Should đã chọn) và khối lượng của từng sinh viên (mức khung).
3. **≥ 5 KPI nghiệp vụ định lượng** **[ĐỀ XUẤT]** (số cụ thể do hai bạn và GVHD đặt):
   - độ khớp giữa kết quả hệ thống và kết quả tính độc lập trên bộ kiểm chứng (đích: 100% trong dung sai thỏa thuận);
   - tỷ lệ lỗi nhập điểm cài sẵn trong bộ thử mà hệ thống phát hiện đúng;
   - thời gian từ nhập tệp điểm đến có báo cáo CLO của một lớp (so với quy trình hiện tại, **cần đo cơ sở**);
   - số thao tác để truy vết từ một kết quả về dòng điểm gốc;
   - tỷ lệ hoàn thành tác vụ chính của người dùng thật và điểm SUS;
   - tỷ lệ câu hỏi/tiêu chí có ánh xạ CLO–PI đã xác nhận trong các học phần thử nghiệm.
4. **≥ 5 yêu cầu phi chức năng có ràng buộc định lượng** **[ĐỀ XUẤT, cần điều chỉnh theo thực tế máy chủ]**:
   - nhập và kiểm tra tệp ~5.000 dòng trong ≤ 10 giây;
   - tính CLO cho một lớp ~60 sinh viên trong ≤ 5 giây;
   - API đọc p95 ≤ 500 ms với 50 người dùng đồng thời;
   - mật khẩu băm (bcrypt/argon2), 100% endpoint kiểm tra quyền, không có lỗ hổng mức cao trong quét phụ thuộc;
   - khả dụng staging ≥ 99% trong giai đoạn thực nghiệm;
   - lỗi hệ thống không để lại dữ liệu nửa vời (kiểm tra bằng test transaction).
5. Bộ metric đánh giá (ngưỡng Mục 5 rubric, có thể chỉnh theo đặc thù) và hình thức nghiệm thu.
6. Chữ ký sinh viên và GVHD.

---

## 9. Rủi ro và phương án

| # | Rủi ro | Mức | Phương án |
|---|---|---|---|
| R1 | Khoa chậm hoặc không cung cấp đề cương, quy tắc đo, điểm theo câu hỏi (MC02–MC08); **ba học phần thử nghiệm chưa chọn** | **Cao** | Rule engine cấu hình; dev/test bằng dữ liệu giả **có nhãn**; chọn học phần càng sớm càng tốt (nên trước K1) và quyết định xong trước K3. Nếu tới K3 vẫn thiếu: chạy ở chế độ không chính thức, ghi rõ giới hạn trong báo cáo và luận văn. Không bịa dữ liệu hay công thức |
| R2 | Điểm chỉ có tổng, không có theo câu hỏi (Q05) | Cao | `dataGranularity = TOTAL_ONLY` có trong thiết kế nhưng **không đo được CLO/PI**; thảo luận với GVHD để đổi phạm vi bằng ADR trước K1 |
| R3 | Không đủ ≥ 10 người dùng thật hoặc ≥ 3 bên liên quan | Trung bình | Lên danh sách và hẹn ngay từ T5; báo sớm cho GVHD nếu thiếu |
| R4 | Transaction cần replica set; môi trường staging không hỗ trợ | Trung bình | Kiểm tra ở P0; nếu không có, đổi cách đảm bảo nhất quán bằng ADR |
| R5 | Phiên bản mới (Java 25, Spring Boot 4.1, Angular 21): tài liệu/công cụ chưa theo kịp | Trung bình | Dùng tài liệu chính thức đúng phiên bản; thử Sonar/JaCoCo sớm ở P0; cân nhắc Java LTS 21 sau khi hỏi Trung tâm CNPM |
| R6 | Phạm vi phình to (dashboard, xuất PDF, đồng bộ Moodle) | Trung bình | Chỉ Must/Should; Could chỉ khi còn dư lực; mọi thay đổi ghi ADR và xin phép |
| R7 | Một sinh viên làm hết phần lõi, người kia không giải thích được (V2/V3) | Cao | Review chéo bắt buộc; diễn tập vấn đáp hằng tuần (mục 5) |
| R8 | **Lịch sử Git thiếu các tuần đầu.** Mỗi repo chỉ có 1 commit trước kỳ, nên nếu T1–T4 không có commit thì tối đa 15/19 ≈ 79% số tuần có commit, đạt Mức 4 (≥ 75%) chứ không đạt Mức 5 (≥ 90%) cho chỉ số này | Trung bình | Commit đều từ T5 trở đi. Không sửa ngày commit hay tạo commit giả để "lấp" các tuần trước. Nếu đã có công việc ở T1–T4 (đọc tài liệu, `doc/`), commit chúng ngay với ngày thật; báo cáo tiến độ T1–T4 (nếu có) là minh chứng bổ sung |
| R9 | Lộ dữ liệu sinh viên hoặc secret | Cao | `studentRef`, dữ liệu giả, gitleaks trong CI, `.gitignore` đúng |
| R10 | **Chậm tiến độ**: T5 mà code mới là scaffold; P0–P2 bị nén còn 5 tuần | Cao | Giữ thứ tự: nền tảng → K1 → danh mục lõi; ưu tiên Must; hỗ trợ chéo để không chờ nhau; cắt Should trước, rồi tới Could, không cắt kiểm thử |
| R11 | **CSDL v0.5 chạm phạm vi:** duyệt ma trận đề thi (NEW-01), `exportedFiles`, `irmWeights`, `departmentId` đi ngoài phạm vi "không duyệt đề" và nguyên tắc "I/R/M ≠ trọng số" (Q11, Q17, Q18). Nếu GVHD/Khoa không đồng ý, mã và test của NEW-01 phải sửa | Trung bình | Hỏi GVHD trước khi code NEW-01 (hạn trước P3, T8). Phương án dự phòng đã ghi ở mục 1: bỏ bước Trưởng bộ môn, giữ khóa `items`. `irmWeights`: engine luôn coi `useIrmAsWeight = false`. Ghi ADR (`adr/0001`) |
| R12 | **Công thức đo lường còn thiếu:** `PLO_DB_UPDATE.md` chỉ có công thức CLO (§4.1) và PI (§4.2). Chưa có công thức cấp PLO, cấp học phần khi `requireAllCloPass = false`, `WEIGHTED_ITEMS`, gộp PI qua nhiều học phần, định nghĩa `minCoverage`/mẫu số, ưu tiên ngưỡng (Q20–Q23; danh sách CT-01…CT-11 ở `plan/F06-do-luong.md` §9) | **Cao** | Hỏi GVHD/Khoa sớm (trước P4, T10); trong lúc chờ chỉ cài CLO và PI theo công thức đã có, phần còn lại trả 422, không tự bịa. Nếu không có trả lời trước K3: báo cáo PLO ở chế độ không chính thức hoặc cắt phạm vi PLO bằng ADR |

---

## 10. Nhịp làm việc hằng tuần

- **Đầu tuần**: chọn story từ backlog; viết/duyệt AC.
- **Trong tuần**: nhánh theo story → PR → review chéo → CI xanh → merge → tự triển khai staging.
- **Cuối tuần**: báo cáo tiến độ với GVHD (nội dung đã làm, vướng mắc, kế hoạch tuần sau); cập nhật
  `doc/rubric-checklist.md`; ghi AI Usage Log; rà nợ kỹ thuật (`doc/tech-debt.md`); diễn tập vấn đáp một mô-đun.
- **2 tuần/lần**: GVHD xem xét và duyệt báo cáo tiến độ.

---

## 11. Việc cần làm ngay (T5–T6)

| # | Việc | Ai |
|---|---|---|
| 1 | Commit tài liệu hiện có (`doc/`, `CLAUDE.md`, `.claude/rules/`, `logsAI/`) lên nhánh, mở PR để người kia review | một bạn mở, bạn kia review |
| 2 | Mỗi người cấu hình `git user.name/email` riêng (lịch sử hiện có tác giả khác với tài khoản đang cấu hình trên máy này) | cả hai |
| 3 | ~~Bật MongoDB replica set (dev)~~ **Xong ở mức dev:** `docker/mongo/` (image `lms-mongo`, replica set `rs0`, nạp `database_import.js` v0.5); staging/CI còn phải kiểm tra (R4) | bạn nào thuận tay |
| 4 | Sửa `.gitignore`, gỡ track `application.properties`, chuyển cấu hình sang biến môi trường | |
| 5 | Quyết bỏ/giữ `websocket`, `mail` trong `pom.xml`; quyết SSR của UI; quyết Java 25 hay LTS | cả hai, hỏi Trung tâm CNPM |
| 6 | Dựng CI sườn (build + test), quét gitleaks, bảo vệ nhánh `main` (≥ 1 review) | |
| 7 | Viết coding convention và `README` | |
| 8 | Chốt bản khung phân công `doc/quy-trinh/phan-cong.md` (một trang) | cả hai |
| 9 | Gửi lại danh mục MC01–MC09 và Q01–Q23 cho đầu mối của Khoa, kèm hạn; đề xuất chọn ba học phần. Hỏi GVHD riêng về Q11 (duyệt ma trận đề thi), vì ảnh hưởng NEW-01 | |
| 10 | Lên danh sách ≥ 3 bên liên quan để phỏng vấn, ≥ 10 người dùng cho thực nghiệm | |
| 11 | Đối chiếu bản CTĐT gốc để trích đủ 7 PLO/17 PI nguyên văn | |
| 12 | Bắt đầu viết SRS/AC cho tập Must, nháp bản cam kết K1 | |
| 13 | Bắt đầu code US-01…US-03 (đăng nhập, phân quyền) để có staging đầu tiên vào T7 | |

---

## 12. Quyết định còn mở

1. **Ngày bắt đầu chính xác của kỳ** và ngày bảo vệ dự kiến (để quy T-n ra ngày).
2. **F08 (theo dõi cải tiến)** có nằm trong cam kết không (kế hoạch đang xếp là Must).
3. **Khi nào chọn ba học phần thử nghiệm** và có dữ liệu điểm theo câu hỏi hay không (R1, R2). Đề xuất: trước K1.
4. Tập story đưa vào bản cam kết Bước 3 (mục 3, cuối).
5. Con số KPI và NFR (mục 8).
6. Java 25 hay LTS; nơi đặt staging; SSR của UI.
7. **NEW-01 (duyệt ma trận đề thi) có nằm trong phạm vi không** (Q11) và có giữ vai trò `DEPARTMENT_HEAD`; có cần
   collection `departments` (Q17). Hỏi GVHD trước P3.
8. Phương pháp mặc định của engine: `pi.aggregationMethod`, `plo.method`, `reimportPolicy` (Q19); có cho quy đổi I/R/M
   không (Q18, mặc định không).
9. **Công thức đo lường còn thiếu (Q20–Q23):** cấp PLO, cấp học phần khi `requireAllCloPass = false`, `WEIGHTED_ITEMS`, gộp PI qua nhiều học phần, thiếu dữ liệu/ngưỡng trong công thức. Danh sách đầy đủ để hỏi GVHD: `plan/F06-do-luong.md` §9. Hạn đề xuất: trước P4 (T10).
