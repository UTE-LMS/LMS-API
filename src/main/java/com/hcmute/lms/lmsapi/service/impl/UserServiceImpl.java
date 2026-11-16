package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CreateUserRequest;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UpdateUserRequest;
import com.hcmute.lms.lmsapi.dto.UpdateUserStatusRequest;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.UserMapper;
import com.hcmute.lms.lmsapi.repository.FacultyRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.repository.UserRepositoryCustom;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.UserService;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import com.hcmute.lms.lmsapi.util.StringNormalizationUtils;
import com.hcmute.lms.lmsapi.validator.PasswordPolicyValidator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Hiện thực dịch vụ quản lý người dùng {@link UserService} (F01 / US-02). Tuân thủ nghiêm ngặt ADR
 * 0002 và ADR 0003: - Chuẩn hóa username/email trước khi lưu. - Kiểm tra chính sách mật khẩu UTF-8
 * (8-72 bytes). - Xác thực các tham chiếu scope qua {@link ScopeValidationService}. - Áp dụng các
 * ADMIN Guards bảo vệ tài khoản quản trị viên. - Thu hồi tức thời và vĩnh viễn token khi tài khoản
 * bị khóa (LOCKED) qua {@link UserRepositoryCustom#incrementTokenVersion}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final FacultyRepository facultyRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator passwordPolicyValidator;

  @Autowired(required = false)
  private AuditService auditService;

  private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$");

  @Override
  public UserResponse createUser(CreateUserRequest request) {
    log.info("Creating new user with username [{}]", request.getUsername());

    // 1. Chuẩn hóa username và email (trim, lowercase)
    String normalizedUsername = StringNormalizationUtils.normalizeUsername(request.getUsername());
    String normalizedEmail = StringNormalizationUtils.normalizeEmail(request.getEmail());

    // 2. Kiểm tra chính sách mật khẩu (8-72 bytes UTF-8)
    passwordPolicyValidator.validate(request.getPassword());

    // 3. Kiểm tra trùng lặp username hoặc email ở tầng ứng dụng
    if (userRepository.existsByUsername(normalizedUsername)) {
      throw new AppException(
          ErrorCode.CONFLICT,
          HttpStatus.CONFLICT,
          "Tên đăng nhập đã tồn tại trong hệ thống: " + normalizedUsername);
    }
    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new AppException(
          ErrorCode.CONFLICT,
          HttpStatus.CONFLICT,
          "Địa chỉ email đã tồn tại trong hệ thống: " + normalizedEmail);
    }

    // 4. Băm mật khẩu bằng BCrypt
    String passwordHash = passwordEncoder.encode(request.getPassword());

    // 6. Khởi tạo User entity
    Instant now = Instant.now();
    User user =
        User.builder()
            .username(normalizedUsername)
            .email(normalizedEmail)
            .passwordHash(passwordHash)
            .fullName(request.getFullName().trim())
            .status(UserStatus.ACTIVE)
            .tokenVersion(0L)
            .roles(
                request.getRoles() != null
                    ? new ArrayList<>(request.getRoles())
                    : new ArrayList<>())
            .createdAt(now)
            .updatedAt(now)
            .build();

    try {
      user = userRepository.save(user);
    } catch (DuplicateKeyException e) {
      log.warn("Database unique index conflict on user creation: {}", e.getMessage());
      throw new AppException(
          ErrorCode.CONFLICT,
          HttpStatus.CONFLICT,
          "Dữ liệu bị trùng lặp khóa duy nhất trong hệ thống (username hoặc email đã tồn tại)");
    }

    log.info("User [{}] created successfully with ID [{}]", user.getUsername(), user.getId());
    AuditLogHelper.record(
        auditService, AuditAction.CREATE, "users", user.getId(), null, user, null);
    return userMapper.toResponse(user);
  }

  @Override
  public PageResponse<UserResponse> getUsers(int page, int size) {
    log.debug("Fetching users page [{}] size [{}]", page, size);

    // Kiểm tra và tạo Pageable chuẩn
    Pageable pageable = PageableValidationUtils.createPageable(page, size);

    Page<User> userPage = userRepository.findAll(pageable);
    List<UserResponse> content = userMapper.toResponseList(userPage.getContent());

    return PageResponse.of(
        content, userPage.getNumber(), userPage.getSize(), userPage.getTotalElements());
  }

  @Override
  public UserResponse getUserById(String id) {
    validateObjectId(id);

    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

    return userMapper.toResponse(user);
  }

  @Override
  public UserResponse updateUser(String id, UpdateUserRequest request, String currentUserId) {
    validateObjectId(id);
    log.info("Updating user ID [{}] by current user [{}]", id, currentUserId);

    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

    // 1. Chuẩn hóa email
    String normalizedEmail = StringNormalizationUtils.normalizeEmail(request.getEmail());

    // 2. Nếu thay đổi email, kiểm tra trùng lặp email với người dùng khác
    if (!normalizedEmail.equalsIgnoreCase(user.getEmail())) {
      Optional<User> existingEmailUser = userRepository.findByEmail(normalizedEmail);
      if (existingEmailUser.isPresent() && !existingEmailUser.get().getId().equals(id)) {
        throw new AppException(
            ErrorCode.CONFLICT,
            HttpStatus.CONFLICT,
            "Địa chỉ email đã tồn tại trong hệ thống: " + normalizedEmail);
      }
    }

    // 3. ADMIN Guards: Kiểm tra bảo vệ tài khoản quản trị viên tối cao
    boolean targetHasAdmin = hasAdminRole(user.getRoles());
    boolean newRolesHaveAdmin = hasAdminRole(request.getRoles());

    // Nếu người dùng mục tiêu đang có quyền ADMIN và thao tác này gỡ bỏ quyền ADMIN:
    if (targetHasAdmin && !newRolesHaveAdmin) {
      // Guard 2: Chặn ADMIN tự gỡ vai trò ADMIN của chính mình
      if (currentUserId != null && currentUserId.equals(id)) {
        log.warn("ADMIN Guard triggered: Admin [{}] attempted self-demotion", currentUserId);
        throw new BusinessRuleViolationException(
            "ADMIN_GUARD", "Quản trị viên không được phép tự gỡ bỏ vai trò ADMIN của chính mình");
      }

      // Guard 3B: Chặn thao tác gỡ vai trò ADMIN của tài khoản ADMIN ở trạng thái ACTIVE duy nhất
      // còn lại
      if (user.getStatus() == UserStatus.ACTIVE) {
        long activeAdminCount =
            userRepository.countByRolesContainingAndStatus(Role.ADMIN, UserStatus.ACTIVE);
        if (activeAdminCount <= 1) {
          log.warn("ADMIN Guard triggered: Attempted to demote the last active ADMIN [{}]", id);
          throw new BusinessRuleViolationException(
              "ADMIN_GUARD",
              "Không thể gỡ bỏ vai trò ADMIN của tài khoản ADMIN ở trạng thái ACTIVE duy nhất còn lại trong hệ thống");
        }
      }
    }

    // 5. Cập nhật các trường cho phép (username là bất biến, không cập nhật passwordHash,
    // tokenVersion được giữ nguyên)
    user.setEmail(normalizedEmail);
    user.setFullName(request.getFullName().trim());
    user.setRoles(
        request.getRoles() != null ? new ArrayList<>(request.getRoles()) : new ArrayList<>());
    user.setUpdatedAt(Instant.now());

    try {
      user = userRepository.save(user);
    } catch (DuplicateKeyException e) {
      log.warn("Database unique index conflict on user update: {}", e.getMessage());
      throw new AppException(
          ErrorCode.CONFLICT,
          HttpStatus.CONFLICT,
          "Địa chỉ email đã tồn tại trong hệ thống: " + normalizedEmail);
    }

    log.info("User [{}] updated successfully", user.getId());
    AuditLogHelper.record(
        auditService, AuditAction.UPDATE, "users", user.getId(), null, user, null);
    return userMapper.toResponse(user);
  }

  @Override
  public UserResponse updateStatus(
      String id, UpdateUserStatusRequest request, String currentUserId) {
    validateObjectId(id);
    log.info(
        "Updating status for user [{}] to [{}] by [{}]", id, request.getStatus(), currentUserId);

    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

    UserStatus newStatus = request.getStatus();

    // Xử lý khi chuyển sang trạng thái LOCKED
    if (newStatus == UserStatus.LOCKED) {
      // Guard 1: Chặn ADMIN tự khóa tài khoản của chính mình
      if (currentUserId != null && currentUserId.equals(id)) {
        log.warn("ADMIN Guard triggered: Admin [{}] attempted self-lock", currentUserId);
        throw new BusinessRuleViolationException(
            "ADMIN_GUARD", "Quản trị viên không được phép tự khóa tài khoản của chính mình");
      }

      // Guard 3A: Chặn khóa tài khoản ADMIN ở trạng thái ACTIVE duy nhất còn lại trong hệ thống
      boolean targetHasAdmin = hasAdminRole(user.getRoles());
      if (targetHasAdmin && user.getStatus() == UserStatus.ACTIVE) {
        long activeAdminCount =
            userRepository.countByRolesContainingAndStatus(Role.ADMIN, UserStatus.ACTIVE);
        if (activeAdminCount <= 1) {
          log.warn("ADMIN Guard triggered: Attempted to lock the last active ADMIN [{}]", id);
          throw new BusinessRuleViolationException(
              "ADMIN_GUARD",
              "Không thể khóa tài khoản ADMIN ở trạng thái ACTIVE duy nhất còn lại trong hệ thống");
        }
      }

      // Cập nhật trạng thái LOCKED
      user.setStatus(UserStatus.LOCKED);
      user.setUpdatedAt(Instant.now());
      user = userRepository.save(user);

      // Tăng nguyên tử tokenVersion để thu hồi toàn bộ token cũ
      userRepository.incrementTokenVersion(id);

      // Nạp lại trạng thái mới nhất từ CSDL
      user = userRepository.findById(id).orElse(user);
    } else if (newStatus == UserStatus.ACTIVE) {
      // Mở khóa tài khoản: chuyển sang ACTIVE (tokenVersion cũ vẫn giữ nguyên giá trị đã tăng,
      // không phục hồi)
      user.setStatus(UserStatus.ACTIVE);
      user.setUpdatedAt(Instant.now());
      user = userRepository.save(user);
    }

    log.info("Status of user [{}] updated to [{}] successfully", id, newStatus);
    AuditLogHelper.record(
        auditService,
        AuditAction.STATUS_CHANGE,
        "users",
        user.getId(),
        null,
        Map.of("status", newStatus),
        null);
    return userMapper.toResponse(user);
  }

  private void validateObjectId(String id) {
    if (id == null || !ObjectId.isValid(id)) {
      throw new AppException(
          ErrorCode.VALIDATION_ERROR,
          HttpStatus.BAD_REQUEST,
          String.format("Định dạng định danh (ObjectId) '%s' không hợp lệ", id));
    }
  }

  @Override
  public AssessmentScoreTemplate generateTemplateCsv() {
    String csv =
        "\uFEFF"
            + "username,email,password,fullName,roles,facultyCode,status\r\n"
            + "pm_fit,pm_fit@hcmute.edu.vn,Password123!,Trưởng ngành CNTT,PROGRAM_MANAGER,FIT,ACTIVE\r\n"
            + "gv_an,gv_an@hcmute.edu.vn,Password123!,Nguyễn Văn An,LECTURER,,ACTIVE\r\n";
    return new AssessmentScoreTemplate("users-template.csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public CsvImportResultResponse importUsers(MultipartFile file) {
    CsvTextParser.ParsedCsv parsed = CsvTextParser.read(file);
    CsvTextParser.requireColumns(
        parsed.headerIndex(), "username", "email", "password", "fullName", "roles");

    int totalRows = parsed.rows().size();
    int successCount = 0;
    List<CsvImportResultResponse.RowError> errors = new ArrayList<>();
    Set<String> seenUsernames = new HashSet<>();
    Set<String> seenEmails = new HashSet<>();
    Map<String, Faculty> facultyCache = new HashMap<>();

    for (int i = 0; i < totalRows; i++) {
      int rowNum = i + 2;
      List<String> row = parsed.rows().get(i);
      String rawUsername = CsvTextParser.cell(parsed.headerIndex(), row, "username");
      String rawEmail = CsvTextParser.cell(parsed.headerIndex(), row, "email");
      String password = CsvTextParser.cell(parsed.headerIndex(), row, "password");
      String fullName = CsvTextParser.cell(parsed.headerIndex(), row, "fullName");
      String rawRoles = CsvTextParser.cell(parsed.headerIndex(), row, "roles");
      String rawFacultyCode = CsvTextParser.cell(parsed.headerIndex(), row, "facultyCode");
      String rawStatus = CsvTextParser.cell(parsed.headerIndex(), row, "status");

      String username = StringNormalizationUtils.normalizeUsername(rawUsername);
      String email = StringNormalizationUtils.normalizeEmail(rawEmail);

      if (username == null
          || username.length() < 3
          || username.length() > 50
          || !USERNAME_PATTERN.matcher(username).matches()) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, rawUsername, "Tên đăng nhập phải 3-50 ký tự hợp lệ"));
        continue;
      }
      if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, username, "Địa chỉ email không đúng định dạng"));
        continue;
      }
      if (fullName.isEmpty()) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, username, "Họ và tên không được để trống"));
        continue;
      }
      try {
        passwordPolicyValidator.validate(password);
      } catch (RuntimeException ex) {
        errors.add(new CsvImportResultResponse.RowError(rowNum, username, ex.getMessage()));
        continue;
      }

      List<Role> parsedRoles = parseUserRoles(rawRoles);
      if (parsedRoles == null || parsedRoles.isEmpty()) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, username, "Danh sách roles không hợp lệ hoặc bị trống"));
        continue;
      }

      UserStatus status = UserStatus.ACTIVE;
      if (!rawStatus.isEmpty()) {
        try {
          status = UserStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
          errors.add(
              new CsvImportResultResponse.RowError(
                  rowNum, username, "Trạng thái không hợp lệ (chỉ ACTIVE hoặc LOCKED)"));
          continue;
        }
      }

      String facultyId = null;
      if (!rawFacultyCode.isEmpty()) {
        String facultyCode = rawFacultyCode.trim().toUpperCase(Locale.ROOT);
        Faculty faculty =
            facultyCache.computeIfAbsent(
                facultyCode, k -> facultyRepository.findByCode(k).orElse(null));
        if (faculty == null || !faculty.isActive()) {
          errors.add(
              new CsvImportResultResponse.RowError(
                  rowNum, username, "Không tìm thấy Khoa đang hoạt động với mã: " + facultyCode));
          continue;
        }
        facultyId = faculty.getId();
      }

      if (!seenUsernames.add(username) || userRepository.existsByUsername(username)) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, username, "Tên đăng nhập đã tồn tại: " + username));
        continue;
      }
      if (!seenEmails.add(email) || userRepository.existsByEmail(email)) {
        errors.add(
            new CsvImportResultResponse.RowError(rowNum, username, "Email đã tồn tại: " + email));
        continue;
      }

      Instant now = Instant.now();
      User user =
          User.builder()
              .username(username)
              .email(email)
              .passwordHash(passwordEncoder.encode(password))
              .fullName(fullName.trim())
              .facultyId(facultyId)
              .status(status)
              .tokenVersion(0L)
              .roles(parsedRoles)
              .createdAt(now)
              .updatedAt(now)
              .build();
      try {
        userRepository.save(user);
        successCount++;
      } catch (DuplicateKeyException ex) {
        errors.add(
            new CsvImportResultResponse.RowError(
                rowNum, username, "Tên đăng nhập hoặc email bị trùng trong CSDL"));
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
        auditService, AuditAction.IMPORT, "users", "batch", null, result, "Import CSV người dùng");
    return result;
  }

  private List<Role> parseUserRoles(String rawRoles) {
    if (rawRoles == null || rawRoles.isBlank()) {
      return List.of();
    }
    Set<Role> distinct = new LinkedHashSet<>();
    for (String token : rawRoles.split("[;|]")) {
      String trimmed = token.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      try {
        distinct.add(Role.valueOf(trimmed.toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ex) {
        return null;
      }
    }
    return new ArrayList<>(distinct);
  }

  private boolean hasAdminRole(List<Role> roles) {
    return roles != null && roles.contains(Role.ADMIN);
  }
}
