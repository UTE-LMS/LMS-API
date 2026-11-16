package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentImportResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentListResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.FieldErrorDetail;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.AuditAction;
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
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.EnrollmentService;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import com.hcmute.lms.lmsapi.util.EnrollmentCsvParser;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

  @Autowired(required = false)
  private AuditService auditService;

  @Autowired(required = false)
  private ScopeAuthorizationService scopeAuthorizationService;

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
                    EnrollmentResponse::getStudentCode,
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
            .findByStudentCodeIn(rows.stream().map(EnrollmentCsvParser.Row::studentCode).toList())
            .stream()
            .collect(Collectors.toMap(Student::getStudentCode, Function.identity()));
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
            .filter(row -> !existingStudents.containsKey(row.studentCode()))
            .map(
                row ->
                    Student.builder()
                        .studentCode(row.studentCode())
                        .cohort(blankToNull(row.cohort()))
                        .build())
            .toList();
    Map<String, Student> studentByRef = new HashMap<>(existingStudents);
    studentRepository.saveAll(newStudents).forEach(s -> studentByRef.put(s.getStudentCode(), s));

    Instant now = Instant.now();
    List<Enrollment> newEnrollments =
        rows.stream()
            .map(
                row ->
                    Enrollment.builder()
                        .sectionId(section.getId())
                        .studentId(studentByRef.get(row.studentCode()).getId())
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
    AuditLogHelper.record(
        auditService,
        AuditAction.IMPORT,
        "enrollments",
        section.getId(),
        null,
        Map.of("totalRows", rows.size(), "newStudents", newStudents.size()),
        "Import CSV ghi danh lớp " + section.getCode());
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
      String ref = row.studentCode();
      String field = "line " + row.lineNumber();
      if (ref.isEmpty()) {
        errors.add(rowError(field, "Thiếu studentCode", ref));
        continue;
      }
      if (!STUDENT_REF_PATTERN.matcher(ref).matches()) {
        errors.add(
            rowError(
                field,
                "studentCode chỉ gồm chữ cái, số, dấu chấm, gạch ngang, gạch dưới (1-"
                    + MAX_FIELD_LENGTH
                    + " ký tự)",
                ref));
        continue;
      }
      if (row.cohort().length() > MAX_FIELD_LENGTH) {
        errors.add(rowError(field, "cohort dài tối đa " + MAX_FIELD_LENGTH + " ký tự", ref));
        continue;
      }
      Integer firstLine = firstLineOfRef.putIfAbsent(ref, row.lineNumber());
      if (firstLine != null) {
        errors.add(rowError(field, "studentCode trùng với dòng " + firstLine + " trong tệp", ref));
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
    AuditLogHelper.record(
        auditService,
        AuditAction.STATUS_CHANGE,
        "enrollments",
        saved.getId(),
        Map.of("status", current),
        Map.of("status", target),
        null);
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

  @Override
  public AssessmentScoreTemplate generateTemplateCsv() {
    String csv = "\uFEFF" + "studentCode,cohort\r\n" + "23110001,K23\r\n" + "23110002,K23\r\n";
    return new AssessmentScoreTemplate(
        "enrollments-template.csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public AssessmentScoreTemplate generateBulkTemplateCsv() {
    String csv =
        "\uFEFF"
            + "sectionCode,studentCode,cohort\r\n"
            + "OOPR230279_01,23110001,K23\r\n"
            + "OOPR230279_01,23110002,K23\r\n";
    return new AssessmentScoreTemplate(
        "enrollments-bulk-template.csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public CsvImportResultResponse importEnrollmentsBulk(
      MultipartFile file, UserPrincipal principal) {
    CsvTextParser.ParsedCsv parsed = CsvTextParser.read(file);
    CsvTextParser.requireColumns(parsed.headerIndex(), "sectionCode", "studentCode");

    int totalRows = parsed.rows().size();
    int successCount = 0;
    List<CsvImportResultResponse.RowError> errors = new ArrayList<>();
    Map<String, CourseSection> sectionCache = new HashMap<>();
    Map<String, Term> termCache = new HashMap<>();
    Map<String, CourseSection> affectedSections = new LinkedHashMap<>();
    Set<String> seenPairs = new HashSet<>();

    for (int i = 0; i < totalRows; i++) {
      int rowNum = i + 2;
      List<String> row = parsed.rows().get(i);
      String sectionCode = CsvTextParser.cell(parsed.headerIndex(), row, "sectionCode");
      String studentCode = CsvTextParser.cell(parsed.headerIndex(), row, "studentCode");
      String cohort = CsvTextParser.cell(parsed.headerIndex(), row, "cohort");

      if (sectionCode.isEmpty() || studentCode.isEmpty()) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Thiếu sectionCode hoặc studentCode"));
        continue;
      }
      if (!STUDENT_REF_PATTERN.matcher(studentCode).matches()) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Mã số sinh viên (studentCode) không hợp lệ: " + studentCode));
        continue;
      }
      if (cohort.length() > MAX_FIELD_LENGTH) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "cohort dài tối đa " + MAX_FIELD_LENGTH + " ký tự"));
        continue;
      }

      CourseSection section =
          sectionCache.computeIfAbsent(
              sectionCode, k -> sectionRepository.findByCode(k).orElse(null));
      if (section == null) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Không tìm thấy lớp học phần: " + sectionCode));
        continue;
      }
      if (scopeAuthorizationService != null
          && !scopeAuthorizationService.canAccessLoadedSection(principal, section, AccessType.WRITE)
          && !scopeAuthorizationService.canManageCourseSections(principal, section.getCourseId())) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Không có quyền ghi danh vào lớp: " + sectionCode));
        continue;
      }
      Term term =
          termCache.computeIfAbsent(
              section.getTermId(), k -> termRepository.findById(k).orElse(null));
      if (section.getStatus() != SectionStatus.OPEN
          || term == null
          || term.getStatus() != TermStatus.OPEN) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Lớp học phần hoặc học kỳ không ở trạng thái OPEN"));
        continue;
      }
      if (!seenPairs.add(section.getId() + ":" + studentCode)) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Trùng cặp lớp-sinh viên trong tệp: " + sectionCode));
        continue;
      }

      List<Student> found = studentRepository.findByStudentCodeIn(List.of(studentCode));
      Student student =
          found.isEmpty()
              ? studentRepository.save(
                  Student.builder().studentCode(studentCode).cohort(blankToNull(cohort)).build())
              : found.get(0);

      try {
        enrollmentRepository.save(
            Enrollment.builder()
                .sectionId(section.getId())
                .studentId(student.getId())
                .status(EnrollmentStatus.ENROLLED)
                .enrolledAt(Instant.now())
                .build());
        affectedSections.put(section.getId(), section);
        successCount++;
      } catch (DuplicateKeyException ex) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Sinh viên đã ghi danh lớp " + sectionCode));
      }
    }

    affectedSections.values().forEach(this::recountEnrolled);
    CsvImportResultResponse result =
        CsvImportResultResponse.builder()
            .totalRows(totalRows)
            .successCount(successCount)
            .failureCount(errors.size())
            .errors(errors)
            .build();
    AuditLogHelper.record(
        auditService,
        AuditAction.IMPORT,
        "enrollments",
        "batch",
        null,
        result,
        "Import CSV ghi danh nhiều lớp");
    return result;
  }
}
