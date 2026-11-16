package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CourseImportResultResponse;
import com.hcmute.lms.lmsapi.dto.CourseImportRowError;
import com.hcmute.lms.lmsapi.dto.CourseRequest;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import com.hcmute.lms.lmsapi.dto.CourseUpdateRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.CourseMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.FacultyRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.CourseService;
import com.hcmute.lms.lmsapi.util.CsvUtils;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

  private final CourseRepository courseRepository;
  private final FacultyRepository facultyRepository;
  private final ProgramCourseRepository programCourseRepository;
  private final ProgramVersionRepository programVersionRepository;
  private final CourseMapper courseMapper;
  private final MongoTemplate mongoTemplate;

  @Autowired(required = false)
  private AuditService auditService;

  @Override
  public PageResponse<CourseResponse> getCourses(
      int page, int size, String search, String facultyId) {
    return getCourses(page, size, search, facultyId, null);
  }

  @Override
  public PageResponse<CourseResponse> getCourses(
      int page, int size, String search, String facultyId, Boolean isActive) {
    PageableValidationUtils.validate(page, size);
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));
    boolean hasSearch = search != null && !search.isBlank();
    boolean hasFaculty = facultyId != null && !facultyId.isBlank();

    if (isActive == null) {
      Page<Course> coursePage;
      if (hasFaculty) {
        String cleanFacultyId = facultyId.trim();
        ObjectIdUtils.requireValid(cleanFacultyId, "facultyId");
        if (hasSearch) {
          String query = search.trim();
          coursePage =
              courseRepository
                  .findByFacultyIdAndNameContainingIgnoreCaseOrFacultyIdAndCodeContainingIgnoreCase(
                      cleanFacultyId, query, cleanFacultyId, query, pageable);
        } else {
          coursePage = courseRepository.findByFacultyId(cleanFacultyId, pageable);
        }
      } else if (hasSearch) {
        String query = search.trim();
        coursePage =
            courseRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(
                query, query, pageable);
      } else {
        coursePage = courseRepository.findAll(pageable);
      }
      List<CourseResponse> content = courseMapper.toResponseList(coursePage.getContent());
      return PageResponse.of(
          content, coursePage.getNumber(), coursePage.getSize(), coursePage.getTotalElements());
    }

    List<Criteria> criteriaList = new ArrayList<>();
    criteriaList.add(Criteria.where("isActive").is(isActive));
    if (hasFaculty) {
      String cleanFacultyId = facultyId.trim();
      ObjectIdUtils.requireValid(cleanFacultyId, "facultyId");
      criteriaList.add(Criteria.where("facultyId").is(new ObjectId(cleanFacultyId)));
    }
    if (hasSearch) {
      String regex = Pattern.quote(search.trim());
      criteriaList.add(
          new Criteria()
              .orOperator(
                  Criteria.where("name").regex(regex, "i"),
                  Criteria.where("code").regex(regex, "i")));
    }

    Query query = new Query(new Criteria().andOperator(criteriaList.toArray(Criteria[]::new)));
    long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Course.class);
    query.with(pageable);
    List<Course> courses = mongoTemplate.find(query, Course.class);
    return PageResponse.of(
        courseMapper.toResponseList(courses),
        pageable.getPageNumber(),
        pageable.getPageSize(),
        total);
  }

  @Override
  public CourseResponse getCourseById(String id) {
    ObjectIdUtils.requireValid(id, "id");
    Course course =
        courseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
    return courseMapper.toResponse(course);
  }

  @Override
  public CourseResponse createCourse(CourseRequest request) {
    String code = request.getCode().trim();
    if (courseRepository.existsByCode(code)) {
      throw conflict(code);
    }

    String facultyId = request.getFacultyId() != null ? request.getFacultyId().trim() : null;
    if (facultyId != null && !facultyId.isEmpty()) {
      validateActiveFaculty(facultyId);
    } else {
      facultyId = null;
    }

    Course course =
        Course.builder()
            .facultyId(facultyId)
            .code(code)
            .name(request.getName().trim())
            .credits(request.getCredits())
            .isActive(true)
            .extra(request.getExtra())
            .build();

    try {
      course = courseRepository.save(course);
    } catch (DuplicateKeyException ex) {
      log.warn("Duplicate course code on create: {}", code);
      throw conflict(code);
    }

    log.info("Course created successfully: id={}, code={}", course.getId(), course.getCode());
    AuditLogHelper.record(
        auditService, AuditAction.CREATE, "courses", course.getId(), null, course, null);
    return courseMapper.toResponse(course);
  }

  @Override
  public CourseResponse updateCourse(String id, CourseUpdateRequest request) {
    ObjectIdUtils.requireValid(id, "id");
    Course course =
        courseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));

    String newName = request.getName().trim();
    boolean coreContentChanged =
        !newName.equals(course.getName())
            || !java.util.Objects.equals(request.getCredits(), course.getCredits());
    if (coreContentChanged && isCourseInPublishedProgramVersions(id)) {
      throw new BusinessRuleViolationException(
          "COURSE_LOCKED_BY_PROGRAM_VERSION",
          "Không thể thay đổi tên hoặc số tín chỉ của học phần đã thuộc phiên bản CTĐT đã ban hành (ACTIVE/ARCHIVED).");
    }

    if (Boolean.FALSE.equals(request.getIsActive()) && course.isActive()) {
      ensureCourseNotInActiveProgramVersions(id);
      course.setActive(false);
    } else if (Boolean.TRUE.equals(request.getIsActive())) {
      course.setActive(true);
    }
    if (request.getFacultyId() != null && !request.getFacultyId().isBlank()) {
      String newFacultyId = request.getFacultyId().trim();
      validateActiveFaculty(newFacultyId);
      course.setFacultyId(newFacultyId);
    }

    course.setName(newName);
    course.setCredits(request.getCredits());
    course.setExtra(request.getExtra());

    course = courseRepository.save(course);
    log.info("Course updated successfully: id={}, code={}", course.getId(), course.getCode());
    AuditLogHelper.record(
        auditService, AuditAction.UPDATE, "courses", course.getId(), null, course, null);
    return courseMapper.toResponse(course);
  }

  @Override
  public void deleteCourse(String id) {
    ObjectIdUtils.requireValid(id, "id");
    if (!courseRepository.existsById(id)) {
      throw new ResourceNotFoundException("Course", "id", id);
    }

    ensureCourseNotInActiveProgramVersions(id);
    if (programCourseRepository.existsByCourseId(id)) {
      throw new BusinessRuleViolationException(
          "COURSE_IN_USE", "Không thể xóa học phần đang được gán vào chương trình đào tạo: " + id);
    }

    courseRepository.deleteById(id);
    log.info("Course deleted successfully: id={}", id);
    AuditLogHelper.record(auditService, AuditAction.DELETE, "courses", id, null, null, null);
  }

  private void ensureCourseNotInActiveProgramVersions(String courseId) {
    List<ProgramCourse> links = programCourseRepository.findByCourseId(courseId);
    if (links.isEmpty()) {
      return;
    }
    Set<String> pvIds =
        links.stream().map(ProgramCourse::getProgramVersionId).collect(Collectors.toSet());
    if (programVersionRepository.existsByIdInAndStatus(pvIds, ProgramVersionStatus.ACTIVE)) {
      throw new BusinessRuleViolationException(
          "COURSE_IN_USE_IN_ACTIVE_PROGRAM_VERSION",
          "Học phần hiện đang được sử dụng trong phiên bản CTĐT đang ACTIVE. Vui lòng khóa phiên bản CTĐT liên quan trước khi vô hiệu hóa hoặc xóa học phần.");
    }
  }

  private boolean isCourseInPublishedProgramVersions(String courseId) {
    List<ProgramCourse> links = programCourseRepository.findByCourseId(courseId);
    if (links.isEmpty()) {
      return false;
    }
    Set<String> pvIds =
        links.stream().map(ProgramCourse::getProgramVersionId).collect(Collectors.toSet());
    return programVersionRepository.existsByIdInAndStatusIn(
        pvIds, List.of(ProgramVersionStatus.ACTIVE, ProgramVersionStatus.ARCHIVED));
  }

  private void validateActiveFaculty(String facultyId) {
    ObjectIdUtils.requireValid(facultyId, "facultyId");
    var faculty =
        facultyRepository
            .findById(facultyId)
            .orElseThrow(
                () ->
                    new BusinessRuleViolationException(
                        "FACULTY_NOT_FOUND", "Khoa không tồn tại: " + facultyId));
    if (!faculty.isActive()) {
      throw new BusinessRuleViolationException(
          "FACULTY_INACTIVE", "Khoa đang bị khóa: " + facultyId);
    }
  }

  private AppException conflict(String code) {
    return new AppException(
        ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Mã học phần đã tồn tại: " + code);
  }

  private static final Pattern COURSE_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+$");

  @Override
  public AssessmentScoreTemplate generateTemplateCsv() {
    String csv =
        "\uFEFF"
            + "code,name,credits,facultyCode\r\n"
            + "OOPR230279,Lập trình hướng đối tượng,3,FIT\r\n"
            + "INFI130110,Nhập môn ngành Công nghệ Tài chính,3,FIT\r\n";
    return new AssessmentScoreTemplate(
        "courses-template.csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public CourseImportResultResponse importCourses(
      MultipartFile file, String facultyId, boolean allowRowFacultyCode) {
    String cleanFacultyId = (facultyId != null && !facultyId.isBlank()) ? facultyId.trim() : null;
    if (cleanFacultyId != null) {
      validateActiveFaculty(cleanFacultyId);
    }
    if (file == null || file.isEmpty()) {
      throw new AppException(
          ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Tệp CSV không được để trống");
    }

    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
      throw new AppException(
          ErrorCode.VALIDATION_ERROR,
          HttpStatus.BAD_REQUEST,
          "Định dạng tệp không hợp lệ, hệ thống chỉ chấp nhận tệp định dạng .csv");
    }

    List<List<String>> records;
    try {
      records = CsvUtils.parse(file.getInputStream());
    } catch (IllegalArgumentException ex) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (IOException ex) {
      log.error("Lỗi khi đọc tệp CSV tải lên", ex);
      throw new AppException(
          ErrorCode.VALIDATION_ERROR,
          HttpStatus.BAD_REQUEST,
          "Không thể đọc dữ liệu từ tệp CSV tải lên");
    }

    if (records.isEmpty()) {
      throw new AppException(
          ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Tệp CSV không có dữ liệu để import");
    }

    List<String> header = records.get(0);
    int codeCol = -1;
    int nameCol = -1;
    int creditsCol = -1;
    int facultyCodeCol = -1;

    for (int i = 0; i < header.size(); i++) {
      String col = header.get(i).trim().toLowerCase();
      if ("code".equals(col) || "mã học phần".equals(col) || "mã môn".equals(col)) {
        codeCol = i;
      } else if ("name".equals(col) || "tên học phần".equals(col) || "tên môn".equals(col)) {
        nameCol = i;
      } else if ("credits".equals(col) || "số tín chỉ".equals(col) || "tín chỉ".equals(col)) {
        creditsCol = i;
      } else if ("facultycode".equals(col) || "mã khoa".equals(col)) {
        facultyCodeCol = i;
      }
    }

    if (codeCol == -1 || nameCol == -1 || creditsCol == -1) {
      throw new AppException(
          ErrorCode.VALIDATION_ERROR,
          HttpStatus.BAD_REQUEST,
          "Tệp CSV thiếu các cột bắt buộc: code, name, credits");
    }

    if (records.size() == 1) {
      throw new AppException(
          ErrorCode.VALIDATION_ERROR,
          HttpStatus.BAD_REQUEST,
          "Tệp CSV không có dữ liệu học phần để import");
    }

    int totalRows = records.size() - 1;
    int successCount = 0;
    List<CourseImportRowError> errors = new ArrayList<>();
    Set<String> seenCodesInFile = new HashSet<>();
    Map<String, Faculty> facultyCache = new HashMap<>();

    for (int i = 1; i < records.size(); i++) {
      int rowNumber = i + 1; // Dòng 1 là header, nên dữ liệu bắt đầu từ dòng 2
      List<String> row = records.get(i);

      int requiredMaxCol = Math.max(codeCol, Math.max(nameCol, creditsCol));
      if (row.size() <= requiredMaxCol) {
        errors.add(
            CourseImportRowError.builder()
                .row(rowNumber)
                .code(row.size() > codeCol ? row.get(codeCol).trim() : null)
                .message("Dòng dữ liệu thiếu cột thông tin bắt buộc")
                .build());
        continue;
      }

      String code = row.get(codeCol).trim();
      String name = row.get(nameCol).trim();
      String creditsStr = row.get(creditsCol).trim();

      // Validate mã học phần
      if (code.isBlank()) {
        errors.add(
            CourseImportRowError.builder()
                .row(rowNumber)
                .code("")
                .message("Mã học phần không được để trống")
                .build());
        continue;
      }

      if (code.length() > 64) {
        errors.add(
            CourseImportRowError.builder()
                .row(rowNumber)
                .code(code)
                .message("Mã học phần tối đa 64 ký tự")
                .build());
        continue;
      }

      if (!COURSE_CODE_PATTERN.matcher(code).matches()) {
        errors.add(
            CourseImportRowError.builder()
                .row(rowNumber)
                .code(code)
                .message(
                    "Mã học phần chỉ được chứa chữ cái, chữ số, dấu gạch dưới, gạch ngang và dấu chấm")
                .build());
        continue;
      }

      // Validate tên học phần
      if (name.isBlank()) {
        errors.add(
            CourseImportRowError.builder()
                .row(rowNumber)
                .code(code)
                .message("Tên học phần không được để trống")
                .build());
        continue;
      }

      if (name.length() > 255) {
        errors.add(
            CourseImportRowError.builder()
                .row(rowNumber)
                .code(code)
                .message("Tên học phần tối đa 255 ký tự")
                .build());
        continue;
      }

      // Validate số tín chỉ
      Integer credits;
      try {
        credits = Integer.parseInt(creditsStr);
        if (credits < 0) {
          errors.add(
              CourseImportRowError.builder()
                  .row(rowNumber)
                  .code(code)
                  .message("Số tín chỉ phải không âm")
                  .build());
          continue;
        }
      } catch (NumberFormatException ex) {
        errors.add(
            CourseImportRowError.builder()
                .row(rowNumber)
                .code(code)
                .message("Số tín chỉ không hợp lệ, phải là số nguyên")
                .build());
        continue;
      }

      // Kiểm tra trùng lặp trong tệp CSV
      if (seenCodesInFile.contains(code)) {
        errors.add(
            CourseImportRowError.builder()
                .row(rowNumber)
                .code(code)
                .message("Mã học phần bị trùng lặp trong tệp CSV: " + code)
                .build());
        continue;
      }

      // Kiểm tra trùng lặp với CSDL
      if (courseRepository.existsByCode(code)) {
        errors.add(
            CourseImportRowError.builder()
                .row(rowNumber)
                .code(code)
                .message("Mã học phần đã tồn tại trong hệ thống: " + code)
                .build());
        continue;
      }

      // Xác định Khoa cho học phần: PM luôn ép theo Khoa của PM; ADMIN ưu tiên facultyCode trên
      // dòng CSV (nếu có), sau đó fallback về facultyId truyền vào trên API
      String rowFacultyId = cleanFacultyId;
      if (allowRowFacultyCode
          && facultyCodeCol != -1
          && row.size() > facultyCodeCol
          && !row.get(facultyCodeCol).trim().isEmpty()) {
        String facultyCode = row.get(facultyCodeCol).trim().toUpperCase(Locale.ROOT);
        Faculty faculty =
            facultyCache.computeIfAbsent(
                facultyCode, k -> facultyRepository.findByCode(k).orElse(null));
        if (faculty == null) {
          errors.add(
              CourseImportRowError.builder()
                  .row(rowNumber)
                  .code(code)
                  .message("Không tìm thấy Khoa với mã: " + facultyCode)
                  .build());
          continue;
        }
        if (!faculty.isActive()) {
          errors.add(
              CourseImportRowError.builder()
                  .row(rowNumber)
                  .code(code)
                  .message("Khoa đang bị khóa, không thể gán học phần: " + facultyCode)
                  .build());
          continue;
        }
        rowFacultyId = faculty.getId();
      }

      // Thêm vào danh sách đã gặp và lưu CSDL (Best effort)
      seenCodesInFile.add(code);
      Course course =
          Course.builder()
              .facultyId(rowFacultyId)
              .code(code)
              .name(name)
              .credits(credits)
              .isActive(true)
              .build();
      try {
        courseRepository.save(course);
        successCount++;
      } catch (DuplicateKeyException ex) {
        errors.add(
            CourseImportRowError.builder()
                .row(rowNumber)
                .code(code)
                .message("Mã học phần đã tồn tại trong hệ thống: " + code)
                .build());
      }
    }

    log.info(
        "Import courses completed: total={}, success={}, failure={}",
        totalRows,
        successCount,
        errors.size());

    CourseImportResultResponse result =
        CourseImportResultResponse.builder()
            .totalRows(totalRows)
            .successCount(successCount)
            .failureCount(errors.size())
            .errors(errors)
            .build();
    AuditLogHelper.record(
        auditService, AuditAction.IMPORT, "courses", "batch", null, result, "Import CSV học phần");
    return result;
  }
}
