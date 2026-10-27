# LMS-API — Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra CTĐT

Learning Outcomes Management and Analytics System. Tiểu luận chuyên ngành CNTT, chấm theo
`doc/Rubric_Do_An_Mon_Hoc_CNPM_V2-1.docx.md` (gọi tắt: **Rubric**).

- Backend: repo này (`D:\TLCN\LMS-API`) — Java, Spring Boot, MongoDB.
- Frontend: repo riêng `D:\TLCN\LMS-UI` — Angular + TypeScript. Có `CLAUDE.md` riêng.
- **Công nghệ đã chốt (người dùng xác nhận): Java, Spring Boot, Angular (TypeScript), MongoDB.**
  Không đề xuất hay thêm công nghệ chính khác (JPA/SQL, framework UI khác…) khi chưa hỏi.
  Frontend dùng **Angular hiện đại theo repo `LMS-UI` (Angular 21, TypeScript)**, đã được người dùng xác nhận.
  Không dùng AngularJS 1.x (đã hết hỗ trợ).
- Tài liệu: `doc/BS.md`, `doc/Misa.md` (kế hoạch, hướng dẫn đọc CTĐT), `doc/GiaiThich_CoSoDuLieu.md`
  (thiết kế CSDL v0.5, 14 nguyên tắc; v0.5 cập nhật theo `doc/PLO_DB_UPDATE.md`), `doc/database_import.js`
  (script dựng CSDL), `docker/mongo/` (image MongoDB replica set nạp script này, xem `README.md`).
- Chuỗi nghiệp vụ: Điểm câu hỏi/tiêu chí → CLO → PI → PLO → Báo cáo → Hành động cải tiến.

## Nhóm 2 sinh viên dùng chung repo

Hai sinh viên cùng làm trong repo này và repo `LMS-UI`: **sinh viên IBS** (nghiệp vụ, chuẩn hóa dữ liệu, thiết
kế báo cáo, kiểm chứng kết quả) và **sinh viên IT** (thiết kế kỹ thuật, lập trình, tích hợp, kiểm thử kỹ thuật),
theo `BS.md` §2 và `Misa.md` §4. Hai bạn **không làm theo phân công cứng nhắc**: IBS/IT chỉ là bên *chủ trì*,
hai người hỗ trợ chéo tự do. Hệ quả cho agent:

- **Biết ai đang làm.** Đầu phiên đọc `git config user.name`; nếu không rõ đó là sinh viên nào, hỏi một lần.
  Mỗi mục AI Usage Log ghi tên sinh viên yêu cầu.
- **Hỗ trợ chéo là bình thường.** Agent không từ chối hay hỏi lại chỉ vì việc thuộc bên chủ trì kia. Nhưng
  Rubric Bước 2 vẫn yêu cầu bảng phân công theo thành viên và hội đồng cần minh chứng đóng góp cá nhân, nên
  hai bạn ghi "chủ trì" từng hạng mục ở `doc/quy-trinh/phan-cong.md` (chỉ cần mức khung); đóng góp thực tế
  được chứng minh qua `git log`, PR, log AI. Agent giúp giữ những dấu vết đó chính xác (commit đúng người,
  log đúng người), không chặn việc.
- **Cả hai phải giải thích được mô-đun lõi.** Hội đồng chọn ngẫu nhiên vị trí trong toàn bộ hồ sơ và hỏi từng
  người; một vị trí không giải thích được là V1–V3 cho đồ án. Sinh viên IT phải hiểu ý nghĩa nghiệp vụ của phép
  tính; sinh viên IBS phải hiểu cấu trúc dữ liệu và cách kiểm chứng kết quả (`Misa.md` §4). Vì vậy mã lõi
  (đo lường, nhập điểm, phân quyền, truy vết) phải kèm mục "Để vấn đáp" viết cho người **không** viết nó.
- **Review chéo.** Mọi PR do một sinh viên mở thì sinh viên còn lại review (IBS soát đáp ứng nghiệp vụ và AC,
  IT soát kỹ thuật). Agent không tự approve/merge và không đóng vai người review thay sinh viên.
- **Mỗi sinh viên dùng danh tính Git của riêng mình** (`user.name`, `user.email`). Không commit bằng danh tính
  của người kia, không gộp công việc hai người vào một tài khoản.
- Việc khớp tài liệu, sơ đồ, ADR, báo cáo tiến độ vẫn là trách nhiệm chung; mỗi mục ghi người phụ trách.

## Vì sao có bộ rule này

Rubric cho phép dùng AI và không trừ điểm vì dùng AI. Rubric trừ điểm khi sinh viên **không làm chủ**
sản phẩm: nội dung không giải thích được, ngoài phạm vi đã chốt, hoặc minh chứng không thật bị coi là
đạo văn (Mục 6: V1–V4, tối đa 0 điểm toàn đồ án). Agent là công cụ; sinh viên chịu trách nhiệm.
Mọi đầu ra của agent phải **giải thích được, đúng phạm vi, có minh chứng thật, nhắm ngưỡng Mức 5**.

## Quy tắc cứng (không được vi phạm, không cần người dùng nhắc lại)

1. **Đúng phạm vi.** Chỉ làm trong phạm vi của `GiaiThich_CoSoDuLieu.md` §1 và `BS.md` §3: tiếp nhận và đo
   lường kết quả có sẵn sau khi thi. KHÔNG tổ chức thi, sinh đề, duyệt *nội dung* đề thi, xuất phôi in đề; KHÔNG lưu
   bài làm sinh viên. Riêng *duyệt ma trận đề thi* (Trưởng bộ môn duyệt ánh xạ CLO–PI của bài đánh giá lớp, khóa
   `items[]`, CSDL v0.5, story NEW-01) là **đề xuất chờ GVHD (Q11)**: có trong CSDL, được phép làm theo
   `doc/chot-feature-do-an.md` nhưng không coi là đã duyệt; ghi ADR (`doc/adr/0001-…`).
   **ML nằm ngoài phạm vi hiện tại**: không viết mã, collection hay dependency ML. Việc ngoài phạm vi hoặc
   ngoài bản cam kết Bước 3 → dừng và hỏi.
2. **Không bịa minh chứng.** Không tự tạo số liệu khảo sát, biên bản, danh sách người dùng thử, điểm SUS,
   số coverage, lịch sử CI/deploy, nội dung nguyên văn PLO/PI/CLO, ánh xạ CLO–PI, công thức hay ngưỡng đo.
   Chưa có thì ghi "chưa có" và hỏi (Rubric Mục 6 V4: làm giả minh chứng = 0 điểm toàn đồ án).
3. **Không đưa vào thứ sinh viên không giải thích được.** Không thêm dependency, thư viện, pattern, cấu
   hình hay mã "cho đủ bộ" nếu không có lý do gắn với yêu cầu. Ưu tiên mã đơn giản, đọc hiểu được.
4. **Ghi AI Usage Log** vào `logsAI/ai-usage-log.md` (mỗi repo một nhật ký riêng; repo UI có `D:\TLCN\LMS-UI\logsAI\`) khi kết thúc mỗi phiên có tạo/sửa mã hoặc tài liệu
   (chi tiết: `.claude/rules/01-ai-usage-and-ownership.md`).
5. **Không làm yếu cơ chế kiểm soát để qua mặt:** không tắt lint, không `@Disabled`/skip test, không hạ
   ngưỡng coverage, không `--no-verify`, không `|| true` trong CI, không sửa test cho khớp mã sai.
6. **Không để lộ secret.** Không ghi khóa, mật khẩu, chuỗi kết nối thật vào mã, test, tài liệu, commit
   hay prompt. Cấu hình qua biến môi trường; chỉ commit `*.example`.
7. **Không sao chép mã/tài liệu từ nguồn khác** khi chưa khai nguồn và giấy phép trong log.
8. **Bất biến nghiệp vụ** trong `.claude/rules/02-domain-invariants.md` (không hardcode ngưỡng/trọng số,
   thiếu dữ liệu ≠ 0, I/R/M ≠ trọng số, Bloom ≠ điểm…). Câu hỏi Q01–Q16 chưa được xác nhận → làm dạng
   cấu hình có trạng thái xác nhận và báo người dùng, không tự quyết.
   **Tài liệu lệch nhau thì theo `GiaiThich_CoSoDuLieu.md` (thiết kế CSDL)** trước `Misa.md`/`BS.md`
   (người dùng đã chốt). Ví dụ: không có chức năng quản lý "minh chứng" trong hệ thống, chỉ lưu tệp điểm gốc
   và tham chiếu. Tài liệu CSDL tự mâu thuẫn hoặc chưa nói tới thì hỏi.
9. **Đổi thiết kế/phạm vi phải xin phép và ghi lý do** (ADR trong `doc/adr/`), vì Rubric TC2.1 yêu cầu
   mọi thay đổi thiết kế có lý do trong báo cáo tiến độ.
10. **Git:** không commit/push khi người dùng chưa yêu cầu; không làm việc trực tiếp trên `main`; không
    force-push. Chi tiết: `.claude/rules/03-code-quality-and-git.md`.

## Quy trình cho mỗi tác vụ

1. Xác định user story và acceptance criteria (AC) của tác vụ. Chưa có → đề xuất viết AC trước.
2. Viết test (hoặc test cùng lúc với mã), kể cả ca âm và ca biên.
3. Viết mã nhỏ, đúng lớp (controller → service → repository), đặt tên nhất quán.
4. Chạy build, lint, test thật và báo kết quả đúng như output. Không nói "đã pass" nếu chưa chạy.
5. Cập nhật tài liệu/sơ đồ bị ảnh hưởng **trong cùng thay đổi** (sơ đồ phải khớp mã, Rubric TC2.1).
6. Ghi AI Usage Log.
7. Cuối câu trả lời, thêm mục **"Để vấn đáp"**: mã vừa tạo làm gì, vì sao có trong đề tài, tham số/thư
   viện đến từ đâu, bỏ đi thì ảnh hưởng gì (đúng 4 câu hội đồng hỏi ở Mục 6.2).

## Chi tiết theo chủ đề (tự động nạp từ `.claude/rules/`)

| File | Tiêu chí Rubric |
|---|---|
| `01-ai-usage-and-ownership.md` | TC2.3, Mục 6 (đạo văn), G3, G4 |
| `02-domain-invariants.md` | TC2.1, TC2.2, tính đúng nghiệp vụ |
| `03-code-quality-and-git.md` | TC2.4, G2, G8 |
| `04-testing-and-cicd.md` | TC2.5, TC2.6, G5, G6 |
| `05-docs-and-evidence.md` | TC1, TC2.1, TC2.7, TC4, Mục 8, G1, G7 |

Bảng theo dõi mục tiêu và minh chứng theo từng tiêu chí: `doc/rubric-checklist.md`.
Kế hoạch xây dựng (backlog US-01…41, giai đoạn P0–P8, cổng K1–K7, rủi ro): `doc/ke-hoach-xay-dung.md`
(dự thảo; mục [ĐỀ XUẤT] và [CHỜ XÁC NHẬN] chưa phải quyết định chính thức).

## Hiện trạng đã phát hiện (chưa xử lý, hỏi người dùng trước khi sửa)

- `pom.xml` đã chuyển sang MongoDB (`spring-boot-starter-data-mongodb` + `-test`, bỏ JPA và PostgreSQL).
  Còn `websocket`, `mail` mà thiết kế chưa dùng đến: phải giữ hoặc bỏ có lý do (dependency phải giải thích
  được, Mục 6). Chờ người dùng quyết.
- MongoDB cục bộ đang chạy dạng **standalone** (thấy `type=STANDALONE` khi chạy test). Xác nhận nhập điểm cần
  transaction nên phải là **replica set**, nếu không transaction sẽ lỗi khi bắt đầu làm phần nhập điểm.
  Đã có image `docker/mongo/` (replica set `rs0` + nạp `database_import.js`) để dùng thay MongoDB cục bộ; staging/CI
  vẫn phải kiểm tra.
- `.gitignore` ghi `.src/main/resources/application.properties` (thừa dấu chấm đầu, nên không có tác dụng)
  và file này **đã được track**. Phải sửa và `git rm --cached` trước khi đặt cấu hình thật vào đó.
- Java 25 và Spring Boot 4.1.0 (API), Angular 21 (UI). `BS.md` §4 yêu cầu bộ phiên bản do Trung tâm CNPM
  thống nhất; chưa có xác nhận trong repo. Các phiên bản này mới, kiến thức của agent có thể lỗi thời:
  tra tài liệu chính thức, đừng đoán API.
- Chưa có CI/CD, README, convention, test thực (chỉ `contextLoads`), cấu hình MongoDB.
- Chưa thấy bản cam kết sản phẩm/metric Bước 3 trong `doc/`. Đây là điều kiện bắt buộc dự bảo vệ (G1).
