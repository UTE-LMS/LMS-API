# Dữ liệu thử nghiệm cho demo (issue #17)

> Mọi bản ghi seed mang nhãn **"[DỮ LIỆU THỬ NGHIỆM]"**. Đây là chữ giả để chạy thử API và giao diện, **không phải** nội dung
> CTĐT, PLO, PI hay người dùng thật. Không dùng cho staging/production. Quyết định thiết kế: `doc/adr/0008-khung-kiem-thu-tich-hop-va-seed-demo.md`.

## 1. Cách bật

Seed chỉ chạy khi Spring profile `demo` bật. Không có profile này thì bean `DemoDataSeeder` không tồn tại.

```
# Git Bash (MongoDB replica set đã chạy, xem README.md mục "MongoDB bằng Docker")
export DEMO_USER_PASSWORD='<mật khẩu của bạn, 8 đến 72 byte UTF-8>'
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

```
:: cmd.exe
set DEMO_USER_PASSWORD=<mật khẩu của bạn, 8 đến 72 byte UTF-8>
set SPRING_PROFILES_ACTIVE=demo
mvnw.cmd spring-boot:run
```

| Biến / thuộc tính | Bắt buộc | Ý nghĩa |
|---|---|---|
| `DEMO_USER_PASSWORD` | Có, để tạo người dùng | Mật khẩu chung của các tài khoản demo, băm BCrypt khi lưu. **Không có giá trị mặc định** và không được commit. Thiếu hoặc vi phạm chính sách 8 đến 72 byte thì seeder bỏ qua phần người dùng (vẫn tạo CTĐT/PLO/PI) và ghi cảnh báo. |
| `app.demo.key` | Không | Tiền tố định danh, mặc định `demo`; khớp `[a-z0-9]{2,30}`. Dùng để test seed không đụng bản demo của bạn. |

Seed chạy lại nhiều lần an toàn (idempotent): bản ghi đã có được giữ nguyên, **không ghi đè** nội dung bạn đã sửa.
Nếu bạn cũng đặt `INITIAL_ADMIN_*`, `InitialAdminSeeder` chạy trước (`@Order(1)`) nên tài khoản admin đó vẫn được tạo.

## 2. Nội dung được tạo

| Collection | Bản ghi | Ghi chú |
|---|---|---|
| `programs` | `DEMO-CTDT-THU-NGHIEM` | `level`, `degree` để trống |
| `program_versions` | `DEMO-PHIEN-BAN-THU-NGHIEM`, `DRAFT` | `totalCredits`, `durationYears` trống; `applicableCohorts` rỗng (Q01); xác nhận `NEEDS_CLARIFICATION` |
| `plos` | `PLO1` (displayOrder 2), `PLO2` (displayOrder 1) | Thứ tự hiển thị cố ý ngược mã để minh họa US-06/AC-4. `contentOriginal` trống; `capabilityLevelRaw` trống; nội dung là câu giả có nhãn |
| `pis` | `PI1.1`, `PI1.2` (thuộc `PLO1`), `PI2.1` (thuộc `PLO2`) | `contentOriginal`, `capabilityLevelRaw`, `targetPassRate` trống |
| `courses` | `DEMO-HP-THU-NGHIEM` | `credits` trống. Chỉ để minh họa ma trận; chưa có đề cương, CLO |
| `course_pi_contributions` | I cho `PI1.1`, R cho `PI1.2`, M cho `PI2.1` | Vai trò sư phạm, không phải trọng số (F02 mục 4.6) |
| `users` | 6 tài khoản dưới đây | Email dạng `@demo.invalid` |

| Username | Vai trò | Phạm vi |
|---|---|---|
| `demo.admin` | ADMIN | toàn hệ thống |
| `demo.manager` | PROGRAM_MANAGER | `programIds` = CTĐT mẫu |
| `demo.head` | DEPARTMENT_HEAD | `courseIds` = học phần mẫu |
| `demo.qa` | QA_OFFICER | `programIds` = CTĐT mẫu (xác nhận được PLO/PI mẫu) |
| `demo.lecturer` | LECTURER | `courseIds` = học phần mẫu |
| `demo.viewer` | VIEWER | scope rỗng, chỉ đọc |

Không seed `STUDENT` (NEW-03 là mục tiêu mở rộng; vai trò này hiện bị từ chối trên tài nguyên lớp học phần).

Không có ngưỡng đạt, trọng số, công thức hay quy tắc đo lường nào trong seed (rule 02). Ví dụ số (40/60, 72%...) chỉ được
dùng trong fixture test và ghi "ví dụ minh họa".

## 3. Dọn dữ liệu demo

Dữ liệu demo nằm cùng CSDL `learning_outcomes`. Để xóa, chạy trong `mongosh` (chỉ khi chắc chắn không có dữ liệu thật mang
mã `DEMO-`):

```
db = db.getSiblingDB('learning_outcomes');
const p = db.programs.findOne({ code: 'DEMO-CTDT-THU-NGHIEM' });
if (p) {
  const v = db.program_versions.find({ programId: p._id }).toArray().map(x => x._id);
  db.course_pi_contributions.deleteMany({ programVersionId: { $in: v } });
  db.pis.deleteMany({ programVersionId: { $in: v } });
  db.plos.deleteMany({ programVersionId: { $in: v } });
  db.program_versions.deleteMany({ programId: p._id });
  db.programs.deleteOne({ _id: p._id });
}
db.courses.deleteOne({ code: 'DEMO-HP-THU-NGHIEM' });
db.users.deleteMany({ username: /^demo\./ });
```

Hoặc dựng lại từ đầu bằng `docker rm -f lms-mongo && docker volume rm lms-mongo-data` (README.md).

## 4. Chưa làm được và lý do

| Hạng mục trong issue #17 | Trạng thái | Lý do |
|---|---|---|
| Học phần đầy đủ, đề cương, CLO, liên kết CLO–PI | Chưa | F03 chưa có mã; nội dung CLO phải do người đối chiếu đề cương, không bịa |
| Bài đánh giá (items, ma trận) | Chưa | F04 chưa có mã |
| Học kỳ, lớp, sinh viên (`studentRef`), ghi danh | Chưa | F05a chưa có mã; sẽ dùng mã giả dạng `SV-THU-NGHIEM-001`, không dùng mã sinh viên thật |
| Tệp điểm mẫu CSV/Excel | Chưa | Định dạng và ánh xạ cột của F05b chưa tồn tại; không tạo tệp theo đoán định dạng |
| Tệp điểm có lỗi cài sẵn (điểm vượt thang, mã lạ, trùng, thiếu trường) cho KPI K2 | Chưa | Phụ thuộc mục trên và quy tắc kiểm tra của F05b |
| Quy tắc đo lường mẫu | Không làm | Ngưỡng/trọng số do Khoa xác nhận (câu hỏi mở ở `GiaiThich_CoSoDuLieu.md` §8), không đưa vào seed |

Issue #17 vì vậy chỉ được tham chiếu (`Refs`), chưa đóng.

## 5. Kiểm chứng

`src/test/java/com/hcmute/lms/lmsapi/config/DemoDataSeederTest.java` (profile bật, key ngẫu nhiên riêng, tự dọn) và
`DemoDataSeederProfileOffTest.java` (profile tắt thì không có seeder). Chạy: `./mvnw test -Dtest='DemoDataSeeder*'` trên
MongoDB replica set (README.md).
