# Rule 04 — Kiểm thử và CI/CD

Căn cứ: TC2.5 và TC2.6 (mỗi tiêu chí 5 điểm), G5, G6.

## Mục tiêu (ngưỡng Mức 5)

| Chỉ số | Mục tiêu |
|---|---|
| Số tầng kiểm thử | ≥ 4 (unit, integration, API, E2E/UI, phi chức năng) |
| Coverage dòng lệnh mô-đun lõi (đo lường, nhập điểm, kiểm tra dữ liệu, phân quyền) | ≥ 70%, có báo cáo |
| Tỷ lệ test case tự động hóa | ≥ 70% |
| Test pass ở lần chạy CI cuối | 100% |
| Defect Critical/Blocker còn tồn khi bảo vệ | 0 |
| Chặng pipeline | ≥ 6 |
| Build xanh | ≥ 90% |
| Triển khai tự động trong kỳ | ≥ 10 lần, trải đều |
| Commit → staging | ≤ 15 phút |

## Kiểm thử

- **Tầng:** unit (JUnit 5, Mockito; Vitest ở UI); integration (Spring Boot Test + Testcontainers MongoDB
  chạy replica set, vì cần transaction); API (MockMvc hoặc RestAssured: quyền, validation, mã lỗi); E2E/UI
  (Playwright); phi chức năng (hiệu năng bằng k6/JMeter theo NFR định lượng, kiểm tra bảo mật cơ bản).
- **Truy vết:** mỗi test gắn một AC (`@DisplayName("US-12/AC-3: …")` hoặc tương đương). Tài liệu có bảng
  AC ↔ test. AC không có test là khoảng trống phải báo.
- **Ca âm và ca biên là bắt buộc**, không chỉ luồng thuận lợi. Tối thiểu cho miền này: điểm > điểm tối đa;
  `BLANK` khác `MISSING`/`ABSENT` (không ngầm bằng 0); trùng bản ghi `(sectionId, assessmentId, itemId,
  studentId)`; tổng `weight` ≠ 1; cặp CLO–PI không có trong `clo_pi_links`; tệp trống/sai cột/nhập trùng
  checksum; quy tắc `DRAFT` không cho chạy chính thức; lớp 45 ghi danh chỉ 40 đủ dữ liệu → báo cả 5 người
  chưa đủ; người dùng ngoài phạm vi lớp bị từ chối; sửa điểm đồng thời báo xung đột.
- **Giá trị kỳ vọng tính tay độc lập**, ghi chú cách tính. Không lấy kết quả của chính mã làm giá trị kỳ
  vọng. Ví dụ minh họa 60%×0,4 + 80%×0,6 = 72% trong tài liệu dùng được, nhưng dán nhãn "ví dụ minh họa".
- Test không phụ thuộc thứ tự chạy, thời gian thực, mạng ngoài. Dữ liệu giả có nhãn, không dùng dữ liệu thật.
- **Cấm:** xóa/skip/`@Disabled` test đang đỏ, nới assertion cho qua, mock chính đối tượng đang kiểm,
  assertion luôn đúng, hạ ngưỡng coverage, sửa test cho khớp mã sai. Test đỏ → tìm nguyên nhân gốc. Nếu
  test sai thật, nói rõ vì sao sai và sửa kèm lý do.
- Khi báo kết quả: nêu lệnh đã chạy, số test pass/fail/skip, coverage đo được. Không có số thì nói không có.
- **Quản lý lỗi:** mỗi lỗi là một issue (nhãn mức độ Critical/Major/Minor, bước tái hiện, trạng thái);
  mỗi lỗi sửa kèm một regression test và commit tham chiếu issue. Agent không tự đóng issue Critical
  nếu chưa có test chứng minh.
- Kế hoạch kiểm thử (`doc/test-plan.md`) và danh sách test case phải có (Rubric mục 8 #8). Không có kiểm thử
  tự động lẫn tài liệu kiểm thử thủ công là G5 (TC2.5 ≤ 1,75).

## CI/CD

- Pipeline (GitHub Actions hoặc nền tảng do Trung tâm CNPM chỉ định), thứ tự các chặng:
  1. build; 2. lint + phân tích tĩnh (Sonar/SpotBugs/Checkstyle; ESLint); 3. test + cổng coverage;
  4. quét bảo mật và phụ thuộc (gitleaks, OWASP Dependency-Check / `npm audit`, Trivy cho image);
  5. đóng gói Docker, gắn phiên bản (tag theo commit/semver); 6. triển khai tự động tới staging.
- Sau triển khai: health check (Spring Actuator `/actuator/health`), log tập trung, cảnh báo khi lỗi.
  Có cách rollback về phiên bản trước (giữ image cũ, lệnh/quy trình rollback có tài liệu).
- Secret qua cơ chế của nền tảng (GitHub Secrets/…), không nằm trong workflow hay Dockerfile.
- **Không làm giả "xanh":** không `continue-on-error`, không `|| true`, không bỏ chặng khi lỗi, không giảm
  ngưỡng cổng chất lượng. Pipeline đỏ thì sửa nguyên nhân.
- Chạy `./mvnw verify` (API) hoặc `npm test`/`ng build` (UI) cục bộ trước khi đề nghị đẩy mã, để giữ tỷ lệ
  build xanh ≥ 90%. Không báo trạng thái CI nếu chưa xem được lần chạy thực. Không bịa số lần deploy hay
  thời gian pipeline.
- Môi trường staging công khai và (tốt nhất) production có người dùng thật phục vụ TC2.2 Mức 5, TC3 (demo
  trực tiếp trên môi trường thật). Pipeline chưa chạy được tại thời điểm bảo vệ là G6 (TC2.6 ≤ 2,75).
