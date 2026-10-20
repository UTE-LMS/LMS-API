# Bảng theo dõi minh chứng theo Rubric

Cột "Trạng thái": `Chưa có` / `Đang làm` / `Có minh chứng`. Chỉ chuyển sang `Có minh chứng` khi file/số liệu
thật đã tồn tại và có đường dẫn. Thiếu minh chứng thì tiêu chí không được xếp từ Mức 4 trở lên (Nguyên tắc 3).
Cập nhật bảng này khi có thay đổi; không điền trạng thái theo kỳ vọng.

## Điều kiện dự bảo vệ (Mục 2, G1)

| Việc | Hạn | Minh chứng | Trạng thái |
|---|---|---|---|
| Phiếu đăng ký đề tài (GV và SV), bảng phân công | Bước 1–2 | `doc/quy-trinh/` | Đang làm (có dự thảo `phan-cong.md`; chưa có phiếu đăng ký) |
| Báo cáo tiến độ ≥ 1 lần/tuần; GVHD duyệt 2 tuần/lần | Hằng tuần | `doc/bao-cao-tien-do/` | Chưa có |
| **Bản cam kết sản phẩm và metric, có chữ ký** | **Tuần 7–10 (trước mốc 50%)** | `doc/cam-ket-san-pham-metric.md` | Đang làm (có dự thảo, **chưa điền, chưa ký**) |
| Kết luận rà soát của bộ môn | Sau mốc 50% | _do bộ môn cấp_ | Chưa có |

## Tiêu chí và ngưỡng Mức 5

| TC | Điểm | Mục tiêu Mức 5 | Minh chứng cần có | Trạng thái |
|---|---|---|---|---|
| TC1 | 10 | ≥ 3 bên liên quan hoặc ≥ 30 phiếu; so sánh ≥ 3 giải pháp; chốt ≥ 5 KPI nghiệp vụ định lượng | Biên bản/phiếu khảo sát, bảng so sánh, mục KPI trong bản cam kết | Chưa có |
| TC2.1 | 10 | 100% US có AC; ≥ 4 loại sơ đồ khớp 100% mã; ≥ 5 NFR định lượng; ADR cho mọi thay đổi thiết kế | SRS/SDD, sơ đồ, `doc/adr/` | Chưa có |
| TC2.2 | 10 | 100% chức năng cam kết chạy ổn; ≥ 100% khối lượng; chạy production/staging công khai có người dùng thật; demo 0 lỗi blocker; xử lý ngoại lệ, phân quyền, log, trạng thái biên; responsive | Biên bản UAT/demo, URL môi trường | Chưa có |
| TC2.3 | 5 | Log đầy đủ, đối chiếu commit; giải thích đúng 100%; ≥ 5 lỗi AI kèm nguyên nhân + commit sửa; quy trình kiểm soát | `logsAI/ai-usage-log.md`, `git log` | Đang làm (đã có mẫu, mục A có 1 dòng, mục B trống) |
| TC2.4 | 10 | Lint 0; Blocker/Critical 0; trùng lặp ≤ 3%; ≥ 90% tuần có commit; ≥ 90% thay đổi qua PR có review; 0 secret; README, hướng dẫn cài đặt, sơ đồ triển khai; danh mục nợ kỹ thuật | Báo cáo Sonar/linter, báo cáo gitleaks, lịch sử PR, README, `doc/tech-debt.md`, `doc/coding-convention.md` | Đang làm (có README hai repo, convention dự thảo, danh mục nợ kỹ thuật, ESLint UI sạch; **chưa** có Sonar/Checkstyle bên API, quét secret, PR có review, lịch sử commit) |
| TC2.5 | 5 | ≥ 4 tầng test; coverage lõi ≥ 70%; tự động hóa ≥ 70%; 100% pass ở CI cuối; 0 defect Critical/Blocker; bug tracker; test truy vết AC, có ca âm/biên | `doc/test-plan.md`, báo cáo JaCoCo, issue tracker, log CI | Chưa có |
| TC2.6 | 5 | ≥ 6 chặng; build xanh ≥ 90%; ≥ 10 deploy tự động; commit→staging ≤ 15 phút; secret qua nền tảng; versioning + rollback; health check, log, alert | `.github/workflows/`, lịch sử run/deploy, URL | Đang làm (có khung CI build/lint/test ở hai repo, **chưa chạy trên GitHub**, chưa có deploy) |
| TC2.7 | 5 | ≥ 10 người dùng thật; hoàn thành tác vụ ≥ 90%; SUS ≥ 80; ≥ 1 vòng cải tiến đo lại trước–sau | `doc/thuc-nghiem-nguoi-dung/` | Chưa có |
| TC3 | 10 | Sai lệch thời gian ≤ 5%; demo trực tiếp trên môi trường thật, không lỗi; có minh chứng CI/CD, coverage, thực nghiệm | Slide, môi trường demo | Chưa có |
| TC4 | 10 | Mạch chặt chẽ; ≥ 5 TLTK ngoại ngữ; trùng lặp ≤ 20%; 0 lỗi định dạng; 100% hình/bảng đánh số và dẫn chiếu | Luận văn, báo cáo trùng lặp | Chưa có |
| TC5 | 15 | Trả lời đúng ≥ 90%; ≥ 95% câu kỹ thuật; giải thích được phần AI hỗ trợ | _Chuẩn bị bằng các mục "Để vấn đáp"_ | — |
| TC6 | 5 | Kết quả nổi bật có minh chứng (bài báo, giải thưởng, xác nhận đơn vị dùng…) | `doc/ket-qua-noi-bat/` | Chưa có |

## Quy tắc chặn điểm cần tránh

| Mã | Điều kiện kích hoạt | Phòng tránh |
|---|---|---|
| G1 | Không có cam kết Bước 3 trước mốc 50% | Chốt sớm, có chữ ký |
| G2 | Báo cáo tiến độ < 0,5 lần/tuần | Báo cáo hằng tuần |
| G3 / G4 | Không có log/cam kết AI; log mâu thuẫn Git hoặc vấn đáp | Ghi log mỗi phiên, trung thực |
| G5 | Không có test tự động và không có tài liệu test | Test cùng lúc với mã |
| G6 | Không có CI/CD chạy được khi bảo vệ | Dựng pipeline sớm |
| G7 | Không có thực nghiệm người dùng thật | Lên lịch thực nghiệm sớm |
| G8 | Repo không cấp quyền hoặc không build được | Kiểm tra clone sạch |
| G9 | Sản phẩm không tương xứng khối lượng đã đăng ký | Bám bản cam kết |
| G10 / V1–V4 | Không giải thích được nội dung; sao chép; làm giả minh chứng | Rule 01 và 05 |
