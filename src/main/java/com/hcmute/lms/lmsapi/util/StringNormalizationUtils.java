package com.hcmute.lms.lmsapi.util;

import java.util.Locale;

/**
 * Tiện ích chuẩn hóa chuỗi phục vụ định danh tài khoản (username, email).
 * Đảm bảo tính nhất quán (trim khoảng trắng hai đầu, chuyển thành chữ thường bằng Locale.ROOT)
 * để tránh lỗi xung đột phân biệt hoa thường khi tìm kiếm và lập chỉ mục trong CSDL.
 */
public final class StringNormalizationUtils {

    private StringNormalizationUtils() {
        // Utility class không cho phép khởi tạo đối tượng
    }

    /**
     * Chuẩn hóa chuỗi bằng cách cắt tỉa khoảng trắng ở hai đầu và chuyển về chữ thường.
     *
     * @param input chuỗi đầu vào (có thể null)
     * @return chuỗi đã chuẩn hóa, hoặc {@code null} nếu chuỗi đầu vào là null
     */
    public static String trimAndLowerCase(String input) {
        if (input == null) {
            return null;
        }
        return input.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Chuẩn hóa tên đăng nhập (username).
     *
     * @param username tên đăng nhập cần chuẩn hóa
     * @return username đã chuẩn hóa
     */
    public static String normalizeUsername(String username) {
        return trimAndLowerCase(username);
    }

    /**
     * Chuẩn hóa địa chỉ email.
     *
     * @param email địa chỉ email cần chuẩn hóa
     * @return email đã chuẩn hóa
     */
    public static String normalizeEmail(String email) {
        return trimAndLowerCase(email);
    }
}
