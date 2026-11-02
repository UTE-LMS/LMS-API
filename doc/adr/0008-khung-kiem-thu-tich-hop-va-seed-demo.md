# ADR 0008 — Khung kiểm thử tích hợp trên MongoDB replica set và seed dữ liệu thử nghiệm bằng Spring profile

- **Trạng thái:** ĐỀ XUẤT, chờ sinh viên còn lại review (không thuộc mục chờ GVHD/Khoa).
- **Ngày:** 2026-11-02
- **Người soạn:** Claude Code theo yêu cầu của sinh viên; người chốt: [ĐIỀN]
- **Liên quan:** issue API #52 (khung kiểm thử tích hợp), #17 (seed dữ liệu thử nghiệm); ADR 0002 (không dùng Testcontainers).

## Bối cảnh

Test tích hợp cần transaction đa document nên phải chạy trên MongoDB replica set thật (US-39/AC-2). Các test F01 và F02
đang tự dựng người dùng, MockMvc và dọn dữ liệu theo cách riêng (tên cố định, xóa từng bản ghi). Hậu quả: mỗi feature
mới phải chép lại khối khởi tạo, test dễ để sót dữ liệu rác trong CSDL dùng chung, và ma trận vai trò × phạm vi chỉ
có bản viết tay cho lớp học phần. Issue #17 cần dữ liệu giả có nhãn để demo, nhưng chỉ cho collection đã có mã.

## Quyết định

1. **Không dùng Testcontainers** (giữ ADR 0002). Integration test đọc kết nối từ biến `MONGODB_URI` và chạy trên
   replica set `rs0` do image `docker/mongo/` (dev) hoặc service MongoDB của CI cung cấp. Không thêm dependency.
2. **Lớp nền `IntegrationTestBase`** (`src/test/.../testsupport`): `@SpringBootTest` + MockMvc có Spring Security, mỗi
   test một `ApiTestFixtures`, tự dọn ở `@AfterEach`. `MongoReplicaSetPreconditionIntegrationTest` báo rõ nếu
   `MONGODB_URI` trỏ nhầm vào mongod standalone.
3. **Cô lập dữ liệu bằng tiền tố theo lần chạy**: mọi tên người dùng, mã CTĐT/học phần/lớp do fixture sinh mang `itest`
   cộng một mã ngẫu nhiên của test; cleanup xóa theo tiền tố đó (cộng các bản ghi con theo phiên bản CTĐT và
   `audit_logs` của user fixture). Test không đụng dữ liệu khởi tạo của `database_import.js` và không phụ thuộc thứ tự.
4. **Token ký trực tiếp bằng `JwtEncoder` của ứng dụng** thay vì gọi `/auth/login` cho mỗi người dùng fixture: nhanh
   hơn, không sinh dòng `audit_logs` LOGIN ngoài ý muốn. Test cần đăng nhập thật (US-01) vẫn gọi API login.
5. **Mẫu ma trận tham số hóa `RoleScopeMatrix`**: feature mới khai báo các dòng `(vai trò, kiểu phạm vi, được READ,
   được WRITE)` chép từ bảng quyền trong tài liệu kế hoạch rồi gọi endpoint; giá trị kỳ vọng không lấy từ mã.
6. **Seed demo bằng Spring profile `demo`** (`DemoDataSeeder`, cùng cơ chế `ApplicationRunner` với `InitialAdminSeeder`),
   không dùng script `mongosh` riêng: dùng chung validator, mã hóa mật khẩu và chính sách mật khẩu của ứng dụng;
   không chạy khi profile tắt (bean không tồn tại). Mật khẩu đọc từ `DEMO_USER_PASSWORD`, không có giá trị mặc định;
   idempotent theo khóa tự nhiên; mọi bản ghi mang nhãn "DỮ LIỆU THỬ NGHIỆM"; không đặt ngưỡng, trọng số, tín chỉ,
   mức năng lực hay nguyên văn PLO/PI. `InitialAdminSeeder` được gắn `@Order(1)` để luôn chạy trước seeder demo.
7. **Jackson từ chối số làm thứ tự enum** (`EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS`, `JacksonConfig`). Test #52 phát hiện
   `"contribution": 1` bị hiểu là `R` (và `"status": 1` là `ACTIVE`), trái US-07/AC-2.

## Hệ quả

- Feature sau chỉ viết kịch bản kế thừa `IntegrationTestBase` và khai báo bảng ma trận; không chép lại khối khởi tạo.
- Test vẫn dùng chung một CSDL `learning_outcomes` (không tạo CSDL riêng vì validator nằm trong CSDL đó). Nếu hai lần
  chạy test song song trên cùng CSDL, tiền tố ngẫu nhiên tránh đụng nhau nhưng các assertion đếm toàn cục sẽ không
  chắc chắn; test hiện tại chỉ đếm trong phạm vi dữ liệu của chính nó.
- Dữ liệu rác chỉ còn khi JVM bị giết giữa chừng (không chạy được `@AfterEach`). Chưa có bước quét dọn toàn cục; ghi nhận
  là nợ nhỏ, xử lý bằng cách xóa theo tiền tố `itest` nếu cần.
- Seed demo mới phủ `users`, `programs`, `program_versions`, `plos`, `pis`, `courses` (chỉ bản ghi tối thiểu để minh họa
  ma trận), `course_pi_contributions`. Học phần đầy đủ/đề cương/CLO, bài đánh giá, lớp, sinh viên, điểm và tệp điểm mẫu
  có lỗi cài sẵn **chưa làm** vì chưa có mã tương ứng (F03-F05b) và định dạng tệp của F05b chưa tồn tại.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Testcontainers MongoDB | Đã loại ở ADR 0002 (cần Docker socket, thêm dependency) |
| CSDL riêng cho mỗi lần chạy test | Validator `$jsonSchema` nạp bởi `database_import.js` vào CSDL `learning_outcomes`; tạo CSDL riêng phải nạp lại script mỗi lần |
| `deleteAll()` giữa các test | Xóa luôn dữ liệu khởi tạo và dữ liệu demo của người dùng |
| Script `mongosh` để seed | Phải lặp lại băm mật khẩu và kiểm tra chính sách ngoài ứng dụng; dễ lệch với validator Java |
| Seed chạy ở mọi profile | Dữ liệu giả lọt vào staging/production |
