# syntax=docker/dockerfile:1
# Image ứng dụng LMS-API (đa tầng): build bằng JDK 25 Temurin, chạy bằng JRE 25, user không phải root.
# Build từ gốc repo:  docker build -t lms-api:local .
# Không chứa secret: mọi cấu hình đi qua biến môi trường lúc chạy (xem application.properties.example).

# ---------- Tầng 1: build ----------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Tách lớp phụ thuộc khỏi lớp mã nguồn: đổi mã Java không buộc tải lại phụ thuộc Maven.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -ntp -q dependency:go-offline

COPY src src
# application.properties thật bị .gitignore/.dockerignore; image dựng từ file mẫu (chỉ có ${ENV:mặc định}, không secret).
# Test chạy ở chặng riêng của CI (cần MongoDB replica set), nên ở đây bỏ qua test.
RUN --mount=type=cache,target=/root/.m2 \
    cp src/main/resources/application.properties.example src/main/resources/application.properties \
    && sh ./mvnw -B -ntp -DskipTests package \
    && cp target/LMS-API-*.jar /workspace/app.jar

# ---------- Tầng 2: chạy ----------
FROM eclipse-temurin:25-jre-alpine AS runtime

# Phiên bản/commit của image, CI truyền vào; hiển thị lại ở nhãn OCI.
ARG APP_VERSION=dev
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="LMS-API" \
      org.opencontainers.image.description="Backend LOMS - đo lường chuẩn đầu ra CTĐT" \
      org.opencontainers.image.source="https://github.com/UTE-LMS/LMS-API" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}"

# Người dùng hệ thống, không đăng nhập được, UID cố định để dễ gán quyền volume nếu cần.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H -h /app -s /sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

USER app:app
EXPOSE 8080

# Chỉnh bộ nhớ JVM theo giới hạn của container; ghi đè được bằng -e JAVA_TOOL_OPTIONS=...
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
# application.properties.example khai báo INITIAL_ADMIN_*=${INITIAL_ADMIN_*:}; nếu biến môi trường hoàn toàn không
# tồn tại thì Spring báo "Circular placeholder reference" và ứng dụng không khởi động. Vì vậy đặt mặc định rỗng
# (cùng cách CI đang làm). Giá trị thật (tài khoản admin đầu tiên) truyền lúc chạy bằng -e / secret, KHÔNG ghi ở đây.
ENV INITIAL_ADMIN_USERNAME="" \
    INITIAL_ADMIN_EMAIL="" \
    INITIAL_ADMIN_PASSWORD=""

# Phiên bản đóng gói hiện ở /actuator/info (app.version), để smoke test đối chiếu image đang chạy với commit vừa deploy.
# Spring Boot mặc định tắt nguồn info.* từ môi trường nên phải bật; chỉ lộ các khóa info.* (tên, mô tả, phiên bản).
ENV MANAGEMENT_INFO_ENV_ENABLED=true \
    INFO_APP_VERSION=${APP_VERSION}

# /actuator/health công khai theo SecurityConfig; trả 503 khi DOWN nên wget thất bại -> container unhealthy.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
