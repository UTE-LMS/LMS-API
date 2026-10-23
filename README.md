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
| Docker | bản gần đây | Cách nhanh nhất để có MongoDB replica set kèm dữ liệu ban đầu (xem bên dưới) |
| MongoDB | 8.x, chạy dạng **replica set** | Cần cho transaction khi xác nhận nhập điểm; thiết kế đã thử trên 8.2.6. Image Docker dùng `mongo:8.0` |
| `mongosh` | bản đi kèm MongoDB | Chỉ cần khi cài MongoDB trực tiếp trên máy, không dùng Docker |
| Maven | dùng wrapper `mvnw` đi kèm | Không cần cài riêng |

## Cài đặt và chạy

1. **Cấu hình.** Sao chép file mẫu rồi đặt giá trị thật qua biến môi trường (không commit):

   ```
   copy src\main\resources\application.properties.example src\main\resources\application.properties
   set MONGODB_URI=mongodb://localhost:27017/learning_outcomes?replicaSet=rs0
   ```

   Spring Boot 4 đọc kết nối MongoDB từ `spring.mongodb.uri`. Nếu không đặt `MONGODB_URI`, mặc định là
   `mongodb://localhost:27017/learning_outcomes`.

2. **MongoDB + nạp CSDL ban đầu** — chọn một trong hai cách (xem mục [MongoDB bằng Docker](#mongodb-bằng-docker)
   và [MongoDB cài sẵn trên máy](#mongodb-cài-sẵn-trên-máy)). Dữ liệu khởi tạo gồm 1 chương trình, 7 PLO,
   17 PI, 1 học phần, 4 dòng ma trận I/R/M và một bản quy tắc đo lường nháp (ngưỡng/trọng số để trống).
   Chi tiết: `doc/GiaiThich_CoSoDuLieu.md` §7, §9.

3. **Chạy ứng dụng:**

   ```
   mvnw.cmd spring-boot:run      # Windows
   ./mvnw spring-boot:run        # Linux/macOS
   ```

## MongoDB bằng Docker

Image `lms-mongo` (định nghĩa ở `docker/mongo/`) gói sẵn MongoDB 8 chạy **replica set một node `rs0`** và tự
nạp `doc/database_import.js` mỗi lần container khởi động. Chỉ cần Docker, không cần cài MongoDB hay `mongosh`.

```
docker build -f docker/mongo/Dockerfile -t lms-mongo .
docker run -d --name lms-mongo -p 27017:27017 -v lms-mongo-data:/data/db lms-mongo
```

Sau khoảng 10–20 giây MongoDB sẵn sàng (xem `docker logs lms-mongo`, dòng cuối có `HOÀN TẤT`). Chuỗi kết nối:

```
MONGODB_URI=mongodb://localhost:27017/learning_outcomes?replicaSet=rs0
```

| Việc | Lệnh |
|---|---|
| Tắt / bật lại (dữ liệu giữ nguyên trong volume) | `docker stop lms-mongo` / `docker start lms-mongo` |
| Mở shell vào CSDL | `docker exec -it lms-mongo mongosh "mongodb://localhost:27017/learning_outcomes?replicaSet=rs0"` |
| Xóa sạch và nạp lại từ đầu | `docker rm -f lms-mongo && docker volume rm lms-mongo-data`, rồi `docker run` lại |

Tùy chọn (truyền bằng `-e` khi `docker run`):

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `MONGO_PORT` | `27017` | Cổng MongoDB. **Phải trùng cổng publish** (`-p N:N`), vì replica set quảng bá địa chỉ `localhost:N` cho client. |
| `LOMS_DB` | `learning_outcomes` | Tên CSDL. |
| `LOMS_SAMPLE` | không đặt | Đặt `1` để nạp thêm dữ liệu minh họa (CLO ví dụ của học phần OOP). Mặc định không nạp để không lẫn với dữ liệu chính thức. |

Ví dụ khi cổng 27017 đã bị MongoDB khác trên máy chiếm, dùng 27018:

```
docker run -d --name lms-mongo -e MONGO_PORT=27018 -p 27018:27018 -v lms-mongo-data:/data/db lms-mongo
```

rồi đặt `MONGODB_URI=mongodb://localhost:27018/learning_outcomes?replicaSet=rs0`.

Lưu ý: container **không bật xác thực**, chỉ dùng cho phát triển trên máy cá nhân; không mở cổng này ra mạng ngoài.
Sửa `doc/database_import.js` xong thì build lại image (`docker build` như trên) rồi chạy lại container; script
chạy lại an toàn, chỉ cập nhật validator và chèn dữ liệu khởi tạo còn thiếu.

## MongoDB cài sẵn trên máy

Dành cho trường hợp không dùng Docker. Cần MongoDB 8.x và `mongosh`.

1. Chạy `mongod` với `--replSet rs0`, rồi khởi tạo replica set một lần:

   ```
   mongosh --eval "rs.initiate()"
   ```

2. Nạp CSDL ban đầu (chạy lại nhiều lần được, không xóa dữ liệu; đổi tên CSDL bằng biến `LOMS_DB`):

   ```
   mongosh "mongodb://localhost:27017" --file doc/database_import.js
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
docker/mongo/                          # image MongoDB dev: replica set + nạp CSDL ban đầu
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
