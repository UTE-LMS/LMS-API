package com.hcmute.lms.lmsapi.controller;

import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.ScopeKind.EMPTY_ARRAYS;
import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.ScopeKind.NULL_SCOPE;
import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.ScopeKind.OTHER_RESOURCES;
import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.ScopeKind.PROGRAM_MATCH;
import static com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.VerificationRequest;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix;
import com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.Case;
import com.hcmute.lms.lmsapi.testsupport.RoleScopeMatrix.Targets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * F02 / US-08: xác nhận nội dung CTĐT/PLO/PI. Chỉ {@code QA_OFFICER} có {@code programIds} chứa
 * CTĐT mới được đổi {@code verification.status} (F02 mục 4.9, ADR 0004); ADMIN và PROGRAM_MANAGER
 * không có quyền này.
 */
@DisplayName("F02 / US-08: API xác nhận nội dung (verification)")
class ProgramVerificationApiIntegrationTest extends IntegrationTestBase {

  private static final String VERSION_BY_ID = "/api/v1/program-versions/{id}";
  private static final String VERSION_VERIFY = "/api/v1/program-versions/{id}/verification";
  private static final String PLO_VERIFY = "/api/v1/program-versions/{pv}/plos/{id}/verification";
  private static final String PI_VERIFY = "/api/v1/program-versions/{pv}/pis/{id}/verification";

  private Program program;
  private ProgramVersion version;
  private Plo plo;
  private Pi pi;

  @BeforeEach
  void setUp() {
    program = fixtures.program();
    version = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);
    plo = fixtures.plo(version, "PLO1", 1);
    pi = fixtures.pi(version, plo, "PI1.1", 1);
  }

  private User qaInScope() {
    return fixtures.user(Role.QA, UserScope.builder().programIds(List.of(program.getId())).build());
  }

  private static VerificationRequest verified(String note) {
    return VerificationRequest.builder().status(VerificationStatus.VERIFIED).note(note).build();
  }

  // ------------------------------------------------------------------ US-08/AC-1, AC-2

  @Test
  @DisplayName(
      "US-08/AC-1: QA đổi verification ghi verifiedBy = người gọi và verifiedAt = thời điểm gọi")
  void verifyVersion_recordsVerifierAndTimestamp() throws Exception {
    User qa = qaInScope();
    Instant before = Instant.now().minusSeconds(1);

    MvcResult result =
        mockMvc
            .perform(
                putAs(
                    fixtures.bearer(qa),
                    VERSION_VERIFY,
                    verified(ApiTestFixtures.LABEL + " ok"),
                    version.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verification.status").value("VERIFIED"))
            .andExpect(jsonPath("$.verification.verifiedBy").value(qa.getId()))
            .andExpect(jsonPath("$.verification.note").value(ApiTestFixtures.LABEL + " ok"))
            .andReturn();

    Instant verifiedAt = Instant.parse(read(result, "$.verification.verifiedAt"));
    assertThat(verifiedAt).isBetween(before, Instant.now().plusSeconds(1));
    var stored =
        Objects.requireNonNull(mongo().findById(version.getId(), ProgramVersion.class))
            .getVerification();
    assertThat(stored.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(stored.getVerifiedBy()).isEqualTo(qa.getId());
  }

  @ParameterizedTest(name = "status = {0}")
  @EnumSource(VerificationStatus.class)
  @DisplayName("US-08/AC-2: QA đặt được mọi trạng thái xác nhận cho CTĐT, PLO và PI")
  void qaCanSetEveryStatusOnAllThreeResources(VerificationStatus target) throws Exception {
    String qa = fixtures.bearer(qaInScope());
    VerificationRequest body = VerificationRequest.builder().status(target).build();

    mockMvc
        .perform(putAs(qa, VERSION_VERIFY, body, version.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verification.status").value(target.name()));
    mockMvc
        .perform(putAs(qa, PLO_VERIFY, body, version.getId(), plo.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verification.status").value(target.name()));
    mockMvc
        .perform(putAs(qa, PI_VERIFY, body, version.getId(), pi.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verification.status").value(target.name()));
  }

  // ------------------------------------------------------------------ US-08/AC-3: ma trận

  /** Chỉ QA có programIds chứa CTĐT được ghi verification (cột write); mọi người đọc được. */
  static Stream<Case> verificationMatrix() {
    return RoleScopeMatrix.cases(
        row(Role.QA, PROGRAM_MATCH, true, true),
        row(Role.QA, NULL_SCOPE, true, false),
        row(Role.QA, EMPTY_ARRAYS, true, false),
        row(Role.QA, OTHER_RESOURCES, true, false),
        row(Role.ADMIN, NULL_SCOPE, true, false),
        row(Role.ADMIN, PROGRAM_MATCH, true, false),
        row(Role.PROGRAM_MANAGER, PROGRAM_MATCH, true, false),
        row(Role.PROGRAM_MANAGER, NULL_SCOPE, true, false),
        row(Role.LECTURER, PROGRAM_MATCH, true, false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("verificationMatrix")
  @DisplayName(
      "US-08/AC-3: ma trận vai trò x phạm vi cho verification (đọc: mọi người; ghi: QA trong CTĐT)")
  void roleScopeVerificationMatches(Case matrixCase) throws Exception {
    Targets targets = new Targets(program.getId(), ApiTestFixtures.missingId(), null);
    User user = fixtures.user(matrixCase.role(), matrixCase.scope(targets));
    String bearer = fixtures.bearer(user);

    ResultActions result =
        matrixCase.access() == AccessType.READ
            ? mockMvc.perform(getAs(bearer, VERSION_BY_ID, version.getId()))
            : mockMvc.perform(
                putAs(bearer, VERSION_VERIFY, verified("kiem thu ma tran"), version.getId()));

    if (matrixCase.allowed()) {
      result.andExpect(status().isOk());
    } else {
      result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
      assertThat(mongo().findById(version.getId(), ProgramVersion.class).getVerification())
          .as("bị từ chối thì dữ liệu không được đổi")
          .extracting(v -> v.getStatus())
          .isEqualTo(VerificationStatus.NEEDS_CLARIFICATION);
    }
  }

  @ParameterizedTest(name = "{0} bị 403 trên PLO và PI")
  @EnumSource(
      value = Role.class,
      names = {"QA"},
      mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("US-08/AC-3: mọi vai trò khác QA (kể cả ADMIN) gọi xác nhận PLO/PI -> 403")
  void nonQaRolesAreForbiddenOnPloAndPi(Role role) throws Exception {
    String bearer =
        fixtures.bearerFor(role, UserScope.builder().programIds(List.of(program.getId())).build());

    mockMvc
        .perform(putAs(bearer, PLO_VERIFY, verified("x"), version.getId(), plo.getId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(putAs(bearer, PI_VERIFY, verified("x"), version.getId(), pi.getId()))
        .andExpect(status().isForbidden());
    assertThat(mongo().findById(plo.getId(), Plo.class).getVerification().getStatus())
        .isEqualTo(VerificationStatus.NEEDS_CLARIFICATION);
    assertThat(mongo().findById(pi.getId(), Pi.class).getVerification().getStatus())
        .isEqualTo(VerificationStatus.NEEDS_CLARIFICATION);
  }

  @Test
  @DisplayName("US-08/AC-3: QA ngoài phạm vi CTĐT bị 403 trên PLO và PI")
  void qaOutOfScopeIsForbiddenOnPloAndPi() throws Exception {
    String outOfScope =
        fixtures.bearerFor(
            Role.QA, UserScope.builder().programIds(List.of(ApiTestFixtures.missingId())).build());
    mockMvc
        .perform(putAs(outOfScope, PLO_VERIFY, verified("x"), version.getId(), plo.getId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(putAs(outOfScope, PI_VERIFY, verified("x"), version.getId(), pi.getId()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("US-08/AC-3: không đăng nhập -> 401 trên cả ba endpoint xác nhận")
  void anonymousIsUnauthorized() throws Exception {
    mockMvc
        .perform(putAs(null, VERSION_VERIFY, verified("x"), version.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(putAs(null, PLO_VERIFY, verified("x"), version.getId(), plo.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(putAs(null, PI_VERIFY, verified("x"), version.getId(), pi.getId()))
        .andExpect(status().isUnauthorized());
  }

  // ------------------------------------------------------------------ ca âm / biên

  @Test
  @DisplayName(
      "US-08 (âm): thiếu status, status ngoài enum, body rỗng, ghi chú > 2000 ký tự -> 400")
  void invalidBodies() throws Exception {
    String qa = fixtures.bearer(qaInScope());
    mockMvc
        .perform(putAs(qa, VERSION_VERIFY, "{}", version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("status")));
    mockMvc
        .perform(putAs(qa, VERSION_VERIFY, "{\"status\":\"APPROVED\"}", version.getId()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(putAs(qa, VERSION_VERIFY, null, version.getId()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(putAs(qa, VERSION_VERIFY, verified("n".repeat(2001)), version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("note")));
    mockMvc
        .perform(putAs(qa, VERSION_VERIFY, verified("n".repeat(2000)), version.getId()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("US-08/AC-1 (âm): người gọi không thể giả verifiedBy qua body")
  void verifiedByCannotBeForged() throws Exception {
    User qa = qaInScope();
    String body =
        "{\"status\":\"VERIFIED\",\"verifiedBy\":\"" + ApiTestFixtures.missingId() + "\"}";
    mockMvc
        .perform(putAs(fixtures.bearer(qa), VERSION_VERIFY, body, version.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verification.verifiedBy").value(qa.getId()));
  }

  @Test
  @DisplayName(
      "US-08 (âm): id không tồn tại -> 404; sai định dạng -> 400; PLO/PI của phiên bản khác -> 404")
  void unknownMalformedAndForeignIds() throws Exception {
    String qa = fixtures.bearer(qaInScope());
    mockMvc
        .perform(putAs(qa, VERSION_VERIFY, verified("x"), ApiTestFixtures.missingId()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(putAs(qa, VERSION_VERIFY, verified("x"), "abc"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(putAs(qa, PLO_VERIFY, verified("x"), version.getId(), "abc"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(putAs(qa, PLO_VERIFY, verified("x"), version.getId(), ApiTestFixtures.missingId()))
        .andExpect(status().isNotFound());

    ProgramVersion other = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);
    Plo foreignPlo = fixtures.plo(other, "PLO1", 1);
    Pi foreignPi = fixtures.pi(other, foreignPlo, "PI1.1", 1);
    mockMvc
        .perform(putAs(qa, PLO_VERIFY, verified("x"), version.getId(), foreignPlo.getId()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(putAs(qa, PI_VERIFY, verified("x"), version.getId(), foreignPi.getId()))
        .andExpect(status().isNotFound());
  }
}
