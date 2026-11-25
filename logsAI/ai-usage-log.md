# Nhật ký sử dụng AI (AI Usage Log)

Phục vụ Rubric TC2.3 và Mục 8 #5. Nhật ký của repo `LMS-API`; repo `LMS-UI` có nhật ký riêng tại `D:\TLCN\LMS-UI\logsAI\ai-usage-log.md` (cùng định dạng). Khi nộp, gộp hai nhật ký.
Quy tắc ghi: `.claude/rules/01-ai-usage-and-ownership.md`.

- Ghi đúng sự thật; kê khai phải khớp `git log` và phần vấn đáp (mâu thuẫn = G4).
- Agent điền các cột về công cụ, phạm vi, prompt, file. Cột **"Sinh viên đã sửa/rà soát"** và **"Commit"** do sinh viên điền sau khi đọc và hiểu mã.
- Mỗi vị trí mã AI sinh phải được sinh viên giải thích được (mục tiêu 100% khi hội đồng chọn ≥ 5 vị trí).
- Cột "Sinh viên": `git config user.name` hiện là `TinMH`, chưa rõ là IBS hay IT nên để _chờ điền_.
- Cột "Prompt chính" chỉ tóm tắt yêu cầu, không chép nguyên văn câu lệnh. Ngày 2026-10-27 nhật ký được gộp từ 14 dòng thành 8; bản 13 dòng gần nhất nằm ở commit `ee14d8f` (`git show ee14d8f:logsAI/ai-usage-log.md`). Ngày 2026-10-28 rút gọn cột prompt và phạm vi dòng 10.

## A. Nhật ký phiên làm việc

| # | Ngày | Sinh viên | Repo | Công cụ / mô hình | Phạm vi | Prompt chính (tóm tắt) | File AI sinh/sửa | Sinh viên đã sửa/rà soát | Kiểm chứng đã làm | Commit |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-20 | _chờ điền_ | LMS-API, LMS-UI | Claude Code, claude-sonnet-5-5 | Soạn và chỉnh bộ rule cho agent (5 lượt yêu cầu trong ngày): rule theo Rubric; chốt công nghệ; chuyển nhật ký sang `logsAI/`; đồ án nhóm 2 sinh viên; mỗi repo một nhật ký; bảng theo dõi minh chứng | Đặt rule để agent không vi phạm Rubric; chốt công nghệ (Angular hiện đại, không AngularJS 1.x); log để trong `logsAI/`; repo làm chung 2 sinh viên | `CLAUDE.md`, `LMS-UI/CLAUDE.md`, `.claude/rules/01…05`, `logsAI/ai-usage-log.md`, `doc/rubric-checklist.md` | _Chờ sinh viên rà soát_ | Đối chiếu `Rubric…md` (ngưỡng, mã G/V), `BS.md` §2, `Misa.md` §4 và §9, `pom.xml`, `package.json`; grep không còn đường dẫn log cũ. Chưa chạy build/test vì chưa có mã | PR #1 (nhánh `chore/project-setup`); sinh viên đối chiếu từng mục với `bca824c`, `9734703` |
| 2 | 2026-10-20 | _chờ điền_ | LMS-API | Claude Code, claude-sonnet-5-5 | Chuyển `pom.xml` từ JPA/PostgreSQL sang MongoDB | "sửa pom.xml sang MongoDB đi" | `pom.xml`, `CLAUDE.md` | _Chờ sinh viên rà soát_ | Artifact `spring-boot-starter-data-mongodb` và `-mongodb-test` 4.1.0 có trên Maven Central; `mvnw test` (Java 25.0.1): context khởi động, Spring Data MongoDB được nạp (chỉ có test mẫu `contextLoads`) | PR #1 |
| 3 | 2026-10-20 | _chờ điền_ | LMS-API, LMS-UI | Claude Code, claude-sonnet-5-5 | Soạn kế hoạch xây dựng `doc/ke-hoach-xay-dung.md` (v0.1) rồi nâng v0.2: đang tuần 5, bỏ ML, hỗ trợ chéo, lệch tài liệu thì theo CSDL; đồng bộ rule | Lập kế hoạch xây dựng từ các file doc; đang tuần 5, bỏ ML, không phân công cứng, lệch tài liệu thì theo CSDL | `doc/ke-hoach-xay-dung.md`, `CLAUDE.md`, `LMS-UI/CLAUDE.md`, `.claude/rules/02…`, `05…` | _Chờ sinh viên rà soát. Số NFR/KPI và phân công IBS/IT là đề xuất của AI_ | Đối chiếu F01–F08, 30 collection, Q01–Q16, ngưỡng Mức 5; đọc `git log` hai repo (mỗi repo 1 commit ngày 2026-08-07); đếm lại 40 story (32 M + 7 S + 1 C); grep `ml_experiments` trong `database_import.js` (có khai báo, chưa dùng). Không chạy mã | PR #1 |
| 4 | 2026-10-20 | _chờ điền_ | LMS-API | Claude Code, claude-sonnet-5-5 | Soạn dự thảo K1 và hồ sơ hỗ trợ; sửa `.gitignore`; README; khung CI | "bạn hãy làm theo hướng bạn nghĩ tối ưu nhất" | `doc/cam-ket-san-pham-metric.md`, `doc/quy-trinh/phan-cong.md`, `doc/coding-convention.md`, `doc/tech-debt.md`, `README.md`, `.gitignore`, `src/main/resources/application.properties.example`, `.github/workflows/ci.yml`; gỡ `application.properties` khỏi git index | _Chờ sinh viên rà soát. K1, phân công, convention là DỰ THẢO; KPI/NFR là đề xuất của AI_ | Chạy thực nghiệm xác định property Mongo của Spring Boot 4 (xem B1); `mvnw verify` BUILD SUCCESS; `git check-ignore`: `application.properties` bị ignore, `.example` thì không. **Chưa kiểm chứng:** cú pháp và lần chạy thật của `ci.yml` trên GitHub | PR #1 (`619baa0` là workflow CI) |
| 5 | 2026-10-21 | _chờ điền_ | LMS-API | Claude Code, claude-sonnet-5-5 | Tóm tắt kế hoạch thành `doc/tom-tat-du-an-de-thao-luan.md` để hai sinh viên thảo luận | Tóm tắt đầu ra của dự án thành file .md để thảo luận | `doc/tom-tat-du-an-de-thao-luan.md` | _Chờ sinh viên rà soát. Chỉ tổng hợp lại tài liệu có sẵn; mục [ĐỀ XUẤT]/[CHỜ XÁC NHẬN] giữ nguyên_ | Đọc 4 file nguồn; đếm lại story và collection; ghi vào mục thảo luận 2 chỗ lệch giữa kế hoạch và bản cam kết (tên K1–K6 trùng giữa cổng và KPI; 6 so với 7 KPI). **Chưa kiểm chứng:** phần "hiện trạng" chưa đối chiếu lại `pom.xml` và CI | `947cf04` |
| 6 | 2026-10-23 | _chờ điền_ | LMS-API | Claude Code, claude-sonnet-5-5 | Dựng MongoDB replica set bằng Docker, nạp `database_import.js`, đóng gói image; cập nhật README | Dựng MongoDB trên Docker, nạp data trong doc; đóng gói image, cập nhật README | `docker/mongo/Dockerfile`, `entrypoint.sh`, `Dockerfile.dockerignore`, `.gitattributes`, `README.md`, `application.properties.example` | _Chờ sinh viên rà soát. Cổng mặc định, tag `mongo:8.0`, không bật xác thực là lựa chọn của AI cho môi trường dev_ | Build image; `rs.status` ra PRIMARY; script nạp 30 collection (7 PLO, 17 PI); khởi động lại không nhân đôi dữ liệu; `LOMS_DB`/`LOMS_SAMPLE` có tác dụng; `docker stop` thoát mã 0 (xem B2). `mvnw spring-boot:run` với `MONGODB_URI` cổng 27018: driver thấy `REPLICA_SET_PRIMARY` của `rs0`, Tomcat lên cổng 8080. **Chưa kiểm chứng:** mặc định cổng 27017 (máy có MongoDB cục bộ chiếm cổng); app chưa có repository nên chưa đọc dữ liệu thật; chưa thử trên Linux/macOS | PR #2 (`cbf22ac`, `b1cc1bb`) |
| 7 | 2026-10-23 | _chờ điền_ | LMS-API | Claude Code, claude-sonnet-5-5 | Cập nhật CSDL lên v0.5 theo `PLO_DB_UPDATE.md`; chốt danh sách feature đồ án | Sửa CSDL theo `PLO_DB_UPDATE.md`; tạo file chốt feature | `doc/database_import.js`, `doc/GiaiThich_CoSoDuLieu.md` (v0.4 → v0.5), `doc/chot-feature-do-an.md` (mới) | _Chờ sinh viên rà soát. Các điểm lệch phạm vi/nguyên tắc (duyệt ma trận đề thi, `irmWeights`, `departmentId`) và danh sách C/MR/K là đề xuất của AI, chưa được GVHD/Khoa duyệt_ | Chạy `database_import.js` hai lần trên MongoDB 8.0 (30 collection, 62 chỉ mục, không nhân đôi seed) và 59 ca chấp nhận/từ chối validator: 59 đạt. Bổ sung ngoài `PLO_DB_UPDATE.md`: `calculation_runs.scopeRef`, chỉ mục khóa chạy theo `PENDING`/`PROCESSING`, `scope.cohort`/`termId` trong `aggregate_results`. **Chưa kiểm chứng:** MongoDB 8.2.6 (chỉ thử 8.0) | `9977777`, `947cf04` |
| 8 | 2026-10-27 | _chờ điền_ | LMS-API | Claude Code, claude-sonnet-5-5 | (a) Đồng bộ kế hoạch, cam kết, rule, README với CSDL v0.5 và thêm ADR 0001 (nháp); (b) tách commit và push `feat/docker-mongo`; (c) tách kế hoạch thành `doc/plan/` theo feature để giao cho LLM; (d) ghi công thức `PLO_DB_UPDATE.md` §4 làm công thức triển khai và liệt kê công thức còn thiếu (CT-01…CT-11, Q20–Q23) để hỏi GVHD; (e) gộp nhật ký này | Cập nhật doc/plan/README theo CSDL mới và import lại DB; tách commit, push; chia plan theo feature; ghi công thức còn thiếu để hỏi GVHD; xóa log thừa | `doc/ke-hoach-xay-dung.md` (v0.3), `tom-tat-…`, `cam-ket-…`, `chot-feature-…`, `GiaiThich` §8 (Q20–Q23), `CLAUDE.md`, `.claude/rules/02…`, `doc/adr/0001-…` (mới), `README.md`, `doc/plan/` (11 file mới), `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát. AC, API, luồng trạng thái trong `doc/plan/` và ADR 0001 là bản nháp của AI. Danh sách CT-01…CT-11 có thể còn sót, IBS rà lại_ | Image `lms-mongo` build lại, chạy container tạm cổng 27019: 30 collection, 92 chỉ mục (62 + 30 `_id`), `plos.category`, `program_versions.totalCredits`, validator từ chối `APPROVED` với `isLocked=false` (mã 121), chấp nhận `DEPARTMENT_HEAD`; container tạm đã dừng. Tính tay lại ví dụ công thức bằng awk (CLO1 0,8; CLO2 0,57778; PI1 0,8; PI2 0,52727; PI1 FROM_CLOS 0,66667) khớp. Quét secret thủ công trên các dòng thêm (chưa cài gitleaks). Lỗi AI: xem B3–B5. **Chưa làm:** dựng lại container `lms-mongo` chính (lệnh xóa container/volume cũ bị hệ thống từ chối, container cũ còn dữ liệu v0.4); xóa "file thừa" (người dùng chưa chọn); chưa chạy `mvnw verify` (không đổi mã Java) | `9977777`, `947cf04`, `c9def16`, `db7ffa9`, `ee14d8f`, `4377683`, `f29fb3b`, `3d2e8bb` (đã push); sửa nhật ký này ở commit sau `3d2e8bb` |
| 9 | 2026-10-27 | TinMH (IT) | LMS-API (kết nối LMS-UI) | Claude Code, claude-sonnet-5-5 | Triển khai Bước 00: Nền tảng dùng chung (US-39, US-40); tái cấu trúc 3 tầng (Package-by-Layer ở cấp gốc kết hợp `.impl` tuân thủ 5 nguyên lý SOLID); kiểm thử MongoDB 8.0 Replica Set; CI PR; ADR 0002; hợp đồng API liên repo; cập nhật nợ kỹ thuật TD-04, TD-12 | Triển khai Bước 00 (US-39, US-40), CI chạy MongoDB 8.0 replica set khi tạo PR, khởi tạo `API-CONTRACT.md`; mô hình 3 tầng, package-by-layer kèm `.impl` | `pom.xml`, `src/main/java/com/hcmute/lms/lmsapi/{controller,dto,service,service/impl,repository,repository/impl,document,mapper,mapper/impl,exception,config,filter}/*`, `src/test/java/...`, `.github/workflows/ci.yml`, `doc/adr/0002-nen-tang-chung-step-00.md`, `doc/coding-convention.md`, `doc/plan/00-nen-tang-chung.md`, `doc/tech-debt.md`, `LMS-UI/doc/API-CONTRACT.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw verify` kiểm thử 100% pass với container MongoDB replica set rs0; test MockMvc kiểm chứng US-40 (400, 401, 403, 404, 409, 422, 500 không lộ stack trace); integration test rollback transaction; test Actuator health/info đạt 200 OK; CI workflow cấu hình service MongoDB 8.0 rs0 nạp schema validator | _Chờ sinh viên điền commit_ |
| 10 | 2026-10-27 | TinMH (IT) / IBS | LMS-API | Teamwork Agent, Gemini / Claude | Triển khai F01 (Người dùng và phân quyền) qua PR 2–PR 5: (PR 2) ADR 0003, `tokenVersion`, `User`, `PasswordPolicyValidator`, `InitialAdminSeeder`; (PR 3) US-01 xác thực Nimbus OAuth2 Resource Server, `/api/v1/auth/login|logout|me`; (PR 4) US-02 CRUD `/api/v1/users/**`, 3 guard bảo vệ ADMIN; (PR 5) US-03 `CourseSection`, `ScopeAuthorizationService`, tài liệu vấn đáp F01, cập nhật CI | Triển khai API F01, lập ADR 0003, đồng bộ tài liệu CSDL, theo kiến trúc 3 tầng (ADR 0002); tách commit rõ ràng | `pom.xml`, `.github/workflows/ci.yml`, `doc/GiaiThich_CoSoDuLieu.md`, `doc/database_import.js`, `doc/adr/0003-xac-thuc-phan-quyen-f01.md`, `doc/de-van-dap/f01-xac-thuc-phan-quyen.md`, `doc/plan/F01-nguoi-dung-phan-quyen.md`, `doc/tech-debt.md`, `src/main/java/com/hcmute/lms/lmsapi/{config,controller,document,dto,exception,mapper,repository,service,util,validator}/**`, `src/test/java/com/hcmute/lms/lmsapi/**`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `./mvnw -B verify` pass 100% (425 tests, 0 failures, 0 errors, JaCoCo >= 70% với overall ~84%, `service.impl` 91%, `controller` 92%, `filter` 100%); kiểm chứng độc lập chống timing attack, thu hồi phiên khi logout/khóa rồi mở khóa, 3 guard bảo vệ ADMIN, quy tắc `lecturerIds` độc lập, strict empty scope của VIEWER, fail-closed khi chỉ có `programIds`, và nạp quyền động từ DB mỗi request | `b1fcd3e`, `53e7243`, `74916b1`, `be1d4ef`, `137c184` (nhánh `feat/f01-user-auth-and-authorization`) |
| 11 | 2026-10-28 | TinMH (chờ xác nhận IBS/IT) | LMS-API | Claude Code, claude-sonnet-5-5 | Tách enum nghiệp vụ (`Role`, `UserStatus`, `AuditAction`, `AccessType`) ra package `enums` ở cấp gốc (trước đó `AccessType` nằm ở `dto`, 3 enum còn lại ở `document`); `ErrorCode` giữ ở `exception`; cập nhật tài liệu | Phản biện vị trí enum trong `document`; tách enum ra package riêng ở gốc | `enums/*.java` (git mv), `document/{User,RoleAssignment,AuditLog}.java` (thêm import), các file import 4 enum này (main + test, gồm chuỗi SpEL `T(...AccessType)`), `doc/coding-convention.md`, `doc/plan/00-nen-tang-chung.md`, `doc/adr/0002-nen-tang-chung-step-00.md` (sơ đồ package) | _Chờ sinh viên rà soát. Giữ tên package `document`; không đổi thành `domain`_ | `mvnw test-compile` thành công; `ScopeAuthorizationServiceImplTest`, `UserScopeEmpiricalChallengeTest`, `UserScopeTest` pass. **Chưa kiểm chứng:** test tích hợp (cần MongoDB `localhost:27018`, đang không chạy nên báo `MongoTimeoutException`), đặc biệt chuỗi SpEL trong `ScopeAuthorizationTestController` | chưa commit |
| 12 | 2026-10-28 | TinMH (chờ xác nhận IBS/IT) | LMS-API | Claude Code, claude-sonnet-5-5 | Sửa CI treo ở bước khởi động MongoDB: vòng chờ `until … ping` lặp vô hạn khi container `mongodb` đã thoát; thêm `concurrency` hủy run cũ khi có commit mới; tìm ra nguyên nhân mongod thoát (kernel runner ≥ 6.19, SERVER-121912) và ghim `runs-on: ubuntu-22.04`; rút gọn nhật ký này | Dán log CI "container … is not running"; "chỉnh lại log AI, xóa bớt prompt thừa"; "commit mới lên PR thì hủy CI đang chạy trước" | `.github/workflows/ci.yml` (hàm `wait_for`: tối đa 60 lần, kiểm tra container còn chạy, in `docker logs mongodb` rồi `exit 1`; khối `concurrency`), `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | Đọc log run treo/xanh/đỏ bằng `gh`: mongod thoát với "Linux kernel versions 6.19 and newer has a known incompatibility". Đọc ticket SERVER-121912, -125742, -121885 (bản vá 8.0.30 chỉ bỏ chặn trên kernel ≥ 7.0.14, không sửa kernel 6.19–7.0.13). **Chưa kiểm chứng:** `ubuntu-22.04` có tránh được kernel lỗi hay không (cần xem dòng `Runner kernel` qua nhiều lần chạy). **Sai sót AI:** đề xuất ghim bản vá 8.0 mới hơn, sau đọc ticket thấy không sửa được | chưa commit |
| 13 | 2026-10-28 | TinMH (chờ xác nhận IBS/IT) | LMS-API | Claude Code, claude-sonnet-5-5 | Chuyển quyền xác nhận nội dung CTĐT, PLO, PI (US-08) từ `PROGRAM_MANAGER` sang `QA_OFFICER` trong tài liệu; thêm ADR 0004 (đề xuất, chờ Q08) | Hỏi các vai trò trong dự án và ai duyệt CTĐT/PLO; phản biện tạo vai trò Trưởng khoa hay gắn quyền vào vai trò cũ; "chỉnh lại cho QA duyệt chương trình đào tạo, PLO" | `doc/plan/F02-ctdt-plo-pi.md`, `doc/chot-feature-do-an.md`, `doc/adr/0004-qa-xac-nhan-ctdt-plo.md` (mới), `doc/plan/F01-nguoi-dung-phan-quyen.md` (§9 ghi chú chuyển giao F02), `doc/de-van-dap/f01-xac-thuc-phan-quyen.md` (bỏ "Trưởng khoa" gán cho `PROGRAM_MANAGER`), `enums/Role.java` (chỉ sửa javadoc `QA_OFFICER`), `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát. Lựa chọn QA_OFFICER và việc gồm cả `program_versions`, `pis` là đề xuất của AI, chưa được Khoa/GVHD duyệt_ | Đọc `Role.java`, F01, F02, `chot-feature-do-an.md` §6, `database_import.js`; grep `src/` không có mã F02 nên không sửa mã. Không chạy build/test (chỉ đổi tài liệu). **Chưa đồng bộ:** `GiaiThich_CoSoDuLieu.md` §8 (Q08), `tom-tat-du-an-de-thao-luan.md` | chưa commit |
| 14 | 2026-10-29 | TinMH (chờ xác nhận IBS/IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Hoàn thiện và kiểm chứng toàn diện phân hệ F02 (Chương trình, phiên bản CTĐT, PLO, PI, đóng góp học phần I/R/M, quy trình xác nhận QA) | Tiếp tục code và hoàn thiện F02 dựa trên logsAI/F02-compaction.md | `src/main/java/com/hcmute/lms/lmsapi/{controller,document,dto,enums,mapper,mapper/impl,repository,repository/impl,service,service/impl,util}/**`, `src/test/java/...`, `doc/plan/F02-ctdt-plo-pi.md`, `logsAI/F02-compaction.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw test -Dtest=ProgramIntegrationTest` pass 3/3 test suites (xác thực, phân quyền, chuyển trạng thái DRAFT->ACTIVE->ARCHIVED, validate mã regex, kiểm tra trùng lặp, QA verification theo program scope, dọn dẹp dữ liệu test); `mvnw -B verify` đạt BUILD SUCCESS với 496/496 test pass (0 failures, 0 errors), JaCoCo check đạt 100% ngưỡng quy định | chưa commit |
| 15 | 2026-11-02 | TinMH (chờ xác nhận IBS/IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Chốt câu hỏi Q17: loại bỏ collection `departments` và bỏ trường `courses.departmentId`, giữ vai trò `DEPARTMENT_HEAD` duyệt ma trận đề thi phân quyền trực tiếp qua `scope.courseIds` | "bỏ collection departments và giữ role DEPARTMENT_HEAD" | `Course.java`, `doc/database_import.js`, `doc/GiaiThich_CoSoDuLieu.md`, `doc/chot-feature-do-an.md`, `doc/ke-hoach-xay-dung.md`, `doc/tom-tat-du-an-de-thao-luan.md`, `doc/plan/F03-hoc-phan-clo.md`, `doc/plan/F04-bai-danh-gia.md`, `doc/PLO_DB_UPDATE.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw test-compile` thành công; `mvnw test -Dtest=ScopeAuthorizationServiceImplTest` chạy 33/33 test pass; kiểm chứng tài liệu khớp 100% không còn tham chiếu tới collection `departments` hay `departmentId` | chưa commit |
| 16 | 2026-11-02 | TinMH (chờ xác nhận IBS/IT) | LMS-API, LMS-UI | Claude Code, claude-sonnet-5-5 (điều phối + 6 subagent song song) | Hoàn thiện nhóm F00 (nền tảng, CI/CD, chất lượng, tài liệu, khung test): tạo 80 issue GitHub (48 API, 32 UI) từ `doc/plan/`; API — PR #59 (tài liệu: test plan, sơ đồ, NFR đề xuất, hướng dẫn bảo vệ `main`), #60 (CI ≥ 6 chặng, Dockerfile, khung staging), #61 (khung test tích hợp, 317 test mới, seed demo, sửa lỗi enum ordinal), #62 (Spotless/SpotBugs/CPD/Sonar, sửa cổng JaCoCo, gỡ websocket/mail, nâng Spring Boot 4.1.2); UI — PR #41 (thành phần dùng chung, xử lý lỗi), #42 (ADR SSR, coverage, CI, Docker, Playwright + axe) | "làm các issue F00, hoàn thiện toàn bộ"; quyết định: staging chỉ chuẩn bị, bảo vệ `main` chỉ viết hướng dẫn, sửa hết vi phạm, tạo PR | Xem danh sách file trong từng PR (#59–#62 API; #41–#42 UI); `doc/adr/0005,0006,0008`; `doc/quality/README.md`; `doc/deploy/staging.md`; `doc/test-plan.md`; `doc/sdd/`; `doc/srs/` | _Chờ sinh viên rà soát_ | Do từng agent chạy thật: API `./mvnw verify` 524 test (PR #62, trên 4.1.2) và 813 test (PR #61, trước khi tích hợp #62), 0 fail/0 skip; SpotBugs 22→0, CPD 18→0; lõi JaCoCo 84,85%; UI lint sạch, 205 test pass (PR #42) và 381 test pass (PR #41), E2E 9/9, `docker build` + smoke; gitleaks lịch sử sạch (sau `.gitleaksignore` cho 3 dương tính giả); CI thật PR #42 xanh. **Chưa chạy/chưa đo:** Sonar, deploy staging, thời gian commit→image, CI thật của PR #59/#61/#62, E2E trên backend thật | PR #59–#62 (API), #41–#42 (UI) — chưa merge; điền mã commit sau khi merge |
| 17 | 2026-11-02 | minhat5 (chờ xác nhận IBS/IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Xử lý nợ kỹ thuật TD-06: Loại bỏ hoàn toàn collection `ml_experiments` khỏi `database_import.js` và đồng bộ các tài liệu (CSDL còn 29 collection chính, xác nhận không thực hiện Machine Learning trong phạm vi đồ án) | "ở doc/tech-debt.md còn TD-06 nói về việc khai báo ml_experiments thì hiện tại ML chưa được thực hiện trong phạm vi đồ án nên sẽ không sử dụng collection này sửa các doc có liên quan tới ML và collection này để không làm tới nó" | `doc/tech-debt.md`, `doc/database_import.js`, `README.md`, `doc/GiaiThich_CoSoDuLieu.md`, `doc/PLO_DB_UPDATE.md`, `doc/chot-feature-do-an.md`, `doc/ke-hoach-xay-dung.md`, `doc/sdd/03-erd-collection.md`, `doc/tom-tat-du-an-de-thao-luan.md`, `.claude/rules/02-domain-invariants.md`, `doc/adr/0002-nen-tang-chung-step-00.md`, `doc/plan/README.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `git grep -i "ml_experiments"` kiểm tra toàn bộ repo không còn tham chiếu chưa xử lý; `git grep -i "30 collection"` không còn; xác minh cú pháp JS của `database_import.js` hợp lệ với 29 collection; kiểm tra TD-06 chuyển sang trạng thái "Đã xử lý" | chưa commit |
| 18 | 2026-11-03 | minhat5 (chờ xác nhận IBS/IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Bổ sung thuộc tính học phần học trước (`previousCourseIds`) vào collection `program_courses`, Document, DTOs, Mapper, Service, Validation, Test suites; bổ sung kiểm tra xung đột giữa tiên quyết và học trước (`PREREQUISITE_AND_PREVIOUS_CONFLICT`) | "ở database các document programcourses đang chỉ có học phần tiên quyết thôi chứ thiếu học phần học trước, thêm thuộc tính đó vô database và các file liên quan"; "có kiểm tra lỗi nếu vừa tiên quyết vừa học trước chưa" | `doc/database_import.js`, `doc/GiaiThich_CoSoDuLieu.md`, `doc/PLO_DB_UPDATE.md`, `doc/plan/F02-ctdt-plo-pi.md`, `doc/plan/F03-hoc-phan-clo.md`, `doc/de-van-dap/f03-hoc-phan-clo.md`, `src/main/java/com/hcmute/lms/lmsapi/document/ProgramCourse.java`, `src/main/java/com/hcmute/lms/lmsapi/dto/ProgramCourseRequest.java`, `src/main/java/com/hcmute/lms/lmsapi/dto/ProgramCourseUpdateRequest.java`, `src/main/java/com/hcmute/lms/lmsapi/dto/ProgramCourseResponse.java`, `src/main/java/com/hcmute/lms/lmsapi/mapper/impl/ProgramCourseMapperImpl.java`, `src/main/java/com/hcmute/lms/lmsapi/service/impl/ProgramCourseServiceImpl.java`, `src/test/java/com/hcmute/lms/lmsapi/service/impl/ProgramCourseServiceImplTest.java`, `src/test/java/com/hcmute/lms/lmsapi/dto/CourseValidationTest.java`, `src/test/java/com/hcmute/lms/lmsapi/controller/CourseIntegrationTest.java`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw test "-Dtest=ProgramCourseServiceImplTest,CourseValidationTest"` đạt 17/17 test pass (0 failures, 0 errors); `mvnw spotless:check` đạt BUILD SUCCESS; kiểm tra tính toàn vẹn tham chiếu cấp ứng dụng (chặn self-reference `PREVIOUS_COURSE_SELF_REFERENCE`, chặn non-existent `PREVIOUS_COURSE_NOT_FOUND`, chặn xung đột vừa tiên quyết vừa học trước `PREREQUISITE_AND_PREVIOUS_CONFLICT`, validate ObjectId hex 24 ký tự); đồng bộ schema MongoDB validator trong `database_import.js` | chưa commit |
| 19 | 2026-11-04 | minhat5 (chờ xác nhận IBS/IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Triển khai trước module Quản lý học kỳ `terms` (F05a / US-18, Issue #28) làm căn cứ xác thực thời gian hiệu lực cho đề cương môn học `syllabi` (F03 / US-10, Issue #21); Hoàn thiện toàn bộ các User Story cốt lõi của F03 gồm chuẩn đầu ra học phần `clos` (US-11, Issue #22) và liên kết `clo_pi_links`, ma trận CLO x PI, thẩm định QA (US-12, Issue #23); Đồng bộ tài liệu vấn đáp F03 và kế hoạch | "ở f03 issues trên git này đang chưa thực hiện xong do chưa thực hiện các bảng liên quan đến clo giờ hãy thực hiện, đồng thời làm trước việc quản lý terms của f05a để code được cho syllabi và đánh dấu git issues của f03 nếu đã thực hiện xong và báo cáo sao để biết được f05a đã thực hiện việc quản lý terms trước. Cái nào chưa xác nhận được thì chưa làm và ghi report ở cuối cái gfi chưa làm và lí do" | `src/main/java/com/hcmute/lms/lmsapi/{document,enums,repository,dto,mapper,mapper/impl,service,service/impl,controller}/**` cho `Term`, `Syllabus`, `Clo`, `CloPiLink`; các bài test `TermServiceImplTest`, `TermValidationTest`, `TermIntegrationTest`, `SyllabusServiceImplTest`, `CloServiceImplTest`, `CloPiLinkServiceImplTest`, `CloValidationTest`, `CloIntegrationTest`; `doc/plan/F03-hoc-phan-clo.md`, `doc/plan/F05a-hoc-ky-lop-sinh-vien.md`, `doc/de-van-dap/f03-hoc-phan-clo.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw test "-Dtest=TermServiceImplTest,TermValidationTest,SyllabusServiceImplTest,CloServiceImplTest,CloPiLinkServiceImplTest,CloValidationTest"` đạt 48/48 test pass (0 failures, 0 errors); kiểm chứng toàn vẹn tham chiếu cấp dịch vụ (chặn duplicate `code`/`version`, chặn `startDate > endDate`, chặn `effectiveToTermId < effectiveFromTermId`, đóng băng sửa CLO khi đề cương `CONFIRMED`, chặn xóa Term có tham chiếu); `mvnw spotless:apply` đạt BUILD SUCCESS | chưa commit |

| 20 | 2026-11-05 | minhat5 (chờ xác nhận IBS/IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Tiếp nhận 3 tệp minh chứng thực tế ngành Fintech; Ban hành ADR 0010 thay thế hoàn toàn ADR 0009; Tái cấu trúc mô hình phân quyền: Admin độc quyền quản lý Master Catalog `courses`, Trưởng bộ môn (`DEPARTMENT_HEAD`) phân công Giảng viên (`LECTURER`) biên soạn đề cương/CLO (DRAFT) và TBM duyệt CONFIRMED, Trưởng ngành (`PROGRAM_MANAGER`) quản lý CTĐT và liên kết CLO-PI; Triệt tiêu lỗ hổng TD-29; Cập nhật toàn diện các tài liệu thiết kế liên quan | "nếu đề cương là do giảng viên biên soạn và trưởng bộ môn là người duyệt thì sao... sửa lại các doc cho về là admin tạo ra các môn học, bỏ đi quyền đó của program manager, đồng thời admin sẽ gán scope các môn cho trưởng bộ môn và trưởng bộ môn sẽ là người phân công quyền viết đề cương cho giảng viên và trưởng bộ môn duyệt các đề cương đó" | `doc/adr/0010-phan-quyen-de-cuong-theo-bo-mon-va-giang-vien.md`, `doc/adr/0009-phan-quyen-pham-vi-de-cuong-clo.md`, `doc/plan/F03-hoc-phan-clo.md`, `doc/de-van-dap/f03-hoc-phan-clo.md`, `doc/sdd/02-component.md`, `doc/sdd/04-sequence.md`, `doc/srs/README.md`, `doc/tech-debt.md`, `doc/test-plan.md`, `doc/cam-ket-san-pham-metric.md`, `doc/ke-hoach-xay-dung.md`, `doc/rubric-checklist.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | Đối chiếu 3 tệp đề cương/CTĐT thật của Fintech; kiểm tra toàn bộ liên kết và ma trận phân quyền khớp 100% nguyên tắc kiểm định AUN-QA và cấu trúc quản trị đại học; giải quyết triệt để TD-29; kiểm tra `git status` và `git diff` sạch | PR #73 (92c703c) |
| 21 | 2026-11-05 | minhat5 (chờ xác nhận IBS/IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Triển khai mã nguồn và bộ kiểm thử toàn diện cho ADR 0010 (Quy trình biên soạn & phê duyệt đề cương giữa Bộ môn, Giảng viên, Trưởng ngành và Admin): Admin độc quyền CRUD Master Courses; Phân công môn học cho GV (`POST /api/v1/departments/assign-course`); Tối ưu `canAccessCourse` $O(1)$; Khóa đề cương/CLO khi khác DRAFT (TD-27); Chặn liên kết CLO-PI nếu môn không thuộc CTĐT (TD-28) | "triển khai mã nguồn và bộ test tương ứng lại trên nhánh mới đi" | `AssignCourseRequest.java`, `DepartmentService.java`, `DepartmentServiceImpl.java`, `DepartmentController.java`, `DepartmentIntegrationTest.java`, `ScopeAuthorizationService.java`, `ScopeAuthorizationServiceImpl.java`, `CourseController.java`, `SyllabusController.java`, `CloController.java`, `CloServiceImpl.java`, `CloPiLinkServiceImpl.java`, `CourseScopeAuthorizationTest.java`, `CloServiceImplTest.java`, `CloPiLinkServiceImplTest.java`, `CourseIntegrationTest.java`, `CloIntegrationTest.java`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | Chạy `CourseScopeAuthorizationTest`, `CloServiceImplTest`, `CloPiLinkServiceImplTest`, `DepartmentIntegrationTest`, `CourseIntegrationTest`, `CloIntegrationTest` (52/52 pass); Chạy toàn bộ test suite `./mvnw test` (977/977 pass 100%); Chạy `./mvnw spotless:check` đạt BUILD SUCCESS; Triệt tiêu triệt để TD-27, TD-28, TD-29 | 03f61c2 |

| 22 | 2026-11-05 | minhat5 (chờ xác nhận IBS/IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Tách biệt trạng thái vòng đời phiên bản CTĐT (`status`) khỏi DTO cập nhật nội dung `ProgramVersionUpdateRequest`; bổ sung endpoint chuẩn RESTful `PUT /api/v1/program-versions/{id}/status` với `ProgramVersionStatusUpdateRequest`; bảo toàn nguyên tắc tách quyền giữa QA thẩm định (`verification.status`) và Trưởng CTĐT quản lý vòng đời; cập nhật và kiểm thử toàn diện | "ở phiên bản chương trình đào tạo, do đang là QA duyệt chương trình đào tạo, để tránh hỏng phân quyền thì ở việc chỉnh sửa phiên bản chương trình đào tạo bỏ status ở dto ProgramVersionUpdateRequest được không có hỏng gì không"; "thực hiện điều đó đi" | `src/main/java/com/hcmute/lms/lmsapi/dto/ProgramVersionStatusUpdateRequest.java` (mới), `ProgramVersionUpdateRequest.java`, `ProgramVersionService.java`, `ProgramVersionServiceImpl.java`, `ProgramVersionController.java`, `ProgramVersionServiceImplTest.java`, `ProgramVersionApiIntegrationTest.java`, `ProgramIntegrationTest.java`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw test "-Dtest=ProgramVersionServiceImplTest,ProgramVersionApiIntegrationTest,ProgramIntegrationTest"` (54/54 pass); `mvnw test "-Dtest=ProgramServiceImplTest,ProgramVersionServiceImplTest,PloServiceImplTest,PiServiceImplTest,CoursePiContributionServiceImplTest,ProgramVerificationServiceImplTest,ProgramScopeAuthorizationTest,F02ValidationTest"` (71/71 pass); `mvnw spotless:check` đạt BUILD SUCCESS; xác nhận bảo toàn FSM `DRAFT -> ACTIVE -> ARCHIVED` qua endpoint riêng | 2bf4f7a |
| 23 | 2026-11-06 | minhat5 (chờ xác nhận IBS/IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Bổ sung endpoint `GET /api/v1/departments/lecturers` và cơ chế lọc/kiểm soát phạm vi Khoa/Ngành (`scope.programIds`) khi Trưởng bộ môn (`DEPARTMENT_HEAD`) tra cứu và phân công Giảng viên biên soạn đề cương (`assign-course`, `unassign-course` theo ADR 0010 - Phương án A); chặn lỗi `422 LECTURER_OUT_OF_DEPARTMENT_SCOPE` nếu cố tình gán giảng viên ngoài phạm vi Khoa/Ngành | "ê nhưng mà nếu mà làm như này thì bộ môn thấy được tất cả giảng viên để phân công đề cương luôn à"; "thực hiện phương án A đi" | `UserRepository.java`, `DepartmentService.java`, `DepartmentServiceImpl.java`, `DepartmentController.java`, `DepartmentIntegrationTest.java`, `doc/adr/0010-phan-quyen-de-cuong-theo-bo-mon-va-giang-vien.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw test "-Dtest=DepartmentIntegrationTest"` đạt 8/8 test pass (kiểm chứng TBM thuộc Program 1 chỉ thấy và gán được GV thuộc Program 1, chặn 422 khi gán GV thuộc Program 2, chặn 403 khi truyền courseId ngoài scope); `mvnw spotless:check` đạt BUILD SUCCESS | _Chờ sinh viên điền commit_ |
| 24 | 2026-11-06 | minhat5 (IT) | LMS-API | Claude Opus 5.5 / Gemini 3.8 Flash (Antigravity) | Rà soát toàn diện các mục chưa duyệt/xác nhận; tiếp nhận giải đáp và chốt nội bộ các câu hỏi Q01–Q23 (chốt Q01 áp dụng khóa K23..K27, Q02, Q05, Q06, Q08, Q09, Q10, Q11, Q14, Q16, Q18; làm rõ Q03; xác nhận chưa có công thức Khoa Q04, Q12, Q13, Q19–Q23); ban hành ADR 0011 (PI suy từ CLO, câu hỏi chỉ gán CLO, bỏ DIRECT_FROM_ITEMS) và ADR 0012 (bỏ hoàn toàn quy đổi I/R/M, xóa useIrmAsWeight/irmWeights); đồng bộ schema MongoDB validator, quy tắc miền, kế hoạch xây dựng, plan F04/F06 và ProgramReferenceRepositoryImpl | "quét lại một lần đồ án để xem những gì chưa được duyệt, xác nhanận"; "Q01-Q23 còn câu hỏi nào cần xác nhận..."; "Q01 áp dụng khoá K23 K24 K25 K26 K27"; "commit lên và tạo pr về main có title comment đầy đủ" | `doc/adr/0011-pi-suy-ra-tu-clo.md` (mới), `doc/adr/0012-bo-quy-doi-irm.md` (mới), `doc/database_import.js`, `doc/GiaiThich_CoSoDuLieu.md`, `doc/tom-tat-du-an-de-thao-luan.md`, `doc/chot-feature-do-an.md`, `doc/ke-hoach-xay-dung.md`, `doc/plan/F04-bai-danh-gia.md`, `doc/plan/F06-do-luong.md`, `doc/plan/README.md`, `doc/de-van-dap/f02-ctdt-plo-pi.md`, `.claude/rules/02-domain-invariants.md`, `src/main/java/.../ProgramReferenceRepositoryImpl.java`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw spotless:check` đạt BUILD SUCCESS; `mvnw test` đạt 981/981 tests pass 100%; kiểm tra schema MongoDB validator cho OUTCOME_MAPPING `{cloId, weight}` và loại bỏ useIrmAsWeight/irmWeights; đối chiếu tính nhất quán 100% giữa ADR 0011/0012 và mã/tài liệu | PR #77 (`987c9d1`) |
| 25 | 2026-11-07 | minhat5 (IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Thu hồi triệt để quyền biên soạn đề cương (`POST /courses/{id}/syllabi`), tạo/sửa/xóa CLO (`/clos/**`) và phê duyệt đề cương (`PUT /syllabi/{id}/status`) của Quản trị viên (`ADMIN`); bảo vệ tính liêm chính học thuật (Academic Integrity): Admin chỉ quản lý danh mục môn học (`courses`), việc viết đề cương thuộc về GV/TBM được phân công môn, việc duyệt đề cương độc quyền thuộc Trưởng bộ môn; cập nhật ADR 0010 và kiểm thử phân quyền tích hợp | "tại sao admin lại có quyền biên soạn đề cương vậy"; "thu hồi đi" | `src/main/java/com/hcmute/lms/lmsapi/controller/SyllabusController.java`, `src/main/java/com/hcmute/lms/lmsapi/controller/CloController.java`, `src/test/java/com/hcmute/lms/lmsapi/controller/CloIntegrationTest.java`, `doc/adr/0010-phan-quyen-de-cuong-theo-bo-mon-va-giang-vien.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw spotless:check` đạt BUILD SUCCESS; `mvnw test` đạt 981/981 tests pass 100% (kiểm chứng ADMIN cố tình POST syllabi hoặc POST/PUT/DELETE CLO bị từ chối 403 Forbidden fail-closed) | `ade4c16` |
| 26 | 2026-11-07 | minhat5 (IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Thu hồi triệt để quyền của ADMIN tại toàn bộ endpoint Bộ môn (`/api/v1/departments/**` gồm tra cứu GV bộ môn và phân công/gỡ môn của GV); loại bỏ bypass `isAdmin` trong `canAssignCourse` và `DepartmentServiceImpl`; loại bỏ hoàn toàn giao diện phân công GV viết đề cương ở trang Admin (cả frontend lẫn backend); cập nhật kiểm thử và ADR 0010 | "nên để lại giao diện phân quyền đề cương cho gianrg viên ở trong trang admin không"; "tôi nghĩ bỏ cả backend lẫn frontend luôn"; "có" | `DepartmentController.java`, `ScopeAuthorizationServiceImpl.java`, `DepartmentServiceImpl.java`, `DepartmentIntegrationTest.java`, `CourseScopeAuthorizationTest.java`, `doc/adr/0010-phan-quyen-de-cuong-theo-bo-mon-va-giang-vien.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw test "-Dtest=DepartmentIntegrationTest,CourseScopeAuthorizationTest"` (22/22 pass); `mvnw test` toàn diện đạt 981/981 tests pass 100%; `mvnw spotless:check` đạt BUILD SUCCESS; kiểm chứng ADMIN gọi `/departments/**` bị 403 Forbidden | _Chờ sinh viên điền commit_ |
| 27 | 2026-11-08 | minhat5 (IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Triển khai tính năng nhập danh mục học phần hàng loạt từ tệp CSV (`POST /api/v1/courses/import`, US-09/AC-4); xây dựng parser CSV thuần Java RFC 4180 (`CsvUtils`) không phụ thuộc thư viện bên ngoài, xử lý UTF-8 BOM, dấu nháy kép `""` và dấu phẩy; xử lý nghiệp vụ theo cơ chế Best-effort vừa nạp dòng hợp lệ vừa báo lỗi chi tiết theo dòng; phân quyền bảo vệ độc quyền của ADMIN | "sử dụng phương án A với best effort ở cơ chế nghiệp vụ tại service" | `src/main/java/com/hcmute/lms/lmsapi/util/CsvUtils.java`, `dto/{CourseImportRowError,CourseImportResultResponse}.java`, `service/{CourseService,impl/CourseServiceImpl}.java`, `controller/CourseController.java`, `src/test/java/.../{CsvUtilsTest,CourseServiceImplTest,CourseIntegrationTest}.java`, `doc/plan/F03-hoc-phan-clo.md`, `doc/de-van-dap/f03-hoc-phan-clo.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw test "-Dtest=CsvUtilsTest,CourseServiceImplTest,CourseIntegrationTest"` đạt 28/28 tests pass 100%; `mvnw spotless:check` đạt BUILD SUCCESS; kiểm chứng ca âm, ca biên và tích hợp với MongoDB replica set thật; kiểm chứng fail-closed phân quyền chỉ ADMIN được import | `c1c9e35` |
| 27 | 2026-11-08 | TinMH (chờ xác nhận IBS/IT) | LMS-API | Claude Code, claude-sonnet-5-5 | Hoàn tất F05a: lớp học phần (US-19: `/api/v1/sections`, quyền ghi theo `scope.programIds` của PM), sinh viên và ghi danh (US-20: xem danh sách, rút/ghi danh lại, nhập CSV một thao tác thống nhất, sĩ số dẫn xuất, đóng băng khi lớp/học kỳ không OPEN); cập nhật plan F05a §11, test-plan §8.5a, SDD (component + sequence), tech-debt TD-30…TD-33 | "tiến hành làm tiếp f05a cho hoàn tất đi. Tách commit ra và push lên một nhánh khác, khi hoàn thành tạo PR vào main. Trước khi push phải test lại và đảm bảo không phá hỏng CI" | `SectionController`, `SectionService(Impl)`, `SectionMapper(Impl)`, `Section*Request/Response`, `SectionStatus`, `CourseSection`, `EnrollmentController`, `EnrollmentService(Impl)`, `EnrollmentMapper(Impl)`, `Enrollment*`, `Student`, `Enrollment`, `StudentStatus`, `EnrollmentStatus`, `StudentRepository`, `EnrollmentRepository`, `EnrollmentCsvParser`, `ImportValidationException`, `GlobalExceptionHandler`, `ScopeAuthorizationService(Impl)`, các test `Section*`, `Enrollment*`, `ApiTestFixtures`; `doc/plan/F05a-…`, `doc/test-plan.md`, `doc/sdd/02-component.md`, `doc/sdd/04-sequence.md`, `doc/srs/README.md`, `doc/tech-debt.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | Mongo replica set `rs0` dựng từ image `lms-mongo:f05a` (cổng 27018); `./mvnw -B -ntp verify` (Spotless, SpotBugs, CPD, JaCoCo, toàn bộ test): lần 1 phát hiện 6 lỗi SpotBugs (B20), sau khi sửa chạy lại, kết quả ghi ở mô tả PR | _Chờ sinh viên điền commit_ |
| 28 | 2026-11-08 | TinMH (chờ xác nhận IBS/IT) | LMS-API | Claude Code, claude-sonnet-5-5 | Hiện thực F04: bài đánh giá khung và bản lớp (US-15), rubric mức 1-4 (US-16), quy trình duyệt ma trận NEW-01 (gửi duyệt, TBM duyệt/từ chối, khóa `items`, audit `ASSESSMENT_APPROVE/REJECT`, độ phủ theo CLO/Bloom); sửa xử lý WriteConflict MongoDB; cập nhật ADR 0001 (GVHD đã xác nhận Q11), plan F04 §11, test-plan §8.5b, SDD, tech-debt TD-34/35 | "NEW-01: GVHD đã ký xác nhận, tôi đã merge, làm f04 đi" | `Assessment*` (document, enum, DTO, mapper, service, controller), `AssessmentItemValidator`, `AssessmentCoverageCalculator`, `ScopeAuthorizationService(Impl)`, `GlobalExceptionHandler`, test `Assessment*`, `WriteConflictHandlingTest`, `ApiTestFixtures`; `CLAUDE.md`, `.claude/rules/02-domain-invariants.md`, `doc/adr/0001-…`, `doc/plan/F04-…`, `doc/test-plan.md`, `doc/sdd/*`, `doc/tech-debt.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | MongoDB `rs0` (container `lms-mongo-f05a`, cổng 27018); `AssessmentIntegrationTest` 22/22, các unit test F04; `./mvnw -B -ntp verify` ghi ở mô tả PR | _Chờ sinh viên điền commit_ |
| 28 | 2026-11-09 | minhat5 (IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Khởi tạo 2 tệp CSV dữ liệu danh mục học phần chuẩn hóa từ 2 bản mô tả CTĐT CNTT (93 môn) và FINTECH (77 môn) tại `doc/data/` phục vụ kiểm thử tính năng Import Course CSV (US-09/AC-4) và cơ chế Best-effort; bổ sung test case xác thực tệp thực tế trong `CsvUtilsTest` | "tạo cho tôi 2 file để import các course vô hệ thống để test chức năng import này thông qua 2 chương trình đào tạo 3_CNTT_Banmotactdt và file Biểu mẫu 09_CTĐT ngành FINTECH v2..."; "commit lên git và tạo pr về main đi" | `doc/data/courses_cntt.csv`, `doc/data/courses_fintech.csv`, `doc/3_CNTT_Banmotactdt.md`, `src/test/java/com/hcmute/lms/lmsapi/util/CsvUtilsTest.java`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw test -Dtest=CsvUtilsTest` đạt 7/7 tests pass 100% (xác thực toàn bộ 94 dòng CNTT và 78 dòng FINTECH khớp regex, không rỗng tên, tín chỉ không âm); `mvnw spotless:check` đạt BUILD SUCCESS | `37bb4c5` |
| 29 | 2026-11-10 | minhat5 (IT) | LMS-API | Gemini 4 Argon (Antigravity) | Soạn và chuẩn hóa tài liệu đặc tả nghiệp vụ & phương pháp tính toán mức độ đạt CLO (`doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`) độc lập với mã nguồn cũ để trình GVHD xem xét: chốt phương án 1 vai trò quản lý học thuật Import CTĐT & Đề cương sinh PLO/PI/CLO (R1); giảng viên lớp học phần tạo bài kiểm tra + ma trận đề không lưu nội dung câu hỏi và tự khóa ma trận đề; giảng viên nhập trực tiếp điểm từng CLO tại từng câu hỏi (câu có nhiều CLO thì nhập riêng từng CLO, không chia theo trọng số); chỉ đo lường chuẩn đầu ra (không đo kết quả bài kiểm tra), tính CLO trên từng bài kiểm tra và tổng hợp CLO toàn học phần theo tỷ lệ tổng điểm đạt / tổng điểm tối đa của từng CLO (B1) kèm ví dụ tính tay | "Giảng viên hướng dẫn đang kêu đổi qua nghiệp vụ lại như thế này..."; "điểm của CLO của từng câu hỏi là do giảng viên nhập không phải theo trọng số, nếu 1 câu hỏi có nhiều CLO thì từng CLO của câu hỏi đó cũng do giảng viên nhập, ngoài ra hệ thống cũng không đo kết quả bài kiểm tra, chỉ đo lường chuẩn đầu ra mà thôi"; "về 3.1A tôi chọn phương án R1, 4.2 chọn phương án B1 câu hỏi 2 ở 7 giảng viên có thể tự khoá ma trận viết cho tôi một đặc tả không dính đến code cũ để gửi gvhd xem xét" | `doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát và gửi GVHD xem xét_ | Kiểm tra toàn bộ phép tính tay ở Mục 5 (CLO1 = 12.5/17.0 = 73.53%, CLO2 = 7.3/11.0 = 66.36%, CLO3 = 1.2/2.0 = 60.00%); rà soát đảm bảo văn bản thuần đặc tả nghiệp vụ và toán học, không chứa tên bảng/biến code cũ hay lịch sử ADR nội bộ. Ghi nhận lỗi quán tính B26. Không sửa mã nguồn Java | chưa commit |
| 30 | 2026-11-13 | minhat5 (IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Tinh gọn hệ thống phân quyền từ 7 vai trò xuống 4 vai trò cốt lõi (`ADMIN`, `PROGRAM_MANAGER`, `LECTURER`, `QA`) theo đặc tả mới (ADR 0014, OBE Schema v0.7); hợp nhất `VIEWER` và `QA_OFFICER` thành `QA` (Cán bộ ĐBCL & Khảo thí, xem báo cáo đo lường, thẩm định CTĐT); loại bỏ `DEPARTMENT_HEAD` (bãi bỏ luồng duyệt ma trận đề); loại bỏ `STUDENT` khỏi enum `Role` (chỉ là thực thể dữ liệu học vụ trong `students`/`enrollments` tính mẫu số $N_{\text{section}}$); cập nhật MongoDB Schema validator, `DemoDataSeeder` (4 demo user), mã nguồn backend Spring Boot, sửa 103 test files (1109/1109 test pass 100%) và cập nhật toàn bộ tài liệu `doc/` | "kiểm tra toàn bộ doc và code backend xem chỗ nào còn chưa đúng với đặc tả mới để còn sửa đổi"; "tôi nghĩ thay tên role VIEWER thành QA để là người xem báo cáo đo lường, tinh gọn triệt để và cập nhật lại toàn bộ doc để đúng với đặc tả mới hiện tại" | `Role.java`, `DemoDataSeeder.java`, `ScopeAuthorizationServiceImpl.java`, `AssessmentController.java`, `CloController.java`, `CloPiLinkController.java`, `DepartmentController.java`, `DepartmentServiceImpl.java`, `PiController.java`, `PloController.java`, `ProgramVersionController.java`, `SyllabusController.java`, `doc/database_import.js`, `doc/GiaiThich_CoSoDuLieu.md`, `doc/adr/0003-xac-thuc-phan-quyen-f01.md`, `doc/adr/0004-qa-xac-nhan-ctdt-plo.md`, `doc/de-van-dap/f05b-nhap-diem.md`, `doc/plan/F04-bai-danh-gia.md`, `doc/sdd/*`, `doc/test-plan.md`, 20+ test files | _Chờ sinh viên rà soát_ | Chạy `mongosh` cập nhật JSON Schema Validator cho 25 collections trên MongoDB `rs0` cổng 27018; `mvnw test` đạt 1109/1109 test pass 100% (0 failures, 0 errors, BUILD SUCCESS trong 6:33 min); xác nhận bảo mật fail-closed và phân quyền 4 vai trò chuẩn hóa | chưa commit |
| 31 | 2026-11-13 | minhat5 (IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Bổ sung API xuất tệp CSV mẫu nhập điểm `GET /api/v1/assessments/{id}/scores/template-csv` và tích hợp cơ chế tự động nhận diện cột điểm từ header tệp mẫu (`autoDetectMapping`) cho F05b | "Bổ sung tính năng còn thiếu của F05b (Phase 3): Xây dựng API xuất tệp CSV mẫu GET /api/v1/assessments/{id}/scores/template-csv và tích hợp cơ chế tự động nhận diện cột điểm từ header tệp mẫu" | `AssessmentScoreTemplate.java`, `Student.java`, `AssessmentService.java`, `AssessmentServiceImpl.java`, `AssessmentController.java`, `ImportBatchServiceImpl.java`, `AssessmentIntegrationTest.java`, `ImportBatchUploadIntegrationTest.java`, `ImportBatchServiceImplTest.java`, `doc/plan/F05b-nhap-diem.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `mvnw spotless:apply` sạch 392 files; chạy 79/79 integration tests phân hệ F05b (`*Import*, *Score*`) pass 100%; kiểm chứng UTF-8 BOM, kiểm tra điều kiện LOCKED/SCORE_ENTERED, trích xuất itemCode/cloCode từ header | `73f1995` |
| 32 | 2026-11-13 | minhat5 (IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Khắc phục 3 lỗi phân tích tĩnh SpotBugs (`URF_UNREAD_FIELD` do trường thừa `adminToken` trong `DepartmentIntegrationTest` và `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` do mutable array `byte[]` trong `AssessmentScoreTemplate`); tuân thủ tuyệt đối quy tắc không dùng `@SuppressWarnings`, dùng defensive copy clone mảng byte | Dán log SpotBugs báo 3 bugs (URF_UNREAD_FIELD, EI_EXPOSE_REP, EI_EXPOSE_REP2) | `AssessmentScoreTemplate.java`, `DepartmentIntegrationTest.java`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `./mvnw test-compile spotbugs:check` đạt BUILD SUCCESS, `BugInstance size is 0`, `Error size is 0`, không có lỗi/cảnh báo; `spotless:check` sạch 392 files | `a6dc7e9` |
| 33 | 2026-11-13 | minhat5 (IT) | LMS-API | Gemini 3.8 Flash (Antigravity) | Khắc phục 2 lỗi trùng lặp mã nguồn của PMD CPD (`maven-pmd-plugin:cpd-check`): trích xuất hàm dùng chung `resolveCloCodeMap` vào `AssessmentItemValidator` cho `AssessmentServiceImpl` và `ImportBatchServiceImpl`; trích xuất helper `expectReadAndWriteForbidden` trong `ScopeAuthorizationMatrixIntegrationTest` | "tìm ra lỗi tận gốc chứng minh được do đó mới lỗi mới được sửa code"; "sửa đi" | `AssessmentItemValidator.java`, `AssessmentServiceImpl.java`, `ImportBatchServiceImpl.java`, `ImportBatchServiceImplTest.java`, `ScopeAuthorizationMatrixIntegrationTest.java`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `./mvnw pmd:cpd-check` đạt BUILD SUCCESS (0 duplication); `./mvnw test-compile spotbugs:check` đạt 0 bug; 51/51 tests tích hợp/đơn vị liên quan pass 100% | _Chờ sinh viên điền commit_ |
| 34 | 2026-11-15 | TinMH (IT) | LMS-API | Antigravity (Gemini + Teamwork Multi-Agent) | Thiết kế và hiện thực tính năng Báo cáo phân tích đa chiều 5 chiều & Xuất CSV ma trận cho F07 (`GET /api/v1/reports/analytics` và `GET /api/v1/reports/analytics/export`, US-36/US-35): 5 chiều (`TIME_TERM`, `COURSE_SECTIONS`, `COHORT`, `ASSESSMENT_COMPONENT`, `CURRICULUM_CLASSIFICATION` gồm `PLO_CATEGORY`/`COURSE_TYPE`/`BLOOM_LEVEL`), tổng hợp chỉ đọc từ lượt chạy `SUCCESS` mà không đổi schema 25 collections MongoDB v0.7, bất biến 10 trường `MatrixMetricCell` xử lý mẫu số 0 trả `null`, phân quyền fail-closed 4 vai trò, chống CSV Formula Injection (CWE-1236), khắc phục 5 lỗi SpotBugs + 7 trùng lặp PMD CPD (B29), cập nhật tài liệu F07/SDD/vấn đáp | "Làm thêm chức năng report nhiều chiều được không?"; "/teamwork tôi muốn làm theo 5 chiều như bạn đề xuất, almf đi"; "coomit tao PR di" | `AnalyticsController.java`, `AnalyticsService.java`, `AnalyticsServiceImpl.java`, `MatrixCsvExporter.java`, `dto/analytics/{AnalyticsMatrixResponse,AnalyticsQueryRequest,MatrixHeader,MatrixMetricCell}.java`, `enums/{AnalyticsDimension,CurriculumClassificationType}.java`, `AnalyticsServiceImplTest.java`, `AnalyticsControllerIntegrationTest.java`, `doc/plan/F07-bao-cao-truy-vet.md`, `doc/sdd/02-component.md`, `doc/de-van-dap/f07-bao-cao-phan-tich-da-chieu.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | `./mvnw -B -ntp test-compile spotbugs:check pmd:cpd-check spotless:check` đạt BUILD SUCCESS (0 SpotBugs, 0 CPD duplications, 421 files Spotless clean); 74/74 unit + integration tests (`AnalyticsServiceImplTest` 31/31, `AnalyticsControllerIntegrationTest` 18/18, `ScopeAuthorizationServiceImplTest` 25/25) pass 100%; JaCoCo line coverage đạt 100% (`AnalyticsController`), 96.9% (`MatrixCsvExporter`), 88.3% (`AnalyticsServiceImpl`) | PR #91 (`e733fff`, `598de96`) |
| 35 | 2026-11-15 | TinMH (IT) | LMS-API | Antigravity (Gemini + Teamwork Multi-Agent) | Mở rộng báo cáo phân tích đa chiều F07 từ 5 lên 7 chiều bằng việc bổ sung `COURSE_LECTURER` (đánh giá hiệu quả giảng dạy môn học theo giảng viên) và `STUDENT` (kết quả chuẩn đầu ra chi tiết theo từng sinh viên), kèm phân quyền fail-closed (LECTURER chỉ đọc lớp mình phụ trách) và xuất CSV; đồng thời khắc phục triệt để lỗi CI trên PR #91 do test fixture thiếu các trường bắt buộc của `$jsonSchema` validator v0.7 (B30); cập nhật tài liệu F07/SDD/vấn đáp | "/teamwork chưa có cái theo một course của 1 giảng viên thì report sao, 1 sinh viên report sao thì mới nhận xét đc giảng viên dạy có ổn hay không. Thêm cho tôi 2 chiều này. CI của PR cũng đang còn đỏ, fix luôn đi"; "ok"; "push lên PR đi" | `AnalyticsDimension.java`, `AnalyticsQueryRequest.java`, `EnrollmentRepository.java`, `AnalyticsServiceImpl.java`, `AnalyticsServiceImplTest.java`, `AnalyticsControllerIntegrationTest.java`, `doc/plan/F07-bao-cao-truy-vet.md`, `doc/sdd/02-component.md`, `doc/de-van-dap/f07-bao-cao-phan-tich-da-chieu.md`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | Spotless, SpotBugs (0 bugs), PMD CPD (0 duplications) PASS; `AnalyticsServiceImplTest` 39/39 PASS; `AnalyticsControllerIntegrationTest` 22/22 PASS trên MongoDB có bật `$jsonSchema` validator v0.7; JaCoCo line coverage đạt 100% (`AnalyticsController`), 98.46% (`MatrixCsvExporter`), 87.44% (`AnalyticsServiceImpl`) | PR #91 |

## B. Lỗi / ảo giác của AI đã phát hiện (mục tiêu ≥ 5, kèm phân tích nguyên nhân)

| # | Ngày | Mô tả lỗi | Phát hiện bằng cách nào | Nguyên nhân | Commit sửa |
|---|---|---|---|---|---|
| B1 | 2026-10-20 | Suýt viết `spring.data.mongodb.uri` vào file cấu hình mẫu | Chạy thực nghiệm: chỉ `spring.mongodb.uri` có tác dụng ở Spring Boot 4 (host vẫn là localhost nếu dùng key cũ) | API đổi giữa Spring Boot 3 và 4; AI dựa vào trí nhớ thay vì tài liệu/thử nghiệm | PR #1 (sinh viên điền mã) |
| B2 | 2026-10-23 | `rs.initiate` trong entrypoint lỗi khi chạy từ PowerShell; `docker stop` thoát mã 143 thay vì 0 | Chạy thật container: PowerShell làm mất dấu nháy kép trong `--eval`; `trap` không kết thúc đúng | Không tính quy tắc quote của PowerShell; xử lý tín hiệu chưa đúng. Đã chuyển lệnh qua stdin và sửa `trap` | `cbf22ac` |
| B3 | 2026-10-27 | Ghi công thức CLO/PI của `PLO_DB_UPDATE.md` §4 là "tham chiếu, chờ Khoa" và lấy ví dụ 72% làm phản biện; đề xuất làm `POINTS_RATIO` trước | Người dùng chỉ ra "làm theo công thức này"; AI mở lại §4.1–4.2 và viết lại `F06` §4.2 | Quá thận trọng, không hiểu đúng ý định người dùng (công thức trong tài liệu đã là lựa chọn của dự án) | `4377683` |
| B4 | 2026-10-27 | `F06` mục 4.4 ghi `ALL_PIS_PASSED`/`AVERAGE_PIS` "cài được ngay" dù tài liệu không có công thức cấp PLO | Khi liệt kê công thức còn thiếu (CT-04) thấy mâu thuẫn với mục 4.2 | Suy diễn "hiển nhiên" thành đặc tả; vi phạm quy tắc không bịa công thức/ngưỡng | `4377683` |
| B5 | 2026-10-27 | `F01` ghi `username` ≥ 3 ký tự như thể đã nằm trong validator MongoDB | Đối chiếu khối `users` trong `database_import.js`: validator không ép; chỉ `PLO_DB_UPDATE.md` ghi | Lẫn đặc tả với hợp đồng dữ liệu thực tế | `4377683` |
| B6 | 2026-10-27 | `ScopeAuthorizationMatrixIntegrationTest` ban đầu sử dụng chuỗi mock ID `"671dcba5f123456789abcttt"` chứa ký tự `'t'` (không phải hex) và `"prog-001"`, dẫn đến `ConversionFailedException` khi chuyển sang `ObjectId` do `targetType = FieldType.OBJECT_ID` | Chạy thực nghiệm `./mvnw test`: Surefire báo lỗi `Failed to convert from type [java.lang.String] to type [org.bson.types.ObjectId]` | Bỏ quên ràng buộc ObjectId (24 ký tự hex hợp lệ) khi tạo test fixture cho document và UserScope có `targetType = FieldType.OBJECT_ID`. Đã sửa thành các chuỗi 24 ký tự hex hợp lệ | _Chờ sinh viên điền commit_ |
| B7 | 2026-11-02 | Cổng JaCoCo có sẵn trong `pom.xml` không bao giờ fail: mẫu `com/hcmute/.../service/impl/**` đặt trong `<rule><includes>` không khớp gì | Đặt `minimum=0.99`, `haltOnFailure=true` mà build vẫn `BUILD SUCCESS` | Nhầm cú pháp `includes` của rule (tên gói dạng chấm) với `includes` cấp goal (tên tệp `.class`); cổng chưa từng đỏ nên không ai nhận ra | PR #62 |
| B8 | 2026-11-02 | google-java-format 1.37.0 (bản mới nhất lúc chọn) không chạy với Spotless 3.10.4 (`InvocationTargetException`) | Chạy thật trên JDK 25 | Chỉ tra "latest" ở Maven Central, không thử tương thích; đã ghim 1.36.1 | PR #62 |
| B9 | 2026-11-02 | `CorrelationIdFilter` ghi nguyên header client vào response/MDC (response splitting / log injection); không test nào kiểm tra | SpotBugs phát hiện | Tin dữ liệu từ client; đã xác thực ký tự `[A-Za-z0-9._-]{1,128}` và thêm 6 test | PR #62 |
| B10 | 2026-11-02 | Jackson chấp nhận số làm thứ tự enum: `{"contribution": 1}` tạo dòng ma trận `R`, `{"status": 1}` đặt `ACTIVE` (trái US-07/AC-2) | Test hợp đồng DTO mới: 3 ca đỏ trước khi sửa | Cấu hình Jackson mặc định cho phép ordinal; bật `FAIL_ON_NUMBERS_FOR_ENUMS` trong `JacksonConfig` | PR #61 |
| B11 | 2026-11-02 | Agent CI giả định `trivy fs` quét được JAR; thực tế báo "sạch" giả | Chạy thật so sánh với `trivy rootfs` | Giả định hành vi công cụ không kiểm chứng; đổi sang `rootfs` | PR #60 |
| B12 | 2026-11-02 | Agent CI ghi "thiếu JaCoCo trong pom" (sai: đã có nhưng `haltOnFailure=false`); agent tài liệu giả định luồng "tạo CTĐT + ghi audit trong transaction" (không có trong mã) | Tự đối chiếu mã/`grep` trước khi push | Suy từ kế hoạch/trí nhớ trước khi đọc mã; đã sửa ADR/README/sơ đồ | PR #59, #60 |
| B13 | 2026-11-02 | Lệnh lint trong hợp đồng CI (`-DskipTests spotless:check spotbugs:check`) khiến SpotBugs in "No files found" rồi vẫn thành công | Agent static-analysis kiểm chứng sau `clean`; điều phối viên sửa workflow | Thiếu `test-compile`; đã thêm `test-compile` và `pmd:cpd-check` | PR #60 (941bc9c) |
| B14 | 2026-11-02 | UI: regex `runtime-config` chấp nhận `//evil.example.org`; regex nginx `{8}` không đặt trong dấu nháy làm nginx không khởi động; image nền có CVE có bản vá | Test âm; chạy container thật; Trivy | Thiếu ca âm; cú pháp nginx; image nền cũ — đã thêm `apk upgrade` | PR #42 |
| B15 | 2026-11-02 | UI: import sai `NzToolTipModule` (đúng `NzTooltipModule`); regex nhận diện chi tiết nội bộ bỏ sót `MongoSocketException`; giả định chưa kiểm chứng "tỷ lệ là phân số 0–1" | TypeScript, test, và ghi vào điểm cần xác nhận hợp đồng | Nhớ sai tên API; regex quá hẹp; hợp đồng F06/F07 chưa có (TD-26) | PR #41 |
| B16 | 2026-11-04 | Nhầm phương thức getter của `AppException` (`getStatus()` thay vì `getHttpStatus()`) trong bài kiểm thử `TermServiceImplTest` | Trình biên dịch `javac` phát hiện khi chạy `mvnw test-compile` | Dựa vào quán tính API Response thay vì đọc trực tiếp lớp `AppException.java` | PR #74 |
| B17 | 2026-11-05 | Gộp trường `status` (`DRAFT`/`ACTIVE`/`ARCHIVED`) vào `ProgramVersionUpdateRequest` tại `PUT /api/v1/program-versions/{id}`, làm `PROGRAM_MANAGER` có thể tự chuyển `ACTIVE` khi sửa thông tin chung và lệch với thiết kế `f02-ctdt-plo-pi.md` (quy định tách riêng `PUT /api/v1/program-versions/{id}/status`) | Sinh viên phát hiện khi rà soát phân quyền giữa QA duyệt CTĐT và việc chỉnh sửa phiên bản CTĐT | Khi code F02, AI tiện tay gộp cập nhật trạng thái vòng đời vào chung DTO sửa metadata mà không tách endpoint `/status` như các tài nguyên khác (`User`, `Term`, `Syllabus`) | `fix/f02-separate-program-version-status` |
| B18 | 2026-11-05 | Khi tách `updateProgramVersionStatus` trong `ProgramVersionServiceImpl.java`, AI viết lại `getProgramVersions` dùng `PageResponse.builder().items(items)` trong khi `PageResponse` thực tế dùng trường `content` và `PageResponse.of(...)` | Trình biên dịch `javac` báo lỗi `cannot find symbol: method items(...)` khi chạy `mvnw test` | Ảo giác tên phương thức builder của `PageResponse` do không đọc lại phần đầu file `ProgramVersionServiceImpl.java` trước khi ghi file | `fix/f02-separate-program-version-status` |
| B19 | 2026-11-06 | Khi thiết kế ADR 0010 và hiện thực `DepartmentController`, AI chỉ tạo `POST /assign-course` và `/unassign-course` mà bỏ sót API cho Trưởng bộ môn lấy danh sách giảng viên (trong khi `GET /api/v1/users` chỉ mở cho `ADMIN`), đồng thời không kiểm tra phạm vi Khoa/Ngành (`scope.programIds`) giữa Trưởng bộ môn và Giảng viên khiến Trưởng bộ môn có thể thấy hoặc phân công nhầm giảng viên khoa khác | Sinh viên phát hiện khi phản biện kế hoạch F03 ("ê nhưng mà nếu mà làm như này thì bộ môn thấy được tất cả giảng viên để phân công đề cương luôn à") | AI giả định ngầm rằng UI có thể lấy danh sách giảng viên từ API người dùng chung và chỉ kiểm tra `scope.courseIds` trên môn học mà quên kiểm tra phạm vi đơn vị (`scope.programIds`) của giảng viên được phân công | `feat/f03-department-lecturers-scope` |
| B20 | 2026-11-08 | `EnrollmentCsvParser.Result` (record giữ `List`) và hai test `EnrollmentImportIntegrationTest` gọi `.getXxx()` trên kết quả `findById`/`findOne` có thể null; `./mvnw verify` báo 6 lỗi SpotBugs (`EI_EXPOSE_REP`, `EI_EXPOSE_REP2`, `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`) dù 1054 test đã xanh | Chạy `./mvnw -B -ntp verify` trọn vẹn trước khi đẩy mã (chặng `spotbugs:check` trong CI sẽ đỏ) | Chỉ chạy test từng nhóm nên chưa thấy chặng phân tích tĩnh; record công khai list có thể sửa từ ngoài; `assertThat(x).isNotNull()` không làm SpotBugs coi x là khác null. Sửa gốc: `List.copyOf` ở constructor và accessor, `Objects.requireNonNull` trong test (không tắt rule) | _Chờ sinh viên điền commit_ |
| B21 | 2026-11-08 | Test `statusChangeAuthorizationAndValidation` kỳ vọng ID ghi danh sai định dạng (`bad-id`) trả 400 nhưng API trả 403 | Chạy `EnrollmentIntegrationTest`: `Status expected:<400> but was:<403>` | Hiểu sai thứ tự: `@PreAuthorize` (fail-closed) chạy trước validate `@Pattern` ở path; hành vi này của mã đúng và nhất quán với TD-22. Sửa kỳ vọng của test kèm chú thích lý do (không sửa mã) | _Chờ sinh viên điền commit_ |
| B22 | 2026-11-08 | Định nghĩa thêm `canAccessSection(UserPrincipal, CourseSection, AccessType)` cạnh `canAccessSection(UserPrincipal, String, AccessType)`: các test cũ gọi `canAccessSection(principal, null, READ)` sẽ không biên dịch được (lời gọi mơ hồ) | Rà `grep "canAccessSection(.*null"` ở `src/test` trước khi biên dịch | Quên rằng literal `null` khớp cả hai overload. Đổi tên phương thức mới thành `canAccessLoadedSection` để không phải sửa test cũ | _Chờ sinh viên điền commit_ |
| B23 | 2026-11-08 | Hai người cùng duyệt một bài đánh giá: người đến sau nhận HTTP 500 thay vì 409 (AI chỉ tính tới `OptimisticLockingFailureException`) | Viết test đồng thời `AssessmentIntegrationTest#new01_concurrentApprovalHasASingleWinner`: trả 500, log báo MongoDB `WriteConflict` (mã 112) bọc trong `DataIntegrityViolationException` | Trong transaction MongoDB, xung đột ghi cùng document báo lỗi 112 ở máy chủ trước khi kiểm `@Version`; AI giả định chỉ có lỗi khóa lạc quan của Spring. Sửa: `GlobalExceptionHandler` ánh xạ 112 sang 409, thêm `WriteConflictHandlingTest` | `dcb4b2d` |
| B24 | 2026-11-08 | Test tìm nhật ký kiểm toán theo `entity.id` (theo `database_import.js` và mã truy vấn có sẵn) không thấy dòng nào dù đã ghi | Test `new01_ac3_approve` báo 0 dòng; `mongosh` cho thấy dữ liệu nằm ở `entity._id` và là ObjectId | Spring Data đổi thuộc tính tên `id` của đối tượng lồng nhau thành `_id` và đổi chuỗi ObjectId hợp lệ thành ObjectId; AI tin vào tên trường trong tài liệu thay vì dữ liệu thật. Ghi nợ kỹ thuật TD-34 (bộ lọc `entityId` hiện có cũng dùng `entity.id`) | _Chờ sinh viên điền commit_ |
| B25 | 2026-11-08 | Hai test gọi `putAs(..., read(result, "$.id"))` bị `ClassCastException: String cannot be cast to Object[]` | Chạy `AssessmentIntegrationTest` | `read` là phương thức generic `<T> T`, đặt ở vị trí tham số varargs `Object...` thì T bị suy ra là `Object[]`. Sửa: gán ra biến `String id` trước | _Chờ sinh viên điền commit_ |
| B26 | 2026-11-10 | Bản nháp đầu của `doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md` vẫn giữ công thức chia điểm câu hỏi theo trọng số `w_{p,k,j}` (`Score = RawScore * w`) và kiểm chứng "bảo toàn tổng điểm môn học" | Sinh viên (`minhat5`) đọc lại tài liệu và phát hiện lệch yêu cầu nghiệp vụ của GVHD | AI bị quán tính từ cấu trúc `outcomeMappings[{cloId, weight}]` và `level = COURSE` của CSDL v0.5 cũ. Đã sửa toàn bộ đặc tả: điểm từng CLO tại từng câu hỏi (kể cả câu có nhiều CLO) đều do giảng viên nhập trực tiếp không qua trọng số, bỏ đo kết quả bài kiểm tra/điểm môn học (`COURSE`), và bổ sung `cloId` vào khóa của `scores` | chưa commit |
| B27 | 2026-11-13 | Record `AssessmentScoreTemplate(String fileName, byte[] content)` trả về và nhận trực tiếp mảng mutable `byte[]`, vi phạm quy tắc an toàn biểu diễn nội bộ của SpotBugs (`EI_EXPOSE_REP`, `EI_EXPOSE_REP2`); đồng thời test fixture thừa field `adminToken` trong `DepartmentIntegrationTest` (`URF_UNREAD_FIELD`) | CI/Local chạy `spotbugs:check` bị fail với 3 bugs | AI dùng canonical record constructor mặc định cho `byte[]` mà không clone mảng dẫn đến nguy cơ lộ tham chiếu mutable; test fixture khai báo biến nhưng không assert. Sửa gốc: defensive copy `.clone()` trong compact constructor và accessor record, xóa field test thừa (tuân thủ quy tắc CLAUDE.md không dùng `@SuppressWarnings`) | `a6dc7e9` |
| B28 | 2026-11-13 | Trùng lặp mã nguồn (CPD 7.17.0): 111 tokens trích xuất `cloCodeMap` từ `AssessmentItem` qua `cloRepository` lặp giữa `AssessmentServiceImpl` và `ImportBatchServiceImpl`; 101 tokens assert MockMvc kiểm tra READ/WRITE bị cấm lặp trong `ScopeAuthorizationMatrixIntegrationTest` | CI/Local chạy `mvnw pmd:cpd-check` fail với `found 2 duplications` | Khi viết tính năng F05b và refactor vai trò QA, AI sao chép đoạn stream ánh xạ CLO thay vì gom vào `AssessmentItemValidator`; trong test copy lại khối `mockMvc.perform` thay vì trích helper method. Sửa: gom logic nghiệp vụ vào `AssessmentItemValidator.resolveCloCodeMap` và thêm helper `expectReadAndWriteForbidden` | _Chờ sinh viên điền commit_ |
| B29 | 2026-11-15 | Mã do multi-agent sinh cho F07 Analytics dù pass toàn bộ unit/integration tests và `spotless:check` nhưng làm đỏ 2 cổng phân tích tĩnh của CI: 5 lỗi SpotBugs (`DLS_DEAD_LOCAL_STORE` biến thừa `totalEvaluated` tại `AnalyticsServiceImpl:1321`; 4 lỗi `URF_UNREAD_FIELD` cho `clo2`, `plo1`, `otherLecturerToken` trong `AnalyticsControllerIntegrationTest` và `qaPrincipal` trong `AnalyticsServiceImplTest`) cùng 7 khối trùng lặp PMD CPD (`>= 100` tokens ở kiểm tra vai trò, dựng `MatrixHeader`, lọc `termId`, chuyển `AggregateResult` sang `MatrixMetricCell` lặp với cả `ReportServiceImpl`, và stub mock trong test) | Chạy `./mvnw -B -ntp test-compile spotbugs:check pmd:cpd-check spotless:check` trước khi tạo commit/PR | Subagent tập trung làm xanh test chức năng và Spotless mà bỏ sót chạy `spotbugs:check` + `pmd:cpd-check` với `includeTests=true`. Sửa tận gốc không dùng `@SuppressWarnings`: xóa biến chết `totalEvaluated`, xóa field fixture thừa và bổ sung unit test cho `qaPrincipal`, trích xuất `RoleFlags`/`resolveRoleFlags`, `toMetricCell`, `matchesTerm`, `toTermHeaders`/`toSectionHeaders`/`toCloHeaders`/`loadPloHeaders`/`loadPiHeaders` và helper `stubCourseSectionsCloScenario` | `e733fff` |
| B30 | 2026-11-15 | `AnalyticsControllerIntegrationTest` pass 18/18 trên DB test trống cục bộ nhưng làm đỏ GitHub Actions CI trên PR #91 do lỗi `Document failed validation` (MongoDB code 121) | Kiểm tra log CI của PR #91 và đối chiếu `$jsonSchema` trong `doc/database_import.js` | Môi trường local chạy test trên DB phụ chưa nạp `doc/database_import.js`, trong khi CI nạp đầy đủ `$jsonSchema` validator v0.7 cho 25 collections; fixture thủ công trong `AnalyticsControllerIntegrationTest` thiếu `syllabusId` trên `course_sections`, thiếu `scope` & `lockKey` trên `calculation_runs`, thiếu `scope` trên `aggregate_results` và sai chuẩn `clos`. Đã sửa tận gốc test fixture bằng `ApiTestFixtures` khớp 100% schema v0.7 (tuyệt đối không sửa schema) | PR #91 |

## C. Danh sách kiểm soát đầu ra AI đã áp dụng

Mỗi PR có AI hỗ trợ tick các mục: build/lint/test đã chạy thật; API thư viện đã đối chiếu tài liệu chính thức đúng phiên bản; dependency mới có lý do và giấy phép; không có secret/dữ liệu thật; không chép mã ngoài; nghiệp vụ đối chiếu `GiaiThich_CoSoDuLieu.md`.

## D. Nguồn và giấy phép của mã/tài liệu tham khảo

| Ngày | Nội dung | Nguồn (URL) | Giấy phép | Dùng ở đâu |
|---|---|---|---|---|
| 2026-11-02 | Spotless 3.10.4 + google-java-format 1.36.1; SpotBugs 4.10.5; PMD/CPD 7.17.0; JaCoCo 0.8.15; sonar-maven-plugin 5.8.0.7211 | Maven Central | Giấy phép ghi trong ADR 0005 (không đọc được từ POM — **sinh viên kiểm lại**) | `pom.xml` (PR #62) |
| 2026-11-02 | gitleaks 8.30.1, Trivy (binary ghim SHA-256), actions ghim phiên bản | GitHub releases | MIT / Apache-2.0 (kiểm tra lại) | `.github/workflows/ci.yml` (PR #60, UI #42) |
| 2026-11-02 | @playwright/test 1.63.0, playwright-core 1.63.0 (Apache-2.0), @axe-core/playwright 4.13.0 và axe-core 4.13.0 (MPL-2.0), @vitest/coverage-v8 4.1.12 | npm | Đọc từ `node_modules` (theo báo cáo agent) | `package.json` UI (PR #42) |

## E. Để vấn đáp — 4 câu hỏi bảo vệ đồ án tốt nghiệp trước Hội đồng (Dành cho Sinh viên IT và IBS)

Phần này chuẩn bị sẵn câu trả lời khoa học, chuẩn mực cho 4 câu hỏi trọng tâm mà Hội đồng chấm đồ án tốt nghiệp thường chất vấn. Nội dung được biên soạn song song theo hai góc nhìn: **Sinh viên chuyên ngành Công nghệ thông tin (IT)** và **Sinh viên chuyên ngành Kinh doanh số (IBS)**.

---

### Câu hỏi 1: Bạn đã làm gì trong bước này? (What did you do?)

#### Trả lời góc nhìn Sinh viên IT:
- **Kiến trúc mã nguồn 3 tầng chuẩn mực & SOLID:** Tái cấu trúc mã nguồn dự án `LMS-API` theo mô hình Package-by-Layer ở cấp gốc package `com.hcmute.lms.lmsapi`:
  - `controller`: Tiếp nhận HTTP Request, kích hoạt Bean Validation, chuyển đổi và trả về DTO.
  - `dto`: Chứa các lớp truyền dữ liệu (`ErrorResponse`, `FieldErrorDetail`), tách bạch hoàn toàn khỏi tầng exception và document theo nguyên lý Single Responsibility (SRP).
  - `service` (Interface) & `service.impl`: Chứa hợp đồng và logic nghiệp vụ, quản lý giao dịch `@Transactional`, không phụ thuộc vào Servlet HTTP (pure service).
  - `repository` (Interface) & `repository.impl`: Chứa hợp đồng Spring Data `MongoRepository` và triển khai truy vấn động phức tạp bằng `MongoTemplate` (`AuditLogRepositoryCustomImpl`).
  - `document`: Ánh xạ các thực thể CSDL MongoDB và Enum nghiệp vụ khớp với `$jsonSchema`.
  - `mapper` (Interface) & `mapper.impl`: Chuyển đổi dữ liệu và đảm nhiệm khử nhiễm dữ liệu nhạy cảm (`AuditLogMapperImpl`), tách rời khỏi Service theo SRP và DIP.
  - `exception`: Xử lý ngoại lệ tập trung với `GlobalExceptionHandler` và `ErrorCode`.
  - `config` & `filter`: Cấu hình hệ thống và bộ lọc `CorrelationIdFilter`.
- **Triển khai US-40 (Xử lý lỗi tập trung):** Xây dựng `@RestControllerAdvice` duy nhất chuyển đổi mọi ngoại lệ thành cấu trúc JSON chuẩn `{ timestamp, status, code, message, path, fieldErrors }`. Phân định chính xác các mã trạng thái HTTP (400, 401, 403, 404, 409, 422, 500) và che giấu hoàn toàn stack trace khi gặp lỗi 500.
- **Triển khai US-39 (Giám sát & Truy vết):** Tích hợp `spring-boot-starter-actuator` (chỉ mở `health` và `info`), cấu hình `SecurityConfig` mở công khai cho endpoint giám sát, và triển khai bộ lọc `CorrelationIdFilter` quản lý `X-Correlation-Id` cùng SLF4J MDC.
- **Cấu hình MongoDB Replica Set & Transaction:** Kích hoạt `MongoTransactionManager` trên cụm replica set `rs0`, đảm bảo tính nguyên tử cho các giao dịch nhiều collection (ví dụ nạp điểm đồng thời ghi `audit_logs`).
- **Tài liệu Hợp đồng liên repo:** Biên soạn `LMS-UI/doc/API-CONTRACT.md`, thiết lập tiếng nói chung giữa Frontend và Backend.
- **Tự động hóa CI Pipeline:** Cấu hình `.github/workflows/ci.yml` khởi chạy MongoDB 8.0 Replica Set, nạp schema và chạy `./mvnw -B verify` tự động khi tạo Pull Request.

#### Trả lời góc nhìn Sinh viên IBS:
- **Chuẩn hóa quy trình nghiệp vụ số:** Thiết lập nền tảng kỹ thuật bảo đảm tính pháp lý và độ tin cậy của quy trình đo lường chuẩn đầu ra (OBE).
- **Thiết lập quy chế kiểm toán số (Digital Audit Trail):** Quy định rõ 7 loại hành vi bắt buộc phải lưu vết kiểm toán trong `audit_logs` (nhập điểm, cập nhật điểm, duyệt ma trận đề thi, phê duyệt chuẩn đầu ra...). Đặc biệt, bắt buộc phải có trường `reason` (lý do giải trình) khi cập nhật điểm (`SCORE_UPDATE`), ngăn chặn hành vi sửa điểm tùy tiện.
- **Chuẩn hóa trải nghiệm người dùng số (UX Standardization):** Phối hợp xây dựng bảng ánh xạ mã lỗi trong `API-CONTRACT.md`. Chuyển hóa các lỗi kỹ thuật phức tạp thành các thông điệp cảnh báo thân thiện bằng tiếng Việt, giúp giảng viên và sinh viên hiểu rõ lý do bị từ chối (ví dụ: vi phạm quy tắc ma trận đề thi, điểm vượt quá thang điểm, xung đột phiên bản chỉnh sửa).
- **Đảm bảo tính toàn vẹn dữ liệu học thuật:** Đưa ra yêu cầu bắt buộc rằng mọi thao tác cập nhật điểm số phải đi kèm giao dịch (Transaction) để không bao giờ xảy ra tình trạng "điểm đã vào hệ thống nhưng không rõ ai nhập" hoặc "có vết kiểm toán nhưng dữ liệu điểm bị lỗi".

---

### Câu hỏi 2: Vì sao phần này có mặt và đóng vai trò gì trong đề tài đo lường chuẩn đầu ra? (Why in thesis?)

#### Trả lời góc nhìn Sinh viên IT:
- **Ngăn ngừa suy thoái kiến trúc (Architectural Erosion):** Một hệ thống đo lường chuẩn đầu ra đại học có miền nghiệp vụ rất phức tạp (tính toán qua nhiều cấp độ: Câu hỏi → CLO → PI → PLO theo nhiều trọng số). Nếu không áp dụng mô hình 3 tầng và các nguyên lý SOLID ngay từ Bước 00, mã nguồn sẽ nhanh chóng rơi vào tình trạng kết dính chặt (tightly coupled), các hàm nghiệp vụ đo lường bị lẫn lộn vào Controller hoặc truy vấn DB, dẫn đến việc không thể viết Unit Test độc lập và không thể mở rộng ở các bước F06, F07.
- **Bảo đảm tính nguyên tử (Atomicity) trong CSDL NoSQL:** MongoDB là hệ cơ sở dữ liệu Document. Khi thực hiện quy trình nhập điểm cho một lớp học phần hàng trăm sinh viên, hệ thống phải cập nhật song song nhiều collection (`student_results`, `course_sections`, `audit_logs`). Nếu không có Replica Set và `MongoTransactionManager`, bất kỳ sự cố mạng hoặc ngoại lệ nào ở giữa tiến trình sẽ để lại dữ liệu rác mồ côi (orphaned data), phá hủy tính đúng đắn của toàn bộ kết quả đo lường sau đó.
- **An toàn thông tin (Information Exposure Prevention):** Xử lý ngoại lệ tập trung giúp ngăn ngừa rò rỉ mã nguồn, tên class, câu lệnh truy vấn MongoDB hoặc cấu trúc hạ tầng máy chủ ra bên ngoài (tuân thủ tiêu chuẩn an toàn OWASP Top 10 và CWE-209).

#### Trả lời góc nhìn Sinh viên IBS:
- **Đáp ứng các tiêu chuẩn kiểm định giáo dục quốc tế (AUN-QA & ABET):** Trong kiểm định chất lượng đào tạo, minh chứng (Evidence) phải đảm bảo 3 yếu tố: Tính xác thực (Authenticity), Tính toàn vẹn (Integrity) và Khả năng truy xuất nguồn gốc (Traceability). Bước 00 đặt nền móng quản trị (Governance Framework): Mọi số liệu đo lường phục vụ báo cáo kiểm định sau này đều được truy vết nguồn gốc rõ ràng thông qua `audit_logs`.
- **Tối ưu hóa chi phí vận hành và rủi ro triển khai:** Hợp đồng giao tiếp `API-CONTRACT.md` giúp hai nhóm phát triển Frontend và Backend phối hợp ăn khớp, giảm thiểu thời gian kiểm thử tích hợp, tránh tình trạng "lệch pha" giao diện gây lãng phí nguồn lực đồ án.

---

### Câu hỏi 3: Các tham số, cấu hình, thư viện từ đâu mà có? (Where do params/libs come from?)

#### Trả lời góc nhìn Sinh viên IT:
- **`spring-boot-starter-actuator`:** Kế thừa từ bộ khung Spring Boot 4.1.0; phiên bản được quản lý tập trung bởi `spring-boot-starter-parent` nhằm loại trừ rủi ro xung đột thư viện (dependency hell).
- **`spring.mongodb.uri` và tham số `replicaSet=rs0`:** Kế thừa từ quy chuẩn kết nối của Spring Data MongoDB 4.x. Tham số `replicaSet=rs0` tương ứng chính xác với tên cụm Replica Set được khởi tạo trong Docker script `docker/mongo/entrypoint.sh` và service MongoDB của GitHub Actions CI runner.
- **Mã lỗi 121 của MongoDB:** Là mã định danh chuẩn (`Error Code 121: DocumentValidationFailure`) do MongoDB Server phát sinh khi một thao tác ghi tài liệu vi phạm quy tắc kiểm tra schema trong `doc/database_import.js`.
- **Cấu trúc DTO `ErrorResponse` và Header `X-Correlation-Id`:** Tuân thủ chuẩn mực thiết kế REST API (RFC 7231, RFC 7807 Problem Details) và mô hình truy vết phân tán W3C Trace Context, sử dụng SLF4J MDC (Mapped Diagnostic Context) có sẵn trong Logback.
- **Phân tách Package-by-Layer và Interface/Impl:** Căn cứ theo quy định tại `doc/coding-convention.md` §2, `.claude/rules/03-code-quality-and-git.md` và 5 nguyên lý thiết kế phần mềm kinh điển SOLID của Robert C. Martin (Uncle Bob).

#### Trả lời góc nhìn Sinh viên IBS:
- **Danh mục 7 giá trị trong `AuditAction`:** Được trích xuất từ tài liệu phân tích nghiệp vụ quản lý đào tạo `doc/GiaiThich_CoSoDuLieu.md` §4.29 và validator CSDL thực tế trong `doc/database_import.js`.
- **Quy tắc bắt buộc nhập `reason` khi `SCORE_UPDATE`:** Bắt nguồn từ Quy chế đào tạo đại học chính quy của Bộ GD&ĐT và quy trình phúc khảo, sửa điểm của Trường ĐH Sư phạm Kỹ thuật TP.HCM (mọi điều chỉnh điểm sau khi chốt sổ đều bắt buộc phải có căn cứ bằng văn bản).
- **Các quy tắc ánh xạ lỗi trên giao diện:** Dựa trên phương pháp phân tích trải nghiệm người dùng (UX Persona & Journey Mapping), đảm bảo rằng người dùng không bị mất trắng dữ liệu form khi xảy ra lỗi xác thực (400) hoặc lỗi nghiệp vụ (422).

---

### Câu hỏi 4: Nếu bỏ thành phần này đi thì hệ thống hoặc đề tài sẽ bị ảnh hưởng/phá vỡ ra sao? (What breaks if removed?)

#### Trả lời góc nhìn Sinh viên IT:
- **Nếu bỏ kiến trúc 3 tầng và SOLID (viết chung mã phẳng hoặc gọi DB từ Controller):**
  - Vi phạm nguyên lý Đơn trách nhiệm (SRP) và Đảo ngược phụ thuộc (DIP).
  - Controller bị gắn chặt với Mongo Driver; khi cần thay đổi thuật toán tính chuẩn đầu ra hoặc chuyển đổi cấu trúc lưu trữ, lập trình viên buộc phải sửa hàng loạt Controller.
  - Không thể viết Unit Test với Mockito cho tầng Service nếu Controller tự ý truy cập Repository. Dự án sẽ bị đánh trượt hoặc trừ điểm nặng ở Tiêu chí Đánh giá Chất lượng Mã nguồn (TC2.4).
- **Nếu bỏ MongoDB Replica Set `rs0`:**
  - `MongoTransactionManager` của Spring Boot sẽ ném ngoại lệ khi khởi động hoặc khi thực thi `@Transactional` vì MongoDB Standalone không hỗ trợ multi-document transactions.
  - Khi lưu điểm sinh viên thất bại ở sinh viên thứ 50, 49 sinh viên trước đó vẫn nằm lại trong CSDL còn bản ghi `audit_logs` có thể không được tạo, gây bất nhất dữ liệu nghiêm trọng.
- **Nếu bỏ `@RestControllerAdvice` (`GlobalExceptionHandler`):**
  - Mọi lỗi ngoại lệ hệ thống sẽ trả về trang HTML mặc định của Tomcat hoặc HTTP 500 kèm toàn bộ Stack Trace. Kẻ tấn công có thể khai thác thông tin này để tấn công cơ sở dữ liệu. Đồng thời, Frontend Angular sẽ bị lỗi phân tích cú pháp (parse error), làm giao diện bị đơ (crash UI).
- **Nếu bỏ `CorrelationIdFilter`:**
  - Khi có hàng trăm sinh viên và giảng viên đồng thời sử dụng hệ thống, các dòng log ở server sẽ đan xen nhau. Quản trị viên hệ thống hoàn toàn không thể biết dòng lỗi nào thuộc về request nào, gây bế tắc trong khâu điều tra sự cố.

#### Trả lời góc nhìn Sinh viên IBS:
- **Nếu bỏ kiểm toán `audit_logs` và ràng buộc `reason`:**
  - Đề tài mất đi tính minh bạch và độ tin cậy học thuật. Khi có nghi vấn về việc sửa điểm hoặc gian lận kết quả đo lường chuẩn đầu ra, hệ thống không có bằng chứng đối soát. Báo cáo đo lường sẽ bị Hội đồng kiểm định chất lượng từ chối công nhận.
- **Nếu bỏ tài liệu hợp đồng `API-CONTRACT.md`:**
  - Quy trình phát triển phần mềm bị phân rã; Frontend và Backend sẽ liên tục phát sinh lỗi không tương thích, kéo dài thời gian hoàn thành đồ án và gây trải nghiệm tồi tệ cho người dùng cuối.

---

> **Lưu ý hồ sơ vấn đáp phân hệ F01 (US-01, US-02, US-03):**  
> Toàn bộ 4 câu hỏi bảo vệ đồ án tốt nghiệp chuyên sâu cho phân hệ F01 (Xác thực Nimbus Resource Server, Quản lý phiên tokenVersion và Phân quyền phạm vi canAccessSection) theo góc nhìn kép Sinh viên IT và Sinh viên IBS được lưu trữ chi tiết tại:  
> `doc/de-van-dap/f01-xac-thuc-phan-quyen.md`.


---

## Phiên #15 — 2026-11-03: Cập nhật phân quyền khởi tạo danh mục CTĐT cho ADMIN và kích hoạt kiểm tra Scope cho PROGRAM_MANAGER (ADR 0005)

- **Sinh viên yêu cầu:** TinMH (IT) / minhat5
- **Mục tiêu:**
  1. Ban hành ADR 0005: Chuyển quyền tạo/sửa danh mục Ngành đào tạo (`POST/PUT /api/v1/programs`) từ `PROGRAM_MANAGER` sang `ADMIN` để giải quyết dứt điểm nghịch lý "con gà - quả trứng" với `ScopeValidationService` (F01) và bảo vệ Master Data cấp trường.
  2. Bổ sung `canAccessProgramVersion` vào `ScopeAuthorizationService`/`ScopeAuthorizationServiceImpl` và kích hoạt kiểm tra bắt buộc `scope.programIds` (`canAccessProgram` / `canAccessProgramVersion`) cho mọi thao tác ghi của `PROGRAM_MANAGER` trên `ProgramVersion`, `PLO`, `PI`, và ma trận I/R/M (`CoursePiContribution`).
  3. Cập nhật bộ kiểm thử tích hợp `ProgramIntegrationTest`, unit test `ProgramScopeAuthorizationTest`, và đồng bộ toàn bộ tài liệu dự án (`doc/adr/0005-...`, `doc/plan/F02-...`, `doc/sdd/02-...`, `doc/sdd/04-...`, `doc/srs/README.md`, `doc/test-plan.md`, `doc/de-van-dap/f02-...`).
- **Mã nguồn đã chỉnh sửa:**
  - `doc/adr/0005-phan-quyen-khoi-tao-danh-muc-ctdt.md`: Tạo mới ghi nhận quyết định kiến trúc.
  - `ScopeAuthorizationService.java` & `ScopeAuthorizationServiceImpl.java`: Bổ sung `canAccessProgramVersion(principal, programVersionId, accessType)`.
  - `ProgramController.java`: Sửa `@PreAuthorize` của `createProgram` và `updateProgram` sang `hasRole('ADMIN')`.
  - `ProgramVersionController.java`, `PloController.java`, `PiController.java`, `CoursePiContributionController.java`: Bổ sung SpEL `@scopeAuthorizationService.canAccessProgram(...)` và `canAccessProgramVersion(...)` kiểm tra `scope.programIds` cho `PROGRAM_MANAGER`.
  - `ProgramScopeAuthorizationTest.java`: Bổ sung unit test cho `canAccessProgramVersion`.
  - `ProgramIntegrationTest.java`: Cập nhật `ADMIN` tạo `Program` (201), `PROGRAM_MANAGER` tạo `Program` bị từ chối (403); `PROGRAM_MANAGER` tạo `ProgramVersion` ngoài scope bị từ chối (403), đúng scope thành công (201).
  - Cập nhật tài liệu: `doc/plan/F02-ctdt-plo-pi.md`, `doc/sdd/02-component.md`, `doc/sdd/04-sequence.md`, `doc/srs/README.md`, `doc/test-plan.md`, `doc/de-van-dap/f02-ctdt-plo-pi.md`.
- **Kết quả kiểm thử:**
  - `mvn test -Dtest=ProgramIntegrationTest,ProgramScopeAuthorizationTest`: 12/12 tests passed (BUILD SUCCESS).
  - `mvn test -Dtest=ScopeAuthorizationMatrixIntegrationTest,ScopeAuthorizationEmpiricalChallengeTest`: 29/29 tests passed (BUILD SUCCESS).


---

## Phiên #16 — 2026-11-03: Hiện thực hóa phân hệ F03 (US-09) — Quản lý Học phần và Gán học phần vào Phiên bản CTĐT (Issue #20)

- **Sinh viên yêu cầu:** TinMH (IT) / minhat5
- **Mục tiêu:**
  1. Rà soát toàn bộ các GitHub Issues thuộc phân hệ F03 (`#20`, `#21`, `#22`, `#23`).
  2. Đánh giá tính khả thi và dữ kiện đầu vào: Xác định Issue #20 (`[F03] US-09`) có đầy đủ dữ kiện, đặc tả và điều kiện tiên quyết từ F00/F01/F02; các Issue #21 (US-10), #22 (US-11), #23 (US-12) bị vướng các phụ thuộc chưa hoàn thành (F05a `terms`, tài liệu đề cương gốc Q02, ngưỡng CLO Q12, ma trận CLO-PI duyệt từ Khoa Q14).
  3. Hiện thực trọn vẹn US-09 bao gồm:
     - Enums: `CourseType` (`COMPULSORY`, `ELECTIVE`), `CourseCategory` (`FOUNDATION`, `MAJOR_FOUNDATION`, `SPECIALIZED`, `CAPSTONE_PROJECT`, `THESIS`).
     - Entity: `ProgramCourse` (`program_courses`) với compound unique index `{'programVersionId': 1, 'courseId': 1}`.
     - Repositories: `CourseRepository`, `ProgramCourseRepository`, `CoursePiContributionRepository.existsByCourseId`.
     - DTOs & Mappers: `CourseRequest`, `CourseUpdateRequest`, `CourseResponse`, `ProgramCourseRequest`, `ProgramCourseUpdateRequest`, `ProgramCourseResponse`, `CourseMapper`, `ProgramCourseMapper`.
     - Services & Controllers: `CourseServiceImpl`, `ProgramCourseServiceImpl`, `CourseController` (`/api/v1/courses`), `ProgramCourseController` (`/api/v1/program-versions/{programVersionId}/courses`).
     - Kiểm soát truy cập Scope-based Security: tích hợp `@scopeAuthorizationService.canAccessProgramVersion` (ADR 0005) bảo đảm fail-closed cho `PROGRAM_MANAGER`.
     - Toàn vẹn tham chiếu cấp ứng dụng: Kiểm tra học phần tiên quyết `prerequisiteCourseIds` (chặn không tồn tại, chặn tự tham chiếu), cơ chế Restrict Delete (chặn xóa học phần đã được gán vào `program_courses` hoặc `course_pi_contributions`).
  4. Kiểm thử toàn diện:
     - Unit tests: `CourseServiceImplTest` (8 tests), `ProgramCourseServiceImplTest` (9 tests).
     - Validation tests: `CourseValidationTest` (5 tests).
     - MockMvc Integration tests: `CourseIntegrationTest` (10 tests) chạy trên MongoDB Replica Set thật (`rs0`), bao phủ US-09/AC-1 (tạo học phần, 409 trùng code), US-09/AC-2 (gán vào CTĐT, 409 trùng), US-09/AC-3 (422 tiên quyết không hợp lệ), RBAC & Scope Authorization (403), toàn vẹn Restrict Delete (422).
  5. Soạn thảo hồ sơ vấn đáp bảo vệ đồ án tốt nghiệp `doc/de-van-dap/f03-hoc-phan-clo.md` theo góc nhìn bổ trợ IT & IBS.
- **Kết quả kiểm định chất lượng (DoD):**
  - Spotless formatting: Tất cả 216 files tuân thủ google-java-format.
  - SpotBugs analysis: 0 bugs found.
  - PMD CPD duplication check: 0 duplications found.
  - JaCoCo coverage: Vượt ngưỡng kiểm định.
  - Toàn bộ test suite dự án: 870/870 tests passed (0 failures, 0 errors).


---

## Phiên #17 — 2026-11-04: Hiện thực hóa US-04 — API xem nhật ký thao tác `GET /api/v1/audit-logs` (Issue #18)

- **Sinh viên yêu cầu:** TinMH (IT)
- **Công cụ / mô hình:** Claude Code, claude-sonnet-5-5
- **Mục tiêu:** Rà soát F01 trên GitHub Issues, làm US-04 (#18) và hoãn NEW-03 (#19, gắn nhãn `hoãn`).
- **File AI sinh/sửa (chưa commit, nhánh `feat/f01-us04-audit-logs-api`):**
  - Mã: `controller/AuditLogController`, `service/AuditLogQueryService` + `impl/AuditLogQueryServiceImpl`, `dto/AuditLogResponse`; sửa `repository/AuditLogRepositoryCustom` + `impl` (thêm `searchLogs` phân trang, lọc `userId` bằng ObjectId), `mapper/AuditLogMapper` + `impl` (thêm `toResponse`, làm sạch lại `before`/`after` khi đọc).
  - Test: `AuditLogApiIntegrationTest` (15 ca), `AuditLogQueryServiceImplTest` (4), thêm ca vào `AuditLogRepositoryCustomUnitTest` (2) và `AuditLogMapperUnitTest` (1).
  - Tài liệu: `doc/plan/F01-nguoi-dung-phan-quyen.md` (AC US-04/AC-1..5, bảng API, mục 9), `doc/test-plan.md`, và `LMS-UI/doc/API-CONTRACT.md` §5.5 (repo UI, chưa commit).
- **Quyết định của AI cần sinh viên xác nhận:**
  - Chỉ `ADMIN` được đọc (issue ghi "ADMIN và/hoặc QA_OFFICER — hỏi"). Chưa hỏi Khoa/GVHD.
  - Tên tham số lọc `entity` (tên collection) + `entityId` + `userId` + `from`/`to`; đây là lựa chọn của AI bám theo chữ trong issue.
  - Không thêm dependency, không đổi schema CSDL.
- **Kiểm chứng đã làm:**
  - `./mvnw test` cho 4 lớp test liên quan US-04 (`AuditLogApiIntegrationTest`, `AuditLogQueryServiceImplTest`, `AuditLogRepositoryCustomUnitTest`, `AuditLogMapperUnitTest`): 26/26 pass; `spotless:check` và `spotbugs:check` = 0 lỗi.
  - Lượt chạy đầu dùng MongoDB đơn lẻ cổng 27017 (database tạm, đã xóa) vì Docker đang tắt; `ApiContractIntegrationTest#f01ResponsesDoNotLeakSecrets` lỗi ở lượt đó do thiếu transaction.
  - Phát hiện DB Docker cũ (nạp tay bằng `mongo:8.0`) thiếu `previousCourseIds` ở `program_courses` và còn `departmentId`, `ml_experiments` so với `database_import.js` hiện tại; image `lms-mongo` cũng build trước commit `6bec776`. Đã build lại `lms-mongo:latest` và chạy container `lms-mongo-rs0` (cổng 27018, volume mới `lms-mongo-data-v2`); volume và container cũ giữ nguyên, chưa xóa.
  - `./mvnw -B -ntp -o verify` trên `rs0` mới: **895 test, 0 lỗi, 0 skip, BUILD SUCCESS** (gồm spotless, SpotBugs, JaCoCo trong `verify`). Chưa xem lần chạy CI trên GitHub.
- **Lỗi AI tự phát hiện khi làm (để điền bảng B):**
  - Fixture test repository giả lập "trang đầy" sai: `PageableExecutionUtils.getPage` suy tổng từ offset khi trang ngắn nên không gọi `count` (test báo 21 thay vì 41). Sửa fixture, không sửa mã.
  - Assertion giả định `Query.limit(-1).skip(-1)` cho giá trị -1; thực tế `isLimited()` = false. Sửa test theo hành vi thật.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** chưa commit. PR nên ghi `Closes #18`; sinh viên IBS review.

---

## Phiên #18 — 2026-11-03: Hiện thực hóa phân hệ F03 các nội dung liên quan đến CLO (US-10, US-11, US-12)

- **Sinh viên yêu cầu:** TinMH (IT) / minhat5
- **Mục tiêu:**
  1. Hiện thực hóa các thành phần liên quan đến CLO trong `doc/plan/F03-hoc-phan-clo.md`:
     - Quản lý đề cương môn học theo phiên bản (`syllabi` - US-10).
     - Quản lý chuẩn đầu ra học phần (`clos` - US-11).
     - Quản lý liên kết CLO-PI và ma trận CLO x PI (`clo_pi_links` - US-12).
  2. Xử lý nghiêm ngặt các nội dung chưa được xác nhận để tránh làm sai:
     - Q02: Không tự seed văn bản đề cương từ trí nhớ; `contentOriginal` để trống chuỗi rỗng `""` hoặc nhận từ client.
     - Q12: Không gán giá trị mặc định cho `clos.measurement`; để `null` cho tới khi Khoa xác nhận.
     - Q14: Không tự suy diễn hay gán ma trận CLO-PI giả định.
     - Q22: `weight` trong `clo_pi_links` được phép `null`.
     - F05a: Chưa có `terms` nên chưa so sánh thời gian thực tế `effectiveTo >= effectiveFrom`.
  3. Cài đặt các Entity NoSQL MongoDB:
     - `Syllabus` (`syllabi`) với compound unique index `{'courseId': 1, 'version': 1}`.
     - `Clo` (`clos`) với compound unique index `{'syllabusId': 1, 'code': 1}`.
     - `CloPiLink` (`clo_pi_links`) với compound unique index `{'cloId': 1, 'piId': 1}`.
  4. Cài đặt DTOs, Mappers, Repositories, Services và Controllers cho:
     - `/api/v1/courses/{courseId}/syllabi`, `/api/v1/syllabi/{id}/status`
     - `/api/v1/syllabi/{syllabusId}/clos`, `/api/v1/clos/{id}`
     - `/api/v1/syllabi/{syllabusId}/clo-pi-links`, `/api/v1/clo-pi-links/{id}`, `/api/v1/syllabi/{syllabusId}/clo-pi-matrix`, `/api/v1/clo-pi-links/{id}/verification`
  5. Ràng buộc toàn vẹn và nghiệp vụ:
     - Đề cương `CONFIRMED` đóng băng (freeze) không cho sửa/xóa/thêm CLO (HTTP 422 - US-10/AC-3).
     - Restrict Delete: Chặn xóa CLO khi đã có trong liên kết `clo_pi_links` (HTTP 422).
     - Regex mã CLO: `^CLO[0-9]+$` và bloomLevel 1-6 (HTTP 400).
     - Scope Authorization cho `QA_OFFICER` khi thẩm định liên kết ma trận (HTTP 403 khi out-of-scope).
  6. Kiểm thử tự động:
     - Unit tests: `SyllabusServiceImplTest` (9 tests), `CloServiceImplTest` (10 tests), `CloPiLinkServiceImplTest` (7 tests).
     - Validation tests: `CloValidationTest` (5 tests).
     - MockMvc Integration tests: `CloIntegrationTest` (4 tests) chạy trên MongoDB Replica Set thật `rs0`.
  7. Bổ sung hồ sơ vấn đáp `doc/de-van-dap/f03-hoc-phan-clo.md` giải thích lý do nhận diện CLO bằng `_id`, phiên bản hóa đề cương, và collection `clo_pi_links`.
- **Kết quả kiểm định chất lượng (DoD):**
  - Spotless formatting: 255 files clean.
  - SpotBugs: 0 bugs found.
  - PMD CPD: 0 duplications found.
  - Test suites: 35/35 new tests passed; toàn bộ test suite dự án đạt 100%.


---

## Phiên #19 — 2026-11-04: Hoàn thiện nhánh F03 — sửa SpotBugs, phân quyền theo phạm vi cho đề cương/CLO (ADR 0009), đồng bộ tài liệu

- **Sinh viên yêu cầu:** TinMH (IT)
- **Công cụ / mô hình:** Claude Code, claude-sonnet-5-5 (có dùng subagent cho việc soạn tài liệu và rà soát chéo mã)
- **Mục tiêu:**
  1. Kiểm chứng nhánh `feat/f03-us10-us11-us12-syllabus-clo` bằng `./mvnw verify`: phát hiện `URF_UNREAD_FIELD` (SpotBugs) ở `CloIntegrationTest` (`pmOutOfScopeToken`, `lecturerToken`) và xung đột `logsAI/ai-usage-log.md` khi gộp `main`.
  2. Sửa SpotBugs bằng cách **viết test dùng hai token đó** (không tắt công cụ, không xóa field để qua mặt).
  3. Phát hiện endpoint ghi của `syllabi`/`clos`/`clo_pi_links` không kiểm `scope.programIds` của `PROGRAM_MANAGER` (trái ADR 0005 §3.2). Sinh viên chọn **phương án A**: suy phạm vi qua `program_courses` (ADR 0009, đề xuất, chờ GVHD/Khoa xác nhận).
  4. Gộp `main` vào nhánh, gộp tay log (đánh lại số phiên #17/#18), đồng bộ tài liệu F03/F05a và `LMS-UI/doc/API-CONTRACT.md`.
- **File AI sinh/sửa (chưa commit):**
  - Mã: `ScopeAuthorizationService` + `ScopeAuthorizationServiceImpl` (thêm `canAccessCourse/Syllabus/Clo/CloPiLink`); `SyllabusController`, `CloController`, `CloPiLinkController` (SpEL kiểm scope cho thao tác ghi).
  - Test: `CloIntegrationTest` (+6 ca scope: US-10/AC-4 ×3, US-11/AC-5, US-12/AC-6 ×2; fixture gán học phần vào CTĐT), `CourseScopeAuthorizationTest` (15 ca unit), `ApiTestFixtures.programCourse` + dọn `program_courses`.
  - Tài liệu: `doc/adr/0009-phan-quyen-pham-vi-de-cuong-clo.md`, `doc/plan/F03-hoc-phan-clo.md`, `doc/plan/F05a-hoc-ky-lop-sinh-vien.md`, `doc/de-van-dap/f03-hoc-phan-clo.md`, `doc/sdd/02-component.md`, `doc/sdd/04-sequence.md`, `doc/srs/README.md`, `doc/test-plan.md`; repo UI: `doc/API-CONTRACT.md` (§6.2, §6.4.1).
- **Quyết định của AI cần sinh viên xác nhận:**
  - PM đọc/ghi: học phần chưa thuộc CTĐT nào thì **chỉ ADMIN ghi được** (hệ quả: ADMIN tạo học phần và gán vào CTĐT trước khi PM soạn đề cương).
  - Tài nguyên không tồn tại: PM nhận 403 (không phải 404), nhất quán `canAccessProgramVersion`.
  - Quyền ghi `courses`/`terms` vẫn `ADMIN`/`PROGRAM_MANAGER` không kiểm scope (ngoài phạm vi ADR 0009).
  - US-18/AC-2 (từ chối nhập điểm khi `SCORE_LOCKED`) **chờ F05b**; Issue #28 chưa đủ điều kiện đóng.
  - Quy tắc 6 plan F03 (không xóa CLO/liên kết đã được `outcomeMappings`/`student_results` tham chiếu) **chờ F06**.
- **Kiểm chứng đã làm:**
  - `./mvnw -B verify` trên worktree nhánh F03 + `main` gộp: 972 test, 0 lỗi, 0 skip, BUILD SUCCESS (gồm Spotless, SpotBugs, JaCoCo, CPD). Trước khi sửa: 929 test pass nhưng SpotBugs báo 2 lỗi.
  - Chạy trên MongoDB replica set `rs0` cổng 27018.
  - Công cụ agy (Antigravity) không dùng được trong phiên này (quota 429), nên không có ý kiến từ agy; phản biện do AI tự thực hiện và một subagent rà soát độc lập.
- **Lỗi AI tự phát hiện khi làm (để điền bảng B):**
  - Lượt chạy test đầu của `us11_ac5_cloWriteIsScoped` fail 400 thay vì 403 do `CloUpdateRequest` bắt buộc `code`; sửa dữ liệu test (không sửa mã).
  - Subagent rà soát độc lập phát hiện `canAccessCourse` cộng dồn quyền QA_OFFICER (ngành khác) thành quyền GHI; đã sửa (chỉ tính `PROGRAM_MANAGER`/`ADMIN`) và thêm test hồi quy. Các phát hiện còn lại chưa sửa vì cần quyết định thiết kế, đã ghi `doc/tech-debt.md` TD-27…TD-29 và ADR 0009 §4.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** chưa commit. PR nên ghi `Closes #21`, `Closes #22`, `Closes #23`; **không** ghi `Closes #28` (chờ F05b). Sinh viên IBS review.

---

## Phiên #20 — 2026-11-04: Tiếp nhận dữ liệu CTĐT Fintech và 2 đề cương chi tiết học phần thử nghiệm, đối chiếu yêu cầu và cập nhật tiến độ

- **Sinh viên yêu cầu:** TinMH (IT)
- **Công cụ / mô hình:** Antigravity, Gemini 3.8 Flash
- **Mục tiêu:**
  1. Rà soát cấu trúc nội dung của 3 tài liệu mới được chuyển vào `doc/`:
     - `doc/Biểu mẫu 09_CTĐT ngành FINTECH v2.md` (CTĐT ngành Công nghệ tài chính 2024, mã 7340205, 9 ELO, 27 PI, 132 tín chỉ, kế hoạch 8 học kỳ).
     - `doc/11. APCM220307 - TIN HỌC ỨNG DỤNG_0001.md` (Đề cương chi tiết học phần Tin học ứng dụng: 3 CLO, ánh xạ PI1.2, PI2.3, PI3.1, PI5.2, kế hoạch kiểm tra 4 bài thực hành + thi cuối kỳ).
     - `doc/47. ABDA433708 - ỨNG DỤNG PHÂN TÍCH DỮ LIỆU LỚN TRONG KINH DOANH_0001.md` (Đề cương chi tiết học phần Ứng dụng phân tích Big Data trong kinh doanh: 4 CLO, ánh xạ PI1.3, PI2.3, PI3.3, PI6.2, rubric thuyết trình & project nhóm).
  2. Đối chiếu với các yêu cầu và rủi ro trong tài liệu đồ án:
     - Thỏa mãn minh chứng gốc **MC01** (thêm CTĐT thứ 2) và **MC02** (đề cương chi tiết học phần thử nghiệm) trong `doc/BS.md`.
     - Giải quyết câu hỏi còn nợ **Q02** (đề cương chính thức) và **Q14** (ánh xạ CLO–PI đã duyệt) trong `doc/GiaiThich_CoSoDuLieu.md` §8.
     - Hạ cấp mức độ rủi ro **R1** (từ **Cao** xuống **Trung bình**) và tháo gỡ điểm nghẽn cho Cột mốc **K1** trong `doc/ke-hoach-xay-dung.md`.
     - Cung cấp dữ liệu thực tế cho các User Story thuộc F02 (`programs`, `program_versions`, `plos`, `pis`), F03 (`courses`, `program_courses`, `syllabi`, `clos`, `clo_pi_links`) và F04 (`assessments`, rubric).
  3. Cập nhật tiến độ vào `doc/cam-ket-san-pham-metric.md`, `doc/ke-hoach-xay-dung.md`, `doc/rubric-checklist.md`.
- **File AI sinh/sửa:**
  - Tài liệu: `doc/cam-ket-san-pham-metric.md`, `doc/ke-hoach-xay-dung.md`, `doc/rubric-checklist.md`, `logsAI/ai-usage-log.md`.
- **Kiểm chứng đã làm:**
  - Đọc và đối chiếu toàn bộ các trường dữ liệu, chuẩn đầu ra ELO/PI/CLO, trọng số đánh giá giữa 3 file dữ liệu gốc và schema CSDL MongoDB v0.5.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** chưa commit.

---

## Phiên #21 — 2026-11-11: Chốt đặc tả nghiệp vụ mới (ADR 0013), cập nhật Schema MongoDB v0.6 (26 collections) và toàn bộ tài liệu + kế hoạch sửa đổi codebase

- **Sinh viên yêu cầu:** TinMH (IT)
- **Công cụ / mô hình:** Antigravity, Gemini 3.1 Pro
- **Mục tiêu:**
  1. Soạn bản đặc tả nghiệp vụ và toán học độc lập gửi GVHD xem xét (`doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`) theo đúng 5 quyết định của sinh viên:
     - **Phương án R1 (Import CSV CTĐT & Đề cương kèm sẵn Trọng số $\sum = 100\%$ và Ngưỡng đạt):** Một vai trò Quản lý học vụ (`PROGRAM_MANAGER` / `ADMIN`) import tệp CSV CTĐT và Đề cương (bao gồm sẵn ngưỡng đạt `PassThreshold` của từng `CLO`, `PI`, `PLO`, trọng số đóng góp `CLO -> PI` với tổng mỗi PI = $100\%$, và trọng số đóng góp `PI -> PLO` với tổng mỗi PLO = $100\%$). Khi vừa Import, dữ liệu lưu ở trạng thái `UNVERIFIED` để hiển thị lên giao diện kiểm tra (nếu sai thì sửa tệp CSV rồi Import lại), bấm Lưu mới chuyển sang `VERIFIED`.
     - **Bảo mật đề thi:** Không lưu ngân hàng câu hỏi và không lưu nội dung đề thi (xoá 3 collections `question_banks`, `question_categories`, `questions` $\to$ từ 29 xuống 26 collections).
     - **Giảng viên tự khóa ma trận đề thi:** Bỏ luồng Tổ trưởng Bộ môn duyệt đề (`SUBMITTED / APPROVED / REJECTED`); giảng viên tự khóa/mở khóa (`DRAFT -> LOCKED -> SCORE_ENTERED`).
     - **Nhập điểm CLO trực tiếp qua tệp CSV mẫu sinh từ ma trận đề đã khóa (Vắng thi = `0` điểm):** Mỗi câu hỏi ánh xạ vào 1 hoặc nhiều CLO với điểm tối đa `maxScore` trực tiếp trên từng CLO (`outcomeMappings: [{ cloId, maxScore }]`); hệ thống sinh tệp CSV mẫu từ ma trận đề đã khóa để giảng viên nhập điểm (sinh viên vắng thi hoặc bỏ trống tính `0` điểm, hiển thị trên giao diện kiểm tra — nếu sai thì sửa CSV import lại — và bấm Lưu thẳng vào `scores`).
     - **Chỉ đo lường chuẩn đầu ra & Đồng nhất công thức tổng hợp $\sum (\text{Trọng số} \times \text{Mức đạt})$ ở cả 4 cấp độ:** Không đo điểm bài kiểm tra hay điểm tổng kết học phần (`COURSE`). Tính mức đạt xuyên suốt 4 cấp độ: (1) CLO trên từng bài kiểm tra ($CLO_{s,p,j} = Score_{s,p,j} / MaxScore_{p,j}$), (2) CLO toàn học phần ($Value(CLO_{s,j}) = \sum_{p} W_{p,j} \times CLO_{s,p,j}$ với $\sum_{p} W_{p,j} = 100\%$), (3) PI từ các CLO ($Value(PI_{s,m}) = \sum_{j} W_{CLO_j \to PI_m} \times Value(CLO_{s,j})$ với $\sum_{j} W_{CLO_j \to PI_m} = 100\%$), và (4) PLO từ các PI ($Value(PLO_{s,q}) = \sum_{m} W_{PI_m \to PLO_q} \times Value(PI_{s,m})$ với $\sum_{m} W_{PI_m \to PLO_q} = 100\%$), đối chiếu với ngưỡng đạt đã Import ở từng cấp.
  2. Cập nhật toàn bộ tài liệu kiến trúc, SRS, SDD, Test Plan, Feature Plans và dọn sạch các collections/fields không cần thiết trong `doc/database_import.js` (nâng lên v0.6 — 26 collections).
  3. Lập kế hoạch sửa đổi codebase (`src/main` và `src/test`) chi tiết theo 4 Phase trong `doc/ke-hoach-sua-doi-theo-nghiep-vu-moi.md`.
- **File AI sinh/sửa:**
  - Đặc tả & ADR: `doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`, `doc/adr/0013-import-ctdt-de-cuong-va-nhap-diem-clo-truc-tiep.md`, `doc/adr/0001-duyet-ma-tran-de-thi.md`, `doc/adr/0010-phan-quyen-de-cuong-theo-bo-mon-va-giang-vien.md`, `doc/adr/0011-pi-suy-ra-tu-clo.md`.
  - Schema & Giải thích CSDL (v0.6): `doc/database_import.js`, `doc/GiaiThich_CoSoDuLieu.md`, `doc/PLO_DB_UPDATE.md`.
  - Quy tắc & Quản lý dự án: `CLAUDE.md`, `.claude/rules/02-domain-invariants.md`, `doc/chot-feature-do-an.md`, `doc/cam-ket-san-pham-metric.md`, `doc/ke-hoach-xay-dung.md`, `doc/tom-tat-du-an-de-thao-luan.md`, `doc/tech-debt.md`.
  - SRS / SDD / Test Plan: `doc/srs/README.md`, `doc/sdd/02-component.md`, `doc/sdd/03-erd-collection.md`, `doc/sdd/04-sequence.md`, `doc/test-plan.md`.
  - Kế hoạch Feature & Refactoring: `doc/plan/README.md`, `doc/plan/F02-ctdt-plo-pi.md`, `doc/plan/F03-hoc-phan-clo.md`, `doc/plan/F04-bai-danh-gia.md`, `doc/plan/F05b-nhap-diem.md`, `doc/plan/F06-do-luong.md`, `doc/plan/F07-bao-cao-truy-vet.md`, `doc/ke-hoach-sua-doi-theo-nghiep-vu-moi.md`.
- **Kiểm chứng đã làm:**
  - Rà soát tính nhất quán giữa `$jsonSchema` trong `doc/database_import.js`, công thức toán học 4 cấp độ (`ASSESSMENT_CLO`, `CLO`, `PI`, `PLO` kèm chuẩn hóa mẫu số $\text{Evaluated}$, lấy lần học gần nhất `LATEST`, tách $N_{\text{section}}$ và $N_{\text{cohort}}$, `PassThreshold` bắt buộc) trong `doc/dac-ta-thay-doi-nghiep-vu-va-phuong-an-tinh-clo.md`, và danh sách class/file thực tế `com.hcmute.lms.lmsapi` trong `doc/ke-hoach-sua-doi-theo-nghiep-vu-moi.md`.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** commit `df0a673` trên nhánh `docs/v0.7-adr0014-obe-4-levels` (sẵn sàng mở PR vào `main`).

---

## Phiên #31 — 2026-11-13: Hoàn thiện F05b (Phase 3) — API xuất CSV mẫu và tự động nhận diện cột điểm từ header

- **Sinh viên yêu cầu:** TinMH (IT) & IBS
- **Công cụ / mô hình:** Antigravity, Gemini 3.1 Pro
- **Mục tiêu:**
  1. Xây dựng API xuất tệp CSV mẫu nhập điểm `GET /api/v1/assessments/{id}/scores/template-csv`:
     - Kiểm tra bài đánh giá ở trạng thái `LOCKED` hoặc `SCORE_ENTERED`, phạm vi lớp `SECTION` và đã có câu hỏi/ma trận `OutcomeMapping`.
     - Sinh tiêu đề cột dạng `MaSV,HoTen,<item.code> - <clo.code> (Max: <mapping.maxScore>)` kèm UTF-8 BOM (`\uFEFF`) để Excel trên Windows hiển thị đúng font tiếng Việt.
     - Tự động điền danh sách sinh viên `ENROLLED` trong lớp (mã sinh viên `studentRef`, họ và tên `fullName`) và để trống các ô điểm.
  2. Tích hợp cơ chế tự động nhận diện cột điểm (`autoDetectMapping`):
     - Khi người dùng tải tệp CSV mẫu lên `POST /sections/{sectionId}/assessments/{assessmentId}/import-batches` mà không gửi `columnMapping`, hệ thống tự động đối sánh header:
       - Cột sinh viên: nhận diện `MaSV`, `MSSV`, `studentRef`,...
       - Bỏ qua các cột thông tin sinh viên phụ như `HoTen`, `STT`,...
       - Cột điểm: trích xuất `item.code` và `clo.code` đối chiếu với ma trận bài đánh giá.
     - Tự động fallback về cấu hình lô trước nếu tệp không phải định dạng mẫu.
  3. Bổ sung trường `fullName` trong `Student.java` (đã có sẵn trong MongoDB validator schema).
  4. Viết integration test toàn diện:
     - `AssessmentIntegrationTest`: test xuất template CSV (LOCKED, DRAFT 422, SYLLABUS_TEMPLATE 422, 403 khác lớp).
     - `ImportBatchUploadIntegrationTest`: test tự động nhận diện header tệp mẫu và nhập điểm thành công khi `columnMapping = null`.
- **File AI sinh/sửa:**
  - Backend: `src/main/java/com/hcmute/lms/lmsapi/controller/AssessmentController.java`, `src/main/java/com/hcmute/lms/lmsapi/service/AssessmentService.java`, `src/main/java/com/hcmute/lms/lmsapi/service/impl/AssessmentServiceImpl.java`, `src/main/java/com/hcmute/lms/lmsapi/service/impl/ImportBatchServiceImpl.java`, `src/main/java/com/hcmute/lms/lmsapi/document/Student.java`, `src/main/java/com/hcmute/lms/lmsapi/dto/AssessmentScoreTemplate.java`.
  - Tests: `src/test/java/com/hcmute/lms/lmsapi/controller/AssessmentIntegrationTest.java`, `src/test/java/com/hcmute/lms/lmsapi/controller/ImportBatchUploadIntegrationTest.java`, `src/test/java/com/hcmute/lms/lmsapi/service/impl/ImportBatchServiceImplTest.java`.
  - Tài liệu: `doc/plan/F05b-nhap-diem.md`, `logsAI/ai-usage-log.md`.
- **Kiểm chứng đã làm:**
  - `./mvnw test "-Dtest=AssessmentIntegrationTest"` pass 18/18 test (100%).
  - `./mvnw test "-Dtest=ImportBatchUploadIntegrationTest"` pass 18/18 test (100%).
  - `./mvnw test "-Dtest=*Import*,*Score*"` pass 79/79 test (100%).
  - `./mvnw spotless:apply` đạt BUILD SUCCESS trên toàn bộ 392 files.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** _sẵn sàng commit_

---

## Phiên #34 — 2026-11-15: Triển khai động cơ báo cáo phân tích đa chiều 5 chiều & xuất CSV ma trận (F07 / US-36, US-35)

- **Sinh viên yêu cầu:** TinMH (IT)
- **Công cụ / mô hình:** Antigravity (Gemini + Teamwork Multi-Agent)
- **Mục tiêu:**
  1. Xây dựng động cơ tổng hợp báo cáo phân tích đa chiều 2D (`GET /api/v1/reports/analytics`) và xuất CSV ma trận (`GET /api/v1/reports/analytics/export`) trên nền kiến trúc CSDL v0.7 hiện hữu (**0 thay đổi collection/schema MongoDB**, **0 dependency Maven mới**).
  2. Hỗ trợ trọn vẹn 5 chiều phân tích (`AnalyticsDimension`):
     - `TIME_TERM`: Xu hướng chuẩn đầu ra (`CLO`, `PI`, `PLO`) qua các học kỳ.
     - `COURSE_SECTIONS`: Đối sánh các lớp học phần song song của cùng môn học trên từng `CLO`.
     - `COHORT`: Đối sánh mức đạt `PLO` / `PI` giữa các khóa tuyển sinh (`K23`, `K24`, ...).
     - `ASSESSMENT_COMPONENT`: Phân rã mức đạt `CLO` theo loại bài đánh giá (`QUIZ`, `PRACTICAL`, `PROJECT`, `MIDTERM`, `FINAL`, `OTHER`) từ cấp `ASSESSMENT_CLO`.
     - `CURRICULUM_CLASSIFICATION`: Phân loại theo `PLO_CATEGORY` (`GENERAL`/`SPECIALIZED`/`UNSPECIFIED`), `COURSE_TYPE` (`COMPULSORY`/`ELECTIVE`), và `BLOOM_LEVEL` (`1..6`, chỉ gom nhóm, không biến đổi điểm số).
  3. Bảo đảm toàn bộ bất biến miền và bảo mật:
     - Chỉ đọc từ lượt tính `calculation_runs` có `status = SUCCESS`.
     - Đủ 10 chỉ số trên mỗi `MatrixMetricCell`, xử lý an toàn mẫu số bằng 0 (`evaluated == 0 -> passRate = null, mean = null`; `enrolled == 0 -> passRateOfEnrolled = null`).
     - Kiểm soát phạm vi fail-closed cho 4 vai trò (`ADMIN`, `QA`, `PROGRAM_MANAGER`, `LECTURER`), chặn IDOR qua `runId` và `sectionIds`.
     - Xuất CSV RFC 4180 kèm UTF-8 BOM (`\uFEFF`) và vô hiệu hóa CSV Formula Injection (CWE-1236).
  4. Khắc phục triệt để 5 cảnh báo SpotBugs và 7 khối trùng lặp PMD CPD phát hiện trước khi mở PR (ghi nhận tại `B29`), đồng thời cập nhật `doc/plan/F07-bao-cao-truy-vet.md`, `doc/sdd/02-component.md` và `doc/de-van-dap/f07-bao-cao-phan-tich-da-chieu.md`.
- **File AI sinh/sửa:**
  - Backend: `src/main/java/com/hcmute/lms/lmsapi/controller/AnalyticsController.java`, `src/main/java/com/hcmute/lms/lmsapi/service/AnalyticsService.java`, `src/main/java/com/hcmute/lms/lmsapi/service/impl/AnalyticsServiceImpl.java`, `src/main/java/com/hcmute/lms/lmsapi/exporter/MatrixCsvExporter.java`, `src/main/java/com/hcmute/lms/lmsapi/dto/analytics/{AnalyticsMatrixResponse,AnalyticsQueryRequest,MatrixHeader,MatrixMetricCell}.java`, `src/main/java/com/hcmute/lms/lmsapi/enums/{AnalyticsDimension,CurriculumClassificationType}.java`.
  - Tests: `src/test/java/com/hcmute/lms/lmsapi/service/AnalyticsServiceImplTest.java`, `src/test/java/com/hcmute/lms/lmsapi/controller/AnalyticsControllerIntegrationTest.java`.
  - Tài liệu: `doc/plan/F07-bao-cao-truy-vet.md`, `doc/sdd/02-component.md`, `doc/de-van-dap/f07-bao-cao-phan-tich-da-chieu.md`, `logsAI/ai-usage-log.md`.
- **Kiểm chứng đã làm:**
  - `./mvnw -B -ntp test-compile spotbugs:check pmd:cpd-check spotless:check` đạt BUILD SUCCESS (`BugInstance size is 0`, `0 CPD duplications`, `421 files clean`).
  - Chạy bộ 74/74 unit & integration tests (`AnalyticsServiceImplTest` 31/31, `AnalyticsControllerIntegrationTest` 18/18, `ScopeAuthorizationServiceImplTest` 25/25) trên MongoDB thật đạt pass 100%.
  - Đo độ phủ dòng lệnh JaCoCo: `AnalyticsController` 100.0% (15/15), `MatrixCsvExporter` 96.9% (126/130), `AnalyticsServiceImpl` 88.3% (650/736) — vượt xa ngưỡng cổng CI 70%.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** nhánh `feat/f07-multi-dimensional-analytics` (PR #91: `e733fff`, `598de96`).


---

## Phiên #35 — 2026-11-15: Bổ sung 2 chiều phân tích (`COURSE_LECTURER`, `STUDENT`) & Khắc phục lỗi `$jsonSchema` CI trên PR #91

- **Sinh viên yêu cầu:** TinMH (IT)
- **Công cụ / mô hình:** Antigravity (Gemini + Teamwork Multi-Agent)
- **Mục tiêu:**
  1. Bổ sung 2 chiều phân tích mới vào `AnalyticsDimension` nâng tổng số lên **7 chiều**:
     - `COURSE_LECTURER`: Đánh giá hiệu quả giảng dạy học phần theo từng giảng viên (`courseId` × `lecturerId`/`lecturerIds`; Dòng = `CLO`, Cột = Giảng viên phụ trách lớp học phần, tổng hợp gia quyền các lớp của giảng viên đó).
     - `STUDENT`: Phân tích kết quả chuẩn đầu ra chi tiết của từng sinh viên (`studentId`/`studentIds`; Dòng = `CLO` hoặc `PI`/`PLO`, Cột = Sinh viên theo `studentRef`, đọc từ `student_results` với phân biệt `COMPUTED` và `insufficientData`).
  2. Bảo đảm phân quyền RBAC fail-closed: `LECTURER` chỉ được xem kết quả của chính mình ở `COURSE_LECTURER` và sinh viên thuộc lớp mình dạy ở `STUDENT` (chặn `403` ngoài phạm vi); hỗ trợ trọn vẹn xuất CSV (`GET /api/v1/reports/analytics/export`).
  3. Khắc phục triệt để lỗi CI trên PR #91 (ghi nhận tại `B30`): chuẩn hóa test fixture trong `AnalyticsControllerIntegrationTest` khớp 100% với `$jsonSchema` validator v0.7 (`syllabusId`, `calculation_runs.scope`/`lockKey`, `aggregate_results.scope`, `clos`) mà không sửa đổi schema CSDL.
- **File AI sinh/sửa:**
  - Backend: `src/main/java/com/hcmute/lms/lmsapi/enums/AnalyticsDimension.java`, `src/main/java/com/hcmute/lms/lmsapi/dto/analytics/AnalyticsQueryRequest.java`, `src/main/java/com/hcmute/lms/lmsapi/repository/EnrollmentRepository.java`, `src/main/java/com/hcmute/lms/lmsapi/service/impl/AnalyticsServiceImpl.java`.
  - Tests: `src/test/java/com/hcmute/lms/lmsapi/service/AnalyticsServiceImplTest.java`, `src/test/java/com/hcmute/lms/lmsapi/controller/AnalyticsControllerIntegrationTest.java`.
  - Tài liệu: `doc/plan/F07-bao-cao-truy-vet.md`, `doc/sdd/02-component.md`, `doc/de-van-dap/f07-bao-cao-phan-tich-da-chieu.md`, `logsAI/ai-usage-log.md`.
- **Kiểm chứng đã làm:**
  - `./mvnw -B -ntp spotless:check test-compile spotbugs:check pmd:cpd-check` đạt BUILD SUCCESS (`0 SpotBugs`, `0 CPD duplications`, `421 files clean`).
  - `AnalyticsServiceImplTest` đạt **39/39 PASS**; `AnalyticsControllerIntegrationTest` đạt **22/22 PASS** trên MongoDB có nạp `$jsonSchema` validator v0.7.
  - Độ phủ dòng lệnh JaCoCo: `AnalyticsController` **100%**, `MatrixCsvExporter` **98.46%**, `AnalyticsServiceImpl` **87.44%**.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** cập nhật trên nhánh `feat/f07-multi-dimensional-analytics` (PR #91).

---

## Phiên #36 — 2026-11-17: Lọc kết quả theo chuẩn/Đạt–Chưa đạt/sinh viên và kiểm tra độ bám sát đề thi

- **Sinh viên yêu cầu:** minhat5 (theo `git config user.name`; chưa xác nhận là IBS hay IT)
- **Công cụ / mô hình:** Antigravity (Claude Sonnet)
- **Mục tiêu:** đáp ứng yêu cầu của giảng viên: (1) lọc đích danh PLO/PI/CLO và lọc Đạt/Chưa đạt; (2) lọc kết quả theo sinh viên; (3) hiển thị ngưỡng đạt cạnh kết quả; (4) kiểm tra bộ đề của lớp có bám sát CLO (kèm Bloom, tỷ trọng, liên kết PI/PLO).
- **File AI sinh/sửa:**
  - `dto/analytics/AnalyticsQueryRequest.java` (+`subjectIds`, `passed`), `dto/analytics/MatrixHeader.java` (+`passThreshold`), `service/impl/AnalyticsServiceImpl.java` (`applyResultFilters`, ngưỡng đạt ở header).
  - `CalculationController`/`CalculationService`/`CalculationServiceImpl`: `student-results` thêm lọc `studentId`, `subjectId`, `passed`.
  - Mới: `SectionAlignmentController` (`GET /api/v1/sections/{sectionId}/assessment-alignment`), `AssessmentAlignmentService(+Impl)`, `AssessmentAlignmentResponse`.
  - Test mới: `AnalyticsResultFilterTest`, `AssessmentAlignmentServiceImplTest`.
- **Lưu ý:** cảnh báo `BLOOM_BELOW_CLO` (mức Bloom cao nhất của câu hỏi < mức Bloom của CLO) là quy tắc đề xuất, chưa được GVHD xác nhận; Bloom chỉ để thống kê, không quy đổi ra điểm.
- **Kiểm chứng đã làm:** `AnalyticsResultFilterTest`, `AssessmentAlignmentServiceImplTest`, `AnalyticsServiceImplTest` chạy qua (sau `clean`). Chưa chạy toàn bộ test suite và chưa có integration test cho endpoint mới.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** chưa commit.

---

## Phiên #37 — 2026-11-17: Phân trang danh sách đo lường và báo cáo

- **Sinh viên yêu cầu:** minhat5 (theo `git config user.name`)
- **Công cụ / mô hình:** Antigravity (Claude Sonnet)
- **Mục tiêu:** các API danh sách trả mảng thô khiến giao diện bị dài; chuyển sang `PageResponse` chuẩn của hệ thống (`page` từ 0, `size` 1–100, mặc định 20).
- **File AI sinh/sửa:** `dto/PageResponse.java` (+`slice`), `CalculationController`/`CalculationService`/`CalculationServiceImpl` (`GET /calculation-runs`, `/{id}/student-results`, `/{id}/aggregate-results`), `ReportController`/`ReportService`/`ReportServiceImpl` (`GET /reports`), test `PageResponseSliceTest`, cập nhật `CalculationAndReportIntegrationTest`.
- **Thay đổi phá vỡ tương thích:** 4 endpoint trên đổi từ mảng `[...]` sang `{content, page, size, totalElements, totalPages, last}`. Repo `LMS-UI` phải đổi theo (chưa sửa).
- **Kiểm chứng đã làm:** `PageResponseSliceTest` 6/6, `CalculationAndReportIntegrationTest` 1/1, `AnalyticsResultFilterTest` 7/7, `AssessmentAlignmentServiceImplTest` 5/5 đạt; `spotless:apply` đã chạy. Chưa chạy toàn bộ test suite.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** chưa commit.

---

## Phiên #38 — 2026-11-17: Cập nhật doc/ban-cam-ket-buoc-3-metric.md

- **Sinh viên yêu cầu:** minhat5 (theo `git config user.name`)
- **Công cụ / mô hình:** Antigravity (Gemini)
- **Mục tiêu:** bổ sung các chức năng mới hoàn thiện (kiểm tra độ bám sát đề thi theo lớp học phần, lọc kết quả đo lường theo chuẩn/đạt-chưa đạt/sinh viên, kèm `passThreshold`, và phân trang chuẩn `PageResponse` cho danh sách đo lường/báo cáo) vào `doc/ban-cam-ket-buoc-3-metric.md`.
- **File AI sinh/sửa:** `doc/ban-cam-ket-buoc-3-metric.md`.
- **Kiểm chứng đã làm:** đối chiếu trực tiếp nội dung mô tả với các endpoint và DTO vừa triển khai ở Phiên #36 và #37 (`SectionAlignmentController`, `AnalyticsQueryRequest`, `MatrixHeader`, `CalculationController`, `ReportController`).
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** chưa commit.

---

## Phiên #39 — 2026-11-18: Bắt buộc đổi mật khẩu lần đầu cho tài khoản Admin tạo (API)

- **Sinh viên yêu cầu:** TinMH (theo `git config user.name`)
- **Công cụ / mô hình:** Claude Code, Claude Sonnet 5.5
- **Mục tiêu:** tài khoản Admin tạo (thủ công hoặc import CSV) đang dùng mật khẩu Admin biết; bắt người dùng tự đổi ở lần đăng nhập đầu. Thứ tự làm đã thống nhất với sinh viên: đổi mật khẩu lần đầu → trang profile → xlsx → OAuth2.
- **Nhánh:** `feat/force-password-change`.
- **File AI sinh/sửa:** `document/User` (+`mustChangePassword`), `dto/UserPrincipal`, `dto/UserProfileResponse`, `dto/ChangePasswordRequest` (mới), `service/AuthService` + `AuthServiceImpl` (`changePassword`, tách `issueLoginResponse`), `controller/AuthController` (`POST /auth/change-password`), `filter/MustChangePasswordFilter` (mới, chặn API khác bằng 403), `config/SecurityConfig` (đăng ký filter), `service/impl/UserServiceImpl` (tạo user/import CSV bật cờ), `doc/database_import.js` (thêm `mustChangePassword: BOOL` vào validator `users`). Test mới: `AuthServiceChangePasswordTest` (3), `MustChangePasswordFilterTest` (5).
- **Quyết định thiết kế cần sinh viên nắm:** (1) chặn ở server bằng filter, không chỉ cờ ở UI, vì gọi thẳng API bằng token vẫn lách được cờ UI; (2) đổi mật khẩu tăng `tokenVersion` rồi cấp token mới, nên token cũ mất hiệu lực nhưng người dùng không bị đăng xuất; (3) sai mật khẩu hiện tại trả 422, không trả 401, để interceptor UI không tự đăng xuất; (4) tài khoản cũ không có trường → `false`, không cần migration; `InitialAdminSeeder`/`DemoDataSeeder` không bật cờ.
- **Kiểm chứng đã làm:** `./mvnw compile` và `test-compile` qua; `spotless:apply` đã chạy; `AuthServiceChangePasswordTest` 3/3, `MustChangePasswordFilterTest` 5/5, `TimingAttackDefenseTest` 5/5, `AuthSessionServiceImplTest` 9/9 đạt. **Chưa chạy** các integration test (`AuthIntegrationTest`, `UserIntegrationTest`...) vì Docker không chạy và cổng 27018 không có MongoDB; **chưa có integration test** cho `/auth/change-password`. Chưa chạy toàn bộ test suite.
- **Lỗi/ảo giác AI tự phát hiện:** lần đầu thêm `ErrorCode.PASSWORD_CHANGE_REQUIRED`; `ErrorCodeAndResponseChallengerTest` khẳng định đúng 7 mã lỗi theo hợp đồng nên đã **hoàn tác**, filter dùng `ErrorCode.FORBIDDEN` kèm thông điệp riêng. Nguyên nhân: không kiểm tra test ràng buộc enum trước khi thêm mã. Commit sửa: cùng commit `feat(auth)` của nhánh `feat/force-password-change`.
- **Việc còn lại:** viết integration test (cần MongoDB replica set), nhắc nhở đóng gói tài liệu sơ đồ nếu có sequence đăng nhập.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** một commit `feat(auth): require password change on first login` trên nhánh `feat/force-password-change` (xem `git log`; chưa mở PR).

---

## Phiên #40 — 2026-11-18: Trang Tài khoản của tôi — API tự sửa họ tên

- **Sinh viên yêu cầu:** TinMH (theo `git config user.name`)
- **Công cụ / mô hình:** Claude Code, Claude Sonnet 5.5
- **Mục tiêu:** bước 2 trong thứ tự đã thống nhất (đổi mật khẩu lần đầu → trang profile → xlsx → OAuth2). Trang profile cần tự sửa hồ sơ.
- **Nhánh:** `feat/profile-page`, tách từ `feat/force-password-change` (cần commit của Phiên #39).
- **File AI sinh/sửa:** `dto/UpdateProfileRequest` (mới), `service/AuthService` + `AuthServiceImpl` (`updateProfile`), `controller/AuthController` (`PUT /auth/me`), `filter/MustChangePasswordFilter` (`/me` chỉ cho GET khi còn cờ). Test mới `AuthServiceUpdateProfileTest` (2); sửa `MustChangePasswordFilterTest` (nay 6 ca, vì hành vi `/me` đổi: trước đó POST `/me` được cho qua).
- **Quyết định thiết kế cần sinh viên nắm:** (1) chỉ cho sửa **họ tên**; email dùng để định danh/liên kết tài khoản (sẽ quan trọng khi làm OAuth2), vai trò/khoa/trạng thái do Admin quản lý; muốn cho sửa email phải cân nhắc lại cùng OAuth2. (2) Không đổi `tokenVersion` khi sửa họ tên. (3) Tài khoản còn cờ đổi mật khẩu không sửa được hồ sơ.
- **Kiểm chứng đã làm:** `spotless:apply` đã chạy; `AuthServiceUpdateProfileTest` 2/2, `AuthServiceChangePasswordTest` 3/3, `MustChangePasswordFilterTest` 6/6, `TimingAttackDefenseTest` 5/5, `AuthSessionServiceImplTest` 9/9 đạt. Vẫn **chưa chạy integration test** (không có MongoDB test) và **chưa có integration test** cho `PUT /auth/me`.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** một commit `feat(profile): let users edit their full name` trên nhánh `feat/profile-page` (xem `git log`; chưa mở PR).
## Phiên #41 — 2026-11-18: Nhập tệp Excel `.xlsx` cho mọi chức năng nhập (ADR 0015, TD-32)

- **Sinh viên yêu cầu:** TinMH (theo `git config user.name`)
- **Công cụ / mô hình:** Claude Code, Claude Sonnet 5.5
- **Mục tiêu:** bước 3 trong thứ tự đã thống nhất. Sinh viên chọn qua câu hỏi: nhận `.xlsx` ở **tất cả** chỗ nhập; dùng **Apache POI poi-ooxml 6.0.0**; màn nhập điểm lấy tên cột qua **endpoint của API**; tệp mẫu có **thêm bản `.xlsx`**.
- **Nhánh:** `feat/xlsx-import` (tách từ `main`).
- **Dependency mới:** `org.apache.poi:poi-ooxml:6.0.0` (Apache-2.0), kéo theo 12 jar (poi, poi-ooxml-lite, xmlbeans, commons-compress/-io/-lang3/-codec/-math3/-collections4, log4j-api, curvesapi, SparseBitSet). Phương án khác (FastExcel Reader, viết bộ đọc riêng, đọc ở trình duyệt) và lý do loại ở `doc/adr/0015-nhap-tep-excel-xlsx.md`. Đã chạy `dependency:tree`, phiên bản log4j-api khớp bản Spring Boot quản lý (2.25.5).
- **Thiết kế cần sinh viên nắm:** `.xlsx` được đổi sang CSV UTF-8 rồi đi qua đúng các bộ đọc CSV sẵn có (`XlsxCsvConverter`, `ImportFiles`), nên 9 chức năng nhập không phải viết lại bộ đọc và hai định dạng luôn cùng quy tắc. Dòng thứ n của Excel là dòng thứ n của CSV (dòng trống giữ chỗ) để số dòng trong báo lỗi khớp. Ô công thức lấy giá trị đã tính, không thực thi công thức. Tệp gốc `.xlsx` của nhập điểm được lưu nguyên trạng (`<batchId>.xlsx`), checksum tính trên tệp gốc.
- **File AI sinh/sửa:** mới `util/XlsxCsvConverter`, `util/ImportFiles`, `util/ImportTemplateResponses`, `dto/ImportFilePreviewResponse`, `doc/adr/0015-nhap-tep-excel-xlsx.md`; sửa `pom.xml`, `CsvTextParser`, `CourseServiceImpl`, `EnrollmentController`, `ImportBatchController` (+`POST .../import-batches/preview`), `ImportBatchService(Impl)`, `ImportFileStorage` + `LocalImportFileStorage` (`save` nhận thêm đuôi), 9 controller có tải mẫu (thêm `?format=xlsx`), `ImportSourceSystem` (javadoc), `doc/tech-debt.md` (TD-32), `doc/adr/0013` (ghi chú). Test mới: `XlsxCsvConverterTest` (15), `ImportTemplateResponsesTest` (3), `CsvTextParserXlsxTest` (4), thêm ca vào `ImportBatchServiceImplTest` (+5), `LocalImportFileStorageTest` (+1), `CourseServiceImplTest` (+2, đổi 1 ca cũ dùng `.xlsx` giả sang `.pdf`).
- **Kiểm chứng đã làm:** `spotless:check` đạt; chạy 77 lớp test không dùng `@SpringBootTest`: 703 test, 0 lỗi logic, 206 lỗi đều là lớp cần khởi tạo Spring context/MongoDB (không có MongoDB test ở máy này, Docker không chạy). **Chưa chạy** các integration test (kể cả `ImportBatchUploadIntegrationTest`, `CourseIntegrationTest`), nên **chưa có bằng chứng chạy thật** cho luồng upload `.xlsx` qua HTTP. Chưa thử tệp `.xlsx` thật do Excel/WPS tạo, chỉ tệp do POI tạo trong test.
- **Lỗi/ảo giác AI tự phát hiện:** (1) lần đầu thông báo lỗi "Nguồn EXCEL cần tệp có đuôi .xlsx" không chứa chữ "Excel" mà `ImportBatchUploadIntegrationTest` (đang kỳ vọng `containsString("Excel")`) kiểm tra — phát hiện khi đọc lại các test cũ, chưa phải khi chạy; đã đổi thông báo, không sửa test. (2) Nhiều lần viết sai ký tự thoát (`\n`, `\|`) khi sinh mã bằng script, bị trình biên dịch/`spotless` báo ngay; đã sửa tay.
- **Rủi ro còn lại:** POI nạp cả tệp vào bộ nhớ (giới hạn 20.000 dòng, 200 cột, giới hạn upload Spring); ô phần trăm/ngày lấy đúng như hiển thị; chỉ đọc trang tính đầu tiên.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** một commit `feat(import): accept .xlsx files in every import` trên nhánh `feat/xlsx-import`, PR vào `main` (xem `git log`).


---

## Phiên #42 — 2026-11-18: Đăng nhập bằng Google (ADR 0016)

- **Sinh viên yêu cầu:** TinMH (theo `git config user.name`)
- **Công cụ / mô hình:** Claude Code, Claude Sonnet 5.5
- **Mục tiêu:** bước 4 trong thứ tự đã thống nhất (đổi mật khẩu lần đầu → profile → xlsx → OAuth2). Sinh viên chọn qua câu hỏi: **chỉ khớp email có sẵn** (không tự tạo tài khoản); **giữ cờ `mustChangePassword`**; luồng **UI lấy ID token, API xác minh**.
- **Nhánh:** `feat/google-login` (từ `main`).
- **Không thêm dependency:** dùng `NimbusJwtDecoder` đã có trong `spring-boot-starter-oauth2-resource-server` để kiểm tra ID token bằng JWKS của Google. Không cần client secret.
- **Thiết kế cần sinh viên nắm:** `POST /api/v1/auth/google {idToken}` (công khai) → `GoogleIdTokenVerifierImpl` kiểm tra chữ ký RS256, hạn, `iss` (`https://accounts.google.com` hoặc `accounts.google.com`), `aud` = `GOOGLE_CLIENT_ID`, có `email` và `email_verified` → `AuthServiceImpl.loginWithGoogle` chuẩn hóa email, tìm user, từ chối nếu không có/bị khóa, ghi audit `LOGIN`, cấp JWT hệ thống bằng chính `issueLoginResponse` của đăng nhập mật khẩu. `GOOGLE_CLIENT_ID` rỗng = tắt (422 "chưa được cấu hình"). Verifier là interface nên service kiểm thử không gọi Google.
- **File AI sinh/sửa:** mới `service/GoogleIdTokenVerifier`, `service/impl/GoogleIdTokenVerifierImpl`, `dto/GoogleLoginRequest`, `doc/adr/0016-dang-nhap-google.md`; sửa `AuthService`, `AuthServiceImpl`, `AuthController`, `SecurityConfig` (permitAll cho `/auth/google` ở cả filter chain và bộ giải quyết Bearer), `application.properties(.example)`, `deploy/staging/{docker-compose.yml,.env.example}`, `doc/deploy/staging.md`. Test mới: `GoogleIdTokenVerifierImplTest` (11, token ký bằng khóa RSA sinh trong test), `AuthServiceGoogleLoginTest` (6).
- **Kiểm chứng đã làm:** `spotless:check` đạt; chạy 77 lớp test không dùng `@SpringBootTest`: 731 test, 0 lỗi logic, 206 lỗi đều là lớp cần Spring context/MongoDB (giống trước). **Chưa chạy** integration test nên chưa kiểm chứng bean `GoogleIdTokenVerifierImpl` khởi tạo được trong context thật và `permitAll` của `/auth/google` qua HTTP. **Chưa thử với ID token thật của Google** (chưa có OAuth client ID; không có mạng/credential để thử).
- **Rủi ro còn lại:** (1) Người chỉ đăng nhập Google vẫn phải biết mật khẩu ban đầu để qua bước đổi mật khẩu lần đầu (quyết định 4 của ADR). (2) Email trong hệ thống phải trùng email Google. (3) API cần ra Internet để lấy khóa công khai của Google.
- **Lỗi/ảo giác AI tự phát hiện:** lúc đầu dùng `jwt.getIssuer()` (kiểu URL) để so `iss`; Google có khi gửi `accounts.google.com` (không phải URL hợp lệ) nên đổi sang `getClaimAsString("iss")` và thêm test cho dạng này. Phát hiện khi đọc lại đặc tả `iss`, trước khi chạy test.
- **Lỗi CI sau khi mở PR #99:** SpotBugs (job Lint và Test) báo "Possible null pointer dereference" ở bộ kiểm tra `aud`; đọc lại thấy cả bộ kiểm tra `iss` cũng có lỗi thật: `List.of(...).contains(null)` ném NullPointerException nên token thiếu `iss`/`aud` sẽ thành lỗi 500 thay vì 401. Đã sửa (kiểm tra null trước) và thêm test `missingIssuerOrAudience`. Nguyên nhân: không chạy `spotbugs:check` cục bộ trước khi mở PR (chỉ chạy `spotless:check` và test). Từ nay chạy đúng lệnh lint của CI (`test-compile spotless:check spotbugs:check pmd:cpd-check`).
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** một commit `feat(auth): add Google sign-in with ID token` trên nhánh `feat/google-login`, PR vào `main` (xem `git log`).

---

## Phiên #43 — 2026-11-24: Bỏ cột trạng thái khi import Khoa và Người dùng, mặc định luôn hoạt động

- **Sinh viên yêu cầu:** minhat5 (theo `git config user.name`)
- **Công cụ / mô hình:** Antigravity, Gemini 4 Neon (High)
- **Mục tiêu:** loại bỏ cột trạng thái khỏi template và luồng import Khoa (`isActive`) và Người dùng (`status`), luôn khởi tạo bản ghi import ở trạng thái hoạt động (`isActive = true` cho Khoa, `UserStatus.ACTIVE` cho Người dùng); bỏ qua cột trạng thái nếu tệp cũ vẫn còn truyền.
- **File AI sinh/sửa:**
  - `src/main/java/com/hcmute/lms/lmsapi/service/impl/FacultyServiceImpl.java` (`generateTemplateCsv`, `importFaculties`)
  - `src/main/java/com/hcmute/lms/lmsapi/service/impl/UserServiceImpl.java` (`generateTemplateCsv`, `importUsers`)
  - `src/test/java/com/hcmute/lms/lmsapi/controller/AdminAndFacultyCsvImportIntegrationTest.java`
  - `src/test/java/com/hcmute/lms/lmsapi/service/impl/UserServiceImplTest.java`
  - `src/test/java/com/hcmute/lms/lmsapi/service/impl/FacultyServiceImplTest.java` (mới)
  - `doc/data/faculties_hcmute.csv`, `doc/data/lecturers_fe.csv`, `doc/data/lecturers_fit.csv`, `doc/data/lecturers_hcmute.csv`
  - `doc/ban-cam-ket-buoc-3-metric.md`
  - `logsAI/ai-usage-log.md`
- **Kiểm chứng đã làm:**
  - `.\mvnw.cmd spotless:apply test-compile spotless:check spotbugs:check pmd:cpd-check test "-Dtest=FacultyServiceImplTest,UserServiceImplTest"`: `BUILD SUCCESS`, 0 lỗi Spotless / SpotBugs / PMD-CPD, 22/22 unit test đạt (`FacultyServiceImplTest` 4/4, `UserServiceImplTest` 18/18).
  - `.\mvnw.cmd test "-Dtest=AdminAndFacultyCsvImportIntegrationTest"`: `BUILD SUCCESS`, 3/3 integration test đạt trên MongoDB replica set (`localhost:27018`).
- **Lỗi/ảo giác AI tự phát hiện:** (1) Ban đầu viết `template.filename()` trong test mới thay vì `template.fileName()` của record `AssessmentScoreTemplate`, bị `testCompile` báo lỗi và đã sửa ngay. (2) PMD-CPD báo trùng lặp 11–12 dòng giữa các test case import mới; đã tách helper `importValidFaculties` và `importValidUsers` để đạt 0 lỗi CPD.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** nhánh `feat/import-default-active-and-xlsx-data` (PR vào `main`, xem `git log`; lưu ý kiểm tra và đồng bộ hiển thị danh sách cột import trên repo `LMS-UI` nếu có).

---

## Phiên #44 — 2026-11-24: Bổ sung 12 tệp dữ liệu mẫu Excel (.xlsx) trong `doc/data/`

- **Sinh viên yêu cầu:** minhat5 (theo `git config user.name`)
- **Công cụ / mô hình:** Antigravity, Gemini 4 Neon (High)
- **Mục tiêu:** tạo 12 tệp `.xlsx` trong `doc/data/` có nội dung giống hệt 12 tệp `.csv` hiện có để phục vụ kiểm thử chức năng import bằng Excel (`.xlsx`), đồng thời thêm test tự động đối chiếu 12 cặp `.csv`/`.xlsx`.
- **File AI sinh/sửa:**
  - Tạo mới 12 tệp trong `doc/data/`: `courses_cntt.xlsx`, `courses_fintech.xlsx`, `enrollments_fintech_2026_hk1.xlsx`, `faculties_hcmute.xlsx`, `lecturers_fe.xlsx`, `lecturers_fit.xlsx`, `lecturers_hcmute.xlsx`, `program_courses_fintech.xlsx`, `program_version_fintech.xlsx`, `sections_fintech_2026_hk1.xlsx`, `syllabus_abda433708.xlsx`, `syllabus_apcm220307.xlsx` (sinh trực tiếp qua `XlsxCsvConverter.fromCsv` từ 12 tệp `.csv` tương ứng)
  - Sửa `src/test/java/com/hcmute/lms/lmsapi/util/XlsxCsvConverterTest.java` (thêm test `docDataXlsxMatchesCsvFiles`)
  - Cập nhật `logsAI/ai-usage-log.md`
- **Kiểm chứng đã làm:**
  - `.\mvnw.cmd spotless:apply test-compile spotless:check spotbugs:check pmd:cpd-check test "-Dtest=XlsxCsvConverterTest,CsvUtilsTest"`: `BUILD SUCCESS`, 0 lỗi Spotless / SpotBugs / PMD-CPD, 23/23 test đạt (`CsvUtilsTest` 7/7, `XlsxCsvConverterTest` 16/16, gồm `docDataXlsxMatchesCsvFiles` đối chiếu toàn bộ 12 cặp `.csv`–`.xlsx`).
- **Lỗi/ảo giác AI tự phát hiện:** (1) Dùng nhầm `existsDirectory()` / `existsRegularFile()` thay vì `isDirectory()` / `isRegularFile()` của AssertJ `AbstractPathAssert`, được `testCompile` phát hiện và sửa ngay. (2) SpotBugs báo 2 cảnh báo `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` cho `Path.getFileName().toString()`; đã đổi sang `String.valueOf(path.getFileName())` để đạt 0 bug SpotBugs.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** nhánh `feat/import-default-active-and-xlsx-data` (PR vào `main`, xem `git log`).

---

## Phiên #45 — 2026-11-24: Bổ sung endpoint `GET /api/v1/sections/lecturers` tra cứu giảng viên mở lớp học phần

- **Sinh viên yêu cầu:** minhat5 (theo `git config user.name`)
- **Công cụ / mô hình:** Antigravity, Gemini 4 Neon (High)
- **Mục tiêu:** thay thế endpoint cũ `GET /api/v1/departments/lecturers` (đã xóa khi bỏ `DEPARTMENT_HEAD` theo ADR 0014) bằng `GET /api/v1/sections/lecturers` (tùy chọn `?facultyId=...`) để `ADMIN` và `PROGRAM_MANAGER` lấy danh sách giảng viên đang hoạt động (`Role.LECTURER`, `UserStatus.ACTIVE`) khi tạo/sửa lớp học phần mà vẫn giữ `/api/v1/users/**` độc quyền cho `ADMIN`.
- **File AI sinh/sửa:**
  - `src/main/java/com/hcmute/lms/lmsapi/service/SectionService.java` (`getActiveLecturers`)
  - `src/main/java/com/hcmute/lms/lmsapi/service/impl/SectionServiceImpl.java` (`getActiveLecturers`, lọc theo `facultyId`, sắp xếp theo `fullName`/`username`)
  - `src/main/java/com/hcmute/lms/lmsapi/controller/SectionController.java` (`GET /api/v1/sections/lecturers`)
  - `src/test/java/com/hcmute/lms/lmsapi/service/impl/SectionServiceImplTest.java`
  - `src/test/java/com/hcmute/lms/lmsapi/controller/SectionIntegrationTest.java`
  - `logsAI/ai-usage-log.md`
- **Kiểm chứng đã làm:**
  - `.\mvnw.cmd spotless:apply test-compile spotless:check spotbugs:check pmd:cpd-check test "-Dtest=SectionServiceImplTest,SectionIntegrationTest"`: `BUILD SUCCESS`, 0 lỗi Spotless / SpotBugs / PMD-CPD, 28/28 test đạt (`SectionIntegrationTest` 15/15 trên MongoDB replica set `localhost:27018`, `SectionServiceImplTest` 13/13).
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** nhánh `feat/section-active-lecturers-endpoint` (PR vào `main`, xem `git log`; lưu ý đổi URL trong `LMS-UI` `list-sections.component.ts` từ `/api/v1/departments/lecturers` sang `/api/v1/sections/lecturers`).

---

## Phiên #46 — 2026-11-25: Bổ sung danh sách tóm tắt giảng viên (`lecturers`) trong `SectionResponse`

- **Sinh viên yêu cầu:** minhat5 (theo `git config user.name`)
- **Công cụ / mô hình:** Antigravity, Gemini 4 Neon (High)
- **Mục tiêu:** trả kèm danh sách tóm tắt `lecturers: [{ id, username, fullName }]` trong `SectionResponse` bằng 1 truy vấn batch `userRepository.findAllById(...)`, giúp mọi vai trò xem danh sách lớp học phần hiển thị được tên giảng viên (kể cả giảng viên đã bị `LOCKED` ở học kỳ cũ) mà không cần gọi thêm `/api/v1/users` (giữ `GET /api/v1/users` độc quyền `ADMIN`) hay mở `/api/v1/sections/lecturers` cho toàn trường.
- **File AI sinh/sửa:**
  - `src/main/java/com/hcmute/lms/lmsapi/dto/SectionResponse.java` (thêm `lecturers` và `LecturerSummary`)
  - `src/main/java/com/hcmute/lms/lmsapi/mapper/SectionMapper.java`, `mapper/impl/SectionMapperImpl.java` (nạp `LecturerSummary` theo đúng thứ tự `lecturerIds`)
  - `src/main/java/com/hcmute/lms/lmsapi/service/impl/SectionServiceImpl.java` (`loadLecturersById` batch + tái sử dụng map ở `createSection` / `updateLecturers`)
  - `src/test/java/com/hcmute/lms/lmsapi/service/impl/SectionServiceImplTest.java`
  - `src/test/java/com/hcmute/lms/lmsapi/controller/SectionIntegrationTest.java`
  - `logsAI/ai-usage-log.md`
- **Kiểm chứng đã làm:**
  - `.\mvnw.cmd spotless:apply test-compile spotless:check spotbugs:check pmd:cpd-check test "-Dtest=SectionServiceImplTest,SectionIntegrationTest"`: `BUILD SUCCESS`, 0 lỗi Spotless / SpotBugs / PMD-CPD, 30/30 test đạt (`SectionIntegrationTest` 16/16 trên MongoDB replica set `localhost:27018`, `SectionServiceImplTest` 14/14).
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** chưa commit (lưu ý cập nhật `LMS-UI` đọc `section.lecturers` để hiện tên giảng viên và chỉ gọi `/api/v1/sections/lecturers` khi user có vai trò `ADMIN` hoặc `PROGRAM_MANAGER`).

---

## Phiên #47 — 2026-11-25: Bổ sung import sinh viên cho ADMIN và chặn tự tạo sinh viên khi ghi danh

- **Sinh viên yêu cầu:** minhat5 (theo `git config user.name`)
- **Cơ sở / công cụ / mô hình:** Antigravity, Gemini 4 Neon (High)
- **Mục tiêu:** bổ sung chức năng import danh sách sinh viên hàng loạt (`CSV` và `.xlsx`) cho vai trò `ADMIN` (`GET /api/v1/students/import-template`, `POST /api/v1/students/import`), đồng thời cập nhật nghiệp vụ import ghi danh (`POST /api/v1/sections/{sectionId}/enrollments/import` và `POST /api/v1/enrollments/import`) để báo lỗi và từ chối ghi danh khi `studentCode` chưa tồn tại trong hệ thống thay vì tự động tạo mới `Student`.
- **File AI sinh/sửa:**
  - `src/main/java/com/hcmute/lms/lmsapi/repository/StudentRepository.java` (thêm `existsByStudentCode`, `findByStudentCode`)
  - `src/main/java/com/hcmute/lms/lmsapi/service/StudentService.java`, `src/main/java/com/hcmute/lms/lmsapi/service/impl/StudentServiceImpl.java` (mẫu `studentCode,fullName,cohort`, mặc định `StudentStatus.ACTIVE`, kiểm tra từng dòng best-effort và ghi `audit_logs`)
  - `src/main/java/com/hcmute/lms/lmsapi/controller/StudentController.java` (`@PreAuthorize("hasRole('ADMIN')")`)
  - `src/main/java/com/hcmute/lms/lmsapi/service/EnrollmentService.java`, `src/main/java/com/hcmute/lms/lmsapi/service/impl/EnrollmentServiceImpl.java` (báo lỗi theo dòng nếu `studentCode` chưa có trong `students`, không tự tạo `Student`)
  - `src/test/java/com/hcmute/lms/lmsapi/service/impl/StudentServiceImplTest.java`, `src/test/java/com/hcmute/lms/lmsapi/controller/StudentImportIntegrationTest.java`
  - `src/test/java/com/hcmute/lms/lmsapi/service/impl/EnrollmentServiceImplTest.java`, `src/test/java/com/hcmute/lms/lmsapi/controller/EnrollmentImportIntegrationTest.java`
  - `doc/ban-cam-ket-buoc-3-metric.md`, `doc/plan/F05a-hoc-ky-lop-sinh-vien.md`, `logsAI/ai-usage-log.md`
- **Kiểm chứng đã làm:**
  - `.\mvnw.cmd spotless:apply test-compile spotless:check spotbugs:check pmd:cpd-check test "-Dtest=StudentServiceImplTest,StudentImportIntegrationTest,EnrollmentServiceImplTest,EnrollmentImportIntegrationTest,EnrollmentCsvParserTest,XlsxCsvConverterTest"`: `BUILD SUCCESS`, 0 lỗi Spotless / SpotBugs / PMD-CPD, 61/61 test đạt (`EnrollmentImportIntegrationTest` 13/13 và `StudentImportIntegrationTest` 4/4 chạy trên MongoDB replica set `localhost:27018`; `EnrollmentServiceImplTest` 12/12; `StudentServiceImplTest` 5/5; `EnrollmentCsvParserTest` 11/11; `XlsxCsvConverterTest` 16/16).
- **Lỗi/ảo giác AI tự phát hiện:** trong lượt `testCompile` đầu tiên dùng nhầm getter `getRowNumber()`/`getStudentCode()` thay vì `getRow()`/`getKey()` của `CsvImportResultResponse.RowError`; đã sửa lại và chạy vượt qua toàn bộ kiểm tra.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** chưa commit (lưu ý cập nhật `LMS-UI` để bổ sung màn hình/nút import sinh viên cho `ADMIN` gọi `/api/v1/students/import` và cập nhật thông báo hướng dẫn khi import ghi danh báo lỗi mã sinh viên chưa tồn tại).

---

## Phiên #48 — 2026-11-25: Bổ sung cập nhật trạng thái sinh viên hàng loạt (JSON + CSV/XLSX Import) và chặn ghi danh sinh viên khác `ACTIVE`

- **Sinh viên yêu cầu:** minhat5 (theo `git config user.name`)
- **Cơ sở / công cụ / mô hình:** Antigravity, Gemini 4 Neon (High)
- **Mục tiêu:** bổ sung API cho `ADMIN` tra cứu danh sách sinh viên có lọc/phân trang, đổi trạng thái đơn lẻ hoặc nhiều sinh viên cùng lúc (`JSON` API + import `.csv`/`.xlsx` với cột `studentCode,status`), đồng thời chặn ghi danh (import 1 lớp, import nhiều lớp và ghi danh lại từ `WITHDRAWN`) khi sinh viên không ở trạng thái `ACTIVE`.
- **File AI sinh/sửa:**
  - DTO mới: `StudentResponse.java`, `StudentStatusUpdateRequest.java`, `StudentBatchStatusUpdateRequest.java`, `StudentBatchStatusUpdateResponse.java`
  - Repository: `StudentRepositoryCustom.java`, `StudentRepositoryCustomImpl.java`, `StudentRepository.java`
  - Service & Controller: `StudentService.java`, `StudentServiceImpl.java`, `StudentController.java` (`GET /api/v1/students`, `PUT /api/v1/students/{id}/status`, `PUT /api/v1/students/status`, `GET /api/v1/students/status-import-template`, `POST /api/v1/students/status-import`)
  - Chặn ghi danh: `EnrollmentServiceImpl.java` (`validateRows`, `importEnrollmentsBulk`, `updateStatus`)
  - Tests & Docs: `StudentServiceImplTest.java`, `StudentImportIntegrationTest.java`, `EnrollmentServiceImplTest.java`, `EnrollmentImportIntegrationTest.java`, `doc/ban-cam-ket-buoc-3-metric.md`, `doc/plan/F05a-hoc-ky-lop-sinh-vien.md`, `logsAI/ai-usage-log.md`
- **Kiểm chứng đã làm:**
  - `.\mvnw.cmd spotless:apply test-compile spotless:check spotbugs:check pmd:cpd-check test "-Dtest=StudentServiceImplTest,StudentImportIntegrationTest,EnrollmentServiceImplTest,EnrollmentImportIntegrationTest,EnrollmentCsvParserTest,XlsxCsvConverterTest"`: `BUILD SUCCESS`, 0 lỗi Spotless / SpotBugs / PMD-CPD, 70/70 test đạt (`EnrollmentImportIntegrationTest` 14/14 và `StudentImportIntegrationTest` 7/7 trên MongoDB replica set `localhost:27018`; `EnrollmentServiceImplTest` 13/13; `StudentServiceImplTest` 9/9; `EnrollmentCsvParserTest` 11/11; `XlsxCsvConverterTest` 16/16).
- **Lỗi/ảo giác AI tự phát hiện:** trong test tích hợp `EnrollmentImportIntegrationTest.nonActiveStudentsAreRejectedFromEnrollment`, ban đầu assert `$.code == "STUDENT_NOT_ACTIVE"` trong khi `GlobalExceptionHandler` chuẩn hóa mã JSON cho `BusinessRuleViolationException` là `BUSINESS_RULE_VIOLATION` kèm chi tiết trong `message`; đã sửa lại assertion kiểm tra `BUSINESS_RULE_VIOLATION` + `message` chứa `DROPPED_OUT` và chạy lại đạt 70/70.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** chưa commit (lưu ý cập nhật `LMS-UI` màn hình quản lý sinh viên cho `ADMIN`: bảng lọc/chọn nhiều sinh viên gọi `GET /api/v1/students`, `PUT /api/v1/students/status`, và tải/import file trạng thái qua `/api/v1/students/status-import`).

---

## Phiên #49 — 2026-11-25: Bắt buộc `fullName` và `cohort` cho sinh viên, bổ sung dữ liệu mẫu CSV/Excel và cập nhật Docker `lms-mongo`

- **Sinh viên yêu cầu:** minhat5 (theo `git config user.name`)
- **Cơ sở / công cụ / mô hình:** Antigravity, Gemini 4 Neon (High)
- **Mục tiêu:** siết bắt buộc hai trường `fullName` và `cohort` khi import và lưu document `students` (cả Java service lẫn `$jsonSchema` MongoDB validator), tạo tệp dữ liệu mẫu `doc/data/students_fintech.csv` + `.xlsx` cho 25 sinh viên Fintech, và cập nhật trực tiếp 25 bản ghi sinh viên + validator trong container Docker `lms-mongo`.
- **File AI sinh/sửa:**
  - `src/main/java/com/hcmute/lms/lmsapi/document/Student.java` (cập nhật Javadoc bắt buộc)
  - `src/main/java/com/hcmute/lms/lmsapi/service/impl/StudentServiceImpl.java` (`CsvTextParser.requireColumns(..., "studentCode", "fullName", "cohort")` và kiểm tra không được để trống từng dòng)
  - `doc/database_import.js` (`schema(['studentCode', 'fullName', 'cohort'], ...)` với `fullName: STR, cohort: STR`)
  - `doc/data/students_fintech.csv`, `doc/data/students_fintech.xlsx` (25 sinh viên K24 và K22 khớp `enrollments_fintech_2026_hk1.csv`)
  - `src/test/java/com/hcmute/lms/lmsapi/util/XlsxCsvConverterTest.java` (13 cặp CSV/XLSX), `ApiTestFixtures.java`, `CalculationAndReportIntegrationTest.java`, `ImportBatchPerformanceIntegrationTest.java`, `StudentServiceImplTest.java`, `StudentImportIntegrationTest.java`, `EnrollmentImportIntegrationTest.java`
  - `doc/ban-cam-ket-buoc-3-metric.md`, `doc/plan/F05a-hoc-ky-lop-sinh-vien.md`, `logsAI/ai-usage-log.md`
- **Kiểm chứng đã làm:**
  - Cập nhật `lms-mongo` cổng `27018`: `UPDATED=25`, `TOTAL=25`, `MISSING=0`, chạy lại `/opt/loms/database_import.js` cập nhật validator `required: ["studentCode","fullName","cohort"]`.
  - `.\mvnw.cmd spotless:apply test-compile spotless:check spotbugs:check pmd:cpd-check test "-Dtest=StudentServiceImplTest,StudentImportIntegrationTest,EnrollmentServiceImplTest,EnrollmentImportIntegrationTest,CalculationAndReportIntegrationTest,XlsxCsvConverterTest"`: `BUILD SUCCESS`, 0 lỗi Spotless / SpotBugs / PMD-CPD, 60/60 test đạt (`CalculationAndReportIntegrationTest` 1/1, `EnrollmentImportIntegrationTest` 14/14, `StudentImportIntegrationTest` 7/7 trên `lms-mongo` cổng `27018` bật validator mới; `EnrollmentServiceImplTest` 13/13; `StudentServiceImplTest` 9/9; `XlsxCsvConverterTest` 16/16 đối chiếu 13 cặp `.csv`/`.xlsx` trong `doc/data/`).
- **Lỗi/ảo giác AI tự phát hiện:** trong câu lệnh PowerShell kiểm tra `$or` trong `mongosh`, quên thoát chuỗi dấu nháy kép khiến PowerShell diễn giải nhầm `$or` là biến; đã chuyển sang here-string nháy đơn `@'...'@` và chạy thành công.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** chưa commit (lưu ý cập nhật ghi chú phía `LMS-UI` để thể hiện cả 3 cột `studentCode`, `fullName`, `cohort` đều bắt buộc khi import sinh viên).

---

## Phiên #50 — 2026-11-26: Bổ sung phân trang chuẩn (`PageResponse`) kèm tìm kiếm & xuất CSV/XLSX bảng điểm cho 6 endpoint danh sách/báo cáo

- **Sinh viên yêu cầu:** minhat5 (theo `git config user.name`)
- **Cơ sở / công cụ / mô hình:** Antigravity, Gemini 4 Neon (High)
- **Mục tiêu:** bổ sung phân trang server-side (`page`, `size`, `totalElements`, `totalPages`, `last` với `PageableValidationUtils`) và tham số `search` cho 6 endpoint ưu tiên cao/trung bình (`GET /api/v1/sections`, `GET /api/v1/assessments/{id}/scores` & `/sections/{sectionId}/assessments/{assessmentId}/scores`, `GET /api/v1/sections/{sectionId}/enrollments`, `GET /api/v1/reports/analytics` với `dimension=STUDENT`, `GET /api/v1/program-versions/{pvId}/courses`, `GET /api/v1/terms`), kèm endpoint xuất toàn bộ bảng điểm `GET /api/v1/assessments/{id}/scores/export` (và `/sections/{sectionId}/assessments/{assessmentId}/scores/export`, hỗ trợ `format=csv|xlsx`).
- **File AI sinh/sửa:**
  - `SectionService.java`, `SectionServiceImpl.java`, `SectionController.java` (`GET /api/v1/sections`: `PageResponse<SectionResponse>` + `search`, `page`, `size`; chỉ tải batch giảng viên cho trang hiện tại)
  - `AssessmentService.java`, `AssessmentServiceImpl.java`, `AssessmentController.java` (`GET /assessments/{id}/scores` & `/sections/{sectionId}/assessments/{assessmentId}/scores`: phân trang theo nhóm sinh viên + `search`, tách helper dựng header CSV dùng chung với `template-csv`, thêm `/scores/export` qua `ImportTemplateResponses.of`)
  - `EnrollmentListResponse.java`, `EnrollmentService.java`, `EnrollmentServiceImpl.java`, `EnrollmentController.java` (giữ nguyên tổng `enrolled`/`withdrawn` toàn lớp, thêm `search`, `page`, `size` và metadata phân trang vào `EnrollmentListResponse`, không lộ `fullName` ở `EnrollmentResponse`)
  - `AnalyticsQueryRequest.java`, `AnalyticsMatrixResponse.java`, `AnalyticsServiceImpl.java` (thêm `page`, `size`, `search` cho `dimension=STUDENT` cắt theo trục `columns[]` + từng hàng `matrix`, `exportAnalyticsMatrixCsv` gọi `buildFilteredMatrix` không cắt trang)
  - `ProgramCourseService.java`, `ProgramCourseServiceImpl.java`, `ProgramCourseController.java` (`PageResponse<ProgramCourseResponse>` + `page`, `size`)
  - `TermService.java`, `TermServiceImpl.java`, `TermController.java` (`PageResponse<TermResponse>` + `search`, `status`, `page`, `size`)
  - Tests & Docs: `SectionServiceImplTest.java`, `SectionIntegrationTest.java`, `ImportBatchUploadIntegrationTest.java`, `EnrollmentServiceImplTest.java`, `EnrollmentIntegrationTest.java`, `AnalyticsServiceImplTest.java`, `AnalyticsControllerIntegrationTest.java`, `ProgramCourseServiceImplTest.java`, `CourseIntegrationTest.java`, `ProgramAndSyllabusImportIntegrationTest.java`, `PublicHomepageCatalogIntegrationTest.java`, `TermServiceImplTest.java`, `TermIntegrationTest.java`, `doc/tech-debt.md` (TD-30), `doc/plan/F05a-hoc-ky-lop-sinh-vien.md`, `logsAI/ai-usage-log.md`
- **Kiểm chứng đã làm:**
  - `.\mvnw.cmd spotless:apply test-compile spotless:check spotbugs:check pmd:cpd-check test "-Dtest=SectionServiceImplTest,SectionIntegrationTest,AssessmentIntegrationTest,ImportBatchUploadIntegrationTest,EnrollmentServiceImplTest,EnrollmentIntegrationTest,EnrollmentImportIntegrationTest,AnalyticsServiceImplTest,AnalyticsControllerIntegrationTest,ProgramCourseServiceImplTest,CourseIntegrationTest,ProgramAndSyllabusImportIntegrationTest,PublicHomepageCatalogIntegrationTest,TermServiceImplTest,TermIntegrationTest"`: `BUILD SUCCESS`, 0 lỗi Spotless / SpotBugs / PMD-CPD, 199/199 test đạt trên MongoDB replica set `localhost:27018` (`AnalyticsControllerIntegrationTest` 22, `AssessmentIntegrationTest` 18, `CourseIntegrationTest` 7, `EnrollmentImportIntegrationTest` 14, `EnrollmentIntegrationTest` 6, `ImportBatchUploadIntegrationTest` 19, `ProgramAndSyllabusImportIntegrationTest` 2, `PublicHomepageCatalogIntegrationTest` 2, `SectionIntegrationTest` 16, `TermIntegrationTest` 4, `AnalyticsServiceImplTest` 39, `EnrollmentServiceImplTest` 13, `ProgramCourseServiceImplTest` 13, `SectionServiceImplTest` 14, `TermServiceImplTest` 10).
- **Lỗi/ảo giác AI tự phát hiện:** trong lần chạy đầu (197/199), `AssessmentIntegrationTest.templateCsv_syllabusTemplate_returns422` lỗi khớp chuỗi thông báo do gộp helper kiểm tra phạm vi lớp; đã cho phép truyền thông báo riêng từng ngữ cảnh, đồng thời bổ sung `courseId` vào request phân trang trong `AnalyticsServiceImplTest.testStudentHappyPathCloLevel` để tránh thiếu stub `courseSectionRepository.findById` khi tách `pagedReq`; chạy lại đạt 199/199.
- **Sinh viên đã sửa/rà soát:** _chờ sinh viên rà soát_
- **Commit / PR:** chưa commit (lưu ý cập nhật `LMS-UI` đọc `.content` ở 5 endpoint dùng `PageResponse`, đọc `enrollments` kèm `totalElements`/`page`/`size` ở `/enrollments`, cập nhật bảng ma trận `STUDENT` đọc `totalElements`/`page`/`size` ở `/reports/analytics`, và gọi `/scores/export` khi xuất CSV/XLSX bảng điểm).
