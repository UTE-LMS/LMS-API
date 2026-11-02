# ADR 0006 — CI/CD ≥ 6 chặng, đóng gói Docker và chuẩn bị staging

- **Trạng thái:** ĐỀ XUẤT (PROPOSED). Phần pipeline và đóng gói đã chạy thử cục bộ (mục 5); phần triển khai staging
  chỉ là **khung chờ nền tảng** (Q06, Q07), chưa chạy trên GitHub và chưa triển khai lên máy chủ nào.
- **Ngày:** 2026-11-02
- **Issue:** API #13 (CI ≥ 6 chặng, xử lý TD-09 phần API), API #14 (staging, health check, log, cảnh báo, rollback).
- **Phạm vi:** `.github/workflows/ci.yml`, `.github/actions/`, `Dockerfile`, `.dockerignore`, `deploy/staging/`,
  `.gitleaksignore`, `doc/deploy/staging.md`. Không đổi `pom.xml`.

## 1. Bối cảnh

Rubric TC2.6 / G6 đòi pipeline ≥ 6 chặng (build; lint + phân tích tĩnh; test + cổng coverage; quét bảo mật; đóng gói
Docker + gắn phiên bản; triển khai staging) và `.claude/rules/04-testing-and-cicd.md` cấm làm giả "xanh". CI cũ chỉ có
một job (`build-test`) dựng MongoDB replica set `rs0` rồi chạy `./mvnw verify`. Chưa có Dockerfile ứng dụng, chưa có
quét bảo mật, chưa có chỗ triển khai.

## 2. Quyết định

### QĐ-1. Chia pipeline thành các job riêng, phụ thuộc rõ ràng

| Job | Chặng | Phụ thuộc | Nội dung |
|---|---|---|---|
| `build` | 1 | – | `./mvnw -DskipTests package`, tải JAR lên artifact |
| `lint` | 2 | build | `./mvnw -DskipTests test-compile spotless:check spotbugs:check pmd:cpd-check`; Sonar chỉ khi có `SONAR_TOKEN` |
| `test` | 3 | build | MongoDB 8.0 replica set `rs0` + `./mvnw verify` (cổng JaCoCo ≥ 70% khai trong `pom.xml`, hiện `haltOnFailure=false` nên chưa chặn); tải báo cáo JaCoCo và surefire |
| `secret-scan` | 4a | – | gitleaks CLI quét toàn bộ lịch sử Git |
| `dependency-scan` | 4b | build | Trivy `rootfs` trên JAR, fail khi có HIGH/CRITICAL đã có bản vá |
| `package-image` | 5 | lint, test, secret-scan, dependency-scan | build image, Trivy quét image, đẩy GHCR (chỉ main/tag) |
| `deploy-staging` | 6 | package-image | khung SSH + `deploy.sh`; chỉ main và khi `STAGING_ENABLED == 'true'` |
| `smoke-staging`, `rollback-staging` | 6b, 6c | deploy-staging | smoke qua HTTP; rollback nếu smoke đỏ |

Quy tắc áp dụng cho mọi job: không `continue-on-error`, không `|| true`; lỗi ở job nào thì các job phụ thuộc bị
bỏ qua (skipped) và workflow đỏ; `timeout-minutes` cho mỗi job; `permissions: {}` ở cấp workflow, mỗi job tự khai quyền
tối thiểu (`contents: read`, riêng `package-image` thêm `packages: write`, `deploy-staging` thêm `packages: read`);
secret chỉ qua `secrets.*`.

Chặng 1 và 3 tách riêng (đáng lẽ có thể gộp) để có một cổng biên dịch rẻ chạy trước và để báo cáo JaCoCo là artifact
độc lập. `lint` và `test` chạy song song để rút thời gian (mục tiêu commit → image ≤ 15 phút, **chưa đo**).

### QĐ-2. Quét secret bằng gitleaks CLI, không dùng gitleaks-action

`gitleaks/gitleaks-action` đòi license trả phí với repo thuộc tổ chức GitHub (repo này thuộc `UTE-LMS`). Dùng binary
gitleaks 8.30.1 tải từ bản phát hành, **ghim phiên bản và kiểm SHA-256** trong workflow. trufflehog OSS là phương án
dự phòng, chưa dùng.

Lần chạy thử trên lịch sử `origin/main` (38 commit) báo 3 phát hiện, đều là dương tính giả có thể giải thích: khóa HMAC thử
nghiệm trong `ci.yml` cũ và hai JWT giả có chữ ký cố ý sai trong `AuthIntegrationTest`. Chúng được cho qua bằng
`.gitleaksignore` **theo fingerprint** (không dùng allowlist theo đường dẫn hay regex, để không che secret thật về
sau). CI mới không còn ghi cứng khóa JWT: sinh ngẫu nhiên mỗi lần chạy. Hệ quả: nhánh khác có JWT giả tương tự ở commit khác
(thấy 2 phát hiện trên `feat/f01-user-auth-and-authorization`) sẽ đỏ ở chặng này cho tới khi thêm fingerprint
tương ứng sau khi người dùng xác nhận.

### QĐ-3. Quét phụ thuộc bằng Trivy, chưa dùng OWASP Dependency-Check

OWASP Dependency-Check cần NVD API key để tải cơ sở dữ liệu trong thời gian chấp nhận được; chưa có key nên không đưa vào
như một cổng bắt buộc (một cổng hay đỏ vì hết hạn mức NVD sẽ dạy nhóm bỏ qua nó). Trivy dùng cơ sở dữ liệu công khai,
không cần key. Khi có key, có thể bổ sung Dependency-Check như một job song song (xem mục 6).

Quét **JAR đã build** bằng `trivy rootfs`, không phải `trivy fs`:

- `trivy fs` trên thư mục chứa JAR báo "không có file được hỗ trợ" và thoát mã 0 (**xanh giả**, đã gặp khi chạy thử);
  `trivy rootfs` phân tích JAR và tìm ra 11 lỗ hổng HIGH/CRITICAL trên cùng JAR.
- `trivy fs` trên `pom.xml` bị Maven Central trả 429 (Trivy tải BOM từ xa); quét JAR không phụ thuộc mạng Maven.

Cùng JAR đó được quét lần nữa trong image (chặng 5) cùng lớp hệ điều hành.

### QĐ-4. Cài Trivy bằng binary ghim checksum, không dùng trivy-action

Giảm một thành phần bên thứ ba chạy trong job có quyền ghi gói. Composite action
`.github/actions/install-trivy` tải `trivy_0.76.1_Linux-64bit.tar.gz`, kiểm SHA-256, rồi chạy. Muốn nâng phiên bản phải đổi
cả version và sha256. (Không dựa trên một sự cố cụ thể nào; đây là giảm bề mặt, không phải phản ứng với sự cố.)

### QĐ-5. Ghim action theo tag major; chưa ghim SHA

Theo yêu cầu, mọi action dùng tag major cụ thể (`actions/checkout@v7`, `actions/setup-java@v6`,
`actions/upload-artifact@v7`, `actions/download-artifact@v8`, `docker/setup-buildx-action@v4`,
`docker/metadata-action@v6`, `docker/build-push-action@v7`, `docker/login-action@v4`). Đã đối chiếu `action.yml` của
`checkout`, `setup-java`, `build-push-action`, `upload-artifact`: cả bốn chạy Node 24; các action còn lại chưa đối chiếu. Tag là mutable. Khuyến nghị mở rộng sau: ghim commit SHA
và bật Dependabot cho `github-actions` (chưa làm, cần quyết định của nhóm).

### QĐ-6. Đóng gói và đánh phiên bản image

- `Dockerfile` đa tầng: build `eclipse-temurin:25-jdk-alpine`, chạy `eclipse-temurin:25-jre-alpine`, user `app` (UID 10001),
  `HEALTHCHECK` gọi `/actuator/health`, cấu hình hoàn toàn qua biến môi trường, không bake secret. Image dựng từ
  `application.properties.example` (file `application.properties` thật bị `.dockerignore` loại).
- Tag: `sha-<7 ký tự commit>` (bất biến; dùng để deploy và rollback) và `<YYYYMMDD>-<7 ký tự commit>`; push tag `vX.Y.Z` thì
  tag phiên bản là `X.Y.Z`. Không có `latest`, vì rollback cần trỏ đúng một bản.
- Thứ tự: build vào daemon cục bộ → Trivy quét image (fail khi HIGH/CRITICAL đã có bản vá) → mới đăng nhập GHCR và đẩy.
  PR và nhánh khác main chỉ build + quét, không đẩy. Giá trị `info.app.version` của image là chuỗi phiên bản
  (`INFO_APP_VERSION`), để smoke test đối chiếu image đang chạy với commit vừa deploy.
- Đã tắt provenance/SBOM attestation của buildx (`provenance: false`) để image `load` được vào daemon và manifest đơn giản;
  chưa bật SBOM (xem mục 6).

### QĐ-7. Staging: chuẩn bị sẵn, không bịa nền tảng

Chưa chọn nền tảng (Q06, Q07), nên **không có URL, host, hay số lần deploy nào được ghi**. Đã chuẩn bị:

- `deploy/staging/docker-compose.yml`: ứng dụng + MongoDB 8.0 replica set `rs0` một node (volume, healthcheck, xoay log
  `json-file`). Không dùng image `docker/mongo` vì nó quảng bá thành viên replica set là `localhost:<cổng>` (đúng cho dev
  cùng máy, sai khi ứng dụng ở container khác); ở đây thành viên là `mongo:27017` và dịch vụ `mongo-init` nạp
  `doc/database_import.js`. Cổng MongoDB không publish ra máy chủ.
- `deploy.sh` (kéo tag, `up -d`, đợi `/actuator/health` UP có giới hạn, tự rollback), `rollback.sh` (giữ `previous_tag`),
  `smoke.sh` (kiểm health, version, endpoint quản trị không mở, API cần token).
- Job `deploy-staging` giả định tạm "máy chủ Linux có Docker, truy cập SSH" và tham số hóa bằng variable/secret. Nếu nền tảng được
  chọn khác (PaaS, Kubernetes…), chỉ thay nội dung job này và `deploy/staging/`; các chặng 1-5 không đổi.
- `/actuator/health` ở staging chỉ trả UP/DOWN (`MANAGEMENT_ENDPOINT_HEALTH_SHOWDETAILS=never`). Mẫu cấu hình đang để `always`,
  nên health công khai lộ tên cơ sở dữ liệu và dung lượng đĩa (đã xác nhận khi chạy thử).
- Log: `logging.structured.format.console=ecs` qua biến môi trường, đã chạy xác nhận (JSON một dòng một sự kiện, có
  `correlationId`). Bật mặc định trong compose staging, không đổi `application.properties.example`.

### QĐ-8. Sonar có điều kiện

Bước Sonar chỉ chạy khi có `secrets.SONAR_TOKEN`. Không có token (hiện tại) thì bước này bị bỏ qua và ghi chú "chưa quét Sonar" vào job summary.
Không được khai báo là đã quét Sonar cho tới khi có lần chạy thật với token.

## 3. Phương án đã cân nhắc

| Chủ đề | Chọn | Không chọn | Lý do |
|---|---|---|---|
| Quét secret | gitleaks CLI ghim checksum | gitleaks-action | đòi license với repo tổ chức |
| | | trufflehog OSS | dự phòng; gitleaks đã chạy được và dễ ghi ngoại lệ theo fingerprint |
| Quét phụ thuộc | Trivy rootfs trên JAR | OWASP Dependency-Check | cần NVD API key (chưa có) |
| | | Trivy fs trên pom.xml | 429 từ Maven Central; `fs` trên JAR báo sạch giả |
| Cài Trivy | binary + sha256 | aquasecurity/trivy-action | bớt thành phần bên thứ ba |
| Nền tảng staging | khung SSH + Docker Compose, chờ quyết định | chọn PaaS/Kubernetes ngay | chưa có quyết định Q06/Q07 |
| MongoDB staging | `mongo:8.0` + `mongo-init` | image `docker/mongo` | quảng bá `localhost`, không dùng được cho container khác |
| Tag image | `sha-…` + ngày-sha / semver | `latest` | rollback phải trỏ đúng một bản |

## 4. Hệ quả

- Tích cực: pipeline có 9 job (6 chặng), mỗi chặng có thể xem và chạy lại độc lập; image được quét trước khi đẩy; rollback có quy trình
  và đã chạy thử cục bộ; không còn khóa ghi cứng trong workflow.
- **Pipeline sẽ đỏ trong một số tình huống, đúng và có chủ đích:**
  1. Chặng `lint` đỏ cho tới khi PR `chore/f00-static-analysis` (thêm Spotless/SpotBugs vào `pom.xml`) vào main: lần chạy đầu trên GitHub báo
     `No plugin found for prefix 'spotless'`.
  2. Cổng coverage chưa thực sự chặn: `pom.xml` trên main đã có JaCoCo với luật LINE ≥ 0,70 nhưng đặt `haltOnFailure=false`, nên `verify` xanh dù dưới ngưỡng.
     Báo cáo JaCoCo vẫn được tạo và tải lên artifact. Cần nhóm static-analysis đặt `haltOnFailure=true`; CI này không tự sửa vì `pom.xml` thuộc nhóm khác.
  3. Chặng `dependency-scan` và `package-image` đỏ với Spring Boot **4.1.0**: Trivy báo 11 lỗ hổng HIGH/CRITICAL đã có bản vá
     (tomcat-embed-core 11.0.22, spring-webmvc 7.0.8, jackson-core/-databind 3.1.4…). Dựng thử cục bộ trên bản sao với parent
     **4.1.2** (Maven Central có 4.1.1 và 4.1.2) thì image quét sạch (0 HIGH/CRITICAL, Alpine 3.24.3 cũng 0); mới chỉ dựng và quét, chưa chạy test với bản đó. Cần người
     sở hữu `pom.xml` nâng patch và chạy `./mvnw verify` đầy đủ. ADR này **không** giảm ngưỡng hay thêm `.trivyignore` để né.
- Rủi ro còn lại: các bước của chặng 5 (bộ nhớ đệm GHA, đăng nhập và đẩy GHCR, Trivy image trên runner) chưa từng chạy trên GitHub vì chặng 5 bị
  bỏ qua do chặng trước đỏ; staging chưa có nền tảng; MongoDB staging chưa bật xác thực (chỉ mở trong mạng compose).

## 5. Đã kiểm chứng và chưa kiểm chứng

Đã chạy thật (Docker Desktop, máy phát triển, 2026-11-02): `docker build` image (thành công; image ~110 MB, user `app`, nhãn OCI);
chạy image với MongoDB replica set (cổng 27020): `/actuator/health` UP, `/actuator/info` có phiên bản, `/actuator/env` và `/api/v1/auth/me` trả 401, log ECS có
`correlationId`; `docker compose` staging với `deploy.sh`, `smoke.sh`, `rollback.sh` (triển khai hai tag, triển khai tag hỏng → tự rollback exit 1,
`rollback.sh` hai chiều); gitleaks trên lịch sử `origin/main`; Trivy quét image và JAR (kết quả ở QĐ-3, mục 4);
`actionlint 1.7.12` và `shellcheck 0.11.0` không báo lỗi.

Lần chạy đầu trên GitHub Actions (push `chore/f00-ci-cd`, 2026-11-02): `build` xanh (~49 s), `secret-scan` xanh (~6 s), `test` xanh (~97 s, MongoDB rs0 dựng được, đã tải 3 artifact gồm `jacoco-report`), `lint` đỏ (thiếu plugin Spotless, như dự đoán), `dependency-scan` đỏ (đúng 11 lỗ hổng HIGH/CRITICAL như cục bộ), `package-image` và các chặng staging bị bỏ qua. Số giây lấy từ thời điểm bắt đầu/kết thúc job do GitHub ghi, chưa phải thời gian commit → image.

Chưa kiểm chứng: chặng 5 chạy thật (thời gian commit → image **chưa đo**); đẩy GHCR; bước Sonar; OWASP Dependency-Check
(không dùng); `deploy-staging`/`smoke-staging`/`rollback-staging` trên máy chủ thật (chưa có); `./mvnw verify` với Spotless/SpotBugs/JaCoCo
(thuộc PR khác); smoke test `rootfs` sau khi nâng Spring Boot.

## 6. Việc tiếp theo

1. Nâng Spring Boot patch trong `pom.xml` (4.1.2 hoặc mới hơn) và chạy `./mvnw verify` — mở khóa chặng 4b/5.
2. Quyết định nền tảng staging (Q06/Q07), tạo variable/secret (`doc/deploy/staging.md`), bật `STAGING_ENABLED=true`.
3. Sau lần chạy đầu trên GitHub: ghi thời gian thật của từng chặng và của commit → image vào báo cáo tiến độ.
4. Cân nhắc: OWASP Dependency-Check khi có NVD API key; SBOM/attestation; ghim action theo SHA + Dependabot; bật xác thực MongoDB staging;
   nâng cảnh báo/uptime monitor (đề xuất ở `doc/deploy/staging.md`).
