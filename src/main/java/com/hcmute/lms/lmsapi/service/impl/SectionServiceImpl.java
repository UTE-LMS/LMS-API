package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.SectionRequest;
import com.hcmute.lms.lmsapi.dto.SectionResponse;
import com.hcmute.lms.lmsapi.dto.SectionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.SectionUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.AuditAction;
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
import com.hcmute.lms.lmsapi.mapper.UserMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import com.hcmute.lms.lmsapi.service.SectionService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import com.hcmute.lms.lmsapi.util.StringNormalizationUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
  private final UserMapper userMapper;

  @Autowired(required = false)
  private AuditService auditService;

  @Override
  public PageResponse<SectionResponse> getSections(
      UserPrincipal principal, String termId, String courseId, String search, int page, int size) {
    String term = normalizeFilter(termId, "termId");
    String course = normalizeFilter(courseId, "courseId");
    PageableValidationUtils.validate(page, size);

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

    String keyword =
        search != null && !search.isBlank() ? search.trim().toLowerCase(Locale.ROOT) : null;

    // US-19/AC-4: chỉ trả về lớp mà người gọi được đọc (giảng viên: lớp mình) hoặc quản lý được
    List<CourseSection> visible =
        candidates.stream()
            .filter(
                s ->
                    scopeAuthorizationService.canAccessLoadedSection(principal, s, AccessType.READ)
                        || scopeAuthorizationService.canManageCourseSections(
                            principal, s.getCourseId()))
            .filter(
                s ->
                    keyword == null
                        || (s.getCode() != null
                            && s.getCode().toLowerCase(Locale.ROOT).contains(keyword)))
            .sorted(Comparator.comparing(CourseSection::getCode))
            .toList();
    return PageResponse.slice(
        visible,
        page,
        size,
        pageSections ->
            sectionMapper.toResponseList(pageSections, loadLecturersById(pageSections)));
  }

  @Override
  public List<UserResponse> getActiveLecturers(String facultyId) {
    String cleanFacultyId = normalizeFilter(facultyId, "facultyId");
    List<User> lecturers =
        userRepository.findByRolesContainingAndStatus(Role.LECTURER, UserStatus.ACTIVE);
    return userMapper.toResponseList(
        lecturers.stream()
            .filter(u -> cleanFacultyId == null || Objects.equals(u.getFacultyId(), cleanFacultyId))
            .sorted(
                Comparator.comparing(
                        (User u) -> u.getFullName() != null ? u.getFullName() : "",
                        String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(
                        u -> u.getUsername() != null ? u.getUsername() : "",
                        String.CASE_INSENSITIVE_ORDER))
            .toList());
  }

  @Override
  public SectionResponse getSection(String id) {
    CourseSection section = requireSection(id);
    return sectionMapper.toResponse(section, loadLecturersById(List.of(section)));
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
    ValidatedLecturers validated = validateLecturers(request.getLecturerIds());

    CourseSection section = sectionMapper.toDocument(request, validated.ids());
    try {
      CourseSection saved = sectionRepository.save(section);
      log.info("Mở lớp học phần id=[{}] mã [{}]", saved.getId(), saved.getCode());
      AuditLogHelper.record(
          auditService, AuditAction.CREATE, "course_sections", saved.getId(), null, saved, null);
      return sectionMapper.toResponse(saved, validated.users());
    } catch (DuplicateKeyException ex) {
      throw conflict(String.format("Mã lớp học phần [%s] đã tồn tại trong hệ thống", code));
    }
  }

  @Override
  public SectionResponse updateLecturers(String id, SectionUpdateRequest request) {
    CourseSection section = requireSection(id);
    ValidatedLecturers validated = validateLecturers(request.getLecturerIds());
    section.setLecturerIds(validated.ids());
    CourseSection saved = sectionRepository.save(section);
    log.info("Cập nhật giảng viên phụ trách lớp id=[{}] mã [{}]", saved.getId(), saved.getCode());
    AuditLogHelper.record(
        auditService, AuditAction.UPDATE, "course_sections", saved.getId(), null, saved, null);
    return sectionMapper.toResponse(saved, validated.users());
  }

  @Override
  public SectionResponse updateStatus(
      String id, SectionStatusUpdateRequest request, String actorUsername) {
    CourseSection section = requireSection(id);
    SectionStatus current = section.getStatus();
    SectionStatus target = request.getStatus();

    if (current == target) {
      return sectionMapper.toResponse(section, loadLecturersById(List.of(section)));
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
    AuditLogHelper.record(
        auditService,
        AuditAction.STATUS_CHANGE,
        "course_sections",
        saved.getId(),
        Map.of("status", current),
        Map.of("status", target),
        request.getReason());
    return sectionMapper.toResponse(saved, loadLecturersById(List.of(saved)));
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
  private ValidatedLecturers validateLecturers(List<String> requestedIds) {
    List<String> distinctIds = new ArrayList<>(new LinkedHashSet<>(requestedIds));
    Map<String, User> found =
        userRepository.findAllById(distinctIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

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
    return new ValidatedLecturers(distinctIds, found);
  }

  private Map<String, User> loadLecturersById(List<CourseSection> sections) {
    Set<String> allIds = new LinkedHashSet<>();
    for (CourseSection section : sections) {
      if (section.getLecturerIds() != null) {
        for (String lecturerId : section.getLecturerIds()) {
          if (lecturerId != null && !lecturerId.isBlank()) {
            allIds.add(lecturerId);
          }
        }
      }
    }
    if (allIds.isEmpty()) {
      return Map.of();
    }
    return userRepository.findAllById(allIds).stream()
        .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
  }

  private record ValidatedLecturers(List<String> ids, Map<String, User> users) {}

  private boolean hasLecturerRole(User user) {
    return user.getRoles() != null && user.getRoles().contains(Role.LECTURER);
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

  @Override
  public AssessmentScoreTemplate generateTemplateCsv() {
    String csv =
        "\uFEFF"
            + "code,courseCode,syllabusVersion,termCode,lecturerUsernames\r\n"
            + "OOPR230279_01,OOPR230279,2026.1,2026-HK1,gv_an;gv_binh\r\n";
    return new AssessmentScoreTemplate(
        "sections-template.csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public CsvImportResultResponse importSections(MultipartFile file, UserPrincipal principal) {
    CsvTextParser.ParsedCsv parsed = CsvTextParser.read(file);
    CsvTextParser.requireColumns(
        parsed.headerIndex(),
        "code",
        "courseCode",
        "syllabusVersion",
        "termCode",
        "lecturerUsernames");

    int totalRows = parsed.rows().size();
    int successCount = 0;
    List<CsvImportResultResponse.RowError> errors = new ArrayList<>();
    Set<String> seenCodes = new HashSet<>();
    Map<String, Course> courseCache = new HashMap<>();
    Map<String, Term> termCache = new HashMap<>();
    Map<String, User> userCache = new HashMap<>();

    for (int i = 0; i < totalRows; i++) {
      int rowNum = i + 2;
      List<String> row = parsed.rows().get(i);
      String code = CsvTextParser.cell(parsed.headerIndex(), row, "code");
      String courseCode = CsvTextParser.cell(parsed.headerIndex(), row, "courseCode");
      String syllabusVersion = CsvTextParser.cell(parsed.headerIndex(), row, "syllabusVersion");
      String termCode = CsvTextParser.cell(parsed.headerIndex(), row, "termCode");
      String rawLecturers = CsvTextParser.cell(parsed.headerIndex(), row, "lecturerUsernames");

      if (code.isEmpty()) {
        errors.add(
            new CsvImportResultResponse.RowError(rowNum, "", "Thiếu mã lớp học phần (code)"));
        continue;
      }
      if (!seenCodes.add(code) || sectionRepository.existsByCode(code)) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, code, "Mã lớp học phần đã tồn tại: " + code));
        continue;
      }
      if (courseCode.isEmpty() || syllabusVersion.isEmpty() || termCode.isEmpty()) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, code, "Thiếu courseCode, syllabusVersion hoặc termCode"));
        continue;
      }

      Course course =
          courseCache.computeIfAbsent(courseCode, k -> courseRepository.findByCode(k).orElse(null));
      if (course == null) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, code, "Không tìm thấy học phần: " + courseCode));
        continue;
      }
      if (!scopeAuthorizationService.canManageCourseSections(principal, course.getId())) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, code, "Không có quyền mở lớp cho học phần thuộc khoa khác: " + courseCode));
        continue;
      }

      Syllabus syllabus =
          syllabusRepository.findByCourseIdAndVersion(course.getId(), syllabusVersion).orElse(null);
      if (syllabus == null || syllabus.getStatus() != SyllabusStatus.CONFIRMED) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum,
                code,
                "Đề cương không tồn tại hoặc chưa CONFIRMED: "
                    + courseCode
                    + " / "
                    + syllabusVersion));
        continue;
      }

      Term term =
          termCache.computeIfAbsent(termCode, k -> termRepository.findByCode(k).orElse(null));
      if (term == null || term.getStatus() != TermStatus.OPEN) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, code, "Học kỳ không tồn tại hoặc không ở trạng thái OPEN: " + termCode));
        continue;
      }

      List<String> lecturerIds = resolveLecturersByUsernames(rawLecturers, userCache);
      if (lecturerIds == null || lecturerIds.isEmpty()) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, code, "Danh sách giảng viên không hợp lệ hoặc không có role LECTURER"));
        continue;
      }

      CourseSection section =
          CourseSection.builder()
              .code(code)
              .courseId(course.getId())
              .syllabusId(syllabus.getId())
              .termId(term.getId())
              .lecturerIds(lecturerIds)
              .enrolledCount(0)
              .status(SectionStatus.OPEN)
              .build();
      try {
        sectionRepository.save(section);
        successCount++;
      } catch (DuplicateKeyException ex) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, code, "Mã lớp học phần đã tồn tại: " + code));
      }
    }

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
        "course_sections",
        "batch",
        null,
        result,
        "Import CSV lớp học phần");
    return result;
  }

  private List<String> resolveLecturersByUsernames(
      String rawLecturers, Map<String, User> userCache) {
    if (rawLecturers == null || rawLecturers.isBlank()) {
      return List.of();
    }
    Set<String> ids = new LinkedHashSet<>();
    for (String token : rawLecturers.split("[;|]")) {
      String trimmed = token.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      String username = StringNormalizationUtils.normalizeUsername(trimmed);
      User user =
          userCache.computeIfAbsent(username, k -> userRepository.findByUsername(k).orElse(null));
      if (user == null || user.getStatus() != UserStatus.ACTIVE || !hasLecturerRole(user)) {
        return null;
      }
      ids.add(user.getId());
    }
    return new ArrayList<>(ids);
  }
}
