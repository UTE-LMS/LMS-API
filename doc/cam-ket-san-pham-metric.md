# Bản cam kết sản phẩm cuối cùng và bộ metric đánh giá (Bước 3)

> **TRẠNG THÁI: DỰ THẢO — CHƯA KÝ.** Đây là khung do Claude Code soạn từ rubric và các tài liệu thiết kế để hai
> sinh viên và giảng viên hướng dẫn (GVHD) **đọc, sửa và chốt**. Mọi mục **[ĐỀ XUẤT]** là ý của người soạn; mục
> **[ĐIỀN]** là thông tin chỉ con người cung cấp được. Agent không ký thay và không điền thay các mục đó.
>
> **Hạn bắt buộc (Rubric Bước 3, G1):** chậm nhất khi qua 50% thời gian thực hiện (tuần 7–10), có chữ ký của sinh
> viên và GVHD. Không có bản này thì đồ án bị đánh giá Không đạt và không được thực hiện tiếp. Mục tiêu nội bộ: hết T7.
> Sau khi ký, mọi thay đổi phạm vi phải ghi ADR (`doc/adr/`) và được GVHD đồng ý.

---

## 1. Thông tin chung

| Mục | Nội dung |
|---|---|
| Tên đề tài | Xây dựng Hệ thống Website Quản lý học tập tích hợp đo lường chuẩn đầu ra (Learning Outcomes Management and Analytics System) |
| Giảng viên hướng dẫn | [ĐIỀN] |
| Sinh viên 1 (IBS) | [ĐIỀN họ tên, MSSV] |
| Sinh viên 2 (IT) | [ĐIỀN họ tên, MSSV] |
| Thời gian thực hiện | 95 ngày làm việc; bắt đầu [ĐIỀN ngày]; bảo vệ dự kiến [ĐIỀN ngày] |
| Ngày lập bản này / ngày ký | [ĐIỀN] / [ĐIỀN] |
| Đơn vị cung cấp nghiệp vụ | Khoa Công nghệ thông tin, Trường HCM-UTE |
| Đơn vị hướng dẫn công nghệ | Trung tâm Công nghệ phần mềm |
| Công nghệ | Java, Spring Boot 4.1.0, MongoDB (replica set), Angular 21 + TypeScript |
| Repository | `UTE-LMS/LMS-API`, `UTE-LMS/LMS-UI` (GitHub) |

## 2. Mục tiêu và người dùng

**Mục tiêu.** Hệ thống tiếp nhận kết quả có sẵn sau khi thi (điểm theo từng câu hỏi/tiêu chí) và đo lường mức đạt
theo chuỗi *câu hỏi/tiêu chí → CLO → PI → PLO → báo cáo → hành động cải tiến*; có phiên bản; truy vết được về dữ
liệu gốc; luôn báo rõ phạm vi, mẫu số và số sinh viên chưa đủ dữ liệu.

**Người dùng mục tiêu** (vai trò hệ thống): quản trị (ADMIN), người phụ trách CTĐT (PROGRAM_MANAGER), cán bộ đảm
bảo chất lượng (QA_OFFICER), giảng viên (LECTURER), người xem (VIEWER). Chân dung chi tiết: [ĐIỀN sau khảo sát,
xem TC1].

## 3. Phạm vi

**Làm:** CTĐT CNTT (7 PLO, 17 PI) và các học phần thử nghiệm; 8 nhóm chức năng F01–F08 theo mục 4.

**Không làm:** tổ chức thi, sinh đề, duyệt đề; lưu bài làm của sinh viên; thử nghiệm ML; quản lý "minh chứng" như
danh mục dữ liệu (chỉ lưu tệp điểm gốc và tham chiếu); kết luận mức đạt của toàn chương trình; thay thế quy trình
đo lường chính thức của Khoa.

**Học phần thử nghiệm:** [ĐIỀN: ba học phần; chưa chọn]. Nguồn dữ liệu điểm: [ĐIỀN]. Giới hạn: kết quả chỉ kết luận
trong phạm vi có đủ minh chứng.

**Phụ thuộc bên ngoài (rủi ro):** quy tắc đo lường (công thức, trọng số, ngưỡng) do Khoa xác nhận. Nếu đến hạn K3
(cuối T10) Khoa chưa xác nhận, hệ thống chạy ở chế độ không chính thức và báo cáo ghi rõ giới hạn. Phương án
này cần GVHD đồng ý: [ĐIỀN: đồng ý / không đồng ý].

## 4. Sản phẩm cam kết

Cột "Quyết định" do hai sinh viên và GVHD chọn: **C** = cam kết, **MR** = mục tiêu mở rộng (làm nếu còn thời gian,
không tính vào cam kết), **K** = không làm. Cột "Đề xuất" là gợi ý của người soạn theo mức ưu tiên trong
`ke-hoach-xay-dung.md` (M → C; S → MR; C → K). Mỗi story đều phải có acceptance criteria trong SRS trước khi làm.

| ID | Story | Đề xuất | Quyết định |
|---|---|---|---|
| US-01 | Đăng nhập/đăng xuất | C | [ĐIỀN] |
| US-02 | Tạo người dùng, gán vai trò và phạm vi | C | [ĐIỀN] |
| US-03 | Mọi API kiểm tra quyền theo phạm vi ở backend | C | [ĐIỀN] |
| US-04 | Nhật ký đăng nhập và thao tác quan trọng | MR | [ĐIỀN] |
| US-05 | Quản lý chương trình và phiên bản CTĐT | C | [ĐIỀN] |
| US-06 | Quản lý 7 PLO và 17 PI theo mã, kèm nguồn và trạng thái xác nhận | C | [ĐIỀN] |
| US-07 | Ma trận học phần đóng góp PI (I/R/M) | C | [ĐIỀN] |
| US-08 | Quy trình xác nhận nội dung (UNVERIFIED/VERIFIED/NEEDS_CLARIFICATION) | MR | [ĐIỀN] |
| US-09 | Quản lý học phần và vị trí trong CTĐT | C | [ĐIỀN] |
| US-10 | Quản lý đề cương theo phiên bản | C | [ĐIỀN] |
| US-11 | Quản lý CLO thuộc một đề cương | C | [ĐIỀN] |
| US-12 | Liên kết CLO–PI và xác nhận | C | [ĐIỀN] |
| US-13 | Ngân hàng câu hỏi và cây danh mục 3 cấp | MR | [ĐIỀN] |
| US-14 | Câu hỏi kèm ánh xạ nhiều cặp (CLO, PI, weight) | C | [ĐIỀN] |
| US-15 | Bài đánh giá theo đề cương và theo lớp (`items[]`) | C | [ĐIỀN] |
| US-16 | Tiêu chí rubric và mức rubric 1–4 | C | [ĐIỀN] |
| US-17 | Nhập câu hỏi từ Excel/Moodle | K | [ĐIỀN] |
| US-18 | Học kỳ và khóa sổ điểm | C | [ĐIỀN] |
| US-19 | Lớp học phần và giảng viên phụ trách | C | [ĐIỀN] |
| US-20 | Nhập danh sách lớp → sinh viên và ghi danh | C | [ĐIỀN] |
| US-21 | Tải tệp điểm và ánh xạ cột | C | [ĐIỀN] |
| US-22 | Kiểm tra tệp, báo lỗi theo dòng/cột | C | [ĐIỀN] |
| US-23 | Chọn cách hiểu ô trống (BLANK/MISSING) và chính sách nhập lại | C | [ĐIỀN] |
| US-24 | Xác nhận nhập (transaction) hoặc rollback lô | C | [ĐIỀN] |
| US-25 | Sửa điểm đơn lẻ có lý do | MR | [ĐIỀN] |
| US-26 | Quy tắc đo lường có phiên bản (DRAFT/CONFIRMED/RETIRED) | C | [ĐIỀN] |
| US-27 | Tính CLO của từng sinh viên theo lớp | C | [ĐIỀN] |
| US-28 | Tính PI và PLO từ kết quả cấp dưới | C | [ĐIỀN] |
| US-29 | Tổng hợp cấp lớp/khóa/CTĐT kèm mẫu số | C | [ĐIỀN] |
| US-30 | Chống chạy trùng, đánh dấu kết quả cũ | MR | [ĐIỀN] |
| US-31 | Báo cáo CLO theo lớp học phần | C | [ĐIỀN] |
| US-32 | Báo cáo PI theo học phần/khóa, PLO theo CTĐT | C | [ĐIỀN] |
| US-33 | Báo cáo độ đầy đủ dữ liệu | C | [ĐIỀN] |
| US-34 | Truy vết kết quả → điểm → lô nhập → dòng tệp gốc | C | [ĐIỀN] |
| US-35 | Xuất báo cáo Excel/PDF | MR | [ĐIỀN] |
| US-36 | Dashboard tổng quan | MR | [ĐIỀN] |
| US-37 | Tạo vấn đề và hành động cải tiến (F08) | C | [ĐIỀN] |
| US-38 | Theo dõi trạng thái hành động cải tiến (F08) | C | [ĐIỀN] |
| US-39 | Health check, log tập trung, cảnh báo | C | [ĐIỀN] |
| US-40 | Xử lý ngoại lệ thống nhất, trạng thái rỗng/lỗi/hết thời gian ở UI | C | [ĐIỀN] |

**Lưu ý về khối lượng:** đề xuất có 32 story "C" cho hai sinh viên trong khoảng 10 tuần lập trình (từ T6) khi mã mới
là khung. Rubric TC2.2 chấm theo đúng bản cam kết này (100% chức năng cam kết hoạt động, khối lượng ≥ 100%), nên
**nên cam kết ít hơn khả năng, và để phần còn lại ở "MR"**. Hai bạn và GVHD cân nhắc trước khi ký.

Tổng số story cam kết: [ĐIỀN]. Khối lượng ước tính mỗi người (mức khung, hỗ trợ chéo tự do): [ĐIỀN].

## 5. Môi trường triển khai cam kết

| Mục | Cam kết |
|---|---|
| Môi trường chạy | [ĐIỀN: staging công khai / production có người dùng thật] — URL: [ĐIỀN] |
| Hạ tầng | [ĐIỀN: do Trung tâm CNPM hướng dẫn] |
| Pipeline | build → lint/phân tích tĩnh → test + coverage → quét bảo mật/phụ thuộc → đóng gói Docker → triển khai tự động staging |

## 6. KPI nghiệp vụ định lượng (≥ 5 KPI theo Mức 5 TC1)

Các ngưỡng dưới đây là **[ĐỀ XUẤT]**, chỉ chốt khi hai sinh viên và GVHD đồng ý. Cột "Giá trị cơ sở" cần đo thực tế
trước khi cam kết các KPI so sánh trước–sau.

| # | KPI | Cách đo / nguồn minh chứng | Ngưỡng [ĐỀ XUẤT] | Giá trị cơ sở | Chốt |
|---|---|---|---|---|---|
| K1 | Độ khớp giữa kết quả CLO/PI/PLO của hệ thống và kết quả tính độc lập (tính tay/bảng tính) trên bộ kiểm chứng | Bộ ca kiểm chứng + biên bản đối chiếu | 100% ca khớp trong dung sai [ĐIỀN] | — | ☐ |
| K2 | Tỷ lệ lỗi cài sẵn trong tệp thử (mã sinh viên sai, điểm vượt thang, trùng bản ghi, thiếu trường) được hệ thống phát hiện đúng dòng/cột | Tệp thử có danh sách lỗi cài sẵn + `import_row_errors` | 100% | — | ☐ |
| K3 | Thời gian từ nhập tệp điểm của một lớp đến có báo cáo CLO | Đo thời gian thực hiện bằng hệ thống so với quy trình thủ công hiện tại | giảm ≥ [ĐIỀN]% so với cơ sở | [ĐIỀN, cần đo] | ☐ |
| K4 | Số thao tác để truy vết từ một kết quả PLO về dòng điểm gốc | Kịch bản kiểm thử E2E | ≤ 3 | — | ☐ |
| K5 | Tỷ lệ hoàn thành tác vụ chính của người dùng thật | Thực nghiệm người dùng (TC2.7) | ≥ 90% | — | ☐ |
| K6 | Điểm SUS | Phiếu SUS chuẩn hóa | ≥ 80 | — | ☐ |
| K7 | Tỷ lệ câu hỏi/tiêu chí của các học phần thử nghiệm có ánh xạ CLO–PI đã xác nhận | Dữ liệu trong hệ thống | ≥ [ĐIỀN]% | — | ☐ |

## 7. Yêu cầu phi chức năng định lượng (≥ 5 theo Mức 5 TC2.1)

**[ĐỀ XUẤT]**, cần điều chỉnh theo máy chủ thật và đo bằng k6/JMeter hoặc test tương ứng.

| # | Yêu cầu | Ngưỡng | Cách kiểm chứng |
|---|---|---|---|
| N1 | Nhập và kiểm tra tệp điểm ~5.000 dòng | ≤ 10 giây | Test hiệu năng |
| N2 | Tính CLO cho một lớp ~60 sinh viên | ≤ 5 giây | Test hiệu năng |
| N3 | Thời gian phản hồi API đọc p95 với 50 người dùng đồng thời | ≤ 500 ms | k6/JMeter |
| N4 | Bảo mật: mật khẩu băm (bcrypt/argon2); 100% endpoint kiểm tra quyền; 0 lỗ hổng mức cao trong quét phụ thuộc | đạt | Test API phân quyền + `npm audit`/OWASP Dependency-Check |
| N5 | Khả dụng của staging trong giai đoạn thực nghiệm | ≥ 99% | Giám sát health check |
| N6 | Toàn vẹn: lỗi giữa chừng khi xác nhận nhập không để lại điểm nửa vời | 0 bản ghi nửa vời | Test integration với transaction |

## 8. Bộ metric đánh giá theo Rubric (Mục 5)

Mặc định nhắm **Mức 5**; hai bạn và GVHD có thể điều chỉnh ngưỡng theo đặc thù đề tài (Rubric Mục 5 cho phép
điều chỉnh tại Bước 3). Cột "Mục tiêu đề tài" bắt đầu bằng ngưỡng Mức 5.

| Nhóm | Metric | Mục tiêu đề tài | Nguồn minh chứng |
|---|---|---|---|
| TC1 | Bên liên quan khảo sát | ≥ 3 (hoặc ≥ 30 phiếu) | Biên bản/phiếu |
| TC1 | Giải pháp hiện có được so sánh | ≥ 3 | Bảng so sánh trong luận văn |
| TC1 | KPI nghiệp vụ định lượng | ≥ 5 (mục 6) | Bản này |
| TC2.1 | User story có acceptance criteria | 100% | SRS/backlog |
| TC2.1 | Loại sơ đồ thiết kế khớp mã nguồn | ≥ 4, khớp 100% | SDD |
| TC2.1 | Yêu cầu phi chức năng định lượng | ≥ 5 (mục 7) | SRS |
| TC2.2 | Chức năng cam kết hoạt động | 100% | UAT/demo |
| TC2.2 | Lỗi blocker khi demo | 0 | Ghi nhận hội đồng |
| TC2.2 | Môi trường chạy | [ĐIỀN] | URL |
| TC2.2 | Khối lượng hoàn thành so với cam kết | ≥ 100% | Biên bản nghiệm thu |
| TC2.3 | AI Usage Log đối chiếu được với commit; lỗi AI tự phát hiện | đầy đủ; ≥ 5 | `logsAI/` |
| TC2.4 | Lỗi lint / issue Blocker-Critical / trùng lặp | 0 / 0 / ≤ 3% | Báo cáo linter, Sonar |
| TC2.4 | Tuần có commit / thay đổi qua PR có review / secret lộ | ≥ 90% / ≥ 90% / 0 | Git, PR, gitleaks |
| TC2.5 | Coverage lõi / tự động hóa / pass ở CI cuối / defect Critical | ≥ 70% / ≥ 70% / 100% / 0 | Coverage, CI, bug tracker |
| TC2.5 | Số tầng kiểm thử | ≥ 4 | Test plan |
| TC2.6 | Chặng pipeline / build xanh / số lần deploy / commit→staging | ≥ 6 / ≥ 90% / ≥ 10 / ≤ 15 phút | Lịch sử pipeline |
| TC2.6 | Giám sát sau triển khai | Monitoring và cảnh báo | Cấu hình |
| TC2.7 | Người dùng thật / tỷ lệ hoàn thành / SUS / vòng cải tiến đo lại | ≥ 10 / ≥ 90% / ≥ 80 / ≥ 1 | Báo cáo thực nghiệm |
| Quy trình | Tần suất báo cáo tiến độ | ≥ 1,0 lần/tuần | Sổ tiến độ |

## 9. Hình thức nghiệm thu

- UAT/demo trực tiếp trên môi trường đã triển khai, theo luồng: đăng nhập → chọn lớp/bài đánh giá → tải tệp điểm →
  xem lỗi → xác nhận nhập → xem kết quả CLO (kèm mẫu số) → báo cáo → truy vết.
- Biên bản nghiệm thu đối chiếu từng story cam kết (mục 4) và từng KPI (mục 6).
- Dữ liệu dùng để nghiệm thu: [ĐIỀN: dữ liệu thật đã được phép / dữ liệu thử nghiệm có nhãn].

## 10. Quy tắc thay đổi sau khi ký

Mọi thay đổi story cam kết, KPI, NFR hoặc phạm vi phải: (1) ghi ADR nêu lý do, tác động tiến độ; (2) được GVHD đồng ý
bằng văn bản; (3) cập nhật bản này (ghi phiên bản và ngày). Sản phẩm không tương xứng khối lượng đã đăng ký là G9.

## 11. Chữ ký

| Vai trò | Họ tên | Chữ ký | Ngày |
|---|---|---|---|
| Sinh viên 1 | | | |
| Sinh viên 2 | | | |
| Giảng viên hướng dẫn | | | |
| Kết luận rà soát của bộ môn | | | |
