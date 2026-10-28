package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.AuditLog;

/**
 * Interface chuyển đổi và làm sạch dữ liệu kiểm toán.
 * Tuân thủ Single Responsibility Principle (SRP) và Dependency Inversion Principle (DIP):
 * tách rời logic biến đổi/sanitize dữ liệu nhạy cảm ra khỏi Business Service.
 */
public interface AuditLogMapper {

    /**
     * Chuyển đổi thông tin kiểm toán và làm sạch dữ liệu before/after để tạo AuditLog document.
     *
     * @param action Hành động kiểm toán
     * @param entity Thực thể bị tác động
     * @param before Dữ liệu trước thay đổi
     * @param after Dữ liệu sau thay đổi
     * @param reason Lý do thay đổi
     * @param validatedUserId ID người dùng đã được kiểm tra tính hợp lệ ObjectId
     * @param ip Địa chỉ IP
     * @return AuditLog document sẵn sàng lưu vào CSDL
     */
    AuditLog toDocument(AuditAction action, AuditEntity entity, Object before, Object after, String reason, String validatedUserId, String ip);

    /**
     * Khử khuẩn các trường nhạy cảm và chuẩn hóa sang Map<String, Object> hoặc null
     * phù hợp với schema OBJ_N của MongoDB.
     *
     * @param input Dữ liệu thô ban đầu
     * @return Dữ liệu đã làm sạch an toàn
     */
    Object sanitize(Object input);
}
