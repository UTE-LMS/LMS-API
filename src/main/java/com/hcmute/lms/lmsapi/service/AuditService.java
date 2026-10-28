package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import org.bson.types.ObjectId;

/**
 * Dịch vụ ghi nhận lịch sử kiểm toán dùng chung cho toàn bộ hệ thống LMS-API.
 * Tầng Business Logic (Service Interface) theo chuẩn kiến trúc phân tầng và nguyên lý DIP.
 */
public interface AuditService {

    /**
     * Ghi một bản ghi kiểm toán vào CSDL.
     * Phương thức này thực thi cùng ranh giới transaction với nghiệp vụ đang gọi nó
     * (@Transactional propagation = REQUIRED).
     * Tự động trích xuất userId và client IP từ SecurityContext và RequestContext nếu có.
     *
     * @param action Hành động kiểm toán thuộc danh mục AuditAction
     * @param entity Thông tin thực thể bị tác động (collection, id)
     * @param before Dữ liệu trước thay đổi (sẽ được tự động làm sạch trường nhạy cảm qua AuditLogMapper)
     * @param after Dữ liệu sau thay đổi (sẽ được tự động làm sạch trường nhạy cảm qua AuditLogMapper)
     * @param reason Lý do thay đổi (bắt buộc không được để trống khi action == SCORE_UPDATE)
     * @throws IllegalArgumentException nếu action hoặc entity không hợp lệ, hoặc SCORE_UPDATE thiếu reason
     */
    void record(AuditAction action, AuditEntity entity, Object before, Object after, String reason);

    /**
     * Ghi một bản ghi kiểm toán với thông tin userId và IP được chỉ định tường minh.
     */
    void record(AuditAction action, AuditEntity entity, Object before, Object after, String reason, String userId, String ip);

    /**
     * Overload hỗ trợ ObjectId userId trực tiếp.
     */
    default void record(AuditAction action, AuditEntity entity, Object before, Object after, String reason, ObjectId userId, String ip) {
        record(action, entity, before, after, reason, userId != null ? userId.toHexString() : null, ip);
    }
}
