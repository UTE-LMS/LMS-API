package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository truy xuất dữ liệu người dùng trên collection users.
 * Kế thừa đồng thời {@link MongoRepository} và {@link UserRepositoryCustom}.
 */
@Repository
public interface UserRepository extends MongoRepository<User, String>, UserRepositoryCustom {

    /**
     * Tìm kiếm người dùng theo tên đăng nhập (username).
     *
     * @param username tên đăng nhập đã chuẩn hóa
     * @return Optional chứa User nếu tìm thấy
     */
    Optional<User> findByUsername(String username);

    /**
     * Tìm kiếm người dùng theo địa chỉ email.
     *
     * @param email địa chỉ email đã chuẩn hóa
     * @return Optional chứa User nếu tìm thấy
     */
    Optional<User> findByEmail(String email);

    /**
     * Kiểm tra sự tồn tại của tên đăng nhập.
     *
     * @param username tên đăng nhập
     * @return true nếu đã tồn tại
     */
    boolean existsByUsername(String username);

    /**
     * Kiểm tra sự tồn tại của địa chỉ email.
     *
     * @param email địa chỉ email
     * @return true nếu đã tồn tại
     */
    boolean existsByEmail(String email);

    /**
     * Đếm số lượng người dùng có vai trò cụ thể và ở trạng thái xác định.
     * Phục vụ Guard bảo vệ tài khoản ADMIN ACTIVE cuối cùng trong hệ thống.
     *
     * @param role vai trò cần đếm
     * @param status trạng thái người dùng
     * @return số lượng người dùng thỏa mãn
     */
    @Query(value = "{ 'roleAssignments.role': ?0, 'status': ?1 }", count = true)
    long countByRoleAssignmentsRoleAndStatus(Role role, UserStatus status);
}
