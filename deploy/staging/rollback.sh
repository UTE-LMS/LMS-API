#!/usr/bin/env bash
# Quay lại bản triển khai tốt trước đó (hoặc một tag chỉ định), rồi đợi /actuator/health UP.
#
#   ./rollback.sh            về previous_tag (ghi trong .deploy/previous_tag)
#   ./rollback.sh <tag>      về tag chỉ định (image phải còn trên máy chủ hoặc kéo được từ registry)
#
# Sau khi rollback thành công, current_tag và previous_tag được hoán đổi: chạy lại ./rollback.sh sẽ quay về bản vừa rời.
# Mã thoát: 0 = rollback xong và UP; 1 = lỗi hoặc không UP.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=lib.sh
. ./lib.sh

[ -f .env ] || die "Chưa có .env."
command -v docker >/dev/null || die "Không tìm thấy docker."

mkdir -p "$STATE_DIR"
current="$(read_tag current)"
target="${1:-$(read_tag previous)}"
[ -n "$target" ] || die "Chưa có bản trước (.deploy/previous_tag trống). Chỉ định tag: ./rollback.sh <tag>"
[ "$target" != "$current" ] || die "Tag '$target' đã là bản đang chạy."

log "Rollback từ '${current:-chưa rõ}' về '$target'."
start_with_tag "$target"

if wait_healthy; then
  if [ -n "$current" ]; then write_tag previous "$current"; fi
  write_tag current "$target"
  log "Rollback về '$target' thành công."
  exit 0
fi

log "Rollback về '$target' KHÔNG UP. Log ứng dụng (100 dòng cuối):"
compose logs --no-color --tail=100 app || true
exit 1
