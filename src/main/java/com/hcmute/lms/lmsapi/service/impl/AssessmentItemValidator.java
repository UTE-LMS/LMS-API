package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.dto.AssessmentCloWeightDto;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.OutcomeMappingDto;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra quy tắc nghiệp vụ của items[] và cloWeights trước khi ghi (ADR 0014, DB Schema v0.7).
 */
@Component
@RequiredArgsConstructor
public class AssessmentItemValidator {

  private final CloRepository cloRepository;
  private final AssessmentRepository assessmentRepository;

  public void validate(String syllabusId, List<AssessmentItemDto> items) {
    if (items == null || items.isEmpty()) {
      return;
    }
    Set<String> codes = new HashSet<>();
    Set<String> ids = new HashSet<>();
    Set<String> allCloIds = new LinkedHashSet<>();

    for (AssessmentItemDto item : items) {
      if (!codes.add(item.getCode())) {
        throw rule("Mã phần tử [" + item.getCode() + "] bị trùng trong bài đánh giá");
      }
      if (item.getId() != null && !ids.add(item.getId())) {
        throw rule("ID phần tử [" + item.getId() + "] bị trùng trong bài đánh giá");
      }
      validateMappings(item);
      item.getOutcomeMappings().forEach(m -> allCloIds.add(m.getCloId()));
    }
    validateClosBelongToSyllabus(syllabusId, allCloIds);
  }

  private void validateMappings(AssessmentItemDto item) {
    if (item.getOutcomeMappings() == null || item.getOutcomeMappings().isEmpty()) {
      throw rule("Phần tử [" + item.getCode() + "] phải có ít nhất một outcome mapping");
    }
    Set<String> cloIds = new HashSet<>();
    for (OutcomeMappingDto mapping : item.getOutcomeMappings()) {
      if (!cloIds.add(mapping.getCloId())) {
        throw rule(
            "Phần tử ["
                + item.getCode()
                + "]: CLO ["
                + mapping.getCloId()
                + "] được khai báo nhiều lần");
      }
      if (mapping.getMaxScore() == null || mapping.getMaxScore() <= 0) {
        throw rule(
            "Phần tử ["
                + item.getCode()
                + "]: maxScore của CLO ["
                + mapping.getCloId()
                + "] phải lớn hơn 0");
      }
    }
  }

  /**
   * Kiểm tra tính hợp lệ của cloWeights: 1. Không trùng cloId 2. Trọng số mỗi CLO trong (0, 1] 3.
   * Mọi cloId trong items[] phải có trong cloWeights và ngược lại 4. Tổng trọng số tích lũy của
   * từng CLO qua các bài đã LOCKED / SCORE_ENTERED trong cùng sectionId không vượt quá 1.0 (100%)
   */
  public void validateCloWeights(
      String syllabusId,
      String sectionId,
      String currentAssessmentId,
      List<AssessmentCloWeightDto> cloWeights,
      List<AssessmentItemDto> items) {
    if (cloWeights == null || cloWeights.isEmpty()) {
      return;
    }

    Set<String> declaredCloIds = new HashSet<>();
    for (AssessmentCloWeightDto cw : cloWeights) {
      if (!declaredCloIds.add(cw.getCloId())) {
        throw rule("CLO [" + cw.getCloId() + "] bị khai báo trùng trong cloWeights");
      }
      if (cw.getWeight() == null || cw.getWeight() <= 0.0 || cw.getWeight() > 1.0) {
        throw rule("Trọng số CLO [" + cw.getCloId() + "] phải thuộc khoảng (0, 1]");
      }
    }

    validateClosBelongToSyllabus(syllabusId, declaredCloIds);

    if (items != null && !items.isEmpty()) {
      Set<String> itemCloIds = new HashSet<>();
      for (AssessmentItemDto it : items) {
        if (it.getOutcomeMappings() != null) {
          for (OutcomeMappingDto m : it.getOutcomeMappings()) {
            itemCloIds.add(m.getCloId());
          }
        }
      }
      if (!declaredCloIds.equals(itemCloIds)) {
        throw rule(
            "Danh sách CLO trong cloWeights phải khớp chính xác với các CLO xuất hiện trong câu hỏi (items)");
      }
    }

    // Kiểm tra tổng tích lũy trong cùng lớp học phần (sectionId)
    if (sectionId != null) {
      List<Assessment> existing = assessmentRepository.findBySectionId(sectionId);
      Map<String, Double> currentSums = new java.util.HashMap<>();
      for (Assessment other : existing) {
        if (currentAssessmentId != null && currentAssessmentId.equals(other.getId())) {
          continue;
        }
        if (other.getStatus() == AssessmentStatus.LOCKED
            || other.getStatus() == AssessmentStatus.SCORE_ENTERED) {
          if (other.getCloWeights() != null) {
            for (var w : other.getCloWeights()) {
              currentSums.merge(w.getCloId(), w.getWeight(), Double::sum);
            }
          }
        }
      }

      for (AssessmentCloWeightDto cw : cloWeights) {
        double prior = currentSums.getOrDefault(cw.getCloId(), 0.0);
        if (prior + cw.getWeight() > 1.0001) {
          throw rule(
              String.format(
                  "Tổng trọng số đóng góp của CLO [%s] qua các bài đã khóa vượt quá 100%% (hiện tại: %.2f%%, bài này: %.2f%%)",
                  cw.getCloId(), prior * 100, cw.getWeight() * 100));
        }
      }
    }
  }

  private void validateClosBelongToSyllabus(String syllabusId, Set<String> cloIds) {
    if (cloIds.isEmpty()) {
      return;
    }
    Map<String, Clo> found =
        cloRepository.findAllById(cloIds).stream()
            .collect(Collectors.toMap(Clo::getId, Function.identity()));
    for (String cloId : cloIds) {
      Clo clo = found.get(cloId);
      if (clo == null) {
        throw rule("CLO không tồn tại: " + cloId);
      }
      if (!syllabusId.equals(clo.getSyllabusId())) {
        throw rule("CLO [" + clo.getCode() + "] không thuộc đề cương của bài đánh giá");
      }
    }
  }

  private BusinessRuleViolationException rule(String message) {
    return new BusinessRuleViolationException(message);
  }
}
