package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.StudentBatchStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.StudentBatchStatusUpdateResponse;
import com.hcmute.lms.lmsapi.dto.StudentResponse;
import com.hcmute.lms.lmsapi.dto.StudentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.StudentStatus;
import com.hcmute.lms.lmsapi.service.StudentService;
import com.hcmute.lms.lmsapi.util.ImportTemplateResponses;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** API quản lý danh sách sinh viên và cập nhật trạng thái hàng loạt dành riêng cho ADMIN. */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StudentController {

  private final StudentService studentService;

  @GetMapping
  public ResponseEntity<PageResponse<StudentResponse>> getStudents(
      @RequestParam(defaultValue = "0", required = false)
          @Min(value = 0, message = "Chỉ số trang (page) phải lớn hơn hoặc bằng 0")
          Integer page,
      @RequestParam(defaultValue = "20", required = false)
          @Min(value = 1, message = "Kích thước trang (size) phải lớn hơn hoặc bằng 1")
          @Max(value = 100, message = "Kích thước trang (size) phải nằm trong khoảng từ 1 đến 100")
          Integer size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String cohort,
      @RequestParam(required = false) StudentStatus status) {
    PageableValidationUtils.validate(page, size);
    int resolvedPage = page != null ? page : PageableValidationUtils.DEFAULT_PAGE;
    int resolvedSize = size != null ? size : PageableValidationUtils.DEFAULT_SIZE;
    return ResponseEntity.ok(
        studentService.getStudents(resolvedPage, resolvedSize, search, cohort, status));
  }

  @PutMapping("/{id}/status")
  public ResponseEntity<StudentResponse> updateStudentStatus(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID sinh viên phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody StudentStatusUpdateRequest request) {
    log.info("API request: PUT /api/v1/students/{}/status to [{}]", id, request.getStatus());
    return ResponseEntity.ok(studentService.updateStudentStatus(id, request));
  }

  @PutMapping("/status")
  public ResponseEntity<StudentBatchStatusUpdateResponse> updateStudentsStatusBatch(
      @Valid @RequestBody StudentBatchStatusUpdateRequest request) {
    log.info(
        "API request: PUT /api/v1/students/status count=[{}] to [{}]",
        request.getStudentIds() != null ? request.getStudentIds().size() : 0,
        request.getStatus());
    return ResponseEntity.ok(studentService.updateStudentsStatusBatch(request));
  }

  @GetMapping(
      value = "/import-template",
      produces = {ImportTemplateResponses.CSV_MEDIA_TYPE, ImportTemplateResponses.XLSX_MEDIA_TYPE})
  public ResponseEntity<byte[]> getImportTemplate(
      @RequestParam(name = "format", defaultValue = "csv") String format) {
    log.info("API request: GET /api/v1/students/import-template");
    AssessmentScoreTemplate template = studentService.generateTemplateCsv();
    return ImportTemplateResponses.of(template, format);
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<CsvImportResultResponse> importStudents(
      @RequestPart("file") MultipartFile file) {
    log.info(
        "API request: POST /api/v1/students/import with file [{}]",
        file != null ? file.getOriginalFilename() : "null");
    return ResponseEntity.ok(studentService.importStudents(file));
  }

  @GetMapping(
      value = "/status-import-template",
      produces = {ImportTemplateResponses.CSV_MEDIA_TYPE, ImportTemplateResponses.XLSX_MEDIA_TYPE})
  public ResponseEntity<byte[]> getStatusImportTemplate(
      @RequestParam(name = "format", defaultValue = "csv") String format) {
    log.info("API request: GET /api/v1/students/status-import-template");
    AssessmentScoreTemplate template = studentService.generateStatusTemplateCsv();
    return ImportTemplateResponses.of(template, format);
  }

  @PostMapping(value = "/status-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<CsvImportResultResponse> importStudentStatuses(
      @RequestPart("file") MultipartFile file) {
    log.info(
        "API request: POST /api/v1/students/status-import with file [{}]",
        file != null ? file.getOriginalFilename() : "null");
    return ResponseEntity.ok(studentService.importStudentStatuses(file));
  }
}
