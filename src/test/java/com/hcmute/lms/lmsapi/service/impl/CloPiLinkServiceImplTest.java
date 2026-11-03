package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.dto.CloPiLinkRequest;
import com.hcmute.lms.lmsapi.dto.CloPiLinkResponse;
import com.hcmute.lms.lmsapi.dto.CloPiMatrixResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.VerificationRequest;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.mapper.CloMapper;
import com.hcmute.lms.lmsapi.mapper.CloPiLinkMapper;
import com.hcmute.lms.lmsapi.mapper.PiMapper;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloPiLinkServiceImpl Unit Tests (F03 / US-12)")
class CloPiLinkServiceImplTest {

  @Mock private CloPiLinkRepository cloPiLinkRepository;
  @Mock private SyllabusRepository syllabusRepository;
  @Mock private CloRepository cloRepository;
  @Mock private PiRepository piRepository;
  @Mock private ProgramVersionRepository programVersionRepository;
  @Mock private ScopeAuthorizationService scopeAuthorizationService;
  @Mock private CloPiLinkMapper cloPiLinkMapper;
  @Mock private CloMapper cloMapper;
  @Mock private PiMapper piMapper;

  @InjectMocks private CloPiLinkServiceImpl cloPiLinkService;

  private String syllabusId;
  private String programVersionId;
  private String cloId;
  private String piId;
  private String linkId;
  private Syllabus syllabus;
  private Clo clo;
  private Pi pi;
  private CloPiLink link;
  private CloPiLinkRequest request;
  private CloPiLinkResponse response;

  @BeforeEach
  void setUp() {
    syllabusId = new ObjectId().toHexString();
    programVersionId = new ObjectId().toHexString();
    cloId = new ObjectId().toHexString();
    piId = new ObjectId().toHexString();
    linkId = new ObjectId().toHexString();

    syllabus = Syllabus.builder().id(syllabusId).build();
    clo = Clo.builder().id(cloId).syllabusId(syllabusId).build();
    pi = Pi.builder().id(piId).programVersionId(programVersionId).build();

    link =
        CloPiLink.builder()
            .id(linkId)
            .syllabusId(syllabusId)
            .programVersionId(programVersionId)
            .cloId(cloId)
            .piId(piId)
            .weight(0.5)
            .build();

    request =
        CloPiLinkRequest.builder()
            .programVersionId(programVersionId)
            .cloId(cloId)
            .piId(piId)
            .weight(0.5)
            .build();

    response =
        CloPiLinkResponse.builder()
            .id(linkId)
            .syllabusId(syllabusId)
            .programVersionId(programVersionId)
            .cloId(cloId)
            .piId(piId)
            .weight(0.5)
            .build();
  }

  @Test
  @DisplayName(
      "createLink: Ném BusinessRuleViolationException khi PI không thuộc programVersionId (US-12/AC-2)")
  void createLink_PiMismatchVersion_ThrowsBusinessRuleViolation() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(programVersionRepository.existsById(programVersionId)).thenReturn(true);
    when(cloRepository.findById(cloId)).thenReturn(Optional.of(clo));

    // PI thuộc version khác
    String otherVersionId = new ObjectId().toHexString();
    pi.setProgramVersionId(otherVersionId);
    when(piRepository.findById(piId)).thenReturn(Optional.of(pi));

    assertThatThrownBy(() -> cloPiLinkService.createLink(syllabusId, request))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("PI");
  }

  @Test
  @DisplayName("createLink: Ném CONFLICT (409) khi trùng (cloId, piId) (US-12/AC-2)")
  void createLink_DuplicateLink_ThrowsConflict() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(programVersionRepository.existsById(programVersionId)).thenReturn(true);
    when(cloRepository.findById(cloId)).thenReturn(Optional.of(clo));
    when(piRepository.findById(piId)).thenReturn(Optional.of(pi));
    when(cloPiLinkRepository.existsByCloIdAndPiId(cloId, piId)).thenReturn(true);

    assertThatThrownBy(() -> cloPiLinkService.createLink(syllabusId, request))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
  }

  @Test
  @DisplayName("createLink: Tạo liên kết thành công (US-12/AC-1)")
  void createLink_Success() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(programVersionRepository.existsById(programVersionId)).thenReturn(true);
    when(cloRepository.findById(cloId)).thenReturn(Optional.of(clo));
    when(piRepository.findById(piId)).thenReturn(Optional.of(pi));
    when(cloPiLinkRepository.existsByCloIdAndPiId(cloId, piId)).thenReturn(false);
    when(cloPiLinkMapper.toDocument(syllabusId, request)).thenReturn(link);
    when(cloPiLinkRepository.save(link)).thenReturn(link);
    when(cloPiLinkMapper.toResponse(link)).thenReturn(response);

    CloPiLinkResponse result = cloPiLinkService.createLink(syllabusId, request);
    assertThat(result).isNotNull();
    assertThat(result.getCloId()).isEqualTo(cloId);
    verify(cloPiLinkRepository).save(link);
  }

  @Test
  @DisplayName("deleteLink: Xóa liên kết thành công")
  void deleteLink_Success() {
    when(cloPiLinkRepository.existsById(linkId)).thenReturn(true);

    cloPiLinkService.deleteLink(linkId);
    verify(cloPiLinkRepository).deleteById(linkId);
  }

  @Test
  @DisplayName("getMatrix: Trả ma trận CLO x PI thành công (US-12/AC-4)")
  void getMatrix_Success() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(programVersionRepository.existsById(programVersionId)).thenReturn(true);
    when(cloRepository.findBySyllabusIdOrderByCodeAsc(syllabusId)).thenReturn(List.of(clo));
    when(piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId))
        .thenReturn(List.of(pi));
    when(cloPiLinkRepository.findBySyllabusIdAndProgramVersionId(syllabusId, programVersionId))
        .thenReturn(List.of(link));

    CloPiMatrixResponse matrix = cloPiLinkService.getMatrix(syllabusId, programVersionId);
    assertThat(matrix).isNotNull();
    assertThat(matrix.getSyllabusId()).isEqualTo(syllabusId);
    assertThat(matrix.getLinks()).hasSize(1);
  }

  @Test
  @DisplayName("verifyLink: Chặn khi QA không có quyền scope trên programVersion")
  void verifyLink_OutOfScope_ThrowsForbidden() {
    when(cloPiLinkRepository.findById(linkId)).thenReturn(Optional.of(link));
    UserPrincipal principal = UserPrincipal.builder().id("qa_user").build();

    when(scopeAuthorizationService.canAccessProgramVersion(
            principal, programVersionId, AccessType.WRITE))
        .thenReturn(false);

    VerificationRequest req =
        VerificationRequest.builder().status(VerificationStatus.VERIFIED).build();

    assertThatThrownBy(() -> cloPiLinkService.verifyLink(linkId, req, principal))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
  }

  @Test
  @DisplayName("verifyLink: Cập nhật status sang VERIFIED thành công (US-12/AC-3)")
  void verifyLink_Success() {
    when(cloPiLinkRepository.findById(linkId)).thenReturn(Optional.of(link));
    UserPrincipal principal = UserPrincipal.builder().id("qa_user").build();

    when(scopeAuthorizationService.canAccessProgramVersion(
            principal, programVersionId, AccessType.WRITE))
        .thenReturn(true);
    when(cloPiLinkRepository.save(any(CloPiLink.class))).thenReturn(link);
    when(cloPiLinkMapper.toResponse(any(CloPiLink.class))).thenReturn(response);

    VerificationRequest req =
        VerificationRequest.builder().status(VerificationStatus.VERIFIED).build();

    CloPiLinkResponse result = cloPiLinkService.verifyLink(linkId, req, principal);
    assertThat(result).isNotNull();
    verify(cloPiLinkRepository).save(link);
    assertThat(link.getVerification()).isNotNull();
    assertThat(link.getVerification().getStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(link.getVerification().getVerifiedBy()).isEqualTo("qa_user");
  }
}
