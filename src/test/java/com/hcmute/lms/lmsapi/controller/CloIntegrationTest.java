package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Term;
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
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    mongo().remove(new Query(), "terms");
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
  @DisplayName("US-10/AC-2: Xác thực ràng buộc học kỳ hiệu lực với collection terms (F05a)")
  void us10_ac2_effectiveTermsValidation() throws Exception {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    Term termFrom =
        Term.builder()
            .code("TERM-" + fixtures.code("HK1"))
            .startDate(now)
            .endDate(now.plus(120, ChronoUnit.DAYS))
            .status(TermStatus.OPEN)
            .build();
    mongo().save(termFrom);

    Term termTo =
        Term.builder()
            .code("TERM-" + fixtures.code("HK2"))
            .startDate(now.plus(180, ChronoUnit.DAYS))
            .endDate(now.plus(300, ChronoUnit.DAYS))
            .status(TermStatus.OPEN)
            .build();
    mongo().save(termTo);

    // 1. effectiveToTermId sau effectiveFromTermId -> 201 Created
    SyllabusRequest validReq =
        SyllabusRequest.builder()
            .version("2026.VALID")
            .effectiveFromTermId(termFrom.getId())
            .effectiveToTermId(termTo.getId())
            .build();

    mockMvc
        .perform(postAs(pmToken, "/api/v1/courses/{courseId}/syllabi", validReq, course1.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.effectiveFromTermId").value(termFrom.getId()))
        .andExpect(jsonPath("$.effectiveToTermId").value(termTo.getId()));

    // 2. effectiveToTermId trước effectiveFromTermId -> 422 BUSINESS_RULE_VIOLATION
    SyllabusRequest invertedReq =
        SyllabusRequest.builder()
            .version("2026.INV")
            .effectiveFromTermId(termTo.getId())
            .effectiveToTermId(termFrom.getId())
            .build();

    mockMvc
        .perform(
            postAs(pmToken, "/api/v1/courses/{courseId}/syllabi", invertedReq, course1.getId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    // 3. effectiveFromTermId không tồn tại -> 404 NOT_FOUND
    SyllabusRequest nonExistentTermReq =
        SyllabusRequest.builder()
            .version("2026.NOTFOUND")
            .effectiveFromTermId(new ObjectId().toHexString())
            .build();

    mockMvc
        .perform(
            postAs(
                pmToken, "/api/v1/courses/{courseId}/syllabi", nonExistentTermReq, course1.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
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

    // 1. Tạo CLO1 trên syl1 thành công (US-11/AC-1)
    CloRequest clo1Req =
        CloRequest.builder()
            .code("CLO1")
            .contentOriginal("Hiểu các khái niệm cơ bản về lập trình hướng đối tượng")
            .bloomLevel(2)
            .measurement(
                CloMeasurementDto.builder().defaultWeight(0.4).defaultPassThreshold(0.5).build())
            .build();

    mockMvc
        .perform(postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clos", clo1Req, syl1Id))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("CLO1"))
        .andExpect(jsonPath("$.bloomLevel").value(2))
        .andExpect(jsonPath("$.measurement.defaultWeight").value(0.4));

    // 2. Tạo CLO1 trên syl2 thành công (cùng code khác syllabusId) (US-11/AC-2)
    mockMvc
        .perform(postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clos", clo1Req, syl2Id))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("CLO1"));

    // 3. Trùng code CLO1 trong cùng syl1 -> 409 CONFLICT (US-11/AC-2)
    mockMvc
        .perform(postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clos", clo1Req, syl1Id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    // 4. Validate bloomLevel = 0 hoặc 7 -> 400 (US-11/AC-3)
    CloRequest badBloomReq =
        CloRequest.builder().code("CLO2").contentOriginal("Nội dung hợp lệ").bloomLevel(7).build();
    mockMvc
        .perform(postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clos", badBloomReq, syl1Id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // 5. Validate measurement ngoài [0, 1] -> 400 (US-11/AC-4)
    CloRequest badMeasurementReq =
        CloRequest.builder()
            .code("CLO2")
            .contentOriginal("Nội dung hợp lệ")
            .bloomLevel(3)
            .measurement(CloMeasurementDto.builder().defaultWeight(1.5).build())
            .build();
    mockMvc
        .perform(postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clos", badMeasurementReq, syl1Id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // 6. Freeze: Chuyển syl1 sang CONFIRMED -> thêm CLO bị từ chối 422 (US-10/AC-3)
    mockMvc
        .perform(
            putAs(
                pmToken,
                "/api/v1/syllabi/{id}/status",
                SyllabusStatusUpdateRequest.builder().status(SyllabusStatus.CONFIRMED).build(),
                syl1Id))
        .andExpect(status().isOk());

    CloRequest newCloReq =
        CloRequest.builder().code("CLO3").contentOriginal("Nội dung hợp lệ").bloomLevel(3).build();
    mockMvc
        .perform(postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clos", newCloReq, syl1Id))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
  }

  @Test
  @DisplayName(
      "US-12: Tạo liên kết CLO-PI (201), validate PI khác CTĐT (422), trùng (409), weight (400), matrix và QA xác nhận")
  void us12_cloPiLinkAndMatrixWorkflow() throws Exception {
    // 1. Tạo syllabus trên course1
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
    String syllabusId = read(sylResult, "$.id");

    // 2. Tạo CLO1 trên syllabus
    CloRequest cloReq =
        CloRequest.builder()
            .code("CLO1")
            .contentOriginal("Phân tích yêu cầu bài toán hướng đối tượng")
            .bloomLevel(4)
            .build();
    MvcResult cloResult =
        mockMvc
            .perform(postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clos", cloReq, syllabusId))
            .andExpect(status().isCreated())
            .andReturn();
    String cloId = read(cloResult, "$.id");

    // 3. Tạo link (CLO1, PI1.1) hợp lệ -> 201 Created (US-12/AC-1)
    CloPiLinkRequest linkReq =
        CloPiLinkRequest.builder()
            .programVersionId(programVersion.getId())
            .cloId(cloId)
            .piId(pi.getId())
            .weight(0.5)
            .build();

    MvcResult linkResult =
        mockMvc
            .perform(
                postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clo-pi-links", linkReq, syllabusId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.programVersionId").value(programVersion.getId()))
            .andExpect(jsonPath("$.cloId").value(cloId))
            .andExpect(jsonPath("$.piId").value(pi.getId()))
            .andExpect(jsonPath("$.weight").value(0.5))
            .andExpect(jsonPath("$.verification.status").value("UNVERIFIED"))
            .andReturn();
    String linkId = read(linkResult, "$.id");

    // 4. Trùng (cloId, piId) -> 409 CONFLICT (US-12/AC-2)
    mockMvc
        .perform(postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clo-pi-links", linkReq, syllabusId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    // 5. PI thuộc CTĐT khác -> 422 BUSINESS_RULE_VIOLATION (US-12/AC-2)
    Program otherProg = fixtures.program();
    ProgramVersion otherVersion = fixtures.programVersion(otherProg, ProgramVersionStatus.DRAFT);
    Plo otherPlo = fixtures.plo(otherVersion, "PLO1", 1);
    Pi otherPi = fixtures.pi(otherVersion, otherPlo, "PI1.1", 1);

    CloPiLinkRequest badVersionLinkReq =
        CloPiLinkRequest.builder()
            .programVersionId(programVersion.getId()) // version này
            .cloId(cloId)
            .piId(otherPi.getId()) // nhưng PI của version khác
            .build();

    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/syllabi/{syllabusId}/clo-pi-links",
                badVersionLinkReq,
                syllabusId))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    // 6. Weight ngoài (0, 1] -> 400 VALIDATION_ERROR (US-12/AC-5)
    CloPiLinkRequest badWeightReq =
        CloPiLinkRequest.builder()
            .programVersionId(programVersion.getId())
            .cloId(cloId)
            .piId(pi.getId())
            .weight(1.5)
            .build();
    mockMvc
        .perform(
            postAs(pmToken, "/api/v1/syllabi/{syllabusId}/clo-pi-links", badWeightReq, syllabusId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // 7. Lấy ma trận CLO x PI -> 200 OK (US-12/AC-4)
    mockMvc
        .perform(
            getAs(
                pmToken,
                "/api/v1/syllabi/{syllabusId}/clo-pi-matrix?programVersionId={pvId}",
                syllabusId,
                programVersion.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clos", hasSize(1)))
        .andExpect(jsonPath("$.pis", hasSize(1)))
        .andExpect(jsonPath("$.links", hasSize(1)))
        .andExpect(jsonPath("$.links[0].cloId").value(cloId))
        .andExpect(jsonPath("$.links[0].piId").value(pi.getId()))
        .andExpect(jsonPath("$.links[0].weight").value(0.5))
        .andExpect(jsonPath("$.links[0].verification.status").value("UNVERIFIED"));

    // 8. QA xác nhận liên kết CLO-PI -> VERIFIED (US-12/AC-3)
    VerificationRequest verifyReq =
        VerificationRequest.builder()
            .status(VerificationStatus.VERIFIED)
            .note("Đã đối chiếu với ma trận chuẩn của Khoa")
            .build();

    mockMvc
        .perform(putAs(qaToken, "/api/v1/clo-pi-links/{id}/verification", verifyReq, linkId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verification.status").value("VERIFIED"))
        .andExpect(
            jsonPath("$.verification.note").value("Đã đối chiếu với ma trận chuẩn của Khoa"));

    // 9. QA ngoài scope -> 403 Forbidden
    mockMvc
        .perform(
            putAs(qaOutOfScopeToken, "/api/v1/clo-pi-links/{id}/verification", verifyReq, linkId))
        .andExpect(status().isForbidden());
  }
}
