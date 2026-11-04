package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.support.ApiTestSupport;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.time.Instant;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * US-04: API xem nhật ký thao tác {@code GET /api/v1/audit-logs} (F01, mục tiêu mở rộng).
 *
 * <p>Mọi dòng nhật ký của test nằm trong một collection giả mang tiền tố {@code itest_audit_} kèm
 * mã chạy, và mọi request đều lọc theo collection đó, nên test không phụ thuộc dữ liệu nhật ký có
 * sẵn trong CSDL và không phụ thuộc thứ tự chạy.
 */
@DisplayName("US-04: API xem nhật ký thao tác (audit_logs)")
class AuditLogApiIntegrationTest extends IntegrationTestBase {

  private static final String URL = "/api/v1/audit-logs";

  private static final Instant T1 = Instant.parse("2026-10-01T08:00:00Z");
  private static final Instant T2 = Instant.parse("2026-10-02T08:00:00Z");
  private static final Instant T3 = Instant.parse("2026-10-03T08:00:00Z");

  private String entityCollection;
  private String adminBearer;
  private String actorA;
  private String actorB;

  @BeforeEach
  void seedAuditLogs() {
    entityCollection = "itest_audit_" + fixtures.runId();
    adminBearer = fixtures.bearerFor(Role.ADMIN, null);
    actorA = new ObjectId().toHexString();
    actorB = new ObjectId().toHexString();

    // Ba dòng, thời điểm tăng dần: T1 (LOGIN, A), T2 (SCORE_UPDATE, B), T3 (LOGIN, A).
    save(T1, actorA, AuditAction.LOGIN, "e1", null, null, null);
    save(T2, actorB, AuditAction.SCORE_UPDATE, "e2", Map.of("score", 5), Map.of("score", 7), "sửa");
    save(T3, actorA, AuditAction.LOGIN, "e3", null, null, null);
  }

  @AfterEach
  void removeAuditLogs() {
    mongo()
        .remove(
            Query.query(Criteria.where("entity.collection").is(entityCollection)), "audit_logs");
  }

  private void save(
      Instant at,
      String userId,
      AuditAction action,
      String entityId,
      Object before,
      Object after,
      String reason) {
    mongo()
        .save(
            AuditLog.builder()
                .at(at)
                .userId(userId)
                .action(action)
                .entity(AuditEntity.builder().collection(entityCollection).id(entityId).build())
                .before(before)
                .after(after)
                .reason(reason)
                .ip("203.0.113.7")
                .build());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder scoped() {
    return getAs(adminBearer, URL).param("entity", entityCollection);
  }

  // ------------------------------------------------------------------ AC-1

  @Test
  @DisplayName("US-04/AC-1: ADMIN xem được danh sách phân trang chuẩn, mới nhất trước")
  void adminListsNewestFirstWithStandardEnvelope() throws Exception {
    mockMvc
        .perform(scoped())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.last").value(true))
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].entity.id").value("e3"))
        .andExpect(jsonPath("$.content[1].entity.id").value("e2"))
        .andExpect(jsonPath("$.content[2].entity.id").value("e1"))
        .andExpect(jsonPath("$.content[0].action").value("LOGIN"))
        .andExpect(jsonPath("$.content[0].userId").value(actorA))
        .andExpect(jsonPath("$.content[0].ip").value("203.0.113.7"))
        .andExpect(jsonPath("$.content[1].reason").value("sửa"))
        .andExpect(jsonPath("$.content[1].before.score").value(5))
        .andExpect(jsonPath("$.content[1].after.score").value(7));
  }

  @Test
  @DisplayName("US-04/AC-1 (biên): phân trang tách đúng trang, trang cuối đánh dấu last")
  void paginationSplitsPages() throws Exception {
    mockMvc
        .perform(scoped().param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.last").value(false))
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[0].entity.id").value("e3"));

    mockMvc
        .perform(scoped().param("size", "2").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.last").value(true))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].entity.id").value("e1"));
  }

  @Test
  @DisplayName("US-04/AC-1 (biên): trang vượt quá cuối trả danh sách rỗng, không lỗi")
  void pageBeyondLastReturnsEmptyContent() throws Exception {
    mockMvc
        .perform(scoped().param("page", "9"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  @DisplayName("US-04/AC-1 (biên): không có dòng nào khớp thì trả trang rỗng chuẩn")
  void noMatchReturnsEmptyPage() throws Exception {
    mockMvc
        .perform(getAs(adminBearer, URL).param("entity", entityCollection + "_none"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0))
        .andExpect(jsonPath("$.last").value(true))
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  // ------------------------------------------------------------------ AC-2

  @Test
  @DisplayName("US-04/AC-2: lọc theo action")
  void filterByAction() throws Exception {
    mockMvc
        .perform(scoped().param("action", "LOGIN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].action").value("LOGIN"))
        .andExpect(jsonPath("$.content[1].action").value("LOGIN"));
  }

  @Test
  @DisplayName("US-04/AC-2: lọc theo entity (collection) và entityId")
  void filterByEntityAndEntityId() throws Exception {
    mockMvc
        .perform(scoped().param("entityId", "e2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].entity.id").value("e2"))
        .andExpect(jsonPath("$.content[0].entity.collection").value(entityCollection));
  }

  @Test
  @DisplayName("US-04/AC-2: lọc theo người thực hiện (userId)")
  void filterByUserId() throws Exception {
    mockMvc
        .perform(scoped().param("userId", actorB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].userId").value(actorB))
        .andExpect(jsonPath("$.content[0].action").value("SCORE_UPDATE"));
  }

  @Test
  @DisplayName("US-04/AC-2: lọc theo khoảng thời gian, hai đầu mút đều bao gồm")
  void filterByTimeRangeInclusive() throws Exception {
    mockMvc
        .perform(scoped().param("from", T1.toString()).param("to", T2.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].entity.id").value("e2"))
        .andExpect(jsonPath("$.content[1].entity.id").value("e1"));

    mockMvc
        .perform(scoped().param("from", T3.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].entity.id").value("e3"));

    mockMvc
        .perform(scoped().param("to", T1.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].entity.id").value("e1"));
  }

  @Test
  @DisplayName("US-04/AC-2: các bộ lọc kết hợp bằng AND")
  void filtersCombineWithAnd() throws Exception {
    mockMvc
        .perform(
            scoped().param("action", "LOGIN").param("userId", actorA).param("from", T2.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].entity.id").value("e3"));

    mockMvc
        .perform(scoped().param("action", "SCORE_UPDATE").param("userId", actorA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  // ------------------------------------------------------------------ AC-3

  @Test
  @DisplayName("US-04/AC-3: vai trò không phải ADMIN nhận 403")
  void nonAdminRolesAreForbidden() throws Exception {
    for (Role role :
        new Role[] {
          Role.PROGRAM_MANAGER, Role.QA_OFFICER, Role.DEPARTMENT_HEAD, Role.LECTURER, Role.VIEWER
        }) {
      String bearer = fixtures.bearerFor(role, UserScope.builder().build());
      ApiTestSupport.expectForbidden(mockMvc, getAs(bearer, URL));
    }
  }

  @Test
  @DisplayName("US-04/AC-3: chưa đăng nhập nhận 401")
  void anonymousIsUnauthorized() throws Exception {
    mockMvc.perform(getAs(null, URL)).andExpect(status().isUnauthorized());
  }

  // ------------------------------------------------------------------ AC-4

  @Test
  @DisplayName(
      "US-04/AC-4: before/after không lộ passwordHash, token, secret kể cả dòng cũ chưa sạch")
  void sensitiveFieldsAreNeverReturned() throws Exception {
    // Ghi thẳng vào CSDL (bỏ qua AuditLogMapper) để mô phỏng dòng cũ còn chứa trường nhạy cảm.
    save(
        Instant.parse("2026-10-04T08:00:00Z"),
        actorA,
        AuditAction.LOGIN,
        "leaky",
        Map.of("username", "alice", "passwordHash", "$2a$10$leakedhashleakedhashleaked"),
        Map.of("nested", Map.of("accessToken", "eyJ.leaked.jwt", "secret", "s3cr3t-value")),
        null);

    mockMvc
        .perform(scoped().param("entityId", "leaky"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].before.username").value("alice"))
        .andExpect(jsonPath("$.content[0].before.passwordHash").value("[PROTECTED]"))
        .andExpect(jsonPath("$.content[0].after.nested.accessToken").value("[PROTECTED]"))
        .andExpect(jsonPath("$.content[0].after.nested.secret").value("[PROTECTED]"))
        .andExpect(content().string(not(containsString("leakedhash"))))
        .andExpect(content().string(not(containsString("eyJ.leaked.jwt"))))
        .andExpect(content().string(not(containsString("s3cr3t-value"))));
  }

  // ------------------------------------------------------------------ AC-5

  @Test
  @DisplayName("US-04/AC-5: page < 0, size < 1 hoặc size > 100 trả 400 VALIDATION_ERROR")
  void invalidPaginationIsRejected() throws Exception {
    for (String[] bad :
        new String[][] {{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"size", "abc"}}) {
      mockMvc
          .perform(scoped().param(bad[0], bad[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
  }

  @Test
  @DisplayName("US-04/AC-5: action lạ, userId sai định dạng, thời gian sai định dạng trả 400")
  void invalidFiltersAreRejected() throws Exception {
    for (String[] bad :
        new String[][] {
          {"action", "NOT_AN_ACTION"},
          {"userId", "not-an-object-id"},
          {"from", "01/10/2026"},
          {"to", "yesterday"}
        }) {
      mockMvc
          .perform(scoped().param(bad[0], bad[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
  }

  @Test
  @DisplayName("US-04/AC-5: from sau to trả 400 thay vì trả danh sách rỗng")
  void fromAfterToIsRejected() throws Exception {
    mockMvc
        .perform(scoped().param("from", T3.toString()).param("to", T1.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("from"));
  }
}
