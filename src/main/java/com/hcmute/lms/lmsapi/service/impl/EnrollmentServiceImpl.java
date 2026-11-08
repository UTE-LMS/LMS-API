package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.EnrollmentListResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.EnrollmentMapper;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.EnrollmentRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import com.hcmute.lms.lmsapi.service.EnrollmentService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

  private final EnrollmentRepository enrollmentRepository;
  private final StudentRepository studentRepository;
  private final CourseSectionRepository sectionRepository;
  private final TermRepository termRepository;
  private final EnrollmentMapper enrollmentMapper;

  @Override
  public EnrollmentListResponse getEnrollments(String sectionId, EnrollmentStatus status) {
    CourseSection section = requireSection(sectionId);
    List<Enrollment> enrollments =
        status == null
            ? enrollmentRepository.findBySectionId(section.getId())
            : enrollmentRepository.findBySectionIdAndStatus(section.getId(), status);

    Map<String, Student> students =
        studentRepository
            .findAllById(enrollments.stream().map(Enrollment::getStudentId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Student::getId, Function.identity()));

    List<EnrollmentResponse> items =
        enrollments.stream()
            .map(e -> enrollmentMapper.toResponse(e, students.get(e.getStudentId())))
            .sorted(
                Comparator.comparing(
                    EnrollmentResponse::getStudentRef,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

    return EnrollmentListResponse.builder()
        .sectionId(section.getId())
        .enrolled(
            enrollmentRepository.countBySectionIdAndStatus(
                section.getId(), EnrollmentStatus.ENROLLED))
        .withdrawn(
            enrollmentRepository.countBySectionIdAndStatus(
                section.getId(), EnrollmentStatus.WITHDRAWN))
        .enrollments(items)
        .build();
  }

  @Override
  @Transactional
  public EnrollmentResponse updateStatus(
      String enrollmentId, EnrollmentStatusUpdateRequest request, String actorUsername) {
    ObjectIdUtils.requireValid(enrollmentId, "enrollmentId");
    Enrollment enrollment =
        enrollmentRepository
            .findById(enrollmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Ghi danh không tồn tại: " + enrollmentId));
    CourseSection section = requireSection(enrollment.getSectionId());
    Student student = studentRepository.findById(enrollment.getStudentId()).orElse(null);

    EnrollmentStatus current = enrollment.getStatus();
    EnrollmentStatus target = request.getStatus();
    if (current == target) {
      return enrollmentMapper.toResponse(enrollment, student);
    }

    requireEnrollmentEditable(section);

    enrollment.setStatus(target);
    if (target == EnrollmentStatus.ENROLLED) {
      // Ghi danh lại: enrolledAt phản ánh lần ghi danh hiện hành
      enrollment.setEnrolledAt(Instant.now());
    }
    Enrollment saved = enrollmentRepository.save(enrollment);
    recountEnrolled(section);

    log.info(
        "Ghi danh id=[{}] lớp=[{}] chuyển từ [{}] sang [{}] bởi user=[{}]",
        saved.getId(),
        section.getCode(),
        current,
        target,
        actorUsername);
    return enrollmentMapper.toResponse(saved, student);
  }

  /**
   * Danh sách ghi danh là mẫu số của tỷ lệ đạt, nên chỉ đổi được khi lớp còn OPEN và học kỳ còn
   * OPEN; sau khi lớp đóng/khóa hoặc học kỳ khóa sổ điểm thì mẫu số không được đổi âm thầm.
   */
  void requireEnrollmentEditable(CourseSection section) {
    if (section.getStatus() != SectionStatus.OPEN) {
      throw new BusinessRuleViolationException(
          String.format(
              "Không thể đổi danh sách ghi danh vì lớp [%s] đang ở trạng thái %s",
              section.getCode(), section.getStatus()));
    }
    Term term =
        termRepository
            .findById(section.getTermId())
            .orElseThrow(
                () ->
                    new BusinessRuleViolationException(
                        "Học kỳ của lớp không tồn tại: " + section.getTermId()));
    if (term.getStatus() != TermStatus.OPEN) {
      throw new BusinessRuleViolationException(
          String.format(
              "Không thể đổi danh sách ghi danh vì học kỳ [%s] đang ở trạng thái %s",
              term.getCode(), term.getStatus()));
    }
  }

  /** Tính lại sĩ số từ enrollments (không cộng dồn) để giá trị dẫn xuất không bị lệch. */
  void recountEnrolled(CourseSection section) {
    long enrolled =
        enrollmentRepository.countBySectionIdAndStatus(section.getId(), EnrollmentStatus.ENROLLED);
    section.setEnrolledCount((int) enrolled);
    sectionRepository.save(section);
  }

  private CourseSection requireSection(String sectionId) {
    ObjectIdUtils.requireValid(sectionId, "sectionId");
    return sectionRepository
        .findById(sectionId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Lớp học phần không tồn tại: " + sectionId));
  }
}
