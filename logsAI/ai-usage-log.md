# Nhật ký sử dụng AI (AI Usage Log)

Phục vụ Rubric TC2.3 và Mục 8 #5. Nhật ký của repo `LMS-API`; repo `LMS-UI` có nhật ký riêng tại `D:\TLCN\LMS-UI\logsAI\ai-usage-log.md` (cùng định dạng). Khi nộp, gộp hai nhật ký. Các dòng #1–#7 bên dưới ghi cả việc đã sửa rule bên UI trước khi tách nhật ký.
Quy tắc ghi: `.claude/rules/01-ai-usage-and-ownership.md`.

- Ghi đúng sự thật; kê khai phải khớp `git log` và phần vấn đáp (mâu thuẫn = G4).
- Agent điền các cột về công cụ, phạm vi, prompt, file. Cột **"Sinh viên đã sửa/rà soát"** và **"Commit"**
  do sinh viên điền sau khi đọc và hiểu mã.
- Mỗi vị trí mã AI sinh phải được sinh viên giải thích được (mục tiêu 100% khi hội đồng chọn ≥ 5 vị trí).

## A. Nhật ký phiên làm việc

| # | Ngày | Sinh viên | Repo | Công cụ / mô hình | Phạm vi | Prompt chính (tóm tắt) | File AI sinh/sửa | Sinh viên đã sửa/rà soát | Kiểm chứng đã làm | Commit |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-20 | _chờ điền_ | LMS-API, LMS-UI | Claude Code, claude-sonnet-5-5 | Soạn bộ rule cho agent theo Rubric; bảng theo dõi minh chứng; mẫu log này | "Đọc rubric và tài liệu, set các rule để agent không vi phạm nguyên tắc của rubric" | `CLAUDE.md`, `.claude/rules/01…05`, `logsAI/ai-usage-log.md` (ban đầu tạo ở `doc/`, sau chuyển sang `logsAI/`), `doc/rubric-checklist.md`, `LMS-UI/CLAUDE.md` | _Chờ sinh viên rà soát_ | Đối chiếu với `Rubric…md` (ngưỡng, mã G/V); đọc `pom.xml`, `.gitignore`, `package.json` để ghi hiện trạng. Chưa chạy build/test vì chưa có mã | Nhánh `chore/project-setup` (sinh viên điền mã commit) |
| 2 | 2026-10-20 | _chờ điền_ | LMS-API, LMS-UI | Claude Code, claude-sonnet-5-5 | Ghi công nghệ đã chốt vào rule | "Đã chốt: Java, Spring Boot, AngularJS, TypeScript, MongoDB" | `CLAUDE.md`, `LMS-UI/CLAUDE.md` | _Chờ sinh viên rà soát_ | Đối chiếu với `pom.xml`, `package.json`, `BS.md`. Sinh viên đã xác nhận dùng Angular theo repo LMS-UI (không phải AngularJS 1.x) | Nhánh `chore/project-setup` (sinh viên điền mã commit) |
| 3 | 2026-10-20 | _chờ điền_ | LMS-API, LMS-UI | Claude Code, claude-sonnet-5-5 | Chuyển nhật ký AI sang thư mục `logsAI/` và cập nhật đường dẫn trong rule | "Logs AI để trong folder logsAI, tạo folder và set rule như vậy" | `logsAI/ai-usage-log.md` (di chuyển), `CLAUDE.md`, `LMS-UI/CLAUDE.md`, `.claude/rules/01…`, `.claude/rules/05…`, `doc/rubric-checklist.md` | _Chờ sinh viên rà soát_ | Grep toàn bộ `.md` để chắc không còn đường dẫn `doc/ai-usage-log.md` | Nhánh `chore/project-setup` (sinh viên điền mã commit) |
| 4 | 2026-10-20 | _chờ điền_ | LMS-API | Claude Code, claude-sonnet-5-5 | Chuyển `pom.xml` từ JPA/PostgreSQL sang MongoDB | "sửa pom.xml sang MongoDB đi" | `pom.xml`, `CLAUDE.md` | _Chờ sinh viên rà soát_ | Kiểm tra artifact `spring-boot-starter-data-mongodb` và `-mongodb-test` 4.1.0 tồn tại trên Maven Central; chạy `mvnw test` (Java 25.0.1): context khởi động, Spring Data MongoDB được nạp. Chỉ có 1 test mẫu `contextLoads` | Nhánh `chore/project-setup` (sinh viên điền mã commit) |
| 5 | 2026-10-20 | _chờ điền_ | LMS-API, LMS-UI | Claude Code, claude-sonnet-5-5 | Cập nhật rule cho đồ án nhóm 2 sinh viên dùng chung repo | "repo này là làm chung của cả 2 sinh viên" | `CLAUDE.md`, `LMS-UI/CLAUDE.md`, `.claude/rules/01…`, `03…`, `05…`, `logsAI/ai-usage-log.md` (thêm cột Sinh viên) | _Chờ sinh viên rà soát_ | Đối chiếu `BS.md` §2, `Misa.md` §4 và §9 về vai trò IBS/IT và minh chứng cá nhân | Nhánh `chore/project-setup` (sinh viên điền mã commit) |
| 6 | 2026-10-20 | _chờ điền_ | LMS-API | Claude Code, claude-sonnet-5-5 | Soạn kế hoạch xây dựng sản phẩm từ các file doc | "từ các file doc có 1 plan hoàn chỉnh để tiến hành build sản phẩm" | `doc/ke-hoach-xay-dung.md`, `CLAUDE.md` | _Chờ sinh viên rà soát. Các số NFR/KPI và phân công IBS/IT là đề xuất của AI, chưa được xác nhận_ | Đối chiếu F01–F08, 30 collection, Q01–Q16, ngưỡng Mức 5 trong rubric; không có chạy mã | Nhánh `chore/project-setup` (sinh viên điền mã commit) |
| 7 | 2026-10-20 | _chờ điền_ | LMS-API, LMS-UI | Claude Code, claude-sonnet-5-5 | Cập nhật kế hoạch lên v0.2 (đang T5, bỏ ML, hỗ trợ chéo, lệch tài liệu thì theo CSDL) và đồng bộ rule | "đang ở tuần 5; không phân công cứng nhắc; ML không nằm trong phạm vi; ba học phần để sau; lệch thì theo CSDL" | `doc/ke-hoach-xay-dung.md`, `CLAUDE.md`, `LMS-UI/CLAUDE.md`, `.claude/rules/02…`, `05…` | _Chờ sinh viên rà soát_ | Đọc `git log` hai repo để ghi hiện trạng (mỗi repo 1 commit ngày 2026-08-07); đếm lại số story (32 Must + 7 Should + 1 Could = 40); grep `ml_experiments` trong `database_import.js` (có khai báo ở dòng 493, chưa dùng) | Nhánh `chore/project-setup` (sinh viên điền mã commit) |
| 8 | 2026-10-20 | _chờ điền_ | LMS-API | Claude Code, claude-sonnet-5-5 | Đổi quy tắc: mỗi repo một nhật ký AI riêng | "set rule bên phần UI" (kéo theo việc tách nhật ký UI) | `CLAUDE.md`, `.claude/rules/01…`, `05…`, `logsAI/ai-usage-log.md` | _Chờ sinh viên rà soát_ | Đọc lại các đường dẫn nhật ký trong rule hai repo để đảm bảo nhất quán | Nhánh `chore/project-setup` (sinh viên điền mã commit) |
| 9 | 2026-10-20 | _chờ điền_ | LMS-API | Claude Code, claude-sonnet-5-5 | Soạn dự thảo K1 và hồ sơ hỗ trợ; sửa `.gitignore`; README; khung CI | "bạn hãy làm theo hướng bạn nghĩ tối ưu nhất" | `doc/cam-ket-san-pham-metric.md`, `doc/quy-trinh/phan-cong.md`, `doc/coding-convention.md`, `doc/tech-debt.md`, `doc/rubric-checklist.md`, `README.md`, `.gitignore`, `src/main/resources/application.properties.example`, `.github/workflows/ci.yml`; gỡ `application.properties` khỏi git index | _Chờ sinh viên rà soát. K1, phân công, convention là DỰ THẢO; các KPI/NFR là đề xuất của AI_ | Chạy thực nghiệm để xác định property Mongo của Spring Boot 4: `spring.mongodb.uri` có tác dụng, `spring.data.mongodb.uri` thì không (host vẫn là localhost) — suýt viết sai file mẫu theo trí nhớ. `mvnw verify` BUILD SUCCESS; test vẫn pass khi không có MongoDB. `git check-ignore` xác nhận `application.properties` bị ignore, `.example` thì không. **Chưa kiểm chứng:** cú pháp và lần chạy thật của `ci.yml` trên GitHub (máy không có Python/js-yaml) | Nhánh `chore/project-setup` (sinh viên điền mã commit) |

## B. Lỗi / ảo giác của AI đã phát hiện (mục tiêu ≥ 5, kèm phân tích nguyên nhân)

| # | Ngày | Mô tả lỗi | Phát hiện bằng cách nào | Nguyên nhân | Commit sửa |
|---|---|---|---|---|---|
| | | | | | |

## C. Danh sách kiểm soát đầu ra AI đã áp dụng

Mỗi PR có AI hỗ trợ tick các mục: build/lint/test đã chạy thật; API thư viện đã đối chiếu tài liệu chính
thức đúng phiên bản; dependency mới có lý do và giấy phép; không có secret/dữ liệu thật; không chép mã
ngoài; nghiệp vụ đối chiếu `GiaiThich_CoSoDuLieu.md`.

## D. Nguồn và giấy phép của mã/tài liệu tham khảo

| Ngày | Nội dung | Nguồn (URL) | Giấy phép | Dùng ở đâu |
|---|---|---|---|---|
| | | | | |
