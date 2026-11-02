# Chất lượng mã nguồn API (LMS-API)

Phục vụ Rubric TC2.4 (0 lỗi lint, 0 Blocker/Critical, trùng lặp ≤ 3%) và TC2.5 (coverage lõi ≥ 70%). Lý do chọn công cụ,
giấy phép và phương án khác: [ADR 0005](../adr/0005-cong-cu-chat-luong-api.md). Nợ kỹ thuật liên quan: TD-07, TD-08
(`doc/tech-debt.md`).

## 1. Cổng chất lượng và ngưỡng

`./mvnw -B -ntp verify` chạy đủ các cổng dưới đây theo thứ tự; cổng nào đỏ thì build đỏ (không có `|| true`,
không `continue-on-error`).

| Cổng | Công cụ (phiên bản ghim trong `pom.xml`) | Ngưỡng | Goal gọi riêng |
|---|---|---|---|
| Định dạng/lint | Spotless + google-java-format (`GOOGLE`) | 0 vi phạm | `spotless:check` (sửa: `spotless:apply`) |
| Lỗi tiềm ẩn | SpotBugs, effort `Max`, threshold `Medium`, gồm test | 0 phát hiện | `spotbugs:check` |
| Trùng lặp | PMD CPD, 100 token, gồm `src/main` và `src/test` | 0 khối trùng | `pmd:cpd-check` |
| Coverage lõi | JaCoCo, dòng lệnh | ≥ 70% gộp **và** từng gói `service.impl`, `controller`, `filter`, `config` | `jacoco:check@jacoco-check` (cần `target/jacoco.exec`) |
| Test | JUnit 5 qua Surefire | 100% pass, 0 skip | `test` |
| Sonar | `sonar-maven-plugin` | Quality Gate của SonarCloud | `sonar:sonar` (cần `SONAR_TOKEN`) |

Gọi riêng các cổng tĩnh, **không chạy test**:

```
./mvnw -B -ntp -DskipTests test-compile spotless:check spotbugs:check pmd:cpd-check
```

`test-compile` là bắt buộc. `spotbugs:check` phân tích bytecode; trên cây chưa biên dịch nó in
`No files found to generate report on` rồi **vẫn thành công** (đã kiểm chứng sau `./mvnw clean`), nên nếu thiếu
bước biên dịch cổng này vô hiệu mà không báo đỏ.

### Cách đọc cấu hình chính

- **Phạm vi cổng coverage** nằm ở `<includes>` cấp goal (tên tệp `.class`), không phải trong `<rule>`: `<rule><includes>`
  so với tên gói/bundle dạng dấu chấm nên mẫu dạng đường dẫn không khớp gì. Đây chính là lỗi khiến cổng cũ không bao
  giờ fail (ADR 0005, mục "Phát hiện về JaCoCo").
- **Không tính khung rỗng vào cổng**: chỉ các lớp trong 4 gói lõi được đưa vào `<includes>`. Báo cáo HTML/XML vẫn
  liệt kê toàn bộ 62 lớp có mã để nhìn được cả phần ngoài cổng.
- Chưa có mô-đun đo lường (F06) và nhập điểm (F05b); khi có, thêm gói của chúng vào `<includes>` (không thu hẹp
  ngưỡng).

## 2. Cách chạy từng công cụ

Điều kiện: JDK 25, Docker (MongoDB replica set cho test tích hợp, xem `README.md` gốc), `MONGODB_URI` nếu không dùng
cổng mặc định.

| Việc | Lệnh |
|---|---|
| Toàn bộ cổng + test | `./mvnw -B -ntp verify` |
| Sửa định dạng | `./mvnw spotless:apply` |
| Báo cáo coverage | mở `target/site/jacoco/index.html` (XML cho Sonar: `target/site/jacoco/jacoco.xml`, CSV: `jacoco.csv`) |
| Báo cáo SpotBugs | `target/spotbugsXml.xml` (GUI: `./mvnw spotbugs:gui`) |
| Chi tiết khối trùng CPD | `target/cpd.xml` |
| Phần trăm trùng lặp | `./mvnw -B -ntp pmd:cpd-check; bash doc/quality/cpd-density.sh` (gộp các dòng nằm trong khối trùng, chia cho tổng số dòng; **không phải** số chính thức của Sonar) |
| Chỉ đo mã production | thêm `-Dcpd.includeTests=false` |
| Đẩy lên SonarCloud | `SONAR_TOKEN=<token CI> ./mvnw -B -ntp verify sonar:sonar -Dsonar.organization=<tổ chức> -Dsonar.projectKey=<khóa dự án>` |

Về Sonar: `sonar.organization` và `sonar.projectKey` chưa được xác nhận trong repo nên **không** đặt trong `pom.xml`;
truyền từ CI. Token chỉ qua biến môi trường `SONAR_TOKEN`, không bao giờ commit. Đặt `.git-blame-ignore-revs`:
`git config blame.ignoreRevsFile .git-blame-ignore-revs` (bỏ qua commit định dạng hàng loạt khi `git blame`).

## 3. Kết quả đo thật

Đo ngày 2026-11-02 trên máy Windows, JDK 25.0.1, MongoDB 8.0 replica set bằng Docker (cổng 27018), Maven wrapper.
Mọi số dưới đây lấy từ output của lệnh, không ước lượng.

### 3.1. Trước và sau

| Chỉ số | Trước (nhánh `main`, commit `54ef80e`) | Sau (nhánh này) |
|---|---|---|
| Lệnh | `./mvnw -B -ntp verify` (cổng JaCoCo cũ) | `./mvnw -B -ntp verify` |
| Test | 496 chạy, 0 fail, 0 error, 0 skip | 524 chạy, 0 fail, 0 error, 0 skip (`BUILD SUCCESS`, 3 phút 9 giây; thêm 28 test: 6 của `CorrelationIdFilter`, 22 của `JwtConfig`) |
| Tệp Java lệch format Google | 173/173 | 0 |
| Phát hiện SpotBugs (Max/Medium, gồm test) | 22 (2 ở mã production, 20 ở test) | 0 |
| Khối trùng CPD ≥ 100 token | 18 (đều trong test; 0 ở `src/main`) | 0 |
| Trùng lặp theo dòng (`cpd-density.sh`) | 2,75% tổng (517/18827 dòng; test 4,45%) trên mã gốc; 2,93% (594/20296) sau khi chỉ định dạng lại | 0,00% (0/20364) |
| Coverage dòng lệnh, cổng lõi (gộp) | cổng cũ không đánh giá gì; đo tay 82% | **84,85%** (851/1003 dòng; `service.impl`, `controller`, `filter`, `config`) |
| Coverage dòng lệnh, toàn bộ 62 lớp có mã | 81,2% (1351/1664 dòng) | 81,7% (1483/1816 dòng) |
| Cổng JaCoCo | luôn xanh (vô hiệu) | đỏ khi < 70% (đã kiểm chứng) |

Ghi chú về cột "Trước": số 82% là tính lại từ `jacoco.csv` của chính lần chạy cũ với tập lớp của cổng mới (4 gói), vì cổng
cũ không đo gì. Số 496 test và 81,2% lấy từ lần `verify` trên `main` trước khi sửa.

Mẫu số coverage tăng từ 1664 lên 1816 dòng **không phải do thêm mã production** (chỉ thêm mã ở
`CorrelationIdFilter`): google-java-format ngắt các biểu thức dài thành nhiều dòng và JaCoCo đếm theo dòng nguồn có
mã, nên cùng một mã cho số dòng khác. So sánh trước/sau theo phần trăm, không theo số dòng tuyệt đối.

### 3.2. Coverage theo gói (lần chạy cuối)

Nguồn: `target/site/jacoco/jacoco.csv`, cột `LINE_MISSED`/`LINE_COVERED`. Cột "Cổng" ghi gói có bị cổng 70% áp hay không.

| Gói | Dòng phủ / tổng | % | Cổng |
|---|---|---|---|
| `config` | 160 / 160 | 100,0 | có |
| `controller` | 75 / 98 | 76,5 | có |
| `filter` | 23 / 23 | 100,0 | có |
| `service.impl` | 593 / 722 | 82,1 | có |
| `dto` | 50 / 64 | 78,1 | không |
| `enums` | 38 / 38 | 100,0 | không |
| `exception` | 306 / 402 | 76,1 | không |
| `mapper.impl` | 140 / 187 | 74,9 | không |
| `repository.impl` | 43 / 56 | 76,8 | không |
| `document` | 4 / 8 | 50,0 | không |
| `util` | 29 / 31 | 93,5 | không |
| `validator` | 21 / 22 | 95,5 | không |
| gói gốc và `service` (interface) | 1 / 5 | 20,0 | không |

Lớp lõi thấp nhất (đo riêng từng lớp, không bị cổng áp): `CoursePiContributionController` 50,0% (4/8), `PloServiceImpl` 57,4%
(39/68), `PiController` và `PloController` 62,5% (10/16), `PiServiceImpl` 63,6% (49/77). Đây là mã F02, nên bổ sung test
khi F02 được rà soát. `JwtConfig` đã từ 43,3% (13/30) lên 100% nhờ `JwtConfigTest`.

### 3.3. Kiểm chứng rằng cổng thật sự chặn

| Thử nghiệm cố ý | Kết quả |
|---|---|
| Đặt ngưỡng JaCoCo 0,99 với cấu hình **cũ** và `haltOnFailure=true` | `BUILD SUCCESS` (cổng cũ vô hiệu) |
| Đặt ngưỡng 0,99 với cấu hình **mới** | `BUILD FAILURE`: bundle 0,82; `config` 0,89; `service.impl` 0,81; `controller` 0,73 |
| Cấu hình mới, ngưỡng 0,70 | `BUILD SUCCESS` |
| Sửa `public final class ObjectIdUtils {` thành `...ObjectIdUtils{` rồi `spotless:check` | `BUILD FAILURE`, liệt kê `ObjectIdUtils.java` và dòng sai |
| Chèn `id.trim();` (bỏ giá trị trả về) vào `ObjectIdUtils.requireValid` rồi `test-compile spotbugs:check` | `BUILD FAILURE`: `RV_RETURN_VALUE_IGNORED` (High) và null-check sau khi đã dereference (Medium), "failed with 2 bugs" |
| Sao chép `PloServiceImpl` thành `PloServiceImplCopy` rồi `pmd:cpd-check` | `BUILD FAILURE`: "Found 123 lines of duplicated code", "CPD 7.17.0 has found 1 duplication" |
| `./mvnw verify` khi một tệp test vừa sửa chưa chạy `spotless:apply` (xảy ra thật trong phiên làm việc) | `BUILD FAILURE` ở `spotless-check`, dù 502 test đã pass |

Mọi thay đổi thử nghiệm trên đã được hoàn tác (`git status` sạch) trước khi commit.

## 4. Tương thích Java 25 (TD-03)

Không đổi phiên bản Java/Spring (chờ Trung tâm CNPM). Kết quả chạy thật trên JDK 25.0.1:

| Công cụ | Kết quả |
|---|---|
| JaCoCo 0.8.15 | Chạy tốt, sinh HTML/XML/CSV |
| SpotBugs 4.10.5 (plugin 4.10.5.0) | Chạy tốt trên bytecode Java 25 |
| PMD 7.17.0 (CPD, qua maven-pmd-plugin 3.28.0) | Chạy tốt trên mã Java 25 |
| Spotless 3.10.4 + google-java-format 1.36.1 | Chạy tốt; Spotless in cảnh báo `sun.misc.Unsafe::staticFieldBase` (terminally deprecated) |
| google-java-format 1.37.0 | **Không chạy**: `InvocationTargetException` ở bước format cùng Spotless 3.10.4; 1.33.0, 1.35.0, 1.36.1 và 2.0.0 chạy. Ghim 1.36.1 (2.0.0 phát hành 2026-10-30, quá mới) |
| Linux (eclipse-temurin:25-jdk trong Docker, bản clone git từ bundle của commit `fd88bab`) | `test-compile spotless:check spotbugs:check pmd:cpd-check`: Spotless 175 tệp sạch, SpotBugs 0 phát hiện, CPD `BUILD SUCCESS` (2 phút 3 giây gồm tải phụ thuộc). Lưu ý: giải nén `git archive` vào thư mục không có `.git` thì Spotless báo mọi tệp sai kiểu xuống dòng, nên chạy trong bản clone (như `actions/checkout`) |
| sonar-maven-plugin 5.8.0.7211 | **Chưa chạy** (cần SonarCloud và token). Không có số liệu Sonar trong tài liệu này |

## 5. Chưa đo được / giới hạn

- **Blocker/Critical theo Sonar và trùng lặp theo Sonar: chưa đo.** Cần tổ chức SonarCloud và `SONAR_TOKEN`. Hai số
  thay thế (SpotBugs = 0, CPD = 0 khối trùng) là bằng chứng gần, không thay thế Quality Gate của Sonar.
- Quy tắc đặt tên, độ phức tạp, code smell của Sonar chưa kiểm; Spotless chỉ đảm bảo định dạng Google.
- Lombok sinh getter/builder nằm trong mẫu số coverage (chưa bật `lombok.addLombokGeneratedAnnotation`).
- Một số lớp lõi vẫn dưới 70% khi xét riêng từng lớp (cổng áp ở mức gộp và từng gói, không áp từng lớp).
