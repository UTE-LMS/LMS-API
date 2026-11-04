package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.TermRequest;
import com.hcmute.lms.lmsapi.dto.TermResponse;
import com.hcmute.lms.lmsapi.dto.TermStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.mapper.TermMapper;
import com.hcmute.lms.lmsapi.mapper.impl.TermMapperImpl;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TermServiceImplTest {

  @Mock private TermRepository termRepository;
  @Mock private SyllabusRepository syllabusRepository;
  @Mock private CourseSectionRepository courseSectionRepository;

  private final TermMapper termMapper = new TermMapperImpl();
  private TermServiceImpl termService;

  private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

  @BeforeEach
  void setUp() {
    termService =
        new TermServiceImpl(
            termRepository, syllabusRepository, courseSectionRepository, termMapper);
  }

  @Test
  @DisplayName("US-18/AC-1: Tạo học kỳ thành công khi startDate <= endDate")
  void createTerm_success() {
    Instant start = now;
    Instant end = now.plus(150, ChronoUnit.DAYS);
    TermRequest request =
        TermRequest.builder()
            .code("2026-HK1")
            .academicYear("2025-2026")
            .startDate(start)
            .endDate(end)
            .build();

    when(termRepository.existsByCode("2026-HK1")).thenReturn(false);
    when(termRepository.save(any(Term.class)))
        .thenAnswer(
            invocation -> {
              Term t = invocation.getArgument(0);
              t.setId("507f1f77bcf86cd799439011");
              return t;
            });

    TermResponse response = termService.createTerm(request);

    assertThat(response.getId()).isEqualTo("507f1f77bcf86cd799439011");
    assertThat(response.getCode()).isEqualTo("2026-HK1");
    assertThat(response.getStatus()).isEqualTo(TermStatus.OPEN);
  }

  @Test
  @DisplayName("US-18/AC-1: Trùng mã học kỳ (code) -> ném ngoại lệ xung đột HTTP 409")
  void createTerm_duplicateCode_throwsConflict() {
    TermRequest request = TermRequest.builder().code("2026-HK1").build();
    when(termRepository.existsByCode("2026-HK1")).thenReturn(true);

    assertThatThrownBy(() -> termService.createTerm(request))
        .isInstanceOf(AppException.class)
        .satisfies(
            ex -> {
              AppException appEx = (AppException) ex;
              assertThat(appEx.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
            });
    verify(termRepository, never()).save(any());
  }

  @Test
  @DisplayName("US-18/AC-1: startDate > endDate -> ném BusinessRuleViolationException HTTP 422")
  void createTerm_invalidDateRange_throwsException() {
    Instant start = now.plus(10, ChronoUnit.DAYS);
    Instant end = now;
    TermRequest request =
        TermRequest.builder().code("2026-HK1").startDate(start).endDate(end).build();

    when(termRepository.existsByCode("2026-HK1")).thenReturn(false);

    assertThatThrownBy(() -> termService.createTerm(request))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("startDate) không được sau");
    verify(termRepository, never()).save(any());
  }

  @Test
  @DisplayName("US-18/AC-2: Đổi trạng thái sang SCORE_LOCKED thành công")
  void updateStatus_toScoreLocked_success() {
    Term term =
        Term.builder()
            .id("507f1f77bcf86cd799439011")
            .code("2026-HK1")
            .status(TermStatus.OPEN)
            .build();
    when(termRepository.findById("507f1f77bcf86cd799439011")).thenReturn(Optional.of(term));
    when(termRepository.save(any(Term.class))).thenAnswer(i -> i.getArgument(0));

    TermStatusUpdateRequest request =
        TermStatusUpdateRequest.builder().status(TermStatus.SCORE_LOCKED).build();

    TermResponse response = termService.updateStatus("507f1f77bcf86cd799439011", request, "admin");

    assertThat(response.getStatus()).isEqualTo(TermStatus.SCORE_LOCKED);
  }

  @Test
  @DisplayName(
      "US-18/AC-3: Mở lại học kỳ SCORE_LOCKED -> OPEN khi có lý do giải trình -> thành công")
  void updateStatus_reopenWithReason_success() {
    Term term =
        Term.builder()
            .id("507f1f77bcf86cd799439011")
            .code("2026-HK1")
            .status(TermStatus.SCORE_LOCKED)
            .build();
    when(termRepository.findById("507f1f77bcf86cd799439011")).thenReturn(Optional.of(term));
    when(termRepository.save(any(Term.class))).thenAnswer(i -> i.getArgument(0));

    TermStatusUpdateRequest request =
        TermStatusUpdateRequest.builder()
            .status(TermStatus.OPEN)
            .reason("Phúc khảo điểm theo quyết định số 123/QĐ-ĐHSPKT")
            .build();

    TermResponse response = termService.updateStatus("507f1f77bcf86cd799439011", request, "admin");

    assertThat(response.getStatus()).isEqualTo(TermStatus.OPEN);
  }

  @Test
  @DisplayName("US-18/AC-3: Mở lại học kỳ SCORE_LOCKED -> OPEN thiếu lý do -> ném ngoại lệ 422")
  void updateStatus_reopenWithoutReason_throwsException() {
    Term term =
        Term.builder()
            .id("507f1f77bcf86cd799439011")
            .code("2026-HK1")
            .status(TermStatus.SCORE_LOCKED)
            .build();
    when(termRepository.findById("507f1f77bcf86cd799439011")).thenReturn(Optional.of(term));

    TermStatusUpdateRequest request =
        TermStatusUpdateRequest.builder().status(TermStatus.OPEN).reason("   ").build();

    assertThatThrownBy(() -> termService.updateStatus("507f1f77bcf86cd799439011", request, "admin"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Cần nêu rõ lý do giải trình");
  }

  @Test
  @DisplayName("deleteTerm: Chặn xóa khi học kỳ đang được áp dụng trong đề cương (syllabi) -> 422")
  void deleteTerm_hasSyllabi_throwsException() {
    Term term = Term.builder().id("507f1f77bcf86cd799439011").code("2026-HK1").build();
    when(termRepository.findById("507f1f77bcf86cd799439011")).thenReturn(Optional.of(term));
    when(syllabusRepository.existsByEffectiveFromTermIdOrEffectiveToTermId(
            "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439011"))
        .thenReturn(true);

    assertThatThrownBy(() -> termService.deleteTerm("507f1f77bcf86cd799439011"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("đang được áp dụng trong đề cương");
    verify(termRepository, never()).delete(any());
  }

  @Test
  @DisplayName("deleteTerm: Chặn xóa khi học kỳ đang có lớp học phần -> 422")
  void deleteTerm_hasSections_throwsException() {
    Term term = Term.builder().id("507f1f77bcf86cd799439011").code("2026-HK1").build();
    when(termRepository.findById("507f1f77bcf86cd799439011")).thenReturn(Optional.of(term));
    when(syllabusRepository.existsByEffectiveFromTermIdOrEffectiveToTermId(
            "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439011"))
        .thenReturn(false);
    when(courseSectionRepository.existsByTermId("507f1f77bcf86cd799439011")).thenReturn(true);

    assertThatThrownBy(() -> termService.deleteTerm("507f1f77bcf86cd799439011"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("đang có lớp học phần");
    verify(termRepository, never()).delete(any());
  }

  @Test
  @DisplayName("deleteTerm: Xóa thành công khi không có tham chiếu")
  void deleteTerm_success() {
    Term term = Term.builder().id("507f1f77bcf86cd799439011").code("2026-HK1").build();
    when(termRepository.findById("507f1f77bcf86cd799439011")).thenReturn(Optional.of(term));
    when(syllabusRepository.existsByEffectiveFromTermIdOrEffectiveToTermId(
            "507f1f77bcf86cd799439011", "507f1f77bcf86cd799439011"))
        .thenReturn(false);
    when(courseSectionRepository.existsByTermId("507f1f77bcf86cd799439011")).thenReturn(false);

    termService.deleteTerm("507f1f77bcf86cd799439011");

    verify(termRepository).delete(term);
  }
}
