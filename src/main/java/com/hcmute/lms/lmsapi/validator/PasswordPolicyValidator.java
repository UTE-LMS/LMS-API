package com.hcmute.lms.lmsapi.validator;

import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Validator kiểm tra chính sách độ dài mật khẩu theo chuẩn UTF-8 (từ 8 đến 72 bytes).
 * <p>
 * Lưu ý kiến trúc: Thuật toán băm BCrypt giới hạn độ dài mật khẩu tối đa là 72 bytes.
 * Các ký tự tiếng Việt có dấu chiếm 2 đến 3 bytes trong UTF-8. Nếu chỉ dùng {@code @Size(min=8, max=72)}
 * để đếm ký tự, mật khẩu dài 30 ký tự tiếng Việt có thể vượt quá 72 bytes và bị BCrypt âm thầm cắt ngắn
 * (BCrypt password truncation attack).
 * Vì vậy, validator này đo trực tiếp độ dài theo mảng byte UTF-8.
 */
@Component
public class PasswordPolicyValidator {

    public static final int MIN_BYTES = 8;
    public static final int MAX_BYTES = 72;

    /**
     * Kiểm tra mật khẩu có thỏa mãn chính sách độ dài 8-72 UTF-8 bytes hay không.
     *
     * @param password mật khẩu thô cần kiểm tra
     * @return {@code true} nếu mật khẩu hợp lệ (8 <= byteLength <= 72), ngược lại {@code false}
     */
    public boolean isValid(String password) {
        if (password == null) {
            return false;
        }
        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        return byteLength >= MIN_BYTES && byteLength <= MAX_BYTES;
    }

    /**
     * Xác thực mật khẩu theo chính sách độ dài 8-72 UTF-8 bytes.
     * Ném ngoại lệ {@link BusinessRuleViolationException} nếu không hợp lệ.
     *
     * @param password mật khẩu thô cần xác thực
     * @throws BusinessRuleViolationException nếu mật khẩu không thỏa mãn chính sách
     */
    public void validate(String password) {
        if (!isValid(password)) {
            int length = password != null ? password.getBytes(StandardCharsets.UTF_8).length : 0;
            throw new BusinessRuleViolationException(
                    "PASSWORD_POLICY",
                    String.format("Độ dài mật khẩu phải từ %d đến %d bytes UTF-8 (hiện tại: %d bytes)",
                            MIN_BYTES, MAX_BYTES, length)
            );
        }
    }

    /**
     * Lấy độ dài byte theo bảng mã UTF-8 của chuỗi mật khẩu.
     *
     * @param password mật khẩu thô
     * @return độ dài byte UTF-8 (0 nếu null)
     */
    public int getByteLength(String password) {
        if (password == null) {
            return 0;
        }
        return password.getBytes(StandardCharsets.UTF_8).length;
    }
}
