package com.hcmute.lms.lmsapi.exception;

import com.hcmute.lms.lmsapi.dto.ErrorResponse;
import com.hcmute.lms.lmsapi.dto.FieldErrorDetail;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("US-40: GlobalExceptionHandler Empirical Adversarial Tests")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(new TestExceptionController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // =========================================================================
    // DTO & DUMMY CONTROLLER FOR TESTING
    // =========================================================================

    public static class TestRequestDto {
        @NotBlank(message = "Tên không được để trống")
        private String name;

        @Min(value = 1, message = "Số lượng phải lớn hơn hoặc bằng 1")
        private int quantity;

        @NotBlank(message = "Mật khẩu không được để trống")
        private String userPassword;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public String getUserPassword() { return userPassword; }
        public void setUserPassword(String userPassword) { this.userPassword = userPassword; }
    }

    @RestController
    @RequestMapping("/test-api")
    static class TestExceptionController {

        @PostMapping("/validation")
        public String testValidation(@Valid @RequestBody TestRequestDto dto) {
            return "OK";
        }

        @GetMapping("/object-id/{id}")
        public String testObjectIdPathVariable(@PathVariable("id") ObjectId id) {
            return id.toHexString();
        }

        @GetMapping("/named-id/{courseId}")
        public String testNamedIdPathVariable(@PathVariable("courseId") Long courseId) {
            return String.valueOf(courseId);
        }

        @GetMapping("/illegal-argument")
        public String testIllegalArgument(@RequestParam(value = "score", required = false) String score) {
            if ("score_rule".equals(score)) {
                throw new IllegalArgumentException("Thao tác SCORE_UPDATE yêu cầu lý do (reason) bắt buộc");
            }
            throw new IllegalArgumentException("Giá trị đối số không hợp lệ: " + score);
        }

        @GetMapping("/invalid-object-id-parsing")
        public String testInvalidObjectIdParsing() {
            new ObjectId("invalid-hex-string-not-24-chars");
            return "OK";
        }

        @GetMapping("/duplicate-key")
        public String testDuplicateKey() {
            throw new DuplicateKeyException("E11000 duplicate key error collection: learning_outcomes.courses index: code_1 dup key: { code: \"SE101\" }");
        }

        @GetMapping("/business-rule")
        public String testBusinessRule() {
            throw new BusinessRuleViolationException("Điểm số không thể cập nhật sau khi kỳ thi đã đóng");
        }

        @GetMapping("/mongo-121-write-error")
        public String testMongo121WriteError() {
            WriteError writeError = new WriteError(121, "Document failed validation", new BsonDocument());
            MongoWriteException mwe = new MongoWriteException(writeError, new ServerAddress("localhost", 27017));
            throw new DataIntegrityViolationException("MongoDB schema validation error", mwe);
        }

        @GetMapping("/mongo-121-message")
        public String testMongo121Message() {
            throw new DataIntegrityViolationException("Insert failed with code 121: Document failed validation");
        }

        @GetMapping("/data-integrity-duplicate-11000")
        public String testDataIntegrityDuplicate11000() {
            throw new DataIntegrityViolationException("E11000 duplicate key error collection (code 11000)");
        }

        @GetMapping("/data-integrity-unknown")
        public String testDataIntegrityUnknown() {
            throw new DataIntegrityViolationException("Foreign key or unexpected integrity violation");
        }

        @GetMapping("/generic-npe")
        public String testGenericNpe() {
            String secretValue = null;
            // Triggers NullPointerException with sensitive context
            secretValue.toLowerCase();
            return secretValue;
        }

        @GetMapping("/generic-runtime")
        public String testGenericRuntime() {
            throw new RuntimeException("Internal SQL query failed at com.hcmute.lms.secret.InternalService.query(InternalService.java:99)");
        }

        @GetMapping("/resource-not-found")
        public String testResourceNotFound() {
            throw new ResourceNotFoundException("Không tìm thấy môn học với ID: 671d4b6842df152a5c4e90a1");
        }

        @GetMapping("/optimistic-lock")
        public String testOptimisticLock() {
            throw new OptimisticLockingFailureException("Revision mismatch on document 671d4b6842df152a5c4e90a1");
        }

        @GetMapping("/app-exception")
        public String testAppException() {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Không được phép truy cập tài nguyên này");
        }

        @GetMapping("/required-param")
        public String testRequiredParam(@RequestParam("filter") String filter) {
            return filter;
        }

        @GetMapping("/required-header")
        public String testRequiredHeader(@RequestHeader("X-Custom-Auth") String header) {
            return header;
        }
    }

    // =========================================================================
    // 1. HTTP 400: VALIDATION & INPUT ERRORS (US-40/AC-1)
    // =========================================================================

    @Nested
    @DisplayName("US-40/AC-1: Bean Validation & Input Mismatch (HTTP 400)")
    class ValidationAndInputErrorTests {

        @Test
        @DisplayName("MethodArgumentNotValidException -> 400, code VALIDATION_ERROR, fieldErrors populated")
        void shouldHandleMethodArgumentNotValid() throws Exception {
            String invalidJson = """
                    {
                        "name": "",
                        "quantity": 0,
                        "userPassword": ""
                    }
                    """;

            mockMvc.perform(post("/test-api/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Dữ liệu yêu cầu không hợp lệ"))
                    .andExpect(jsonPath("$.path").value("/test-api/validation"))
                    .andExpect(jsonPath("$.fieldErrors").isArray())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThanOrEqualTo(2))))
                    .andExpect(jsonPath("$.fieldErrors[*].field", hasItems("name", "quantity", "userPassword")));
        }

        @Test
        @DisplayName("MethodArgumentNotValidException -> sensitive rejected values are sanitized ([PROTECTED])")
        void shouldSanitizeSensitiveRejectedValuesInValidationErrors() throws Exception {
            String jsonWithInvalidSensitiveField = """
                    {
                        "name": "Toán rời rạc",
                        "quantity": -5,
                        "userPassword": ""
                    }
                    """;

            MvcResult result = mockMvc.perform(post("/test-api/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonWithInvalidSensitiveField))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            // Verify userPassword field has [PROTECTED] or is sanitized
            assertThat(responseBody).contains("userPassword");
        }

        @Test
        @DisplayName("Invalid ObjectId path variable (MethodArgumentTypeMismatchException) -> 400, code VALIDATION_ERROR")
        void shouldHandleInvalidObjectIdPathVariable() throws Exception {
            mockMvc.perform(get("/test-api/object-id/not-a-valid-hex-24"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message", containsString("Định dạng định danh (ObjectId)")))
                    .andExpect(jsonPath("$.path").value("/test-api/object-id/not-a-valid-hex-24"))
                    .andExpect(jsonPath("$.fieldErrors").isArray())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("id"))
                    .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").value("not-a-valid-hex-24"));
        }

        @Test
        @DisplayName("Type mismatch on parameter named ending with 'id' -> 400, code VALIDATION_ERROR")
        void shouldHandleTypeMismatchOnNamedId() throws Exception {
            mockMvc.perform(get("/test-api/named-id/invalid-long"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message", containsString("không hợp lệ")));
        }

        @Test
        @DisplayName("IllegalArgumentException from ObjectId constructor -> 400, code VALIDATION_ERROR")
        void shouldHandleIllegalArgumentFromInvalidObjectIdParsing() throws Exception {
            mockMvc.perform(get("/test-api/invalid-object-id-parsing"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.path").value("/test-api/invalid-object-id-parsing"));
        }

        @Test
        @DisplayName("Malformed / empty JSON body -> 400, code VALIDATION_ERROR")
        void shouldHandleMalformedJsonBody() throws Exception {
            mockMvc.perform(post("/test-api/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ malformed-json: broken ]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message", containsString("không đúng định dạng JSON")));
        }

        @Test
        @DisplayName("Missing required request parameter -> 400, code VALIDATION_ERROR")
        void shouldHandleMissingServletRequestParameter() throws Exception {
            mockMvc.perform(get("/test-api/required-param"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message", containsString("Thiếu tham số bắt buộc: 'filter'")));
        }

        @Test
        @DisplayName("Missing required request header -> 400, code VALIDATION_ERROR")
        void shouldHandleMissingRequestHeader() throws Exception {
            mockMvc.perform(get("/test-api/required-header"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message", containsString("Thiếu header bắt buộc: 'X-Custom-Auth'")));
        }
    }

    // =========================================================================
    // 2. HTTP 409: CONFLICT (US-40/AC-3)
    // =========================================================================

    @Nested
    @DisplayName("US-40/AC-3: Duplicate Key & Optimistic Lock Conflict (HTTP 409)")
    class ConflictTests {

        @Test
        @DisplayName("DuplicateKeyException -> 409, code CONFLICT")
        void shouldHandleDuplicateKeyException() throws Exception {
            mockMvc.perform(get("/test-api/duplicate-key"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.message").value("Dữ liệu bị trùng lặp khóa duy nhất trong hệ thống"))
                    .andExpect(jsonPath("$.path").value("/test-api/duplicate-key"));
        }

        @Test
        @DisplayName("OptimisticLockingFailureException -> 409, code CONFLICT")
        void shouldHandleOptimisticLockingFailure() throws Exception {
            mockMvc.perform(get("/test-api/optimistic-lock"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.message", containsString("Dữ liệu đã bị thay đổi bởi phiên làm việc khác")));
        }

        @Test
        @DisplayName("DataIntegrityViolationException wrapping code 11000 -> 409, code CONFLICT")
        void shouldHandleDataIntegrityWrappingDuplicateKey11000() throws Exception {
            mockMvc.perform(get("/test-api/data-integrity-duplicate-11000"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.message").value("Dữ liệu bị trùng lặp khóa duy nhất trong hệ thống"));
        }
    }

    // =========================================================================
    // 3. HTTP 422: BUSINESS RULE VIOLATION & $jsonSchema CODE 121 (US-40/AC-2)
    // =========================================================================

    @Nested
    @DisplayName("US-40/AC-2: Business Rule Violation & Mongo $jsonSchema (HTTP 422)")
    class BusinessRuleViolationTests {

        @Test
        @DisplayName("BusinessRuleViolationException -> 422, code BUSINESS_RULE_VIOLATION")
        void shouldHandleBusinessRuleViolationException() throws Exception {
            mockMvc.perform(get("/test-api/business-rule"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value("Điểm số không thể cập nhật sau khi kỳ thi đã đóng"))
                    .andExpect(jsonPath("$.path").value("/test-api/business-rule"));
        }

        @Test
        @DisplayName("DataIntegrityViolationException wrapping MongoWriteException code 121 -> 422, code BUSINESS_RULE_VIOLATION")
        void shouldHandleMongoWriteExceptionCode121() throws Exception {
            mockMvc.perform(get("/test-api/mongo-121-write-error"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value("Dữ liệu không thỏa mãn ràng buộc schema của cơ sở dữ liệu (mã 121)"))
                    .andExpect(jsonPath("$.path").value("/test-api/mongo-121-write-error"));
        }

        @Test
        @DisplayName("DataIntegrityViolationException with message code 121 -> 422, code BUSINESS_RULE_VIOLATION")
        void shouldHandleMongoCode121Message() throws Exception {
            mockMvc.perform(get("/test-api/mongo-121-message"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value("Dữ liệu không thỏa mãn ràng buộc schema của cơ sở dữ liệu (mã 121)"))
                    .andExpect(jsonPath("$.path").value("/test-api/mongo-121-message"));
        }

        @Test
        @DisplayName("IllegalArgumentException containing SCORE_UPDATE -> 422, code BUSINESS_RULE_VIOLATION")
        void shouldMapScoreUpdateIllegalArgumentTo422() throws Exception {
            mockMvc.perform(get("/test-api/illegal-argument?score=score_rule"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message", containsString("SCORE_UPDATE")));
        }
    }

    // =========================================================================
    // 4. HTTP 500: UNCAUGHT EXCEPTION & INFORMATION LEAKAGE DEFENSE (US-40/AC-4)
    // =========================================================================

    @Nested
    @DisplayName("US-40/AC-4: Uncaught Exception & Information Leakage Defense (HTTP 500)")
    class InternalServerErrorTests {

        @Test
        @DisplayName("NullPointerException -> 500, code INTERNAL_ERROR, NO class name or stack trace in body")
        void shouldHandleNullPointerExceptionSecurely() throws Exception {
            MvcResult result = mockMvc.perform(get("/test-api/generic-npe"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Đã xảy ra lỗi hệ thống không mong muốn. Vui lòng liên hệ quản trị viên."))
                    .andExpect(jsonPath("$.path").value("/test-api/generic-npe"))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();

            // Adversarial assertions: strictly zero information leakage
            assertThat(responseBody).doesNotContain("NullPointerException");
            assertThat(responseBody).doesNotContain("java.lang");
            assertThat(responseBody).doesNotContain("at com.hcmute");
            assertThat(responseBody).doesNotContain("Exception in thread");
            assertThat(responseBody).doesNotContain("stackTrace");
        }

        @Test
        @DisplayName("RuntimeException with internal code details -> 500, code INTERNAL_ERROR, NO internal package/query leak")
        void shouldHandleRuntimeExceptionSecurely() throws Exception {
            MvcResult result = mockMvc.perform(get("/test-api/generic-runtime"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Đã xảy ra lỗi hệ thống không mong muốn. Vui lòng liên hệ quản trị viên."))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();

            // Adversarial assertions: verify message does NOT leak internal query or package details
            assertThat(responseBody).doesNotContain("Internal SQL query failed");
            assertThat(responseBody).doesNotContain("InternalService");
            assertThat(responseBody).doesNotContain("com.hcmute.lms.secret");
            assertThat(responseBody).doesNotContain("RuntimeException");
        }

        @Test
        @DisplayName("DataIntegrityViolationException with unknown cause falls back to 500, NO leakage")
        void shouldFallbackDataIntegrityUnknownTo500() throws Exception {
            MvcResult result = mockMvc.perform(get("/test-api/data-integrity-unknown"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).doesNotContain("Foreign key or unexpected integrity violation");
            assertThat(responseBody).doesNotContain("DataIntegrityViolationException");
        }
    }

    // =========================================================================
    // 5. HTTP 404 & DOMAIN APP EXCEPTION
    // =========================================================================

    @Nested
    @DisplayName("ResourceNotFoundException & AppException")
    class OtherExceptionTests {

        @Test
        @DisplayName("ResourceNotFoundException -> 404, code NOT_FOUND")
        void shouldHandleResourceNotFoundException() throws Exception {
            mockMvc.perform(get("/test-api/resource-not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message", containsString("Không tìm thấy môn học")))
                    .andExpect(jsonPath("$.path").value("/test-api/resource-not-found"));
        }

        @Test
        @DisplayName("AppException with HttpStatus.FORBIDDEN -> 403, code FORBIDDEN")
        void shouldHandleAppException() throws Exception {
            mockMvc.perform(get("/test-api/app-exception"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.message").value("Không được phép truy cập tài nguyên này"));
        }
    }
}
