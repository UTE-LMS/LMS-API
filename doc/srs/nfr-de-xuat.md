# Yêu cầu phi chức năng định lượng — ĐỀ XUẤT

> **[ĐỀ XUẤT — chờ sinh viên và GVHD chốt]**
>
> Đây là **đề xuất của AI** (rule 05: "Số do sinh viên và GVHD chốt, agent chỉ đề xuất để chọn"). **Mọi con số dưới đây đều
> cần người chốt**: chúng là điểm xuất phát thông dụng, **không dựa trên khảo sát người dùng, đo đạc trên hệ thống này hay
> yêu cầu của Khoa**. Tài liệu **không khẳng định** hệ thống đã đạt bất kỳ NFR nào; cột "Hiện trạng" chỉ ghi điều quan sát
> được trong mã/repo hoặc "chưa đo". Khi hai sinh viên và GVHD đã chốt, ghi số chốt, người chốt và ngày vào mục 3, đổi nhãn đầu
> file, rồi đưa NFR đã chốt vào SRS chính thức.

Căn cứ yêu cầu: rule 05 (≥ 5 NFR có ràng buộc định lượng, ví dụ p95, số người dùng đồng thời, bảo mật, thời gian nhập một lô
điểm, tỷ lệ khả dụng) và rule 04 (chỉ tiêu CI/CD). Liên quan: [`doc/test-plan.md`](../test-plan.md) (tầng phi chức năng),
[`doc/sdd/05-deployment.md`](../sdd/05-deployment.md) (môi trường), [`README.md`](README.md).

## 1. Bảng tóm tắt

| ID | Nhóm | Yêu cầu (số **đề xuất**) | Đo được ngay hôm nay? |
|---|---|---|---|
| NFR-01 | Hiệu năng | p95 phản hồi API đọc ≤ **500 ms** ở tải 30 người dùng đồng thời | Một phần (đo được với API F01/F02 trên máy dev; chưa có staging) |
| NFR-02 | Sức chứa | Phục vụ ≥ **50** người dùng đồng thời mà vẫn thỏa NFR-01, lỗi < **1%** | Như trên |
| NFR-03 | Hiệu năng nghiệp vụ | Nhập và xác nhận một lô điểm: lớp **45** sinh viên ≤ **5 s**, lớp **200** sinh viên ≤ **15 s** | Không (F05b chưa có mã) |
| NFR-04 | Hiệu năng nghiệp vụ | Chạy đo lường CLO → PI → PLO cho một lớp 200 sinh viên ≤ **30 s** | Không (F06 chưa có mã) |
| NFR-05 | Khả dụng | ≥ **99%** trong khung giờ **07:00–22:00** (giờ Việt Nam) suốt giai đoạn staging/bảo vệ | Không (chưa có staging) |
| NFR-06 | Bảo mật | 0 lỗ hổng mức High/Critical chưa xử lý từ quét phụ thuộc; 0 secret trong repo; đạt **OWASP ASVS mức 1** cho các mục áp dụng | Một phần (xem quan sát ở mục 2) |
| NFR-07 | Bảo mật xác thực | 100% endpoint ngoài danh sách công khai trả 401 khi thiếu token hợp lệ; thời hạn token ≤ **60 phút**; mật khẩu băm BCrypt | Có thể kiểm bằng test (một phần đã có) |
| NFR-08 | Chất lượng kiểm thử | Coverage dòng lệnh mô-đun lõi ≥ **70%**; 100% test pass ở lần chạy CI cuối; build xanh ≥ **90%** | Chưa đo (xem `test-plan.md` §2) |
| NFR-09 | Phân phối | Commit → staging ≤ **15 phút** | Không (chưa có staging, chưa có chặng deploy) |
| NFR-10 | Truy vết | **100%** thao tác sửa điểm, xác nhận nhập, chạy tính, xác nhận quy tắc, duyệt/từ chối bài đánh giá, đăng nhập có bản ghi `audit_logs` | Một phần (chỉ `LOGIN` đã có mã) |
| NFR-11 | Phục hồi | Sao lưu CSDL hằng ngày; RPO ≤ **24 giờ**, RTO ≤ **2 giờ** | Không (chưa có kế hoạch sao lưu) |
| NFR-12 | Khả năng dùng | Tỷ lệ hoàn thành tác vụ ≥ **90%**, điểm SUS ≥ **80** với ≥ **10** người dùng thật thuộc nhóm mục tiêu | Không (cần người thật; AI không tạo kết quả) |

(Con số NFR-08, NFR-12 và phần "≥ 6 chặng pipeline" lấy từ ngưỡng Mức 5 của Rubric/rule 04, không phải ước lượng của AI; vẫn
cần hai bạn xác nhận chọn nhắm mức đó.)

## 2. Chi tiết từng NFR

### NFR-01 — Thời gian phản hồi API đọc
- **Mô tả:** các endpoint `GET` (danh sách có phân trang và chi tiết) trả kết quả đủ nhanh để giao diện thao tác mượt.
- **Số đề xuất:** p95 ≤ 500 ms, p99 ≤ 1500 ms; tải 30 người dùng đồng thời; bộ dữ liệu cỡ một khoa (số bản ghi do hai bạn chốt, ghi vào báo cáo đo).
- **Cách đo:** kịch bản k6 gọi các `GET` của F01/F02 (và F07 khi có) sau bước đăng nhập; chạy ≥ 5 phút sau 1 phút khởi động; ghi phân vị từ báo cáo của công cụ.
- **Công cụ:** k6 hoặc Apache JMeter (rule 04). Số liệu đo phải ghi kèm cấu hình máy, commit, ngày.
- **Hiện trạng:** chưa có kịch bản, chưa đo. Số 500 ms là con số tham khảo thông dụng cho API nội bộ, không phải kết quả đo của hệ thống này.

### NFR-02 — Người dùng đồng thời
- **Mô tả:** số người dùng thao tác cùng lúc mà hệ thống vẫn đạt NFR-01.
- **Số đề xuất:** ≥ 50 người dùng đồng thời, tỷ lệ lỗi HTTP 5xx < 1%. Con số này cần hai bạn hỏi Khoa/GVHD (số giảng viên, cán bộ, sinh viên xem kết quả trong cao điểm), vì AI chưa có dữ liệu thật về quy mô người dùng.
- **Cách đo:** tăng dần số người dùng ảo (ramp-up) đến mức đề xuất rồi giữ ổn định; ghi tỷ lệ lỗi và p95.
- **Công cụ:** k6/JMeter.
- **Hiện trạng:** chưa đo. Lưu ý API không dùng phiên (stateless JWT) nhưng mỗi request đọc lại bản ghi người dùng từ MongoDB (`AuthSessionServiceImpl`), nên tải kéo theo một truy vấn `users` cho mỗi request.

### NFR-03 — Thời gian nhập một lô điểm
- **Mô tả:** từ khi người dùng xác nhận một tệp điểm hợp lệ tới khi điểm được ghi (kèm kiểm tra và ghi `audit_logs`/`import_batches`) trong một transaction.
- **Số đề xuất:** lớp 45 sinh viên ≤ 5 s; lớp 200 sinh viên ≤ 15 s (số câu hỏi/tiêu chí mỗi bài do hai bạn chốt, ví dụ 20; ghi vào báo cáo đo). Cỡ lớp 45–200 là giả định do người giao việc nêu (tài liệu repo chỉ có ví dụ minh họa 45 ghi danh); cần xác nhận với Khoa.
- **Cách đo:** test tích hợp/hiệu năng với tệp giả có nhãn "DỮ LIỆU THỬ NGHIỆM", đo thời gian xử lý ở server (log hoặc `StopWatch`) trên MongoDB replica set.
- **Công cụ:** JUnit + đo thời gian, hoặc k6 gọi API tải tệp.
- **Hiện trạng:** F05b chưa có mã; không đo được. Không dùng số điểm thật.

### NFR-04 — Thời gian chạy đo lường
- **Mô tả:** thời gian chạy tính CLO → PI → PLO cho một lớp (đồng thời ghi `calculation_runs`).
- **Số đề xuất:** lớp 200 sinh viên ≤ 30 s.
- **Cách đo / công cụ:** như NFR-03; kiểm bằng dữ liệu giả có kết quả tính tay độc lập để vừa đo tốc độ vừa đo tính đúng.
- **Hiện trạng:** F06 chưa có mã.

### NFR-05 — Tỷ lệ khả dụng
- **Mô tả:** tỷ lệ thời gian hệ thống phục vụ được yêu cầu trong khung giờ làm việc.
- **Số đề xuất:** ≥ 99% trong 07:00–22:00 suốt giai đoạn staging/bảo vệ (ngoài giờ không cam kết). Cần hai bạn cân nhắc theo nền tảng sẽ chọn (gói miễn phí thường ngủ khi không dùng).
- **Cách đo:** thăm dò `GET /actuator/health` định kỳ (ví dụ mỗi 1–5 phút) và tính tỷ lệ phản hồi `UP`; endpoint này đã có và công khai.
- **Công cụ:** dịch vụ theo dõi uptime hoặc workflow GitHub Actions theo lịch (**chưa chọn**).
- **Hiện trạng:** chưa có môi trường staging (Q06/Q07), chưa có theo dõi.

### NFR-06 — Bảo mật ứng dụng và chuỗi cung ứng
- **Mô tả:** không để lỗ hổng đã biết ở phụ thuộc, không lộ secret, và các biện pháp bảo mật ứng dụng cơ bản.
- **Số đề xuất:** 0 lỗ hổng High/Critical còn tồn từ OWASP Dependency-Check (API) và `npm audit` (UI); 0 secret theo gitleaks; checklist OWASP ASVS mức 1 cho các mục áp dụng (ví dụ xác thực, quản lý phiên, kiểm tra đầu vào, xử lý lỗi, cấu hình); quét image Docker bằng Trivy khi có image.
- **Cách đo:** chạy công cụ trong CI và đính báo cáo; hoàn thành checklist thủ công có người ký.
- **Công cụ:** OWASP Dependency-Check, `npm audit`, gitleaks, Trivy, (tùy chọn) OWASP ZAP (rule 04). Kết quả ghi ở `doc/quality/README.md`.
- **Hiện trạng:** các công cụ này chưa được cài vào CI của repo API (TD-09). **Quan sát từ mã** (đọc ngày 2026-11-02, chưa phải kết quả kiểm thử bảo mật; sinh viên cần xác nhận và quyết định xử lý):
  1. `JwtConfig` và `application.properties.example` có **giá trị mặc định cố định** cho khóa JWT (`${JWT_SECRET:0123…}`, 64 ký tự công khai trong repo). Nếu triển khai mà quên đặt `JWT_SECRET`, ứng dụng vẫn khởi động với khóa này; kiểm tra fail-fast chỉ chặn khóa ngắn hơn 32 byte.
  2. `management.endpoint.health.show-details=always` kết hợp `/actuator/health/**` công khai nên chi tiết các thành phần (ví dụ trạng thái kết nối MongoDB) hiển thị cho người chưa đăng nhập; `ActuatorHealthTest` còn khẳng định `$.components` tồn tại khi không xác thực.
  3. Chưa có giới hạn tần suất đăng nhập (đã ghi là nợ kỹ thuật trong plan F01 §9).
  4. `@SuppressWarnings("unchecked")` tồn tại ở `AuditLogMapperImpl` (không phải lỗ hổng; liên quan quy ước "không tắt cảnh báo").

### NFR-07 — Bảo mật xác thực và phân quyền
- **Mô tả:** mọi tài nguyên bảo vệ yêu cầu token hợp lệ; token ngắn hạn; mật khẩu không lưu dạng đọc được; phân quyền kiểm ở backend.
- **Số đề xuất:** 100% endpoint ngoài danh sách công khai (`/api/v1/auth/login`, `/actuator/health/**`, `/actuator/info`, OPTIONS) trả 401 khi thiếu/sai token và 403 khi sai vai trò hoặc phạm vi; thời hạn token ≤ 60 phút; mật khẩu băm BCrypt, độ dài 8–72 byte UTF-8.
- **Cách đo:** test API duyệt toàn bộ endpoint (dựa danh sách mapping) với token thiếu, sai và vai trò không đủ; kiểm tra TTL trong cấu hình.
- **Công cụ:** JUnit + MockMvc.
- **Hiện trạng:** TTL mặc định `60m` và BCrypt đã có trong mã; BCrypt và các ca 401/403 đã được kiểm theo nhóm endpoint nhưng **chưa có test duyệt toàn bộ endpoint** (khoảng trống G-12 ở `test-plan.md`).

### NFR-08 — Chất lượng kiểm thử tự động
- **Mô tả:** bộ test đủ rộng cho mô-đun lõi và pipeline ổn định.
- **Số đề xuất:** coverage dòng lệnh ≥ 70% cho mô-đun lõi (đo lường, nhập điểm, kiểm tra dữ liệu, phân quyền); 100% test pass ở lần chạy CI cuối; build xanh ≥ 90% số lần chạy; không `@Disabled`, không `continue-on-error`.
- **Cách đo:** báo cáo JaCoCo công bố từ CI; thống kê lần chạy workflow bằng `gh run list`.
- **Công cụ:** JaCoCo (đã cấu hình ngưỡng 70% cho `service/impl`, `controller`, `filter` nhưng `haltOnFailure=false`), GitHub Actions.
- **Hiện trạng:** chưa có số coverage nào được công bố trong tài liệu; chưa có báo cáo ở CI. Không trích số khi chưa đọc báo cáo thật.

### NFR-09 — Thời gian từ commit đến staging
- **Mô tả:** thời gian từ commit vào `main` đến khi bản chạy trên staging.
- **Số đề xuất:** ≤ 15 phút (ngưỡng rule 04); ≥ 10 lần triển khai tự động trong kỳ, trải đều.
- **Cách đo:** hiệu thời điểm commit và thời điểm hoàn tất chặng deploy trong GitHub Actions.
- **Công cụ:** GitHub Actions; nền tảng deploy chưa chọn.
- **Hiện trạng:** chưa có chặng đóng gói/deploy (TD-09); không có số liệu và **không được bịa**.

### NFR-10 — Truy vết thao tác quan trọng
- **Mô tả:** thao tác quan trọng đều để lại dấu vết kiểm toán, không lộ dữ liệu nhạy cảm.
- **Số đề xuất:** 100% thao tác thuộc `AuditAction` (`SCORE_UPDATE`, `IMPORT_CONFIRM`, `ASSESSMENT_APPROVE`, `ASSESSMENT_REJECT`, `RUN_CALCULATION`, `RULE_CONFIRM`, `LOGIN`) có bản ghi `audit_logs` đúng `entity`, `userId`, `at`; `reason` bắt buộc với `SCORE_UPDATE`; 0 trường nhạy cảm (mật khẩu, token…) trong `before`/`after`.
- **Cách đo:** test tích hợp mỗi thao tác trên replica set; truy vấn đối chiếu số thao tác và số bản ghi.
- **Công cụ:** JUnit + MongoDB.
- **Hiện trạng:** `AuditService` có (che trường nhạy cảm, bắt buộc `reason` với `SCORE_UPDATE`), nhưng **chỉ `LOGIN` đang được gọi** từ nghiệp vụ; các hành động còn lại thuộc feature chưa có mã.

### NFR-11 — Sao lưu và phục hồi
- **Mô tả:** khả năng khôi phục dữ liệu và phiên bản ứng dụng sau sự cố.
- **Số đề xuất:** sao lưu CSDL hằng ngày; RPO ≤ 24 giờ; RTO ≤ 2 giờ; rollback ứng dụng về bản trước ≤ 15 phút (giữ image cũ).
- **Cách đo:** diễn tập khôi phục từ bản sao lưu và rollback, ghi thời gian thực tế.
- **Công cụ:** `mongodump`/`mongorestore` hoặc sao lưu của nền tảng; quy trình viết trong tài liệu vận hành (chưa có).
- **Hiện trạng:** chưa có kế hoạch sao lưu hay tài liệu rollback; phụ thuộc lựa chọn nền tảng (Q06/Q07).

### NFR-12 — Khả năng dùng
- **Mô tả:** người dùng mục tiêu hoàn thành tác vụ chính mà không cần hướng dẫn nhiều.
- **Số đề xuất:** tỷ lệ hoàn thành tác vụ ≥ 90%, điểm SUS ≥ 80, ≥ 10 người dùng thật thuộc nhóm mục tiêu (giảng viên, cán bộ phụ trách CTĐT/đảm bảo chất lượng), có ≥ 1 vòng cải tiến đo lại trước–sau (ngưỡng Mức 5 của Rubric TC2.7).
- **Cách đo:** thực nghiệm người dùng với kịch bản tác vụ và phiếu SUS; kết quả lưu ở `doc/thuc-nghiem-nguoi-dung/`.
- **Công cụ:** phiếu SUS, biên bản; do người thật thực hiện.
- **Hiện trạng:** chưa có thực nghiệm. AI không tạo người tham gia hay kết quả.

## 3. Bảng chốt (hai sinh viên và GVHD điền)

| ID | Số đề xuất | Số chốt | Người chốt | Ngày chốt | Ghi chú |
|---|---|---|---|---|---|
| NFR-01 | p95 ≤ 500 ms, 30 người dùng | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |
| NFR-02 | ≥ 50 người dùng, lỗi < 1% | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |
| NFR-03 | 45 SV ≤ 5 s; 200 SV ≤ 15 s | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |
| NFR-04 | 200 SV ≤ 30 s | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |
| NFR-05 | ≥ 99%, 07:00–22:00 | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |
| NFR-06 | 0 High/Critical, 0 secret, ASVS L1 | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |
| NFR-07 | 100% 401/403, TTL ≤ 60 phút | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |
| NFR-08 | ≥ 70%, 100% pass, xanh ≥ 90% | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |
| NFR-09 | ≤ 15 phút | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |
| NFR-10 | 100% thao tác có audit | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |
| NFR-11 | RPO ≤ 24 giờ, RTO ≤ 2 giờ | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |
| NFR-12 | hoàn thành ≥ 90%, SUS ≥ 80 | [ĐIỀN] | [ĐIỀN] | [ĐIỀN] | |

Rubric yêu cầu **≥ 5** NFR định lượng; có thể giữ 5–8 NFR quan trọng nhất và bỏ phần còn lại thay vì chốt cả 12.
Chưa có chữ ký/xác nhận của GVHD ở bất kỳ dòng nào.
