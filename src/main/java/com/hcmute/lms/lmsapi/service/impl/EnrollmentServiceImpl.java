package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.EnrollmentImportResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentListResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.FieldErrorDetail;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ImportValidationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.EnrollmentMapper;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.EnrollmentRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import com.hcmute.lms.lmsapi.service.EnrollmentService;
import com.hcmute.lms.lmsapi.util.EnrollmentCsvParser;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

  private static final int MAX_FIELD_LENGTH = 50;
  private static final Pattern STUDENT_REF_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,50}$");

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
  public EnrollmentImportResponse importEnrollments(
      String sectionId, InputStream csv, String actorUsername) {
    CourseSection section = requireSection(sectionId);
    requireEnrollmentEditable(section);
    EnrollmentCsvParser.Result parsed = EnrollmentCsvParser.parse(csv);
    List<EnrollmentCsvParser.Row> rows = parsed.rows();

    Map<String, Student> existingStudents =
        studentRepository
            .findByStudentRefIn(rows.stream().map(EnrollmentCsvParser.Row::studentRef).toList())
            .stream()
            .collect(Collectors.toMap(Student::getStudentRef, Function.identity()));
    Map<String, Enrollment> enrollmentByStudentId =
        enrollmentRepository.findBySectionId(section.getId()).stream()
            .collect(Collectors.toMap(Enrollment::getStudentId, Function.identity()));

    // US-20/AC-2: gom MỌI lỗi theo dòng rồi mới quyết định ghi hay không, để không ghi nửa chừng
    List<FieldErrorDetail> errors = validateRows(rows, existingStudents, enrollmentByStudentId);
    if (!errors.isEmpty()) {
      throw new ImportValidationException(errors);
    }

    List<Student> newStudents =
        rows.stream()
            .filter(row -> !existingStudents.containsKey(row.studentRef()))
            .map(
                row ->
                    Student.builder()
                        .studentRef(row.studentRef())
                        .cohort(blankToNull(row.cohort()))
                        .majorCode(blankToNull(row.majorCode()))
                        .build())
            .toList();
    Map<String, Student> studentByRef = new HashMap<>(existingStudents);
    studentRepository.saveAll(newStudents).forEach(s -> studentByRef.put(s.getStudentRef(), s));

    Instant now = Instant.now();
    List<Enrollment> newEnrollments =
        rows.stream()
            .map(
                row ->
                    Enrollment.builder()
                        .sectionId(section.getId())
                        .studentId(studentByRef.get(row.studentRef()).getId())
                        .status(EnrollmentStatus.ENROLLED)
                        .enrolledAt(now)
                        .build())
            .toList();
    enrollmentRepository.saveAll(newEnrollments);
    recountEnrolled(section);

    log.info(
        "Nhập danh sách lớp=[{}] bởi user=[{}]: {} dòng, {} sinh viên mới, {} sinh viên dùng lại",
        section.getCode(),
        actorUsername,
        rows.size(),
        newStudents.size(),
        rows.size() - newStudents.size());
    return EnrollmentImportResponse.builder()
        .sectionId(section.getId())
        .totalRows(rows.size())
        .newStudents(newStudents.size())
        .reusedStudents(rows.size() - newStudents.size())
        .sectionEnrolledCount(section.getEnrolledCount())
        .ignoredColumns(parsed.ignoredColumns())
        .build();
  }

  /** Kiểm tra từng dòng; trả về danh sách lỗi theo dòng (rỗng nếu tất cả hợp lệ). */
  private List<FieldErrorDetail> validateRows(
      List<EnrollmentCsvParser.Row> rows,
      Map<String, Student> existingStudents,
      Map<String, Enrollment> enrollmentByStudentId) {
    List<FieldErrorDetail> errors = new ArrayList<>();
    Map<String, Integer> firstLineOfRef = new HashMap<>();

    for (EnrollmentCsvParser.Row row : rows) {
      String ref = row.studentRef();
      String field = "line " + row.lineNumber();
      if (ref.isEmpty()) {
        errors.add(rowError(field, "Thiếu studentRef", ref));
        continue;
      }
      if (!STUDENT_REF_PATTERN.matcher(ref).matches()) {
        errors.add(
            rowError(
                field,
                "studentRef chỉ gồm chữ cái, số, dấu chấm, gạch ngang, gạch dưới (1-"
                    + MAX_FIELD_LENGTH
                    + " ký tự)",
                ref));
        continue;
      }
      if (row.cohort().length() > MAX_FIELD_LENGTH || row.majorCode().length() > MAX_FIELD_LENGTH) {
        errors.add(
            rowError(field, "cohort/majorCode dài tối đa " + MAX_FIELD_LENGTH + " ký tự", ref));
        continue;
      }
      Integer firstLine = firstLineOfRef.putIfAbsent(ref, row.lineNumber());
      if (firstLine != null) {
        errors.add(rowError(field, "studentRef trùng với dòng " + firstLine + " trong tệp", ref));
        continue;
      }
      Student existing = existingStudents.get(ref);
      Enrollment current = existing == null ? null : enrollmentByStudentId.get(existing.getId());
      if (current != null) {
        errors.add(
            rowError(
                field,
                current.getStatus() == EnrollmentStatus.ENROLLED
                    ? "Sinh viên đã ghi danh lớp này"
                    : "Sinh viên đã rút khỏi lớp này, dùng PUT /api/v1/enrollments/"
                        + current.getId()
                        + "/status để ghi danh lại",
                ref));
      }
    }
    return errors;
  }

  private FieldErrorDetail rowError(String field, String message, String rejectedValue) {
    return FieldErrorDetail.builder()
        .field(field)
        .message(message)
        .rejectedValue(rejectedValue)
        .build();
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
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
