#!/usr/bin/env bash
# Smoke test nhẹ sau triển khai, gọi qua HTTP từ bên ngoài (runner CI hoặc máy bất kỳ).
#
#   ./smoke.sh <base-url> [chuỗi-phiên-bản-mong-đợi]
#   ví dụ: ./smoke.sh https://<địa-chỉ-staging> 1a2b3c4
#
# Kiểm tra:
#   1. GET /actuator/health trả 200 và {"status":"UP"} (US-39/AC-1), thử lại có giới hạn.
#   2. GET /actuator/info trả 200; nếu có chuỗi phiên bản mong đợi thì app.version phải chứa nó (đúng image vừa deploy).
#   3. Endpoint quản trị không được mở: GET /actuator/env không được trả 200.
#   4. GET /api/v1/auth/me không kèm token trả 401 (API nghiệp vụ không mở công khai).
# Biến tùy chọn: SMOKE_RETRIES (mặc định 20), SMOKE_RETRY_DELAY (giây, mặc định 5).
set -euo pipefail

base="${1:-}"
expected_version="${2:-}"
[ -n "$base" ] || { echo "Dùng: ./smoke.sh <base-url> [chuỗi-phiên-bản]" >&2; exit 2; }
base="${base%/}"
retries="${SMOKE_RETRIES:-20}"
delay="${SMOKE_RETRY_DELAY:-5}"

fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }
status_of() { curl -s -o /dev/null -m 10 -w '%{http_code}' "$1" || true; }

echo "Smoke test: $base"

health_body=""
tmp_body="$(mktemp)"
trap 'rm -f "$tmp_body"' EXIT
for attempt in $(seq 1 "$retries"); do
  code="$(curl -s -m 10 -o "$tmp_body" -w '%{http_code}' "$base/actuator/health" || true)"
  health_body="$(cat "$tmp_body" 2>/dev/null || true)"
  if [ "$code" = "200" ] && printf '%s' "$health_body" | grep -q '"status":"UP"'; then
    echo "OK  health UP (lần thử $attempt): $health_body"
    break
  fi
  echo "... health chưa UP (HTTP ${code:-000}), lần thử $attempt/$retries"
  [ "$attempt" -lt "$retries" ] || fail "/actuator/health không UP sau $retries lần thử. Phản hồi cuối: $health_body"
  sleep "$delay"
done

info="$(curl -s -m 10 -w '\n%{http_code}' "$base/actuator/info" || true)"
info_code="${info##*$'\n'}"
info_body="${info%$'\n'*}"
[ "$info_code" = "200" ] || fail "/actuator/info trả HTTP ${info_code:-000}"
echo "OK  info: $info_body"
if [ -n "$expected_version" ]; then
  printf '%s' "$info_body" | grep -q "\"version\":\"[^\"]*${expected_version}[^\"]*\"" \
    || fail "app.version trong /actuator/info không chứa '$expected_version' (có thể image cũ vẫn đang chạy)."
  echo "OK  app.version chứa '$expected_version'"
fi

env_code="$(status_of "$base/actuator/env")"
[ "$env_code" != "200" ] || fail "/actuator/env đang mở công khai (HTTP 200)."
echo "OK  /actuator/env không công khai (HTTP $env_code)"

me_code="$(status_of "$base/api/v1/auth/me")"
[ "$me_code" = "401" ] || fail "/api/v1/auth/me không kèm token phải trả 401, nhận HTTP ${me_code:-000}."
echo "OK  /api/v1/auth/me không token -> 401"

echo "Smoke test đạt."
