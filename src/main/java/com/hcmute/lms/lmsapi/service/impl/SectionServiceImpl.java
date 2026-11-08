package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.SectionRequest;
import com.hcmute.lms.lmsapi.dto.SectionResponse;
import com.hcmute.lms.lmsapi.dto.SectionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.SectionUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.SectionMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import com.hcmute.lms.lmsapi.service.SectionService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SectionServiceImpl implements SectionService {

  private final CourseSectionRepository sectionRepository;
  private final CourseRepository courseRepository;
  private final SyllabusRepository syllabusRepository;
  private final TermRepository termRepository;
  private final UserRepository userRepository;
  private final ScopeAuthorizationService scopeAuthorizationService;
  private final SectionMapper sectionMapper;

  @Override
  public List<SectionResponse> getSections(
      UserPrincipal principal, String termId, String courseId) {
    String term = normalizeFilter(termId, "termId");
    String course = normalizeFilter(courseId, "courseId");

    List<CourseSection> candidates;
    if (term != null && course != null) {
      candidates = sectionRepository.findByTermIdAndCourseId(term, course);
    } else if (term != null) {
      candidates = sectionRepository.findByTermId(term);
    } else if (course != null) {
      candidates = sectionRepository.findByCourseId(course);
    } else {
      candidates = sectionRepository.findAll();
    }

    // US-19/AC-4: chỉ trả về lớp mà người gọi được đọc (giảng viên: lớp mình) hoặc quản lý được
    List<CourseSection> visible =
        candidates.stream()
            .filter(
                s ->
                    scopeAuthorizationService.canAccessLoadedSection(principal, s, AccessType.READ)
                        || scopeAuthorizationService.canManageCourseSections(
                            principal, s.getCourseId()))
            .sorted(Comparator.comparing(CourseSection::getCode))
            .toList();
    return sectionMapper.toResponseList(visible);
  }

  @Override
  public SectionResponse getSection(String id) {
    return sectionMapper.toResponse(requireSection(id));
  }

  @Override
  public SectionResponse createSection(SectionRequest request) {
    String code = request.getCode().trim();
    if (sectionRepository.existsByCode(code)) {
      throw conflict(String.format("Mã lớp học phần [%s] đã tồn tại trong hệ thống", code));
    }

    // US-19/AC-2: mọi tham chiếu trong body phải tồn tại và nhất quán, sai thì 422
    if (!courseRepository.existsById(request.getCourseId())) {
      throw new BusinessRuleViolationException("Học phần không tồn tại: " + request.getCourseId());
    }
    validateSyllabus(request.getSyllabusId(), request.getCourseId());
    validateTermOpen(request.getTermId());
    List<String> lecturerIds = validateLecturers(request.getLecturerIds());

    CourseSection section = sectionMapper.toDocument(request, lecturerIds);
    try {
      CourseSection saved = sectionRepository.save(section);
      log.info("Mở lớp học phần id=[{}] mã [{}]", saved.getId(), saved.getCode());
      return sectionMapper.toResponse(saved);
    } catch (DuplicateKeyException ex) {
      throw conflict(String.format("Mã lớp học phần [%s] đã tồn tại trong hệ thống", code));
    }
  }

  @Override
  public SectionResponse updateLecturers(String id, SectionUpdateRequest request) {
    CourseSection section = requireSection(id);
    section.setLecturerIds(validateLecturers(request.getLecturerIds()));
    CourseSection saved = sectionRepository.save(section);
    log.info("Cập nhật giảng viên phụ trách lớp id=[{}] mã [{}]", saved.getId(), saved.getCode());
    return sectionMapper.toResponse(saved);
  }

  @Override
  public SectionResponse updateStatus(
      String id, SectionStatusUpdateRequest request, String actorUsername) {
    CourseSection section = requireSection(id);
    SectionStatus current = section.getStatus();
    SectionStatus target = request.getStatus();

    if (current == target) {
      return sectionMapper.toResponse(section);
    }

    // Mở lại lớp đã khóa sổ điểm phải có lý do giải trình (cùng quy tắc US-18/AC-3 của học kỳ)
    if (current == SectionStatus.LOCKED
        && (request.getReason() == null || request.getReason().isBlank())) {
      throw new BusinessRuleViolationException(
          "Cần nêu rõ lý do giải trình khi mở lại lớp học phần đã khóa sổ điểm");
    }

    section.setStatus(target);
    CourseSection saved = sectionRepository.save(section);
    log.info(
        "Chuyển trạng thái lớp id=[{}] từ [{}] sang [{}] bởi user=[{}] lý do=[{}]",
        saved.getId(),
        current,
        target,
        actorUsername,
        request.getReason());
    return sectionMapper.toResponse(saved);
  }

  private void validateSyllabus(String syllabusId, String courseId) {
    Syllabus syllabus =
        syllabusRepository
            .findById(syllabusId)
            .orElseThrow(
                () -> new BusinessRuleViolationException("Đề cương không tồn tại: " + syllabusId));
    if (!courseId.equals(syllabus.getCourseId())) {
      throw new BusinessRuleViolationException(
          "Đề cương [" + syllabusId + "] không thuộc học phần [" + courseId + "]");
    }
    // [ĐỀ XUẤT] plan F05a §4 quy tắc 3: chỉ mở lớp trên đề cương đã chốt
    if (syllabus.getStatus() != SyllabusStatus.CONFIRMED) {
      throw new BusinessRuleViolationException(
          "Chỉ được mở lớp trên đề cương đã chốt (CONFIRMED), đề cương hiện ở trạng thái "
              + syllabus.getStatus());
    }
  }

  private void validateTermOpen(String termId) {
    Term term =
        termRepository
            .findById(termId)
            .orElseThrow(
                () -> new BusinessRuleViolationException("Học kỳ không tồn tại: " + termId));
    if (term.getStatus() != TermStatus.OPEN) {
      throw new BusinessRuleViolationException(
          String.format(
              "Không thể mở lớp trong học kỳ [%s] vì học kỳ đang ở trạng thái %s",
              term.getCode(), term.getStatus()));
    }
  }

  /** Mỗi ID phải là người dùng ACTIVE có vai trò LECTURER; trả về danh sách đã khử trùng lặp. */
  private List<String> validateLecturers(List<String> requestedIds) {
    List<String> distinctIds = new ArrayList<>(new LinkedHashSet<>(requestedIds));
    Map<String, User> found =
        userRepository.findAllById(distinctIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

    for (String lecturerId : distinctIds) {
      User user = found.get(lecturerId);
      if (user == null) {
        throw new BusinessRuleViolationException("Giảng viên không tồn tại: " + lecturerId);
      }
      if (user.getStatus() != UserStatus.ACTIVE) {
        throw new BusinessRuleViolationException(
            "Giảng viên [" + user.getUsername() + "] không ở trạng thái ACTIVE");
      }
      if (!hasLecturerRole(user)) {
        throw new BusinessRuleViolationException(
            "Người dùng [" + user.getUsername() + "] không có vai trò LECTURER");
      }
    }
    return distinctIds;
  }

  private boolean hasLecturerRole(User user) {
    return user.getRoleAssignments() != null
        && user.getRoleAssignments().stream()
            .map(RoleAssignment::getRole)
            .anyMatch(role -> role == Role.LECTURER);
  }

  private CourseSection requireSection(String id) {
    ObjectIdUtils.requireValid(id, "sectionId");
    return sectionRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Lớp học phần không tồn tại: " + id));
  }

  private String normalizeFilter(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    ObjectIdUtils.requireValid(trimmed, fieldName);
    return trimmed;
  }

  private AppException conflict(String message) {
    return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }
}
