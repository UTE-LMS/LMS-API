package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.dto.ProgramVersionRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionUpdateRequest;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.mapper.ProgramVersionMapper;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProgramVersionServiceImpl Unit Tests (F02 / US-05)")
class ProgramVersionServiceImplTest {

  private static final String PROGRAM_ID = "671dcba5f123456789abc001";
  private static final String VERSION_ID = "671dcba5f123456789abc002";

  @Mock private ProgramVersionRepository programVersionRepository;
  @Mock private ProgramRepository programRepository;
  @Mock private ProgramVersionMapper programVersionMapper;

  @InjectMocks private ProgramVersionServiceImpl service;

  private ProgramVersionRequest createRequest() {
    return ProgramVersionRequest.builder()
        .programId(PROGRAM_ID)
        .version("2026")
        .totalCredits(130)
        .durationYears(4.0)
        .applicableCohorts(List.of("K26"))
        .build();
  }

  private ProgramVersion existing(ProgramVersionStatus status) {
    return ProgramVersion.builder()
        .id(VERSION_ID)
        .programId(PROGRAM_ID)
        .version("2026")
        .status(status)
        .build();
  }

  @Test
  @DisplayName("Tạo phiên bản khi chương trình không tồn tại trả 422")
  void create_programMissing_businessRule() {
    when(programRepository.existsById(PROGRAM_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.createProgramVersion(createRequest()))
        .isInstanceOf(BusinessRuleViolationException.class);
  }

  @Test
  @DisplayName("Tạo phiên bản trùng số hiệu trả 409")
  void create_duplicateVersion_conflict() {
    when(programRepository.existsById(PROGRAM_ID)).thenReturn(true);
    when(programVersionRepository.existsByProgramIdAndVersion(PROGRAM_ID, "2026")).thenReturn(true);

    assertThatThrownBy(() -> service.createProgramVersion(createRequest()))
        .isInstanceOf(AppException.class)
        .satisfies(
            ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
  }

  @Test
  @DisplayName("Tạo phiên bản luôn ở DRAFT và chờ xác nhận")
  void create_success_defaultsDraft() {
    when(programRepository.existsById(PROGRAM_ID)).thenReturn(true);
    when(programVersionRepository.existsByProgramIdAndVersion(PROGRAM_ID, "2026"))
        .thenReturn(false);
    when(programVersionRepository.save(any(ProgramVersion.class)))
        .thenAnswer(
            invocation -> {
              ProgramVersion version = invocation.getArgument(0);
              version.setId(VERSION_ID);
              return version;
            });
    when(programVersionMapper.toResponse(any(ProgramVersion.class)))
        .thenReturn(
            ProgramVersionResponse.builder()
                .id(VERSION_ID)
                .status(ProgramVersionStatus.DRAFT)
                .build());

    ProgramVersionResponse response = service.createProgramVersion(createRequest());

    assertThat(response.getStatus()).isEqualTo(ProgramVersionStatus.DRAFT);
    verify(programVersionRepository).save(any(ProgramVersion.class));
  }

  @Test
  @DisplayName("Cập nhật thông tin phiên bản thành công")
  void update_metadata_success() {
    ProgramVersion version = existing(ProgramVersionStatus.DRAFT);
    when(programVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
    when(programVersionRepository.save(any(ProgramVersion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(programVersionMapper.toResponse(any(ProgramVersion.class)))
        .thenReturn(
            ProgramVersionResponse.builder()
                .id(VERSION_ID)
                .version("2026-v2")
                .totalCredits(135)
                .durationYears(4.0)
                .status(ProgramVersionStatus.DRAFT)
                .build());

    ProgramVersionUpdateRequest request =
        ProgramVersionUpdateRequest.builder()
            .version("2026-v2")
            .totalCredits(135)
            .durationYears(4.0)
            .applicableCohorts(List.of("K26"))
            .build();

    ProgramVersionResponse response = service.updateProgramVersion(VERSION_ID, request);

    assertThat(response.getVersion()).isEqualTo("2026-v2");
    assertThat(response.getTotalCredits()).isEqualTo(135);
    assertThat(response.getStatus()).isEqualTo(ProgramVersionStatus.DRAFT);
  }

  @Test
  @DisplayName("Chuyển DRAFT sang ACTIVE thành công qua updateProgramVersionStatus")
  void updateStatus_draftToActive_success() {
    ProgramVersion version = existing(ProgramVersionStatus.DRAFT);
    when(programVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
    when(programVersionRepository.save(any(ProgramVersion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(programVersionMapper.toResponse(any(ProgramVersion.class)))
        .thenReturn(
            ProgramVersionResponse.builder()
                .id(VERSION_ID)
                .status(ProgramVersionStatus.ACTIVE)
                .build());

    ProgramVersionStatusUpdateRequest request =
        ProgramVersionStatusUpdateRequest.builder().status(ProgramVersionStatus.ACTIVE).build();

    ProgramVersionResponse response = service.updateProgramVersionStatus(VERSION_ID, request);

    assertThat(response.getStatus()).isEqualTo(ProgramVersionStatus.ACTIVE);
  }

  @Test
  @DisplayName("Chuyển ACTIVE lùi về DRAFT trả 422")
  void updateStatus_activeToDraft_businessRule() {
    when(programVersionRepository.findById(VERSION_ID))
        .thenReturn(Optional.of(existing(ProgramVersionStatus.ACTIVE)));

    ProgramVersionStatusUpdateRequest request =
        ProgramVersionStatusUpdateRequest.builder().status(ProgramVersionStatus.DRAFT).build();

    assertThatThrownBy(() -> service.updateProgramVersionStatus(VERSION_ID, request))
        .isInstanceOf(BusinessRuleViolationException.class);
    verify(programVersionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Bỏ qua ACTIVE để chuyển DRAFT sang ARCHIVED trả 422")
  void updateStatus_skipActive_businessRule() {
    when(programVersionRepository.findById(VERSION_ID))
        .thenReturn(Optional.of(existing(ProgramVersionStatus.DRAFT)));

    ProgramVersionStatusUpdateRequest request =
        ProgramVersionStatusUpdateRequest.builder().status(ProgramVersionStatus.ARCHIVED).build();

    assertThatThrownBy(() -> service.updateProgramVersionStatus(VERSION_ID, request))
        .isInstanceOf(BusinessRuleViolationException.class);
  }
}
