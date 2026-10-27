package com.hcmute.lms.lmsapi.exception;

import com.hcmute.lms.lmsapi.dto.ErrorResponse;
import com.hcmute.lms.lmsapi.dto.FieldErrorDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ErrorCodeAndResponseChallengerTest {

    @Autowired(required = false)
    private ObjectMapper springObjectMapper;

    @Test
    @DisplayName("ErrorCode contains exactly the 7 standardized error codes matching contract")
    void testErrorCodeEnumValuesAndStatusMapping() {
        ErrorCode[] codes = ErrorCode.values();
        assertThat(codes).hasSize(7);

        assertThat(ErrorCode.VALIDATION_ERROR.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.UNAUTHORIZED.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.FORBIDDEN.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.CONFLICT.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.BUSINESS_RULE_VIOLATION.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.INTERNAL_ERROR.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(ErrorCode.VALIDATION_ERROR.getHttpStatus().value()).isEqualTo(400);
        assertThat(ErrorCode.UNAUTHORIZED.getHttpStatus().value()).isEqualTo(401);
        assertThat(ErrorCode.FORBIDDEN.getHttpStatus().value()).isEqualTo(403);
        assertThat(ErrorCode.NOT_FOUND.getHttpStatus().value()).isEqualTo(404);
        assertThat(ErrorCode.CONFLICT.getHttpStatus().value()).isEqualTo(409);
        assertThat(ErrorCode.BUSINESS_RULE_VIOLATION.getHttpStatus().value()).isEqualTo(422);
        assertThat(ErrorCode.INTERNAL_ERROR.getHttpStatus().value()).isEqualTo(500);
    }

    @Test
    @DisplayName("ErrorResponse serialization with Spring ObjectMapper includes standard fields and omits null fieldErrors")
    void testErrorResponseSerializationWithSpringMapper() throws Exception {
        ObjectMapper mapper = springObjectMapper != null ? springObjectMapper : JsonMapper.builder().build();

        Instant now = Instant.parse("2026-10-27T05:00:00Z");
        ErrorResponse responseWithoutFieldErrors = ErrorResponse.builder()
                .timestamp(now)
                .status(404)
                .code(ErrorCode.NOT_FOUND)
                .message("Không tìm thấy sinh viên")
                .path("/api/students/123")
                .fieldErrors(null)
                .build();

        String json = mapper.writeValueAsString(responseWithoutFieldErrors);
        System.out.println("Serialized ErrorResponse (null fieldErrors): " + json);

        assertThat(json).contains("\"status\":404");
        assertThat(json).contains("\"code\":\"NOT_FOUND\"");
        assertThat(json).contains("\"message\":\"Không tìm thấy sinh viên\"");
        assertThat(json).contains("\"path\":\"/api/students/123\"");
        assertThat(json).contains("2026-10-27T05:00:00Z");

        // Verify omits fieldErrors when null
        assertThat(json).doesNotContain("\"fieldErrors\"");
    }

    @Test
    @DisplayName("ErrorResponse serialization includes fieldErrors when populated")
    void testErrorResponseSerializationWithPopulatedFieldErrors() throws Exception {
        ObjectMapper mapper = springObjectMapper != null ? springObjectMapper : JsonMapper.builder().build();

        Instant now = Instant.parse("2026-10-27T05:00:00Z");
        List<FieldErrorDetail> errors = List.of(
                FieldErrorDetail.builder().field("email").message("Email không hợp lệ").rejectedValue("bad-email").build()
        );

        ErrorResponse responseWithFieldErrors = ErrorResponse.builder()
                .timestamp(now)
                .status(400)
                .code(ErrorCode.VALIDATION_ERROR)
                .message("Dữ liệu yêu cầu không hợp lệ")
                .path("/api/students")
                .fieldErrors(errors)
                .build();

        String json = mapper.writeValueAsString(responseWithFieldErrors);
        System.out.println("Serialized ErrorResponse (with fieldErrors): " + json);

        assertThat(json).contains("\"fieldErrors\":[");
        assertThat(json).contains("\"field\":\"email\"");
        assertThat(json).contains("\"message\":\"Email không hợp lệ\"");
        assertThat(json).contains("\"rejectedValue\":\"bad-email\"");
    }

    @Test
    @DisplayName("ErrorResponse serialization omits fieldErrors when empty list")
    void testErrorResponseSerializationWithEmptyFieldErrors() throws Exception {
        ObjectMapper mapper = springObjectMapper != null ? springObjectMapper : JsonMapper.builder().build();

        Instant now = Instant.parse("2026-10-27T05:00:00Z");
        ErrorResponse responseWithEmptyFieldErrors = ErrorResponse.builder()
                .timestamp(now)
                .status(500)
                .code(ErrorCode.INTERNAL_ERROR)
                .message("Lỗi nội bộ")
                .path("/api/error")
                .fieldErrors(Collections.emptyList())
                .build();

        String json = mapper.writeValueAsString(responseWithEmptyFieldErrors);
        System.out.println("Serialized ErrorResponse (empty fieldErrors): " + json);

        // JsonInclude.Include.NON_EMPTY should omit empty list
        assertThat(json).doesNotContain("\"fieldErrors\"");
    }
}
