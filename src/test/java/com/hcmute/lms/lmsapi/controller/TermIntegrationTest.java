package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.TermRequest;
import com.hcmute.lms.lmsapi.dto.TermStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("F05a: Terms Management Integration Tests (US-18)")
class TermIntegrationTest extends IntegrationTestBase {

  private String adminToken;
  private String pmToken;
  private String lecturerToken;

  private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

  @BeforeEach
  void setUpTokens() {
    adminToken = fixtures.bearerFor(Role.ADMIN, UserScope.builder().build());
    pmToken = fixtures.bearerFor(Role.PROGRAM_MANAGER, UserScope.builder().build());
    lecturerToken = fixtures.bearerFor(Role.LECTURER, UserScope.builder().build());
  }

  @AfterEach
  void cleanUpTerms() {
    mongo().remove(new Query(), "terms");
  }

  @Test
  @DisplayName(
      "US-18/AC-1: Tạo học kỳ thành công (201); trùng code ném 409; startDate > endDate ném 422")
  void us18_ac1_createTermValidationAndDuplicate() throws Exception {
    String code = fixtures.code("HK1");
    TermRequest req =
        TermRequest.builder()
            .code(code)
            .academicYear("2025-2026")
            .startDate(now)
            .endDate(now.plus(150, ChronoUnit.DAYS))
            .build();

    // 1. Tạo mới thành công -> 201 Created
    MvcResult result =
        mockMvc
            .perform(postAs(adminToken, "/api/v1/terms", req))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.academicYear").value("2025-2026"))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andReturn();

    String termId = read(result, "$.id");

    // 2. Trùng mã -> 409 CONFLICT
    mockMvc
        .perform(postAs(adminToken, "/api/v1/terms", req))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    // 3. startDate > endDate -> 422 BUSINESS_RULE_VIOLATION
    TermRequest invalidDateReq =
        TermRequest.builder()
            .code(fixtures.code("INV"))
            .startDate(now.plus(10, ChronoUnit.DAYS))
            .endDate(now)
            .build();

    mockMvc
        .perform(postAs(adminToken, "/api/v1/terms", invalidDateReq))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    // 4. Tra cứu danh sách và chi tiết
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/terms"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));

    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/terms/{id}", termId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(termId))
        .andExpect(jsonPath("$.code").value(code));
  }

  @Test
  @DisplayName("US-18/AC-2 & AC-3: Khóa sổ điểm (SCORE_LOCKED) và mở lại cần lý do giải trình")
  void us18_ac2_ac3_statusTransitions() throws Exception {
    String code = fixtures.code("LOCK");
    TermRequest req =
        TermRequest.builder()
            .code(code)
            .academicYear("2025-2026")
            .startDate(now)
            .endDate(now.plus(120, ChronoUnit.DAYS))
            .build();

    MvcResult result =
        mockMvc
            .perform(postAs(pmToken, "/api/v1/terms", req))
            .andExpect(status().isCreated())
            .andReturn();

    String termId = read(result, "$.id");

    // US-18/AC-2: Khóa sổ điểm SCORE_LOCKED
    mockMvc
        .perform(
            putAs(
                pmToken,
                "/api/v1/terms/{id}/status",
                TermStatusUpdateRequest.builder().status(TermStatus.SCORE_LOCKED).build(),
                termId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SCORE_LOCKED"));

    // US-18/AC-3: Mở lại SCORE_LOCKED -> OPEN mà không có lý do -> 422
    mockMvc
        .perform(
            putAs(
                pmToken,
                "/api/v1/terms/{id}/status",
                TermStatusUpdateRequest.builder().status(TermStatus.OPEN).build(),
                termId))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    // US-18/AC-3: Mở lại kèm lý do giải trình -> 200 OK
    mockMvc
        .perform(
            putAs(
                pmToken,
                "/api/v1/terms/{id}/status",
                TermStatusUpdateRequest.builder()
                    .status(TermStatus.OPEN)
                    .reason("Phúc khảo điểm theo quyết định của Hội đồng")
                    .build(),
                termId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  @Test
  @DisplayName("RBAC: LECTURER không thể thêm/sửa học kỳ (403); Chưa đăng nhập trả 401")
  void rbac_enforcementOnTerms() throws Exception {
    TermRequest req = TermRequest.builder().code(fixtures.code("RBAC")).build();

    // LECTURER tạo -> 403 Forbidden
    mockMvc.perform(postAs(lecturerToken, "/api/v1/terms", req)).andExpect(status().isForbidden());

    // Anonymous tạo -> 401 Unauthorized
    mockMvc.perform(postAs(null, "/api/v1/terms", req)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("deleteTerm: ADMIN xóa học kỳ chưa có tham chiếu thành công (204)")
  void deleteTerm_success() throws Exception {
    String code = fixtures.code("DEL");
    TermRequest req = TermRequest.builder().code(code).build();

    MvcResult result =
        mockMvc
            .perform(postAs(adminToken, "/api/v1/terms", req))
            .andExpect(status().isCreated())
            .andReturn();

    String termId = read(result, "$.id");

    mockMvc
        .perform(deleteAs(adminToken, "/api/v1/terms/{id}", termId))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(getAs(adminToken, "/api/v1/terms/{id}", termId))
        .andExpect(status().isNotFound());
  }
}
