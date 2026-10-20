# Rule 05 — Tài liệu, minh chứng, quy trình đồ án

Căn cứ: Rubric Nguyên tắc 3 (chấm theo minh chứng; thiếu minh chứng thì tiêu chí không vượt Mức 3),
TC1, TC2.1, TC2.7, TC4, Mục 2 (quy trình), Mục 8 (hồ sơ), G1, G7.

## Nguyên tắc

Agent **soạn khung, hỗ trợ tổng hợp và kiểm tra nhất quán**; không tạo ra minh chứng thực địa. Mọi dữ liệu
thu từ người thật (phỏng vấn, khảo sát, thực nghiệm, chữ ký, xác nhận của giảng viên/Khoa) do con người
cung cấp. Thiếu thì ghi "chưa có", đừng điền chỗ trống bằng số hợp lý.

## Phân tích và thiết kế (TC2.1, mục tiêu Mức 5)

- 100% user story/use case có acceptance criteria (đặt mã `US-xx`, `AC-xx-y`, dạng Given/When/Then hoặc
  tương đương, kiểm chứng được). Không thêm chức năng không có US/AC.
- Mô hình quy trình nghiệp vụ, business rules, từ điển dữ liệu (bám `GiaiThich_CoSoDuLieu.md`).
- ≥ 4 loại sơ đồ khớp 100% mã nguồn: context, component, ERD/class (collection), sequence, deployment.
  Viết dưới dạng văn bản (Mermaid/PlantUML) trong `doc/` để diff được. Hội đồng đối chiếu ngẫu nhiên 5
  điểm; sơ đồ do AI sinh nguyên trạng là Mức 2. Khi đổi mã ảnh hưởng thiết kế, cập nhật sơ đồ trong cùng PR.
- ≥ 5 yêu cầu phi chức năng có ràng buộc định lượng (ví dụ thời gian phản hồi p95, số người dùng đồng thời,
  tiêu chuẩn bảo mật, thời gian nhập một lô điểm, tỷ lệ khả dụng). Số do sinh viên và GVHD chốt, agent chỉ
  đề xuất để chọn.
- Giải thích lựa chọn kiến trúc/công nghệ và phương án thay thế đã cân nhắc. Mỗi thay đổi thiết kế ghi một
  ADR ngắn ở `doc/adr/NNNN-tiêu-đề.md` (bối cảnh, phương án, quyết định, lý do, hệ quả, ngày).

## Quy trình đồ án (Mục 2)

- **Phân công hai sinh viên** (IBS và IT) ở mức khung: ai *chủ trì* từng chức năng F01–F08 và loại hồ sơ,
  ghi ở `doc/quy-trinh/phan-cong.md`; hai bạn được hỗ trợ chéo tự do. Rubric Bước 2 yêu cầu phân công rõ
  từng thành viên, nên vẫn cần bản khung này. Agent có thể soạn khung từ `Misa.md` §4 để hai bạn chỉnh,
  nhưng không tự quyết ai chủ trì gì.
- Tính theo từng người: log AI (cột sinh viên), `git log`, PR, báo cáo tiến độ. Hồ sơ nhóm cần minh chứng
  cá nhân nếu muốn kết luận năng lực từng sinh viên (`Misa.md` §9).

- **Bước 3, bản cam kết sản phẩm cuối cùng và metric** (`doc/cam-ket-san-pham-metric.md`, có chữ ký của sinh
  viên và GVHD) phải chốt chậm nhất tuần 7–10, nếu không đề tài bị đánh giá Không đạt (G1). Nếu file chưa
  có, nhắc người dùng như một việc chặn. Khi đã có, đó là đường cơ sở phạm vi: không mở rộng, không cắt
  bớt mà không xin phép và ghi ADR (khối lượng không tương xứng đăng ký là G9).
- **Báo cáo tiến độ** ≥ 1 lần/tuần: nội dung đã làm, vướng mắc, kế hoạch tuần sau (`doc/bao-cao-tien-do/`).
  Agent có thể soạn nháp từ `git log` và issue; sinh viên xác nhận, GVHD duyệt (2 tuần/lần). Agent không
  ký thay và không tự ghi "đã được GVHD duyệt".
- Cập nhật `doc/rubric-checklist.md` khi một mục có minh chứng thật; để "chưa có" khi chưa.

## Thực nghiệm người dùng (TC2.7)

Agent có thể soạn kịch bản tác vụ, mẫu phiếu SUS/CSAT, mẫu biên bản, bảng tính tỷ lệ hoàn thành. Agent
**không** tạo người tham gia, phản hồi, điểm SUS hay kết quả trước/sau. Mục tiêu Mức 5: ≥ 10 người dùng
thật thuộc nhóm mục tiêu (giảng viên, cán bộ phụ trách CTĐT/đảm bảo chất lượng, không chỉ bạn cùng lớp),
danh sách ẩn danh, tỷ lệ hoàn thành ≥ 90%, SUS ≥ 80, ≥ 1 vòng cải tiến có đo lại trước–sau.

## Tính thực tiễn (TC1)

Mức 5: phỏng vấn ≥ 3 bên liên quan thực (hoặc ≥ 30 phiếu) có biên bản và dữ liệu; bối cảnh, chân dung người
dùng, phạm vi làm / không làm; so sánh ≥ 3 giải pháp hiện có và khoảng trống; chốt ≥ 5 KPI nghiệp vụ định
lượng ở Bước 3 và giữ nhất quán đến khi bảo vệ. Agent có thể giúp so sánh giải pháp từ nguồn công khai đã
mở đọc và dẫn nguồn; không tự bịa tính năng của sản phẩm khác.

## Luận văn (TC4)

- Mạch: vấn đề → cơ sở lý thuyết → giải pháp → thực nghiệm → bàn luận → kết luận; kết luận bám số liệu
  thực nghiệm đã có.
- ≥ 5 tài liệu ngoại ngữ chất lượng, thật sự được vận dụng (sách, bài báo, ISO/IEEE, tài liệu kỹ thuật
  chính thức), đánh dấu trong danh mục TLTK. Mỗi nguồn agent đưa vào phải mở và đọc được; có URL/DOI thật.
- 100% hình và bảng được đánh số, có chú thích, được dẫn chiếu trong nội dung; một chuẩn trích dẫn nhất quán;
  0 lỗi chính tả/định dạng; tuân mẫu của Khoa/Trường; trùng lặp ≤ 20%.
- Luận văn là sản phẩm sinh viên làm chủ: agent hỗ trợ cấu trúc, rà lỗi, kiểm tra nhất quán thuật ngữ;
  không viết hộ cả chương rồi để nguyên.

## Hồ sơ nộp (Mục 8) — vị trí đề xuất

| # | Hồ sơ | Vị trí |
|---|---|---|
| 1 | Phiếu đăng ký đề tài, bảng phân công | `doc/quy-trinh/` |
| 2 | Sổ tiến độ hằng tuần | `doc/bao-cao-tien-do/` |
| 3 | Bản cam kết sản phẩm và metric (Bước 3) | `doc/cam-ket-san-pham-metric.md` |
| 4 | Link repo, quyền cho hội đồng | README |
| 5 | AI Usage Log | `logsAI/ai-usage-log.md` (mỗi repo một bản, gộp khi nộp) |
| 6 | SRS/SDD và sơ đồ | `doc/srs/`, `doc/sdd/` |
| 7 | Báo cáo phân tích tĩnh, quét secret, coding convention | `doc/quality/`, `doc/coding-convention.md` |
| 8 | Kế hoạch test, test case, báo cáo coverage, danh sách defect | `doc/test-plan.md`, CI artifacts, issue tracker |
| 9 | Cấu hình pipeline, lịch sử chạy, link môi trường | `.github/workflows/`, README |
| 10 | Báo cáo thực nghiệm người dùng | `doc/thuc-nghiem-nguoi-dung/` |
| 11 | Báo cáo trùng lặp luận văn, TLTK | `doc/luan-van/` |
| 12 | Kết quả nổi bật (TC6) | `doc/ket-qua-noi-bat/` |

Cấu trúc thư mục là đề xuất; tạo thư mục khi có nội dung thật, đừng tạo file rỗng giả vờ có hồ sơ.
