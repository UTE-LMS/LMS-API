package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentCloWeight;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.dto.AssessmentAlignmentResponse;
import com.hcmute.lms.lmsapi.dto.AssessmentAlignmentResponse.CloAlignment;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssessmentAlignmentServiceImpl")
class AssessmentAlignmentServiceImplTest {

  @Mock private CourseSectionRepository courseSectionRepository;
  @Mock private AssessmentRepository assessmentRepository;
  @Mock private CloRepository cloRepository;
  @Mock private CloPiLinkRepository cloPiLinkRepository;
  @Mock private PiRepository piRepository;
  @Mock private PloRepository ploRepository;

  @InjectMocks private AssessmentAlignmentServiceImpl service;

  private static Clo clo(String id, String code, Integer bloom) {
    Clo c = new Clo();
    c.setId(id);
    c.setCode(code);
    c.setBloomLevel(bloom);
    return c;
  }

  private static AssessmentItem item(String cloId, double max, Integer bloom) {
    OutcomeMapping m = new OutcomeMapping();
    m.setCloId(cloId);
    m.setMaxScore(max);
    AssessmentItem i = new AssessmentItem();
    i.setBloomLevel(bloom);
    i.getOutcomeMappings().add(m);
    return i;
  }

  private static AssessmentCloWeight weight(String cloId, double w) {
    AssessmentCloWeight cw = new AssessmentCloWeight();
    cw.setCloId(cloId);
    cw.setWeight(w);
    return cw;
  }

  private void stubSection() {
    CourseSection section = new CourseSection();
    section.setId("sec1");
    section.setCode("SEC_01");
    section.setCourseId("course1");
    section.setSyllabusId("syl1");
    when(courseSectionRepository.findById("sec1")).thenReturn(Optional.of(section));
  }

  @Test
  @DisplayName("lớp không tồn tại -> 404")
  void sectionNotFound() {
    when(courseSectionRepository.findById("x")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getSectionAlignment("x"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName(
      "CLO không có câu hỏi nào -> NOT_ASSESSED; CLO đủ tỷ lệ, Bloom khớp -> không cảnh báo")
  void notAssessedAndHealthyClo() {
    stubSection();
    when(cloRepository.findBySyllabusIdOrderByCodeAsc("syl1"))
        .thenReturn(List.of(clo("c1", "CLO1", 2), clo("c2", "CLO2", 3)));

    Assessment a1 = new Assessment();
    a1.setId("a1");
    a1.setCode("BT1");
    a1.getItems().add(item("c1", 4.0, 2));
    a1.getItems().add(item("c1", 6.0, 3));
    a1.getCloWeights().add(weight("c1", 1.0));
    when(assessmentRepository.findBySectionIdOrderByCodeAsc("sec1")).thenReturn(List.of(a1));
    when(cloPiLinkRepository.findBySyllabusId("syl1")).thenReturn(List.of());

    AssessmentAlignmentResponse r = service.getSectionAlignment("sec1");

    assertThat(r.getTotalPoints()).isEqualTo(10.0);
    CloAlignment c1 = r.getClos().get(0);
    assertThat(c1.getPoints()).isEqualTo(10.0);
    assertThat(c1.getPercentage()).isEqualTo(100.0);
    assertThat(c1.getTotalWeight()).isEqualTo(1.0);
    assertThat(c1.getMaxItemBloomLevel()).isEqualTo(3);
    assertThat(c1.getWarnings()).isEmpty();
    assertThat(c1.getAssessments()).hasSize(1);
    assertThat(c1.getAssessments().get(0).getQuestionCount()).isEqualTo(2);

    CloAlignment c2 = r.getClos().get(1);
    assertThat(c2.getWarnings()).containsExactly(AssessmentAlignmentResponse.NOT_ASSESSED);
    assertThat(c2.getPercentage()).isEqualTo(0.0);
    assertThat(r.getCloWithWarningCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("tổng tỷ lệ != 1.0 và Bloom câu hỏi thấp hơn CLO -> cảnh báo")
  void weightAndBloomWarnings() {
    stubSection();
    when(cloRepository.findBySyllabusIdOrderByCodeAsc("syl1"))
        .thenReturn(List.of(clo("c1", "CLO1", 4)));
    Assessment a1 = new Assessment();
    a1.setId("a1");
    a1.setCode("BT1");
    a1.getItems().add(item("c1", 5.0, 1));
    a1.getCloWeights().add(weight("c1", 0.4));
    when(assessmentRepository.findBySectionIdOrderByCodeAsc("sec1")).thenReturn(List.of(a1));
    when(cloPiLinkRepository.findBySyllabusId("syl1")).thenReturn(List.of());

    CloAlignment c1 = service.getSectionAlignment("sec1").getClos().get(0);

    assertThat(c1.getWarnings())
        .containsExactly(
            AssessmentAlignmentResponse.WEIGHT_NOT_FULL,
            AssessmentAlignmentResponse.BLOOM_BELOW_CLO);
  }

  @Test
  @DisplayName("câu hỏi không khai báo Bloom -> BLOOM_UNDECLARED")
  void bloomUndeclared() {
    stubSection();
    when(cloRepository.findBySyllabusIdOrderByCodeAsc("syl1"))
        .thenReturn(List.of(clo("c1", "CLO1", 2)));
    Assessment a1 = new Assessment();
    a1.setId("a1");
    a1.getItems().add(item("c1", 5.0, null));
    a1.getCloWeights().add(weight("c1", 1.0));
    when(assessmentRepository.findBySectionIdOrderByCodeAsc("sec1")).thenReturn(List.of(a1));
    when(cloPiLinkRepository.findBySyllabusId("syl1")).thenReturn(List.of());

    CloAlignment c1 = service.getSectionAlignment("sec1").getClos().get(0);
    assertThat(c1.getWarnings()).containsExactly(AssessmentAlignmentResponse.BLOOM_UNDECLARED);
  }

  @Test
  @DisplayName("liên kết CLO -> PI -> PLO được trả kèm trọng số")
  void linkedPiAndPlo() {
    stubSection();
    when(cloRepository.findBySyllabusIdOrderByCodeAsc("syl1"))
        .thenReturn(List.of(clo("c1", "CLO1", 2)));
    when(assessmentRepository.findBySectionIdOrderByCodeAsc("sec1")).thenReturn(List.of());

    CloPiLink link = new CloPiLink();
    link.setCloId("c1");
    link.setPiId("p1");
    link.setWeight(0.3);
    when(cloPiLinkRepository.findBySyllabusId("syl1")).thenReturn(List.of(link));
    Pi pi = new Pi();
    pi.setId("p1");
    pi.setCode("PI1.1");
    pi.setPloId("l1");
    when(piRepository.findAllById(any(Iterable.class))).thenReturn(List.of(pi));
    Plo plo = new Plo();
    plo.setId("l1");
    plo.setCode("PLO1");
    when(ploRepository.findAllById(any(Iterable.class))).thenReturn(List.of(plo));

    CloAlignment c1 = service.getSectionAlignment("sec1").getClos().get(0);

    assertThat(c1.getLinkedPis()).hasSize(1);
    assertThat(c1.getLinkedPis().get(0).getPiCode()).isEqualTo("PI1.1");
    assertThat(c1.getLinkedPis().get(0).getPloCode()).isEqualTo("PLO1");
    assertThat(c1.getLinkedPis().get(0).getWeight()).isEqualTo(0.3);
  }
}
