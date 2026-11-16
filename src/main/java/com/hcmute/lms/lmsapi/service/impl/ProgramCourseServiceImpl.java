package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.ProgramCourseCsvImportResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseRequest;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseUpdateRequest;
import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.ProgramCourseMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.ProgramCourseService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramCourseServiceImpl implements ProgramCourseService {

  private final ProgramCourseRepository programCourseRepository;
  private final ProgramVersionRepository programVersionRepository;
  private final CourseRepository courseRepository;
  private final ProgramCourseMapper programCourseMapper;

  @Override
  public List<ProgramCourseResponse> getCoursesByProgramVersion(String programVersionId) {
    return getCoursesByProgramVersion(programVersionId, null, null, null, null);
  }

  @Override
  public List<ProgramCourseResponse> getCoursesByProgramVersion(
      String programVersionId,
      Integer semester,
      CourseType type,
      CourseCategory category,
      String search) {
    requireProgramVersion(programVersionId);
    List<ProgramCourse> programCourses =
        programCourseRepository.findByProgramVersionIdOrderBySemesterAscCourseIdAsc(
            programVersionId);

    List<String> courseIds = programCourses.stream().map(ProgramCourse::getCourseId).toList();
    Map<String, Course> courseMap =
        courseRepository.findAllById(courseIds).stream()
            .collect(Collectors.toMap(Course::getId, Function.identity()));

    String query = search != null && !search.isBlank() ? search.trim().toLowerCase() : null;
    return programCourses.stream()
        .filter(pc -> semester == null || semester.equals(pc.getSemester()))
        .filter(pc -> type == null || type == pc.getType())
        .filter(pc -> category == null || category == pc.getCategory())
        .map(pc -> programCourseMapper.toResponse(pc, courseMap.get(pc.getCourseId())))
        .filter(
            resp -> {
              if (query == null) {
                return true;
              }
              if (resp.getCourse() == null) {
                return false;
              }
              String code = resp.getCourse().getCode();
              String name = resp.getCourse().getName();
              return (code != null && code.toLowerCase().contains(query))
                  || (name != null && name.toLowerCase().contains(query));
            })
        .toList();
  }

  @Override
  public ProgramCourseResponse getProgramCourse(String programVersionId, String courseId) {
    requireProgramVersion(programVersionId);
    ObjectIdUtils.requireValid(courseId, "courseId");

    ProgramCourse programCourse =
        programCourseRepository
            .findByProgramVersionIdAndCourseId(programVersionId, courseId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "ProgramCourse", "courseId", courseId + " in PV " + programVersionId));

    Course course = courseRepository.findById(courseId).orElse(null);
    return programCourseMapper.toResponse(programCourse, course);
  }

  @Override
  public ProgramCourseResponse addCourseToProgramVersion(
      String programVersionId, ProgramCourseRequest request) {
    requireProgramVersion(programVersionId);
    ObjectIdUtils.requireValid(request.getCourseId(), "courseId");

    Course course =
        courseRepository
            .findById(request.getCourseId())
            .orElseThrow(
                () ->
                    new BusinessRuleViolationException(
                        "COURSE_NOT_FOUND", "Học phần không tồn tại: " + request.getCourseId()));

    if (programCourseRepository.existsByProgramVersionIdAndCourseId(
        programVersionId, request.getCourseId())) {
      throw conflict(request.getCourseId());
    }

    List<String> sanitizedPrereqs =
        validatePrerequisites(request.getCourseId(), request.getPrerequisiteCourseIds());
    List<String> sanitizedPreviousCourses =
        validatePreviousCourses(request.getCourseId(), request.getPreviousCourseIds());
    validateNoPrerequisiteAndPreviousConflict(sanitizedPrereqs, sanitizedPreviousCourses);

    Integer credits = request.getCredits() != null ? request.getCredits() : course.getCredits();

    ProgramCourse programCourse =
        ProgramCourse.builder()
            .programVersionId(programVersionId)
            .courseId(request.getCourseId())
            .semester(request.getSemester())
            .credits(credits)
            .type(request.getType())
            .category(request.getCategory())
            .prerequisiteCourseIds(sanitizedPrereqs)
            .previousCourseIds(sanitizedPreviousCourses)
            .build();

    try {
      programCourse = programCourseRepository.save(programCourse);
    } catch (DuplicateKeyException ex) {
      log.warn(
          "Duplicate program course mapping for pvId={} courseId={}",
          programVersionId,
          request.getCourseId());
      throw conflict(request.getCourseId());
    }

    log.info(
        "Course {} assigned to ProgramVersion {} successfully",
        request.getCourseId(),
        programVersionId);
    return programCourseMapper.toResponse(programCourse, course);
  }

  @Override
  public ProgramCourseResponse updateProgramCourse(
      String programVersionId, String courseId, ProgramCourseUpdateRequest request) {
    requireProgramVersion(programVersionId);
    ObjectIdUtils.requireValid(courseId, "courseId");

    ProgramCourse programCourse =
        programCourseRepository
            .findByProgramVersionIdAndCourseId(programVersionId, courseId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "ProgramCourse", "courseId", courseId + " in PV " + programVersionId));

    List<String> sanitizedPrereqs =
        validatePrerequisites(courseId, request.getPrerequisiteCourseIds());
    List<String> sanitizedPreviousCourses =
        validatePreviousCourses(courseId, request.getPreviousCourseIds());
    validateNoPrerequisiteAndPreviousConflict(sanitizedPrereqs, sanitizedPreviousCourses);

    programCourse.setSemester(request.getSemester());
    programCourse.setCredits(request.getCredits());
    programCourse.setType(request.getType());
    programCourse.setCategory(request.getCategory());
    programCourse.setPrerequisiteCourseIds(sanitizedPrereqs);
    programCourse.setPreviousCourseIds(sanitizedPreviousCourses);

    programCourse = programCourseRepository.save(programCourse);
    Course course = courseRepository.findById(courseId).orElse(null);

    log.info("ProgramCourse mapping updated for pvId={} courseId={}", programVersionId, courseId);
    return programCourseMapper.toResponse(programCourse, course);
  }

  @Override
  public void removeCourseFromProgramVersion(String programVersionId, String courseId) {
    requireProgramVersion(programVersionId);
    ObjectIdUtils.requireValid(courseId, "courseId");

    if (!programCourseRepository.existsByProgramVersionIdAndCourseId(programVersionId, courseId)) {
      throw new ResourceNotFoundException(
          "ProgramCourse", "courseId", courseId + " in PV " + programVersionId);
    }

    programCourseRepository.deleteByProgramVersionIdAndCourseId(programVersionId, courseId);
    log.info("Course {} removed from ProgramVersion {}", courseId, programVersionId);
  }

  private void requireProgramVersion(String programVersionId) {
    ObjectIdUtils.requireValid(programVersionId, "programVersionId");
    if (!programVersionRepository.existsById(programVersionId)) {
      throw new BusinessRuleViolationException(
          "PROGRAM_VERSION_NOT_FOUND",
          "Phiên bản chương trình đào tạo không tồn tại: " + programVersionId);
    }
  }

  private List<String> validatePrerequisites(String courseId, List<String> prerequisiteCourseIds) {
    if (prerequisiteCourseIds == null || prerequisiteCourseIds.isEmpty()) {
      return new ArrayList<>();
    }

    List<String> distinctPrereqs = new ArrayList<>(new LinkedHashSet<>(prerequisiteCourseIds));

    for (String prereqId : distinctPrereqs) {
      ObjectIdUtils.requireValid(prereqId, "prerequisiteCourseId");

      if (prereqId.equals(courseId)) {
        throw new BusinessRuleViolationException(
            "PREREQUISITE_SELF_REFERENCE",
            "Học phần không thể là học phần tiên quyết của chính nó: " + prereqId);
      }

      if (!courseRepository.existsById(prereqId)) {
        throw new BusinessRuleViolationException(
            "PREREQUISITE_NOT_FOUND", "Học phần tiên quyết không tồn tại: " + prereqId);
      }
    }

    return distinctPrereqs;
  }

  private List<String> validatePreviousCourses(String courseId, List<String> previousCourseIds) {
    if (previousCourseIds == null || previousCourseIds.isEmpty()) {
      return new ArrayList<>();
    }

    List<String> distinctPrevious = new ArrayList<>(new LinkedHashSet<>(previousCourseIds));

    for (String prevId : distinctPrevious) {
      ObjectIdUtils.requireValid(prevId, "previousCourseId");

      if (prevId.equals(courseId)) {
        throw new BusinessRuleViolationException(
            "PREVIOUS_COURSE_SELF_REFERENCE",
            "Học phần không thể là học phần học trước của chính nó: " + prevId);
      }

      if (!courseRepository.existsById(prevId)) {
        throw new BusinessRuleViolationException(
            "PREVIOUS_COURSE_NOT_FOUND", "Học phần học trước không tồn tại: " + prevId);
      }
    }

    return distinctPrevious;
  }

  private void validateNoPrerequisiteAndPreviousConflict(
      List<String> prerequisiteCourseIds, List<String> previousCourseIds) {
    if (prerequisiteCourseIds == null || previousCourseIds == null) {
      return;
    }

    for (String prereqId : prerequisiteCourseIds) {
      if (previousCourseIds.contains(prereqId)) {
        throw new BusinessRuleViolationException(
            "PREREQUISITE_AND_PREVIOUS_CONFLICT",
            "Học phần không thể đồng thời vừa là học phần tiên quyết vừa là học phần học trước: "
                + prereqId);
      }
    }
  }

  @Override
  public AssessmentScoreTemplate generateTemplateCsv() {
    String csv =
        "\uFEFF"
            + "courseCode,semester,credits,type,category,previousCourseCodes,prerequisiteCourseCodes\r\n"
            + "INFI130110,1,3,COMPULSORY,MAJOR_FOUNDATION,,\r\n"
            + "DANA230410,3,3,COMPULSORY,SPECIALIZED,FUBY323808;DMSY233208,\r\n";
    return new AssessmentScoreTemplate(
        "program-courses-template.csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public ProgramCourseCsvImportResponse importProgramCoursesCsv(
      String programVersionId, MultipartFile file) {
    ObjectIdUtils.requireValid(programVersionId, "programVersionId");
    ProgramVersion pv =
        programVersionRepository
            .findById(programVersionId)
            .orElseThrow(
                () -> new ResourceNotFoundException("ProgramVersion", "id", programVersionId));
    if (pv.getStatus() == ProgramVersionStatus.ARCHIVED) {
      throw new BusinessRuleViolationException(
          "Không thể import khung học phần khi phiên bản CTĐT đã lưu trữ (ARCHIVED)");
    }

    CsvTextParser.ParsedCsv parsed = CsvTextParser.read(file);
    CsvTextParser.requireColumns(parsed.headerIndex(), "courseCode");

    Map<String, ProgramCourse> existingByCourseId =
        programCourseRepository
            .findByProgramVersionIdOrderBySemesterAscCourseIdAsc(programVersionId)
            .stream()
            .collect(
                Collectors.toMap(ProgramCourse::getCourseId, Function.identity(), (a, b) -> a));
    Map<String, Course> courseCache = new LinkedHashMap<>();
    List<String> warnings = new ArrayList<>();
    List<ProgramCourse> toSave = new ArrayList<>();
    List<Course> savedCourses = new ArrayList<>();
    int importedCount = 0;
    int updatedCount = 0;

    for (int i = 0; i < parsed.rows().size(); i++) {
      int rowNum = i + 2;
      List<String> row = parsed.rows().get(i);
      String courseCode = CsvTextParser.cell(parsed.headerIndex(), row, "courseCode");
      if (courseCode.isEmpty()) {
        throw new BusinessRuleViolationException(
            "Dòng " + rowNum + ": courseCode không được để trống");
      }

      Course course = resolveCourseByCode(courseCode, courseCache);
      if (course == null) {
        warnings.add(
            "Dòng " + rowNum + ": Không tìm thấy học phần mã " + courseCode + "; đã bỏ qua.");
        continue;
      }

      Integer semester =
          parsePositiveInt(
              CsvTextParser.cell(parsed.headerIndex(), row, "semester"), "semester", rowNum);
      Integer credits =
          parsePositiveInt(
              CsvTextParser.cell(parsed.headerIndex(), row, "credits"), "credits", rowNum);
      if (credits == null) {
        credits = course.getCredits();
      }
      CourseType type =
          parseEnum(
              CsvTextParser.cell(parsed.headerIndex(), row, "type"),
              CourseType.class,
              "type",
              rowNum);
      CourseCategory category =
          parseEnum(
              CsvTextParser.cell(parsed.headerIndex(), row, "category"),
              CourseCategory.class,
              "category",
              rowNum);

      List<String> previousIds =
          resolveRelatedCourseIds(
              CsvTextParser.cell(parsed.headerIndex(), row, "previousCourseCodes"),
              course.getId(),
              "học trước",
              rowNum,
              courseCache,
              warnings);
      List<String> prerequisiteIds =
          resolveRelatedCourseIds(
              CsvTextParser.cell(parsed.headerIndex(), row, "prerequisiteCourseCodes"),
              course.getId(),
              "tiên quyết",
              rowNum,
              courseCache,
              warnings);
      validateNoPrerequisiteAndPreviousConflict(prerequisiteIds, previousIds);

      ProgramCourse pc = existingByCourseId.get(course.getId());
      if (pc == null) {
        pc =
            ProgramCourse.builder()
                .programVersionId(programVersionId)
                .courseId(course.getId())
                .build();
        importedCount++;
      } else {
        updatedCount++;
      }
      pc.setSemester(semester);
      pc.setCredits(credits);
      pc.setType(type);
      pc.setCategory(category);
      pc.setPreviousCourseIds(previousIds);
      pc.setPrerequisiteCourseIds(prerequisiteIds);
      existingByCourseId.put(course.getId(), pc);
      toSave.add(pc);
      savedCourses.add(course);
    }

    List<ProgramCourse> saved = programCourseRepository.saveAll(toSave);
    List<ProgramCourseResponse> responses = new ArrayList<>();
    for (int i = 0; i < saved.size(); i++) {
      responses.add(programCourseMapper.toResponse(saved.get(i), savedCourses.get(i)));
    }

    return ProgramCourseCsvImportResponse.builder()
        .programVersionId(programVersionId)
        .importedCount(importedCount)
        .updatedCount(updatedCount)
        .warnings(warnings)
        .courses(responses)
        .build();
  }

  private Course resolveCourseByCode(String code, Map<String, Course> cache) {
    if (cache.containsKey(code)) {
      return cache.get(code);
    }
    Optional<Course> found = courseRepository.findByCode(code);
    Course resolved = found.orElse(null);
    cache.put(code, resolved);
    return resolved;
  }

  private List<String> resolveRelatedCourseIds(
      String rawCodes,
      String selfCourseId,
      String relationLabel,
      int rowNum,
      Map<String, Course> cache,
      List<String> warnings) {
    if (rawCodes == null || rawCodes.isEmpty()) {
      return new ArrayList<>();
    }
    List<String> ids = new ArrayList<>();
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (String token : rawCodes.split("[;|]")) {
      String code = token.trim();
      if (code.isEmpty()) {
        continue;
      }
      Course related = resolveCourseByCode(code, cache);
      if (related == null) {
        warnings.add(
            "Dòng "
                + rowNum
                + ": Không tìm thấy học phần "
                + relationLabel
                + " mã "
                + code
                + "; đã bỏ qua.");
        continue;
      }
      if (related.getId().equals(selfCourseId)) {
        throw new BusinessRuleViolationException(
            "Dòng "
                + rowNum
                + ": Học phần không thể tự tham chiếu chính nó làm môn "
                + relationLabel);
      }
      if (seen.add(related.getId())) {
        ids.add(related.getId());
      }
    }
    return ids;
  }

  private Integer parsePositiveInt(String raw, String fieldLabel, int rowNum) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    try {
      int val = Integer.parseInt(raw);
      if (val < 1) {
        throw new BusinessRuleViolationException(
            "Dòng " + rowNum + ": " + fieldLabel + " phải >= 1, nhận được " + raw);
      }
      return val;
    } catch (NumberFormatException ex) {
      throw new BusinessRuleViolationException(
          "Dòng " + rowNum + ": giá trị số nguyên không hợp lệ cho " + fieldLabel + ": " + raw);
    }
  }

  private <E extends Enum<E>> E parseEnum(
      String raw, Class<E> enumType, String fieldLabel, int rowNum) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    try {
      return Enum.valueOf(enumType, raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new BusinessRuleViolationException(
          "Dòng " + rowNum + ": giá trị không hợp lệ cho " + fieldLabel + ": " + raw);
    }
  }

  private AppException conflict(String courseId) {
    return new AppException(
        ErrorCode.CONFLICT,
        HttpStatus.CONFLICT,
        "Học phần đã được gán vào phiên bản chương trình đào tạo: " + courseId);
  }
}
