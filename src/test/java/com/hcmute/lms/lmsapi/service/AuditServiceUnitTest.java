package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.mapper.AuditLogMapper;
import com.hcmute.lms.lmsapi.mapper.impl.AuditLogMapperImpl;
import com.hcmute.lms.lmsapi.repository.AuditLogRepository;
import com.hcmute.lms.lmsapi.service.impl.AuditServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService: Business Logic & Orchestration Unit Tests")
public class AuditServiceUnitTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogMapper auditLogMapper;
    private AuditServiceImpl auditService;

    @BeforeEach
    void setUp() {
        var mapper = JsonMapper.builder().build();
        auditLogMapper = new AuditLogMapperImpl(mapper);
        auditService = new AuditServiceImpl(auditLogRepository, auditLogMapper);
        lenient().when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("AuditAction enum contains exactly the 7 defined actions matching schema")
    void testAuditActionEnumValues() {
        AuditAction[] actions = AuditAction.values();
        assertThat(actions).hasSize(7);
        assertThat(actions).containsExactlyInAnyOrder(
                AuditAction.SCORE_UPDATE,
                AuditAction.IMPORT_CONFIRM,
                AuditAction.ASSESSMENT_APPROVE,
                AuditAction.ASSESSMENT_REJECT,
                AuditAction.RUN_CALCULATION,
                AuditAction.RULE_CONFIRM,
                AuditAction.LOGIN
        );
    }

    @Test
    @DisplayName("SCORE_UPDATE throws IllegalArgumentException when reason is null")
    void testScoreUpdateWithNullReasonThrowsException() {
        AuditEntity entity = new AuditEntity("scores", "507f1f77bcf86cd799439011");

        assertThatThrownBy(() -> auditService.record(AuditAction.SCORE_UPDATE, entity, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SCORE_UPDATE");
    }

    @Test
    @DisplayName("SCORE_UPDATE throws IllegalArgumentException when reason is empty string")
    void testScoreUpdateWithEmptyReasonThrowsException() {
        AuditEntity entity = new AuditEntity("scores", "507f1f77bcf86cd799439011");

        assertThatThrownBy(() -> auditService.record(AuditAction.SCORE_UPDATE, entity, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SCORE_UPDATE");
    }

    @Test
    @DisplayName("SCORE_UPDATE throws IllegalArgumentException when reason is whitespace string")
    void testScoreUpdateWithWhitespaceReasonThrowsException() {
        AuditEntity entity = new AuditEntity("scores", "507f1f77bcf86cd799439011");

        assertThatThrownBy(() -> auditService.record(AuditAction.SCORE_UPDATE, entity, null, null, "   \t\n   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SCORE_UPDATE");
    }

    @Test
    @DisplayName("Non-SCORE_UPDATE actions succeed with null or empty reason")
    void testNonScoreUpdateAllowsNullReason() {
        AuditEntity entity = new AuditEntity("users", "507f1f77bcf86cd799439011");

        assertThatCode(() -> auditService.record(AuditAction.LOGIN, entity, null, null, null))
                .doesNotThrowAnyException();
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("SCORE_UPDATE succeeds when reason is non-empty")
    void testScoreUpdateSucceedsWithValidReason() {
        AuditEntity entity = new AuditEntity("scores", "507f1f77bcf86cd799439011");

        assertThatCode(() -> auditService.record(AuditAction.SCORE_UPDATE, entity, null, null, "Phúc khảo điểm thi"))
                .doesNotThrowAnyException();
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Sensitive data masking handles password, passwordHash, token, secret, and nested maps")
    void testSensitiveDataMasking() {
        AuditEntity entity = new AuditEntity("users", "507f1f77bcf86cd799439011");

        Map<String, Object> nested = new HashMap<>();
        nested.put("rawPassword", "secretPlainPassword");
        nested.put("secretKey", "jwt-secret-xyz");
        nested.put("safeNestedField", "safeValue");

        Map<String, Object> beforeData = new HashMap<>();
        beforeData.put("username", "student01");
        beforeData.put("password", "oldSecret123");
        beforeData.put("passwordHash", "$2a$12$e8fjd8s9f...");
        beforeData.put("token", "bearer eyJhbGciOi...");
        beforeData.put("secret", "topSecretValue");
        beforeData.put("nestedConfig", nested);

        auditService.record(AuditAction.LOGIN, entity, beforeData, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getBefore()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitized = (Map<String, Object>) savedLog.getBefore();

        // Plaintext safe field preserved
        assertThat(sanitized.get("username")).isEqualTo("student01");

        // Sensitive fields masked
        assertThat(sanitized.get("password")).isEqualTo("[PROTECTED]");
        assertThat(sanitized.get("passwordHash")).isEqualTo("[PROTECTED]");
        assertThat(sanitized.get("token")).isEqualTo("[PROTECTED]");
        assertThat(sanitized.get("secret")).isEqualTo("[PROTECTED]");

        // Nested sensitive fields masked
        assertThat(sanitized.get("nestedConfig")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitizedNested = (Map<String, Object>) sanitized.get("nestedConfig");
        assertThat(sanitizedNested.get("rawPassword")).isEqualTo("[PROTECTED]");
        assertThat(sanitizedNested.get("secretKey")).isEqualTo("[PROTECTED]");
        assertThat(sanitizedNested.get("safeNestedField")).isEqualTo("safeValue");
    }

    @Test
    @DisplayName("Case-insensitivity and special character key masking for sensitive data")
    void testSensitiveKeyVariationsAndNullValues() {
        AuditEntity entity = new AuditEntity("users", "507f1f77bcf86cd799439011");

        Map<String, Object> map = new HashMap<>();
        map.put("USER_PASSWORD", "Pass1");
        map.put("x-access-token", "Token2");
        map.put("Authorization_Header", "Bearer xyz");
        map.put("Client_Secret", "sec3");
        map.put("salt_value", "salt4");
        map.put("null_password_field", null);
        map.put("normal_null_field", null);
        map.put("normal_field", "value");

        auditService.record(AuditAction.LOGIN, entity, map, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitized = (Map<String, Object>) savedLog.getBefore();

        assertThat(sanitized.get("USER_PASSWORD")).isEqualTo("[PROTECTED]");
        assertThat(sanitized.get("x-access-token")).isEqualTo("[PROTECTED]");
        assertThat(sanitized.get("Authorization_Header")).isEqualTo("[PROTECTED]");
        assertThat(sanitized.get("Client_Secret")).isEqualTo("[PROTECTED]");
        assertThat(sanitized.get("salt_value")).isEqualTo("[PROTECTED]");
        // key has 'password', value null -> masked to [PROTECTED]
        assertThat(sanitized.get("null_password_field")).isEqualTo("[PROTECTED]");
        assertThat(sanitized.get("normal_null_field")).isNull();
        assertThat(sanitized.get("normal_field")).isEqualTo("value");
    }

    @Test
    @DisplayName("UserId validation: valid 24-char hex ObjectId kept, invalid string converted to null")
    void testUserIdValidation() {
        AuditEntity entity = new AuditEntity("scores", "507f1f77bcf86cd799439011");

        // Valid ObjectId hex
        auditService.record(AuditAction.SCORE_UPDATE, entity, null, null, "Reason 1", "507f1f77bcf86cd799439011", "127.0.0.1");
        // Invalid string
        auditService.record(AuditAction.SCORE_UPDATE, entity, null, null, "Reason 2", "not-a-valid-object-id", "127.0.0.1");
        // Null
        auditService.record(AuditAction.SCORE_UPDATE, entity, null, null, "Reason 3", (String) null, "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(3)).save(captor.capture());

        List<AuditLog> allLogs = captor.getAllValues();
        assertThat(allLogs.get(0).getUserId()).isEqualTo("507f1f77bcf86cd799439011");
        assertThat(allLogs.get(1).getUserId()).isNull(); // sanitized to null
        assertThat(allLogs.get(2).getUserId()).isNull();
    }

    @Test
    @DisplayName("Sanitize handles collections, scalars, and null before/after correctly")
    void testSanitizeCollectionsAndScalars() {
        AuditEntity entity = new AuditEntity("courses", "507f1f77bcf86cd799439011");

        // Collection with sensitive map
        Map<String, Object> sensitiveItem = Map.of("password", "abc", "id", "123");
        List<Object> list = List.of(sensitiveItem, "stringItem");

        auditService.record(AuditAction.IMPORT_CONFIRM, entity, list, "scalarString", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getBefore()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> beforeMap = (Map<String, Object>) saved.getBefore();
        assertThat(beforeMap).containsKey("items");

        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) beforeMap.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> firstItem = (Map<String, Object>) items.get(0);
        assertThat(firstItem.get("password")).isEqualTo("[PROTECTED]");
        assertThat(firstItem.get("id")).isEqualTo("123");

        // Scalar after is converted to Map.of("value", input)
        assertThat(saved.getAfter()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> afterMap = (Map<String, Object>) saved.getAfter();
        assertThat(afterMap.get("value")).isEqualTo("scalarString");
    }

    @Test
    @DisplayName("Null action or entity throws IllegalArgumentException")
    void testNullActionOrEntityThrowsException() {
        AuditEntity validEntity = new AuditEntity("scores", "507f1f77bcf86cd799439011");

        assertThatThrownBy(() -> auditService.record(null, validEntity, null, null, "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");

        assertThatThrownBy(() -> auditService.record(AuditAction.LOGIN, null, null, null, "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entity");

        assertThatThrownBy(() -> auditService.record(AuditAction.LOGIN, new AuditEntity(null, "123"), null, null, "reason"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> auditService.record(AuditAction.LOGIN, new AuditEntity("collection", null), null, null, "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
