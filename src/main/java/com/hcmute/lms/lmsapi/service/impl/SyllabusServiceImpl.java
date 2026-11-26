package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.SyllabusRequest;
import com.hcmute.lms.lmsapi.dto.SyllabusResponse;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.SyllabusMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.SyllabusService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyllabusServiceImpl implements SyllabusService {

  private final SyllabusRepository syllabusRepository;
  private final CourseRepository courseRepository;
  private final ProgramVersionRepository programVersionRepository;
  private final ProgramCourseRepository programCourseRepository;
  private final TermRepository termRepository;
  private final SyllabusMapper syllabusMapper;

  @Autowired(required = false)
  private AuditService auditService;

  @Override
  public List<SyllabusResponse> getSyllabi(String courseId) {
    return getSyllabi(courseId, null, null, null);
  }

  @Override
  public List<SyllabusResponse> getSyllabi(
      String courseId, SyllabusStatus status, String effectiveFromTermId, String search) {
    requireCourse(courseId);
    List<Syllabus> syllabi = syllabusRepository.findByCourseIdOrderByVersionAsc(courseId);
    return toResponses(filterSyllabi(syllabi, null, status, effectiveFromTermId, search));
  }

  @Override
  public List<SyllabusResponse> getSyllabiByProgramVersion(
      String programVersionId,
      String courseId,
      SyllabusStatus status,
      String effectiveFromTermId,
      String search) {
    ObjectIdUtils.requireValid(programVersionId, "programVersionId");
    if (!programVersionRepository.existsById(programVersionId)) {
      throw new ResourceNotFoundException("ProgramVersion", "id", programVersionId);
    }
    if (courseId != null && !courseId.isBlank()) {
      ObjectIdUtils.requireValid(courseId.trim(), "courseId");
    }

    List<String> courseIds =
        programCourseRepository
            .findByProgramVersionIdOrderBySemesterAscCourseIdAsc(programVersionId)
            .stream()
            .map(ProgramCourse::getCourseId)
            .distinct()
            .toList();
    if (courseIds.isEmpty()) {
      return List.of();
    }

    List<Syllabus> syllabi =
        syllabusRepository.findByCourseIdInOrderByCourseIdAscVersionAsc(courseIds);
    return toResponses(filterSyllabi(syllabi, courseId, status, effectiveFromTermId, search));
  }

  private List<Syllabus> filterSyllabi(
      List<Syllabus> syllabi,
      String courseId,
      SyllabusStatus status,
      String effectiveFromTermId,
      String search) {
    String cleanCourseId = courseId != null && !courseId.isBlank() ? courseId.trim() : null;
    String cleanTermId =
        effectiveFromTermId != null && !effectiveFromTermId.isBlank()
            ? effectiveFromTermId.trim()
            : null;
    if (cleanTermId != null) {
      ObjectIdUtils.requireValid(cleanTermId, "effectiveFromTermId");
    }
    String cleanSearch = search != null && !search.isBlank() ? search.trim().toLowerCase() : null;

    return syllabi.stream()
        .filter(s -> cleanCourseId == null || cleanCourseId.equals(s.getCourseId()))
        .filter(s -> status == null || status == s.getStatus())
        .filter(s -> cleanTermId == null || cleanTermId.equals(s.getEffectiveFromTermId()))
        .filter(
            s ->
                cleanSearch == null
                    || (s.getVersion() != null
                        && s.getVersion().toLowerCase().contains(cleanSearch)))
        .toList();
  }

  @Override
  public SyllabusResponse getSyllabus(String id) {
    return toResponse(requireSyllabus(id));
  }

  @Override
  public SyllabusResponse createSyllabus(String courseId, SyllabusRequest request) {
    requireCourse(courseId);

    String version = request.getVersion().trim();
    if (syllabusRepository.existsByCourseIdAndVersion(courseId, version)) {
      throw conflict(
          String.format(
              "Đề cương phiên bản [%s] đã tồn tại cho học phần id=[%s]", version, courseId));
    }

    // US-10/AC-2: Xác thực ràng buộc học kỳ hiệu lực từ collection terms (F05a)
    validateEffectiveTerms(request.getEffectiveFromTermId(), request.getEffectiveToTermId());

    Syllabus syllabus = syllabusMapper.toDocument(courseId, request);
    try {
      Syllabus saved = syllabusRepository.save(syllabus);
      log.info(
          "Tạo thành công đề cương id=[{}] phiên bản [{}] cho học phần id=[{}]",
          saved.getId(),
          saved.getVersion(),
          courseId);
      AuditLogHelper.record(
          auditService, AuditAction.CREATE, "syllabi", saved.getId(), null, saved, null);
      return toResponse(saved);
    } catch (DuplicateKeyException ex) {
      throw conflict(
          String.format(
              "Đề cương phiên bản [%s] đã tồn tại cho học phần id=[%s]", version, courseId));
    }
  }

  @Override
  public SyllabusResponse updateStatus(String id, SyllabusStatus newStatus) {
    Syllabus syllabus = requireSyllabus(id);
    SyllabusStatus currentStatus = syllabus.getStatus();

    if (currentStatus == newStatus) {
      return toResponse(syllabus);
    }

    boolean validTransition =
        currentStatus == SyllabusStatus.CONFIRMED && newStatus == SyllabusStatus.ARCHIVED;

    if (!validTransition) {
      throw new BusinessRuleViolationException(
          String.format(
              "Không thể chuyển trạng thái đề cương từ [%s] sang [%s]", currentStatus, newStatus));
    }

    syllabus.setStatus(newStatus);
    Syllabus updated = syllabusRepository.save(syllabus);
    log.info(
        "Cập nhật trạng thái đề cương id=[{}] từ [{}] sang [{}]", id, currentStatus, newStatus);
    AuditLogHelper.record(
        auditService,
        AuditAction.STATUS_CHANGE,
        "syllabi",
        updated.getId(),
        Map.of("status", currentStatus),
        Map.of("status", newStatus),
        null);
    return toResponse(updated);
  }

  private List<SyllabusResponse> toResponses(List<Syllabus> syllabi) {
    if (syllabi == null || syllabi.isEmpty()) {
      return List.of();
    }
    Set<String> courseIds = new LinkedHashSet<>();
    Set<String> termIds = new LinkedHashSet<>();
    for (Syllabus s : syllabi) {
      if (hasText(s.getCourseId())) {
        courseIds.add(s.getCourseId());
      }
      if (hasText(s.getEffectiveFromTermId())) {
        termIds.add(s.getEffectiveFromTermId());
      }
      if (hasText(s.getEffectiveToTermId())) {
        termIds.add(s.getEffectiveToTermId());
      }
    }
    Map<String, Course> coursesById =
        courseIds.isEmpty()
            ? Collections.emptyMap()
            : courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
    Map<String, Term> termsById =
        termIds.isEmpty()
            ? Collections.emptyMap()
            : termRepository.findAllById(termIds).stream()
                .collect(Collectors.toMap(Term::getId, Function.identity(), (a, b) -> a));
    return syllabi.stream()
        .map(
            s ->
                syllabusMapper.toResponse(
                    s,
                    hasText(s.getCourseId()) ? coursesById.get(s.getCourseId()) : null,
                    hasText(s.getEffectiveFromTermId())
                        ? termsById.get(s.getEffectiveFromTermId())
                        : null,
                    hasText(s.getEffectiveToTermId())
                        ? termsById.get(s.getEffectiveToTermId())
                        : null))
        .toList();
  }

  private SyllabusResponse toResponse(Syllabus syllabus) {
    return toResponses(List.of(syllabus)).get(0);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private void validateEffectiveTerms(String fromTermId, String toTermId) {
    Term fromTerm = null;
    if (fromTermId != null && !fromTermId.trim().isEmpty()) {
      ObjectIdUtils.requireValid(fromTermId, "effectiveFromTermId");
      fromTerm =
          termRepository
              .findById(fromTermId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Học kỳ bắt đầu hiệu lực (effectiveFromTermId) không tồn tại: "
                              + fromTermId));
    }

    Term toTerm = null;
    if (toTermId != null && !toTermId.trim().isEmpty()) {
      ObjectIdUtils.requireValid(toTermId, "effectiveToTermId");
      toTerm =
          termRepository
              .findById(toTermId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Học kỳ kết thúc hiệu lực (effectiveToTermId) không tồn tại: "
                              + toTermId));
    }

    // US-10/AC-2: effectiveToTermId không được trước effectiveFromTermId
    if (fromTerm != null && toTerm != null) {
      if (fromTerm.getId().equals(toTerm.getId())) {
        return; // Cùng 1 học kỳ là hợp lệ
      }

      if (fromTerm.getStartDate() != null && toTerm.getStartDate() != null) {
        if (toTerm.getStartDate().isBefore(fromTerm.getStartDate())) {
          throw new BusinessRuleViolationException(
              "effectiveToTermId không được trước effectiveFromTermId");
        }
      } else if (fromTerm.getEndDate() != null && toTerm.getEndDate() != null) {
        if (toTerm.getEndDate().isBefore(fromTerm.getEndDate())) {
          throw new BusinessRuleViolationException(
              "effectiveToTermId không được trước effectiveFromTermId");
        }
      } else if (fromTerm.getAcademicYear() != null && toTerm.getAcademicYear() != null) {
        if (toTerm.getAcademicYear().compareTo(fromTerm.getAcademicYear()) < 0) {
          throw new BusinessRuleViolationException(
              "effectiveToTermId không được trước effectiveFromTermId");
        }
      }
    }
  }

  private void requireCourse(String courseId) {
    ObjectIdUtils.requireValid(courseId, "courseId");
    if (!courseRepository.existsById(courseId)) {
      throw new ResourceNotFoundException("Học phần không tồn tại: " + courseId);
    }
  }

  private Syllabus requireSyllabus(String id) {
    ObjectIdUtils.requireValid(id, "syllabusId");
    return syllabusRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Đề cương không tồn tại: " + id));
  }

  private AppException conflict(String message) {
    return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }
}
