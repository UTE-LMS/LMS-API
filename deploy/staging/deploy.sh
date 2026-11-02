#!/usr/bin/env bash
# Triển khai LMS-API staging theo tag image, tự rollback nếu ứng dụng không UP.
#
#   ./deploy.sh <image-tag>        ví dụ: ./deploy.sh sha-1a2b3c4
#
# Mã thoát: 0 = triển khai thành công
#           1 = triển khai thất bại và đã rollback về bản trước (bản trước UP trở lại)
#           2 = triển khai thất bại và KHÔNG khôi phục được (chưa có bản trước, hoặc rollback cũng không UP)
# Biến môi trường tùy chọn: HEALTH_TIMEOUT_SECONDS (mặc định 120), DEPLOY_SKIP_PULL=1 (chỉ để thử cục bộ với image
# chưa có trên registry; không dùng trên staging thật).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=lib.sh
. ./lib.sh

tag="${1:-}"
[ -n "$tag" ] || die "Thiếu tag. Dùng: ./deploy.sh <image-tag>"
[ -f .env ] || die "Chưa có .env. Sao chép .env.example thành .env và điền giá trị."
command -v docker >/dev/null || die "Không tìm thấy docker."

mkdir -p "$STATE_DIR"
current="$(read_tag current)"
log "Triển khai tag '$tag' (bản đang chạy: ${current:-chưa có})."

if [ "${DEPLOY_SKIP_PULL:-0}" != "1" ]; then
  IMAGE_TAG="$tag" compose pull app || die "Không kéo được image tag '$tag' (kiểm tra đăng nhập registry và tag có tồn tại)."
fi

start_with_tag "$tag"

if wait_healthy; then
  # Chỉ đẩy bản cũ vào previous khi thực sự đổi bản, để chạy lại cùng tag không làm mất bản trước đó.
  if [ -n "$current" ] && [ "$current" != "$tag" ]; then write_tag previous "$current"; fi
  write_tag current "$tag"
  log "Triển khai '$tag' thành công."
  exit 0
fi

log "Triển khai '$tag' THẤT BẠI. Log ứng dụng (100 dòng cuối):"
compose logs --no-color --tail=100 app || true

if [ -z "$current" ] || [ "$current" = "$tag" ]; then
  log "Không có bản trước để rollback."
  exit 2
fi

log "Rollback về '$current'..."
start_with_tag "$current"
if wait_healthy; then
  log "Đã rollback về '$current' và ứng dụng UP. Triển khai '$tag' cần được sửa rồi chạy lại."
  exit 1
fi
log "Rollback về '$current' cũng không UP. Cần xử lý thủ công."
exit 2
