#!/bin/bash
# Khởi động mongod dạng replica set một node, khởi tạo replica set nếu chưa có,
# rồi nạp CSDL ban đầu. Cả hai bước đều chạy lại an toàn (không xóa dữ liệu).
set -euo pipefail

PORT="${MONGO_PORT:-27017}"
RS="${MONGO_REPLSET:-rs0}"

mongod --port "$PORT" --replSet "$RS" --bind_ip_all &
MONGOD_PID=$!

# docker stop gửi SIGTERM: chuyển cho mongod để tắt sạch
trap 'kill -TERM "$MONGOD_PID"; wait "$MONGOD_PID" || true; exit 0' TERM INT

until mongosh --port "$PORT" --quiet --eval 'db.hello().ok' >/dev/null 2>&1; do
  sleep 1
done

mongosh --port "$PORT" --quiet --eval "
  try { rs.status() }
  catch (e) { rs.initiate({ _id: '$RS', members: [{ _id: 0, host: 'localhost:$PORT' }] }) }
" >/dev/null

until mongosh --port "$PORT" --quiet --eval 'db.hello().isWritablePrimary' | grep -q true; do
  sleep 1
done

# LOMS_DB và LOMS_SAMPLE (nếu được đặt bằng -e) được script đọc qua process.env
mongosh --port "$PORT" --quiet --file /opt/loms/database_import.js

echo ">>> MongoDB sẵn sàng tại localhost:$PORT (replica set $RS)"
wait "$MONGOD_PID"
