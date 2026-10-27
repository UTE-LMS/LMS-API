package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.dto.CreateUserRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UpdateUserRequest;
import com.hcmute.lms.lmsapi.dto.UpdateUserStatusRequest;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.UserMapper;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.repository.UserRepositoryCustom;
import com.hcmute.lms.lmsapi.service.ScopeValidationService;
import com.hcmute.lms.lmsapi.service.UserService;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import com.hcmute.lms.lmsapi.util.StringNormalizationUtils;
import com.hcmute.lms.lmsapi.validator.PasswordPolicyValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hiện thực dịch vụ quản lý người dùng {@link UserService} (F01 / US-02).
 * Tuân thủ nghiêm ngặt ADR 0002 và ADR 0003:
 * - Chuẩn hóa username/email trước khi lưu.
 * - Kiểm tra chính sách mật khẩu UTF-8 (8-72 bytes).
 * - Xác thực các tham chiếu scope qua {@link ScopeValidationService}.
 * - Áp dụng các ADMIN Guards bảo vệ tài khoản quản trị viên.
 * - Thu hồi tức thời và vĩnh viễn token khi tài khoản bị khóa (LOCKED) qua {@link UserRepositoryCustom#incrementTokenVersion}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final ScopeValidationService scopeValidationService;

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
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                    "Tên đăng nhập đã tồn tại trong hệ thống: " + normalizedUsername);
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                    "Địa chỉ email đã tồn tại trong hệ thống: " + normalizedEmail);
        }

        // 4. Kiểm tra tính hợp lệ của phạm vi phụ trách (Scope Validation)
        scopeValidationService.validateRoleAssignments(request.getRoleAssignments());

        // 5. Băm mật khẩu bằng BCrypt
        String passwordHash = passwordEncoder.encode(request.getPassword());

        // 6. Khởi tạo User entity
        Instant now = Instant.now();
        User user = User.builder()
                .username(normalizedUsername)
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .fullName(request.getFullName().trim())
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(request.getRoleAssignments() != null ? new ArrayList<>(request.getRoleAssignments()) : new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            user = userRepository.save(user);
        } catch (DuplicateKeyException e) {
            log.warn("Database unique index conflict on user creation: {}", e.getMessage());
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                    "Dữ liệu bị trùng lặp khóa duy nhất trong hệ thống (username hoặc email đã tồn tại)");
        }

        log.info("User [{}] created successfully with ID [{}]", user.getUsername(), user.getId());
        return userMapper.toResponse(user);
    }

    @Override
    public PageResponse<UserResponse> getUsers(int page, int size) {
        log.debug("Fetching users page [{}] size [{}]", page, size);

        // Kiểm tra và tạo Pageable chuẩn
        Pageable pageable = PageableValidationUtils.createPageable(page, size);

        Page<User> userPage = userRepository.findAll(pageable);
        List<UserResponse> content = userMapper.toResponseList(userPage.getContent());

        return PageResponse.of(content, userPage.getNumber(), userPage.getSize(), userPage.getTotalElements());
    }

    @Override
    public UserResponse getUserById(String id) {
        validateObjectId(id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse updateUser(String id, UpdateUserRequest request, String currentUserId) {
        validateObjectId(id);
        log.info("Updating user ID [{}] by current user [{}]", id, currentUserId);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // 1. Chuẩn hóa email
        String normalizedEmail = StringNormalizationUtils.normalizeEmail(request.getEmail());

        // 2. Nếu thay đổi email, kiểm tra trùng lặp email với người dùng khác
        if (!normalizedEmail.equalsIgnoreCase(user.getEmail())) {
            Optional<User> existingEmailUser = userRepository.findByEmail(normalizedEmail);
            if (existingEmailUser.isPresent() && !existingEmailUser.get().getId().equals(id)) {
                throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                        "Địa chỉ email đã tồn tại trong hệ thống: " + normalizedEmail);
            }
        }

        // 3. Kiểm tra tính hợp lệ của phạm vi phụ trách (Scope Validation)
        scopeValidationService.validateRoleAssignments(request.getRoleAssignments());

        // 4. ADMIN Guards: Kiểm tra bảo vệ tài khoản quản trị viên tối cao
        boolean targetHasAdmin = hasAdminRole(user.getRoleAssignments());
        boolean newRolesHaveAdmin = hasAdminRole(request.getRoleAssignments());

        // Nếu người dùng mục tiêu đang có quyền ADMIN và thao tác này gỡ bỏ quyền ADMIN:
        if (targetHasAdmin && !newRolesHaveAdmin) {
            // Guard 2: Chặn ADMIN tự gỡ vai trò ADMIN của chính mình
            if (currentUserId != null && currentUserId.equals(id)) {
                log.warn("ADMIN Guard triggered: Admin [{}] attempted self-demotion", currentUserId);
                throw new BusinessRuleViolationException("ADMIN_GUARD",
                        "Quản trị viên không được phép tự gỡ bỏ vai trò ADMIN của chính mình");
            }

            // Guard 3B: Chặn thao tác gỡ vai trò ADMIN của tài khoản ADMIN ở trạng thái ACTIVE duy nhất còn lại
            if (user.getStatus() == UserStatus.ACTIVE) {
                long activeAdminCount = userRepository.countByRoleAssignmentsRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
                if (activeAdminCount <= 1) {
                    log.warn("ADMIN Guard triggered: Attempted to demote the last active ADMIN [{}]", id);
                    throw new BusinessRuleViolationException("ADMIN_GUARD",
                            "Không thể gỡ bỏ vai trò ADMIN của tài khoản ADMIN ở trạng thái ACTIVE duy nhất còn lại trong hệ thống");
                }
            }
        }

        // 5. Cập nhật các trường cho phép (username là bất biến, không cập nhật passwordHash, tokenVersion được giữ nguyên)
        user.setEmail(normalizedEmail);
        user.setFullName(request.getFullName().trim());
        user.setRoleAssignments(request.getRoleAssignments() != null ? new ArrayList<>(request.getRoleAssignments()) : new ArrayList<>());
        user.setUpdatedAt(Instant.now());

        try {
            user = userRepository.save(user);
        } catch (DuplicateKeyException e) {
            log.warn("Database unique index conflict on user update: {}", e.getMessage());
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                    "Địa chỉ email đã tồn tại trong hệ thống: " + normalizedEmail);
        }

        log.info("User [{}] updated successfully", user.getId());
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse updateStatus(String id, UpdateUserStatusRequest request, String currentUserId) {
        validateObjectId(id);
        log.info("Updating status for user [{}] to [{}] by [{}]", id, request.getStatus(), currentUserId);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        UserStatus newStatus = request.getStatus();

        // Xử lý khi chuyển sang trạng thái LOCKED
        if (newStatus == UserStatus.LOCKED) {
            // Guard 1: Chặn ADMIN tự khóa tài khoản của chính mình
            if (currentUserId != null && currentUserId.equals(id)) {
                log.warn("ADMIN Guard triggered: Admin [{}] attempted self-lock", currentUserId);
                throw new BusinessRuleViolationException("ADMIN_GUARD",
                        "Quản trị viên không được phép tự khóa tài khoản của chính mình");
            }

            // Guard 3A: Chặn khóa tài khoản ADMIN ở trạng thái ACTIVE duy nhất còn lại trong hệ thống
            boolean targetHasAdmin = hasAdminRole(user.getRoleAssignments());
            if (targetHasAdmin && user.getStatus() == UserStatus.ACTIVE) {
                long activeAdminCount = userRepository.countByRoleAssignmentsRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
                if (activeAdminCount <= 1) {
                    log.warn("ADMIN Guard triggered: Attempted to lock the last active ADMIN [{}]", id);
                    throw new BusinessRuleViolationException("ADMIN_GUARD",
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
            // Mở khóa tài khoản: chuyển sang ACTIVE (tokenVersion cũ vẫn giữ nguyên giá trị đã tăng, không phục hồi)
            user.setStatus(UserStatus.ACTIVE);
            user.setUpdatedAt(Instant.now());
            user = userRepository.save(user);
        }

        log.info("Status of user [{}] updated to [{}] successfully", id, newStatus);
        return userMapper.toResponse(user);
    }

    private void validateObjectId(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    String.format("Định dạng định danh (ObjectId) '%s' không hợp lệ", id));
        }
    }

    private boolean hasAdminRole(List<RoleAssignment> assignments) {
        if (assignments == null) {
            return false;
        }
        return assignments.stream()
                .anyMatch(ra -> ra != null && Role.ADMIN.equals(ra.getRole()));
    }
}
