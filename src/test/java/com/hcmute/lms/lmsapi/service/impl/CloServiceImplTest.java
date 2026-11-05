package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.dto.CloRequest;
import com.hcmute.lms.lmsapi.dto.CloResponse;
import com.hcmute.lms.lmsapi.dto.CloUpdateRequest;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.CloMapper;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
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
@DisplayName("CloServiceImpl Unit Tests (F03 / US-11)")
class CloServiceImplTest {

  @Mock private CloRepository cloRepository;
  @Mock private SyllabusRepository syllabusRepository;
  @Mock private CloPiLinkRepository cloPiLinkRepository;
  @Mock private CloMapper cloMapper;

  @InjectMocks private CloServiceImpl cloService;

  private String syllabusId;
  private String courseId;
  private String cloId;
  private Syllabus syllabus;
  private Clo clo;
  private CloRequest request;
  private CloResponse response;

  @BeforeEach
  void setUp() {
    syllabusId = new ObjectId().toHexString();
    courseId = new ObjectId().toHexString();
    cloId = new ObjectId().toHexString();

    syllabus =
        Syllabus.builder()
            .id(syllabusId)
            .courseId(courseId)
            .status(SyllabusStatus.DRAFT)
            .version("2024.1")
            .build();

    clo =
        Clo.builder()
            .id(cloId)
            .syllabusId(syllabusId)
            .courseId(courseId)
            .code("CLO1")
            .bloomLevel(3)
            .build();

    request =
        CloRequest.builder()
            .code("CLO1")
            .bloomLevel(3)
            .contentOriginal("Nắm được cú pháp Java OOP")
            .build();

    response =
        CloResponse.builder()
            .id(cloId)
            .syllabusId(syllabusId)
            .courseId(courseId)
            .code("CLO1")
            .bloomLevel(3)
            .build();
  }

  @Test
  @DisplayName("getClos: Ném ResourceNotFoundException khi đề cương không tồn tại")
  void getClos_SyllabusNotFound_ThrowsNotFound() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> cloService.getClos(syllabusId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(syllabusId);
  }

  @Test
  @DisplayName("getClos: Thành công khi đề cương tồn tại")
  void getClos_Success() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(cloRepository.findBySyllabusIdOrderByCodeAsc(syllabusId)).thenReturn(List.of(clo));
    when(cloMapper.toResponseList(List.of(clo))).thenReturn(List.of(response));

    List<CloResponse> result = cloService.getClos(syllabusId);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCode()).isEqualTo("CLO1");
  }

  @Test
  @DisplayName("createClo: Chặn thêm CLO khi đề cương đã CONFIRMED (US-10/AC-3)")
  void createClo_WhenSyllabusConfirmed_ThrowsBusinessRuleViolation() {
    syllabus.setStatus(SyllabusStatus.CONFIRMED);
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));

    assertThatThrownBy(() -> cloService.createClo(syllabusId, request))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("không cho phép thêm CLO mới");
  }

  @Test
  @DisplayName("createClo: Chặn thêm CLO khi đề cương đã ARCHIVED (TD-27)")
  void createClo_WhenSyllabusArchived_ThrowsBusinessRuleViolation() {
    syllabus.setStatus(SyllabusStatus.ARCHIVED);
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));

    assertThatThrownBy(() -> cloService.createClo(syllabusId, request))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("không cho phép thêm CLO mới");
  }

  @Test
  @DisplayName("createClo: Ném CONFLICT (409) khi trùng mã CLO trong cùng đề cương (US-11/AC-2)")
  void createClo_DuplicateCodeInSameSyllabus_ThrowsConflict() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(cloRepository.existsBySyllabusIdAndCode(syllabusId, "CLO1")).thenReturn(true);

    assertThatThrownBy(() -> cloService.createClo(syllabusId, request))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
  }

  @Test
  @DisplayName("createClo: Bắt DuplicateKeyException ném CONFLICT (409)")
  void createClo_DuplicateKeyException_ThrowsConflict() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(cloRepository.existsBySyllabusIdAndCode(syllabusId, "CLO1")).thenReturn(false);
    when(cloMapper.toDocument(syllabusId, courseId, request)).thenReturn(clo);
    when(cloRepository.save(clo)).thenThrow(new DuplicateKeyException("E11000 duplicate key"));

    assertThatThrownBy(() -> cloService.createClo(syllabusId, request))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
  }

  @Test
  @DisplayName("createClo: Tạo CLO thành công")
  void createClo_Success() {
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(cloRepository.existsBySyllabusIdAndCode(syllabusId, "CLO1")).thenReturn(false);
    when(cloMapper.toDocument(syllabusId, courseId, request)).thenReturn(clo);
    when(cloRepository.save(clo)).thenReturn(clo);
    when(cloMapper.toResponse(clo)).thenReturn(response);

    CloResponse result = cloService.createClo(syllabusId, request);
    assertThat(result).isNotNull();
    assertThat(result.getCode()).isEqualTo("CLO1");
    verify(cloRepository).save(clo);
  }

  @Test
  @DisplayName("updateClo: Chặn sửa CLO khi đề cương đã CONFIRMED (US-10/AC-3)")
  void updateClo_WhenSyllabusConfirmed_ThrowsBusinessRuleViolation() {
    syllabus.setStatus(SyllabusStatus.CONFIRMED);
    when(cloRepository.findById(cloId)).thenReturn(Optional.of(clo));
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));

    CloUpdateRequest updateReq = CloUpdateRequest.builder().code("CLO1").bloomLevel(4).build();

    assertThatThrownBy(() -> cloService.updateClo(cloId, updateReq))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("không cho phép sửa nội dung CLO");
  }

  @Test
  @DisplayName("updateClo: Cập nhật thành công khi đề cương DRAFT")
  void updateClo_Success() {
    when(cloRepository.findById(cloId)).thenReturn(Optional.of(clo));
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(cloRepository.save(any(Clo.class))).thenReturn(clo);
    when(cloMapper.toResponse(any(Clo.class))).thenReturn(response);

    CloUpdateRequest updateReq = CloUpdateRequest.builder().code("CLO1").bloomLevel(4).build();

    CloResponse result = cloService.updateClo(cloId, updateReq);
    assertThat(result).isNotNull();
    verify(cloRepository).save(clo);
  }

  @Test
  @DisplayName("deleteClo: Chặn xóa khi CLO đã được liên kết trong clo_pi_links (Restrict delete)")
  void deleteClo_WhenLinkedToPi_ThrowsBusinessRuleViolation() {
    when(cloRepository.findById(cloId)).thenReturn(Optional.of(clo));
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(cloPiLinkRepository.existsByCloId(cloId)).thenReturn(true);

    assertThatThrownBy(() -> cloService.deleteClo(cloId))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Không thể xóa CLO đã được liên kết với PI");
  }

  @Test
  @DisplayName("deleteClo: Xóa thành công khi đề cương DRAFT và chưa liên kết")
  void deleteClo_Success() {
    when(cloRepository.findById(cloId)).thenReturn(Optional.of(clo));
    when(syllabusRepository.findById(syllabusId)).thenReturn(Optional.of(syllabus));
    when(cloPiLinkRepository.existsByCloId(cloId)).thenReturn(false);

    cloService.deleteClo(cloId);
    verify(cloRepository).deleteById(cloId);
  }
}
