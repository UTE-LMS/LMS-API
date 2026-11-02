#!/usr/bin/env bash
# Hàm dùng chung cho deploy.sh và rollback.sh. Được `source`, không chạy trực tiếp.
# Yêu cầu: docker compose v2 (có plugin `docker compose`), thư mục hiện tại là thư mục chứa file này.

STATE_DIR=".deploy"
# Giới hạn chờ /actuator/health UP (giây); đặt biến môi trường HEALTH_TIMEOUT_SECONDS khi chạy script để đổi.
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-120}"
HEALTH_POLL_SECONDS=3

log() { printf '[%s] %s\n' "$(date -u +%FT%TZ)" "$*"; }
die() { log "LỖI: $*" >&2; exit 1; }

compose() { docker compose -f docker-compose.yml "$@"; }

# Đọc/ghi tag trong thư mục trạng thái. current_tag là bản đang chạy tốt; previous_tag là bản chạy tốt trước đó.
read_tag() { [ -s "$STATE_DIR/$1_tag" ] && cat "$STATE_DIR/$1_tag" || true; }
write_tag() { printf '%s\n' "$2" > "$STATE_DIR/$1_tag"; }

# Đợi tối đa HEALTH_TIMEOUT_SECONDS để GET /actuator/health trả {"status":"UP"} (gọi từ bên trong container app,
# nên không cần curl trên máy chủ). Trả 0 nếu UP, 1 nếu quá hạn.
wait_healthy() {
  local start=$SECONDS
  log "Đợi /actuator/health UP (tối đa ${HEALTH_TIMEOUT_SECONDS}s)..."
  while [ $((SECONDS - start)) -lt "$HEALTH_TIMEOUT_SECONDS" ]; do
    if compose exec -T app wget -q -O - http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
      log "Ứng dụng UP sau $((SECONDS - start))s."
      return 0
    fi
    sleep "$HEALTH_POLL_SECONDS"
  done
  log "Hết ${HEALTH_TIMEOUT_SECONDS}s mà /actuator/health chưa UP."
  return 1
}

# Khởi động (hoặc tạo lại) ứng dụng với tag chỉ định; MongoDB không bị tạo lại nếu không đổi.
start_with_tag() {
  IMAGE_TAG="$1" compose up -d --remove-orphans
}
