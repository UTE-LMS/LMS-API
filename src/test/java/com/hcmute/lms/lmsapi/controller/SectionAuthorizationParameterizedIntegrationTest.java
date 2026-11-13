package com.hcmute.lms.lmsapi.controller;

import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.ScopeKind.COURSE_MATCH;
import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.ScopeKind.EMPTY_ARRAYS;
import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.ScopeKind.LISTED_AS_SECTION_LECTURER;
import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.ScopeKind.NULL_SCOPE;
import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.ScopeKind.OTHER_RESOURCES;
import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.ScopeKind.PROGRAM_MATCH;
import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.ScopeKind.SECTION_MATCH;
import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.row;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix;
import com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.Case;
import com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.Targets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * US-03/AC-3 và AC-4: ma trận vai trò × phạm vi × READ/WRITE trên lớp học phần, khai báo dưới dạng
 * bảng dữ liệu. Giá trị kỳ vọng chép từ bảng ma trận trong {@code doc/plan/F01-nguoi-dung-phan-
 * quyen.md} mục 4.2 và 4.3, không lấy từ {@code ScopeAuthorizationServiceImpl}.
 *
 * <p>Đây là mẫu cho feature sau (bài đánh giá, nhập điểm...): đổi endpoint và bảng {@code rows}.
 */
@DisplayName("US-03/AC-3: Ma trận vai trò x phạm vi x READ/WRITE (tham số hóa)")
class SectionAuthorizationParameterizedIntegrationTest extends IntegrationTestBase {

  private static final String READ_URL = "/api/v1/test/sections/{id}";
  private static final String WRITE_URL = "/api/v1/test/sections/{id}/grades";

  static Stream<Case> matrix() {
    return RoleScopeMatrix.cases(
        // ADMIN: toàn quyền, bất kể phạm vi (mục 4.2, 4.3).
        row(Role.ADMIN, NULL_SCOPE, true, true),
        row(Role.ADMIN, OTHER_RESOURCES, true, true),
        // LECTURER: WRITE và READ khi khớp sectionIds / courseIds / lecturerIds (mục 4.2).
        row(Role.LECTURER, SECTION_MATCH, true, true),
        row(Role.LECTURER, COURSE_MATCH, true, true),
        row(Role.LECTURER, LISTED_AS_SECTION_LECTURER, true, true),
        // AC-4: scope rỗng và không nằm trong lecturerIds thì fail-closed.
        row(Role.LECTURER, NULL_SCOPE, false, false),
        row(Role.LECTURER, EMPTY_ARRAYS, false, false),
        row(Role.LECTURER, PROGRAM_MATCH, false, false),
        row(Role.LECTURER, OTHER_RESOURCES, false, false),
        // PROGRAM_MANAGER: chỉ READ trong sectionIds/courseIds.
        row(Role.PROGRAM_MANAGER, SECTION_MATCH, true, false),
        row(Role.PROGRAM_MANAGER, COURSE_MATCH, true, false),
        row(Role.PROGRAM_MANAGER, PROGRAM_MATCH, false, false),
        row(Role.PROGRAM_MANAGER, NULL_SCOPE, false, false),
        row(Role.PROGRAM_MANAGER, OTHER_RESOURCES, false, false),
        // QA: chỉ READ; scope rỗng đọc toàn hệ thống; chỉ programIds thì KHÔNG coi là rỗng.
        row(Role.QA, NULL_SCOPE, true, false),
        row(Role.QA, EMPTY_ARRAYS, true, false),
        row(Role.QA, SECTION_MATCH, true, false),
        row(Role.QA, COURSE_MATCH, true, false),
        row(Role.QA, PROGRAM_MATCH, false, false),
        row(Role.QA, OTHER_RESOURCES, false, false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("matrix")
  @DisplayName("US-03/AC-3: vai trò x phạm vi x READ/WRITE khớp bảng ma trận")
  void roleScopeAccessMatchesMatrix(Case matrixCase) throws Exception {
    var course = fixtures.course();
    var program = fixtures.program();
    CourseSection section = fixtures.section(course.getId(), List.of());
    Targets targets = new Targets(program.getId(), course.getId(), section.getId());
    User user = fixtures.user(matrixCase.role(), matrixCase.scope(targets));
    if (matrixCase.listedAsLecturer()) {
      section.setLecturerIds(List.of(user.getId()));
      mongo().save(section);
    }

    ResultActions result =
        matrixCase.access() == AccessType.READ
            ? mockMvc.perform(getAs(fixtures.bearer(user), READ_URL, section.getId()))
            : mockMvc.perform(postAs(fixtures.bearer(user), WRITE_URL, null, section.getId()));

    if (matrixCase.allowed()) {
      result.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
    } else {
      result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
  }

  @Test
  @DisplayName("US-03/AC-3: lớp không tồn tại thì mọi vai trò kể cả ADMIN bị 403 (fail-closed)")
  void missingSectionIsForbiddenForEveryRole() throws Exception {
    for (Role role : Role.values()) {
      String bearer = fixtures.bearerFor(role, null);
      mockMvc
          .perform(getAs(bearer, READ_URL, ApiTestFixtures.missingId()))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(postAs(bearer, WRITE_URL, null, ApiTestFixtures.missingId()))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  @DisplayName("US-03/AC-2: không đăng nhập thì 401 trên cả READ và WRITE")
  void anonymousIsUnauthorized() throws Exception {
    CourseSection section = fixtures.section(fixtures.course().getId(), List.of());
    mockMvc
        .perform(getAs(null, READ_URL, section.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mockMvc
        .perform(postAs(null, WRITE_URL, null, section.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}
