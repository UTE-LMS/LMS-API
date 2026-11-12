# ADR 0011 — PI suy ra từ kết quả CLO; câu hỏi/tiêu chí chỉ gán CLO (Q10)

- **Trạng thái:** ĐÃ CHẤP NHẬN (ACCEPTED) ngày 2026-11-06; **cập nhật bởi [ADR 0013](file:///D:/Code/Project/LMS-API/doc/adr/0013-import-ctdt-de-cuong-va-nhap-diem-clo-truc-tiep.md) ngày 2026-11-10** (`outcomeMappings[]` đổi từ `{ cloId, weight }` sang `{ cloId, maxScore }`, bỏ 3 collections ngân hàng câu hỏi).
- **Ngày:** 2026-11-06
- **Người soạn:** Antigravity theo yêu cầu của sinh viên Minh (`minhat5`); sinh viên rà soát và chốt: [ĐIỀN]
- **Thay thế:** câu trả lời tạm cũ của Q10 (`pi.method = DIRECT_FROM_ITEMS`, PI gán trực tiếp cho từng câu hỏi)
- **Liên quan:** `GiaiThich_CoSoDuLieu.md` nguyên tắc 8, 12, §4.11, §4.14, §4.15, §4.23, §8 (Q10, Q15, Q22);
  `PLO_DB_UPDATE.md` §4.2 (chế độ 2 `FROM_CLOS`); `plan/F04-bai-danh-gia.md`; `plan/F06-do-luong.md` §9 (CT-06);
  ADR 0012 (bỏ quy đổi I/R/M)

## 1. Bối cảnh

Thiết kế v0.5 cho mỗi câu hỏi/tiêu chí khai báo cặp `(cloId, piId, weight)` trong `outcomeMappings`. PI được tính
trực tiếp từ điểm câu hỏi (`DIRECT_FROM_ITEMS`), và cặp (CLO, PI) phải nằm trong `clo_pi_links`.

Hai đề cương chính thức đã nhận (`APCM220307`, `ABDA433708`) chỉ ghi **CLO → PI** ở mục chuẩn đầu ra học phần. Phần
đánh giá ghi **CLO** cho từng lần đánh giá, không ghi PI. Như vậy giảng viên chỉ có thông tin để gán CLO cho câu hỏi;
nếu bắt gán thêm PI cho từng câu thì phải tự suy ra, và tự suy là loại dữ liệu dự án cam kết không bịa
(CLAUDE.md quy tắc 2).

## 2. Quyết định

1. Câu hỏi (`questions`) và câu hỏi/tiêu chí trong bài đánh giá (`assessments.items[]`) **chỉ gán CLO**:
   `outcomeMappings[] = { cloId, weight }`. Bỏ trường `piId` khỏi `outcomeMappings`.
2. PI của sinh viên **luôn suy ra từ kết quả CLO** qua `clo_pi_links` (chế độ `FROM_CLOS` của `PLO_DB_UPDATE.md` §4.2):

   `Value(PI_s,m) = Σ_j [Value(CLO_s,j) × W_j→m] / Σ_j W_j→m` với `j` là các CLO có liên kết tới `PI_m`,
   `W_j→m = clo_pi_links.weight`. (Không còn hệ số `IRM_Factor`, xem ADR 0012.)
3. `measurement_rules.pi.method` chỉ còn `FROM_CLOS`. Bỏ `DIRECT_FROM_ITEMS`.
4. Nguồn ánh xạ CLO–PI: mục chuẩn đầu ra của 2 đề cương chính thức (Q14).

## 3. Phương án đã cân nhắc

| Phương án | Lý do không chọn / chọn |
|---|---|
| Giữ `DIRECT_FROM_ITEMS` (PI gán từng câu) | Đề cương không có dữ liệu PI cho từng câu; giảng viên phải tự suy |
| Cho cả hai chế độ | Hai chế độ cần hai bộ kiểm thử và hai cách giải thích khi vấn đáp, trong khi chỉ có dữ liệu cho một chế độ |
| **Chỉ `FROM_CLOS`** | Khớp đề cương; chuỗi Câu hỏi → CLO → PI → PLO đúng mô tả nghiệp vụ trong `CLAUDE.md` |

## 4. Hệ quả

- **Chưa tính được PI chính thức:** đề cương không ghi trọng số `W` của CLO → PI. Ví dụ CLO1 của `APCM220307` liên kết
  PI1.2 và PI2.3; ở chiều PI thì một PI có thể nhận nhiều CLO. Cách xác định `W` (hoặc có coi các CLO ngang nhau không)
  thuộc Q22/CT-06, **chưa có câu trả lời**. Hệ thống để `clo_pi_links.weight = null` và **không tự đặt trọng số bằng
  nhau**; engine báo PI "chưa đủ quy tắc" thay vì tính.
- **Q15 vẫn mở**, nhưng đổi nội dung: "một câu đo nhiều **CLO** thì chia điểm thế nào". Thiết kế giữ `weight` (tổng
  bằng 1) như hiện tại cho tới khi có câu trả lời.
- **Kiểm tra dữ liệu:** `noDuplicatePairs` thành "mỗi CLO chỉ xuất hiện một lần trong một câu". Bỏ chỉ mục
  `outcomeMappings.piId`, `items.outcomeMappings.piId`.
- **Nguyên tắc 12 của CSDL** ("PI gắn cho câu hỏi chỉ được chọn trong ánh xạ CLO–PI đã duyệt") không còn áp dụng vì câu
  hỏi không gắn PI. Thay bằng: "CLO gắn cho câu hỏi phải thuộc đề cương của bài; chạy tính chính thức yêu cầu
  `clo_pi_links` của các CLO đó đã `VERIFIED`".
- **Mã:** `ProgramReferenceRepositoryImpl.existsPiReference` bỏ kiểm tra `questions.outcomeMappings.piId` và
  `assessments.items.outcomeMappings.piId` (hai trường không còn tồn tại). PI vẫn bị chặn xóa khi còn `clo_pi_links`.
  Chưa có mã F04/F06 nên không phải sửa thêm.
- **Báo cáo truy vết (F07):** đường truy vết PI → CLO → câu hỏi → điểm vẫn đầy đủ, chỉ thêm một tầng CLO.

## 5. Tài liệu cập nhật cùng thay đổi

`database_import.js`, `GiaiThich_CoSoDuLieu.md`, `tom-tat-du-an-de-thao-luan.md` §9, `chot-feature-do-an.md`,
`ke-hoach-xay-dung.md`, `plan/F04-bai-danh-gia.md`, `plan/F06-do-luong.md`, `.claude/rules/02-domain-invariants.md`.
`PLO_DB_UPDATE.md` là tài liệu đầu vào, giữ nguyên.
