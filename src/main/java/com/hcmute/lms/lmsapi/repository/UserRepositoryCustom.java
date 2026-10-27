package com.hcmute.lms.lmsapi.repository;

/**
 * Interface định nghĩa các truy vấn và cập nhật đặc thù trên collection users.
 * Tách biệt interface để thỏa mãn nguyên lý phân tách giao diện (ISP) và đảo ngược phụ thuộc (DIP) của ADR 0002.
 */
public interface UserRepositoryCustom {

    /**
     * Tăng nguyên tử tokenVersion của người dùng thêm 1 đơn vị bằng toán tử $inc của MongoDB.
     * Sử dụng khi người dùng đăng xuất (logout) hoặc khi tài khoản bị khóa (LOCKED)
     * để vô hiệu hóa tức thời toàn bộ các JWT access token cũ đã được phát hành.
     *
     * @param userId ID định danh của người dùng
     * @return số lượng bản ghi bị thay đổi (1 nếu thành công, 0 nếu không tìm thấy người dùng)
     */
    long incrementTokenVersion(String userId);
}
