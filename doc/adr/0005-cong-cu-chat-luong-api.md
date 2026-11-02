# ADR 0005 — Công cụ chất lượng mã nguồn cho API (lint, phân tích tĩnh, trùng lặp, coverage)

- **Trạng thái:** ĐỀ XUẤT, **chờ hai sinh viên xác nhận** (convention là dự thảo, xem `coding-convention.md`).
- **Ngày:** 2026-11-02
- **Người soạn:** Claude Code theo yêu cầu của sinh viên TinMH; sinh viên rà soát và ghi tên người chốt: [ĐIỀN]
- **Liên quan:** issue #11 (TD-07), #12 (TD-08), #15 (TD-01, TD-03). Cách chạy và số đo thật: `doc/quality/README.md`.

## Bối cảnh

Rubric TC2.4 (Mức 5) yêu cầu 0 lỗi lint, 0 Blocker/Critical, trùng lặp ≤ 3%; TC2.5 yêu cầu coverage dòng lệnh mô-đun
lõi ≥ 70% có báo cáo. Trước ADR này API chưa có linter, phân tích tĩnh hay đo trùng lặp, và cổng JaCoCo có sẵn trong
`pom.xml` **không bao giờ fail** (xem mục "Phát hiện về JaCoCo"). Quy tắc cứng của repo cấm tắt rule, cấm
`@SuppressWarnings` để cho qua, cấm hạ ngưỡng. Convention đã công bố là Google Java Style
(`coding-convention.md` §2); ADR này chỉ chọn công cụ bám convention đó, không đổi convention.

## Phương án đã cân nhắc

| Nhu cầu | Chọn | Phương án khác | Lý do chọn / không chọn |
|---|---|---|---|
| Format + lint kiểu Google | **Spotless** (`spotless-maven-plugin` 3.10.4) + **google-java-format 1.36.1** | Checkstyle `google_checks.xml` | google-java-format *là* bộ định dạng của Google Java Style nên "đúng convention" được định nghĩa bằng chính công cụ, `spotless:apply` sửa tự động, `spotless:check` làm cổng. Checkstyle bản Google bắt buộc Javadoc cho phương thức public, mâu thuẫn quy tắc "không comment nhắc lại điều mã đã nói" (`coding-convention.md` §1); gỡ các rule Javadoc đó là tự sửa convention. Không chọn Checkstyle để tránh thêm công cụ thứ hai chồng lấn. Quy tắc đặt tên/độ phức tạp sẽ do Sonar kiểm (xem dưới). |
| Lỗi tiềm ẩn trên bytecode | **SpotBugs** (`spotbugs-maven-plugin` 4.10.5.0, SpotBugs 4.10.5), effort `Max`, threshold `Medium`, gồm cả test | PMD (rule set), Error Prone | SpotBugs được `.claude/rules/03` nêu tên, chạy trên bytecode, không cần processor trong trình biên dịch (Error Prone cần `-Xplugin`/`--add-exports` với Lombok và JDK 25, rủi ro cao). Threshold `Medium` là mức mặc định hợp lý; `Low` chủ yếu là cảnh báo phong cách. |
| Trùng lặp mã | **PMD CPD** (`maven-pmd-plugin` 3.28.0, goal `cpd-check`, PMD 7.17.0), 100 token | Chỉ dựa vào SonarCloud | Chạy offline được, có số đo ngay trên máy và trong CI, không cần token. Sonar vẫn là số đo chính thức của Rubric (xem "Giới hạn"). CPD chỉ báo khối trùng, không báo phần trăm; script `doc/quality/cpd-density.sh` đổi ra phần trăm. |
| Blocker/Critical, smell, security hotspot | **SonarCloud** qua `sonar-maven-plugin` 5.8.0.7211 | SonarQube tự dựng | Không phải duy trì máy chủ; token chỉ qua biến môi trường `SONAR_TOKEN` trong CI. Chưa chạy được trong phiên này (cần token và tổ chức SonarCloud), xem "Giới hạn". |
| Coverage | **JaCoCo** 0.8.15 (đã có) | Cobertura, IntelliJ coverage | Đã có sẵn, hỗ trợ Java 25 (đã chạy thật), sinh HTML/XML/CSV cho Sonar. Chỉ sửa cấu hình cổng. |

## Giấy phép (công cụ chạy lúc build, không đóng gói vào ứng dụng)

| Công cụ | Giấy phép | Nguồn kiểm |
|---|---|---|
| spotless-maven-plugin | Apache-2.0 | `<licenses>` trong POM trên Maven Central |
| google-java-format | Apache-2.0 | Dự án google/google-java-format (POM cha không khai `<licenses>`) |
| spotbugs-maven-plugin | Apache-2.0 | POM |
| SpotBugs | LGPL-2.1 | POM `spotbugs-4.10.5` |
| maven-pmd-plugin / PMD | Apache-2.0 / BSD-style | Dự án Apache Maven, PMD (POM không khai `<licenses>` trực tiếp) |
| sonar-maven-plugin | LGPL-3.0 | Trang dự án SonarScanner for Maven (POM không khai `<licenses>`, **chưa xác minh trong POM**) |
| jacoco-maven-plugin | EPL-2.0 | Dự án JaCoCo (POM không khai `<licenses>` trực tiếp) |

Mọi mục "dự án" nêu trên là kiến thức về dự án, không phải kết quả đọc POM; hai sinh viên nên kiểm lại trước khi nộp.
Không có công cụ nào là phụ thuộc runtime nên không ảnh hưởng giấy phép của sản phẩm.

## Quyết định

1. **Format/lint:** Spotless + google-java-format `GOOGLE` (2 dấu cách, 100 cột, import có thứ tự, bỏ import thừa)
   cho `src/main/java` và `src/test/java`. Gắn `spotless:check` vào pha `verify`. Sửa bằng `./mvnw spotless:apply`.
2. **Phân tích tĩnh:** `spotbugs:check` (Max/Medium, gồm test) gắn vào `verify`. Không dùng filter loại trừ,
   không `@SuppressFBWarnings`; mọi phát hiện đã được sửa trong mã.
3. **Trùng lặp:** `pmd:cpd-check` (100 token) gắn vào `verify`, áp cho cả `src/main` và `src/test`, 0 khối trùng
   cho phép (chặt hơn ngưỡng 3% của Rubric). Ban đầu test có 18 khối trùng (2,75% tổng số dòng); đã tách helper dùng
   chung (`support/ApiTestSupport`) để về 0. Chỉ đo mã production: `-Dcpd.includeTests=false`.
4. **Coverage:** cổng JaCoCo `haltOnFailure=true`, ≥ 70% dòng lệnh cho gói lõi `service.impl`, `controller`, `filter`,
   `config`, ở cả hai mức: gộp (BUNDLE) và từng gói (PACKAGE).
5. **Sonar:** `sonar-maven-plugin` được ghim phiên bản, `sonar.host.url` và đường dẫn báo cáo JaCoCo đặt trong
   `pom.xml`; `sonar.organization`, `sonar.projectKey` và `SONAR_TOKEN` truyền từ CI, không commit.
6. **Không đổi Java/Spring** (TD-03 chờ Trung tâm CNPM). Chỉ ghi nhận: các công cụ trên chạy được với JDK 25
   (xem `doc/quality/README.md`). Riêng google-java-format 1.37.0 **không** chạy được cùng Spotless 3.10.4 trong môi
   trường này (`InvocationTargetException`), nên ghim 1.36.1.

## Phát hiện về JaCoCo (lỗi có sẵn, đã sửa)

Cổng JaCoCo cũ đặt mẫu dạng đường dẫn lớp (`com/hcmute/.../service/impl/**`) trong `<rule><includes>`. Ở đó JaCoCo
so với **tên gói/bundle dạng dấu chấm** nên mẫu không khớp gì và không có quy tắc nào được đánh giá. Kiểm chứng: đặt
`minimum=0.99`, `haltOnFailure=true` vẫn `BUILD SUCCESS`. Cổng mới đưa việc chọn lớp lên `<includes>` cấp goal (khớp
tên tệp `.class`) và đã kiểm cả hai chiều: ngưỡng 0,99 làm build fail, ngưỡng 0,70 qua.

## Giới hạn và rủi ro đã biết

- **Chưa có số đo Sonar.** Blocker/Critical và trùng lặp "chính thức" chỉ có sau khi CI đẩy lên SonarCloud. Trong
  khi chờ, đã dùng SpotBugs (0 phát hiện) và CPD (0 khối trùng ở `src/main`) làm số đo thay thế, ghi rõ là thay thế.
- CPD tính khối ≥ 100 token, Sonar dùng ngưỡng riêng (khoảng 10 câu lệnh/100 token cho Java); số hai bên có thể
  lệch nhẹ.
- Cổng CPD 0 khối trùng ở 100 token có thể gây vướng khi viết test mới (dễ sao chép khung test); cách xử lý là tách
  helper như `ApiTestSupport`. Nếu hai sinh viên thấy quá chặt, đổi `cpd.includeTests` và ghi lý do, không hạ ngưỡng
  im lặng.
- Lombok sinh mã getter/builder làm tăng mẫu số coverage; chưa bật `lombok.addLombokGeneratedAnnotation` để không
  "làm đẹp" số đo. Cân nhắc sau nếu hai sinh viên đồng ý.
- Spotless dùng `Unsafe` nội bộ trên JDK 25 (cảnh báo `sun.misc.Unsafe::staticFieldBase` ở console). Chưa gây lỗi;
  theo dõi khi nâng Spotless.
- Trên Windows tệp nguồn có thể là CRLF còn trong git là LF (`core.autocrlf=true`). Spotless mặc định lấy kiểu xuống dòng
  từ git/nền tảng nên `check` đạt trên cả Windows lẫn Linux (đã kiểm chứng trên Linux, xem `doc/quality/README.md` mục 4).
  Hệ quả: `spotless:check` phải chạy trong một bản **clone git** (như `actions/checkout`); trên thư mục không có `.git`
  (ví dụ `git archive` giải nén vào image Docker) mọi tệp bị báo sai xuống dòng. Bước build Docker nên dùng
  `package -DskipTests`, không dùng `verify`.

## Hệ quả

- `./mvnw -B -ntp verify` thất bại khi: sai format, có phát hiện SpotBugs, có khối trùng ≥ 100 token ở `src/main` hoặc `src/test`,
  coverage lõi < 70% (gộp hoặc từng gói), hoặc test đỏ.
- Gọi riêng các cổng (không chạy test) phải có `test-compile`, vì `spotbugs:check` trên cây chưa biên dịch báo "No
  files found" rồi **vẫn thành công**: `./mvnw -B -ntp -DskipTests test-compile spotless:check spotbugs:check
  pmd:cpd-check`.
- Đã tái định dạng toàn bộ 173 tệp Java sang Google Java Style (2 dấu cách). `git blame` bị ảnh hưởng; dùng
  `.git-blame-ignore-revs` (đã thêm) và `git config blame.ignoreRevsFile .git-blame-ignore-revs`.
- Nhánh khác đang sửa tệp Java sẽ xung đột với commit định dạng; nên merge PR này sớm rồi cập nhật các nhánh đó
  (`./mvnw spotless:apply` sau khi rebase).
- Một thay đổi hành vi có chủ đích do SpotBugs phát hiện: `CorrelationIdFilter` chỉ chấp nhận `X-Correlation-Id` từ
  client nếu gồm `[A-Za-z0-9._-]`, dài 1..128; giá trị khác bị thay bằng UUID mới (chống response splitting/log
  injection). `API-CONTRACT.md` của UI nên ghi rõ giới hạn này.
- Gỡ `spring-boot-starter-websocket` và `spring-boot-starter-mail` (+ test starter): không có mã nào dùng; F08 loại
  trừ gửi email. Nếu về sau cần email hoặc WebSocket thì thêm lại kèm ADR riêng.
