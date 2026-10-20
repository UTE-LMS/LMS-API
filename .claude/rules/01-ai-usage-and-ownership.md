# Rule 01 — Làm chủ sản phẩm và dùng AI có kiểm soát

Căn cứ: Rubric Nguyên tắc 1, TC2.3 (5 điểm), Mục 6, quy tắc chặn G3, G4.
Mức 5 TC2.3: AI Usage Log đầy đủ và đối chiếu được với commit; giải thích đúng 100% vị trí mã AI sinh
khi hội đồng chọn ≥ 5 vị trí; ≥ 5 lỗi/ảo giác AI tự phát hiện kèm nguyên nhân và commit sửa; có quy trình
kiểm soát đầu ra AI.

## 1. AI Usage Log (`logsAI/ai-usage-log.md`)

Mỗi repo có nhật ký riêng trong thư mục `logsAI/` ở gốc repo: phiên làm ở `LMS-API` ghi vào
`LMS-API/logsAI/ai-usage-log.md`; phiên làm ở `LMS-UI` ghi vào `LMS-UI/logsAI/ai-usage-log.md`. Lý do: mã commit
trong log phải khớp `git log` của đúng repo, và agent không nên ghi ra ngoài thư mục làm việc. Khi nộp, gộp hai
nhật ký. Không để log AI ở nơi khác. Có thể thêm file phụ trong `logsAI/` (ví dụ ghi chú theo tuần), nhưng bảng
chính là `ai-usage-log.md`.

Cuối mỗi phiên có tạo/sửa mã hoặc tài liệu, agent thêm một mục theo mẫu trong file đó:
**sinh viên yêu cầu (IBS hoặc IT, theo `git config user.name` hoặc hỏi một lần)**, công cụ và mô hình, phạm vi, prompt chính (tóm tắt), file do AI sinh/sửa, phần sinh viên cần tự sửa hoặc
rà soát, kiểm chứng đã làm, commit liên quan.

- Chỉ ghi điều đã thực sự xảy ra. Kê khai mâu thuẫn với lịch sử Git hoặc vấn đáp = G4 (TC2.3 = 0 điểm và
  kích hoạt quy trình đạo văn).
- Cột "phần sinh viên đã sửa" do **sinh viên** điền. Agent không tự điền thay, chỉ để "chờ sinh viên rà soát".
- Chưa có commit thì ghi "chưa commit", rồi nhắc sinh viên cập nhật mã commit sau khi commit.
- Khi người dùng yêu cầu commit có mã do AI sinh, thêm trailer
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` để log đối chiếu được với `git log`.

## 2. Ghi lỗi và ảo giác của chính agent

Mỗi khi phát hiện (bởi test, compiler, review hoặc người dùng) rằng agent đã sinh sai, ghi vào bảng
"Lỗi/ảo giác AI đã phát hiện" trong log: mô tả lỗi, cách phát hiện, nguyên nhân (API không tồn tại, sai
phiên bản thư viện, hiểu sai nghiệp vụ, thiếu ca biên…), commit sửa. Mục tiêu ≥ 5. Không che giấu lỗi để
log trông sạch.

## 3. Quy trình kiểm soát đầu ra AI (đưa vào checklist review của mọi PR)

Trước khi coi một đầu ra là xong, agent tự làm và báo kết quả:

- [ ] Build, lint và test đã chạy thật; dán kết luận đúng theo output.
- [ ] API thư viện/framework đã đối chiếu tài liệu chính thức của đúng phiên bản (Spring Boot 4.x, Angular 21,
      Spring Data MongoDB…). Không dựa vào trí nhớ cho API đổi giữa các phiên bản.
- [ ] Không thêm dependency mới khi chưa nêu lý do, giấy phép, và phương án không dùng nó.
- [ ] Không có secret, dữ liệu sinh viên thật hoặc dữ liệu nhạy cảm trong mã, test, tài liệu, prompt.
- [ ] Không chép mã từ dự án khác; nếu mô phỏng mẫu công khai thì ghi nguồn và giấy phép.
- [ ] Phần nghiệp vụ đối chiếu với tài liệu thiết kế (rule 02), không suy diễn công thức.

## 4. Mã phải giải thích được

Đồ án có hai sinh viên và hội đồng có thể hỏi bất kỳ ai ở bất kỳ vị trí nào, nên "giải thích được" áp dụng cho
**cả hai người**, không chỉ người đã yêu cầu agent. Với mã lõi, phần "Để vấn đáp" viết sao cho người chưa
từng thấy đoạn mã đọc xong vẫn trả lời được 4 câu của Mục 6.2.

- Mỗi khối mã không tầm thường kèm phần "Để vấn đáp" (làm gì, vì sao có, tham số/thư viện từ đâu, bỏ đi
  thì sao). Hội đồng cho tối đa 3 phút mỗi vị trí.
- Phần lõi (tính CLO/PI/PLO, nhập và kiểm tra điểm, phân quyền, truy vết): làm từng bước nhỏ, giải thích
  trước khi sang bước tiếp, ưu tiên mã thẳng và dễ đọc hơn mã "thông minh". Đây là "mô-đun chính";
  không giải thích được ở đây là V2 (TC2 = 0).
- Không sinh khối lớn nhiều lớp trừu tượng khi một lớp/hàm đơn giản đủ dùng. Không thêm "tính năng cho
  sẵn" ngoài yêu cầu.
- Nếu người dùng dán mã/ý tưởng từ nơi khác, hỏi nguồn và giấy phép trước khi tích hợp.

## 5. Tài liệu và luận văn

- Chỉ viết dựa trên sự thật đã kiểm chứng trong repo hoặc tài liệu do người dùng cung cấp.
- Không bịa tài liệu tham khảo, DOI, số trang, trích dẫn. Chỉ liệt kê nguồn đã mở và đọc được; nếu không
  xác minh được, ghi "cần sinh viên kiểm chứng".
- Bản thảo do agent viết là nháp để sinh viên đọc, hiểu và viết lại bằng lời của mình. Không chép nguyên
  văn từ nguồn ngoài (ngưỡng trùng lặp ≤ 20%, V4 nếu vượt).

## 6. Bảo mật khi dùng AI

- Không đưa vào prompt/công cụ ngoài: điểm thật, mã số sinh viên thật, thông tin cá nhân, khóa API, chuỗi
  kết nối. Dùng dữ liệu giả có nhãn rõ "DỮ LIỆU THỬ NGHIỆM".
- Nếu phát hiện secret hoặc dữ liệu thật trong repo, dừng, báo người dùng, đề nghị thu hồi/đổi khóa.
