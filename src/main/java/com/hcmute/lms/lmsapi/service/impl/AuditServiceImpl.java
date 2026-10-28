package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.mapper.AuditLogMapper;
import com.hcmute.lms.lmsapi.repository.AuditLogRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Triển khai nghiệp vụ ghi nhận nhật ký kiểm toán.
 * Tầng Business Logic (Service Implementation) theo chuẩn kiến trúc phân tầng.
 * - Tuân thủ SRP: Ủy thác trách nhiệm khử khuẩn và tạo document cho AuditLogMapper.
 * - Tuân thủ DIP: Tiêm phụ thuộc qua abstraction (AuditLogRepository và AuditLogMapper interfaces).
 * - Tham gia cùng transaction với nghiệp vụ gọi nó (@Transactional REQUIRED).
 * - Kiểm tra hợp lệ lý do (reason) với hành động SCORE_UPDATE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, AuditEntity entity, Object before, Object after, String reason) {
        String userId = resolveCurrentUserId();
        String ip = resolveClientIp();
        record(action, entity, before, after, reason, userId, ip);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, AuditEntity entity, Object before, Object after, String reason, String userId, String ip) {
        // 1. Kiểm tra tính hợp lệ cơ bản
        if (action == null) {
            throw new IllegalArgumentException("Hành động kiểm toán (action) không được để trống");
        }
        if (entity == null || entity.getCollection() == null || entity.getCollection().isBlank()
                || entity.getId() == null || entity.getId().isBlank()) {
            throw new IllegalArgumentException("Thực thể kiểm toán (entity.collection và entity.id) không được để trống");
        }

        // 2. Kiểm tra quy tắc bắt buộc của SCORE_UPDATE
        if (action == AuditAction.SCORE_UPDATE) {
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("Lý do cập nhật điểm (reason) là bắt buộc đối với thao tác SCORE_UPDATE");
            }
        }

        // 3. Đảm bảo userId hợp lệ ObjectId BSON hoặc null
        String validatedUserId = null;
        if (userId != null && ObjectId.isValid(userId)) {
            validatedUserId = userId;
        }

        // 4. Ủy thác chuyển đổi và làm sạch dữ liệu cho AuditLogMapper (SRP & DIP)
        AuditLog auditLog = auditLogMapper.toDocument(action, entity, before, after, reason, validatedUserId, ip);

        // 5. Lưu vào MongoDB qua Repository interface
        AuditLog saved = auditLogRepository.save(auditLog);
        log.debug("Đã lưu audit log id={} action={} entity={}:{}",
                saved.getId(), action, entity.getCollection(), entity.getId());
    }

    private String resolveCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                return null;
            }
            String name = authentication.getName();
            if (name != null && ObjectId.isValid(name)) {
                return name;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
