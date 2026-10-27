package com.hcmute.lms.lmsapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Cấu hình quản lý giao dịch nhiều document (Multi-document Transaction)
 * cho Spring Data MongoDB trên MongoDB Replica Set (rs0).
 *
 * Đáp ứng yêu cầu US-39/AC-2 và quy chuẩn thiết kế §3.1 của Bước 00.
 */
@Configuration
@EnableTransactionManagement
public class MongoConfig {

    /**
     * Khởi tạo bean MongoTransactionManager liên kết với MongoDatabaseFactory.
     * Bean này cho phép sử dụng chú thích @Transactional trên các Service,
     * tự động mở ClientSession và commit/rollback theo ranh giới transaction.
     *
     * @param dbFactory Factory kết nối CSDL MongoDB được Spring Boot tự động nạp
     * @return MongoTransactionManager quản lý session transaction
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
