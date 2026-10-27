# F07 — Báo cáo, truy vết, xuất tệp, dashboard

Story: **US-31** báo cáo CLO theo lớp (C), **US-32** báo cáo PI theo học phần/khóa và PLO theo CTĐT (C), **US-33** báo cáo độ đầy đủ
dữ liệu (C), **US-34** truy vết kết quả → điểm → lô nhập → dòng tệp gốc (C), **US-35** xuất báo cáo (MR; chỉ Excel, PDF: K),
**US-36** dashboard (MR). Giai đoạn P5. Chủ trì đề xuất: IBS (nội dung, cách đọc) + IT. Package: `report`.
Phụ thuộc: `00`, `F01`, `F06` (đọc kết quả đã tính). **F07 chỉ đọc kết quả, không tự tính lại.**

## 1. Mục tiêu

Biến kết quả đo lường (`student_results`, `aggregate_results`) thành báo cáo đọc được, **luôn nói rõ phạm vi, quy tắc, phiên bản, mẫu số và giới hạn**, và cho phép đi
ngược từ một kết quả về dòng điểm trong tệp gốc trong ≤ 3 thao tác.

## 2. Đọc trước

- `GiaiThich_CoSoDuLieu.md` nguyên tắc 4, 9, 10, §4.25–4.27, §5 (ví dụ truy vết), Q04.
- `database_import.js`: khối `reports`, `aggregate_results`, `student_results`.
- `F06-do-luong.md` mục 4.5–4.7 (cấu trúc kết quả và `inputs[]`).

## 3. Collection `reports`

`type` (`SECTION_CLO`, `COURSE_PI`, `PROGRAM_PLO`, `DATA_COMPLETENESS`), `title`, `params` (phạm vi lọc), `runId`, `ruleId`, `programVersionId`, `summary`, `limitations`
(chuỗi), `exports[]` (`{ format, fileRef, generatedAt }`), `generatedBy`, `createdAt`. Chỉ mục: `runId`; `(type, programVersionId, createdAt desc)`.

## 4. Quy tắc nghiệp vụ

1. **Không tự tính lại.** Báo cáo và dashboard đọc `aggregate_results` / `student_results` của một `runId`; không duyệt cả `scores` mỗi lần mở trang. Số hiển thị là số API trả, UI không tính lại.
2. **Mọi báo cáo ghi:** phạm vi (lớp/học phần/khóa/CTĐT, học kỳ), `ruleId` + `version` + trạng thái quy tắc, `programVersionId`, `runId`, `isOfficial`, thời điểm tính, `counts` đủ 5 số kèm `passRateOfEnrolled`.
3. **`limitations` được sinh từ sự kiện, không viết tay tùy ý**, tối thiểu: kết quả *không chính thức* (quy tắc `DRAFT`/liên kết chưa `VERIFIED`); lần chạy `isStale = true`; `dataCompleteness < 1` (nêu số người chưa đủ dữ liệu);
   phạm vi chỉ là các học phần thử nghiệm ⇒ **"chưa đủ dữ liệu để kết luận toàn chương trình"** (báo cáo PLO/CTĐT).
4. **Mẫu số luôn có mặt:** không bao giờ hiển thị tỷ lệ đạt mà thiếu số ghi danh và số chưa đủ dữ liệu. Mẫu số 0 hiển thị "không có dữ liệu", không phải 0%.
5. **Phân biệt trong hiển thị:** CLO cá nhân, tỷ lệ đạt lớp và mục tiêu cấp lớp là ba đại lượng riêng; PLO/PI nhận diện bằng mã, sắp theo `displayOrder`.
6. **Quyền:** giảng viên chỉ xem báo cáo lớp mình; `STUDENT` (NEW-03) chỉ xem kết quả của chính mình; xuất tệp không chứa thông tin ngoài phạm vi người xuất. Báo cáo hiển thị `studentRef`, không hiển thị thông tin cá nhân không cần thiết.
7. **Truy vết (US-34):** `student_results.inputs[]` là một cấp liền kề; chuỗi đầy đủ PLO → PI → (CLO) → `scores` → `import_batches` + `sourceRow`/`fileRef` được dựng **bằng các lần truy vấn từng cấp** (API trả một cấp mỗi lần hoặc một API dựng sẵn chuỗi bằng nhiều lần đọc). Mục tiêu ≤ 3 thao tác từ kết quả PLO tới dòng điểm gốc **[ĐỀ XUẤT]**.
8. Báo cáo đã tạo gắn `runId` nên **giữ nguyên** khi quy tắc/điểm sau đó đổi; bản mới là báo cáo mới, không sửa đè.
9. **Xuất tệp (US-35, MR):** chỉ Excel; ghi vào `reports.exports[]` (định dạng, `fileRef`, thời điểm). Cần thư viện Excel (chung với F05b qua ADR). PDF: không làm.
10. **Dashboard (US-36, MR):** chỉ hiển thị số từ `aggregate_results`, kèm mẫu số; không có phép tính mới ở UI.

## 5. Stories và acceptance criteria (nháp)

| ID | AC |
|---|---|
| US-31/AC-1 | Báo cáo CLO của một lớp liệt kê từng CLO với `counts`, `passRate`, `passRateOfEnrolled`, mục tiêu/`targetMet`, và khối phạm vi + quy tắc + `programVersionId` + `runId` |
| US-31/AC-2 | Quy tắc `DRAFT`/chưa chính thức ⇒ `limitations` nêu "không chính thức"; lần chạy `isStale` ⇒ nêu "dữ liệu đã thay đổi sau lần tính" |
| US-31/AC-3 | Báo cáo của lớp khác (ngoài phạm vi giảng viên) ⇒ 403 |
| US-32/AC-1 | Báo cáo PI theo học phần/khóa và PLO theo CTĐT sắp theo `displayOrder`, nhận diện bằng mã; chỉ dựa trên kết quả đã tính |
| US-32/AC-2 | Khi phạm vi chỉ là học phần thử nghiệm ⇒ `limitations` ghi "chưa đủ dữ liệu để kết luận toàn chương trình" |
| US-33/AC-1 | Báo cáo độ đầy đủ liệt kê sinh viên/câu hỏi thiếu dữ liệu và lý do (`ABSENT`, `NOT_GRADED`, `EXEMPT`, `MISSING`, chưa nhập), kèm `dataCompleteness` |
| US-33/AC-2 | Lớp 45 ghi danh, 40 đủ dữ liệu (ví dụ minh họa) ⇒ báo cáo liệt kê đúng 5 người chưa đủ |
| US-34/AC-1 | Từ một `student_results` PLO truy được tới `scores` rồi `import_batches` + `sourceRow`/`fileName` trong ≤ 3 thao tác (kịch bản E2E, KPI K4) |
| US-34/AC-2 | Truy vết hiển thị đúng giá trị `normalizedValue` và `weight` của từng đầu vào; sinh viên không thuộc phạm vi ⇒ 403 |
| US-35/AC-1 (MR) | Xuất Excel tạo `reports.exports[]` với `format`, `fileRef`, `generatedAt`; nội dung khớp báo cáo hiển thị |
| US-36/AC-1 (MR) | Dashboard chỉ dùng `aggregate_results`; mọi tỷ lệ kèm mẫu số |

## 6. API đề xuất **[ĐỀ XUẤT]**

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| POST | `/api/reports` `{ type, params, runId }` (sinh và lưu báo cáo) | PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER; LECTURER (lớp mình) |
| GET | `/api/reports`, `/api/reports/{id}` | theo phạm vi |
| GET | `/api/trace/student-results/{id}` (một cấp), `/api/trace/scores/{id}` (về lô và dòng gốc) | theo phạm vi |
| POST | `/api/reports/{id}/export?format=xlsx` (MR) | như trên |
| GET | `/api/dashboard/...` (MR) | theo phạm vi |

## 7. Kiểm tra tham chiếu ở service

`runId` tồn tại, `status = SUCCESS`; `ruleId`/`programVersionId` khớp lần chạy; người gọi có quyền trên phạm vi; khi truy vết, `refId` thuộc đúng collection tương ứng với `sourceType`.

## 8. Test bắt buộc

Báo cáo lớp với quy tắc `DRAFT`; với lần chạy `isStale`; mẫu số 0 (không có sinh viên đủ dữ liệu); lớp 45/40/5 (ví dụ minh họa); PLO của học phần thử nghiệm có câu "chưa đủ dữ liệu";
truy vết: kết quả không có `inputs`, `refId` mồ côi, sinh viên ngoài phạm vi; hai báo cáo cùng phạm vi khác `runId` không ghi đè nhau; xuất Excel có `exports[]` đúng; số trong báo cáo khớp `aggregate_results`.

## 9. Không làm / chưa quyết

- Không làm: PDF, tự kết luận mức đạt toàn chương trình, tính lại công thức ở báo cáo/UI, đồng bộ ra hệ thống ngoài.
- Chờ: mẫu báo cáo chính thức của Khoa (nếu có) và ngôn ngữ của `limitations` (IBS soạn, cần GVHD duyệt); thư viện Excel (ADR chung với F05b).

## 10. Để vấn đáp

Vì sao báo cáo không tự tính lại (một nguồn sự thật duy nhất là engine ở F06, tránh hai con số khác nhau); vì sao `limitations` (kết quả nháp/thiếu dữ liệu không được đọc như kết luận chính thức);
vì sao truy vết đi từng cấp (mỗi kết quả tự chứng minh bằng đầu vào cấp liền kề); bỏ mẫu số thì "80% đạt" có thể là 32/40 hay 32/45.
