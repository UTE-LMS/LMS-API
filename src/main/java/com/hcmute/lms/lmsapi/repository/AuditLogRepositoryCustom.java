package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditLog;

import java.time.Instant;
import java.util.List;

/**
 * Interface mở rộng cho các truy vấn động trên collection audit_logs.
 * Áp dụng nguyên lý Open-Closed Principle (OCP) và Dependency Inversion Principle (DIP):
 * cho phép mở rộng hành vi truy vấn bằng MongoTemplate mà không làm thay đổi interface chính.
 */
public interface AuditLogRepositoryCustom {

    /**
     * Tìm kiếm nhật ký kiểm toán theo các tiêu chí động với giới hạn bản ghi.
     *
     * @param collection Tên collection (tùy chọn)
     * @param entityId ID của thực thể (tùy chọn)
     * @param action Hành động kiểm toán (tùy chọn)
     * @param from Thời điểm bắt đầu (tùy chọn)
     * @param to Thời điểm kết thúc (tùy chọn)
     * @param limit Giới hạn số lượng bản ghi trả về
     * @return Danh sách AuditLog thỏa điều kiện, sắp xếp theo thời điểm giảm dần
     */
    List<AuditLog> findLogsByCriteria(String collection, String entityId, AuditAction action, Instant from, Instant to, int limit);
}
