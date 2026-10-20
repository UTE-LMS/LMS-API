# LMS-API

Backend của **Hệ thống quản lý, đo lường và phân tích chuẩn đầu ra chương trình đào tạo** (Learning Outcomes
Management and Analytics System, LOMS). Tiểu luận chuyên ngành CNTT, Trường HCM-UTE.

Hệ thống tiếp nhận kết quả có sẵn sau khi thi (điểm theo từng câu hỏi/tiêu chí) và đo lường mức đạt theo chuỗi
*câu hỏi/tiêu chí → CLO → PI → PLO → báo cáo → hành động cải tiến*, có phiên bản và truy vết về dữ liệu gốc.
Không tổ chức thi, không sinh đề, không lưu bài làm của sinh viên.

> **Trạng thái:** đang phát triển (khung dự án). Chưa có API nghiệp vụ.

## Công nghệ

Java, Spring Boot 4.1.0 (Maven), Spring Data MongoDB, Spring Security, Bean Validation, MongoDB (replica set).
Frontend: repo riêng `LMS-UI` (Angular 21 + TypeScript).

## Yêu cầu môi trường

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| JDK | 25 (đang cấu hình trong `pom.xml`) | Chờ xác nhận bộ phiên bản với Trung tâm CNPM |
| MongoDB | 8.x, chạy dạng **replica set** | Cần cho transaction khi xác nhận nhập điểm; thiết kế đã thử trên 8.2.6 |
| `mongosh` | bản đi kèm MongoDB | Để nạp CSDL ban đầu |
| Maven | dùng wrapper `mvnw` đi kèm | Không cần cài riêng |

## Cài đặt và chạy

1. **Cấu hình.** Sao chép file mẫu rồi đặt giá trị thật qua biến môi trường (không commit):

   ```
   copy src\main\resources\application.properties.example src\main\resources\application.properties
   set MONGODB_URI=mongodb://localhost:27017/learning_outcomes?replicaSet=rs0
   ```

   Spring Boot 4 đọc kết nối MongoDB từ `spring.mongodb.uri`. Nếu không đặt `MONGODB_URI`, mặc định là
   `mongodb://localhost:27017/learning_outcomes`.

2. **MongoDB replica set (dev, một node).** Chạy `mongod` với `--replSet rs0`, rồi khởi tạo một lần:

   ```
   mongosh --eval "rs.initiate()"
   ```

3. **Nạp CSDL ban đầu** (chạy lại nhiều lần được, không xóa dữ liệu; đổi tên CSDL bằng biến `LOMS_DB`):

   ```
   mongosh "mongodb://localhost:27017" --file doc/database_import.js
   ```

   Dữ liệu khởi tạo gồm 1 chương trình, 7 PLO, 17 PI, 1 học phần, 4 dòng ma trận I/R/M và một bản quy tắc đo
   lường nháp (ngưỡng/trọng số để trống). Chi tiết: `doc/GiaiThich_CoSoDuLieu.md` §7, §9.

4. **Chạy ứng dụng:**

   ```
   mvnw.cmd spring-boot:run      # Windows
   ./mvnw spring-boot:run        # Linux/macOS
   ```

## Kiểm thử và build

```
mvnw.cmd verify      # build + test
```

Hiện chỉ có một test khởi động ngữ cảnh (`LmsApiApplicationTests`); test nghiệp vụ sẽ được bổ sung theo
`doc/ke-hoach-xay-dung.md`. Kết quả CI và coverage sẽ được công bố khi pipeline đầy đủ.

## Cấu trúc

```
src/main/java/com/hcmute/lms/lmsapi/   # mã nguồn (dự kiến chia module theo chức năng)
src/main/resources/                    # cấu hình (chỉ commit *.example)
doc/                                   # tài liệu thiết kế, kế hoạch, hồ sơ đồ án
logsAI/                                # nhật ký sử dụng AI (Rubric TC2.3)
.claude/rules/, CLAUDE.md              # quy tắc làm việc cho trợ lý AI
```

## Tài liệu chính

- `doc/GiaiThich_CoSoDuLieu.md` — thiết kế CSDL MongoDB (29 collection, 14 nguyên tắc).
- `doc/ke-hoach-xay-dung.md` — kế hoạch, backlog, lộ trình, rủi ro.
- `doc/cam-ket-san-pham-metric.md` — bản cam kết Bước 3 (dự thảo).
- `doc/coding-convention.md`, `doc/tech-debt.md`, `doc/rubric-checklist.md`.
- `doc/BS.md`, `doc/Misa.md` — kế hoạch và hướng dẫn đọc CTĐT.

## Quy trình đóng góp

Nhánh theo tác vụ (`feat/…`, `fix/…`), commit theo Conventional Commits, mở Pull Request, **sinh viên còn lại review**
trước khi merge vào `main`. Không commit secret hay dữ liệu sinh viên thật. Hai sinh viên cùng làm; xem
`doc/quy-trinh/phan-cong.md`.
