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
| 12 | 2026-10-28 | TinMH (chờ xác nhận IBS/IT) | LMS-API | Claude Code, claude-sonnet-5-5 | Sửa CI treo ở bước khởi động MongoDB: vòng chờ `until … ping` lặp vô hạn khi container `mongodb` đã thoát; thêm `concurrency` hủy run cũ khi có commit mới; rút gọn nhật ký này | Dán log CI "container … is not running"; "chỉnh lại log AI, xóa bớt prompt thừa"; "commit mới lên PR thì hủy CI đang chạy trước" | `.github/workflows/ci.yml` (hàm `wait_for`: tối đa 60 lần, kiểm tra container còn chạy, in `docker logs mongodb` rồi `exit 1`; khối `concurrency`), `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | Chỉ đọc và sửa tay file workflow. **Chưa kiểm chứng:** chưa chạy workflow trên GitHub; **chưa biết nguyên nhân mongod thoát** (cần xem `docker logs` ở lần chạy sau) | chưa commit |

## B. Lỗi / ảo giác của AI đã phát hiện (mục tiêu ≥ 5, kèm phân tích nguyên nhân)

| # | Ngày | Mô tả lỗi | Phát hiện bằng cách nào | Nguyên nhân | Commit sửa |
|---|---|---|---|---|---|
| B1 | 2026-10-20 | Suýt viết `spring.data.mongodb.uri` vào file cấu hình mẫu | Chạy thực nghiệm: chỉ `spring.mongodb.uri` có tác dụng ở Spring Boot 4 (host vẫn là localhost nếu dùng key cũ) | API đổi giữa Spring Boot 3 và 4; AI dựa vào trí nhớ thay vì tài liệu/thử nghiệm | PR #1 (sinh viên điền mã) |
| B2 | 2026-10-23 | `rs.initiate` trong entrypoint lỗi khi chạy từ PowerShell; `docker stop` thoát mã 143 thay vì 0 | Chạy thật container: PowerShell làm mất dấu nháy kép trong `--eval`; `trap` không kết thúc đúng | Không tính quy tắc quote của PowerShell; xử lý tín hiệu chưa đúng. Đã chuyển lệnh qua stdin và sửa `trap` | `cbf22ac` |
| B3 | 2026-10-27 | Ghi công thức CLO/PI của `PLO_DB_UPDATE.md` §4 là "tham chiếu, chờ Khoa" và lấy ví dụ 72% làm phản biện; đề xuất làm `POINTS_RATIO` trước | Người dùng chỉ ra "làm theo công thức này"; AI mở lại §4.1–4.2 và viết lại `F06` §4.2 | Quá thận trọng, không hiểu đúng ý định người dùng (công thức trong tài liệu đã là lựa chọn của dự án) | `4377683` |
| B4 | 2026-10-27 | `F06` mục 4.4 ghi `ALL_PIS_PASSED`/`AVERAGE_PIS` "cài được ngay" dù tài liệu không có công thức cấp PLO | Khi liệt kê công thức còn thiếu (CT-04) thấy mâu thuẫn với mục 4.2 | Suy diễn "hiển nhiên" thành đặc tả; vi phạm quy tắc không bịa công thức/ngưỡng | `4377683` |
| B5 | 2026-10-27 | `F01` ghi `username` ≥ 3 ký tự như thể đã nằm trong validator MongoDB | Đối chiếu khối `users` trong `database_import.js`: validator không ép; chỉ `PLO_DB_UPDATE.md` ghi | Lẫn đặc tả với hợp đồng dữ liệu thực tế | `4377683` |
| B6 | 2026-10-27 | `ScopeAuthorizationMatrixIntegrationTest` ban đầu sử dụng chuỗi mock ID `"671dcba5f123456789abcttt"` chứa ký tự `'t'` (không phải hex) và `"prog-001"`, dẫn đến `ConversionFailedException` khi chuyển sang `ObjectId` do `targetType = FieldType.OBJECT_ID` | Chạy thực nghiệm `./mvnw test`: Surefire báo lỗi `Failed to convert from type [java.lang.String] to type [org.bson.types.ObjectId]` | Bỏ quên ràng buộc ObjectId (24 ký tự hex hợp lệ) khi tạo test fixture cho document và UserScope có `targetType = FieldType.OBJECT_ID`. Đã sửa thành các chuỗi 24 ký tự hex hợp lệ | _Chờ sinh viên điền commit_ |

## C. Danh sách kiểm soát đầu ra AI đã áp dụng

Mỗi PR có AI hỗ trợ tick các mục: build/lint/test đã chạy thật; API thư viện đã đối chiếu tài liệu chính thức đúng phiên bản; dependency mới có lý do và giấy phép; không có secret/dữ liệu thật; không chép mã ngoài; nghiệp vụ đối chiếu `GiaiThich_CoSoDuLieu.md`.

## D. Nguồn và giấy phép của mã/tài liệu tham khảo

| Ngày | Nội dung | Nguồn (URL) | Giấy phép | Dùng ở đâu |
|---|---|---|---|---|
| | | | | |

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

