# Danh mục nợ kỹ thuật

Phục vụ Rubric TC2.4 (Mức 5: có danh mục nợ kỹ thuật kèm kế hoạch xử lý). Dùng chung cho cả `LMS-API` và `LMS-UI`
(cột Repo). Cập nhật khi phát hiện hoặc xử lý. "Phụ trách" và "Hạn" do hai sinh viên điền.

| ID | Repo | Mô tả | Tác động | Kế hoạch xử lý | Phụ trách | Hạn | Trạng thái |
|---|---|---|---|---|---|---|---|
| TD-01 | API | `pom.xml` còn `spring-boot-starter-websocket` và `spring-boot-starter-mail` mà thiết kế chưa dùng | Dependency không giải thích được (rủi ro vấn đáp, Mục 6); tăng bề mặt tấn công | Bỏ, hoặc nêu lý do giữ trong ADR | [ĐIỀN] | [ĐIỀN] | Mở |
| TD-02 | UI | SSR đang bật (`@angular/ssr`, `express`, hydration) mà thiết kế chưa nêu lý do | Thành phần không giải thích được; triển khai phức tạp hơn; cần `allowedHosts` | Quyết giữ/bỏ bằng ADR trước khi xây màn hình | [ĐIỀN] | [ĐIỀN] | Mở |
| TD-03 | API | Java 25 chưa xác nhận với Trung tâm CNPM | Công cụ (Sonar, JaCoCo) có thể chưa hỗ trợ đầy đủ | Hỏi Trung tâm; cân nhắc LTS 21; thử công cụ sớm | [ĐIỀN] | [ĐIỀN] | Mở |
| TD-04 | API | MongoDB cục bộ đang standalone | Transaction nhập điểm sẽ lỗi | Chuyển replica set (dev, CI, staging) trước khi làm F05 | [ĐIỀN] | [ĐIỀN] | Mở |
| TD-05 | UI | `app.html` còn template mẫu của Angular; `app.spec.ts` kiểm tra tiêu đề mẫu | Test gắn với nội dung giả | Thay template, sửa test theo hành vi thật (không xóa test) | [ĐIỀN] | [ĐIỀN] | Mở |
| TD-06 | API | `database_import.js` có khai báo `ml_experiments` dù ML ngoài phạm vi | Phần không dùng trong script (collection tùy chọn) | Quyết bỏ khỏi script hoặc giữ có ghi chú | [ĐIỀN] | [ĐIỀN] | Mở |
| TD-07 | API | Chưa có linter/phân tích tĩnh (Checkstyle/Spotless, SonarQube, SpotBugs) | Chưa đo được lint, Blocker/Critical, trùng lặp (TC2.4) | Cài và đưa vào CI | [ĐIỀN] | [ĐIỀN] | Mở |
| TD-08 | API | Chưa có công cụ coverage (JaCoCo) | Chưa có báo cáo coverage (TC2.5) | Cài JaCoCo, đặt cổng coverage cho mô-đun lõi | [ĐIỀN] | [ĐIỀN] | Mở |
| TD-09 | Cả hai | CI mới có build/lint/test cơ bản; chưa có quét secret, quét phụ thuộc (API), Docker, deploy, giám sát | Chưa đủ 6 chặng (TC2.6) | Bổ sung dần theo `ke-hoach-xay-dung.md` §7 | [ĐIỀN] | [ĐIỀN] | Mở |
| TD-10 | Cả hai | Chưa có bảo vệ nhánh `main` (bắt buộc review) | Chỉ số "thay đổi qua PR có review" không được bảo đảm | Bật trong cài đặt GitHub của repo | [ĐIỀN] | [ĐIỀN] | Mở |
| TD-11 | UI | `npm` 11 chặn script cài đặt của `esbuild`, `lmdb`, `msgpackr-extract`, `@parcel/watcher` (cảnh báo `install-scripts`); build/test vẫn chạy | Có thể phát sinh khác biệt môi trường CI | Theo dõi; nếu CI lỗi thì cấu hình `allow-scripts` có chủ đích và ghi lý do | [ĐIỀN] | [ĐIỀN] | Theo dõi |
| TD-12 | UI | Hợp đồng API giữa UI và backend chưa có (interface thủ công hay sinh từ OpenAPI) | Dễ lệch tên trường | Quyết bằng ADR cùng bên API | [ĐIỀN] | [ĐIỀN] | Mở |

## Đã xử lý

| ID | Mô tả | Cách xử lý | Ngày |
|---|---|---|---|
| TD-A1 | `.gitignore` của API gõ sai đường dẫn và `application.properties` đã bị track | Sửa `.gitignore`, gỡ khỏi git index (`git rm --cached`), thêm `.env`; chờ commit | 2026-10-20 |
| TD-A2 | `pom.xml` dùng JPA và PostgreSQL | Chuyển sang `spring-boot-starter-data-mongodb` (+ `-test`) | 2026-10-20 |
| TD-A3 | UI có 26 lỗ hổng trong `npm audit` (3 critical) | Thêm ESLint; nâng Angular 21.2.19 → 21.2.25 qua `ng update`, Vitest 4.1.10 → 4.1.12; `npm audit` còn 0 | 2026-10-20 |
