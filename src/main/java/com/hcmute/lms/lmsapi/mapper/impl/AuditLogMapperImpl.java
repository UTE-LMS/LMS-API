package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

/**
 * Lớp thực thi chuyển đổi và làm sạch dữ liệu kiểm toán.
 * Đảm bảo nguyên lý Single Responsibility Principle (SRP):
 * - Tách biệt hoàn toàn trách nhiệm sanitize dữ liệu nhạy cảm ra khỏi Service.
 * - Chuẩn hóa dữ liệu before/after sang dạng Map/Collection hoặc null phù hợp với $jsonSchema của MongoDB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogMapperImpl implements AuditLogMapper {

    private final ObjectMapper objectMapper;

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password", "passwordhash", "rawpassword", "pass", "pwd",
            "token", "accesstoken", "refreshtoken", "secret", "secretkey",
            "hash", "salt", "credential", "credentials", "apikey", "authorization"
    );

    @Override
    public AuditLog toDocument(AuditAction action, AuditEntity entity, Object before, Object after,
                               String reason, String validatedUserId, String ip) {
        Object sanitizedBefore = sanitize(before);
        Object sanitizedAfter = sanitize(after);

        return AuditLog.builder()
                .at(Instant.now())
                .userId(validatedUserId)
                .action(action)
                .entity(entity)
                .before(sanitizedBefore)
                .after(sanitizedAfter)
                .reason(reason)
                .ip(ip)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object sanitize(Object input) {
        if (input == null) {
            return null;
        }

        try {
            if (input instanceof Map<?, ?> map) {
                return sanitizeMap((Map<String, Object>) map);
            } else if (input instanceof Collection<?> collection) {
                List<Object> sanitizedList = new ArrayList<>();
                for (Object item : collection) {
                    sanitizedList.add(sanitize(item));
                }
                return Map.of("items", sanitizedList);
            } else if (isScalar(input)) {
                return Map.of("value", input);
            } else {
                Map<String, Object> map = objectMapper.convertValue(input, Map.class);
                return sanitizeMap(map);
            }
        } catch (Exception e) {
            log.warn("Không thể chuyển đổi dữ liệu audit sang Map, sử dụng fallback an toàn: {}", e.getMessage());
            return Map.of("value", "[SANITIZATION_FALLBACK]");
        }
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> map) {
        Map<String, Object> cleanMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSensitiveKey(key)) {
                cleanMap.put(key, "[PROTECTED]");
            } else if (value instanceof Map<?, ?> nestedMap) {
                cleanMap.put(key, sanitizeMap((Map<String, Object>) nestedMap));
            } else if (value instanceof Collection<?> collection) {
                List<Object> cleanList = new ArrayList<>();
                for (Object item : collection) {
                    if (item instanceof Map<?, ?> nestedItemMap) {
                        cleanList.add(sanitizeMap((Map<String, Object>) nestedItemMap));
                    } else {
                        cleanList.add(item);
                    }
                }
                cleanMap.put(key, cleanList);
            } else {
                cleanMap.put(key, value);
            }
        }
        return cleanMap;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase().replaceAll("[^a-z]", "");
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isScalar(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean
                || obj instanceof Character || obj.getClass().isPrimitive();
    }
}
