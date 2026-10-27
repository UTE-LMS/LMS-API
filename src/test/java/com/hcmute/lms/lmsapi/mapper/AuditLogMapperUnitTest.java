package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.mapper.impl.AuditLogMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditLogMapper: Data Sanitization & Mapping Unit Tests (SRP)")
public class AuditLogMapperUnitTest {

    private AuditLogMapper mapper;

    @BeforeEach
    void setUp() {
        var jsonMapper = JsonMapper.builder().build();
        mapper = new AuditLogMapperImpl(jsonMapper);
    }

    @Test
    @DisplayName("toDocument successfully builds AuditLog with sanitized before and after")
    void testToDocument() {
        AuditEntity entity = new AuditEntity("grades", "123456789012345678901234");
        Map<String, Object> before = Map.of("score", 8.5, "password", "hidden123");
        Map<String, Object> after = Map.of("score", 9.0);

        AuditLog log = mapper.toDocument(
                AuditAction.SCORE_UPDATE,
                entity,
                before,
                after,
                "Chấm phúc khảo",
                "123456789012345678901234",
                "192.168.1.1"
        );

        assertThat(log).isNotNull();
        assertThat(log.getAction()).isEqualTo(AuditAction.SCORE_UPDATE);
        assertThat(log.getEntity()).isEqualTo(entity);
        assertThat(log.getReason()).isEqualTo("Chấm phúc khảo");
        assertThat(log.getUserId()).isEqualTo("123456789012345678901234");
        assertThat(log.getIp()).isEqualTo("192.168.1.1");
        assertThat(log.getAt()).isNotNull();

        assertThat(log.getBefore()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitizedBefore = (Map<String, Object>) log.getBefore();
        assertThat(sanitizedBefore.get("score")).isEqualTo(8.5);
        assertThat(sanitizedBefore.get("password")).isEqualTo("[PROTECTED]");

        assertThat(log.getAfter()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitizedAfter = (Map<String, Object>) log.getAfter();
        assertThat(sanitizedAfter.get("score")).isEqualTo(9.0);
    }

    @Test
    @DisplayName("sanitize returns null when input is null")
    void testSanitizeNull() {
        assertThat(mapper.sanitize(null)).isNull();
    }

    @Test
    @DisplayName("sanitize transforms POJO and masks sensitive properties")
    void testSanitizePojo() {
        record SampleUser(String username, String accessToken, int age) {}
        SampleUser user = new SampleUser("teacher_minh", "jwt-token-val", 35);

        Object sanitized = mapper.sanitize(user);
        assertThat(sanitized).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) sanitized;
        assertThat(map.get("username")).isEqualTo("teacher_minh");
        assertThat(map.get("accessToken")).isEqualTo("[PROTECTED]");
        assertThat(map.get("age")).isEqualTo(35);
    }
}
