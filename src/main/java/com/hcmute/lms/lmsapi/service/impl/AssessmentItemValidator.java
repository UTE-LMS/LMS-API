package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.OutcomeMappingDto;
import com.hcmute.lms.lmsapi.dto.RubricLevelDto;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
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
 * Kiểm tra quy tắc nghiệp vụ của items[] trước khi ghi (plan F04 §4 và §7). Service phải kiểm tra
 * trước để trả lỗi 422 rõ ràng, không để lộ lỗi $jsonSchema (mã 121) của MongoDB. Định dạng từng
 * trường đã được Bean Validation kiểm ở DTO.
 *
 * <ul>
 *   <li>mã phần tử và id (nếu có) không trùng trong một bài;
 *   <li>mỗi phần tử: CLO không trùng, Σ weight = 1 (sai số 0,0001, cùng phép kiểm với validator của
 *       MongoDB);
 *   <li>rubric: mức không trùng, điểm mỗi mức không vượt maxScore;
 *   <li>mọi CLO tồn tại và thuộc đúng đề cương của bài (ADR 0011).
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AssessmentItemValidator {

  /** Sai số cho phép của Σ weight, khớp biểu thức sumsToOne trong database_import.js. */
  static final double WEIGHT_TOLERANCE = 0.0001;

  private final CloRepository cloRepository;

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
      validateRubric(item);
      item.getOutcomeMappings().forEach(m -> allCloIds.add(m.getCloId()));
    }
    validateClosBelongToSyllabus(syllabusId, allCloIds);
  }

  private void validateMappings(AssessmentItemDto item) {
    Set<String> cloIds = new HashSet<>();
    double sum = 0;
    for (OutcomeMappingDto mapping : item.getOutcomeMappings()) {
      if (!cloIds.add(mapping.getCloId())) {
        throw rule(
            "Phần tử ["
                + item.getCode()
                + "]: CLO ["
                + mapping.getCloId()
                + "] được khai báo nhiều lần");
      }
      sum += mapping.getWeight();
    }
    if (Math.abs(sum - 1) > WEIGHT_TOLERANCE) {
      throw rule(
          "Phần tử ["
              + item.getCode()
              + "]: tổng phần điểm chia cho các CLO phải bằng 1, hiện là "
              + sum);
    }
  }

  private void validateRubric(AssessmentItemDto item) {
    if (item.getRubric() == null) {
      return;
    }
    Set<Integer> levels = new HashSet<>();
    for (RubricLevelDto level : item.getRubric().getLevels()) {
      if (!levels.add(level.getLevel())) {
        throw rule(
            "Phần tử ["
                + item.getCode()
                + "]: mức rubric "
                + level.getLevel()
                + " bị khai báo lặp");
      }
      if (level.getScore() > item.getMaxScore()) {
        throw rule(
            "Phần tử ["
                + item.getCode()
                + "]: điểm của mức rubric "
                + level.getLevel()
                + " ("
                + level.getScore()
                + ") vượt điểm tối đa "
                + item.getMaxScore());
      }
    }
  }

  private void validateClosBelongToSyllabus(String syllabusId, Set<String> cloIds) {
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
