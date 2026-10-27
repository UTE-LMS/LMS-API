# Chốt danh sách feature của đồ án LOMS

> **Trạng thái: ĐỀ XUẤT CHỐT, chưa ký.** File này là bản quyết định phạm vi tính năng, soạn từ
> `tom-tat-du-an-de-thao-luan.md` (2026-10-21) và thiết kế CSDL v0.5 (`GiaiThich_CoSoDuLieu.md`, cập nhật theo
> `PLO_DB_UPDATE.md`). Nó trả lời các câu hỏi phạm vi ở mục 12 của bản tóm tắt. Nó **không thay** bản cam kết
> `cam-ket-san-pham-metric.md` (cổng K1, cần chữ ký GVHD): sau khi hai bạn đồng ý, chép cột "Quyết định" ở mục 3
> sang mục 4 của bản cam kết. Mục nào ghi **[CHỜ GVHD]** hoặc **[CHỜ KHOA]** thì chưa được coi là đã duyệt.
>
> Ngày soạn: 2026-10-23 (tuần 5 của kế hoạch 19 tuần; đóng băng tính năng cuối T15).

Quy ước: **C** = cam kết (phải chạy được khi bảo vệ), **MR** = mục tiêu mở rộng (làm nếu còn thời gian, không
tính vào cam kết), **K** = không làm.

---

## 1. Tóm tắt quyết định

| Hạng mục | Số lượng | Ghi chú |
|---|---|---|
| Story cam kết (C) | **32** | 31 story trong backlog gốc + 1 story mới do CSDL v0.5 (NEW-01) |
| Mục tiêu mở rộng (MR) | 11 | Gồm 8 story gốc và 3 story mới (NEW-02 đến NEW-04) |
| Không làm (K) | 8 | Mục 4 |
| Collection dùng | 29 | `ml_experiments` có trong CSDL nhưng không dùng |

Khối lượng 32 story cho hai người trong khoảng 10 tuần lập trình là **nặng**. Thay vì cắt ngay, mục 5 đặt thứ tự cắt
theo từng cổng để quyết định có căn cứ. Nếu hai bạn muốn cam kết nhẹ hơn trước khi ký, hạ US-33 và US-38 xuống MR
(ít ảnh hưởng nhất tới luồng chính).

---

## 2. Luồng nghiệp vụ cam kết (tiêu chí nghiệm thu)

```
Khai báo CTĐT, PLO/PI, học phần, CLO, liên kết CLO–PI
  → Lập bài đánh giá bản lớp (items kèm cặp CLO–PI, weight)
  → Trưởng bộ môn duyệt ma trận (APPROVED) → mở cổng nhập điểm
  → Giảng viên tải tệp điểm → kiểm tra, báo lỗi dòng/cột → xác nhận (transaction) hoặc rollback
  → Chạy đo lường theo quy tắc cấu hình → CLO → PI → PLO (student_results)
  → Tổng hợp lớp có mẫu số (aggregate_results)
  → Báo cáo (có limitations) → truy vết ngược về dòng tệp gốc
  → Ghi nhận vấn đề và hành động cải tiến
```

Demo nghiệm thu đi đúng luồng này trên môi trường đã triển khai, dùng dữ liệu thử nghiệm có nhãn
"DỮ LIỆU THỬ NGHIỆM" cho tới khi Khoa cấp dữ liệu thật.

---

## 3. Danh sách story đã chốt

Cột "Đề xuất" phân biệt story gốc (giữ nguyên ưu tiên) với thay đổi so với kế hoạch gốc (có lý do ở cột cuối).

### E01 Người dùng và quyền (F01)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-01 | Đăng nhập / đăng xuất | **C** | |
| US-02 | Tạo người dùng, gán vai trò và phạm vi | **C** | 6 vai trò dùng trong cam kết: ADMIN, PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER, LECTURER, VIEWER |
| US-03 | Backend kiểm tra quyền theo phạm vi mọi API | **C** | Giảng viên chỉ thấy và nhập điểm lớp mình; trái phạm vi trả 403 |
| US-04 | Màn hình xem nhật ký thao tác | MR | **Việc ghi `audit_logs` vẫn thuộc cam kết**, nằm trong AC của US-24, US-26, US-27, NEW-01 (sửa điểm, xác nhận nhập, chạy tính, xác nhận quy tắc, duyệt đề, đăng nhập) |

### E02 CTĐT, PLO, PI (F02)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-05 | Chương trình và phiên bản CTĐT | **C** | `(programId, version)` duy nhất; có `totalCredits`, `durationYears` |
| US-06 | 7 PLO, 17 PI theo mã | **C** | Nhận diện bằng mã; `category`, `displayOrder`; không tự điền nguyên văn |
| US-07 | Ma trận học phần đóng góp PI (I/R/M) | **C** | I/R/M chỉ là thiết kế sư phạm, không là trọng số |
| US-08 | Quy trình xác nhận nội dung | MR | Trạng thái `verification` vẫn có trong dữ liệu; chỉ làm màn hình luồng xác nhận nếu còn thời gian |

### E03 Học phần, CLO (F03)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-09 | Học phần và vị trí trong CTĐT | **C** | `type` là `COMPULSORY`/`ELECTIVE` |
| US-10 | Đề cương theo phiên bản | **C** | |
| US-11 | CLO thuộc một đề cương | **C** | `code` duy nhất trong một đề cương; Bloom 1–6 |
| US-12 | Liên kết CLO–PI và xác nhận | **C** | Chỉ liên kết `VERIFIED` được dùng để tính chính thức |

### E04 Bài đánh giá (F04)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-13 | Ngân hàng câu hỏi và cây danh mục 3 cấp | MR | **Đổi từ Should**: xem lý do ở US-14 |
| US-14 | Câu hỏi kèm ánh xạ nhiều cặp trong ngân hàng | MR | **Đổi từ Must.** Câu hỏi/tiêu chí khai báo trực tiếp trong `assessments.items[]` (US-15) đã đủ để đo CLO/PI, nên ngân hàng chỉ là tiện ích tái sử dụng. Bỏ khỏi cam kết để giảm khối lượng; CSDL vẫn giữ nguyên |
| US-15 | Bài đánh giá khung (theo đề cương) và bản lớp | **C** | `items[]` có `outcomeMappings`; tổng `weight` mỗi câu bằng 1; cặp (CLO, PI) phải thuộc `clo_pi_links` |
| US-16 | Tiêu chí rubric, mức rubric 1–4 | **C** | Mức rubric khác mức Bloom |
| US-17 | Nhập câu hỏi từ Excel/Moodle | K | |
| **NEW-01** | **Duyệt ma trận đề thi:** giảng viên gửi duyệt, Trưởng bộ môn duyệt hoặc từ chối, khóa `items` sau khi duyệt, kèm bảng độ phủ theo CLO và theo Bloom (`coverageSummary`) | **C** | **Story mới từ CSDL v0.5.** Điều kiện: chỉ nhập điểm khi `APPROVED`/`SCORE_ENTERED`. **[CHỜ GVHD]** vì chạm vào phạm vi "không duyệt đề" (Q11). Phương án nếu GVHD không đồng ý: bỏ bước Trưởng bộ môn, giảng viên tự xác nhận ma trận (DRAFT → APPROVED), giữ nguyên khóa `items` |

### E05 Học kỳ, lớp, sinh viên

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-18 | Học kỳ và khóa sổ điểm | **C** | Học kỳ `SCORE_LOCKED` thì không nhập/sửa điểm |
| US-19 | Lớp học phần, giảng viên phụ trách | **C** | |
| US-20 | Nhập danh sách lớp → sinh viên, ghi danh | **C** | Ghi danh là mẫu số; dùng `studentRef` (mã thay thế) |

### E06 Nhập điểm (F05)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-21 | Tải tệp (CSV/Excel) và ánh xạ cột | **C** | |
| US-22 | Kiểm tra, báo lỗi theo dòng/cột | **C** | 4 loại lỗi: mã sinh viên sai, điểm vượt thang, trùng bản ghi, thiếu trường |
| US-23 | Chọn BLANK/MISSING cho ô trống; chính sách nhập lại | **C** | Chính sách nhập lại cam kết: `REJECT_ON_CONFLICT`. `OVERWRITE`, `UPDATE_BLANK_ONLY` là MR |
| US-24 | Xác nhận nhập trong một transaction hoặc rollback | **C** | Cần MongoDB replica set. Chỉ nhập được khi bài ở `APPROVED`/`SCORE_ENTERED` |
| US-25 | Sửa điểm đơn lẻ có lý do | MR | Nếu làm: dùng `scores.revision`, ghi audit trước/sau |

### E07 Đo lường (F06)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-26 | Quy tắc đo lường có phiên bản | **C** | Cấu hình `measurement_rules`; chỉ `CONFIRMED` mới cho kết luận chính thức |
| US-27 | Tính CLO của từng sinh viên | **C** | Kèm khóa chống chạy trùng bằng `lockKey` (chỉ mục duy nhất, chi phí thấp) |
| US-28 | Tính PI và PLO | **C** | PI: `DIRECT_FROM_ITEMS` (theo Q10) và `FROM_CLOS`, công thức theo `PLO_DB_UPDATE.md` §4.2. **Công thức cấp PLO chưa có trong tài liệu (Q21)**: cài sau khi GVHD/Khoa trả lời, xem `plan/F06-do-luong.md` §9. PLO: cài đặt phương pháp Khoa xác nhận, tối thiểu một trong `ALL_PIS_PASSED`/`WEIGHTED_PIS`/`AVERAGE_PIS` **[CHỜ KHOA, Q04, Q19]**. `pi.aggregationMethod`: tối thiểu `WEIGHTED_AVERAGE` |
| US-29 | Tổng hợp lớp/khóa/CTĐT kèm mẫu số | **C** | Luôn trả đủ `counts` (ghi danh, đủ dữ liệu, đạt, chưa đạt, chưa đủ dữ liệu) và `passRateOfEnrolled` |
| US-30 | Đánh dấu kết quả cũ (`isStale`) | MR | |

### E08 Báo cáo và truy vết (F07)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-31 | Báo cáo CLO theo lớp | **C** | Có phạm vi, quy tắc, phiên bản, `limitations` |
| US-32 | Báo cáo PI theo học phần/khóa, PLO theo CTĐT | **C** | Ghi rõ "chưa đủ dữ liệu để kết luận toàn chương trình" khi áp dụng |
| US-33 | Báo cáo độ đầy đủ dữ liệu | **C** | |
| US-34 | Truy vết kết quả → điểm → lô nhập → dòng tệp gốc | **C** | ≤ 3 thao tác |
| US-35 | Xuất báo cáo | MR | Nếu làm: chỉ Excel. PDF: K |
| US-36 | Dashboard tổng quan | MR | Đọc từ `aggregate_results`, không tính lại từ `scores` |

### E09 Cải tiến (F08)

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-37 | Tạo vấn đề và hành động cải tiến | **C** | Gắn CLO/PI/PLO, `rootCause`, `actionPlan`, `expectedTarget`, người phụ trách, hạn, mức ưu tiên (kể cả `CRITICAL`) |
| US-38 | Theo dõi trạng thái (OPEN → IN_PROGRESS → DONE → VERIFIED) | **C** | Lưu `history[]` |
| NEW-02 | Đánh giá hiệu quả cải tiến ở chu kỳ đo kế tiếp (`evaluation`: `newPassRate`, `isEffective`) | MR | Story mới từ v0.5; nối với một lượt tính sau đó |

### E10 Hệ thống

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| US-39 | Health check, log tập trung, cảnh báo | **C** | `/actuator/health` |
| US-40 | Xử lý ngoại lệ thống nhất, UI có trạng thái rỗng/lỗi/hết thời gian | **C** | |

### Story mới khác do CSDL v0.5

| ID | Story | Quyết định | Ghi chú |
|---|---|---|---|
| NEW-03 | Vai trò STUDENT: sinh viên đăng nhập xem `student_results` của chính mình | MR | Cần nối `students.userId`; chỉ đọc |
| NEW-04 | Phương pháp mở rộng của engine: `pi.method = FROM_CLOS`, `aggregationMethod` còn lại (`MAX`, `LATEST`, `ACCUMULATED_EVIDENCE`), các phương pháp PLO còn lại | MR | CSDL đã hỗ trợ giá trị enum; chỉ cài đặt phần ngoài cam kết nếu còn thời gian |

---

## 4. Không làm (K)

| Hạng mục | Lý do |
|---|---|
| Tổ chức thi, sinh đề; duyệt **nội dung** đề thi | Ngoài phạm vi đã chốt. NEW-01 chỉ duyệt ma trận ánh xạ chuẩn đầu ra, không duyệt nội dung câu hỏi |
| Xuất phôi in đề thi (`assessments.exportedFiles`) | Thuộc khâu tổ chức thi. Trường vẫn nằm trong CSDL nhưng không có tính năng. **[CHỜ GVHD]** |
| Lưu bài làm của sinh viên | Chỉ lưu điểm và tham chiếu tệp điểm gốc |
| Machine Learning (`ml_experiments`) | Ngoài phạm vi hiện tại |
| Quy đổi I/R/M thành hệ số (`pi.useIrmAsWeight`, `irmWeights`) | Trái bất biến "I/R/M ≠ trọng số". Engine luôn coi cờ này là `false`; không đặt giá trị mẫu. Chỉ xem lại khi Khoa xác nhận (Q18) |
| Collection bộ môn (`departments`) | Chưa có trong thiết kế; `courses.departmentId` chỉ là trường chờ (Q17) |
| Đo CLO/PI khi chỉ có điểm tổng (`dataGranularity = TOTAL_ONLY`) | Không đo được CLO/PI. Nếu dữ liệu thật chỉ có điểm tổng thì phải đổi phạm vi bằng ADR trước K1 |
| Nhập câu hỏi từ Excel/Moodle (US-17), đồng bộ trực tiếp với Moodle, xuất PDF | Giữ phạm vi chặt; tránh phình to |

---

## 5. Thứ tự cắt khi chậm tiến độ

Chỉ cắt trong cột MR trước; story C chỉ hạ xuống khi có ADR và GVHD đồng ý (bản cam kết mục 10).

| Cổng | Nếu chậm | Cắt |
|---|---|---|
| K2 (cuối T9) | P2 chưa xong | Bỏ US-08, US-13, US-14 (đã là MR); không làm thêm MR nào của P3 |
| K3 (cuối T10) | Khoa chưa xác nhận quy tắc | Chạy chế độ không chính thức, ghi rõ giới hạn; không cắt story |
| K4 (cuối T13) | Engine chưa khớp bộ kiểm chứng | Bỏ US-36, US-35, US-30, NEW-02 |
| K5 (cuối T15) | Chưa đủ coverage lõi ≥ 70% | Dừng thêm tính năng, chỉ sửa lỗi; không cắt kiểm thử |

Thứ tự ưu tiên làm: nền tảng và staging (T5–T7) → US-01–03 → CTĐT/CLO (E02, E03) → NEW-01 và E04 → E05 → E06 → E07
→ E08 → E09. Mỗi giai đoạn là lát cắt dọc (UI + API + test + tài liệu chạy trên staging).

---

## 6. Phân quyền theo vai trò (đề xuất, chờ Q08)

Vai trò là bên *chủ trì* thao tác; backend kiểm tra theo `roleAssignments[].scope`. Bảng này chưa được Khoa duyệt;
điểm cần Khoa chốt là ai được xác nhận quy tắc đo (`RULE_CONFIRM`).

| Thao tác | ADMIN | PROGRAM_MANAGER | DEPARTMENT_HEAD | QA_OFFICER | LECTURER | VIEWER |
|---|---|---|---|---|---|---|
| Quản lý người dùng | ✔ | | | | | |
| CTĐT, PLO/PI, ma trận I/R/M, học phần, đề cương, CLO, liên kết CLO–PI | | ✔ | | | | |
| Lập và gửi duyệt bài đánh giá lớp | | | | | ✔ | |
| Duyệt / từ chối ma trận đề thi | | | ✔ | | | |
| Nhập điểm, xác nhận lô | | | | | ✔ (lớp mình) | |
| Xác nhận quy tắc đo | | ? | | ? | | |
| Chạy đo lường, xem báo cáo | | ✔ | ✔ | ✔ | ✔ (lớp mình) | đọc |
| Tạo / theo dõi hành động cải tiến | | ✔ | ✔ | ✔ | | |

---

## 7. Quy tắc nghiệp vụ ràng buộc mọi feature

Tóm tắt từ `GiaiThich_CoSoDuLieu.md` mục 2; vi phạm là lỗi nghiệp vụ.

1. Không hardcode công thức, trọng số, ngưỡng, mẫu số; đọc từ `measurement_rules`. Các số 40/60, 60%, 72%, 20/40/40 chỉ là ví dụ minh họa.
2. Thiếu dữ liệu không mặc định bằng 0: chỉ `BLANK` tính 0; `ABSENT`, `NOT_GRADED`, `EXEMPT`, `MISSING` có `rawScore = null`.
3. Bloom ≠ điểm; I/R/M ≠ trọng số; mức rubric ≠ Bloom; `capabilityLevelRaw` lưu nguyên, không diễn giải.
4. Tỷ lệ đạt luôn kèm mẫu số: ghi danh, đủ dữ liệu, đạt, chưa đạt, chưa đủ dữ liệu.
5. Một câu đo nhiều cặp (CLO, PI) chia điểm theo `weight` (tổng bằng 1); PI chỉ chọn trong PI đã liên kết với CLO của câu đó.
6. Mọi kết quả gắn `programVersionId`, `syllabusId`, `ruleId`; truy vết được về lô nhập và dòng tệp gốc.
7. Không bịa nguyên văn PLO/PI/CLO, ánh xạ CLO–PI, công thức hay ngưỡng: để trống, đánh dấu chờ xác nhận.
8. Angular chỉ gọi API; công thức đo lường chỉ chạy ở backend.

---

## 8. Việc còn treo trước khi ký (phụ thuộc người khác)

| # | Việc | Ai trả lời | Ảnh hưởng tới feature |
|---|---|---|---|
| 1 | Chấp nhận NEW-01 (Trưởng bộ môn duyệt ma trận) và không làm xuất phôi in | GVHD (Q11) | NEW-01, vai trò DEPARTMENT_HEAD |
| 2 | Chọn ba học phần thử nghiệm; xác nhận điểm có theo từng câu hỏi (Q05) | Khoa, GVHD | Toàn bộ E06–E08; nếu chỉ có điểm tổng phải đổi phạm vi bằng ADR |
| 3 | Công thức, ngưỡng, mẫu số, xử lý thiếu dữ liệu (Q04, Q12, Q13) | Khoa | US-26–29; nếu không kịp K3 thì chạy chế độ không chính thức |
| 4 | Phương pháp mặc định PI/PLO và chính sách nhập lại (Q19) | Khoa | US-23, US-28 |
| 5 | Ánh xạ CLO–PI đã duyệt của ba học phần (Q14) | Khoa | US-12, US-15; KPI K7 |
| 6 | Ai được xác nhận quy tắc đo (Q08) | Khoa | Phân quyền mục 6 |
| 7 | Dùng mã sinh viên thật hay mã thay thế (Q09) | Khoa | US-20 |
| 8 | Chấp nhận chạy chế độ không chính thức nếu K3 trượt | GVHD | Bản cam kết mục 3 |
| 9 | ~~Cập nhật `CLAUDE.md` và rule 02~~ **Đã cập nhật 2026-10-27**; ADR nháp ở `adr/0001-duyet-ma-tran-de-thi.md`, cần GVHD trả lời để chốt | Hai sinh viên | Tránh agent tự mâu thuẫn với CSDL v0.5 |
| 10 | **Công thức còn thiếu** (cấp PLO; cấp học phần khi `requireAllCloPass = false`; `WEIGHTED_ITEMS`; gộp PI nhiều học phần; thiếu dữ liệu/ngưỡng trong công thức): 11 mục CT-01…CT-11 ở `plan/F06-do-luong.md` §9 | GVHD, Khoa (Q20–Q23) | US-27, US-28, US-29, US-32; PLO không thể báo cáo chính thức nếu chưa có |

Việc 1 nên ghi thành ADR (`doc/adr/`) vì đổi phạm vi (CLAUDE.md quy tắc 9).

---

## 9. Chữ ký chốt

| Vai trò | Họ tên | Chữ ký | Ngày |
|---|---|---|---|
| Sinh viên 1 (IBS) | | | |
| Sinh viên 2 (IT) | | | |
| Giảng viên hướng dẫn | | | |
