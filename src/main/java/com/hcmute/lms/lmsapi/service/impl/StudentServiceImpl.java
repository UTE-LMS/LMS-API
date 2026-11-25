package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.StudentBatchStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.StudentBatchStatusUpdateResponse;
import com.hcmute.lms.lmsapi.dto.StudentResponse;
import com.hcmute.lms.lmsapi.dto.StudentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.StudentStatus;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.StudentService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

  private static final Pattern STUDENT_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,50}$");
  private static final int MAX_FULL_NAME_LENGTH = 150;
  private static final int MAX_COHORT_LENGTH = 50;

  private final StudentRepository studentRepository;

  @Autowired(required = false)
  private AuditService auditService;

  @Override
  public PageResponse<StudentResponse> getStudents(
      int page, int size, String search, String cohort, StudentStatus status) {
    PageableValidationUtils.validate(page, size);
    Pageable pageable = PageRequest.of(page, size);
    Page<Student> studentPage = studentRepository.searchStudents(search, cohort, status, pageable);
    List<StudentResponse> content =
        studentPage.getContent().stream().map(this::toResponse).toList();
    return PageResponse.of(
        content, studentPage.getNumber(), studentPage.getSize(), studentPage.getTotalElements());
  }

  @Override
  public StudentResponse updateStudentStatus(String id, StudentStatusUpdateRequest request) {
    ObjectIdUtils.requireValid(id, "studentId");
    Student student =
        studentRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sinh viên: " + id));

    StudentStatus previous = student.getStatus();
    StudentStatus target = request.getStatus();
    if (previous == target) {
      return toResponse(student);
    }

    student.setStatus(target);
    student.setUpdatedAt(Instant.now());
    Student saved = studentRepository.save(student);
    log.info(
        "Cập nhật trạng thái sinh viên id=[{}] code=[{}] từ [{}] sang [{}]",
        saved.getId(),
        saved.getStudentCode(),
        previous,
        target);
    AuditLogHelper.record(
        auditService,
        AuditAction.STATUS_CHANGE,
        "students",
        saved.getId(),
        previous != null ? Map.of("status", previous) : null,
        Map.of("status", target),
        null);
    return toResponse(saved);
  }

  @Override
  public StudentBatchStatusUpdateResponse updateStudentsStatusBatch(
      StudentBatchStatusUpdateRequest request) {
    Set<String> uniqueIds = new LinkedHashSet<>(request.getStudentIds());
    uniqueIds.forEach(id -> ObjectIdUtils.requireValid(id, "studentId"));

    List<Student> found = new ArrayList<>(studentRepository.findAllById(uniqueIds));
    if (found.size() < uniqueIds.size()) {
      Set<String> foundIds = found.stream().map(Student::getId).collect(Collectors.toSet());
      List<String> missingIds = uniqueIds.stream().filter(id -> !foundIds.contains(id)).toList();
      throw new ResourceNotFoundException(
          "Không tìm thấy sinh viên với ID: " + String.join(", ", missingIds));
    }

    StudentStatus targetStatus = request.getStatus();
    Instant now = Instant.now();
    List<Student> changed = new ArrayList<>();
    for (Student student : found) {
      if (student.getStatus() != targetStatus) {
        student.setStatus(targetStatus);
        student.setUpdatedAt(now);
        changed.add(student);
      }
    }

    if (!changed.isEmpty()) {
      List<Student> saved = new ArrayList<>(studentRepository.saveAll(changed));
      Map<String, Student> savedById = new HashMap<>();
      saved.forEach(s -> savedById.put(s.getId(), s));
      found.replaceAll(s -> savedById.getOrDefault(s.getId(), s));

      List<String> changedIds = saved.stream().map(Student::getId).toList();
      AuditLogHelper.record(
          auditService,
          AuditAction.STATUS_CHANGE,
          "students",
          "batch",
          null,
          Map.of("studentIds", changedIds, "status", targetStatus, "updatedCount", changed.size()),
          "Cập nhật trạng thái hàng loạt sinh viên");
    }

    List<StudentResponse> responses =
        found.stream()
            .map(this::toResponse)
            .sorted(
                Comparator.comparing(
                    StudentResponse::getStudentCode,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    return StudentBatchStatusUpdateResponse.builder()
        .requestedCount(uniqueIds.size())
        .updatedCount(changed.size())
        .status(targetStatus)
        .students(responses)
        .build();
  }

  @Override
  public AssessmentScoreTemplate generateTemplateCsv() {
    String csv =
        "\uFEFF"
            + "studentCode,fullName,cohort\r\n"
            + "23110001,Nguyễn Văn An,K23\r\n"
            + "23110002,Trần Thị Bình,K23\r\n";
    return new AssessmentScoreTemplate(
        "students-template.csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public CsvImportResultResponse importStudents(MultipartFile file) {
    CsvTextParser.ParsedCsv parsed = CsvTextParser.read(file);
    CsvTextParser.requireColumns(parsed.headerIndex(), "studentCode", "fullName", "cohort");

    int totalRows = parsed.rows().size();
    int successCount = 0;
    List<CsvImportResultResponse.RowError> errors = new ArrayList<>();
    Set<String> seenCodes = new HashSet<>();

    for (int i = 0; i < totalRows; i++) {
      int rowNum = i + 2;
      List<String> row = parsed.rows().get(i);
      String studentCode = CsvTextParser.cell(parsed.headerIndex(), row, "studentCode");
      String fullName = CsvTextParser.cell(parsed.headerIndex(), row, "fullName");
      String cohort = CsvTextParser.cell(parsed.headerIndex(), row, "cohort");

      String codeError = validateStudentCodeSyntax(studentCode);
      if (codeError != null) {
        errors.add(new CsvImportResultResponse.RowError(rowNum, studentCode, codeError));
        continue;
      }
      if (fullName.isEmpty()) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Họ và tên (fullName) không được để trống"));
        continue;
      }
      if (fullName.length() > MAX_FULL_NAME_LENGTH) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum,
                studentCode,
                "Họ và tên (fullName) dài tối đa " + MAX_FULL_NAME_LENGTH + " ký tự"));
        continue;
      }
      if (cohort.isEmpty()) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Khóa/niên khóa (cohort) không được để trống"));
        continue;
      }
      if (cohort.length() > MAX_COHORT_LENGTH) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum,
                studentCode,
                "Khóa/niên khóa (cohort) dài tối đa " + MAX_COHORT_LENGTH + " ký tự"));
        continue;
      }
      if (!seenCodes.add(studentCode) || studentRepository.existsByStudentCode(studentCode)) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Mã số sinh viên đã tồn tại: " + studentCode));
        continue;
      }

      Instant now = Instant.now();
      Student student =
          Student.builder()
              .studentCode(studentCode)
              .fullName(fullName)
              .cohort(cohort)
              .status(StudentStatus.ACTIVE)
              .createdAt(now)
              .updatedAt(now)
              .build();
      try {
        studentRepository.save(student);
        successCount++;
      } catch (DuplicateKeyException ex) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Mã số sinh viên bị trùng trong CSDL: " + studentCode));
      }
    }

    return buildAndAuditImportResult(
        totalRows, successCount, errors, AuditAction.IMPORT, "batch", "Import CSV sinh viên");
  }

  @Override
  public AssessmentScoreTemplate generateStatusTemplateCsv() {
    String csv =
        "\uFEFF" + "studentCode,status\r\n" + "23110001,GRADUATED\r\n" + "23110002,SUSPENDED\r\n";
    return new AssessmentScoreTemplate(
        "students-status-template.csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public CsvImportResultResponse importStudentStatuses(MultipartFile file) {
    CsvTextParser.ParsedCsv parsed = CsvTextParser.read(file);
    CsvTextParser.requireColumns(parsed.headerIndex(), "studentCode", "status");

    int totalRows = parsed.rows().size();
    int successCount = 0;
    List<CsvImportResultResponse.RowError> errors = new ArrayList<>();
    Set<String> seenCodes = new HashSet<>();

    for (int i = 0; i < totalRows; i++) {
      int rowNum = i + 2;
      List<String> row = parsed.rows().get(i);
      String studentCode = CsvTextParser.cell(parsed.headerIndex(), row, "studentCode");
      String rawStatus = CsvTextParser.cell(parsed.headerIndex(), row, "status");

      String codeError = validateStudentCodeSyntax(studentCode);
      if (codeError != null) {
        errors.add(new CsvImportResultResponse.RowError(rowNum, studentCode, codeError));
        continue;
      }

      StudentStatus targetStatus = parseStudentStatus(rawStatus);
      if (targetStatus == null) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum,
                studentCode,
                "Trạng thái (status) không hợp lệ: "
                    + rawStatus
                    + " (chỉ chấp nhận: ACTIVE, SUSPENDED, GRADUATED, DROPPED_OUT)"));
        continue;
      }

      if (!seenCodes.add(studentCode)) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Mã số sinh viên bị trùng trong tệp: " + studentCode));
        continue;
      }

      Optional<Student> found = studentRepository.findByStudentCode(studentCode);
      if (found.isEmpty()) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, studentCode, "Mã sinh viên không tồn tại trong hệ thống: " + studentCode));
        continue;
      }

      Student student = found.get();
      student.setStatus(targetStatus);
      student.setUpdatedAt(Instant.now());
      studentRepository.save(student);
      successCount++;
    }

    return buildAndAuditImportResult(
        totalRows,
        successCount,
        errors,
        AuditAction.STATUS_CHANGE,
        "status-import",
        "Import CSV cập nhật trạng thái sinh viên");
  }

  private String validateStudentCodeSyntax(String studentCode) {
    if (studentCode.isEmpty()) {
      return "Mã số sinh viên (studentCode) không được để trống";
    }
    if (!STUDENT_CODE_PATTERN.matcher(studentCode).matches()) {
      return "Mã số sinh viên (studentCode) chỉ gồm chữ cái, số, dấu chấm, gạch ngang, gạch dưới (1-50 ký tự)";
    }
    return null;
  }

  private StudentStatus parseStudentStatus(String rawStatus) {
    if (rawStatus == null || rawStatus.isBlank()) {
      return null;
    }
    try {
      return StudentStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private CsvImportResultResponse buildAndAuditImportResult(
      int totalRows,
      int successCount,
      List<CsvImportResultResponse.RowError> errors,
      AuditAction action,
      String entityId,
      String reason) {
    CsvImportResultResponse result =
        CsvImportResultResponse.builder()
            .totalRows(totalRows)
            .successCount(successCount)
            .failureCount(errors.size())
            .errors(errors)
            .build();
    AuditLogHelper.record(auditService, action, "students", entityId, null, result, reason);
    return result;
  }

  private StudentResponse toResponse(Student student) {
    return StudentResponse.builder()
        .id(student.getId())
        .studentCode(student.getStudentCode())
        .fullName(student.getFullName())
        .cohort(student.getCohort())
        .status(student.getStatus())
        .createdAt(student.getCreatedAt())
        .updatedAt(student.getUpdatedAt())
        .build();
  }
}
