package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data MongoDB Repository cho collection audit_logs.
 * Kế thừa MongoRepository và AuditLogRepositoryCustom (tuân thủ OCP và DIP).
 * Tầng Data Access (Repository) theo chuẩn kiến trúc phân tầng.
 */
@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String>, AuditLogRepositoryCustom {

    /**
     * Tìm kiếm nhật ký kiểm toán theo thực thể và sắp xếp giảm dần theo thời gian.
     * Tận dụng tối đa chỉ mục compound {'entity.collection': 1, 'entity.id': 1, 'at': -1}.
     */
    List<AuditLog> findByEntityCollectionAndEntityIdOrderByAtDesc(String collection, String id);

    /**
     * Tìm kiếm lịch sử thao tác của một người dùng theo thứ tự thời gian mới nhất.
     */
    List<AuditLog> findByUserIdOrderByAtDesc(String userId);

    /**
     * Tìm kiếm theo loại hành vi kiểm toán.
     */
    List<AuditLog> findByActionOrderByAtDesc(AuditAction action);
}
