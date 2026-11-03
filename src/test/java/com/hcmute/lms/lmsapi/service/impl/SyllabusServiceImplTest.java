package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.SyllabusRequest;
import com.hcmute.lms.lmsapi.dto.SyllabusResponse;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.SyllabusMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyllabusServiceImpl Unit Tests (F03 / US-10)")
class SyllabusServiceImplTest {

  @Mock private SyllabusRepository syllabusRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private TermRepository termRepository;
  @Mock private SyllabusMapper syllabusMapper;

  @InjectMocks private SyllabusServiceImpl syllabusService;

  private String courseId;
  private String syllabusId;
  private String fromTermId;
  private String toTermId;
  private Syllabus syllabus;
  private SyllabusRequest request;
  private SyllabusResponse response;

  @BeforeEach
  void setUp() {
    courseId = new ObjectId().toHexString();
    syllabusId = new ObjectId().toHexString();
    fromTermId = new ObjectId().toHexString();
    toTermId = new ObjectId().toHexString();

    syllabus =
        Syllabus.builder()
            .id(syllabusId)
            .courseId(courseId)
            .version("2024.1")
            .status(SyllabusStatus.DRAFT)
            .build();

    request = SyllabusRequest.builder().version("2024.1").build();

    response =
        SyllabusResponse.builder()
            .id(syllabusId)
            .courseId(courseId)
            .version("2024.1")
            .status(SyllabusStatus.DRAFT)
            .build();
  }

  @Test
  @DisplayName("getSyllabi: Ném ResourceNotFoundException khi học phần không tồn tại")
  void getSyllabi_CourseNotFound_ThrowsNotFound() {
    when(courseRepository.existsById(courseId)).thenReturn(false);

    assertThatThrownBy(() -> syllabusService.getSyllabi(courseId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(courseId);
  }

  @Test
  @DisplayName("getSyllabi: Thành công khi học phần tồn tại")
  void getSyllabi_Success() {
    when(courseRepository.existsById(courseId)).thenReturn(true);
    when(syllabusRepository.findByCourseIdOrderByVersionAsc(courseId))
        .thenReturn(List.of(syllabus));
    when(syllabusMapper.toResponseList(List.of(syllabus))).thenReturn(List.of(response));

    List<SyllabusResponse> result = syllabusService.getSyllabi(courseId);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getVersion()).isEqualTo("2024.1");
  }

  @Test
  @DisplayName("getSyllabus: Ném ResourceNotFoundException khi đề cương không tồn tại")
  void getSyllabus_NotFound_ThrowsNotFound() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> syllabusService.getSyllabus(syllabusId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(syllabusId);
  }

  @Test
  @DisplayName("getSyllabus: Thành công khi đề cương tồn tại")
  void getSyllabus_Success() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(syllabusMapper.toResponse(syllabus)).thenReturn(response);

    SyllabusResponse result = syllabusService.getSyllabus(syllabusId);
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(syllabusId);
  }

  @Test
  @DisplayName("createSyllabus: Ném CONFLICT (409) khi trùng (courseId, version)")
  void createSyllabus_DuplicateVersion_ThrowsConflict() {
    when(courseRepository.existsById(courseId)).thenReturn(true);
    when(syllabusRepository.existsByCourseIdAndVersion(courseId, "2024.1")).thenReturn(true);

    assertThatThrownBy(() -> syllabusService.createSyllabus(courseId, request))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
  }

  @Test
  @DisplayName("createSyllabus: Bắt DuplicateKeyException ném CONFLICT (409)")
  void createSyllabus_DuplicateKeyException_ThrowsConflict() {
    when(courseRepository.existsById(courseId)).thenReturn(true);
    when(syllabusRepository.existsByCourseIdAndVersion(courseId, "2024.1")).thenReturn(false);
    when(syllabusMapper.toDocument(courseId, request)).thenReturn(syllabus);
    when(syllabusRepository.save(any(Syllabus.class)))
        .thenThrow(new DuplicateKeyException("duplicate"));

    assertThatThrownBy(() -> syllabusService.createSyllabus(courseId, request))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
  }

  @Test
  @DisplayName("createSyllabus: Tạo mới thành công với status=DRAFT")
  void createSyllabus_Success() {
    when(courseRepository.existsById(courseId)).thenReturn(true);
    when(syllabusRepository.existsByCourseIdAndVersion(courseId, "2024.1")).thenReturn(false);
    when(syllabusMapper.toDocument(courseId, request)).thenReturn(syllabus);
    when(syllabusRepository.save(syllabus)).thenReturn(syllabus);
    when(syllabusMapper.toResponse(syllabus)).thenReturn(response);

    SyllabusResponse result = syllabusService.createSyllabus(courseId, request);
    assertThat(result).isNotNull();
    assertThat(result.getVersion()).isEqualTo("2024.1");
    verify(syllabusRepository).save(syllabus);
  }

  @Test
  @DisplayName(
      "US-10/AC-2: effectiveFromTermId không tồn tại -> ném ResourceNotFoundException (404)")
  void createSyllabus_FromTermNotFound_ThrowsNotFound() {
    request.setEffectiveFromTermId(fromTermId);
    when(courseRepository.existsById(courseId)).thenReturn(true);
    when(syllabusRepository.existsByCourseIdAndVersion(courseId, "2024.1")).thenReturn(false);
    when(termRepository.findById(fromTermId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> syllabusService.createSyllabus(courseId, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("effectiveFromTermId");
  }

  @Test
  @DisplayName(
      "US-10/AC-2: effectiveToTermId trước effectiveFromTermId -> ném BusinessRuleViolationException (422)")
  void createSyllabus_EffectiveToBeforeFrom_ThrowsException() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    Term fromTerm =
        Term.builder()
            .id(fromTermId)
            .code("2026-HK2")
            .startDate(now.plus(180, ChronoUnit.DAYS))
            .build();
    Term toTerm = Term.builder().id(toTermId).code("2026-HK1").startDate(now).build();

    request.setEffectiveFromTermId(fromTermId);
    request.setEffectiveToTermId(toTermId);

    when(courseRepository.existsById(courseId)).thenReturn(true);
    when(syllabusRepository.existsByCourseIdAndVersion(courseId, "2024.1")).thenReturn(false);
    when(termRepository.findById(fromTermId)).thenReturn(Optional.of(fromTerm));
    when(termRepository.findById(toTermId)).thenReturn(Optional.of(toTerm));

    assertThatThrownBy(() -> syllabusService.createSyllabus(courseId, request))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("effectiveToTermId không được trước effectiveFromTermId");
  }

  @Test
  @DisplayName("US-10/AC-2: effectiveToTermId sau effectiveFromTermId -> hợp lệ và tạo thành công")
  void createSyllabus_ValidEffectiveTerms_Success() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    Term fromTerm = Term.builder().id(fromTermId).code("2026-HK1").startDate(now).build();
    Term toTerm =
        Term.builder()
            .id(toTermId)
            .code("2026-HK2")
            .startDate(now.plus(180, ChronoUnit.DAYS))
            .build();

    request.setEffectiveFromTermId(fromTermId);
    request.setEffectiveToTermId(toTermId);

    when(courseRepository.existsById(courseId)).thenReturn(true);
    when(syllabusRepository.existsByCourseIdAndVersion(courseId, "2024.1")).thenReturn(false);
    when(termRepository.findById(fromTermId)).thenReturn(Optional.of(fromTerm));
    when(termRepository.findById(toTermId)).thenReturn(Optional.of(toTerm));
    when(syllabusMapper.toDocument(courseId, request)).thenReturn(syllabus);
    when(syllabusRepository.save(syllabus)).thenReturn(syllabus);
    when(syllabusMapper.toResponse(syllabus)).thenReturn(response);

    SyllabusResponse result = syllabusService.createSyllabus(courseId, request);
    assertThat(result).isNotNull();
    verify(syllabusRepository).save(syllabus);
  }

  @Test
  @DisplayName("updateStatus: Chuyển DRAFT -> CONFIRMED thành công")
  void updateStatus_DraftToConfirmed_Success() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(syllabusRepository.save(any(Syllabus.class))).thenReturn(syllabus);
    when(syllabusMapper.toResponse(any(Syllabus.class))).thenReturn(response);

    SyllabusResponse result = syllabusService.updateStatus(syllabusId, SyllabusStatus.CONFIRMED);
    assertThat(result).isNotNull();
    assertThat(syllabus.getStatus()).isEqualTo(SyllabusStatus.CONFIRMED);
  }

  @Test
  @DisplayName("updateStatus: Chuyển ngược CONFIRMED -> DRAFT bị chặn (422)")
  void updateStatus_ConfirmedToDraft_ThrowsBusinessRuleViolation() {
    syllabus.setStatus(SyllabusStatus.CONFIRMED);
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));

    assertThatThrownBy(() -> syllabusService.updateStatus(syllabusId, SyllabusStatus.DRAFT))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Không thể chuyển trạng thái đề cương");
  }
}
