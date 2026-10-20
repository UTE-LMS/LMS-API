Dạ thầy, với thông tin bổ sung, ngày 1 có thể hoàn thiện phần định hướng dự án, lựa
chọn công nghệ và phân công trách nhiệm:
• Backend: Spring Boot.
• Frontend: Angular.
• Cơ sở dữ liệu: MongoDB.
• Nguồn minh chứng nghiệp vụ: Khoa Công nghệ thông tin, Trường HCM-UTE.
• Hướng dẫn công nghệ: Trung tâm Công nghệ phần mềm.
Dưới đây là nội dung hồ sơ ngày 1 đã cập nhật, có thể dùng để giao việc và lập biên
bản khởi động. Những việc như nhận dữ liệu, kiểm tra máy và cài đặt môi trường chỉ ghi
“hoàn thành” khi đã có minh chứng thực tế.
1. Thông tin khởi động dự án
Nội dung Thông tin
Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra
Tên dự án đề xuất
chương trình đào tạo
Chương trình đào tạo ngành Công nghệ thông tin theo tài liệu
Đối tượng thử nghiệm
đã cung cấp
Thời gian thực hiện 95 ngày làm việc
Nhân sự thực hiện Một sinh viên IBS năm 3 và một sinh viên IT năm 3
Đơn vị cung cấp minh chứng
Khoa Công nghệ thông tin, Trường HCM-UTE
nghiệp vụ
Đơn vị hướng dẫn công nghệ Trung tâm Công nghệ phần mềm
Công nghệ chính Spring Boot, Angular, MongoDB
Hệ thống thử nghiệm phục vụ khảo sát, kiểm chứng và đánh
Phạm vi triển khai
giá
Thông tin bổ sung trong biên bản Ngày họp, người tham dự, người hướng dẫn, đầu mối cung
thực tế cấp dữ liệu
2. Nội dung mục tiêu đưa vào biên bản
Dự án xây dựng hệ thống hỗ trợ quản lý chuẩn đầu ra chương trình đào tạo và học phần;
tổ chức minh chứng đánh giá; tính toán mức đạt theo quy tắc được xác nhận; cung cấp
báo cáo phục vụ theo dõi và cải tiến chất lượng đào tạo.
Hệ thống dự kiến hỗ trợ chuỗi nghiệp vụ:
Minh chứng đánh giá → CLO → PI → PLO → Báo cáo → Hành động cải tiến.
Trong đó:
• Khoa CNTT cung cấp tài liệu, dữ liệu và giải thích nghiệp vụ; đầu mối xác nhận nội
dung chuyên môn cần được ghi rõ.
• Trung tâm Công nghệ phần mềm hướng dẫn kiến trúc, công nghệ, quy trình phát
triển và triển khai.
• Sinh viên IBS chủ trì phân tích nghiệp vụ, chuẩn hóa dữ liệu, thiết kế báo cáo và
kiểm chứng kết quả.
• Sinh viên IT chủ trì thiết kế kỹ thuật, lập trình, tích hợp và kiểm thử kỹ thuật.
3. Phạm vi ban đầu của hệ thống
Nhóm chức năng Nội dung dự kiến
Quản lý CTĐT Thông tin chương trình, phiên bản áp dụng, danh mục PLO và PI
Quản lý học phần Học phần, phiên bản đề cương, CLO và liên kết CLO–PI
Quản lý đánh giá Bài đánh giá, câu hỏi, tiêu chí rubric, thang điểm
Tiếp nhận dữ liệu Nhập dữ liệu điểm; phát hiện dòng lỗi, mã trùng, thiếu thông tin
Đo lường Tính CLO; tổng hợp PI/PLO theo quy tắc được xác nhận
Báo cáo Kết quả đạt chuẩn, độ đầy đủ minh chứng, truy vết về dữ liệu gốc
Cải tiến Ghi nhận vấn đề, hành động, người phụ trách và trạng thái

| Nhóm chức năng |     | Nội dung dự kiến |     |     |
| -------------- | --- | ---------------- | --- | --- |
Người dùng Đăng nhập và phân quyền theo vai trò, phạm vi phụ trách
ML Một thử nghiệm được lựa chọn sau khi đánh giá dữ liệu
Theo kế hoạch, có thể khảo sát trước ba học phần Lập trình hướng đối tượng, Cơ sở
dữ liệu và Công nghệ phần mềm. Danh sách chính thức được xác nhận theo khả năng
cung cấp dữ liệu của Khoa.
Danh mục 7 PLO và 17 PI có thể đưa vào hệ thống đầy đủ; kết quả đo lường chỉ
được kết luận trong phạm vi có đủ minh chứng.
4. Quyết định công nghệ ngày 1
Bộ công nghệ thầy chọn phù hợp với cách chia ứng dụng thành giao diện, xử lý nghiệp vụ
và lưu trữ dữ liệu. Với hai sinh viên năm 3, nên triển khai một ứng dụng backend chia
theo các nhóm chức năng, giúp nhóm tập trung hoàn thiện quy trình nghiệp vụ và kiểm
chứng phép tính.
| Thành phần | Công nghệ |     | Trách nhiệm |     |
| ---------- | --------- | --- | ----------- | --- |
Màn hình quản lý, nhập dữ liệu, ma trận liên
| Giao diện | Angular |     |     |     |
| --------- | ------- | --- | --- | --- |
kết, dashboard và báo cáo
API, xác thực, phân quyền, kiểm tra dữ liệu,
| Backend | Spring Boot |     |     |     |
| ------- | ----------- | --- | --- | --- |
tính toán và truy vết
Lưu danh mục, phiên bản, liên kết, điểm, cấu
| Cơ sở dữ liệu | MongoDB |     |     |     |
| ------------- | ------- | --- | --- | --- |
hình và kết quả
| Quản lý mã  | Git, trên nền tảng do Trung  |     |     |     |
| ----------- | ---------------------------- | --- | --- | --- |
Lưu lịch sử, phối hợp và rà soát thay đổi
| nguồn           | tâm thống nhất            |     |     |     |
| --------------- | ------------------------- | --- | --- | --- |
| Môi trường thử  | Theo hướng dẫn của Trung  |     |     |     |
Chạy ứng dụng, kiểm thử, sao lưu và bàn giao
| nghiệm | tâm |     |     |     |
| ------ | --- | --- | --- | --- |
Các nguyên tắc kỹ thuật cần ghi ngay từ đầu:
• Angular trao đổi với Spring Boot qua API; không truy cập trực tiếp MongoDB.
• Spring Boot kiểm tra quyền và dữ liệu trước khi xử lý.
• Công thức đo lường chính thức được thực hiện và kiểm chứng ở backend.
MongoDB cần quy tắc kiểm tra cấu trúc, mã định danh và liên kết dữ liệu.
•
Dữ liệu và kết quả đo lường phải gắn với phiên bản CTĐT, đề cương và quy tắc áp
•
dụng.
• Nơi lưu tệp minh chứng được thiết kế riêng; MongoDB lưu thông tin mô tả và tham
chiếu theo phương án Trung tâm hướng dẫn.
Ngày 1 chốt bộ công nghệ. Phiên bản JDK, Spring Boot, Node.js, Angular và MongoDB
được Trung tâm thống nhất thành một bộ cấu hình tương thích để hai sinh viên sử dụng
đồng nhất.
5. Phân định trách nhiệm giữa Khoa và Trung tâm
Trung tâm Công
| Công việc | Khoa CNTT |     |     | Sinh viên |
| --------- | --------- | --- | --- | --------- |
nghệ phần mềm
Xác định nhu  Cung cấp yêu cầu và người  IBS ghi nhận; IT đối
Góp ý tính khả thi
| cầu sử dụng | dùng nghiệp vụ |     |     | chiếu |
| ----------- | -------------- | --- | --- | ----- |
Cung cấp tài
Cung cấp hoặc chỉ định nguồn  Hướng dẫn cách tiếp  Kiểm kê, lưu nguồn,
liệu, minh
|     | được phép sử dụng | nhận kỹ thuật | kiểm tra chất lượng |     |
| --- | ----------------- | ------------- | ------------------- | --- |
chứng
Giải thích PLO,  Giải thích và chỉ định người xác  Hỗ trợ chuyển thành  Chuẩn hóa và ghi câu
| PI, CLO                      | nhận | mô hình hệ thống  |     | hỏi |
| ---------------------------- | ---- | ----------------- | --- | --- |
| Đầu mối chuyên môn xác nhận  |      | Rà soát khả năng  |     |     |
Xác nhận cách
hoặc chuyển người có thẩm  hiện thực và kiểm  IBS đặc tả; IT lập trình
đo lường
|     | quyền | chứng |     |     |
| --- | ----- | ----- | --- | --- |

Trung tâm Công
| Công việc |     | Khoa CNTT |     |     |     |     | Sinh viên |     |
| --------- | --- | --------- | --- | --- | --- | --- | --------- | --- |
nghệ phần mềm
| Kiến trúc và  |                         |     |     |                   |     | IT thực hiện; IBS kiểm  |                       |     |
| ------------- | ----------------------- | --- | --- | ----------------- | --- | ----------------------- | --------------------- | --- |
|               | Góp ý điều kiện sử dụng |     |     | Chủ trì hướng dẫn |     |                         |                       |     |
| công nghệ     |                         |     |     |                   |     |                         | tra đáp ứng nghiệp vụ |     |
Phản hồi về tính đúng và khả  Hướng dẫn kiểm thử  Hai sinh viên thực
Kiểm thử
|     |     | năng sử dụng |     |     | kỹ thuật |     |     | hiện |
| --- | --- | ------------ | --- | --- | -------- | --- | --- | ---- |
Tiếp nhận phần nghiệp vụ theo  Hướng dẫn bàn giao  Chuẩn bị sản phẩm và
Bàn giao
|     |     | phân công |     |     | kỹ thuật |     |     | tài liệu |
| --- | --- | --------- | --- | --- | -------- | --- | --- | -------- |
Cần ghi rõ người xác nhận nghiệp vụ và người hướng dẫn kỹ thuật. Một phép tính có
thể chạy đúng theo mã nguồn nhưng vẫn chưa phù hợp nếu quy tắc đầu vào chưa được
xác nhận.
6. Danh mục minh chứng đề nghị Khoa cung cấp
Ngày 1 lập danh mục nhu cầu và đầu mối tiếp nhận; mỗi tài liệu được cập nhật trạng thái
khi thực sự nhận.
| Mã Tài liệu hoặc dữ liệu |     |     |                         | Mục đích |     | Trạng thái ban đầu           |     |     |
| ------------------------ | --- | --- | ----------------------- | -------- | --- | ---------------------------- | --- | --- |
|                          |     |     | Xác định PLO, PI và ma  |          |     | Đã có bản tài liệu; cần xác  |     |     |
MC01 Bản mô tả CTĐT CNTT đã gửi
|                               |                 |     |                           | trận học phần |     | nhận phiên bản áp dụng |     |     |
| ----------------------------- | --------------- | --- | ------------------------- | ------------- | --- | ---------------------- | --- | --- |
| Đề cương chi tiết các học     |                 |     | Xác định CLO và liên kết  |               |     |                        |     |     |
| MC02                          |                 |     |                           |               |     | Đề nghị cung cấp       |     |     |
|                               | phần thử nghiệm |     |                           | chuẩn         |     |                        |     |     |
| Quy trình đo lường chuẩn đầu  |                 |     | Xác định công thức,       |               |     |                        |     |     |
| MC03                          |                 |     |                           |               |     | Đề nghị cung cấp       |     |     |
| ra và hướng dẫn áp dụng       |                 |     | ngưỡng và cách tổng hợp   |               |     |                        |     |     |
Đề kiểm tra, bài tập, yêu cầu
| MC04 |     |     | Xác định nhiệm vụ đánh giá |     |     | Đề nghị cung cấp |     |     |
| ---- | --- | --- | -------------------------- | --- | --- | ---------------- | --- | --- |
đồ án
Xác định tiêu chí và mức
| MC05 Rubric hoặc hướng dẫn chấm |     |     |     |     |     | Đề nghị cung cấp |     |     |
| ------------------------------- | --- | --- | --- | --- | --- | ---------------- | --- | --- |
đánh giá
Bảng điểm theo câu hỏi hoặc
| MC06 |     |     | Thử nghiệm phép tính CLO |     |     | Đề nghị cung cấp |     |     |
| ---- | --- | --- | ------------------------ | --- | --- | ---------------- | --- | --- |
tiêu chí
| Danh sách lớp, học kỳ, mã  |                    |     | Đối chiếu dữ liệu và phạm  |            |     |                  |     |     |
| -------------------------- | ------------------ | --- | -------------------------- | ---------- | --- | ---------------- | --- | --- |
| MC07                       |                    |     |                            |            |     | Đề nghị cung cấp |     |     |
|                            | sinh viên thay thế |     |                            | vi báo cáo |     |                  |     |     |
| Báo cáo đo lường đã thực   |                    |     | So sánh kết quả phần mềm   |            |     |                  |     |     |
| MC08                       |                    |     |                            |            |     | Đề nghị cung cấp |     |     |
|                            | hiện               |     | với kết quả đối chứng      |            |     |                  |     |     |
Hồ sơ hoặc mẫu theo dõi cải
| MC09 |     |     | Thiết kế chức năng cải tiến |     |     | Đề nghị cung cấp |     |     |
| ---- | --- | --- | --------------------------- | --- | --- | ---------------- | --- | --- |
tiến
Mỗi minh chứng nên ghi tối thiểu: mã, tên, phiên bản, nguồn cung cấp, ngày nhận,
phạm vi học phần/học kỳ, quyền sử dụng và tình trạng kiểm tra.
Nếu chỉ nhận được điểm tổng kết học phần, nhóm cần ghi rõ giới hạn: chưa đủ dữ liệu
để đo từng CLO theo câu hỏi hoặc tiêu chí rubric.
7. Công việc cụ thể của hai sinh viên trong ngày 1
Thời
|     | Sinh viên IBS |     |     | Sinh viên IT |     |     | Đầu ra chung |     |
| --- | ------------- | --- | --- | ------------ | --- | --- | ------------ | --- |
lượng
Đọc mục tiêu, xác định nhu  Đọc phạm vi, xác định nội dung  Danh sách câu hỏi
1 giờ
|         | cầu cần hỏi                 |     |                               | kỹ thuật cần hỏi  |     |     |                    | khởi động |
| ------- | --------------------------- | --- | ----------------------------- | ----------------- | --- | --- | ------------------ | --------- |
|         | Trao đổi về người dùng, dữ  |     | Trao đổi về công nghệ, hướng  |                   |     |     |                    |           |
| 1,5 giờ |                             |     |                               |                   |     |     | Ghi chép khởi động |           |
|         | liệu và đầu ra              |     |                               | dẫn và môi trường |     |     |                    |           |
Lập danh mục minh chứng  Khảo sát máy, công cụ và năng  Phiếu khảo sát ban
1,5 giờ
|       | và đầu mối cung cấp          |     |                                  | lực hiện có             |     |     |                | đầu |
| ----- | ---------------------------- | --- | -------------------------------- | ----------------------- | --- | --- | -------------- | --- |
|       | Hoàn thiện phạm vi và trách  |     | Hoàn thiện quyết định công nghệ  |                         |     |     |                |     |
| 1 giờ |                              |     |                                  |                         |     |     | Bảng phân công |     |
|       | nhiệm nghiệp vụ              |     |                                  | và trách nhiệm kỹ thuật |     |     |                |     |

Thời
Sinh viên IBS Sinh viên IT Đầu ra chung
lượng
Soạn biên bản, danh sách Soạn phiếu môi trường, rà soát
1,5 giờ Hồ sơ dự thảo
vấn đề cần xác nhận nội dung kỹ thuật
Trình bày và cập nhật góp ý Trình bày và cập nhật góp ý kỹ Hồ sơ cuối ngày,
1,5 giờ
nghiệp vụ thuật nhật ký cá nhân
Phiếu khảo sát kỹ thuật cần ghi:
• Thiết bị, hệ điều hành, RAM và dung lượng trống.
• Kinh nghiệm Java, Spring Boot, TypeScript, Angular và MongoDB.
• Công cụ đã có và công cụ cần cài.
• Quyền truy cập kho mã, tài liệu và môi trường thử nghiệm.
• Nội dung cần Trung tâm hướng dẫn bổ sung.
Không cần yêu cầu ứng dụng hoàn chỉnh chạy ngay trong ngày 1. Nếu Trung tâm đã có
dự án mẫu, sinh viên IT có thể chạy thử và lưu kết quả làm minh chứng khảo sát.
8. Triển khai và kiểm chứng trong 3 giờ cuối ngày
Khoảng thời
Việc cần hoàn thành Bằng chứng kiểm tra
gian
Hoàn thiện biên bản khởi động và Nêu đúng Spring Boot, Angular,
0–45 phút
quyết định công nghệ MongoDB và vai trò hai đơn vị
Hoàn thiện phân công, danh mục Mỗi việc có người theo dõi; mỗi tài liệu
45–90 phút
minh chứng, phiếu kỹ thuật có trạng thái
Ghi và sửa các nội dung chưa thống
90–120 phút Hai sinh viên đọc chéo hồ sơ
nhất
Ghi nhận góp ý và nội dung cần quyết
120–150 phút Báo cáo người hướng dẫn
định
Cập nhật hồ sơ, nhật ký và việc ngày Lưu phiên bản cuối ngày cùng trạng thái
150–180 phút
2 xác nhận
9. Tiêu chí xác nhận hoàn tất ngày 1
Ngày 1 được xem là hoàn tất về công việc của nhóm khi có đủ:
Sản phẩm Yêu cầu đạt
Biên bản khởi động Có mục tiêu, phạm vi, công nghệ, vai trò và vấn đề cần xác nhận
Bảng phân công Phân biệt rõ phần việc IBS, IT và trách nhiệm phối hợp
Có nguồn từ Khoa CNTT, đầu mối hoặc người theo dõi, trạng thái tiếp
Danh mục minh chứng
nhận
Phiếu khảo sát kỹ thuật Phản ánh đúng điều kiện hiện có và nhu cầu hướng dẫn
Danh sách việc tiếp
Có người phụ trách và mốc xử lý
theo
Nhật ký cá nhân Ghi việc thực hiện, sản phẩm, thời gian và vướng mắc
Với thông tin thầy đã cung cấp, công nghệ chính, nguồn minh chứng và đơn vị hướng
dẫn kỹ thuật đã được xác định. Những nội dung còn cần điền theo thực tế là tên đầu
mối, lịch cấp minh chứng, kết quả khảo sát thiết bị và bộ phiên bản công nghệ do
Trung tâm hướng dẫn.
Ngày 2: IBS tiếp tục lập bảng thuật ngữ và đọc cấu trúc CTĐT; IT phác thảo các thành
phần hệ thống theo Spring Boot–Angular–MongoDB, đồng thời làm quen với cấu hình và
dự án mẫu do Trung tâm cung cấp.