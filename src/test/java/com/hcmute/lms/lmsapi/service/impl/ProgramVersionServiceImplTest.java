package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionUpdateRequest;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.mapper.ProgramVersionMapper;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProgramVersionServiceImpl Unit Tests (F02 / US-05)")
class ProgramVersionServiceImplTest {

  private static final String PROGRAM_ID = "671dcba5f123456789abc001";
  private static final String VERSION_ID = "671dcba5f123456789abc002";

  @Mock private ProgramVersionRepository programVersionRepository;
  @Mock private ProgramRepository programRepository;
  @Mock private PloRepository ploRepository;
  @Mock private PiRepository piRepository;
  @Mock private ProgramVersionMapper programVersionMapper;
  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private ProgramVersionServiceImpl service;

  private ProgramVersionRequest createRequest() {
    return ProgramVersionRequest.builder()
        .programId(PROGRAM_ID)
        .version("2026")
        .totalCredits(130)
        .durationYears(4.0)
        .applicableCohort("K26")
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

  private Program sampleProgram() {
    return Program.builder().id(PROGRAM_ID).code("CNTT").name("Công nghệ thông tin").build();
  }

  @Test
  @DisplayName("Tạo phiên bản khi chương trình không tồn tại trả 422")
  void create_programMissing_businessRule() {
    when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.createProgramVersion(createRequest()))
        .isInstanceOf(BusinessRuleViolationException.class);
  }

  @Test
  @DisplayName("Tạo phiên bản trùng số hiệu trả 409")
  void create_duplicateVersion_conflict() {
    when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(sampleProgram()));
    when(programVersionRepository.existsByProgramIdAndVersion(PROGRAM_ID, "2026")).thenReturn(true);

    assertThatThrownBy(() -> service.createProgramVersion(createRequest()))
        .isInstanceOf(AppException.class)
        .satisfies(
            ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
  }

  @Test
  @DisplayName("Tạo phiên bản mặc định ở ACTIVE và đã xác nhận")
  void create_success_defaultsActive() {
    Program program = sampleProgram();
    when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(program));
    when(programVersionRepository.existsByProgramIdAndVersion(PROGRAM_ID, "2026"))
        .thenReturn(false);
    when(programVersionRepository.save(any(ProgramVersion.class)))
        .thenAnswer(
            invocation -> {
              ProgramVersion version = invocation.getArgument(0);
              version.setId(VERSION_ID);
              return version;
            });
    when(programVersionMapper.toResponse(any(ProgramVersion.class), eq(program), eq(0L), eq(0L)))
        .thenReturn(
            ProgramVersionResponse.builder()
                .id(VERSION_ID)
                .programCode("CNTT")
                .programName("Công nghệ thông tin")
                .status(ProgramVersionStatus.ACTIVE)
                .build());

    ProgramVersionResponse response = service.createProgramVersion(createRequest());

    assertThat(response.getStatus()).isEqualTo(ProgramVersionStatus.ACTIVE);
    assertThat(response.getProgramCode()).isEqualTo("CNTT");
    verify(programVersionRepository).save(any(ProgramVersion.class));
  }

  @Test
  @DisplayName("Cập nhật thông tin phiên bản thành công")
  void update_metadata_success() {
    ProgramVersion version = existing(ProgramVersionStatus.ACTIVE);
    when(programVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
    when(programVersionRepository.save(any(ProgramVersion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(sampleProgram()));
    when(ploRepository.countByProgramVersionId(VERSION_ID)).thenReturn(3L);
    when(piRepository.countByProgramVersionId(VERSION_ID)).thenReturn(8L);
    when(programVersionMapper.toResponse(any(ProgramVersion.class), any(), eq(3L), eq(8L)))
        .thenReturn(
            ProgramVersionResponse.builder()
                .id(VERSION_ID)
                .version("2026-v2")
                .totalCredits(135)
                .durationYears(4.0)
                .status(ProgramVersionStatus.ACTIVE)
                .ploCount(3L)
                .piCount(8L)
                .build());

    ProgramVersionUpdateRequest request =
        ProgramVersionUpdateRequest.builder()
            .version("2026-v2")
            .totalCredits(135)
            .durationYears(4.0)
            .applicableCohort("K26")
            .build();

    ProgramVersionResponse response = service.updateProgramVersion(VERSION_ID, request);

    assertThat(response.getVersion()).isEqualTo("2026-v2");
    assertThat(response.getTotalCredits()).isEqualTo(135);
    assertThat(response.getStatus()).isEqualTo(ProgramVersionStatus.ACTIVE);
    assertThat(response.getPloCount()).isEqualTo(3L);
    assertThat(response.getPiCount()).isEqualTo(8L);
  }

  @Test
  @DisplayName("Chuyển ACTIVE sang ARCHIVED thành công qua updateProgramVersionStatus")
  void updateStatus_activeToArchived_success() {
    ProgramVersion version = existing(ProgramVersionStatus.ACTIVE);
    when(programVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
    when(programVersionRepository.save(any(ProgramVersion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(sampleProgram()));
    when(programVersionMapper.toResponse(any(ProgramVersion.class), any(), anyLong(), anyLong()))
        .thenReturn(
            ProgramVersionResponse.builder()
                .id(VERSION_ID)
                .status(ProgramVersionStatus.ARCHIVED)
                .build());

    ProgramVersionStatusUpdateRequest request =
        ProgramVersionStatusUpdateRequest.builder().status(ProgramVersionStatus.ARCHIVED).build();

    ProgramVersionResponse response = service.updateProgramVersionStatus(VERSION_ID, request);

    assertThat(response.getStatus()).isEqualTo(ProgramVersionStatus.ARCHIVED);
  }

  @Test
  @DisplayName("Chuyển ARCHIVED lùi về ACTIVE trả 422")
  void updateStatus_archivedToActive_businessRule() {
    when(programVersionRepository.findById(VERSION_ID))
        .thenReturn(Optional.of(existing(ProgramVersionStatus.ARCHIVED)));

    ProgramVersionStatusUpdateRequest request =
        ProgramVersionStatusUpdateRequest.builder().status(ProgramVersionStatus.ACTIVE).build();

    assertThatThrownBy(() -> service.updateProgramVersionStatus(VERSION_ID, request))
        .isInstanceOf(BusinessRuleViolationException.class);
    verify(programVersionRepository, never()).save(any());
  }

  @Test
  @DisplayName("getProgramVersions: batch-load program và đếm ploCount/piCount theo trang")
  @SuppressWarnings("unchecked")
  void getProgramVersions_hydratesProgramAndPloPiCounts() {
    ProgramVersion pv = existing(ProgramVersionStatus.ACTIVE);
    Program program = sampleProgram();
    when(programVersionRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(pv)));
    when(programRepository.findAllById(any())).thenReturn(List.of(program));
    when(mongoTemplate.aggregate(any(Aggregation.class), any(Class.class), eq(Document.class)))
        .thenReturn(
            new AggregationResults<>(
                List.of(new Document("_id", VERSION_ID).append("count", 5L)), new Document()));
    when(programVersionMapper.toResponse(pv, program, 5L, 5L))
        .thenReturn(
            ProgramVersionResponse.builder()
                .id(VERSION_ID)
                .programId(PROGRAM_ID)
                .programCode("CNTT")
                .programName("Công nghệ thông tin")
                .ploCount(5L)
                .piCount(5L)
                .build());

    PageResponse<ProgramVersionResponse> result = service.getProgramVersions(0, 20, null);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getProgramCode()).isEqualTo("CNTT");
    assertThat(result.getContent().get(0).getPloCount()).isEqualTo(5L);
  }
}
