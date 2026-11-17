package com.hcmute.lms.lmsapi.service.impl;

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
import com.hcmute.lms.lmsapi.dto.AssessmentAlignmentResponse.AssessmentUsage;
import com.hcmute.lms.lmsapi.dto.AssessmentAlignmentResponse.BloomUsage;
import com.hcmute.lms.lmsapi.dto.AssessmentAlignmentResponse.CloAlignment;
import com.hcmute.lms.lmsapi.dto.AssessmentAlignmentResponse.PiContribution;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.service.AssessmentAlignmentService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Đối chiếu đề cương (CLO + mức Bloom) với ma trận đề của mọi bài trong lớp (items, cloWeights) và
 * liên kết CLO–PI trong clo_pi_links. Chỉ đọc; Bloom chỉ dùng để thống kê, không quy đổi ra điểm.
 */
@Service
@RequiredArgsConstructor
public class AssessmentAlignmentServiceImpl implements AssessmentAlignmentService {

  /** Sai số khi so sánh tổng tỷ lệ đóng góp với 1.0 (số thực). */
  private static final double WEIGHT_EPSILON = 1e-6;

  private final CourseSectionRepository courseSectionRepository;
  private final AssessmentRepository assessmentRepository;
  private final CloRepository cloRepository;
  private final CloPiLinkRepository cloPiLinkRepository;
  private final PiRepository piRepository;
  private final PloRepository ploRepository;

  @Override
  public AssessmentAlignmentResponse getSectionAlignment(String sectionId) {
    CourseSection section =
        courseSectionRepository
            .findById(sectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Lớp học phần", "id", sectionId));

    List<Clo> clos = cloRepository.findBySyllabusIdOrderByCodeAsc(section.getSyllabusId());
    List<Assessment> assessments = assessmentRepository.findBySectionIdOrderByCodeAsc(sectionId);
    Map<String, List<PiContribution>> pisByClo = loadPiContributions(section.getSyllabusId());

    List<CloAlignment> cloAlignments = new ArrayList<>();
    double totalPoints = 0.0;
    for (Clo clo : clos) {
      CloAlignment alignment = buildCloAlignment(clo, assessments, pisByClo);
      totalPoints += alignment.getPoints();
      cloAlignments.add(alignment);
    }
    final double grandTotal = totalPoints;
    cloAlignments.forEach(
        a -> a.setPercentage(grandTotal > 0 ? a.getPoints() / grandTotal * 100.0 : null));

    int withWarning = (int) cloAlignments.stream().filter(a -> !a.getWarnings().isEmpty()).count();
    return AssessmentAlignmentResponse.builder()
        .sectionId(section.getId())
        .sectionCode(section.getCode())
        .courseId(section.getCourseId())
        .syllabusId(section.getSyllabusId())
        .assessmentCount(assessments.size())
        .totalPoints(totalPoints)
        .clos(cloAlignments)
        .cloWithWarningCount(withWarning)
        .build();
  }

  private CloAlignment buildCloAlignment(
      Clo clo, List<Assessment> assessments, Map<String, List<PiContribution>> pisByClo) {
    List<AssessmentUsage> usages = new ArrayList<>();
    Map<Integer, int[]> questionCountByBloom =
        new TreeMap<>(Comparator.nullsFirst(Integer::compare));
    Map<Integer, Double> pointsByBloom = new HashMap<>();
    double points = 0.0;
    double totalWeight = 0.0;
    Integer maxBloom = null;

    for (Assessment assessment : assessments) {
      int questionCount = 0;
      double assessmentPoints = 0.0;
      for (AssessmentItem item : safe(assessment.getItems())) {
        for (OutcomeMapping mapping : safe(item.getOutcomeMappings())) {
          if (!clo.getId().equals(mapping.getCloId()) || mapping.getMaxScore() == null) {
            continue;
          }
          questionCount++;
          assessmentPoints += mapping.getMaxScore();
          Integer bloom = item.getBloomLevel();
          questionCountByBloom.computeIfAbsent(bloom, k -> new int[1])[0]++;
          pointsByBloom.merge(bloom, mapping.getMaxScore(), Double::sum);
          if (bloom != null && (maxBloom == null || bloom > maxBloom)) {
            maxBloom = bloom;
          }
        }
      }
      if (questionCount == 0) {
        continue;
      }
      double weight = 0.0;
      for (AssessmentCloWeight w : safe(assessment.getCloWeights())) {
        if (clo.getId().equals(w.getCloId()) && w.getWeight() != null) {
          weight = w.getWeight();
        }
      }
      points += assessmentPoints;
      totalWeight += weight;
      usages.add(
          AssessmentUsage.builder()
              .assessmentId(assessment.getId())
              .assessmentCode(assessment.getCode())
              .type(assessment.getType())
              .status(assessment.getStatus())
              .questionCount(questionCount)
              .points(assessmentPoints)
              .weight(weight)
              .build());
    }

    List<BloomUsage> bloomUsages = new ArrayList<>();
    questionCountByBloom.forEach(
        (bloom, count) ->
            bloomUsages.add(
                BloomUsage.builder()
                    .bloomLevel(bloom)
                    .questionCount(count[0])
                    .points(pointsByBloom.getOrDefault(bloom, 0.0))
                    .build()));

    List<String> warnings = new ArrayList<>();
    if (usages.isEmpty()) {
      warnings.add(AssessmentAlignmentResponse.NOT_ASSESSED);
    } else {
      if (Math.abs(totalWeight - 1.0) > WEIGHT_EPSILON) {
        warnings.add(AssessmentAlignmentResponse.WEIGHT_NOT_FULL);
      }
      if (clo.getBloomLevel() == null || maxBloom == null) {
        warnings.add(AssessmentAlignmentResponse.BLOOM_UNDECLARED);
      } else if (maxBloom < clo.getBloomLevel()) {
        warnings.add(AssessmentAlignmentResponse.BLOOM_BELOW_CLO);
      }
    }

    return CloAlignment.builder()
        .cloId(clo.getId())
        .cloCode(clo.getCode())
        .cloBloomLevel(clo.getBloomLevel())
        .points(points)
        .totalWeight(totalWeight)
        .maxItemBloomLevel(maxBloom)
        .assessments(usages)
        .itemBloom(bloomUsages)
        .linkedPis(pisByClo.getOrDefault(clo.getId(), List.of()))
        .warnings(warnings)
        .build();
  }

  /** Gom liên kết CLO→PI→PLO của đề cương theo cloId. */
  private Map<String, List<PiContribution>> loadPiContributions(String syllabusId) {
    List<CloPiLink> links = cloPiLinkRepository.findBySyllabusId(syllabusId);
    Map<String, Pi> pis = new HashMap<>();
    piRepository
        .findAllById(links.stream().map(CloPiLink::getPiId).distinct().toList())
        .forEach(pi -> pis.put(pi.getId(), pi));
    Map<String, Plo> plos = new HashMap<>();
    ploRepository
        .findAllById(
            pis.values().stream().map(Pi::getPloId).filter(id -> id != null).distinct().toList())
        .forEach(plo -> plos.put(plo.getId(), plo));

    Map<String, List<PiContribution>> result = new HashMap<>();
    for (CloPiLink link : links) {
      Pi pi = pis.get(link.getPiId());
      if (pi == null) {
        continue;
      }
      Plo plo = pi.getPloId() != null ? plos.get(pi.getPloId()) : null;
      result
          .computeIfAbsent(link.getCloId(), k -> new ArrayList<>())
          .add(
              PiContribution.builder()
                  .piId(pi.getId())
                  .piCode(pi.getCode())
                  .ploId(pi.getPloId())
                  .ploCode(plo != null ? plo.getCode() : null)
                  .weight(link.getWeight())
                  .build());
    }
    return result;
  }

  private static <T> List<T> safe(List<T> list) {
    return list != null ? list : List.of();
  }
}
