# Rule 03 — Chất lượng mã nguồn và quản lý Git

Căn cứ: TC2.4 (10 điểm), G2, G8. Mức 5: 0 lỗi lint; 0 issue Blocker/Critical; trùng lặp ≤ 3%;
≥ 90% số tuần có commit; ≥ 90% thay đổi qua Pull Request có review; 0 secret; cấu hình qua biến môi
trường; README, hướng dẫn cài đặt, sơ đồ triển khai; danh mục nợ kỹ thuật kèm kế hoạch xử lý.

## Cấu trúc mã (Spring Boot)

- Package theo chức năng, bám 8 nhóm trong thiết kế: người dùng, CTĐT, học phần/CLO, ngân hàng câu hỏi/bài
  đánh giá, học kỳ/lớp/sinh viên, nhập điểm, đo lường, báo cáo/cải tiến. Trong mỗi nhóm:
  `controller` → `service` → `repository`, cùng `dto`, `mapper`, `document` (entity Mongo).
- Controller chỉ nhận/trả DTO và gọi service; **không** chứa logic nghiệp vụ, không trả `document` ra ngoài.
  Logic đo lường nằm ở service riêng, thuần (pure) và dễ test, không phụ thuộc HTTP.
- Không trộn logic nghiệp vụ với truy cập dữ liệu hoặc giao diện (lỗi bị trừ ở Mức 3).
- Validate đầu vào bằng Bean Validation; xử lý lỗi tập trung (`@RestControllerAdvice`) với mã lỗi nhất quán.
  Đã có sẵn `spring-boot-starter-validation`, dùng nó thay vì tự viết.
- Log có cấu trúc, không ghi dữ liệu nhạy cảm. Có `correlationId` nếu thêm được mà không phức tạp hóa.
- Xử lý biên: danh sách rỗng, tệp rỗng/sai định dạng, timeout, trùng lặp, xung đột đồng thời.
- Hàm ngắn, một việc. Không magic number, không chuỗi hardcode: dùng enum/hằng/cấu hình. Không comment
  nhắc lại điều mã đã nói; chỉ comment "vì sao".

## Convention và công cụ phân tích

- Coding convention phải **được công bố** trong `doc/coding-convention.md` (Rubric mục 8 #7). Mặc định đề
  xuất: Google Java Style (kiểm bằng Checkstyle hoặc Spotless) cho API; Angular Style Guide + ESLint +
  Prettier cho UI. Người dùng có thể đổi; hỏi trước khi cố định.
- Mục tiêu bắt buộc trước khi báo "xong": **0 lỗi lint**, **0 Blocker/Critical** (SonarQube/SonarCloud hoặc
  SpotBugs), **trùng lặp ≤ 3%**. Chạy công cụ thật; nếu chưa cài thì nói rõ là chưa đo, đừng ước lượng.
- Không tắt rule, không `@SuppressWarnings`/`// eslint-disable` để cho qua. Nếu phải ngoại lệ, ghi lý do
  tại chỗ và trong `doc/tech-debt.md`.

## Cấu hình và secret

- Không commit cấu hình thật. `application.properties` chỉ chứa giá trị không nhạy cảm hoặc `${ENV_VAR}`;
  giá trị thật qua biến môi trường/secret của nền tảng. Cập nhật `application.properties.example` khi thêm khóa.
- Trước commit chạy quét secret (gitleaks hoặc trufflehog). Thấy secret: dừng, báo, đề nghị thu hồi khóa.
  Không "xóa trong commit sau" rồi coi như xong, vì lịch sử vẫn còn.
- Sửa `.gitignore` đúng đường dẫn (`src/main/resources/application.properties`, không có dấu chấm đầu) và
  bỏ track file đã lỡ commit; cần sự đồng ý của người dùng vì đụng tới index.

## Git

- Nhánh theo tác vụ: `feat/<mô-tả>`, `fix/…`, `docs/…`, `test/…`, `chore/…`. Không làm trực tiếp trên `main`.
- Mọi thay đổi vào `main` qua Pull Request có review (≥ 90% PR có review). Hai sinh viên review chéo nhau:
  người mở PR không tự approve; agent không approve/merge thay. Cấu hình bảo vệ nhánh `main` (bắt buộc ≥ 1
  review) để chỉ số này đạt tự nhiên.
- Mỗi sinh viên commit bằng danh tính Git của riêng mình; lịch sử Git là minh chứng đóng góp cá nhân. Mô tả PR nêu: mục tiêu, user
  story/AC liên quan, cách kiểm chứng, phần có AI hỗ trợ, ảnh hưởng tới tài liệu/sơ đồ.
- Commit nhỏ, một ý, thông điệp có ý nghĩa theo Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`,
  `refactor:`, `chore:`), tiếng Anh, thể mệnh lệnh. Không gộp mọi thứ vào một commit lớn cuối kỳ.
- Không `--no-verify`, không `push --force` lên nhánh chung, không rewrite lịch sử đã đẩy (lịch sử là
  minh chứng chấm TC2.3 và TC2.4).
- Chỉ commit/push khi người dùng yêu cầu. Khi thấy `git log` cách quãng hơn một tuần hoặc commit dồn cục,
  nhắc người dùng (mục tiêu ≥ 90% số tuần có commit; tần suất báo cáo tiến độ ≥ 1 lần/tuần, G2).
- Repository phải cấp quyền cho hội đồng và build được từ mã đã nộp (G8): sau mỗi thay đổi lớn, kiểm tra
  `./mvnw verify` chạy được từ bản clone sạch.

## Tài liệu kỹ thuật cho người tiếp nhận

Giữ cập nhật: `README.md` (mục đích, kiến trúc, yêu cầu môi trường, cài đặt, chạy, test, biến môi trường),
hướng dẫn cài đặt/vận hành/phục hồi, sơ đồ triển khai, `doc/tech-debt.md` (mỗi mục: mô tả, tác động, kế
hoạch xử lý, người phụ trách). Thêm dependency, biến môi trường hay bước chạy mới → cập nhật README cùng lúc.

## Dependency

Mỗi dependency phải có lý do gắn yêu cầu, giấy phép tương thích, phiên bản cố định. Xóa dependency không
dùng. Bộ phiên bản JDK/Spring Boot/Node/Angular/MongoDB phải thống nhất với Trung tâm CNPM (`BS.md` §4):
không tự nâng/hạ phiên bản chính khi chưa hỏi.
