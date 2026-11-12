package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.dto.AssessmentCloWeightDto;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.OutcomeMappingDto;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssessmentItemValidator (ADR 0014, v0.7)")
class AssessmentItemValidatorTest {

  private static final String SYLLABUS = "671dcba5f123456789abc100";
  private static final String CLO1 = "671dcba5f123456789abc001";
  private static final String CLO2 = "671dcba5f123456789abc002";
  private static final String FOREIGN_CLO = "671dcba5f123456789abc003";
  private static final String MISSING_CLO = "671dcba5f123456789abc004";

  @Mock private CloRepository cloRepository;
  @Mock private AssessmentRepository assessmentRepository;
  @InjectMocks private AssessmentItemValidator validator;

  @BeforeEach
  void stubClos() {
    lenient()
        .when(cloRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<String>>any()))
        .thenAnswer(
            invocation -> {
              Iterable<String> ids = invocation.getArgument(0);
              List<Clo> found = new ArrayList<>();
              for (String id : ids) {
                if (CLO1.equals(id) || CLO2.equals(id)) {
                  found.add(
                      Clo.builder()
                          .id(id)
                          .code("C" + id.charAt(id.length() - 1))
                          .syllabusId(SYLLABUS)
                          .build());
                } else if (FOREIGN_CLO.equals(id)) {
                  found.add(
                      Clo.builder()
                          .id(id)
                          .code("X1")
                          .syllabusId("671dcba5f123456789abc999")
                          .build());
                }
              }
              return found;
            });
  }

  private OutcomeMappingDto mapping(String cloId, double maxScore) {
    return OutcomeMappingDto.builder().cloId(cloId).maxScore(maxScore).build();
  }

  private AssessmentItemDto item(String code, OutcomeMappingDto... mappings) {
    return AssessmentItemDto.builder()
        .code(code)
        .outcomeMappings(List.of(mappings))
        .build();
  }

  private void assertRejected(AssessmentItemDto item, String messagePart) {
    assertThatThrownBy(() -> validator.validate(SYLLABUS, List.of(item)))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining(messagePart);
  }

  @Test
  @DisplayName("OutcomeMapping hợp lệ (maxScore > 0, CLO thuộc syllabus) được chấp nhận")
  void validMappingsAreAccepted() {
    assertThatCode(
            () ->
                validator.validate(
                    SYLLABUS,
                    List.of(
                        item("Q1", mapping(CLO1, 2.0)),
                        item("Q2", mapping(CLO1, 1.0), mapping(CLO2, 2.0)))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("maxScore <= 0 -> 422")
  void nonPositiveMaxScoreIsRejected() {
    assertRejected(item("Q1", mapping(CLO1, 0.0)), "maxScore");
    assertRejected(item("Q1", mapping(CLO1, -1.0)), "maxScore");
  }

  @Test
  @DisplayName("Cùng một CLO khai báo hai lần trong một phần tử -> 422")
  void duplicateCloIsRejected() {
    assertRejected(item("Q1", mapping(CLO1, 1.0), mapping(CLO1, 2.0)), "nhiều lần");
  }

  @Test
  @DisplayName("CLO thuộc đề cương khác hoặc không tồn tại -> 422")
  void cloMustExistAndBelongToTheSyllabus() {
    assertRejected(item("Q1", mapping(FOREIGN_CLO, 1.0)), "không thuộc đề cương");
    assertRejected(item("Q1", mapping(MISSING_CLO, 1.0)), "không tồn tại");
  }

  @Test
  @DisplayName("Mã phần tử hoặc id phần tử trùng trong một bài -> 422")
  void duplicateCodeOrIdIsRejected() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    SYLLABUS,
                    List.of(item("Q1", mapping(CLO1, 1.0)), item("Q1", mapping(CLO1, 2.0)))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("bị trùng");

    AssessmentItemDto first = item("Q1", mapping(CLO1, 1.0));
    AssessmentItemDto second = item("Q2", mapping(CLO1, 2.0));
    first.setId("671dcba5f123456789abc777");
    second.setId("671dcba5f123456789abc777");
    assertThatThrownBy(() -> validator.validate(SYLLABUS, List.of(first, second)))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("ID phần tử");
  }

  @Test
  @DisplayName("validateCloWeights: tập CLO phải khớp chính xác với CLO xuất hiện trong câu hỏi")
  void cloWeightsMustMatchItems() {
    List<AssessmentItemDto> items = List.of(item("Q1", mapping(CLO1, 4.0)));
    List<AssessmentCloWeightDto> cloWeightsMismatch =
        List.of(AssessmentCloWeightDto.builder().cloId(CLO2).weight(0.5).build());

    assertThatThrownBy(
            () -> validator.validateCloWeights(SYLLABUS, null, null, cloWeightsMismatch, items))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("khớp chính xác");

    List<AssessmentCloWeightDto> validWeights =
        List.of(AssessmentCloWeightDto.builder().cloId(CLO1).weight(0.5).build());
    assertThatCode(() -> validator.validateCloWeights(SYLLABUS, null, null, validWeights, items))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Danh sách rỗng hoặc null là hợp lệ (bài nháp chưa có phần tử)")
  void emptyListIsValid() {
    assertThatCode(() -> validator.validate(SYLLABUS, List.of())).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(SYLLABUS, null)).doesNotThrowAnyException();
  }
}
