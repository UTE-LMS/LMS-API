package com.hcmute.lms.lmsapi.config;

import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.util.StringNormalizationUtils;
import com.hcmute.lms.lmsapi.validator.PasswordPolicyValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Seeder tự động khởi tạo tài khoản quản trị viên tối cao (ADMIN) ban đầu (F01 / ADR 0003).
 * <p>
 * Logic hoạt động:
 * 1. Khi ứng dụng khởi động, kiểm tra collection {@code users}.
 * 2. Nếu collection đang rỗng (count == 0):
 *    - Đọc các biến môi trường {@code INITIAL_ADMIN_USERNAME}, {@code INITIAL_ADMIN_EMAIL}, {@code INITIAL_ADMIN_PASSWORD}.
 *    - Nếu thiếu bất kỳ biến nào: cảnh báo WARN ở cấp hệ thống và không tạo tài khoản.
 *    - Nếu đầy đủ: kiểm tra độ dài mật khẩu qua {@link PasswordPolicyValidator} (8-72 UTF-8 bytes).
 *      Nếu vi phạm chính sách: ghi log ERROR và không tạo tài khoản.
 *      Nếu hợp lệ: băm mật khẩu bằng {@link PasswordEncoder} (BCrypt), chuẩn hóa username/email và lưu tài khoản ADMIN ACTIVE.
 * 3. Nếu collection đã có người dùng: bỏ qua seeder.
 * <p>
 * BẢO MẬT TUYỆT ĐỐI: Không bao giờ hardcode mật khẩu, không bao giờ in mật khẩu thô ra log dưới bất kỳ hình thức nào.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialAdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;

    @Value("${INITIAL_ADMIN_USERNAME:}")
    private String initialAdminUsername;

    @Value("${INITIAL_ADMIN_EMAIL:}")
    private String initialAdminEmail;

    @Value("${INITIAL_ADMIN_PASSWORD:}")
    private String initialAdminPassword;

    @Override
    public void run(ApplicationArguments args) {
        long userCount = userRepository.count();
        if (userCount > 0) {
            log.debug("Collection 'users' đã chứa {} bản ghi. Bỏ qua khởi tạo ADMIN ban đầu.", userCount);
            return;
        }

        String rawUsername = initialAdminUsername != null ? initialAdminUsername.trim() : "";
        String rawEmail = initialAdminEmail != null ? initialAdminEmail.trim() : "";
        String rawPassword = initialAdminPassword != null ? initialAdminPassword : "";

        if (rawUsername.isEmpty() || rawEmail.isEmpty() || rawPassword.isEmpty()) {
            log.warn("Collection 'users' đang rỗng và thông tin quản trị viên ban đầu chưa được cấu hình đầy đủ "
                    + "qua các biến môi trường (INITIAL_ADMIN_USERNAME, INITIAL_ADMIN_EMAIL, INITIAL_ADMIN_PASSWORD). "
                    + "Hệ thống khởi động ở trạng thái rỗng chưa có tài khoản quản trị.");
            return;
        }

        if (!passwordPolicyValidator.isValid(rawPassword)) {
            log.error("Mật khẩu quản trị viên ban đầu (INITIAL_ADMIN_PASSWORD) không thỏa mãn chính sách mật khẩu "
                    + "(yêu cầu từ {} đến {} bytes UTF-8, hiện tại: {} bytes). "
                    + "Bỏ qua tạo tài khoản quản trị viên.",
                    PasswordPolicyValidator.MIN_BYTES,
                    PasswordPolicyValidator.MAX_BYTES,
                    passwordPolicyValidator.getByteLength(rawPassword));
            return;
        }

        String username = StringNormalizationUtils.normalizeUsername(rawUsername);
        String email = StringNormalizationUtils.normalizeEmail(rawEmail);
        String passwordHash = passwordEncoder.encode(rawPassword);

        Instant now = Instant.now();
        User adminUser = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .fullName("System Administrator")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(
                        RoleAssignment.builder()
                                .role(Role.ADMIN)
                                .scope(null)
                                .build()
                ))
                .createdAt(now)
                .updatedAt(now)
                .build();

        userRepository.save(adminUser);

        log.info("Khởi tạo thành công tài khoản quản trị viên tối cao ban đầu: username='{}', email='{}'",
                username, email);
    }
}
