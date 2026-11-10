package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.OutcomeMappingDto;
import com.hcmute.lms.lmsapi.dto.RubricDto;
import com.hcmute.lms.lmsapi.dto.RubricLevelDto;
import com.hcmute.lms.lmsapi.enums.ItemKind;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
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
@DisplayName("AssessmentItemValidator (US-15/AC-2, US-16/AC-1)")
class AssessmentItemValidatorTest {

  private static final String SYLLABUS = "671dcba5f123456789abc100";
  private static final String CLO1 = "671dcba5f123456789abc001";
  private static final String CLO2 = "671dcba5f123456789abc002";
  private static final String FOREIGN_CLO = "671dcba5f123456789abc003";
  private static final String MISSING_CLO = "671dcba5f123456789abc004";

  @Mock private CloRepository cloRepository;
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

  private OutcomeMappingDto mapping(String cloId, double weight) {
    return OutcomeMappingDto.builder().cloId(cloId).weight(weight).build();
  }

  private AssessmentItemDto item(String code, double maxScore, OutcomeMappingDto... mappings) {
    return AssessmentItemDto.builder()
        .code(code)
        .kind(ItemKind.QUESTION)
        .maxScore(maxScore)
        .outcomeMappings(List.of(mappings))
        .build();
  }

  private void assertRejected(AssessmentItemDto item, String messagePart) {
    assertThatThrownBy(() -> validator.validate(SYLLABUS, List.of(item)))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining(messagePart);
  }

  @Test
  @DisplayName("US-15/AC-2: Σ weight = 1 (một CLO = 1, hoặc 0,5 + 0,5) được chấp nhận")
  void weightsSummingToOneAreAccepted() {
    assertThatCode(
            () ->
                validator.validate(
                    SYLLABUS,
                    List.of(
                        item("Q1", 2, mapping(CLO1, 1.0)),
                        item("Q2", 2, mapping(CLO1, 0.5), mapping(CLO2, 0.5)))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("US-15/AC-2: Σ weight ≠ 1 (0,5; 0,9998; 1,01; 0,5 + 0,6) -> 422")
  void weightsNotSummingToOneAreRejected() {
    assertRejected(item("Q1", 2, mapping(CLO1, 0.5)), "phải bằng 1");
    assertRejected(item("Q1", 2, mapping(CLO1, 0.5), mapping(CLO2, 0.4998)), "phải bằng 1");
    assertRejected(item("Q1", 2, mapping(CLO1, 0.5), mapping(CLO2, 0.51)), "phải bằng 1");
    assertRejected(item("Q1", 2, mapping(CLO1, 0.5), mapping(CLO2, 0.6)), "phải bằng 1");
  }

  @Test
  @DisplayName(
      "US-15/AC-2: sai số nằm trong 0,0001 vẫn được chấp nhận (cùng phép kiểm với MongoDB)")
  void toleranceIsAccepted() {
    assertThatCode(
            () ->
                validator.validate(
                    SYLLABUS, List.of(item("Q1", 2, mapping(CLO1, 0.5), mapping(CLO2, 0.49995)))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("US-15/AC-2: cùng một CLO khai báo hai lần trong một phần tử -> 422")
  void duplicateCloIsRejected() {
    assertRejected(item("Q1", 2, mapping(CLO1, 0.5), mapping(CLO1, 0.5)), "nhiều lần");
  }

  @Test
  @DisplayName("US-15/AC-2: CLO thuộc đề cương khác hoặc không tồn tại -> 422")
  void cloMustExistAndBelongToTheSyllabus() {
    assertRejected(item("Q1", 2, mapping(FOREIGN_CLO, 1.0)), "không thuộc đề cương");
    assertRejected(item("Q1", 2, mapping(MISSING_CLO, 1.0)), "không tồn tại");
  }

  @Test
  @DisplayName("Mã phần tử hoặc id phần tử trùng trong một bài -> 422")
  void duplicateCodeOrIdIsRejected() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    SYLLABUS,
                    List.of(item("Q1", 2, mapping(CLO1, 1.0)), item("Q1", 3, mapping(CLO1, 1.0)))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("bị trùng");

    AssessmentItemDto first = item("Q1", 2, mapping(CLO1, 1.0));
    AssessmentItemDto second = item("Q2", 2, mapping(CLO1, 1.0));
    first.setId("671dcba5f123456789abc777");
    second.setId("671dcba5f123456789abc777");
    assertThatThrownBy(() -> validator.validate(SYLLABUS, List.of(first, second)))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("ID phần tử");
  }

  @Test
  @DisplayName("US-16/AC-1: điểm mức rubric vượt maxScore hoặc mức lặp -> 422; đúng thì chấp nhận")
  void rubricRules() {
    AssessmentItemDto criterion = item("C1", 4, mapping(CLO1, 1.0));
    criterion.setKind(ItemKind.CRITERION);

    criterion.setRubric(
        RubricDto.builder()
            .levels(
                List.of(
                    RubricLevelDto.builder().level(1).score(1.0).build(),
                    RubricLevelDto.builder().level(4).score(4.0).build()))
            .build());
    assertThatCode(() -> validator.validate(SYLLABUS, List.of(criterion)))
        .doesNotThrowAnyException();

    criterion.setRubric(
        RubricDto.builder()
            .levels(List.of(RubricLevelDto.builder().level(4).score(4.5).build()))
            .build());
    assertRejected(criterion, "vượt điểm tối đa");

    criterion.setRubric(
        RubricDto.builder()
            .levels(
                List.of(
                    RubricLevelDto.builder().level(2).score(1.0).build(),
                    RubricLevelDto.builder().level(2).score(2.0).build()))
            .build());
    assertRejected(criterion, "khai báo lặp");
  }

  @Test
  @DisplayName("Danh sách rỗng hoặc null là hợp lệ (bài nháp chưa có phần tử)")
  void emptyListIsValid() {
    assertThatCode(() -> validator.validate(SYLLABUS, List.of())).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(SYLLABUS, null)).doesNotThrowAnyException();
  }
}
