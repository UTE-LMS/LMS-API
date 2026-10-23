# MongoDB dev cho LMS-API: replica set một node (rs0) + nạp CSDL ban đầu từ doc/database_import.js.
# Build từ gốc repo:  docker build -f docker/mongo/Dockerfile -t lms-mongo .
FROM mongo:8.0

COPY doc/database_import.js /opt/loms/database_import.js
COPY docker/mongo/entrypoint.sh /opt/loms/entrypoint.sh

# Phòng trường hợp file shell bị checkout với CRLF trên Windows
RUN sed -i 's/\r$//' /opt/loms/entrypoint.sh && chmod +x /opt/loms/entrypoint.sh

# Cổng nghe trong container PHẢI trùng cổng publish ra máy host (-p N:N), vì replica set
# quảng bá địa chỉ localhost:N cho client. Đổi bằng -e MONGO_PORT=N.
ENV MONGO_PORT=27017
ENV MONGO_REPLSET=rs0

VOLUME /data/db
ENTRYPOINT ["/opt/loms/entrypoint.sh"]
