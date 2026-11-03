package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.CloMeasurementDto;
import com.hcmute.lms.lmsapi.dto.CloPiLinkRequest;
import com.hcmute.lms.lmsapi.dto.CloRequest;
import com.hcmute.lms.lmsapi.dto.SyllabusRequest;
import com.hcmute.lms.lmsapi.dto.SyllabusStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.VerificationRequest;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("F03: Syllabi, CLOs and CLO-PI Links Integration Tests (US-10, US-11, US-12)")
class CloIntegrationTest extends IntegrationTestBase {

  private Program program;
  private ProgramVersion programVersion;
  private Course course1;
  private Course course2;
  private Plo plo;
  private Pi pi;

  private String pmToken;
  private String pmOutOfScopeToken;
  private String qaToken;
  private String qaOutOfScopeToken;
  private String lecturerToken;

  @BeforeEach
  void setUpFixtures() {
    program = fixtures.program();
    programVersion = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);

    course1 = fixtures.course();
    course2 = fixtures.course();

    plo = fixtures.plo(programVersion, "PLO1", 1);
    pi = fixtures.pi(programVersion, plo, "PI1.1", 1);

    pmToken =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER, UserScope.builder().programIds(List.of(program.getId())).build());
    pmOutOfScopeToken =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER,
            UserScope.builder().programIds(List.of("671dcba5f123456789abc999")).build());
    qaToken =
        fixtures.bearerFor(
            Role.QA_OFFICER, UserScope.builder().programIds(List.of(program.getId())).build());
    qaOutOfScopeToken =
        fixtures.bearerFor(
            Role.QA_OFFICER,
            UserScope.builder().programIds(List.of("671dcba5f123456789abc999")).build());
    lecturerToken = fixtures.bearerFor(Role.LECTURER, UserScope.builder().build());
  }

  @AfterEach
  void cleanUpCollections() {
    mongo().remove(new Query(), "clo_pi_links");
    mongo().remove(new Query(), "clos");
    mongo().remove(new Query(), "syllabi");
  }

  @Test
  @DisplayName("US-10: Vòng đời Syllabi, tạo mới (201), trùng version (409) và chuyển trạng thái")
  void us10_syllabiLifecycleAndDuplicate() throws Exception {
    String version = "2024.1";
    SyllabusRequest req = SyllabusRequest.builder().version(version).build();

    // 1. Tạo đề cương mới với PM token -> 201 Created
    MvcResult createResult =
        mockMvc
            .perform(postAs(pmToken, "/api/v1/courses/{courseId}/syllabi", req, course1.getId()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.version").value(version))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn();

    String id = read(createResult, "$.id");

    // 2. Trùng (courseId, version) -> 409 CONFLICT (US-10/AC-1)
    mockMvc
        .perform(postAs(pmToken, "/api/v1/courses/{courseId}/syllabi", req, course1.getId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    // 3. Chuyển trạng thái sang CONFIRMED -> 200 OK
    mockMvc
        .perform(
            putAs(
                pmToken,
                "/api/v1/syllabi/{id}/status",
                SyllabusStatusUpdateRequest.builder().status(SyllabusStatus.CONFIRMED).build(),
                id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    // 4. Chuyển ngược sang DRAFT -> 422 UNPROCESSABLE_ENTITY
    mockMvc
        .perform(
            putAs(
                pmToken,
                "/api/v1/syllabi/{id}/status",
                SyllabusStatusUpdateRequest.builder().status(SyllabusStatus.DRAFT).build(),
                id))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
  }

  @Test
  @DisplayName(
      "US-11: Tạo CLO (201), validate bloomLevel/code (400), trùng code (409) và freeze khi CONFIRMED (422)")
  void us11_cloCreationValidationAndFreeze() throws Exception {
    // Tạo 1 draft syllabus trên course1
    MvcResult syl1Result =
        mockMvc
            .perform(
                postAs(
                    pmToken,
                    "/api/v1/courses/{courseId}/syllabi",
                    SyllabusRequest.builder().version("2024.1").build(),
                    course1.getId()))
            .andExpect(status().isCreated())
            .andReturn();
    String syl1Id = read(syl1Result, "$.id");

    // Tạo 1 draft syllabus trên course2
    MvcResult syl2Result =
        mockMvc
            .perform(
                postAs(
                    pmToken,
                    "/api/v1/courses/{courseId}/syllabi",
                    SyllabusRequest.builder().version("2024.1").build(),
                    course2.getId()))
            .andExpect(status().isCreated())
            .andReturn();
    String syl2Id = read(syl2Result, "$.id");

    // 1. Mã CLO sai định dạng -> 400 Bad Request
    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/syllabi/{syllabusId}/clos",
                CloRequest.builder().code("clo1").bloomLevel(3).build(),
                syl1Id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // 2. bloomLevel = 7 -> 400 Bad Request (US-11/AC-3)
    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/syllabi/{syllabusId}/clos",
                CloRequest.builder().code("CLO1").bloomLevel(7).build(),
                syl1Id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // 3. Tạo CLO1 trên syl1 -> 201 Created (US-11/AC-1)
    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/syllabi/{syllabusId}/clos",
                CloRequest.builder()
                    .code("CLO1")
                    .bloomLevel(3)
                    .contentOriginal("Nắm được cú pháp OOP")
                    .measurement(
                        CloMeasurementDto.builder()
                            .defaultWeight(0.3)
                            .defaultPassThreshold(0.5)
                            .build())
                    .build(),
                syl1Id))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("CLO1"))
        .andExpect(jsonPath("$.bloomLevel").value(3))
        .andExpect(jsonPath("$.measurement.defaultWeight").value(0.3));

    // 4. Trùng CLO1 trong cùng syl1 -> 409 CONFLICT (US-11/AC-2)
    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/syllabi/{syllabusId}/clos",
                CloRequest.builder().code("CLO1").bloomLevel(2).build(),
                syl1Id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    // 5. Cùng code CLO1 nhưng ở syl2 khác đề cương -> 201 Created (US-11/AC-2)
    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/syllabi/{syllabusId}/clos",
                CloRequest.builder().code("CLO1").bloomLevel(4).build(),
                syl2Id))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("CLO1"));

    // 6. Chuyển syl1 sang CONFIRMED
    mockMvc
        .perform(
            putAs(
                pmToken,
                "/api/v1/syllabi/{id}/status",
                SyllabusStatusUpdateRequest.builder().status(SyllabusStatus.CONFIRMED).build(),
                syl1Id))
        .andExpect(status().isOk());

    // 7. Thử thêm CLO mới vào syl1 đã CONFIRMED -> 422 Unprocessable Entity (US-10/AC-3)
    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/syllabi/{syllabusId}/clos",
                CloRequest.builder().code("CLO2").bloomLevel(3).build(),
                syl1Id))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
  }

  @Test
  @DisplayName(
      "US-12: Tạo liên kết CLO-PI (201), chặn PI sai version (422), trùng link (409), ma trận và thẩm định QA")
  void us12_cloPiLinksAndMatrix() throws Exception {
    // 1. Chuẩn bị syllabus và CLO
    MvcResult sylResult =
        mockMvc
            .perform(
                postAs(
                    pmToken,
                    "/api/v1/courses/{courseId}/syllabi",
                    SyllabusRequest.builder().version("2024.1").build(),
                    course1.getId()))
            .andExpect(status().isCreated())
            .andReturn();
    String sylId = read(sylResult, "$.id");

    MvcResult cloResult =
        mockMvc
            .perform(
                postAs(
                    pmToken,
                    "/api/v1/syllabi/{syllabusId}/clos",
                    CloRequest.builder().code("CLO1").bloomLevel(3).build(),
                    sylId))
            .andExpect(status().isCreated())
            .andReturn();
    String cloId = read(cloResult, "$.id");

    // 2. PI thuộc version khác -> 422 (US-12/AC-2)
    ProgramVersion otherVersion = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);
    Plo otherPlo = fixtures.plo(otherVersion, "PLO1", 1);
    Pi otherPi = fixtures.pi(otherVersion, otherPlo, "PI1.1", 1);

    CloPiLinkRequest invalidReq =
        CloPiLinkRequest.builder()
            .programVersionId(programVersion.getId()) // version 1
            .cloId(cloId)
            .piId(otherPi.getId()) // PI của otherVersion
            .weight(0.5)
            .build();

    mockMvc
        .perform(postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clo-pi-links", invalidReq, sylId))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    // 3. Tạo liên kết hợp lệ -> 201 Created (US-12/AC-1)
    CloPiLinkRequest validReq =
        CloPiLinkRequest.builder()
            .programVersionId(programVersion.getId())
            .cloId(cloId)
            .piId(pi.getId())
            .weight(0.5)
            .build();

    MvcResult linkResult =
        mockMvc
            .perform(postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clo-pi-links", validReq, sylId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cloId").value(cloId))
            .andExpect(jsonPath("$.piId").value(pi.getId()))
            .andExpect(jsonPath("$.weight").value(0.5))
            .andReturn();
    String linkId = read(linkResult, "$.id");

    // 4. Trùng (cloId, piId) -> 409 CONFLICT (US-12/AC-2)
    mockMvc
        .perform(postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clo-pi-links", validReq, sylId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    // 5. API Ma trận CLO x PI -> 200 OK (US-12/AC-4)
    mockMvc
        .perform(
            getAs(
                pmToken,
                "/api/v1/syllabi/{syllabusId}/clo-pi-matrix?programVersionId={pvId}",
                sylId,
                programVersion.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clos", hasSize(1)))
        .andExpect(jsonPath("$.pis", hasSize(1)))
        .andExpect(jsonPath("$.links", hasSize(1)))
        .andExpect(jsonPath("$.links[0].linkId").value(linkId));

    // 6. Thẩm định QA ngoài scope -> 403 Forbidden
    mockMvc
        .perform(
            putAs(
                qaOutOfScopeToken,
                "/api/v1/clo-pi-links/{id}/verification",
                VerificationRequest.builder().status(VerificationStatus.VERIFIED).build(),
                linkId))
        .andExpect(status().isForbidden());

    // 7. Thẩm định QA đúng scope -> 200 OK (US-12/AC-3)
    mockMvc
        .perform(
            putAs(
                qaToken,
                "/api/v1/clo-pi-links/{id}/verification",
                VerificationRequest.builder()
                    .status(VerificationStatus.VERIFIED)
                    .note("Đã xác nhận ma trận")
                    .build(),
                linkId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verification.status").value("VERIFIED"));

    // 8. Chặn xóa CLO khi đang được liên kết trong clo_pi_links (Restrict delete) -> 422
    mockMvc
        .perform(deleteAs(pmToken, "/api/v1/clos/{id}", cloId))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    // 9. Xóa liên kết thành công -> 204
    mockMvc
        .perform(deleteAs(pmToken, "/api/v1/clo-pi-links/{id}", linkId))
        .andExpect(status().isNoContent());

    // 10. Giờ xóa CLO thành công -> 204
    mockMvc
        .perform(deleteAs(pmToken, "/api/v1/clos/{id}", cloId))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName(
      "RBAC: LECTURER không thể tạo đề cương (403); PM ngoài scope không thể tạo liên kết CLO-PI (403)")
  void rbacAndScopeRejection() throws Exception {
    // LECTURER tạo đề cương -> 403
    mockMvc
        .perform(
            postAs(
                lecturerToken,
                "/api/v1/courses/{courseId}/syllabi",
                SyllabusRequest.builder().version("2024.1").build(),
                course1.getId()))
        .andExpect(status().isForbidden());

    // PM ngoài scope tạo liên kết CLO-PI -> 403
    CloPiLinkRequest req =
        CloPiLinkRequest.builder()
            .programVersionId(programVersion.getId())
            .cloId(new ObjectId().toHexString())
            .piId(new ObjectId().toHexString())
            .build();

    mockMvc
        .perform(
            postAs(
                pmOutOfScopeToken,
                "/api/v1/syllabi/{syllabusId}/clo-pi-links",
                req,
                new ObjectId().toHexString()))
        .andExpect(status().isForbidden());
  }
}
