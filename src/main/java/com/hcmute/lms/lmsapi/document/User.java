package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Document đại diện cho người dùng và thông tin phân quyền trong collection users (F01).
 * Tuân thủ cấu trúc CSDL và validator schema trong doc/database_import.js và ADR 0003.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
@CompoundIndexes({
    @CompoundIndex(name = "username_unique_idx", def = "{'username': 1}", unique = true),
    @CompoundIndex(name = "email_unique_idx", def = "{'email': 1}", unique = true)
})
public class User {

    @Id
    private String id;

    /**
     * Tên đăng nhập của người dùng. Duy nhất, bất biến (không cho phép sửa sau khi tạo).
     */
    @Indexed(unique = true)
    @Field("username")
    private String username;

    /**
     * Địa chỉ email của người dùng. Duy nhất, cho phép cập nhật.
     */
    @Indexed(unique = true)
    @Field("email")
    private String email;

    /**
     * Mật khẩu đã băm bằng thuật toán BCrypt. Tuyệt đối không lưu mật khẩu thô.
     */
    @Field("passwordHash")
    private String passwordHash;

    /**
     * Họ và tên đầy đủ của người dùng.
     */
    @Field("fullName")
    private String fullName;

    /**
     * Trạng thái hoạt động của tài khoản (ACTIVE, LOCKED). Mặc định là ACTIVE.
     */
    @Builder.Default
    @Field("status")
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Số phiên đăng nhập/bảo mật (mặc định 0L).
     * Tăng nguyên tử qua UserRepositoryCustom.incrementTokenVersion khi người dùng đăng xuất
     * hoặc khi tài khoản bị khóa (LOCKED) để vô hiệu hóa tức thời và vĩnh viễn các JWT token cũ.
     */
    @Builder.Default
    @Field("tokenVersion")
    private long tokenVersion = 0L;

    /**
     * Danh sách phân công vai trò và phạm vi phụ trách của người dùng.
     */
    @Builder.Default
    @Field("roleAssignments")
    private List<RoleAssignment> roleAssignments = new ArrayList<>();

    /**
     * Thời điểm tạo tài khoản.
     */
    @Field("createdAt")
    private Instant createdAt;

    /**
     * Thời điểm cập nhật tài khoản gần nhất.
     */
    @Field("updatedAt")
    private Instant updatedAt;
}
