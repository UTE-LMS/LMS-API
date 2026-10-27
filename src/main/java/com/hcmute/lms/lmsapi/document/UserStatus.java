package com.hcmute.lms.lmsapi.document;

/**
 * Trạng thái hoạt động của tài khoản người dùng trong hệ thống (F01).
 */
public enum UserStatus {

    /**
     * Tài khoản đang hoạt động bình thường, được phép đăng nhập và truy cập tài nguyên.
     */
    ACTIVE,

    /**
     * Tài khoản bị khóa (vô hiệu hóa tức thời mọi phiên làm việc qua việc tăng tokenVersion).
     */
    LOCKED
}
