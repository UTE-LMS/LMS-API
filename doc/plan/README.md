# Kế hoạch theo feature (bản cho người và LLM đọc)

> **Trạng thái: DỰ THẢO, chưa ký.** Tách từ `doc/ke-hoach-xay-dung.md` (v0.3) và `doc/chot-feature-do-an.md`, đối chiếu
> với thiết kế CSDL v0.5. Mục **[ĐỀ XUẤT]** là ý của người soạn; **[CHỜ GVHD]** / **[CHỜ KHOA]** là chưa được duyệt.
> Acceptance criteria (AC) trong các file này là **bản nháp để IBS/IT chỉnh trước khi code**, chưa phải SRS chính thức.
> Ngày soạn: 2026-10-27.

Mỗi file là **một feature, tự đủ để giao cho một LLM** (kèm file này làm ngữ cảnh chung). Đừng bắt LLM đọc cả
`ke-hoach-xay-dung.md` (35 KB) khi chỉ làm một feature.

## 1. Danh sách file

| File | Feature | Story | Package (`com.hcmute.lms.lmsapi.…`) | Giai đoạn |
|---|---|---|---|---|
| [00-nen-tang-chung.md](00-nen-tang-chung.md) | Nền tảng dùng chung (lỗi, audit, transaction, kiểm thử, health) | US-39, US-40 | `common`, `audit` | P0 |
| [F01-nguoi-dung-phan-quyen.md](F01-nguoi-dung-phan-quyen.md) | Người dùng và phân quyền | US-01…04, NEW-03 | `auth` | P2 |
| [F02-ctdt-plo-pi.md](F02-ctdt-plo-pi.md) | CTĐT, PLO, PI, ma trận I/R/M | US-05…08 | `program` | P2 |
| [F03-hoc-phan-clo.md](F03-hoc-phan-clo.md) | Học phần, đề cương, CLO, liên kết CLO–PI | US-09…12 | `course` | P2 |
| [F04-bai-danh-gia.md](F04-bai-danh-gia.md) | Bài đánh giá, rubric, duyệt ma trận, ngân hàng câu hỏi | US-13…17, NEW-01 | `assessment` | P3 |
| [F05a-hoc-ky-lop-sinh-vien.md](F05a-hoc-ky-lop-sinh-vien.md) | Học kỳ, lớp học phần, sinh viên, ghi danh | US-18…20 | `academic` | P3 |
| [F05b-nhap-diem.md](F05b-nhap-diem.md) | Nhập điểm từ tệp | US-21…25 | `scoreimport` | P3 |
| [F06-do-luong.md](F06-do-luong.md) | Quy tắc và engine đo lường CLO → PI → PLO | US-26…30, NEW-04 | `measurement` | P4 |
| [F07-bao-cao-truy-vet.md](F07-bao-cao-truy-vet.md) | Báo cáo, truy vết, xuất tệp, dashboard | US-31…36 | `report` | P5 |
| [F08-cai-tien.md](F08-cai-tien.md) | Hành động cải tiến (đóng vòng) | US-37, 38, NEW-02 | `improvement` | P5 |

Thứ tự phụ thuộc (mũi tên = phải xong trước):

```
00 nền tảng → F01 → F02 → F03 → F04 ─┐
                       F05a ───────────┼→ F05b → F06 → F07 → F08
                                        └──────────┘
```

F06 cần dữ liệu F02–F05b; F07 chỉ **đọc** kết quả F06; F08 gắn vào kết quả/báo cáo của F06–F07.

## 2. Ưu tiên và phạm vi cam kết (đề xuất, chưa ký)

Ký hiệu: **C** = cam kết, **MR** = mục tiêu mở rộng, **K** = không làm (theo `chot-feature-do-an.md`). Cột "Kế hoạch v0.3"
là mức Must/Should/Could của `ke-hoach-xay-dung.md`; hai nguồn lệch ở US-04, 13, 14 (nguồn nào chốt do hai bạn và GVHD).

| Story | Kế hoạch v0.3 | Đề xuất chốt | Story | Kế hoạch v0.3 | Đề xuất chốt |
|---|---|---|---|---|---|
| US-01…03 | M | C | US-23 | M | C |
| US-04 | S | MR (ghi audit vẫn C) | US-24 | M | C |
| US-05…07 | M | C | US-25 | S | MR |
| US-08 | S | MR | US-26…29 | M | C |
| US-09…12 | M | C | US-30 | S | MR |
| US-13 | S | MR | US-31…34 | M | C |
| US-14 | M | MR | US-35, 36 | S | MR |
| US-15, 16 | M | C | US-37, 38 | M | C |
| US-17 | C | K | US-39, 40 | M | C |
| NEW-01 | M | C **[CHỜ GVHD, Q11]** | NEW-02 | S | MR |
| US-18…20 | M | C | NEW-03, 04 | S, C | MR |
| US-21, 22 | M | C | | | |

## 3. Nguồn sự thật và thứ tự ưu tiên khi tài liệu lệch nhau

1. `doc/database_import.js`: validator `$jsonSchema`, enum, chỉ mục **thực tế** của 30 collection. Đây là hợp đồng dữ liệu.
2. `doc/GiaiThich_CoSoDuLieu.md` (v0.5): ý nghĩa nghiệp vụ, 14 nguyên tắc (§2), từng collection (§4), câu hỏi Q01–Q23 (§8).
3. `doc/chot-feature-do-an.md` rồi `doc/ke-hoach-xay-dung.md`: phạm vi và ưu tiên.
4. `doc/PLO_DB_UPDATE.md`: đặc tả nguồn của v0.5. **Chỉ lấy nội dung dữ liệu và công thức (§3, §4)**; dòng đầu của file ghi
   "Spring Boot 3.x – Angular 17+" **không áp dụng**: dự án dùng **Spring Boot 4.1.0, Java 25, Angular 21**.
5. `CLAUDE.md` và `.claude/rules/*.md`: quy tắc làm việc (không hardcode ngưỡng, ghi AI log, git…).

Lệch nhau mà không tự giải quyết được thì **dừng và hỏi**, đừng chọn rồi viết như thể đã được duyệt.

## 4. Bối cảnh kỹ thuật chung (mọi feature)

- Backend: Java 25, Spring Boot 4.1.0, Spring Data MongoDB, Spring Security, Bean Validation, Lombok (đã có trong
  `pom.xml`). Chưa có: actuator, thư viện đọc Excel/CSV, JWT, Testcontainers. Thêm dependency nào phải có lý do + giấy phép + ADR.
  **Spring Boot 4 khác 3.x:** kết nối Mongo dùng `spring.mongodb.uri` (không phải `spring.data.mongodb.uri`). Tra tài
  liệu chính thức đúng phiên bản trước khi dùng API, đừng đoán theo trí nhớ.
- MongoDB 8, **replica set** (cần transaction). Dev: image `docker/mongo` (xem `README.md` gốc), chuỗi kết nối mặc định
  `mongodb://localhost:27017/learning_outcomes?replicaSet=rs0`.
- Frontend (repo `LMS-UI`, Angular 21) **chỉ gọi API**; công thức đo lường chỉ chạy ở backend.
- Cấu trúc package mỗi feature: `controller → service → repository`, cùng `dto`, `mapper`, `document`. Controller chỉ
  nhận/trả DTO, **không trả `document`**, không chứa logic. Chi tiết: `.claude/rules/03-code-quality-and-git.md`.
- Tên collection `snake_case`; `_id` là `ObjectId`; thời điểm là `Date`.

## 5. Bất biến nghiệp vụ (vi phạm là lỗi nghiệp vụ, mọi feature)

1. **Không hardcode** công thức, trọng số, ngưỡng, mẫu số: đọc từ `measurement_rules`. Các số 40/60, 60%, 72%, 20/40/40,
   45/40 trong tài liệu chỉ là **ví dụ minh họa**; dùng trong test thì đặt tên fixture "ví dụ minh họa", không đưa vào seed/cấu hình.
2. **Thiếu dữ liệu ≠ 0.** Chỉ `BLANK` tính 0; `ABSENT`, `NOT_GRADED`, `EXEMPT`, `MISSING` có `rawScore = null`.
3. **Bloom ≠ điểm. I/R/M ≠ trọng số** (engine luôn coi `pi.useIrmAsWeight = false`). Mức rubric (1–4) ≠ Bloom.
   `capabilityLevelRaw` lưu nguyên, không diễn giải.
4. **Tỷ lệ đạt luôn kèm mẫu số**: `counts` = ghi danh, đủ dữ liệu, đạt, chưa đạt, chưa đủ dữ liệu; luôn có `passRateOfEnrolled`.
5. **CLO nhận diện bằng `_id`** (mã `CLO1` chỉ duy nhất trong một đề cương). **PLO/PI nhận diện bằng mã**, không bằng thứ tự dòng.
6. Một câu hỏi/tiêu chí đo nhiều cặp (CLO, PI): `weight` các cặp **tổng bằng 1**; cặp phải nằm trong `clo_pi_links` của chính CLO đó.
7. **MongoDB không có khóa ngoại:** service tự kiểm tra tham chiếu trước khi ghi.
8. Mọi kết quả gắn `programVersionId`, `syllabusId`, `ruleId`; truy vết được về `scores` → `import_batches` + số dòng tệp gốc.
9. Sửa điểm, xác nhận nhập, duyệt/từ chối bài đánh giá, chạy tính, xác nhận quy tắc, đăng nhập đều ghi `audit_logs`.
10. `contentOriginal` của PLO/PI/CLO để trống tới khi đối chiếu bản gốc; **không điền nguyên văn từ trí nhớ**.
11. Dùng `studentRef` (mã thay thế). Test/seed chỉ dùng dữ liệu giả có nhãn "DỮ LIỆU THỬ NGHIỆM". Không lưu bài làm của sinh viên.
12. **Ngoài phạm vi:** tổ chức thi, sinh đề, duyệt *nội dung* đề, xuất phôi in, lưu bài làm, **mọi thứ ML**, quản lý "minh chứng"
    như danh mục dữ liệu, quy đổi I/R/M thành hệ số.

## 6. Cách dùng với LLM hỗ trợ code

Giao **một feature mỗi lần**, theo thứ tự phụ thuộc. Gửi kèm: file này, `00-nen-tang-chung.md`, file feature, và các mục
`GiaiThich_CoSoDuLieu.md` / vùng `database_import.js` mà file feature chỉ ra ở "Đọc trước". Prompt mẫu:

```
Bạn đang làm feature <Fxx> của dự án LMS-API (Spring Boot 4.1, Java 25, MongoDB). Đọc doc/plan/README.md,
doc/plan/00-nen-tang-chung.md, doc/plan/<file>.md và các mục "Đọc trước". Làm story <US-xx> theo đúng AC trong file.
Quy tắc: không hardcode ngưỡng/trọng số; không thêm dependency khi chưa nêu lý do; không đụng ngoài phạm vi của file;
validator trong database_import.js là hợp đồng dữ liệu; tra tài liệu chính thức cho API Spring Boot 4 / Spring Data MongoDB.
Viết test cùng lúc (ca thuận, ca âm, ca biên) với tên @DisplayName("US-xx/AC-y: …"). Chạy ./mvnw verify và báo kết quả
đúng như output. Cuối cùng viết mục "Để vấn đáp" (làm gì, vì sao có trong đề tài, tham số/thư viện từ đâu, bỏ đi thì sao).
Việc chưa rõ hoặc thuộc mục [CHỜ …]: hỏi tôi, đừng tự chọn.
```

Sau mỗi phiên: ghi `logsAI/ai-usage-log.md` (sinh viên yêu cầu, file sinh/sửa, kiểm chứng, lỗi AI phát hiện). Người còn
lại review PR (IBS soát nghiệp vụ và AC, IT soát kỹ thuật). Không commit/push khi chưa được yêu cầu.

## 7. Definition of Done cho một story

1. AC viết xong trước khi code và người còn lại đã đọc (sửa file feature nếu AC đổi).
2. Mã qua lint/phân tích tĩnh; test gắn AC, có ca âm và ca biên; `./mvnw verify` xanh.
3. Tài liệu/sơ đồ bị ảnh hưởng cập nhật cùng PR; ADR nếu đổi thiết kế.
4. Không secret, không dữ liệu thật; chạy được trên staging.
5. AI Usage Log đã ghi; người viết **và** người review giải thích được từng đoạn lõi.

## 8. Ghi chú bảo trì

Khi một feature đổi (AC, API, quy tắc), sửa **file feature tương ứng** và dòng ở bảng mục 1–2; nếu đổi phạm vi thì ghi ADR
và cập nhật `ke-hoach-xay-dung.md`, `chot-feature-do-an.md`, `cam-ket-san-pham-metric.md`. Thư mục này là bản chia nhỏ
của kế hoạch, **không thay thế** các file đó.
